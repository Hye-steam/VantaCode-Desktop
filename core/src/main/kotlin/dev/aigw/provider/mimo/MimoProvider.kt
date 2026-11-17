package dev.aigw.provider.mimo

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.ApiError
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.CredentialImportSupport
import dev.aigw.provider.ModelCatalogSupport
import dev.aigw.provider.ModelInfo
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.SseSink
import dev.aigw.provider.WebTokenSupport
import dev.aigw.util.Http
import dev.aigw.util.Log
import java.net.URLEncoder
import java.util.UUID

/**
 * Mimo（小米 AI Studio，aistudio.xiaomimimo.com）供应商。
 *
 * - 鉴权：纯 Cookie 三件套（serviceToken / userId / xiaomichatbot_ph），无 Authorization 头
 * - 对话：`/open-apis/react/chat` SSE；对话前必须先 `/open-apis/chat/conversation/save` 登记会话
 * - 输入：上游只接受一条拍平的 query 字符串，OpenAI messages 由 [MimoProtocol] 展平
 * - 输出：SSE 的 `message`/`reasoningContent` 转 OpenAI chunk，工具调用经
 *   `<|CHAT2API|…>` 托管协议解析回 `tool_calls`
 */
class MimoProvider : Provider, ModelCatalogSupport, CredentialImportSupport, WebTokenSupport {

    override val id = ID
    override val displayName = "MiMo"

    override val loginPageUrl = BASE

    // ------------------------------------------------------------------ 模型

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        runCatching { MimoProtocol.MODEL_MAP.keys.toList() }

    override fun modelCatalog(account: ProviderAccount): List<ModelInfo> =
        MimoProtocol.MODEL_MAP.map { (openAiName, _) ->
            ModelInfo(
                id = openAiName,
                displayName = openAiName,
                tags = listOf("chat", "工具调用"),
            )
        }

    // ------------------------------------------------------------------ 凭证

    private fun creds(account: ProviderAccount): MimoProtocol.Credentials? =
        runCatching { MimoProtocol.Credentials.parse(account.secret) }.getOrNull()?.takeIf { it.isValid }

    /** 粘贴 Cookie（`k=v; k=v` / 换行 / JSON）即可建账号。 */
    override fun importCredentials(raw: String): ProviderAccount {
        // 兼容「账号导出 JSON」：secret 字段里才是真正的凭证（字符串或对象形态都认）
        val effective = runCatching {
            val root = JsonParser.parseString(raw.trim()).asJsonObject
            val secret = root.get("secret")
            when {
                root.has("serviceToken") || root.has("service_token") -> raw
                secret != null && secret.isJsonPrimitive -> secret.asString
                secret != null && secret.isJsonObject -> secret.toString()
                else -> raw
            }
        }.getOrDefault(raw)
        val c = MimoProtocol.Credentials.parse(effective)
            ?: throw IllegalArgumentException("解析失败：需同时包含 serviceToken、userId、xiaomichatbot_ph")
        return ProviderAccount(
            providerId = id,
            uid = c.userId,
            displayName = "MiMo ${c.userId.takeLast(6)}",
            secret = c.toJson(),
        )
    }

    /** 网页登录后从 CookieManager 取到的三件套同样走 Cookie 解析。 */
    override fun loginWithToken(token: String): ProviderAccount = importCredentials(token)

    // ------------------------------------------------------------------ 请求头

