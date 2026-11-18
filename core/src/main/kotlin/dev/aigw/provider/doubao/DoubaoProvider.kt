package dev.aigw.provider.doubao

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.AccountActivitySupport
import dev.aigw.provider.ApiError
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.SseSink
import dev.aigw.gateway.MediaProxy
import dev.aigw.util.Http
import dev.aigw.util.Log
import java.util.UUID

/**
 * 豆包：把客户端「工作模式」的 /chat/completion 协议包装成 OpenAI 兼容接口。
 *
 * 协议来源：yyydsz/doubao2api（Go 实现）的实抓结果。
 * 与网页版通道的区别：这条走的是 PC 客户端协议，不需要 WebView，
 * 直接用账号 Cookie 就能调，代价是需要 device_id / web_id 与 Cookie 同源。
 *
 * 请求要点：
 * - `need_deep_think` / `use_deep_think` 是 10001（不是网页版的 9）
 * - 走日常模式（`agent_mode=0` + `conversation_mode=1`）：14 秒出结果，
 *   且图片/视频直接可用。工作模式（agent=1）要先读技能文件、走沙箱，
 *   一次要 100 秒以上，同样的请求收益更差。
 * - `model_item_key` 决定实际模型，不是一个通用 model 字段
 *
 * 响应要点：
 * - 正文优先取 CHUNK_DELTA.text，没有时退回 STREAM_CHUNK 里 block_type=10000
 * - 思考过程是 block_type=10040 的 thinking_block
 * - 会话 id 从 SSE_ACK.ack_client_meta 取，多轮时要回传
 * - 收尾看 SSE_REPLY_END，msg_finish_attr.brief 是完整答案（可兜底）
 */
class DoubaoProvider : Provider, AccountActivitySupport {

    override val id = ID
    override val displayName = "豆包"

    /** 凭证：Cookie + 与它同源的设备标识。 */
    data class Cred(
        val cookie: String,
        val deviceId: String,
        val webId: String,
        val localAppId: String = "582478",
    )

    companion object {
        const val ID = "doubao"

        private const val QUOTA_URL = "https://www.doubao.com/alice/commerce/sale/subscription/quota/summary/"
private const val CHAT_URL = "https://www.doubao.com/chat/completion"

        /** bot_id / mode_id / 窗口大小来自实抓的工作模式配置。 */
        private const val BOT_ID = "7338286299411103781"
        private const val MODE_ID = "3"
        private const val REASONING_EFFORT = "4"
        private const val MEMORY_PROFILE = "300k_230"
        private const val TOTAL_WINDOW = "300000"

        /** 模型名 → model_item_key。逐条来自参考项目的实测映射。 */
        val MODEL_MAP: LinkedHashMap<String, String> = linkedMapOf(
            "auto" to "9",
            "doubao-2.1-lite" to "seed-lite-7b",
            "doubao-2.1-pro" to "5",
            "deepseek-v4-flash" to "person-deepseek-v4-1-flash-261017",
            "deepseek-v4-pro" to "person-deepseek-v4-pro-ga-260813",
            "kimi-k3" to "person-kimi-k3-261111",
            "kimi-k3.1-pro" to "person-kimi-k3-1-pro-261111",
            "kimi-k3.1-pro-fast" to "person-kimi-k3-1-pro-highspeed-261111",
            "glm-5.3" to "person-glm-5-3-261111",
            "glm-5.3-flash" to "person-glm-5-3-flash-261111",
        )

        /** 每个模型的上下文档位，网关按请求模型选对应 memory_profile。 */
        private val WINDOW_OF: Map<String, Pair<String, String>> = mapOf(
            "auto" to ("256k" to "256000"),
            "doubao-2.1-lite" to ("256k" to "256000"),
            "doubao-2.1-pro" to ("256k" to "256000"),
            "deepseek-v4-flash" to ("256k" to "256000"),
            "deepseek-v4-pro" to ("300k_230" to "300000"),
            "kimi-k3" to ("256k" to "256000"),
            "kimi-k3.1-pro" to ("256k" to "256000"),
            "kimi-k3.1-pro-fast" to ("256k" to "256000"),
            "glm-5.3" to ("256k" to "256000"),
            "glm-5.3-flash" to ("256k" to "256000"),
        )

        fun parseCred(account: ProviderAccount): Cred? {
            val raw = account.secret.ifBlank { account.apiKey }
            if (raw.isBlank()) return null
            return runCatching {
                val o = JsonParser.parseString(raw).asJsonObject
                Cred(
                    cookie = o.get("cookie")?.asString.orEmpty(),
                    deviceId = o.get("device_id")?.asString.orEmpty(),
                    webId = o.get("web_id")?.asString.orEmpty(),
                    localAppId = o.get("local_app_id")?.asString?.ifBlank { "582478" } ?: "582478",
                )
            }.getOrNull()?.takeIf { it.cookie.isNotBlank() && it.deviceId.isNotBlank() }
        }
    }

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        Result.success(MODEL_MAP.keys.toList())

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val cred = parseCred(account)
            ?: return ChatResult.Failure(
                ApiError(ApiError.Kind.AUTH, 401, "豆包账号缺少 Cookie / device_id"),
            )

