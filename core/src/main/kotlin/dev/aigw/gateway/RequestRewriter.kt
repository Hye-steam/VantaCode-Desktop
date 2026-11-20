package dev.aigw.gateway

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * 请求改写管线。
 *
 * 客户端发来的请求体在进入路由之前，按固定顺序过一遍改写器：
 *
 *  1. [Rewriter.Alias]       把客户端写的模型名映射成真实模型
 *  2. [Rewriter.System]      注入/替换 system 消息
 *  3. [Rewriter.ThinkTags]   剥掉历史消息里的 `<think>...</think>`
 *
 * 都只改 `body`，不改路由结果；别名在 [Engine.chat] 里先于路由生效，
 * 所以这里拿到的是别名之后的模型名。
 *
 * 未配置时逐个提前返回，零开销。
 */
object RequestRewriter {

    /** 改写上下文：目前只有目标模型名，后续要按模型分支时可扩展。 */
    data class Ctx(val model: String)

    /**
     * 按配置应用全部改写器。
     *
     * @param body     客户端原始请求体
     * @param settings 当前设置（别名表、system 提示词、think 清理开关）
     */
    fun apply(body: JsonObject, settings: Settings): JsonObject {
        var out = body
        out = Rewriter.System.apply(Ctx(modelOf(out)), out, settings.systemPrompt)
        if (settings.stripThinkTags) out = Rewriter.ThinkTags.apply(Ctx(modelOf(out)), out)
        return out
    }

    /** 把客户端写的模型名按别名表换成真实模型；没有命中返回原值。 */
    fun resolveAlias(model: String, settings: Settings): String {
        val aliases = settings.modelAliases ?: return model
        val hit = aliases[model]?.trim()
        return if (hit.isNullOrEmpty()) model else hit
    }

    private fun modelOf(body: JsonObject): String =
        body.get("model")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()

    /** 单个改写器。 */
    interface Rewriter {
        fun apply(ctx: Ctx, body: JsonObject, arg: String = ""): JsonObject

        /** 系统提示词注入：剥掉客户端自带的 system，再把自己那条放最前面。 */
        object System : Rewriter {
            override fun apply(ctx: Ctx, body: JsonObject, arg: String): JsonObject {
                val text = arg.trim()
                val messages = body.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return body

                // 未配置时保持原样：客户端的人设不应该被无故清掉
                if (text.isEmpty()) return body

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

        /**
         * 剥离 assistant 历史里的思考块。
         *
         * 只动 `role=assistant` 且 content 是字符串的消息：这些是客户端回传的
         * 历史记录，`<think>` 留在里面会占上下文，某些上游还会据此判定格式非法。
         * 未闭合的 `<think>`（流式截断的情况）连同后面内容一起丢掉。
         */
        object ThinkTags : Rewriter {
            private val closed = Regex("<think\\b[^>]*>.*?</think\\s*>", RegexOption.DOT_MATCHES_ALL)
            private val openToEnd = Regex("<think\\b[^>]*>.*$", RegexOption.DOT_MATCHES_ALL)

            override fun apply(ctx: Ctx, body: JsonObject, arg: String): JsonObject {
                val messages = body.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: return body
                var changed = false
                val rewritten = JsonArray()
                messages.forEach { el ->
                    val obj = runCatching { el.asJsonObject }.getOrNull()
                        ?: return@forEach run { rewritten.add(el) }
                    val role = obj.get("role")?.takeIf { it.isJsonPrimitive }?.asString
                    val content = obj.get("content")?.takeIf { it.isJsonPrimitive }?.asString
                    if (role != "assistant" || content == null || !content.contains("<think")) {
                        rewritten.add(obj)
                        return@forEach
                    }
                    var text = closed.replace(content, "")
                    text = openToEnd.replace(text, "")
                    if (text == content) {
                        rewritten.add(obj)
                    } else {
                        changed = true
                        val copy = obj.deepCopy()
                        copy.addProperty("content", text.trim())
                        rewritten.add(copy)
                    }
                }
                if (!changed) return body
                return body.deepCopy().also { it.add("messages", rewritten) }
            }
        }
    }
}
