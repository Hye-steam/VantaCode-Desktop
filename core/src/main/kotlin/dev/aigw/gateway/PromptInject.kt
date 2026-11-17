package dev.aigw.gateway

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 系统提示词注入。
 *
 * 把请求体里所有 `role=system` 的消息剥掉，再把用户配置的那条插到最前面。
 * 剥掉而不是追加，是因为客户端会带自己的人设（各家 SDK 的默认提示词），
 * 两段 system 同时存在时模型容易左右摇摆。
 *
 * 未配置时原样返回，零开销。
 */
object PromptInject {

    fun apply(body: JsonObject, system: String): JsonObject {
        val text = system.trim()
        if (text.isEmpty()) return body
        val messages = body.getAsJsonArray("messages") ?: return body

        val kept = JsonArray()
        messages.forEach { el ->
            val obj = runCatching { el.asJsonObject }.getOrNull() ?: return@forEach
            val role = obj.get("role")?.takeIf { it.isJsonPrimitive }?.asString
            if (role != "system") kept.add(obj)
        }

        val injected = JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", text)
        }
        val rewritten = JsonArray().apply {
            add(injected)
            addAll(kept)
        }
        return body.deepCopy().also { it.add("messages", rewritten) }
    }
}
