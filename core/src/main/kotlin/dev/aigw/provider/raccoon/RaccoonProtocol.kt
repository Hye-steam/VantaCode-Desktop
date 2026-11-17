package dev.aigw.provider.raccoon

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.OpenAiChunks
import dev.aigw.util.arrayOrNull
import dev.aigw.util.firstLong
import dev.aigw.util.firstString
import dev.aigw.util.longOrNull
import dev.aigw.util.newCompletionId
import dev.aigw.util.objOrNull
import dev.aigw.util.stringOrNull

/**
 * 商汤「小浣熊」（xiaohuanxiong.com）协议层。
 *
 * 对话端点字段名接近 OpenAI，但真正的 OpenAI 结构被包在 `data` 里
 * （外层是 `{"status":{…},"data":{…}}`），且上游**拒绝非流式**——请求必须
 * `stream:true`，非流式由网关自己聚合。
 */
internal object RaccoonProtocol {

    const val HOST = "https://xiaohuanxiong.com"
    const val APP_NAME = "Raccoon"

    const val PATH_LOGIN = "/code/authorize"
    const val PATH_LOGIN_WITH_CODE = "/api/web/auth/v1/login_with_authorization_code"
    const val PATH_USER_INFO = "/api/web/auth/v1/user_info"
    const val PATH_REFRESH = "/api/web/auth/v1/refresh"
    const val PATH_CHAT = "/api/web/llm/v2/chat/completions"
    const val PATH_CHAT_V1 = "/api/web/llm/v1/chat/completions"
    const val PATH_MODELS = "/api/web/llm/v2/model_catalog"
    const val PATH_BALANCE = "/api/web/points/v1/balance"
    const val PATH_BILLS = "/api/web/points/v1/bills"
    const val PATH_SETTING_INFO = "/api/web/office/v3/setting_info"

    /** 桌面客户端标识：上游靠它判定「调用方是官方桌面端」，缺了不发登录奖励。 */
    const val CLIENT_PLATFORM = "desktop-windows"
    const val CLIENT_VERSION = "v1.0.0"
    const val CLIENT_UA = "office-raccoon/1.0.0"

    /** 官方 Web 前端固定用这个停止符。 */
    const val DEFAULT_STOP = "<|endofmessage|>"

    const val DEFAULT_MODEL = "raccoon-chat-ml-5-5"

    const val CONNECT_TIMEOUT_MS = 30_000
    const val READ_TIMEOUT_MS = 300_000

    const val POINTS_BILLS_LIMIT = 20

    private val BALANCE_POINT_KEYS =
        listOf("available_points", "availablePoints", "points", "balance", "credits")

    // ------------------------------------------------------------------ 凭证

    data class Credential(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val userId: String,
        val nickname: String,
        val orgCode: String,
    ) {
        fun toJson(): String = JsonObject().apply {
            addProperty("accessToken", accessToken)
            addProperty("refreshToken", refreshToken)
            addProperty("expiresAt", expiresAt)
            addProperty("userId", userId)
            addProperty("nickname", nickname)
            if (orgCode.isNotEmpty()) addProperty("orgCode", orgCode)
        }.toString()

        companion object {
            fun parse(secret: String): Credential? {
                val obj = runCatching { JsonParser.parseString(secret).asJsonObject }.getOrNull() ?: return null
                val access = obj.firstString("accessToken", "access_token")
                if (access.isEmpty()) return null
                return Credential(
                    accessToken = access,
                    refreshToken = obj.firstString("refreshToken", "refresh_token"),
                    expiresAt = obj.firstLong("expiresAt", "expires_at") ?: 0L,
                    userId = obj.firstString("userId", "user_id", "uid"),
                    nickname = obj.firstString("nickname", "nickName", "name"),
                    orgCode = obj.firstString("orgCode", "org_code"),
                )
            }
        }
    }

    // ------------------------------------------------------------------ 请求体改写

    /**
     * 标准 OpenAI 请求体 → 小浣熊上游格式。
     *
     * - 字段名保持 OpenAI 标准（v2 是 LiteLLM 兼容层，改名会被 500 拒绝）；
     * - `max_completion_tokens` 按同义映射到 `max_tokens`，否则上游静默丢弃；
     * - **强制 stream:true**（上游拒绝非流式）；
     * - `tools` 原样映射并补 `tool_choice:"auto"`。
     */
    fun prepareBody(src: JsonObject): JsonObject = JsonObject().apply {
        src.stringOrNull("model")?.let { addProperty("model", it) }
        src.arrayOrNull("messages")?.let { add("messages", it) }
        (src.longOrNull("max_tokens") ?: src.longOrNull("max_completion_tokens"))
            ?.let { addProperty("max_tokens", it) }
        src.doubleOrNull("temperature")?.let { addProperty("temperature", it) }
        src.doubleOrNull("top_p")?.let { addProperty("top_p", it) }
        src.doubleOrNull("frequency_penalty")?.let { addProperty("frequency_penalty", it) }
        src.doubleOrNull("presence_penalty")?.let { addProperty("presence_penalty", it) }
        addProperty("n", 1)
        addProperty("stream", true)
        val stop = src.get("stop")?.takeIf { !it.isJsonNull }
        if (stop != null) add("stop", stop) else addProperty("stop", DEFAULT_STOP)
        src.arrayOrNull("tools")?.let { tools ->
            add("tools", tools)
            addProperty("tool_choice", "auto")
        }
    }

