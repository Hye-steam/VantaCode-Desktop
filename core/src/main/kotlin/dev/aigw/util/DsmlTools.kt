package dev.aigw.util

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.Normalizer

/**
 * DSML 软工具调用：把 OpenAI `tools` 渲染成提示词模板，并把上游吐出的 DSML
 * `<｜DSML｜invoke …>` 文本解析回 OpenAI `tool_calls`。
 *
 * 上游网页版接口不认 OpenAI `tools` 字段，模型只会把调用标记当文本吐出来；
 * 网关必须在两端做转换，否则客户端（如 AiCode）看不到 tool_calls、不会执行工具。
 * DeepSeek 与智谱清言共用同一套 DSML 协议（GLM 实测完全遵守该模板）。
 *
 * 已知坑（都来自真实抓包，改动前先看这里）：
 * 1. 块名不固定：tool_calls / calls / function_calls，甚至没有名字；开始与结束块名
 *    还会不一致（见过 tool_calls 开头、calls 结尾）。所以【不要按固定块名匹配】，
 *    直接扫 `invoke` 块、其余 DSML 标签全部剥掉。
 * 2. 字符不规整：全角/半角尖括号与竖线、竖线重复、标记内空格；全角字母「ＤＳＭＬ」、
 *    零宽字符（\u200B 等）混在标记里——渲染一样、字节不同，正则直接匹配会漏。
 *    必须先用 normalize() 做 NFKC + 去零宽。
 * 3. 开始标签会泄成正文：流式下开始标签 `<…invoke name="…">` 含引号/等号，
 *    prefixLength 的「像标记」判断认不出，会提前发出去；因此遇到需规范化的字符
 *    要立刻切到整段缓冲。
 * 4. 工具调用可能写在思维链（reasoning）里，调用方应同时挂正文与思维链两个提取器。
 * 5. 参数值可能包 CDATA；`string="false"` 时值是 JSON。
 * 6. 网页通道偶尔把标记转义成 HTML 实体（&lt; … &gt;）。
 *
 * 解析策略：不猜块名，直接扫描 `<…DSML…invoke name="…">…</…invoke>` 块，
 * 其余 `<…DSML…>` 包装标签一律剥掉；其余文本原样保留。
 */
object DsmlTools {

    class Call(val name: String, val arguments: String)

    private const val BARS = "[｜|]+"
    private const val LT = "[<＜]"
    private const val GT = "[>＞]"
    private const val SLASH = "[/／]"
    private const val SP = "[ \\t\\u3000]*"

    /** 任意 DSML 标签（开或闭，不限块名）。 */
    private val TAG_RE = Regex(
        "$LT$SP$SLASH?$BARS${SP}DSML${SP}$BARS${SP}[^>]*$GT",
        RegexOption.IGNORE_CASE,
    )
    private val INVOKE_RE = Regex(
        "$LT$SP$BARS${SP}DSML${SP}$BARS${SP}invoke${SP}name${SP}=${SP}\"([^\"]*)\"${SP}$GT",
        RegexOption.IGNORE_CASE,
    )
    private val INVOKE_END_RE = Regex(
        "$LT$SP$SLASH$SP$BARS${SP}DSML${SP}$BARS${SP}invoke${SP}$GT",
        RegexOption.IGNORE_CASE,
    )
    private val PARAM_RE = Regex(
        "$LT$SP$BARS${SP}DSML${SP}$BARS${SP}parameter${SP}name${SP}=${SP}\"([^\"]*)\"" +
            "${SP}string${SP}=${SP}\"(true|false)\"${SP}$GT(.*?)" +
            "$LT$SP$SLASH$SP$BARS${SP}DSML${SP}$BARS${SP}parameter${SP}$GT",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    /**
     * 把 OpenAI `tools` 渲染成 DSML 调用模板（模型见到它才知道可以发工具调用）。
     * 无 tools 时返回空串。
     */
    fun renderTools(body: JsonObject): String {
        val tools = body.getAsJsonArray("tools") ?: return ""
        if (tools.size() == 0) return ""
        val schemas = tools.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val fn = obj.getAsJsonObject("function") ?: obj
            val name = fn.get("name")?.asString ?: return@mapNotNull null
            if (name.isEmpty()) return@mapNotNull null
            val desc = fn.get("description")?.asString.orEmpty()
            val params = fn.get("parameters")
            buildString {
                append("- ").append(name)
                if (desc.isNotEmpty()) append("：").append(desc)
                if (params != null) append("\n  参数 JSON Schema：").append(params.toString())
            }
        }.joinToString("\n")
        if (schemas.isEmpty()) return ""
        return "## Tools\n" +
            "You have access to a set of tools. Invoke them by writing a \"<｜DSML｜tool_calls>\" block:\n" +
            "<｜DSML｜tool_calls>\n" +
            "<｜DSML｜invoke name=\"\$TOOL_NAME\">\n" +
            "<｜DSML｜parameter name=\"\$PARAMETER_NAME\" string=\"true|false\">\$PARAMETER_VALUE</｜DSML｜parameter>\n" +
            "</｜DSML｜invoke>\n" +
            "</｜DSML｜tool_calls>\n" +
            "String parameters use string=\"true\"; numbers/booleans/arrays/objects use string=\"false\" with JSON value.\n" +
            "Available Tool Schemas\n" + schemas + "\n" +
            "You MUST strictly follow the above defined tool name and parameter schemas to invoke tool calls." +
            "\n\nRules:\n" +
            "1. If the user's task is not yet fully finished, output ONLY a tool_calls block invoking " +
            "the next required tool. Do NOT add any other text.\n" +
            "2. Output normal text ONLY when the entire task is completely done.\n" +
            "3. Tool results are fed back to you automatically; keep invoking tools until the task is done." +
            "4. If a tool call fails with an error, do not apologize and do not stop: read the error, " +
            "then invoke the correct tool instead. Failed calls have no side effects."
    }

