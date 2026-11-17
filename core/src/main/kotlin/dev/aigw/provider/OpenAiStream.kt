package dev.aigw.provider

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.aigw.util.newCompletionId
import dev.aigw.util.objOrNull
import dev.aigw.util.stringOrNull

/**
 * 把 OpenAI SSE chunk 流聚合成非流式的 `chat.completion`。
 *
 * 小浣熊（上游强制 stream）只能拿到流式上游，而网关对客户端必须支持 stream=false，
 * 所以把聚合逻辑集中在这里，避免各供应商各写一套容易跑偏的实现。
 */
class ChunkAggregator {

    private val content = StringBuilder()
    private val reasoning = StringBuilder()
    private val calls = linkedMapOf<Int, CallState>()
    private var finishReason: String? = null
    private var usage: JsonObject? = null
    private var id: String = ""

    private class CallState(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

    fun consume(chunk: JsonObject) {
        chunk.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { id = it }
        chunk.objOrNull("usage")?.let { usage = it }
        val choices = chunk.getAsJsonArray("choices") ?: return
        for (element in choices) {
            val choice = element as? JsonObject ?: continue
            choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString?.let { finishReason = it }
            val delta = choice.objOrNull("delta") ?: continue
            delta.stringOrNull("content")?.let { content.append(it) }
            delta.stringOrNull("reasoning_content")?.let { reasoning.append(it) }
            delta.getAsJsonArray("tool_calls")?.forEach { callEl ->
                val call = callEl as? JsonObject ?: return@forEach
                val index = call.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                val state = calls.getOrPut(index) { CallState() }
                call.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { state.id = it }
                val fn = call.objOrNull("function") ?: return@forEach
                fn.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let { state.name = it }
                fn.stringOrNull("arguments")?.let { state.args.append(it) }
            }
        }
    }

    /** 上游 200 但没给任何内容（含思考内容）：调用方据此判定失败，不当作「成功的空回复」。 */
    fun isEmpty(): Boolean = content.isBlank() && reasoning.isBlank() && calls.isEmpty()

    fun completion(model: String): JsonObject {
        val message = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content.toString())
            if (reasoning.isNotEmpty()) addProperty("reasoning_content", reasoning.toString())
            if (calls.isNotEmpty()) {
                add("tool_calls", JsonArray().apply {
                    for ((index, call) in calls.entries.sortedBy { it.key }) {
                        if (call.name.isEmpty()) continue
                        add(JsonObject().apply {
                            addProperty("index", index)
                            addProperty("id", call.id.ifEmpty { newCompletionId() })
                            addProperty("type", "function")
                            add("function", JsonObject().apply {
                                addProperty("name", call.name)
                                addProperty("arguments", call.args.toString().ifEmpty { "{}" })
                            })
                        })
                    }
                })
            }
        }
        val reason = when {
            calls.isNotEmpty() -> "tool_calls"
            finishReason.isNullOrBlank() -> "stop"
            else -> finishReason
        }
        return JsonObject().apply {
            addProperty("id", id.ifEmpty { newCompletionId() })
            addProperty("object", "chat.completion")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", model)
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("message", message)
                    addProperty("finish_reason", reason)
                })
            })
            usage?.let { add("usage", it) }
        }
    }
}

/** OpenAI chunk 的构造函数（两家供应商的 SSE 翻译共用）。 */
internal object OpenAiChunks {

    fun chunk(
        id: String,
        model: String,
        delta: JsonObject,
        finishReason: String? = null,
        usage: JsonObject? = null,
    ): String {
        val choice = JsonObject().apply {
            addProperty("index", 0)
            add("delta", delta)
            if (finishReason != null) addProperty("finish_reason", finishReason)
        }
        return JsonObject().apply {
            addProperty("id", id)
            addProperty("object", "chat.completion.chunk")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", model)
            add("choices", JsonArray().apply { add(choice) })
            usage?.let { add("usage", it) }
        }.toString()
    }

    fun textDelta(text: String): JsonObject = JsonObject().apply { addProperty("content", text) }

    fun reasoningDelta(text: String): JsonObject = JsonObject().apply { addProperty("reasoning_content", text) }

    fun roleDelta(): JsonObject = JsonObject().apply { addProperty("role", "assistant") }

    /** 流内错误帧：顶层 error 对象，网关能识别成失败。 */
    fun errorFrame(message: String): String = JsonObject().apply {
        add("error", JsonObject().apply {
            addProperty("message", message)
            addProperty("type", "upstream_error")
        })
    }.toString()

    fun emptyDelta(): JsonObject = JsonObject()
}
