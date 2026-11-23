package dev.aigw.provider.muse

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.aigw.gateway.MediaProxy
import dev.aigw.provider.ApiError
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.SseSink
import dev.aigw.provider.WebTransport
import dev.aigw.util.Log

/**
 * muse.ai 生图 / 生视频。
 *
 * 走的是页面内操作，不是 HTTP 接口：muse.ai 的生成能力只对「浏览器里真实打开
 * 的页面」下发，VM 端点是内网地址（公网 NXDOMAIN），`/api/session` 只能续期
 * 和唤醒，拿不到生成入口。
 *
 * 所以这里把请求交给隐藏 WebView：填 textarea → 点发送 → 等附件容器出现 →
 * 把 blob 读成 base64。真正干活的是 [WebTransport] 的页面操作能力。
 *
 * 模型名约定：
 * - `muse-image`  出图
 * - `muse-video`  出视频
 */
class MuseProvider(private val transport: () -> WebTransport?) : Provider {

    override val id = ID
    override val displayName = "muse.ai"

    companion object {
        const val ID = "muse"

        /** 对外模型名 → 期望的产物类型。 */
        val MODELS = linkedMapOf(
            "muse-image" to "image",
            "muse-video" to "video",
        )
    }

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        Result.success(MODELS.keys.toList())

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val channel = transport() ?: return ChatResult.Failure(
            ApiError(ApiError.Kind.UPSTREAM, 0, "muse 需要页面通道，当前环境不可用"),
        )
        val expect = MODELS[request.model]
            ?: return ChatResult.Failure(
                ApiError(ApiError.Kind.CLIENT, 400, "未知模型 ${request.model}"),
            )

        val prompt = lastUserText(request.body)
        if (prompt.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "muse 需要非空的用户消息"))
        }

        // muse 默认把请求当生图处理，视频必须在提示词里明确要求
        val finalPrompt = if (expect == "video") "Generate a video: $prompt" else prompt

        // 账号 secret 就是登录 cookie，每次交给通道（页面会自动续期）
        if (account.secret.isNotBlank()) {
            channel.prepare(account.secret)
        }

        // 页面通道以 GET 语义提交一次生成任务，返回的是产物描述
        val path = "/__muse/generate?expect=$expect"
        val body = JsonObject().apply {
            addProperty("prompt", finalPrompt)
            addProperty("expect", expect)
        }.toString()

        val result = channel.post(path, emptyMap(), body, stream = false)
        if (!result.ok) {
            val kind = when {
                result.error.isNotEmpty() -> ApiError.Kind.NETWORK
                result.status == 401 || result.status == 403 -> ApiError.Kind.AUTH
                result.status in 400..499 -> ApiError.Kind.CLIENT
                else -> ApiError.Kind.UPSTREAM
            }
            Log.warn("muse 生成失败 ${result.status}：${result.error.ifEmpty { result.body.take(160) }}")
            return ChatResult.Failure(ApiError(kind, result.status, result.error.ifEmpty { result.body.take(300) }))
        }

        val media = runCatching { parseMedia(result.body) }.getOrNull()
            ?: return ChatResult.Failure(
                ApiError(ApiError.Kind.UPSTREAM, 502, "muse 返回无法解析：${result.body.take(200)}"),
            )

        val markdown = buildMarkdown(media, expect)
        return ChatResult.Aggregated(textResponse(request.model, markdown))
    }

    private data class Media(val mime: String, val base64: String, val size: Long)

    private fun parseMedia(raw: String): Media? {
        val obj = runCatching {
            com.google.gson.JsonParser.parseString(raw).asJsonObject
        }.getOrNull() ?: return null
        if (obj.get("ok")?.takeIf { it.isJsonPrimitive }?.asBoolean != true) return null
        val b64 = obj.get("b64")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (b64.isEmpty()) return null
        return Media(
            mime = obj.get("mime")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            base64 = b64,
            size = obj.get("size")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0,
        )
    }

    /**
     * 产物转 markdown。
     *
     * 图片走 data URL 直接内联——体积小，客户端能直接渲染。
     * 视频动辄几十 MB，内联会把响应和客户端一起撞爆，所以落盘后给本地路径。
     */
    private fun buildMarkdown(media: Media, expect: String): String {
        val mime = media.mime.ifEmpty { if (expect == "video") "video/mp4" else "image/png" }
        val sizeMb = media.size / 1048576.0
        return if (mime.startsWith("video/")) {
            val ext = when {
                mime.contains("webm") -> ".webm"
                mime.contains("quicktime") -> ".mov"
                else -> ".mp4"
            }
            val path = MediaProxy.saveBase64(media.base64, ext)
            if (path != null) {
                "已生成视频（${String.format("%.1f", sizeMb)} MB）。\n\n`$path`"
            } else {
                "已生成视频（${String.format("%.1f", sizeMb)} MB），但写入本地失败。"
            }
        } else {
            "![muse 生成](data:$mime;base64,${media.base64})"
        }
    }

    /** 取最后一条 user 消息的纯文本。 */
    private fun lastUserText(body: JsonObject): String {
        val messages = body.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray ?: return ""
        var text = ""
        for (el in messages) {
            val msg = runCatching { el.asJsonObject }.getOrNull() ?: continue
            val role = msg.get("role")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            if (role != "user") continue
            val content = msg.get("content") ?: continue
            text = when {
                content.isJsonPrimitive -> content.asString
                content.isJsonArray -> content.asJsonArray.mapNotNull { partEl ->
                    runCatching { partEl.asJsonObject }.getOrNull()
                        ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                }.joinToString("")
                else -> ""
            }
        }
        return text.trim()
    }

    private fun textResponse(model: String, content: String): JsonObject = JsonObject().apply {
        addProperty("id", "muse-" + System.currentTimeMillis())
        addProperty("object", "chat.completion")
        addProperty("created", System.currentTimeMillis() / 1000)
        addProperty("model", model)
        add("choices", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("index", 0)
                add("message", JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", content)
                })
                addProperty("finish_reason", "stop")
            })
        })
    }
}
