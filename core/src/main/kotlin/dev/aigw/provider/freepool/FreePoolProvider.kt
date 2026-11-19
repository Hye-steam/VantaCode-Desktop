package dev.aigw.provider.freepool

import com.google.gson.JsonParser
import dev.aigw.provider.ApiError
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.SseSink
import dev.aigw.util.Http
import dev.aigw.util.Log

/**
 * 免费池：一批免登录的公共模型通道。
 *
 * 这些上游不需要账号或 Key，转发 OpenAI 格式请求即可直接拿到结果。
 * 模型名用「渠道/模型」两级命名（例如 `pollinations/openai-fast`），
 * 因为不同渠道会存在同名模型，只有渠道加模型才能唯一定位。
 *
 * 已知限制：
 * - 都有人工或自动的额度闸门（匿名每日次数、公共容量），429 是常态；
 * - OVHcloud 公共 endpoints 没有 SLA，随时可能停；
 * - 只在实测能返回正文的渠道才收录，配置里写着但已限流的不放进来。
 */
class FreePoolProvider : Provider {
    override val id = ID
    override val displayName = "免费池"

    data class Channel(
        val key: String,
        val name: String,
        /** 额度说明，展示在模型列表的副标题里。 */
        val note: String,
        val base: String,
        val models: List<String>,
    )

    companion object {
        const val ID = "freepool"

        /** 免登录渠道。全部于 2026-11-17 实测能返回正文（OpenCode 两个端点均 429，未收录）。 */
        val CHANNELS: List<Channel> = listOf(
            Channel(
                key = "pollinations",
                name = "Pollinations",
                note = "匿名通道",
                base = "https://text.pollinations.ai/openai",
                models = listOf("openai-fast", "openai", "gpt-oss", "ovh-reasoning"),
            ),
            Channel(
                key = "llm7",
                name = "LLM7",
                note = "匿名每日约 60 次",
                base = "https://api.llm7.io/v1",
                models = listOf(
                    "default",
                    "fast",
                    "gpt-oss:20b",
                    "DeepSeek-V4-Flash-0731",
                    "GLM-5.3-Flash",
                    "minimax-m2.7",
                    "codestral-latest",
                    "nemotron-3-nano:30b",
                    "mistral-Nemo-Instruct-2407",
                ),
            ),
            Channel(
                key = "kilo",
                name = "Kilo",
                note = "免费池 · 需大 max_tokens（推理模型）",
                base = "https://api.kilo.ai/api/gateway",
                // 官方 /api/gateway/models 共 469 个，这里只收 isFree=true 的 13 个
                models = listOf(
                    "kilo-auto/free",
                    "baidu/ernie-5.5-flash-preview:free",
                    "liquid/lfm-3-1.7b:free",
                    "particle-ai/particle-1:free",
                    "nvidia/nemotron-3.5-super-vl:free",
                    "dots-studio/dots-3-note-preview:free",
                    "nvidia/nemotron-3.5-lightning:free",
                    "cohere/north-mini-code:free",
                    "nvidia/nemotron-3-ultra-550b-a55b:free",
                    "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
                    "openrouter/free",
                    "reflection/beam-free",
                    "stealth/glyph-cluster",
                ),
            ),
            Channel(
                key = "blockrun",
                name = "BlockRun",
                note = "NVIDIA 托管公共池，容量紧张时 429",
                base = "https://blockrun.ai/api/v1",
                models = listOf(
                    "nvidia/nemotron-3.5-lightning",
                    "nvidia/nemotron-3.5-bolt",
                    "nvidia/nemotron-3-super-120b",
                    "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning",
                    "nvidia/nemotron-3-ultra-550b",
                    "nvidia/muse-glimmer-30b",
                    "cohere/north-mini-code",
                    "poolside/laguna-xs-2.1",
                ),
            ),
            Channel(
                key = "pollinations-image",
                name = "Pollinations 图片",
                note = "免登录出图，约 1 张/40 秒",
                base = "https://image.pollinations.ai",
                models = listOf("flux", "turbo", "flux-realism", "flux-anime"),
            ),
            Channel(
                key = "ovh",
                name = "OVHcloud AI",
                note = "公共 endpoints，无 SLA",
                base = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1",
                models = listOf(
                    "gpt-oss-120b",
                    "Meta-Llama-3_3-70B-Instruct",
                    "Qwen3.5-397B-A17B",
                    "Mistral-Small-3.2-24B-Instruct-2506",
                    "Qwen3.6-27B",
                    "Qwen3.8-27B",
                    "Qwen3.5-9B",
                    "Qwen2.5-VL-72B-Instruct",
                    "gpt-oss-20b",
                ),
            ),
        )

        /** 拆 `渠道/模型`；渠道不认识或模型为空时返回 null。 */
        fun split(modelKey: String): Pair<Channel, String>? {
            val slash = modelKey.indexOf('/')
            if (slash <= 0 || slash == modelKey.lastIndex) return null
            val channel = CHANNELS.firstOrNull { it.key == modelKey.substring(0, slash) } ?: return null
            return channel to modelKey.substring(slash + 1)
        }

        /** 模型全名（Engine 会再加 `freepool/` 前缀）。 */
        fun allModelKeys(): List<String> =
            CHANNELS.flatMap { c -> c.models.map { "${c.key}/$it" } }
    }

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        Result.success(allModelKeys())

