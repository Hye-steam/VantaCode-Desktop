package dev.aigw.provider.mimo

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Mimo（小米 AI Studio，aistudio.xiaomimimo.com）协议层。
 *
 * 上游是自定义 SSE（`event:` + `data:`），且只接受**一条拍平的 query 字符串**，
 * 没有 messages 数组、没有原生 function calling。所以两件事必须在这里做：
 *
 * 1. 输入：把 OpenAI messages 拍平成 query 文本；工具调用序列化成
 *    `<|CHAT2API|tool_calls>` 托管协议块，工具结果序列化成
 *    `<|CHAT2API|tool_result …>`，模型据此继续多轮。
 * 2. 输出：把 SSE 的 message/reasoningContent 事件转成 OpenAI chunk，
 *    把正文里的工具调用块解析成 `tool_calls`。
 */
internal object MimoProtocol {

    /** 协议与真实模型映射：旧模型名已被官方下线并重定向到 v2.6 系列。 */
    val MODEL_MAP: Map<String, String> = linkedMapOf(
        "MiMo-V2.6-Flash" to "mimo-v2.6-flash",
        "MiMo-V2.6-Pro" to "mimo-v2.6-pro",
        "MiMo-V2.6-Pro-UltraSpeed" to "mimo-v2.6-pro-ultraspeed-studio",
    )

    /** 小写别名，兼容直接传真实模型名。 */
    private val ALIAS: Map<String, String> = MODEL_MAP.entries.associate { (openAi, real) ->
        openAi.lowercase() to real
    } + mapOf(
        "mimo-v2.6-flash" to "mimo-v2.6-flash",
        "mimo-v2.6-pro" to "mimo-v2.6-pro",
        "mimo-v2.6-pro-ultraspeed" to "mimo-v2.6-pro-ultraspeed-studio",
        "mimo-v2.5-pro" to "mimo-v2.6-pro",
        "mimo-v2.5" to "mimo-v2.6-flash",
        "mimo-v2-flash" to "mimo-v2.6-flash",
    )

    fun resolveModel(requested: String): String {
        val name = requested.trim().substringAfter('/').trim()
        MODEL_MAP[name]?.let { return it }
        return ALIAS[name.lowercase()] ?: "mimo-v2.6-flash"
    }

    /** 是否思考模型（决定 enableThinking）。 */
    fun wantsThinking(requested: String): Boolean {
        val m = requested.lowercase()
        return m.contains("think") || m.contains("pro") || m.contains("r1")
    }

    // ------------------------------------------------------------------ 凭证

    data class Credentials(val serviceToken: String, val userId: String, val phToken: String) {
        val isValid: Boolean get() = serviceToken.isNotBlank() && userId.isNotBlank() && phToken.isNotBlank()

        fun cookieHeader(): String =
            "serviceToken=$serviceToken; userId=$userId; xiaomichatbot_ph=$phToken"

        fun toJson(): String = JsonObject().apply {
            addProperty("serviceToken", serviceToken)
            addProperty("userId", userId)
            addProperty("xiaomichatbot_ph", phToken)
        }.toString()