    private fun headers(c: MimoProtocol.Credentials): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Cookie" to c.cookieHeader(),
        "Origin" to BASE,
        "Referer" to "$BASE/",
        "X-Timezone" to "Asia/Shanghai",
        "Accept" to "*/*",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        "Cache-Control" to "no-cache",
        "Pragma" to "no-cache",
        "Sec-Ch-Ua-Mobile" to "?0",
        "Sec-Ch-Ua-Platform" to "\"Windows\"",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to USER_AGENT,
    )

    private fun url(path: String, c: MimoProtocol.Credentials): String =
        "$BASE$path?xiaomichatbot_ph=" + URLEncoder.encode(c.phToken, "UTF-8")

    /** 对话前必须登记会话，否则上游拒绝。 */
    private fun saveConversation(c: MimoProtocol.Credentials, conversationId: String) {
        val body = JsonObject().apply {
            addProperty("conversationId", conversationId)
            addProperty("title", "新对话")
            addProperty("type", "chat")
        }
        val r = Http.postJson(url(SAVE_PATH, c), headers(c), body.toString())
        val obj = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
        val apiCode = obj?.get("code")?.asInt
        if (!r.ok || (apiCode != null && apiCode != 0)) {
            val msg = obj?.get("msg")?.asString ?: obj?.get("message")?.asString ?: "HTTP ${r.status}"
            throw IllegalStateException("登记会话失败：$msg")
        }
    }

    // ------------------------------------------------------------------ 对话

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val c = creds(account)
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "MiMo 凭据未配置：需要 serviceToken、userId、xiaomichatbot_ph"))
        val requested = request.model
        val realModel = MimoProtocol.resolveModel(requested)
        val streaming = request.stream && sink != null

        val messages = request.body.getAsJsonArray("messages")
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "messages 不能为空"))
        val query = MimoProtocol.buildQuery(messages, request.body.getAsJsonArray("tools"))
        if (query.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "messages 中没有可发送的文本"))
        }

        val conversationId = UUID.randomUUID().toString().replace("-", "")
        val temperature = request.body.get("temperature")
            ?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.8

        return try {
            saveConversation(c, conversationId)
            val body = JsonObject().apply {
                addProperty("msgId", UUID.randomUUID().toString().replace("-", "").take(32))
                addProperty("conversationId", conversationId)
                addProperty("query", query)
                addProperty("isEditedQuery", false)
                add("modelConfig", JsonObject().apply {
                    addProperty("enableThinking", MimoProtocol.wantsThinking(requested))
                    addProperty("webSearchStatus", "disabled")
                    addProperty("model", realModel)
                    addProperty("temperature", temperature)
                    addProperty("topP", 0.95)
                })
                add("multiMedias", JsonArray())
            }
            if (streaming) streamChat(c, body, realModel, sink!!) else completeChat(c, body, realModel)
        } catch (e: Exception) {
            Log.warn("mimo chat error: ${e.message}")
            ChatResult.Failure(ApiError(ApiError.Kind.UPSTREAM, 0, (e.message ?: "请求失败").take(300)))
        }
    }

    private fun streamChat(
        c: MimoProtocol.Credentials,
        body: JsonObject,
        model: String,
        sink: SseSink,
    ): ChatResult {
        var events = 0
        var inputTokens = 0
        var outputTokens = 0
        // 正文先缓冲：工具调用块可能被 SSE 切分到多个分片，攒齐才能判断
        val pending = StringBuilder()
        val parser = MimoProtocol.LineParser()
        var done = false

        fun emitText(text: String) {
            if (text.isEmpty()) return
            events++
            sink.onData(chunk(content = text))
        }

        val r = Http.postStream(
            url = url(CHAT_PATH, c),
            headers = headers(c),
            json = body.toString(),
            onLine = { line ->
                when (val ev = parser.feed(line)) {
                    is MimoProtocol.Event.Text -> {
                        pending.append(ev.content)
                        true
                    }
                    is MimoProtocol.Event.Reasoning -> {
                        events++
                        sink.onData(chunk(reasoning = ev.content))
                        true
                    }
                    is MimoProtocol.Event.Usage -> {
                        inputTokens = ev.input
                        outputTokens = ev.output
                        true
                    }
                    MimoProtocol.Event.Finish -> {
                        done = true
                        false
                    }
                    MimoProtocol.Event.Ignore -> true
                }
            },
            onDone = {},
        )

        if (!r.ok) {
            Http.classify(r)?.let { return ChatResult.Failure(it) }
        }

        // 收尾：拆出工具调用，正文里的协议块清理掉
        val raw = pending.toString()
        val calls = if (MimoProtocol.hasToolCall(raw)) MimoProtocol.parseToolCalls(raw) else emptyList()
        val text = MimoProtocol.stripCitations(
            MimoProtocol.stripThink(MimoProtocol.stripToolBlocks(raw)),
        ).trim()
        if (text.isNotEmpty()) emitText(text)
        calls.forEachIndexed { index, call ->
            events++
            sink.onData(toolCallChunk(index, call))
        }
        sink.onData(chunk(finishReason = if (calls.isEmpty()) "stop" else "tool_calls", empty = true))
        sink.onData("[DONE]")
        sink.onDone()
        if (!done && text.isEmpty() && calls.isEmpty() && events == 0) {
            return ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, r.status, "上游未返回内容"))
        }
        return ChatResult.Streamed(events, inputTokens, outputTokens)
    }

    private fun completeChat(
        c: MimoProtocol.Credentials,
        body: JsonObject,
        model: String,
    ): ChatResult {
        val content = StringBuilder()
        val parser = MimoProtocol.LineParser()
        var inputTokens = 0
        var outputTokens = 0
        val r = Http.postStream(
            url = url(CHAT_PATH, c),
            headers = headers(c),
            json = body.toString(),
            onLine = { line ->
                when (val ev = parser.feed(line)) {
                    is MimoProtocol.Event.Text -> {
                        content.append(ev.content)
                        true
                    }
                    is MimoProtocol.Event.Usage -> {
                        inputTokens = ev.input
                        outputTokens = ev.output
                        true
                    }
                    MimoProtocol.Event.Finish -> false
                    else -> true
                }
            },
            onDone = {},
        )
        if (!r.ok) {
            Http.classify(r)?.let { return ChatResult.Failure(it) }
        }
        val raw = content.toString()
        val calls = if (MimoProtocol.hasToolCall(raw)) MimoProtocol.parseToolCalls(raw) else emptyList()
        val text = MimoProtocol.stripCitations(
            MimoProtocol.stripThink(MimoProtocol.stripToolBlocks(raw)),
        ).trim()
        if (text.isEmpty() && calls.isEmpty()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, r.status, "上游未返回内容"))
        }
        return ChatResult.Aggregated(aggregate(model, text, calls, inputTokens, outputTokens))
    }

    // ------------------------------------------------------------------ OpenAI 输出

    private fun chunkId(): String = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)

    private fun chunk(content: String = "", reasoning: String = "", finishReason: String? = null, empty: Boolean = false): String {
        val delta = JsonObject().apply {
            if (content.isNotEmpty()) addProperty("content", content)
            if (reasoning.isNotEmpty()) addProperty("reasoning_content", reasoning)
        }
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("delta", delta)
            finishReason?.let { addProperty("finish_reason", it) }
        }
        val obj = JsonObject().apply {
            addProperty("id", chunkId())
            addProperty("object", "chat.completion.chunk")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", "mimo")
            add("choices", JsonArray().apply { add(choice) })
        }
        return obj.toString()
    }

    private fun toolCallChunk(index: Int, call: MimoProtocol.Call): String {
        val delta = JsonObject().apply {
            add("tool_calls", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", index)
                    addProperty("id", call.id)
                    addProperty("type", "function")
                    add("function", JsonObject().apply {
                        addProperty("name", call.name)
                        addProperty("arguments", call.arguments)
                    })
                })
            })
        }
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("delta", delta)
        }
        return JsonObject().apply {
            addProperty("id", chunkId())
            addProperty("object", "chat.completion.chunk")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", "mimo")
            add("choices", JsonArray().apply { add(choice) })
        }.toString()
    }

    private fun aggregate(
        model: String,
        text: String,
        calls: List<MimoProtocol.Call>,
        inputTokens: Int,
        outputTokens: Int,
    ): JsonObject {
        val message = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", text)
            if (calls.isNotEmpty()) {
                add("tool_calls", JsonArray().apply {
                    calls.forEach { call ->
                        add(JsonObject().apply {
                            addProperty("id", call.id)
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", call.name)
                                addProperty("arguments", call.arguments)
                            })
                        })
                    }
                })
            }
        }
        return JsonObject().apply {
            addProperty("id", chunkId())
            addProperty("object", "chat.completion")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", model)
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("message", message)
                    addProperty("finish_reason", if (calls.isEmpty()) "stop" else "tool_calls")
                })
            })
            if (inputTokens > 0 || outputTokens > 0) {
                add("usage", JsonObject().apply {
                    addProperty("prompt_tokens", inputTokens)
                    addProperty("completion_tokens", outputTokens)
                    addProperty("total_tokens", inputTokens + outputTokens)
                })
            }
        }
    }

    companion object {
        const val ID = "mimo"
        private const val BASE = "https://aistudio.xiaomimimo.com"
        private const val CHAT_PATH = "/open-apis/react/chat"
        private const val SAVE_PATH = "/open-apis/chat/conversation/save"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0"
    }
}
