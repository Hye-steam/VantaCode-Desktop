package dev.aigw.provider.deepseek

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.arrayOrNull
import dev.aigw.util.objOrNull

/**
 * DeepSeek 网页版 SSE 解析。
 *
 * 实际帧结构（来自真实抓包）：
 * - 首帧无 `p`：`{"v":{"response":{... "fragments":[{"type":"THINK|RESPONSE","content":"..."}]}}}`
 * - 增量带 `p`：`{"p":"response/fragments/-1/content","o":"APPEND","v":"字"}`
 * - 简写帧无 `p`：`{"v":"字"}`，沿用上一帧的路径
 * - 切换片段：`{"p":"response/fragments","o":"APPEND","v":[{"type":"RESPONSE","content":"..."}]}`
 * - 结束：`{"p":"response/status","o":"SET","v":"FINISHED"}` 或 `event: close`
 *
 * 思维链与正文共用 `response/fragments/-1/content`，靠当前片段 `type` 区分。
 */
internal class DeepSeekSseTranslator(
    private val onContent: (String) -> Unit,
    private val onReasoning: (String) -> Unit,
) {

    private var lastPath = ""
    private var currentType = "RESPONSE"

    var finished = false
        private set

    /** 喂一行原始 SSE 行；返回 true 表示流已结束。 */
    fun feed(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return finished

        if (trimmed.startsWith("event:")) {
            if (trimmed.removePrefix("event:").trim() == "close") finished = true
            return finished
        }
        if (!trimmed.startsWith("data:")) return finished

        val data = trimmed.removePrefix("data:").trim()
        if (data.isEmpty()) return finished
        val obj = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return finished

        obj.get("p")?.takeIf { it.isJsonPrimitive }?.asString?.let { lastPath = it }
        val value = obj.get("v")

        // 首帧：完整 response 对象，fragments 里已带首段内容
        val response = (value as? JsonObject)?.objOrNull("response")
        if (response != null) {
            response.arrayOrNull("fragments")?.forEach { emitFragment(it) }
            return finished
        }

        when {
            lastPath == "response/fragments" && value is JsonArray -> value.forEach { emitFragment(it) }

            lastPath.startsWith("response/fragments/") && lastPath.endsWith("/content") -> {
                if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                    emitText(value.asString)
                }
            }

            lastPath == "response/status" && value != null && value.isJsonPrimitive -> {
                if (value.asString == "FINISHED") finished = true
            }

            lastPath == "response" && value is JsonArray -> {
                value.forEach { item ->
                    val entry = item as? JsonObject ?: return@forEach
                    if (entry.get("p")?.asString == "quasi_status" && entry.get("v")?.asString == "FINISHED") {
                        finished = true
                    }
                }
            }
        }
        return finished
    }

    private fun emitFragment(element: JsonElement) {
        val fragment = element as? JsonObject ?: return
        currentType = fragment.get("type")?.asString ?: "RESPONSE"
        val content = fragment.get("content")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (content.isNotEmpty()) emitText(content)
    }

    private fun emitText(text: String) {
        if (currentType == "THINK") onReasoning(text) else onContent(text)
    }
}