        val model = request.body.get("model")?.asString?.trim().orEmpty()
        if (!MODEL_MAP.containsKey(model)) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "未知的豆包模型「$model」"))
        }
        val prompt = extractPrompt(request.body)
        if (prompt.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "messages 里没有可用文本"))
        }

        val url = "$CHAT_URL?" + queryString(cred)
        val body = buildBody(cred, model, prompt).toString()

        return try {
            if (request.stream && sink != null) {
                streamChat(url, headers(cred), body, sink)
            } else {
                // 非流式也要按流解析：上游只有 SSE，没有一次性响应
                nonStreamChat(url, headers(cred), body)
            }
        } catch (e: Exception) {
            Log.warn("豆包请求失败：${e.message}")
            ChatResult.Failure(Http.networkError(e))
        }
    }

    // ---------- 请求构造 ----------

    private fun queryString(cred: Cred): String = listOf(
        "aid" to "582478",
        "real_aid" to "582478",
        "channel" to "win",
        "client_platform" to "pc_client",
        "device_platform" to "web",
        "doubao_device_platform" to "desktop",
        "doubao_pc_version" to "2.30.1",
        "pc_version" to "2.30.1",
        "pkg_type" to "release_version",
        "language" to "zh",
        "region" to "CN",
        "sys_region" to "CN",
        "runtime" to "web",
        "runtime_version" to "3.37.4",
        "samantha_web" to "1",
        "use-olympus-account" to "1",
        "version_code" to "20800",
        "web_platform" to "desktop",
        "chromium_version" to "147.0.7727.149",
        "device_id" to cred.deviceId,
        "web_id" to cred.webId,
    ).joinToString("&") { (k, v) -> "$k=$v" }

    private fun headers(cred: Cred): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "text/event-stream")
        put("Cookie", cred.cookie)
        put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
        put("Referer", "https://www.doubao.com/chat/")
        put("Origin", "https://www.doubao.com")
        // CSRF 从 Cookie 里取，缺了会被上游直接拒绝
        cred.cookie.split(';').map { it.trim() }.firstOrNull {
            it.startsWith("passport_csrf_token=")
        }?.let { put("x-tt-passport-csrf-token", it.substringAfter('=')) }
    }

    private fun buildBody(cred: Cred, model: String, prompt: String): JsonObject {
        val modelKey = MODEL_MAP[model] ?: "9"
        val (memProfile, window) = WINDOW_OF[model] ?: (MEMORY_PROFILE to TOTAL_WINDOW)
        val msgId = UUID.randomUUID().toString()
        val localConvId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val textBlock = JsonObject().apply {
            addProperty("text", prompt)
            addProperty("icon_url", "")
            addProperty("icon_url_dark", "")
            addProperty("summary", "")
        }
        val content = JsonObject().apply {
            add("text_block", textBlock)
            addProperty("pc_event_block", "")
        }
        val block = JsonObject().apply {
            addProperty("block_type", 10000)
            add("content", content)
            addProperty("block_id", UUID.randomUUID().toString())
            addProperty("parent_id", "")
            add("meta_info", JsonArray())
            add("append_fields", JsonArray())
        }
        val message = JsonObject().apply {
            addProperty("local_message_id", msgId)
            add("content_block", JsonArray().apply { add(block) })
            addProperty("message_status", 0)
        }

        val localPermissions = JsonArray().apply {
            listOf(
                "ACCESS_COARSE_LOCATION",
                "ACCESS_FINE_LOCATION",
                "ACCESS_BACKGROUND_LOCATION",
            ).forEach { name ->
                add(JsonObject().apply {
                    addProperty("permission_name", name)
                    addProperty("status", 1)
                })
            }
        }
        val clientMeta = JsonObject().apply {
            addProperty("local_conversation_id", localConvId)
            addProperty("conversation_id", "")
            addProperty("bot_id", BOT_ID)
            addProperty("last_section_id", "")
            add("last_message_index", null)
            add("local_permissions", localPermissions)
        }

        val taskParam = JsonObject().apply {
            addProperty("runtime_type", 2)
            addProperty("sandbox_auth_type", 1)
            addProperty("device_name", cred.deviceId)
            addProperty("folder_name", "")
            addProperty("local_app_id", cred.localAppId)
            addProperty("local_device_id", cred.deviceId)
            addProperty("workspace", "")
        }
        val clientOpt = JsonObject().apply {
            addProperty("enable_sandbox", false)
            addProperty("os", "Windows")
            add("shared_folder_path", JsonArray())
            add("agent_workspace", JsonObject().apply {
                addProperty("agent_workspace", "")
                add("local_skill_paths", JsonArray())
            })
            addProperty("client_env_id", UUID.randomUUID().toString())
            addProperty("sandbox_id", "sandbox-" + UUID.randomUUID())
            addProperty("sandbox_auth_type", 1)
        }
        val taskInput = JsonObject().apply {
            add("agents_md", JsonObject().apply {
                add("files", JsonArray())
                addProperty("state", 2)
            })
            addProperty("schema_version", 1)
            addProperty("home_dir", "")
            add("project_context", JsonObject())
            add("localConnectors", JsonArray())
        }
        val generalTaskParam = JsonObject().apply {
            addProperty("action", 0)
            add("thread_local_message_id", JsonArray().apply { add(msgId) })
            add("client_option", clientOpt)
            addProperty("runtime_type", 2)
            add("agent_task_param", taskParam)
            add("agent_task_param_change", JsonObject().apply {
                addProperty("runtime_changed", false)
                addProperty("device_changed", false)
                addProperty("sandbox_auth_type_changed", false)
            })
            addProperty("need_modify_conversation", false)
            addProperty("task_input_json", taskInput.toString())
        }

        val conversationInitExt = JsonObject().apply {
            addProperty("model_item_key", modelKey)
            addProperty("reasoning_effort", REASONING_EFFORT)
            addProperty("mode_id", MODE_ID)
            addProperty("agent_task_param", taskParam.toString())
        }
        val modelConfig = JsonObject().apply {
            addProperty("model_item_key", modelKey)
            add("model_extra_params", JsonObject().apply {
                addProperty("memory_profile", memProfile)
                addProperty("total_window_size", window)
            })
            addProperty("reasoning_effort", REASONING_EFFORT.toIntOrNull() ?: 4)
        }
        val aggregateParams = JsonObject().apply {
            addProperty("mention_skill_list", "[]")
            addProperty("mention_plugin_list", "[]")
            addProperty("mention_ext", "[{}]")
            addProperty("conversation_mode", "1")
            addProperty("mode_id", MODE_ID)
            addProperty("model_item_key", modelKey)
            addProperty("agent_mode", "0")
            addProperty("reasoning_effort", REASONING_EFFORT)
            addProperty("provider_id", "")
        }
        val ext = JsonObject().apply {
            addProperty("general_task_param", generalTaskParam.toString())
            addProperty("use_deep_think", "10001")
            addProperty("agent_mode", "0")
            addProperty("is_finish", "1")
            addProperty("conversation_init_option", """{"need_ack_conversation":true}""")
            addProperty("commerce_credit_config_enable", "0")
        }
        val option = JsonObject().apply {
            addProperty("create_time_ms", now)
            addProperty("answer_with_suggest", false)
            addProperty("agent_mode", 0)
            addProperty("need_deep_think", 10001)
            addProperty("unique_key", UUID.randomUUID().toString())
            addProperty("start_seq", 0)
            addProperty("need_create_conversation", true)
            add("conversation_init_option", JsonObject().apply {
                addProperty("need_ack_conversation", true)
            })
            add("sse_recv_event_options", JsonObject().apply {
                addProperty("support_chunk_delta", true)
            })
            addProperty("support_lazy_fetch_stream", true)
            addProperty("is_old_user", true)
            addProperty("message_storage_type", 0)
            addProperty("conversation_mode", 1)
            add("conversation_init_ext", conversationInitExt)
            add("model_config", modelConfig)
            add("aggregate_params", aggregateParams)
            add("general_task_param", generalTaskParam)
            add("ext", ext)
        }

        return JsonObject().apply {
            add("client_meta", clientMeta)
            add("messages", JsonArray().apply { add(message) })
            add("option", option)
            add("user_context", JsonArray())
        }
    }

    /** 取出最后一条 user 消息的纯文本；豆包这条协议只吃单轮 prompt。 */
    private fun extractPrompt(body: JsonObject): String {
        val messages = body.getAsJsonArray("messages") ?: return ""
        var last = ""
        messages.forEach { el ->
            val o = runCatching { el.asJsonObject }.getOrNull() ?: return@forEach
            if (o.get("role")?.asString != "user") return@forEach
            val c = o.get("content")
            last = when {
                c == null -> ""
                c.isJsonPrimitive -> c.asString
                c.isJsonArray -> buildString {
                    c.asJsonArray.forEach { part ->
                        val p = runCatching { part.asJsonObject }.getOrNull()
                        p?.get("text")?.takeIf { it.isJsonPrimitive }?.asString?.let { append(it) }
                    }
                }
                else -> ""
            }
        }
        return last.trim()
    }

    // ---------- 响应解析 ----------

    private fun streamChat(
        url: String,
        hdrs: Map<String, String>,
        body: String,
        sink: SseSink,
    ): ChatResult {
        var events = 0
        var textLen = 0
        val state = ParseState()
        val r = Http.postStream(
            url = url,
            headers = hdrs,
            json = body,
            onLine = { line ->
                val parsed = parseLine(line.trim(), state)
                parsed?.let {
                    events++
                    textLen += it.length
                    sink.onData(it)
                }
                true
            },
            // 不能在这里就 sink.onDone()：它会把 done 置位，后续补发的 brief 兜底会被丢掉
            onDone = {},
        )
        if (!r.ok) Http.classify(r)?.let { return ChatResult.Failure(it) }
        // 上游 200 + STREAM_ERROR：必须在产出任何内容前就失败，让 Engine 换号重试
        state.upstreamError?.let { return ChatResult.Failure(it) }
        if (textLen == 0 && state.brief.isNotBlank()) {
            sink.onData(openAiDeltaChunk(state.brief))
        }
        // 一条数据都没产出就当成成功，客户端只会收到 [DONE] 且永远看不到错误，
        // 也就没法换号重试。必须按 EMPTY 失败上报。
        if (events == 0 && state.brief.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, 200, "豆包返回空内容"))
        }
        sink.onDone()
        return ChatResult.Streamed(events, 0, 0, 0)
    }

    private fun nonStreamChat(url: String, hdrs: Map<String, String>, body: String): ChatResult {
        val sb = StringBuilder()
        val state = ParseState()
        val r = Http.postStream(
            url = url,
            headers = hdrs,
            json = body,
            onLine = { line ->
                // 这里要的是纯文本，不能拿 parseLine（它返回的是 OpenAI chunk JSON）
                parseText(line.trim(), state)?.let { sb.append(it) }
                true
            },
            onDone = {},
        )
        if (!r.ok) Http.classify(r)?.let { return ChatResult.Failure(it) }
        state.upstreamError?.let { return ChatResult.Failure(it) }
        val text = sb.toString().ifBlank { state.brief }
        if (text.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, 200, "豆包返回空内容"))
        }
        val json = JsonParser.parseString(
            """{"id":"doubao-${UUID.randomUUID()}","object":"chat.completion",""" +
                """"choices":[{"index":0,"message":{"role":"assistant","content":${quote(text)}},""" +
                """"finish_reason":"stop"}],"usage":{"prompt_tokens":0,"completion_tokens":0,""" +
                """"total_tokens":0}}""",
        ).asJsonObject
        return ChatResult.Aggregated(json)
    }

    private class ParseState {
        /**
         * 上游用 HTTP 200 + STREAM_ERROR 事件报错（风控/限流），
         * 不接住就会被当成「正常但没内容」的空响应。
         */
        var upstreamError: ApiError? = null
        var brief: String = ""
        /** 一旦收到过 CHUNK_DELTA，就不再从 STREAM_CHUNK 取正文，避免重复。 */
        var sawChunkDelta: Boolean = false
        /** 当前事件名，来自 `event:` 行；data 行的解析要靠它分流。 */
        var event: String = ""
        /** 最近一条思考标题（仅记录，不进正文）。 */
        var reasoning: String = ""
        /** 已输出过的媒体，避免 patch 多次导致重复。 */
        val seenMedia: MutableSet<String> = HashSet()
    }

    /**
     * 解析一行 SSE，返回可直接下发给客户端的 OpenAI data 行；无内容返回 null。
     *
     * 上游是 `event: X` + `data: {...}` 的分块格式，这里按行独立处理
     * ——豆包的 data 不会跨行，不需要攒多行再拼。
     */
    /**
     * 只做「取正文文本」；流式与非流式共用，包装由调用方决定。
     *
     * 事件类型来自独立的 `event:` 行，必须跨行记住——只按 data 里的字段猜，
     * CHUNK_DELTA 和 STREAM_CHUNK 会分错流，正文大半被丢掉。
     */
    private fun parseText(line: String, state: ParseState): String? {
        val l = line.trim()
        if (l.startsWith("event:")) {
            state.event = l.removePrefix("event:").trim()
            return null
        }
        if (!l.startsWith("data:")) return null
        val payload = l.removePrefix("data:").trim()
        if (payload.isEmpty() || payload == "[DONE]") return null

        val root = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: return null
        val ev = state.event.ifEmpty {
            root.get("event")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        }

        // patch_op 在 data 根层；有些老格式把字段包在 event_data 里。
        // 必须优先看根层，否则带 event_data 的响应会把 patch_op 整个跳过。
        val body = if (root.has("patch_op")) root
        else root.getAsJsonObject("event_data") ?: root

        when (ev) {

            "STREAM_ERROR" -> {
                val code = runCatching {
                    body.get("error_code")?.takeIf { it.isJsonPrimitive }?.asInt
                }.getOrNull() ?: 0
                val msg = runCatching {
                    body.get("error_msg")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull() ?: "unknown"
                // 710022004/710022002 是字节风控：换号能绕开，所以按 UPSTREAM 处理让 Engine 换号
                state.upstreamError = ApiError(
                    ApiError.Kind.UPSTREAM,
                    502,
                    "豆包上游错误 $code：$msg",
                )
                return null
            }
            "SSE_REPLY_END" -> {
                val brief = runCatching {
                    root.getAsJsonObject("msg_finish_attr")
                        ?.get("brief")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull()
                if (!brief.isNullOrBlank()) state.brief = brief
                return null
            }
            "SSE_ACK" -> return null
            "CHUNK_DELTA" -> {
                val t = body.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                if (!t.isNullOrEmpty()) {
                    state.sawChunkDelta = true
                    return t
                }
                return null
            }
            "STREAM_CHUNK" -> {
                // 不能因为 sawChunkDelta 就整体跳过：日常模式会用 CHUNK_DELTA 发正文、
                // 同时用 STREAM_CHUNK 发图片（2074）和视频，整块跳过就把媒体丢了。
                // 去重只针对文本块，放在 textBlockOf 里判断。
                return textBlockOf(body, state)
            }
            else -> {
                // 没有 event 行（或事件名未知）：按字段结构兜底
                val t = body.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                if (!t.isNullOrEmpty()) {
                    state.sawChunkDelta = true
                    return t
                }
                return textBlockOf(body, state)
            }
        }
    }

    /** 从 STREAM_CHUNK 的 patch_op 里取 block_type=10000 的正文。 */
    private fun textBlockOf(body: JsonObject, state: ParseState): String? {
        val ops = runCatching { body.getAsJsonArray("patch_op") }.getOrNull() ?: return null
        for (op in ops) {
            val blocks = runCatching {
                op.asJsonObject.getAsJsonObject("patch_value")
                    ?.getAsJsonArray("content_block")
            }.getOrNull() ?: continue
            for (b in blocks) {
                val bo = runCatching { b.asJsonObject }.getOrNull() ?: continue
                val type = bo.get("block_type")?.takeIf { it.isJsonPrimitive }?.asInt ?: continue
                when (type) {
                    10000 -> {
                        // 已经有了 CHUNK_DELTA 的正文，这里再取就是重复
                        if (state.sawChunkDelta) continue
                        val t = runCatching {
                            bo.getAsJsonObject("content")
                                ?.getAsJsonObject("text_block")
                                ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                        }.getOrNull()
                        if (!t.isNullOrEmpty()) return t
                    }
                    10084 -> {
                        val md = mediaMarkdown(bo)
                        if (md.isNotEmpty()) return md
                    }
                    2074 -> {
                        val md = creationMarkdown(bo, state)
                        if (md.isNotEmpty()) return md
                    }
                    10040 -> {
                        // 思考过程：只记录标题，不回传给客户端正文
                        val t = runCatching {
                            val tb = bo.getAsJsonObject("content")
                                ?.getAsJsonObject("thinking_block")
                            val s1 = tb?.get("streaming_title")?.takeIf { it.isJsonPrimitive }?.asString
                            val s2 = tb?.get("finish_title")?.takeIf { it.isJsonPrimitive }?.asString
                            if (!s1.isNullOrEmpty()) s1 else s2
                        }.getOrNull()
                        if (!t.isNullOrEmpty()) state.reasoning = t
                    }
                }
            }
        }
        return null
    }

    /**
     * 富媒体块（block_type=10084）→ Markdown。
     *
     * 豆包生成图片/视频后不再走正文流，而是发一个 rich_media_layout_block，
     * 里面按尺寸给了四套 URL（thumb / preview / ori / ori_raw），取 ori 作原图。
     * 客户端能渲染 Markdown 图片就能看到，不能的话至少 URL 是完整的。
     */
    private fun mediaMarkdown(block: JsonObject): String {
        val media = runCatching {
            block.getAsJsonObject("content")
                ?.getAsJsonObject("rich_media_layout_block")
                ?.getAsJsonArray("media")
        }.getOrNull() ?: return ""

        val sb = StringBuilder()
        for (el in media) {
            val creation = runCatching { el.asJsonObject.getAsJsonObject("creation") }.getOrNull()
                ?: continue

            creation.getAsJsonObject("image")?.let { img ->
                val url = runCatching {
                    img.getAsJsonObject("image_ori")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: img.getAsJsonObject("image_preview")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: img.getAsJsonObject("image_thumb")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull()
                if (!url.isNullOrBlank()) sb.append("\n\n![图片](").append(MediaProxy.localize(url) ?: MediaProxy.wrap(url)).append(")\n\n")
            }

            creation.getAsJsonObject("video")?.let { v ->
                val url = runCatching {
                    v.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: v.getAsJsonObject("play_addr")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull()
                if (!url.isNullOrBlank()) sb.append("\n\n[视频](").append(MediaProxy.localize(url) ?: MediaProxy.wrap(url)).append(")")
            }
        }
        return sb.toString()
    }

    /**
     * 日常模式的创作块（block_type=2074）→ Markdown。
     *
     * 与工作模式的 10084 不同：这里一次回多张（`creations` 数组），
     * 同一块会被 patch 多次——先是只有 placeholder 的占位，后一次才带真实 URL。
     * 用 block_id + creation.id 去重，否则正文里会重复冒出同一张图。
     */
    private fun creationMarkdown(block: JsonObject, state: ParseState): String {
        val blockId = block.get("block_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val creations = runCatching {
            block.getAsJsonObject("content")
                ?.getAsJsonObject("creation_block")
                ?.getAsJsonArray("creations")
        }.getOrNull() ?: return ""

        val sb = StringBuilder()
        for (c in creations) {
            val obj = runCatching { c.asJsonObject }.getOrNull() ?: continue
            val key = blockId + "/" + (obj.get("id")?.takeIf { it.isJsonPrimitive }?.asString ?: "")

            obj.getAsJsonObject("image")?.let { img ->
                // 占位阶段没有 image_ori，跳过；等 patch 到真实 URL 再输出
                val url = runCatching {
                    img.getAsJsonObject("image_ori")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: img.getAsJsonObject("image_preview")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: img.getAsJsonObject("image_thumb")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull()
                if (!url.isNullOrBlank() && state.seenMedia.add(key + "/img")) {
                    sb.append("\n\n![图片](").append(MediaProxy.localize(url) ?: MediaProxy.wrap(url)).append(")\n\n")
                }
            }

            obj.getAsJsonObject("video")?.let { v ->
                val url = runCatching {
                    v.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                        ?: v.getAsJsonObject("play_addr")?.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                }.getOrNull()
                if (!url.isNullOrBlank() && state.seenMedia.add(key + "/vid")) {
                    sb.append("\n\n[视频](").append(MediaProxy.localize(url) ?: MediaProxy.wrap(url)).append(")")
                }
            }
        }
        return sb.toString()
    }

    /** 流式路径：文本包成 OpenAI chunk。 */
    private fun parseLine(line: String, state: ParseState): String? =
        parseText(line, state)?.let { openAiDeltaChunk(it) }

    private fun openAiDeltaChunk(text: String): String =
        """{"id":"doubao-${UUID.randomUUID()}","object":"chat.completion.chunk",""" +
            """"choices":[{"index":0,"delta":{"content":${quote(text)}},"finish_reason":null}]}"""

    private fun quote(s: String): String =
        com.google.gson.JsonPrimitive(s).toString()

    // ---------- 账号运营 ----------

    /**
     * 豆包没有传统签到，额度按天/按月自动派发。
     */
    override fun checkin(account: ProviderAccount): Result<CheckinResult> =
        Result.success(CheckinResult.Skipped)

    /**
     * 查当月额度。
     *
     * 接口是 `/alice/commerce/sale/subscription/quota/summary/`，额度在
     * `data.window_limit_section.window_limit_groups[].window_limits[]`，
     * 字段是带千分位的字符串（"2,142"），要先去逗号再解析。
     */
    override fun balance(account: ProviderAccount): Result<BalanceInfo> {
        val cred = parseCred(account) ?: return Result.failure(IllegalStateException("缺少 Cookie"))
        val url = "$QUOTA_URL?" + queryString(cred)
        val sb = StringBuilder()
        val r = Http.postStream(
            url = url,
            headers = headers(cred),
            json = "{}",
            onLine = { line -> sb.append(line); true },
            onDone = {},
        )
        if (!r.ok) return Result.failure(IllegalStateException("额度接口 ${r.status}"))
        val root = runCatching { JsonParser.parseString(sb.toString()).asJsonObject }.getOrNull()
            ?: return Result.failure(IllegalStateException("额度响应不是 JSON"))
        val data = root.getAsJsonObject("data") ?: return Result.failure(IllegalStateException("缺少 data"))
        val groups = data.getAsJsonObject("window_limit_section")
            ?.getAsJsonArray("window_limit_groups") ?: return Result.success(BalanceInfo(remain = 0.0))
        var remain = 0.0
        for (g in groups) {
            val limits = g.asJsonObject.getAsJsonArray("window_limits") ?: continue
            for (w in limits) {
                val o = w.asJsonObject
                val total = number(o, "total_amount")
                val used = number(o, "used_amount")
                remain += (total - used).coerceAtLeast(0.0)
            }
        }
        return Result.success(BalanceInfo(remain = remain))
    }

    private fun number(o: com.google.gson.JsonObject, key: String): Double {
        val raw = runCatching {
            o.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        }.getOrNull() ?: return 0.0
        return raw.replace(",", "").toDoubleOrNull() ?: 0.0
    }
}