        companion object {
            /** 去掉值外层的双引号/单引号：浏览器复制的 Cookie 常带 `"xxx"`。 */
            private fun unquote(v: String): String =
                v.trim().removeSurrounding("\"").removeSurrounding("'").trim()

            /**
             * 解析用户粘贴的 Cookie。支持 `k=v; k=v`、换行/& 分隔、以及 JSON。
             *
             * 键名兼容：serviceToken / service_token / xiaomichatbot_serviceToken，
             * userId / user_id，xiaomichatbot_ph / ph_token / phToken（自身落盘格式）。
             * 值可能被引号包裹，统一剥掉再判空。
             */
            fun parse(raw: String): Credentials? {
                val text = raw.trim()
                if (text.isEmpty()) return null
                if (text.startsWith("{") || text.startsWith("[")) {
                    val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                        ?: return null
                    val st = obj.string("serviceToken") ?: obj.string("service_token")
                        ?: obj.string("xiaomichatbot_serviceToken")
                    val uid = obj.string("userId") ?: obj.string("user_id")
                    val ph = obj.string("xiaomichatbot_ph") ?: obj.string("ph_token") ?: obj.string("phToken")
                    return Credentials(
                        unquote(st.orEmpty()), unquote(uid.orEmpty()), unquote(ph.orEmpty()),
                    ).takeIf { it.isValid }
                }
                val pairs: Map<String, String> = text.split(Regex("[;\n&]")).mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) null else part.substring(0, idx).trim() to unquote(part.substring(idx + 1))
                }.toMap()
                val st = pairs["serviceToken"] ?: pairs["service_token"] ?: pairs["xiaomichatbot_serviceToken"]
                val uid = pairs["userId"] ?: pairs["user_id"]
                val ph = pairs["xiaomichatbot_ph"] ?: pairs["ph_token"] ?: pairs["xiaomichatbot_ph_token"]
                    ?: pairs["phToken"]
                return Credentials(st.orEmpty(), uid.orEmpty(), ph.orEmpty()).takeIf { it.isValid }
            }
        }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull && it.isJsonPrimitive }?.asString

    // ------------------------------------------------------------------ 输入

    private class Msg(
        val role: String,
        val text: String,
        val toolCalls: List<Pair<String, String>> = emptyList(),
        val toolCallId: String? = null,
    )

    /** 把 OpenAI messages 拍平：单条 user 原样返回，其余加角色前缀。 */
    fun buildQuery(messages: JsonArray, tools: JsonArray? = null): String {
        val parsed = messages.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val role = obj.get("role")?.asString ?: return@mapNotNull null
            val calls = obj.getAsJsonArray("tool_calls")?.mapNotNull { tc ->
                val t = tc.asJsonObject
                val fn = t.getAsJsonObject("function") ?: return@mapNotNull null
                val name = fn.get("name")?.asString ?: return@mapNotNull null
                name to (fn.get("arguments")?.asString ?: "{}")
            }.orEmpty()
            Msg(
                role = role,
                text = textOf(obj.get("content")),
                toolCalls = calls,
                toolCallId = obj.get("tool_call_id")?.asString,
            )
        }

        val toolPrompt = if (tools != null && tools.size() > 0) renderToolPrompt(tools) else ""

        val entries = mutableListOf<Pair<String, String>>()
        for (m in parsed) {
            if (m.role == "assistant" && m.toolCalls.isNotEmpty()) {
                entries += "Assistant" to renderToolCalls(m.toolCalls)
                continue
            }
            if (m.role == "tool" && m.toolCallId != null) {
                entries += "User" to renderToolResult(m.toolCallId, m.text)
                continue
            }
            if (m.text.isBlank()) continue
            val label = when (m.role) {
                "system" -> "System"
                "assistant" -> "Assistant"
                else -> "User"
            }
            entries += label to m.text.trim()
        }

        if (entries.size == 1 && entries[0].first == "User") {
            val only = entries[0].second
            return if (toolPrompt.isEmpty()) only else toolPrompt + "\n\n" + only
        }
        val joined = entries.joinToString("\n\n") { "${it.first}: ${it.second}" }
        return if (toolPrompt.isEmpty()) joined else toolPrompt + "\n\n" + joined
    }

    /**
     * 工具清单注入提示：MiMo 网页端没有原生 function calling，模型必须被告知
     * 可用工具与调用格式，否则会直接回复“我无法读取本地文件”。
     */
    private fun renderToolPrompt(tools: JsonArray): String {
        val schemas = tools.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val fn = obj.getAsJsonObject("function") ?: obj
            val name = fn.get("name")?.asString ?: return@mapNotNull null
            val desc = fn.get("description")?.asString.orEmpty()
            val params = fn.get("parameters")
            buildString {
                append("- ").append(name)
                if (desc.isNotEmpty()) append("：").append(desc)
                if (params != null) append("\n  参数 Schema：").append(params.toString())
            }
        }.joinToString("\n")
        if (schemas.isEmpty()) return ""
        return """
            ## 可用工具
            你必须使用下列工具完成任务，不要声称没有权限。调用时只输出一个工具调用块：
            <|CHAT2API|tool_calls><|CHAT2API|invoke name="工具名"><|CHAT2API|parameter name="参数名"><![CDATA[参数值]]></|CHAT2API|parameter></|CHAT2API|invoke></|CHAT2API|tool_calls>
            规则：
            1. 任务未完成时只输出工具调用块，不要输出任何解释。
            2. 工具执行结果会以 User 消息回给你；收到后立即调用下一个所需工具。
            3. 只有任务全部完成后才输出普通文字总结。
            4. 工具调用失败无副作用，不要道歉，换个正确工具重试。
            $schemas
        """.trimIndent()
    }

    fun textOf(content: JsonElement?): String {
        if (content == null || content.isJsonNull) return ""
        if (content.isJsonPrimitive) return content.asString
        if (content.isJsonArray) {
            return content.asJsonArray.joinToString("\n") { item ->
                val obj = item as? JsonObject ?: return@joinToString ""
                if (obj.get("type")?.asString != "text") return@joinToString ""
                obj.get("text")?.asString.orEmpty()
            }.trim()
        }
        return ""
    }

    private fun renderToolCalls(calls: List<Pair<String, String>>): String {
        val sb = StringBuilder("<|CHAT2API|tool_calls>")
        for ((name, args) in calls) {
            sb.append("<|CHAT2API|invoke name=\"").append(name).append("\">")
            val obj = runCatching { JsonParser.parseString(args).asJsonObject }.getOrNull()
            obj?.entrySet()?.forEach { (key, value) ->
                val v = if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    value.asString
                } else {
                    value.toString()
                }
                sb.append("<|CHAT2API|parameter name=\"").append(key).append("\">")
                    .append("<![CDATA[").append(v).append("]]>")
                    .append("</|CHAT2API|parameter>")
            }
            sb.append("</|CHAT2API|invoke>")
        }
        sb.append("</|CHAT2API|tool_calls>")
        return sb.toString()
    }

    private fun renderToolResult(toolCallId: String, content: String): String =
        "<|CHAT2API|tool_result tool_call_id=\"$toolCallId\">$content</|CHAT2API|tool_result>"

    // ------------------------------------------------------------------ 输出

    class Call(val id: String, val name: String, val arguments: String)

    private const val MARKER_OPEN = "<|CHAT2API|tool_calls>"

    private val PARAM_RE = Regex(
        "<\\|CHAT2API\\|parameter name=\"([^\"]+)\">(.*?)</\\|CHAT2API\\|parameter>",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun hasToolCall(text: String): Boolean =
        text.contains(MARKER_OPEN) || text.contains("<function_calls>")

    /** 从正文里拆出工具调用（兼容 CHAT2API 托管格式与 function_calls 格式）。 */
    fun parseToolCalls(text: String): List<Call> {
        val out = mutableListOf<Call>()
        val invokeRe = Regex(
            "<\\|CHAT2API\\|invoke name=\"([^\"]+)\">(.*?)</\\|CHAT2API\\|invoke>",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        for (m in invokeRe.findAll(text)) {
            out += Call(newId(), m.groupValues[1], parseArgs(m.groupValues[2]))
        }
        if (out.isNotEmpty()) return out

        // 兜底：<function_calls><invoke name="…">…</invoke></function_calls>
        val legacy = Regex(
            "<invoke name=\"([^\"]+)\">(.*?)</invoke>",
            setOf(RegexOption.DOT_MATCHES_ALL),
        )
        if (text.contains("<function_calls>")) {
            for (m in legacy.findAll(text)) {
                out += Call(newId(), m.groupValues[1], parseArgs(m.groupValues[2]))
            }
        }
        return out
    }

    private fun parseArgs(inner: String): String {
        val obj = JsonObject()
        for (m in PARAM_RE.findAll(inner)) {
            val key = m.groupValues[1]
            val raw = m.groupValues[2].removeSurrounding("<![CDATA[", "]]>").trim()
            val parsed = runCatching { JsonParser.parseString(raw) }.getOrNull()
            if (parsed == null) obj.addProperty(key, raw) else obj.add(key, parsed)
        }
        return obj.toString()
    }

    fun newId(): String = "call_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12)

    /** 去掉正文里的工具调用块，只把可读文本回给客户端。 */
    fun stripToolBlocks(text: String): String = text
        .replace(Regex("<\\|CHAT2API\\|[\\s\\S]*?\\|tool_calls>"), "")
        .replace(MARKER_OPEN, "")
        .replace("</|CHAT2API|tool_calls>", "")
        .replace(Regex("<\\|CHAT2API\\|invoke name=\"[^\"]+\">"), "")
        .replace("</|CHAT2API|invoke>", "")
        .replace(Regex("<\\|CHAT2API\\|parameter name=\"[^\"]+\">"), "")
        .replace("</|CHAT2API|parameter>", "")
        .replace("<![CDATA[", "")
        .replace("]]>", "")
        // 协议标记被流式切碎后可能残留的碎片
        .replace(Regex("\\|tool_calls>?"), "")
        .replace(Regex("\\|invoke>"), "")
        .replace(Regex("\\|parameter>"), "")
        .replace(Regex("<\\|?CHAT2API\\|?"), "")
        .trim()

    /** 清洗引用标注（Mimo 联网结果会带 citation:N 标记）。 */
    fun stripCitations(text: String): String = text
        .replace(Regex("从\\(citation:\\d+\\)中[：:]\\s*"), "")
        .replace(Regex("-?\\s*citation:\\d+[：:]\\s*"), "")
        .replace(Regex("[（(]\\s*citation:\\d+(?:,\\s*citation:\\d+)*\\s*[）)]"), "")
        .replace(Regex("citation:\\d+(?:,\\s*citation:\\d+)*"), "")
        .replace(Regex("\\(citation:\\d+\\)"), "")
        .replace(Regex("\\[\\d+]"), "")

    /** 去掉 think 标签（思考走 reasoning_content，正文不保留）。 */
    fun stripThink(text: String): String {
        var t = text.replace("\u0000", "")
        t = t.replace(Regex("<think[^>]*>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        val idx = t.indexOf("<think")
        if (idx != -1) t = t.substring(0, idx)
        return t.trimStart()
    }

    /** 流式 SSE 行解析结果。 */
    sealed interface Event {
        data class Text(val content: String) : Event
        data class Reasoning(val content: String) : Event
        data class Usage(val input: Int, val output: Int) : Event
        data object Finish : Event
        data object Ignore : Event
    }

    /** 逐行解析器：`event:` 行会记住，供后续 `data:` 行判断类型。每次请求建一个。 */
    class LineParser {
        private var currentEvent = ""

        fun feed(raw: String): Event {
            val line = raw.trim()
            if (line.isEmpty()) return Event.Ignore
            if (line.startsWith("event:")) {
                currentEvent = line.removePrefix("event:").trim()
                return if (currentEvent == "finish") Event.Finish else Event.Ignore
            }
            if (!line.startsWith("data:")) return Event.Ignore
            val payload = line.removePrefix("data:").trim()
            if (payload.isEmpty()) return Event.Ignore
            if (payload == "[DONE]") return Event.Finish
            val obj = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
                ?: return Event.Ignore
            return when (currentEvent) {
                "message" -> {
                    val reasoning = obj.get("reasoningContent")?.takeIf { !it.isJsonNull }?.asString
                    val content = obj.get("content")?.takeIf { !it.isJsonNull }?.asString
                    when {
                        !reasoning.isNullOrEmpty() -> Event.Reasoning(reasoning)
                        !content.isNullOrEmpty() -> Event.Text(content)
                        else -> Event.Ignore
                    }
                }
                "text" -> {
                    val content = obj.get("content")?.takeIf { !it.isJsonNull }?.asString
                    if (content.isNullOrEmpty()) Event.Ignore else Event.Text(content)
                }
                "usage" -> {
                    val u = obj.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject
                    Event.Usage(
                        input = u?.get("promptTokens")?.asInt ?: 0,
                        output = u?.get("completionTokens")?.asInt ?: 0,
                    )
                }
                "finish" -> Event.Finish
                else -> Event.Ignore
            }
        }
    }

    val gson: Gson = Gson()
}
