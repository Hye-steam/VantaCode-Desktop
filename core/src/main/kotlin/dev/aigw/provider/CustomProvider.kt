package dev.aigw.provider

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.Http
import dev.aigw.util.Log
import dev.aigw.util.objOrNull

/**
 * 自定义 OpenAI 兼容中转站：一个 key = 一个账号，纯直通。
 * 流式时透传上游 SSE 的 data 行；非流式时聚合 JSON 返回。
 */
class CustomProvider : Provider {
    override val id = "custom"
    override val displayName = "自定义"

    override fun listModels(account: ProviderAccount): Result<List<String>> {
        val url = account.baseUrl.trimEnd('/') + "/models"
        return try {
            val r = Http.get(url, headers(account))
            Http.classify(r)?.let { return Result.failure(Exception(it.message)) }
            val root = JsonParser.parseString(r.body).asJsonObject
            val out = mutableListOf<String>()
            (root.getAsJsonArray("data") ?: JsonArray()).forEach { el ->
                val id = el.asJsonObject.get("id")?.asString
                if (!id.isNullOrBlank()) out.add(id)
            }
            Result.success(out)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val url = account.baseUrl.trimEnd('/') + "/chat/completions"
        val json = request.body.toString()
        return try {
            if (request.stream && sink != null) {
                var events = 0
                var inputTokens = 0
                var outputTokens = 0
                var cachedTokens = 0
                val r = Http.postStream(
                    url = url,
                    headers = headers(account),
                    json = json,
                    onLine = { line ->
                        when {
                            line.startsWith("data:") -> {
                                val data = line.removePrefix("data:").trim()
                                if (data.isEmpty()) true
                                else if (data == "[DONE]") {
                                    sink.onData("[DONE]"); false
                                } else {
                                    events++
                                    parseUsageTokens(data)?.let { usage ->
                                        inputTokens = usage.input
                                        outputTokens = usage.output
                                        cachedTokens = usage.cached
                                    }
                                    sink.onData(data)
                                    true
                                }
                            }
                            line.startsWith(":") -> true // SSE 注释
                            else -> true
                        }
                    },
                    onDone = { sink.onDone() },
                )
                if (!r.ok) {
                    Http.classify(r)?.let { return ChatResult.Failure(it) }
                }
                ChatResult.Streamed(events, inputTokens, outputTokens, cachedTokens)
            } else {
                val r = Http.postJson(url, headers(account), json)
                Http.classify(r)?.let { return ChatResult.Failure(it) }
                val body = r.body.trim()
                if (body.isEmpty()) {
                    ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, 200, "upstream returned empty body"))
                } else {
                    ChatResult.Aggregated(JsonParser.parseString(body).asJsonObject)
                }
            }
        } catch (e: Exception) {
            Log.warn("custom upstream error: ${e.message}")
            ChatResult.Failure(Http.networkError(e))
        }
    }

    private fun headers(account: ProviderAccount): Map<String, String> =
        mapOf("Authorization" to "Bearer ${account.apiKey}")
}

/** 从一条 SSE data JSON 里取 usage 的输入/输出 token；无 usage 返回 null。 */
internal fun parseUsageTokens(json: String): UsageTokens? {
    val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
    val usage = obj.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject ?: return null
    fun pick(vararg keys: String): Int {
        for (key in keys) {
            val value = usage.get(key) ?: continue
            if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) return value.asInt
        }
        return 0
    }
    val input = pick("prompt_tokens", "input_tokens")
    val output = pick("completion_tokens", "output_tokens")
    if (input == 0 && output == 0) return null
    return UsageTokens(input, output, cachedTokensOf(usage))
}

/** 从 usage 里取「提示词缓存命中 token」；各家字段名不同，逐个兼容。 */
internal fun cachedTokensOf(usage: JsonObject): Int {
    // OpenAI：usage.prompt_tokens_details.cached_tokens
    usage.objOrNull("prompt_tokens_details")?.get("cached_tokens")
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        ?.let { if (it > 0) return it }
    // DeepSeek API：prompt_cache_hit_tokens；以及若干别名
    for (key in listOf("prompt_cache_hit_tokens", "cached_tokens", "cache_read_input_tokens")) {
        val value = usage.get(key)
        if (value != null && value.isJsonPrimitive && value.asJsonPrimitive.isNumber && value.asInt > 0) {
            return value.asInt
        }
    }
    return 0
}
