package dev.aigw.provider.trae

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.objOrNull

private const val MAX_REF_DEPTH = 32

/**
 * OpenAI 请求体 → SOLO `llm_utils_chat` 请求体。
 *
 * 上游 Go 结构体与 OpenAI 规范有几处硬性差异，缺一即反序列化失败：
 *  - `messages[].content` 必须是 `[{type:text,text:...}]` 数组
 *  - `tools[].function.parameters` 必须是 JSON 字符串而不是对象
 *  - `tool_choice` 只接受字符串
 *  - assistant 的 `tool_calls[].function` 要改名为上游认识的 `function_call`
 *  - `stream` 恒为 true（非流式由网关侧聚合）
 */
object TraePayload {

    fun prepare(src: String, fallbackConfigName: String = TraeConstants.DEFAULT_CONFIG_NAME): String {
        if (src.isEmpty()) return src
        val obj = runCatching { JsonParser.parseString(src).asJsonObject }.getOrNull() ?: return src

        obj.addProperty("stream", true)
        obj.addProperty("function", TraeConstants.FUNCTION)

        rewriteMessages(obj)

        val model = obj.str("model").trim().ifEmpty { fallbackConfigName }
        obj.addProperty("config_name", model)
        obj.addProperty("model", model)

        normalizeToolChoice(obj)
        normalizeTools(obj)
        normalizeAliases(obj)
        pruneUnknownFields(obj)

        return obj.toString()
    }

    /** 新名映射到上游认识的旧名，必须在 pruneUnknownFields 之前。 */
    private fun normalizeAliases(obj: JsonObject) {
        val newMax = obj.get("max_completion_tokens")
        if (newMax != null && !obj.has("max_tokens")) obj.add("max_tokens", newMax)
        obj.remove("stream_options")
    }

    /**
     * 上游结构体不认识未知字段，多一个就用 4001 拒掉整条请求，
     * 所以这里做白名单：只保留上游认识的键。
     */
    private fun pruneUnknownFields(obj: JsonObject) {
        val allowed = setOf(
            "config_name", "model", "messages", "stream", "function",
            "tools", "tool_choice", "functions",
            "temperature", "top_p", "max_tokens", "n", "stop",
            "frequency_penalty", "presence_penalty",
            "poly_prompt", "session_id", "mode_type", "agent_type",
        )
        for (key in obj.keySet().toList()) {
            if (key !in allowed) obj.remove(key)
        }
    }

    private fun rewriteMessages(obj: JsonObject) {
        val messages = obj.get("messages") as? JsonArray ?: return
        for (element in messages) {
            val message = element as? JsonObject ?: continue

            // SOLO 上游只认 system / assistant / user / tool，不认 developer
            if (message.str("role") == "developer") message.addProperty("role", "system")
            if (message.str("role") == "assistant") rewriteAssistantToolCalls(message)

            val content = message.get("content")
            if (content == null || content is JsonNull) continue
            if (content.isJsonPrimitive && content.asJsonPrimitive.isString) {
                val wrapped = JsonArray()
                wrapped.add(JsonObject().apply {
                    addProperty("type", "text")
                    addProperty("text", content.asString)
                })
                message.add("content", wrapped)
            }
        }
    }

    private fun rewriteAssistantToolCalls(message: JsonObject) {
        val toolCalls = message.get("tool_calls") as? JsonArray ?: return
        val kept = JsonArray()
        for (element in toolCalls) {
            val call = element as? JsonObject ?: continue
            (call.get("function") as? JsonObject)?.let { fn ->
                call.add("function_call", fn)
                call.remove("function")
            }
            val name = (call.get("function_call") as? JsonObject)?.str("name").orEmpty().trim()
            if (name.isEmpty()) continue
            kept.add(call)
        }
        if (kept.size() == 0) message.remove("tool_calls") else message.add("tool_calls", kept)
    }

    private fun normalizeToolChoice(obj: JsonObject) {
        val choice: JsonElement = obj.get("tool_choice") ?: return
        when {
            choice.isJsonPrimitive && choice.asJsonPrimitive.isString -> {
                if (choice.asString.trim().equals("none", ignoreCase = true)) {
                    obj.remove("tool_choice")
                    suppressTools(obj)
                }
            }
            choice.isJsonObject -> {
                val v = choice.asJsonObject
                when (v.str("type").trim().lowercase()) {
                    "none" -> {
                        obj.remove("tool_choice")
                        suppressTools(obj)
                    }
                    "auto", "required" -> obj.addProperty("tool_choice", v.str("type").trim().lowercase())
                    "function" -> {
                        val name = (v.objOrNull("function")?.str("name") ?: v.str("name")).trim()
                        obj.addProperty("tool_choice", name.ifEmpty { "auto" })
                    }
                    else -> obj.remove("tool_choice")
                }
            }
            else -> obj.remove("tool_choice")
        }
    }

    private fun suppressTools(obj: JsonObject) {
        obj.remove("tools")
        obj.remove("functions")
    }

    private fun normalizeTools(obj: JsonObject) {
        val raw = obj.get("tools") as? JsonArray ?: return
        if (raw.size() == 0) return
        val out = JsonArray()
        for (element in raw) {
            val tool = element as? JsonObject ?: continue
            val fn = tool.objOrNull("function") ?: continue
            val params = fn.get("parameters")
            if (params != null && params.isJsonObject) {
                // 必须在字符串化之前内联 $ref：Kimi/Moonshot 系后端拒收带 $ref 的 schema
                val inlined = inlineSchemaRefs(params.asJsonObject)
                fn.addProperty("parameters", inlined.toString())
            }
            out.add(tool)
        }
        if (out.size() == 0) obj.remove("tools") else obj.add("tools", out)
    }

    /** 把工具 schema 里的同文档 $ref 内联展开，循环引用用空 schema 断开。 */
    private fun inlineSchemaRefs(root: JsonObject): JsonObject {
        val defs = HashMap<String, JsonElement>()
        (root.get("\$defs") as? JsonObject)?.entrySet()?.forEach { defs["#/\$defs/${it.key}"] = it.value }
        (root.get("definitions") as? JsonObject)?.entrySet()?.forEach { defs["#/definitions/${it.key}"] = it.value }
        if (defs.isEmpty()) return root
        val expanded = expandRefs(root, defs, emptySet(), 0) as? JsonObject ?: return root
        expanded.remove("\$defs")
        expanded.remove("definitions")
        return expanded
    }

    private fun expandRefs(
        element: JsonElement,
        defs: Map<String, JsonElement>,
        seen: Set<String>,
        depth: Int,
    ): JsonElement {
        if (depth > MAX_REF_DEPTH) return element
        if (element.isJsonArray) {
            val out = JsonArray()
            for (item in element.asJsonArray) out.add(expandRefs(item, defs, seen, depth + 1))
            return out
        }
        if (!element.isJsonObject) return element
        val obj = element.asJsonObject
        val ref = obj.get("\$ref")?.takeIf { it.isJsonPrimitive }?.asString
        if (ref != null) {
            if (ref in seen) return JsonObject()
            val target = defs[ref] ?: return obj
            val expanded = expandRefs(target, defs, seen + ref, depth + 1)
            val siblings = obj.entrySet().filter { it.key != "\$ref" }
            if (siblings.isEmpty() || !expanded.isJsonObject) return expanded
            val merged = expanded.asJsonObject
            for ((key, value) in siblings) merged.add(key, expandRefs(value, defs, seen, depth + 1))
            return merged
        }
        val out = JsonObject()
        for ((key, value) in obj.entrySet()) out.add(key, expandRefs(value, defs, seen, depth + 1))
        return out
    }
}