    private fun JsonObject.doubleOrNull(name: String): Double? =
        get(name)?.takeIf { it.isJsonPrimitive }?.asDouble

    // ------------------------------------------------------------------ SSE 翻译

    /**
     * 上游 SSE（`data: {"status":{…},"data":{…}}`，以 `data: [DONE]` 结束，另有 `: ping` 心跳）
     * 逐行翻译成标准 OpenAI chunk。
     */
    class SseTranslator(
        private val model: String,
        private val completionId: String = newCompletionId(),
    ) {
        private var done = false
        private var frameId = completionId

        fun translate(rawLine: String): List<String> {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith(":")) return emptyList()
            // 上游有时不回 SSE，而是整段 JSON（或包着 data 的 JSON）：裸 JSON 行也当负载处理
            val payload = when {
                line.startsWith("data:") -> line.removePrefix("data:").trim()
                line.startsWith("{") -> line
                else -> return emptyList()
            }
            if (payload.isEmpty()) return emptyList()
            if (payload == "[DONE]") {
                done = true
                return listOf("[DONE]")
            }
            val envelope = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
                ?: return emptyList()
            val status = envelope.objOrNull("status")
            val code = status?.longOrNull("code") ?: 0L
            if (code != 0L) {
                done = true
                val message = status?.stringOrNull("message").orEmpty().ifEmpty { "上游流内错误（code=$code）" }
                return listOf(OpenAiChunks.errorFrame(message), "[DONE]")
            }
            val data = unwrapData(envelope)
            data.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { frameId = it }
            val choices = data.arrayOrNull("choices") ?: return emptyList()
            val out = ArrayList<String>(1)
            for (element in choices) {
                val choice = element as? JsonObject ?: continue
                // web 通道的 delta 是字符串，OpenAI 风格是对象，两者都兼容
                val deltaElement = choice.get("delta")
                val delta: JsonObject = when {
                    deltaElement == null || deltaElement.isJsonNull -> JsonObject()
                    deltaElement.isJsonObject -> deltaElement.asJsonObject
                    deltaElement.isJsonPrimitive -> JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", deltaElement.asString)
                    }

                    else -> JsonObject()
                }
                val finishReason = choice.stringOrNull("finish_reason")
                if (delta.size() == 0 && finishReason == null) continue
                out.add(chunk(delta, finishReason))
            }
            return out
        }

        fun close(): List<String> = if (done) emptyList() else listOf("[DONE]")

        private fun chunk(delta: JsonObject, finishReason: String?): String =
            OpenAiChunks.chunk(frameId, model, delta, finishReason?.ifBlank { null })
    }

    // ------------------------------------------------------------------ JSON 取值

    /**
     * 拆外层包裹：`data` 可能是对象，也可能是被二次编码的 JSON 字符串。
     * 拿不到对象时退回整包，让调用方按自己的字段名继续找。
     */
    fun unwrapData(envelope: JsonObject): JsonObject {
        val raw = envelope.get("data")
        return when {
            raw == null || raw.isJsonNull -> envelope
            raw.isJsonObject -> raw.asJsonObject
            raw.isJsonPrimitive && raw.asString.trim().startsWith("{") ->
                runCatching { JsonParser.parseString(raw.asString).asJsonObject }.getOrNull() ?: envelope
            else -> envelope
        }
    }

    fun envelopeData(body: String): JsonObject? =
        runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()?.objOrNull("data")

    fun extractMessage(body: String): String {
        if (body.isEmpty()) return ""
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return ""
        obj.objOrNull("error")?.stringOrNull("message")?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.objOrNull("status")?.stringOrNull("message")?.takeIf { it.isNotEmpty() }?.let { return it }
        for (key in listOf("message", "msg", "details", "detail")) {
            obj.stringOrNull(key)?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }

    /** 在任意嵌套层级找「最像模型列表」的数组（上游目录结构不稳定）。 */
    fun findModelArray(root: JsonElement): JsonArray? {
        val candidates = ArrayList<JsonArray>()
        fun idOf(obj: JsonObject): String =
            obj.firstString("id", "model", "model_id", "modelId", "name", "code")

        fun walk(element: JsonElement) {
            when {
                element.isJsonArray -> {
                    val arr = element.asJsonArray
                    val looksLikeModels = arr.size() > 0 &&
                        arr.count { it.isJsonObject && idOf(it.asJsonObject).isNotEmpty() } >= arr.size() / 2 + 1
                    if (looksLikeModels) candidates.add(arr)
                    arr.forEach { walk(it) }
                }

                element.isJsonObject -> element.asJsonObject.entrySet().forEach { walk(it.value) }
            }
        }
        walk(root)
        return candidates.maxByOrNull { it.size() }
    }

    fun balancePoints(data: JsonObject): Long? =
        BALANCE_POINT_KEYS.firstNotNullOfOrNull { data.longOrNull(it) }
}
