package dev.aigw.provider.deepseek

import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
import dev.aigw.provider.WebTransport
import dev.aigw.util.DsmlTools
import dev.aigw.util.Diagnostics
import dev.aigw.util.Http
import dev.aigw.util.Log
import dev.aigw.util.arrayOrNull
import dev.aigw.util.firstString
import dev.aigw.util.objOrNull
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * DeepSeek 网页版（chat.deepseek.com）。
 *
 * 会话流程：建会话 → 申请 PoW 挑战并求解 → 带 `x-ds-pow-response` 发起 completion，
 * 响应是私有 SSE（`response/fragments/-1/content` 增量），这里翻译成 OpenAI 流。
 *
 * 上游除 token 外还会校验浏览器侧风控参数，原生请求可能被 403。构造时注入的
 * [transport] 允许把请求改由 WebView 页面内的 fetch 发出（自动降级），失败再抛错。
 */
class DeepSeekProvider(
    private val transport: () -> WebTransport? = { null },
) : Provider, CredentialImportSupport, WebTokenSupport, ModelCatalogSupport {

    override val id: String = ID
    override val displayName: String = "DeepSeek"

    /** 网页版模型：reasoner 触发深度思考，search 触发联网搜索。 */
    override fun listModels(account: ProviderAccount): Result<List<String>> =
        Result.success(modelCatalog(account).map { it.id })

    override fun modelCatalog(account: ProviderAccount): List<ModelInfo> = listOf(
        ModelInfo(id = MODEL_CHAT, displayName = "DeepSeek 对话"),
        ModelInfo(id = MODEL_REASONER, displayName = "DeepSeek 深度思考"),
        ModelInfo(id = MODEL_SEARCH, displayName = "DeepSeek 联网搜索"),
        ModelInfo(id = MODEL_SEARCH_REASONER, displayName = "DeepSeek 联网 + 深度思考"),
    )
    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val credential = parse(account)
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "凭证无法解析"))
        val model = request.body.get("model")?.asString.orEmpty()
        val thinking = model.contains("reasoner") || model.contains("思考") || model.contains("think")
        val search = model.contains("search") || model.contains("联网") || model.contains("web")
        val input = try {
            parseInput(request.body)
        } catch (e: IllegalArgumentException) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, e.message ?: "附件无法解析"))
        }
        val toolsText = DsmlTools.renderTools(request.body)
        val prompt = when {
            input.prompt.isNotBlank() -> input.prompt
            input.attachments.isNotEmpty() -> "请阅读并分析我上传的文件。"
            else -> {
                val kinds = input.unsupportedTypes.joinToString("、")
                val message = if (kinds.isEmpty()) {
                    "messages 为空"
                } else {
                    "messages 中没有可发送的文本或附件（内容类型：$kinds）"
                }
                return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, message))
            }
        }.let { if (toolsText.isEmpty()) it else toolsText + "\n\n" + it }
        request.body.arrayOrNull("tools")?.size()?.takeIf { it > 0 }?.let {
            Diagnostics.debug("DeepSeek", "工具定义 $it 个已写入 prompt")
        }
        // 上游只接受文件 ID，不接受 base64；图片/文档先传到网页版附件接口再引用
        Diagnostics.debug(
            "DeepSeek",
            "prompt ${prompt.length} 字符 · 附件 ${input.attachments.size} 个" +
                if (input.unsupportedTypes.isEmpty()) "" else " · 忽略 ${input.unsupportedTypes.joinToString()}",
        )
        if (input.unsupportedTypes.isNotEmpty()) {
            Log.warn("deepseek 忽略不支持的内容类型: ${input.unsupportedTypes.joinToString()}")
        }
        val fileIds = try {
            input.attachments.map { uploadAttachment(credential, it) }
        } catch (e: StatusException) {
            return ChatResult.Failure(classifyUpload(e.status, e.body, e.message ?: "文件上传失败"))
        } catch (e: Exception) {
            Log.warn("deepseek 文件上传异常: ${e.message}")
            return ChatResult.Failure(Http.networkError(e))
        }

        var useWeb = false
        val webAvailable = transport() != null
        return try {
            var session = createSession(credential, useWeb)
            if (session.status == 403 && webAvailable && !useWeb) {
                Log.warn("deepseek 原生请求被风控拦截，改用 WebView 通道")
                useWeb = true
                session = createSession(credential, useWeb)
            }
            val sessionId = session.id
                ?: return ChatResult.Failure(classify(session.status, session.body, "创建会话失败"))

            val pow = try {
                powHeader(credential, useWeb)
            } catch (e: StatusException) {
                if (e.status == 403 && webAvailable && !useWeb) {
                    Log.warn("deepseek PoW 请求被风控拦截，改用 WebView 通道")
                    useWeb = true
                    powHeader(credential, useWeb)
                } else {
                    throw e
                }
            } ?: return ChatResult.Failure(ApiError(ApiError.Kind.UPSTREAM, 502, "获取 PoW 挑战失败"))

            val body = JsonObject().apply {
                addProperty("chat_session_id", sessionId)
                add("parent_message_id", null)
                addProperty("prompt", prompt)
                add("ref_file_ids", JsonArray().apply { fileIds.forEach { add(it) } })
                addProperty("thinking_enabled", thinking)
                addProperty("search_enabled", search)
                addProperty("preempt", false)
            }
            val headers = headers(credential).toMutableMap().apply {
                put("accept", "text/event-stream")
                put("x-ds-pow-response", pow)
            }

            if (request.stream && sink != null) {
                var events = 0
                var closed = false
                val calls = mutableListOf<DsmlTools.Call>()
                // 工具调用可能出现在正文或思维链任一通道，两个通道各挂一个提取器；
                // contentRaw/reasoningRaw 只用于失败时把原始字节打进日志，便于下次定位字符问题。
                val contentRaw = StringBuilder()
                val reasoningRaw = StringBuilder()
                val extractor = DsmlTools.StreamExtractor("DeepSeek") { text ->
                    events++
                    sink.onData(chunk(content = text))
                }
                // 模型偶尔把工具调用写在思维链里，同样要扣出来，否则客户端只看到一堆标记
                // 模型偶尔把工具调用写在思维链里，同样要扣出来，否则客户端只看到一堆标记
                val reasoningExtractor = DsmlTools.StreamExtractor("DeepSeek") { text ->
                    events++
                    sink.onData(chunk(reasoning = text))
                }
                val finishStream = {
                    calls += extractor.finish()
                    calls += reasoningExtractor.finish()
                    if (calls.isNotEmpty()) {
                        Diagnostics.info("DeepSeek", "解析出 ${calls.size} 个工具调用：${calls.joinToString { it.name }}")
                    } else if (contentRaw.contains("DSML", ignoreCase = true) || reasoningRaw.contains("DSML", ignoreCase = true)) {
                        Diagnostics.warn("DeepSeek", "原始内容：${contentRaw.take(1500)}")
                        Diagnostics.warn("DeepSeek", "原始思维链：${reasoningRaw.take(1500)}")
                    }
                    calls.forEachIndexed { index, call ->
                        sink.onData(toolCallChunk(index, call))
                    }
                    sink.onData(chunk(finish = if (calls.isEmpty()) "stop" else "tool_calls"))
                    sink.onData("[DONE]")
                }
                val translator = DeepSeekSseTranslator(
                    onContent = { text ->
                        if (contentRaw.length < 8000) contentRaw.append(text)
                        extractor.feed(text)
                    },
                    onReasoning = { text ->
                        if (reasoningRaw.length < 8000) reasoningRaw.append(text)
                        reasoningExtractor.feed(text)
                    },
                )
                val response = request(
                    path = PATH_COMPLETION,
                    headers = headers,
                    body = body.toString(),
                    useWeb = useWeb,
                    stream = true,
                    onLine = { line ->
                        if (translator.feed(line)) {
                            finishStream()
                            closed = true
                            false
                        } else {
                            true
                        }
                    },
                )
                if (!closed) finishStream()
                sink.onDone()
                if (response.status !in 200..299) {
                    Diagnostics.warn("DeepSeek", "上游 HTTP ${response.status}: ${response.body.take(300)}")
                    return ChatResult.Failure(classify(response.status, response.body, "对话请求失败"))
                }
                return ChatResult.Streamed(events)
            }

            val content = StringBuilder()
            val reasoning = StringBuilder()
            val translator = DeepSeekSseTranslator(
                onContent = { content.append(it) },
                onReasoning = { reasoning.append(it) },
            )
            val response = request(
                path = PATH_COMPLETION,
                headers = headers,
                body = body.toString(),
                useWeb = useWeb,
                stream = true,
                onLine = { line ->
                    translator.feed(line)
                    true
                },
            )
            if (response.status !in 200..299) {
                Diagnostics.warn("DeepSeek", "上游 HTTP ${response.status}: ${response.body.take(300)}")
                return ChatResult.Failure(classify(response.status, response.body, "对话请求失败"))
            }
            val (text, contentCalls) = DsmlTools.splitContent(content.toString())
            // 工具调用也可能出现在思维链里
            val (reasoningText, reasoningCalls) = DsmlTools.splitContent(reasoning.toString())
            val toolCalls = contentCalls + reasoningCalls
            if (toolCalls.isNotEmpty()) {
                Diagnostics.info("DeepSeek", "解析出 ${toolCalls.size} 个工具调用：${toolCalls.joinToString { it.name }}")
            } else if (content.contains("DSML", ignoreCase = true) || reasoning.contains("DSML", ignoreCase = true)) {
                Diagnostics.warn(
                    "DeepSeek",
                    "输出里有 DSML 但未解析出调用：${(content.toString() + reasoning.toString()).take(800)}",
                )
            }
            return ChatResult.Aggregated(aggregate(model, text, reasoningText, toolCalls))
        } catch (e: Exception) {
            Log.warn("deepseek upstream error: ${e.message}")
            ChatResult.Failure(Http.networkError(e))
        }
    }

    // ------------------------------------------------------------------ 请求通道

    private class Response(val status: Int, val body: String)

    private class StatusException(val status: Int, val body: String, message: String) : Exception(message)

    /**
     * 发一次请求。[useWeb] 为 true 时交给 WebView 页面 fetch，否则走原生 HTTP。
     * 流式请求的每一行经 [onLine] 回调。
     */
    private fun request(
        path: String,
        headers: Map<String, String>,
        body: String,
        useWeb: Boolean,
        stream: Boolean,
        onLine: ((String) -> Boolean)? = null,
    ): Response {
        val web = if (useWeb) transport() else null
        if (web != null) {
            val result = web.post(path, headers, body, stream, onLine)
            if (result.error.isNotEmpty()) throw IllegalStateException(result.error)
            return Response(result.status, result.body)
        }
        return if (stream) {
            val result = Http.postStream(
                url = BASE + path,
                headers = headers,
                json = body,
                timeoutMs = STREAM_TIMEOUT_MS,
                onLine = { line -> onLine?.invoke(line) ?: true },
                onDone = { },
            )
            Response(result.status, result.body)
        } else {
            val result = Http.postJson(BASE + path, headers, body, TIMEOUT_MS)
            Response(result.status, result.body)
        }
    }

    // ------------------------------------------------------------------ 网页会话

    private class SessionResult(val status: Int, val body: String, val id: String?)

    private fun createSession(credential: Credential, useWeb: Boolean): SessionResult {
        val response = request(PATH_SESSION_CREATE, headers(credential), "{}", useWeb, stream = false)
        if (response.status !in 200..299) return SessionResult(response.status, response.body, null)
        val obj = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull()
            ?: return SessionResult(response.status, response.body, null)
        val biz = obj.objOrNull("data")?.objOrNull("biz_data") ?: obj.objOrNull("data") ?: obj
        // 抓包结构：data.biz_data.chat_session.id
        val id = biz.objOrNull("chat_session")?.firstString("id")
            ?.ifEmpty { biz.firstString("id", "chat_session_id") }
            ?.ifEmpty { null }
            ?: biz.firstString("id", "chat_session_id").ifEmpty { null }
        return SessionResult(response.status, response.body, id)
    }

    private fun powHeader(credential: Credential, useWeb: Boolean, targetPath: String = PATH_COMPLETION): String? {
        val body = JsonObject().apply { addProperty("target_path", targetPath) }
        val response = request(PATH_POW, headers(credential), body.toString(), useWeb, stream = false)
        if (response.status !in 200..299) {
            throw StatusException(response.status, response.body, "获取 PoW 挑战失败（HTTP ${response.status}）")
        }
        val obj = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull() ?: return null
        val biz = obj.objOrNull("data")?.objOrNull("biz_data") ?: obj.objOrNull("data") ?: obj
        val challenge = biz.objOrNull("challenge") ?: return null
        return DeepSeekHash.powHeader(challenge)
    }

    private fun chunk(content: String = "", reasoning: String = "", finish: String? = null): String {
        val delta = JsonObject()
        if (content.isNotEmpty()) delta.addProperty("content", content)
        if (reasoning.isNotEmpty()) delta.addProperty("reasoning_content", reasoning)
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("delta", delta)
            if (finish != null) addProperty("finish_reason", finish)
        }
        return JsonObject().apply {
            addProperty("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24))
            addProperty("object", "chat.completion.chunk")
            addProperty("created", System.currentTimeMillis() / 1000)
            add("choices", JsonArray().apply { add(choice) })
        }.toString()
    }

    private fun aggregate(
        model: String,
        content: String,
        reasoning: String,
        calls: List<DsmlTools.Call> = emptyList(),
    ): JsonObject {
        val message = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content)
            if (reasoning.isNotEmpty()) addProperty("reasoning_content", reasoning)
            if (calls.isNotEmpty()) {
                add("tool_calls", JsonArray().apply {
                    calls.forEachIndexed { index, call -> add(toolCall(index, call)) }
                })
            }
        }
        return JsonObject().apply {
            addProperty("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24))
            addProperty("object", "chat.completion")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", model.ifEmpty { MODEL_CHAT })
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("message", message)
                    addProperty("finish_reason", if (calls.isEmpty()) "stop" else "tool_calls")
                })
            })
        }
    }

    private fun toolCall(index: Int, call: DsmlTools.Call): JsonObject = JsonObject().apply {
        addProperty("index", index)
        addProperty("id", callId())
        addProperty("type", "function")
        add("function", JsonObject().apply {
            addProperty("name", call.name)
            addProperty("arguments", call.arguments)
        })
    }

    private fun callId(): String = "call_" + UUID.randomUUID().toString().replace("-", "").take(24)

    private fun toolCallChunk(index: Int, call: DsmlTools.Call): String {
        val delta = JsonObject().apply {
            add("tool_calls", JsonArray().apply { add(toolCall(index, call)) })
        }
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("delta", delta)
        }
        return JsonObject().apply {
            addProperty("id", "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24))
            addProperty("object", "chat.completion.chunk")
            addProperty("created", System.currentTimeMillis() / 1000)
            add("choices", JsonArray().apply { add(choice) })
        }.toString()
    }

    // ------------------------------------------------------------------ 登录 / 导入

    override val loginPageUrl: String = BASE + "/sign_in"

    override fun loginWithToken(token: String): ProviderAccount {
        val trimmed = token.trim()
        if (trimmed.length < 16) throw IllegalStateException("没有取到有效的登录 token，请确认已登录完成")
        val credential = Credential(token = trimmed, deviceId = UUID.randomUUID().toString())
        // 拿不到真实用户信息就不能建账号，避免生成一个假账号
        val identity = fetchIdentity(credential)
            ?: throw IllegalStateException("token 无效或已过期，请重新登录")
        return toProviderAccount(credential.copy(uid = identity.first, nickname = identity.second))
    }

    override fun importCredentials(raw: String): ProviderAccount {
        val trimmed = raw.trim()
        val token: String
        var deviceId = ""
        if (trimmed.startsWith("{")) {
            val obj = runCatching { JsonParser.parseString(trimmed).asJsonObject }.getOrNull()
                ?: throw IllegalArgumentException("凭证不是合法 JSON")
            token = obj.firstString("token", "userToken", "access_token")
                .ifEmpty { obj.objOrNull("auth")?.firstString("token").orEmpty() }
            deviceId = obj.firstString("deviceId", "device_id")
        } else {
            token = trimmed
        }
        // 兼容「账号导出 JSON」：顶层没有 token，只有 secret 字段（里面是凭证 JSON）
        if (token.isEmpty()) {
            val secretText = runCatching {
                val secretNode = JsonParser.parseString(trimmed).asJsonObject.get("secret")
                when {
                    secretNode != null && secretNode.isJsonPrimitive -> secretNode.asString
                    secretNode != null && secretNode.isJsonObject -> secretNode.toString()
                    else -> ""
                }
            }.getOrDefault("")
            if (secretText.isNotEmpty()) return importCredentials(secretText)
        }
        if (token.isEmpty()) throw IllegalArgumentException("凭证缺少 token")
        val credential = Credential(
            token = token,
            deviceId = deviceId.ifEmpty { UUID.randomUUID().toString() },
        )
        val identity = fetchIdentity(credential)
            ?: throw IllegalStateException("token 无效或已过期")
        return toProviderAccount(credential.copy(uid = identity.first, nickname = identity.second))
    }

    private fun fetchIdentity(credential: Credential): Pair<String, String>? {
        if (credential.token.isBlank()) return null
        val result = try {
            Http.get(BASE + PATH_USER_CURRENT, headers(credential), TIMEOUT_MS)
        } catch (e: Exception) {
            throw IllegalStateException("校验 token 失败：${e.message}", e)
        }
        if (result.status == 401 || result.status == 403) return null

        if (result.ok) {
            val obj = runCatching { JsonParser.parseString(result.body).asJsonObject }.getOrNull()
            val data = obj?.objOrNull("data")
            val biz = data?.objOrNull("biz_data")
            // 用户信息可能挂在 biz_data、biz_data.user 或 data 下，逐个找
            val candidates = listOfNotNull(biz, biz?.objOrNull("user"), data, obj)
            for (candidate in candidates) {
                val id = candidate.firstString("id", "user_id", "user_id_str")
                if (id.isNotEmpty()) {
                    val name = candidate.firstString("name", "email", "mobile_number", "phone_number")
                        .ifEmpty { findDisplayName(obj) }
                    return id to name
                }
            }
        }

        // users/current 不可用或结构变了：用一次建会话探测 token 是否真的可用。
        // 探测通过说明 token 有效，uid 用 token 摘要（同一 token 永远落在同一账号）。
        return probeToken(credential)
    }

    private fun probeToken(credential: Credential): Pair<String, String>? {
        val probe = try {
            Http.postJson(BASE + PATH_SESSION_CREATE, headers(credential), "{}", TIMEOUT_MS)
        } catch (e: Exception) {
            throw IllegalStateException("校验 token 失败：${e.message}", e)
        }
        if (probe.status == 401 || probe.status == 403) return null
        if (!probe.ok) throw IllegalStateException("校验 token 失败（HTTP ${probe.status}）")
        val obj = runCatching { JsonParser.parseString(probe.body).asJsonObject }.getOrNull()
        val code = obj?.get("code")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        if (code != 0L) {
            val message = obj?.firstString("msg", "message").orEmpty()
            throw IllegalStateException(if (message.isNotEmpty()) message else "校验 token 失败（code=$code）")
        }
        return tokenUid(credential.token) to "DeepSeek 用户"
    }

    /**
     * 定时健康检查：探测网页 token 是否还有效（用 chat_session/create，与真实失败点一致）。
     * 只把「明确凭证失效」判 false；风控 403、5xx、拿不到 id 但无错误信息都判 true，
     * 避免误伤——风控交给请求时的 WebView 降级，瞬态错误交给正常重试。
     */
    fun isTokenValid(account: ProviderAccount): Boolean {
        val credential = parse(account) ?: return false
        return runCatching {
            val session = createSession(credential, useWeb = false)
            when {
                session.status == 401 -> false
                session.status == 403 -> true
                session.id != null -> true
                accountUnavailable(session.body) -> false
                else -> true
            }
        }.getOrDefault(true)
    }

    /** 由 token 派生的稳定 uid：同一 token 两次登录指向同一账号。 */
    /** 在响应任意层级找邮箱/昵称/手机号，作为账号可读名称。 */
    private fun findDisplayName(root: JsonElement?, depth: Int = 0): String {
        if (root == null || depth > 5) return ""
        if (root.isJsonObject) {
            val obj = root.asJsonObject
            for (key in NAME_KEYS) {
                val value = obj.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
                if (!value.isNullOrBlank()) return value
            }
            for (entry in obj.entrySet()) {
                val found = findDisplayName(entry.value, depth + 1)
                if (found.isNotEmpty()) return found
            }
        } else if (root.isJsonArray) {
            for (item in root.asJsonArray) {
                val found = findDisplayName(item, depth + 1)
                if (found.isNotEmpty()) return found
            }
        }
        return ""
    }

    private fun tokenUid(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return "ds-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    // ------------------------------------------------------------------ 工具

    private fun headers(credential: Credential): Map<String, String> = buildMap {
        put("accept", "*/*")
        put("content-type", "application/json")
        put("authorization", "Bearer ${credential.token}")
        put("origin", BASE)
        put("referer", "$BASE/")
        put("x-app-version", CLIENT_VERSION)
        put("x-client-version", CLIENT_VERSION)
        put("x-client-platform", "web")
        put("x-client-bundle-id", "com.deepseek.chat")
        put("x-client-locale", "zh_CN")
        put("x-client-timezone-offset", timezoneOffsetSeconds().toString())
        if (credential.deviceId.isNotEmpty()) put("x-device-id", credential.deviceId)
    }

    private fun timezoneOffsetSeconds(): Int =
        TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000

    private fun parse(account: ProviderAccount): Credential? {
        if (account.secret.isBlank()) return null
        val obj = runCatching { JsonParser.parseString(account.secret).asJsonObject }.getOrNull() ?: return null
        val token = obj.firstString("token")
        return if (token.isEmpty()) null else Credential(
            token = token,
            uid = obj.firstString("uid"),
            nickname = obj.firstString("nickname"),
            deviceId = obj.firstString("deviceId", "device_id"),
        )
    }

    private fun toProviderAccount(credential: Credential): ProviderAccount {
        val secret = JsonObject().apply {
            addProperty("token", credential.token)
            addProperty("uid", credential.uid)
            addProperty("nickname", credential.nickname)
            addProperty("deviceId", credential.deviceId)
        }.toString()
        return ProviderAccount(
            providerId = id,
            uid = credential.uid,
            displayName = credential.nickname.ifEmpty { "DeepSeek " + credential.uid.takeLast(4) },
            secret = secret,
        )
    }

    // ------------------------------------------------------------------ 附件

    private class Attachment(val bytes: ByteArray, val fileName: String, val mime: String)

    private class ParsedInput(
        val prompt: String,
        val attachments: List<Attachment>,
        val unsupportedTypes: Set<String>,
    )

    /**
     * OpenAI 的 content 是「文本 + 附件」混排数组，而上游 completion 只收纯文本 prompt
     * 加 ref_file_ids。这里拆成两部分：文本拼 prompt，data URL 附件解码后等上传。
     * 处理不了的类型不静默丢弃，记下来报给客户端，避免只看到「messages 为空」。
     */
    private fun parseInput(body: JsonObject): ParsedInput {
        val messages = body.arrayOrNull("messages") ?: return ParsedInput("", emptyList(), emptySet())
        val sb = StringBuilder()
        val attachments = mutableListOf<Attachment>()
        val unsupported = mutableSetOf<String>()
        for (element in messages) {
            val message = element as? JsonObject ?: continue
            val role = message.get("role")?.asString.orEmpty()
            val text = StringBuilder()
            val content = message.get("content")
            if (content != null) {
                if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
                    text.append(content.asString)
                } else if (content.isJsonArray) {
                    for (part in content.asJsonArray) {
                        val obj = part as? JsonObject ?: continue
                        val type = obj.firstString("type")
                        when (type) {
                            "text" -> text.append(obj.firstString("text"))
                            "image_url" -> {
                                if (role != "user") continue
                                val url = when (val value = obj.get("image_url")) {
                                    null -> ""
                                    is JsonObject -> value.firstString("url")
                                    else -> if (value.isJsonPrimitive) value.asString else ""
                                }
                                val att = decodeDataUrl(url, "image", "image/png")
                                if (att != null) attachments.add(att) else unsupported.add("image_url")
                            }
                            "image" -> {
                                if (role != "user") continue
                                val source = obj.objOrNull("source")
                                val mime = source?.firstString("media_type").orEmpty().ifEmpty { "image/png" }
                                val data = source?.firstString("data").orEmpty()
                                val att = decodeBase64(data, mime, "image")
                                if (att != null) attachments.add(att) else unsupported.add("image")
                            }
                            "file", "input_file" -> {
                                if (role != "user") continue
                                val file = obj.objOrNull("file") ?: obj
                                val name = file.firstString("filename", "name").ifEmpty { "file" }
                                val mime = file.firstString("mime_type").ifEmpty { "application/octet-stream" }
                                val data = file.firstString("file_data").ifEmpty { file.firstString("file_url") }
                                val att = decodeDataUrl(data, name, mime)
                                if (att != null) {
                                    attachments.add(att)
                                } else if (file.firstString("file_id").isNotEmpty()) {
                                    // 网关没有 /v1/files 存储，这类 file_id 无法回溯成字节
                                    unsupported.add("file_id")
                                } else {
                                    unsupported.add("file")
                                }
                            }
                            else -> if (type.isNotEmpty()) unsupported.add(type)
                        }
                    }
                }
            }
            message.arrayOrNull("tool_calls")?.takeIf { it.size() > 0 }?.let { calls ->
                text.append('\n').append(DsmlTools.renderToolCalls(calls))
            }
            if (text.isBlank()) continue
            val label = when (role) {
                "system", "developer" -> "系统指令"
                "assistant" -> "助手"
                "tool" -> "工具结果"
                else -> "用户"
            }
            sb.append(label).append("：").append(text).append("\n\n")
        }
        return ParsedInput(sb.toString().trim(), attachments, unsupported)
    }

    /** 历史里的 assistant tool_calls 还原成 DSML，否则多轮工具调用会断链。 */
    private fun decodeDataUrl(data: String, nameHint: String, fallbackMime: String): Attachment? {
        if (!data.startsWith("data:", ignoreCase = true)) return null
        val comma = data.indexOf(',')
        if (comma < 5) return null
        val meta = data.substring(5, comma)
        val mime = meta.substringBefore(';').ifEmpty { fallbackMime }
        val payload = data.substring(comma + 1)
        val bytes = try {
            if (meta.contains("base64", ignoreCase = true)) {
                Base64.getDecoder().decode(payload)
            } else {
                URLDecoder.decode(payload, "UTF-8").toByteArray(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            return null
        }
        return buildAttachment(bytes, mime, nameHint)
    }

    private fun decodeBase64(data: String, mime: String, nameHint: String): Attachment? {
        if (data.isBlank()) return null
        val bytes = try {
            Base64.getDecoder().decode(data.trim())
        } catch (_: Exception) {
            return null
        }
        return buildAttachment(bytes, mime, nameHint)
    }

    private fun buildAttachment(bytes: ByteArray, mime: String, nameHint: String): Attachment {
        if (bytes.isEmpty()) throw IllegalArgumentException("附件内容为空")
        if (bytes.size > MAX_ATTACHMENT_BYTES) {
            throw IllegalArgumentException(
                "附件过大：${bytes.size / (1024 * 1024)}MB，最大支持 ${MAX_ATTACHMENT_BYTES / (1024 * 1024)}MB",
            )
        }
        val ext = when (mime.lowercase(Locale.US)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "application/pdf" -> "pdf"
            "text/plain" -> "txt"
            else -> mime.substringAfter('/', "").substringBefore('+').take(8).ifEmpty { "bin" }
        }
        val base = nameHint.ifBlank { "attachment" }
        val fileName = if (base.contains('.')) base else "$base-${digest(bytes).take(8)}.$ext"
        val safe = fileName.map { c -> if (c.isLetterOrDigit() || c in "-_." || c.code > 127) c else '_' }
            .joinToString("").take(100)
        return Attachment(bytes, safe, mime)
    }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ 文件上传

    /**
     * 网页版 completion 只认附件 ID：先申请 upload_file 专用 PoW，再 multipart 上传，
     * 拿到 ID 后轮询 fetch_files 直到解析完成，否则模型读到的是空附件。
     */
    private fun uploadAttachment(credential: Credential, att: Attachment): String {
        val key = digest(credential.token.toByteArray() + byteArrayOf(0) + att.bytes)
        synchronized(fileIdCache) { fileIdCache[key] }?.let {
            Diagnostics.debug("DeepSeek", "附件 ${att.fileName} 命中缓存：$it")
            return it
        }
        val startedAt = System.currentTimeMillis()
        Diagnostics.debug("DeepSeek", "上传附件 ${att.fileName}（${att.bytes.size / 1024}KB）")

        val pow = powHeader(credential, useWeb = false, targetPath = PATH_UPLOAD)
            ?: throw StatusException(502, "", "获取文件上传 PoW 挑战失败")
        val headers = headers(credential).toMutableMap().apply {
            remove("content-type")
            put("accept", "*/*")
            put("x-ds-pow-response", pow)
            put("x-file-size", att.bytes.size.toString())
            put("x-thinking-enabled", "0")
            put("x-model-type", "default")
        }
        val response = Http.postMultipart(
            url = BASE + PATH_UPLOAD,
            headers = headers,
            fieldName = "file",
            fileName = att.fileName,
            contentType = att.mime,
            bytes = att.bytes,
            timeoutMs = UPLOAD_TIMEOUT_MS,
        )
        if (response.status !in 200..299) {
            throw StatusException(response.status, response.body, "文件上传失败（HTTP ${response.status}）")
        }
        val obj = runCatching { JsonParser.parseString(response.body).asJsonObject }.getOrNull()
        val biz = obj?.objOrNull("data")?.objOrNull("biz_data") ?: obj?.objOrNull("data") ?: obj
        val id = biz?.firstString("id", "file_id").orEmpty()
        if (id.isEmpty()) throw StatusException(502, response.body, "文件上传失败：上游未返回文件 ID")
        awaitFileParsed(credential, id)
        synchronized(fileIdCache) { fileIdCache[key] = id }
        Diagnostics.info(
            "DeepSeek",
            "附件上传完成 ${att.fileName}（${System.currentTimeMillis() - startedAt}ms）：$id",
        )
        return id
    }

    private fun awaitFileParsed(credential: Credential, fileId: String) {
        val deadline = System.currentTimeMillis() + FILE_PARSE_TIMEOUT_MS
        while (true) {
            val url = BASE + PATH_FETCH_FILES + "?file_ids=" + URLEncoder.encode(fileId, "UTF-8")
            val response = Http.get(url, headers(credential), TIMEOUT_MS)
            if (response.status !in 200..299) {
                throw StatusException(response.status, response.body, "查询文件解析状态失败（HTTP ${response.status}）")
            }
            val status = fileStatus(response.body, fileId)
            when {
                status == "SUCCESS" -> return
                status in FILE_FAILURE_STATUSES ->
                    throw StatusException(502, response.body, "文件解析失败：$status")
            }
            if (System.currentTimeMillis() > deadline) {
                throw StatusException(504, "", "文件解析超时（${FILE_PARSE_TIMEOUT_MS / 1000}s）")
            }
            Thread.sleep(800)
        }
    }

    private fun fileStatus(body: String, fileId: String): String {
        val root = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return ""
        val files = root.objOrNull("data")?.objOrNull("biz_data")?.arrayOrNull("files")
            ?: root.objOrNull("data")?.arrayOrNull("files")
            ?: root.arrayOrNull("files")
            ?: return ""
        for (item in files) {
            val obj = item as? JsonObject ?: continue
            val id = obj.firstString("id", "file_id")
            if (id.isEmpty() || id == fileId) return obj.firstString("status", "file_status").uppercase(Locale.US)
        }
        return ""
    }

    /**
     * 上传接口的 403 多是风控或体积限制，不能按 AUTH 处理——那会直接禁用整个账号。
     */
    private fun classifyUpload(status: Int, body: String, fallback: String): ApiError {
        val kind = when {
            status == 401 -> ApiError.Kind.AUTH
            // 403 往往是风控而非凭证问题，按上游错误处理：只换号，不禁用账号
            status == 403 && !accountUnavailable(body) -> ApiError.Kind.UPSTREAM
            status == 403 -> ApiError.Kind.AUTH
            status in 400..499 -> ApiError.Kind.CLIENT
            else -> ApiError.Kind.UPSTREAM
        }
        val message = runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrNull()?.firstString("message", "msg", "error_msg")
            .orEmpty()
        return ApiError(kind, status, message.ifEmpty { "$fallback（HTTP $status）" })
    }

    private fun classify(status: Int, body: String, fallback: String): ApiError {
        val kind = when {
            // 业务级账号不可用：上游常返回 HTTP 200 + msg（INVALID_TOKEN / 禁言），不能只认 401/403
            status == 401 || status == 403 || accountUnavailable(body) -> ApiError.Kind.AUTH
            status == 429 -> ApiError.Kind.UPSTREAM
            status in 500..599 -> ApiError.Kind.UPSTREAM
            status in 400..499 -> ApiError.Kind.CLIENT
            else -> ApiError.Kind.UPSTREAM
        }
        val message = runCatching { JsonParser.parseString(body).asJsonObject }
            .getOrNull()?.firstString("message", "msg", "error_msg")
            .orEmpty()
        return ApiError(kind, status, message.ifEmpty { "$fallback（HTTP $status）" })
    }

    /**
     * 响应体是否明确表示「账号不可用」：凭证失效（token 过期/无效）或账号被禁言/封禁。
     * 命中则按 AUTH 禁用账号，避免反复冷却重试。
     */
    private fun accountUnavailable(body: String): Boolean {
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return false
        val msg = obj.firstString("message", "msg", "error_msg").uppercase(Locale.US)
        val code = obj.firstString("code", "error_code", "err_code").uppercase(Locale.US)
        val credentialInvalid = msg.contains("INVALID_TOKEN") || msg.contains("TOKEN_EXPIRED") ||
            code.contains("INVALID_TOKEN") || code.contains("TOKEN_EXPIRED") || msg.contains("UNAUTHORIZED")
        val muted = msg.contains("禁言") || msg.contains("封禁") || msg.contains("使用规范") ||
            msg.contains("BANNED") || msg.contains("MUTED") || msg.contains("SUSPENDED") ||
            msg.contains("FORBIDDEN")
        return credentialInvalid || muted
    }

    private data class Credential(
        val token: String,
        val uid: String = "",
        val nickname: String = "",
        val deviceId: String = "",
    )

    companion object {
        const val ID = "deepseek"
        private const val BASE = "https://chat.deepseek.com"
        private const val PATH_SESSION_CREATE = "/api/v0/chat_session/create"
        private const val PATH_POW = "/api/v0/chat/create_pow_challenge"
        private const val PATH_COMPLETION = "/api/v0/chat/completion"
        private const val PATH_UPLOAD = "/api/v0/file/upload_file"
        private const val PATH_FETCH_FILES = "/api/v0/file/fetch_files"
        private const val PATH_USER_CURRENT = "/api/v0/users/current"
        private const val CLIENT_VERSION = "2.5.0"
        private const val TIMEOUT_MS = 30_000
        private const val STREAM_TIMEOUT_MS = 300_000
        private const val UPLOAD_TIMEOUT_MS = 180_000
        private const val FILE_PARSE_TIMEOUT_MS = 120_000L
        private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024

        /** fetch_files 里表示解析失败的终态，遇到就不必再等。 */
        private val FILE_FAILURE_STATUSES = setOf(
            "FAILED", "ERROR", "CONTENT_FILTER", "CONTENT_TOO_LONG", "CANCELLED", "CONTENT_EMPTY",
        )

        /** 同一张图在历史里会反复出现，按 (token, 内容) 缓存上传结果，避免每轮重传。 */
        private val fileIdCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 32
        }
        /** 对外模型名直接写能力，下游客户端不读 display_name 时也能分辨。 */
        private const val MODEL_CHAT = "对话"
        private const val MODEL_REASONER = "深度思考"
        private const val MODEL_SEARCH = "联网搜索"
        private const val MODEL_SEARCH_REASONER = "联网+深度思考"

        private val NAME_KEYS = listOf(
            "email", "nickname", "display_name", "name",
            "mobile_number", "phone_number", "phone", "user_name", "username",
        )
    }
}
