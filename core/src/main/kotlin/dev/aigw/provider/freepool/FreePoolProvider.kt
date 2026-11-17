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

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val modelKey = request.body.get("model")?.let { if (it.isJsonPrimitive) it.asString else "" }.orEmpty()
        val (channel, realModel) = split(modelKey)
            ?: return ChatResult.Failure(
                ApiError(ApiError.Kind.CLIENT, 400, "未知的免费池模型「$modelKey」"),
            )

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