    /** 历史里的 assistant tool_calls 还原成 DSML，否则多轮工具调用会断链。 */
    fun renderToolCalls(calls: JsonArray): String {
        val sb = StringBuilder("<｜DSML｜tool_calls>")
        for (element in calls) {
            val obj = element as? JsonObject ?: continue
            val fn = obj.getAsJsonObject("function") ?: obj
            val name = fn.get("name")?.asString.orEmpty()
            if (name.isEmpty()) continue
            sb.append("\n<｜DSML｜invoke name=\"").append(name).append("\">")
            val args = runCatching {
                JsonParser.parseString(fn.get("arguments")?.asString.orEmpty()).asJsonObject
            }.getOrNull()
            if (args != null) {
                for ((key, value) in args.entrySet()) {
                    val isString = value.isJsonPrimitive && value.asJsonPrimitive.isString
                    val raw = if (isString) value.asString else value.toString()
                    sb.append("\n<｜DSML｜parameter name=\"").append(key)
                        .append("\" string=\"").append(isString).append("\">")
                        .append(raw).append("</｜DSML｜parameter>")
                }
            }
            sb.append("\n</｜DSML｜invoke>")
        }
        sb.append("\n</｜DSML｜tool_calls>")
        return sb.toString()
    }

    /** 非流式：拆出正文与工具调用。 */
    fun splitContent(content: String): Pair<String, List<Call>> {
        if (content.isEmpty()) return "" to emptyList()
        val source = if (content.contains("DSML", ignoreCase = true) && content.contains("&lt;", ignoreCase = true)) {
            normalize(decodeEntities(content))
        } else {
            normalize(content)
        }
        val text = StringBuilder()
        val calls = mutableListOf<Call>()
        var i = 0
        while (i < source.length) {
            val invoke = INVOKE_RE.find(source, i) ?: break
            text.append(stripTags(source.substring(i, invoke.range.first)))
            val name = invoke.groupValues[1]
            val end = INVOKE_END_RE.find(source, invoke.range.last + 1)
            val body = if (end == null) {
                source.substring(invoke.range.last + 1)
            } else {
                source.substring(invoke.range.last + 1, end.range.first)
            }
            calls += Call(name, parseArguments(body))
            i = if (end == null) source.length else end.range.last + 1
        }
        text.append(stripTags(source.substring(i)))
        return text.toString().trim() to calls
    }

    private fun stripTags(text: String): String = text.replace(TAG_RE, "")

