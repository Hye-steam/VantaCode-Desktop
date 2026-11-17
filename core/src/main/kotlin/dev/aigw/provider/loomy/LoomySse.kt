package dev.aigw.provider.loomy

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.arrayOrNull
import dev.aigw.util.asObjectOrNull
import dev.aigw.util.intOrNull
import dev.aigw.util.longOrNull
import dev.aigw.util.objOrNull
import dev.aigw.util.stringOrNull
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * 把 OpenAI SSE 流聚合成一个非流式 `chat.completion`。
 *
 * 供「上游本身就是 OpenAI 协议」的 provider（Loomy）复用：
 * 非流式请求时上游可能仍回 SSE，统一由这里聚合。
 */
internal object LoomySseAggregator {

    fun aggregate(stream: InputStream, fallbackModel: String): String {
        var id = ""
        var model = fallbackModel
        var created = 0L
        val content = StringBuilder()
        val reasoning = StringBuilder()
        val toolCalls = LinkedHashMap<Int, JsonObject>()
        var finishReason = "stop"
        var usage: JsonObject? = null

        stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                val obj = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull() ?: continue
                obj.stringOrNull("id")?.takeIf { it.isNotEmpty() }?.let { id = it }
                obj.stringOrNull("model")?.takeIf { it.isNotEmpty() }?.let { model = it }
                obj.longOrNull("created")?.takeIf { it > 0 }?.let { created = it }
                obj.objOrNull("usage")?.let { usage = it }
                val choices = obj.arrayOrNull("choices") ?: continue
                for (element in choices) {
                    val choice = element.asObjectOrNull() ?: continue
                    choice.stringOrNull("finish_reason")?.takeIf { it.isNotEmpty() }?.let { finishReason = it }
                    val delta = choice.objOrNull("delta") ?: continue
                    delta.stringOrNull("content")?.let { content.append(it) }
                    delta.stringOrNull("reasoning_content")?.let { reasoning.append(it) }
                    delta.arrayOrNull("tool_calls")?.let { mergeToolCalls(toolCalls, it) }
                }
            }
        }

        val message = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content.toString())
        }
        if (reasoning.isNotEmpty()) message.addProperty("reasoning_content", reasoning.toString())
        if (toolCalls.isNotEmpty()) {
            message.add("tool_calls", JsonArray().apply {
                toolCalls.entries.sortedBy { it.key }.forEach { add(it.value) }
            })
        }
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("message", message)
            addProperty("finish_reason", finishReason)
        }
        return JsonObject().apply {
            addProperty("id", id.ifEmpty { "chatcmpl-aggregated" })
            addProperty("object", "chat.completion")
            addProperty("created", if (created > 0) created else System.currentTimeMillis() / 1000)
            addProperty("model", model)
            add("choices", JsonArray().apply { add(choice) })
            usage?.let { add("usage", it) }
        }.toString()
    }

    private fun mergeToolCalls(merged: MutableMap<Int, JsonObject>, deltas: JsonArray) {
        for (element in deltas) {
            val delta = element.asObjectOrNull() ?: continue
            val index = delta.intOrNull("index") ?: 0
            val target = merged.getOrPut(index) {
                JsonObject().apply {
                    addProperty("index", index)
                    addProperty("type", "function")
                }
            }
            delta.stringOrNull("id")?.takeIf { it.isNotEmpty() }?.let { target.addProperty("id", it) }
            val fn = delta.objOrNull("function") ?: continue
            val targetFn = target.objOrNull("function")
                ?: JsonObject().also { target.add("function", it) }
            fn.stringOrNull("name")?.takeIf { it.isNotEmpty() }?.let { targetFn.addProperty("name", it) }
            fn.stringOrNull("arguments")?.takeIf { it.isNotEmpty() }?.let { chunk ->
                val previous = targetFn.stringOrNull("arguments").orEmpty()
                targetFn.addProperty("arguments", previous + chunk)
            }
        }
    }
}