    /**
     * Pollinations 出图：拼 URL 即可。
     *
     * 它的接口是 `GET /prompt/<text>?width=&height=&seed=&model=`，同一组参数
     * 返回同一张图，所以这里不必替客户端下载，直接给地址让客户端自己拉，
     * 省一次流量也省一次解码。
     */
    private fun imageResult(
        modelKey: String,
        realModel: String,
        request: ChatRequest,
        stream: Boolean,
        sink: SseSink?,
    ): ChatResult {
        val prompt = extractPrompt(request.body)
        if (prompt.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "messages 里没有可用文本"))
        }

        // 客户端会拿当前聊天模型去生成会话标题（VantaCode 的 title-generator.md 就是这样）。
        // 图片模型若照常返回「已生成图片…」，标题就会被污染成这句话，所以这里直接给短标题。
        if (extractSystem(request.body).contains("title generator", ignoreCase = true)) {
            return plainStreamOrAggregate(prompt.take(24), modelKey, stream, sink)
        }
        val encoded = java.net.URLEncoder.encode(prompt, "UTF-8").replace("+", "%20")
        val seed = Math.abs(prompt.hashCode())
        val url = "https://image.pollinations.ai/prompt/$encoded" +
            "?width=1024&height=1024&seed=$seed&model=${realModel}&nologo=true"
        val text = "已生成图片（$modelKey）：\n\n![图片]($url)\n\n" +
            "如果没显示，把链接复制到浏览器打开：\n$url"

        // 客户端几乎都发 stream=true；这里不真连上游，但必须按流式格式回，
        // 否则聚合结果不会被转成 SSE，客户端只看到空响应。
        val id = "pollinations-${System.nanoTime()}"
        if (stream && sink != null) {
            val chunk = """{"id":"$id","object":"chat.completion.chunk","created":${System.currentTimeMillis() / 1000},""" +
                """"model":${quoteJson(modelKey)},"choices":[{"index":0,"delta":{"role":"assistant","content":${quoteJson(text)}},""" +
                """"finish_reason":null}]}"""
            sink.onData(chunk)
            val done = """{"id":"$id","object":"chat.completion.chunk","created":${System.currentTimeMillis() / 1000},""" +
                """"model":${quoteJson(modelKey)},"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
            sink.onData(done)
            sink.onData("[DONE]")
            sink.onDone()
            return ChatResult.Streamed(2)
        }

        val json = JsonParser.parseString(
            """{"id":"$id","object":"chat.completion","choices":[{"index":0,""" +
                """"message":{"role":"assistant","content":${quoteJson(text)}},"finish_reason":"stop"}],""" +
                """"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}""",
        ).asJsonObject
        return ChatResult.Aggregated(json)
    }

    /** 纯文本答复：按请求的 stream 形态返回，供标题生成这类不走图片的场景用。 */
    private fun plainStreamOrAggregate(
        text: String,
        modelKey: String,
        stream: Boolean,
        sink: SseSink?,
    ): ChatResult {
        val id = "pollinations-${System.nanoTime()}"
        val created = System.currentTimeMillis() / 1000
        if (stream && sink != null) {
            sink.onData(
                """{"id":"$id","object":"chat.completion.chunk","created":$created,""" +
                    """"model":${quoteJson(modelKey)},"choices":[{"index":0,""" +
                    """"delta":{"role":"assistant","content":${quoteJson(text)}},"finish_reason":null}]}""",
            )
            sink.onData(
                """{"id":"$id","object":"chat.completion.chunk","created":$created,""" +
                    """"model":${quoteJson(modelKey)},"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
            )
            sink.onData("[DONE]")
            sink.onDone()
            return ChatResult.Streamed(2)
        }
        return ChatResult.Aggregated(
            JsonParser.parseString(
                """{"id":"$id","object":"chat.completion","choices":[{"index":0,""" +
                    """"message":{"role":"assistant","content":${quoteJson(text)}},"finish_reason":"stop"}],""" +
                    """"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}""",
            ).asJsonObject,
        )
    }

    private fun quoteJson(s: String): String = com.google.gson.JsonPrimitive(s).toString()

    /** 拼出所有 system 消息的文本，用于识别「标题生成」这类特殊请求。 */
    private fun extractSystem(body: com.google.gson.JsonObject): String {
        val messages = runCatching { body.getAsJsonArray("messages") }.getOrNull() ?: return ""
        val sb = StringBuilder()
        for (el in messages) {
            val o = runCatching { el.asJsonObject }.getOrNull() ?: continue
            if (runCatching { o.get("role")?.asString }.getOrNull() != "system") continue
            val c = o.get("content") ?: continue
            if (c.isJsonPrimitive) sb.append(c.asString).append('\n')
        }
        return sb.toString()
    }

    /** 从 OpenAI 格式里抽出最后一条 user 文本。 */
    private fun extractPrompt(body: com.google.gson.JsonObject): String {
        val messages = runCatching { body.getAsJsonArray("messages") }.getOrNull() ?: return ""
        var last = ""
        for (el in messages) {
            val o = runCatching { el.asJsonObject }.getOrNull() ?: continue
            if (runCatching { o.get("role")?.asString }.getOrNull() != "user") continue
            val c = o.get("content") ?: continue
            last = when {
                c.isJsonPrimitive -> c.asString
                c.isJsonArray -> buildString {
                    for (part in c.asJsonArray) {
                        val po = runCatching { part.asJsonObject }.getOrNull() ?: continue
                        val t = runCatching { po.get("text")?.asString }.getOrNull()
                        if (!t.isNullOrBlank()) append(t)
                    }
                }
                else -> ""
            }
        }
        return last
    }

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val modelKey = request.body.get("model")?.let { if (it.isJsonPrimitive) it.asString else "" }.orEmpty()
        val (channel, realModel) = split(modelKey)
            ?: return ChatResult.Failure(
                ApiError(ApiError.Kind.CLIENT, 400, "未知的免费池模型「$modelKey」"),
            )

        // 图片渠道：Pollinations 的图片地址本身就是结果，不用发请求，
        // 直接把 URL 拼进 markdown 交给客户端加载（也顺带利用了它的确定性缓存）。
        if (channel.key == "pollinations-image") {
            return imageResult(modelKey, realModel, request, request.stream, sink)
        }

        // 换回上游真实模型 ID；其余字段（messages、tools、temperature 等）原样转发
        val body = request.body.deepCopy().also { it.addProperty("model", realModel) }
        val url = channel.base.trimEnd('/') + "/chat/completions"

        return try {
            if (request.stream && sink != null) {
                var events = 0
                var inTok = 0
                var outTok = 0
                val r = Http.postStream(
                    url = url,
                    headers = headers(account),
                    json = body.toString(),
                    onLine = { line ->
                        when {
                            line.startsWith("data:") -> {
                                val data = line.removePrefix("data:").trim()
                                when {
                                    data.isEmpty() -> true
                                    data == "[DONE]" -> {
                                        sink.onData("[DONE]")
                                        false
                                    }
                                    else -> {
                                        events++
                                        usageOf(data)?.let { (i, o) ->
                                            inTok = i
                                            outTok = o
                                        }
                                        sink.onData(data)
                                        true
                                    }
                                }
                            }
                            // SSE 注释行（有些上游会发送保活心跳）
                            line.startsWith(":") -> true
                            else -> true
                        }
                    },
                    onDone = { sink.onDone() },
                )
                if (!r.ok) Http.classify(r)?.let { return ChatResult.Failure(it) }
                ChatResult.Streamed(events, inTok, outTok, 0)
            } else {
                val r = Http.postJson(url, headers(account), body.toString())
                Http.classify(r)?.let { return ChatResult.Failure(it) }
                val text = r.body.trim()
                if (text.isEmpty()) {
                    ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, 200, "上游返回空内容"))
                } else {
                    ChatResult.Aggregated(JsonParser.parseString(text).asJsonObject)
                }
            }
        } catch (e: Exception) {
            Log.warn("免费池请求失败（${channel.key}）：${e.message}")
            ChatResult.Failure(Http.networkError(e))
        }
    }

    private fun headers(account: ProviderAccount): Map<String, String> {
        val h = mutableMapOf(
            "Content-Type" to "application/json",
            // 部分公共端点会拒绝没有 UA 的请求
            "User-Agent" to "Mozilla/5.0",
        )
        // 免登录渠道默认不带鉴权；用户自己填了 Key（LLM7 支持可选 Key）才带上
        if (account.apiKey.isNotBlank()) h["Authorization"] = "Bearer ${account.apiKey}"
        return h
    }

    private fun usageOf(data: String): Pair<Int, Int>? {
        val usage = runCatching {
            JsonParser.parseString(data).asJsonObject.getAsJsonObject("usage")
        }.getOrNull() ?: return null
        fun pick(key: String) = runCatching { usage.get(key).asInt }.getOrDefault(0)
        return pick("prompt_tokens") to pick("completion_tokens")
    }
}