    private fun parseArguments(inner: String): String {
        val args = JsonObject()
        for (match in PARAM_RE.findAll(inner)) {
            val key = match.groupValues[1]
            val isString = match.groupValues[2].equals("true", ignoreCase = true)
            val raw = match.groupValues[3].removeSurrounding("<![CDATA[", "]]>")
            if (isString) {
                args.addProperty(key, raw.trim())
            } else {
                val value = raw.trim()
                val parsed = runCatching { JsonParser.parseString(value) }.getOrNull()
                if (parsed == null) args.addProperty(key, value) else args.add(key, parsed)
            }
        }
        return args.toString()
    }

    private fun decodeEntities(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#124;", "|")
        .replace("&#x7c;", "|", ignoreCase = true)
        .replace("&vert;", "|")

    /**
     * 字符规范化：上游会把 DSML 标记里的部分字符写成全角（＜＞｜ＤＳＭＬ）或夹零宽字符，
     * 渲染出来一样、字节不同。NFKC 把全角转半角，filter 去掉零宽/不可见字符，再去匹配。
     */
    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC)
            .filter { it != '\u200B' && it != '\u200C' && it != '\u200D' && it != '\u2060' && it != '\uFEFF' }

    /**
     * 流式：正文逐字转出；一旦发现 DSML 标签就整段扣下，等流结束后拆出正文与工具调用。
     * 末尾不足一个标记的片段先留着，避免把半个 `<｜DSML｜…` 当正文发出去。
     *
     * [diagTag] 是诊断日志的供应商标签；[emitContent] 把确定不是标记的正文增量转出。
     */
    class StreamExtractor(
        private val diagTag: String,
        private val emitContent: (String) -> Unit,
    ) {

        private val pending = StringBuilder()
        private val raw = StringBuilder()
        private var buffering = false
        private val calls = mutableListOf<Call>()

        fun feed(text: String) {
            if (text.isEmpty()) return
            // 进入缓冲后什么都不发，等 finish 一次性拆正文 + 工具调用，避免半截标记漏出去
            if (buffering) {
                raw.append(text)
                return
            }
            // 只有出现疑似 DSML 标记（DSML 字样或 <| 开头）才切入缓冲。
            // 不能用「含全角字符」判断：中文正文满屏全角标点，会整段误吞进缓冲，
            // 流式正文全部卡到 finish 才出，还刷「解析失败」日志。
            if (looksLikeDsml(text)) {
                if (pending.isNotEmpty()) {
                    emitContent(pending.toString())
                    pending.clear()
                }
                buffering = true
                raw.append(text)
                return
            }
            pending.append(text)
            if (TAG_RE.containsMatchIn(pending.toString())) {
                buffering = true
                raw.append(pending.toString())
                pending.clear()
                return
            }
            val keep = prefixLength(pending.toString())
            val emitLen = pending.length - keep
            if (emitLen > 0) {
                emitContent(pending.substring(0, emitLen))
                pending.delete(0, emitLen)
            }
        }

        /** 是否疑似 DSML 标记：NFKC + 去零宽后含 DSML 字样或 `<|` 开头。 */
        private fun looksLikeDsml(text: String): Boolean {
            val t = normalize(text)
            return t.contains("DSML", ignoreCase = true) || t.contains("<|")
        }

        fun finish(): List<Call> {
            if (buffering) {
                val original = raw.toString()
                val (clean, parsed) = splitContent(original)
                calls += parsed
                if (parsed.isEmpty()) {
                    Diagnostics.warn(diagTag, "工具调用块解析失败，已原样回传：${original.take(1200)}")
                    emitContent(original)
                } else if (clean.isNotEmpty()) {
                    emitContent(clean)
                }
                raw.clear()
                buffering = false
            }
            if (pending.isNotEmpty()) {
                emitContent(pending.toString())
                pending.clear()
            }
            return calls
        }

        private fun prefixLength(text: String): Int {
            val idx = text.lastIndexOfAny(charArrayOf('<', '＜'))
            if (idx < 0) return 0
            val tail = text.substring(idx)
            if (tail.length > 48) return 0
            if (TAG_RE.containsMatchIn(tail)) return 0
            val markerish = tail.all {
                it == '<' || it == '＜' || it == '/' || it == '／' || it == '｜' || it == '|' ||
                    it == ' ' || it == '\u3000' || it.isLetter()
            }
            return if (markerish) tail.length else 0
        }
    }
}
