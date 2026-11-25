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
import dev.aigw.provider.WebTokenSupport
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
class MuseProvider(private val transport: () -> WebTransport?) : Provider, WebTokenSupport {

    override val id = ID
    override val displayName = "muse.ai"

    companion object {
        const val ID = "muse"

        /** 对外模型名 → 期望的产物类型。 */
        val MODELS = linkedMapOf(
            "muse-image" to "image",
            "muse-video" to "video",
        )

        /** 只描述动作、不含画面主体的说法；命中后要检查剩下多少描述。 */
        private val LACKS_SUBJECT = Regex(
            "(生成|制作|创建|做|来|帮我|请|给我|一段|一个|一张|一幅|图片|图像|视频|短片|动画)" +
                "|\\b(generate|make|create|draw|paint|a|an|video|clip|image|picture|short)\\b",
            RegexOption.IGNORE_CASE,
        )

        /** 提示词里只剩这些套话、没有实际画面描述时，muse 会反问而不是生成。 */
        private fun hasConcreteSubject(prompt: String): Boolean {
            val stripped = LACKS_SUBJECT.replace(prompt, "")
            return stripped.count { it.isLetterOrDigit() } >= 2
        }
    }

    /** 网页登录入口：muse 登录后 cookie 里就有 hatch_sess。 */
    override val loginPageUrl = "https://muse.ai/"

    /**
     * 用页面登录后拿到的 cookie 建账号。
     *
     * uid 取 hatch_sess 前 8 位：同一账号重复登录会覆盖而不是新增。
     */
    override fun loginWithToken(token: String): ProviderAccount {
        val ck = token.trim()
        require(ck.isNotEmpty()) { "cookie 为空" }
        require(ck.contains("hatch_sess")) { "cookie 里缺少 hatch_sess，登录可能没完成" }
        val sess = Regex("hatch_sess=([^;\\s]+)").find(ck)?.groupValues?.get(1).orEmpty()
        require(sess.isNotEmpty()) { "无法从 cookie 解析 hatch_sess" }
        val uid = sess.take(8)
        return ProviderAccount(
            providerId = ID,
            uid = uid,
            displayName = "muse $uid",
            secret = ck,
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

        // 「生成一张图片」「生成一段视频」这类只说动作、没说内容的提示词，
        // muse 会反问要生成什么，页面永远不会出现产物。
        // 直接报错比让客户端空等 15 分钟好。
        if (!hasConcreteSubject(prompt)) {
            val what = if (expect == "video") "视频" else "图片"
            return ChatResult.Failure(
                ApiError(
                    ApiError.Kind.CLIENT, 400,
                    "需要描述${what}内容，例如「一只猫在窗台上晒太阳」。「$prompt」缺少具体画面，muse 会反问而不是生成。",
                ),
            )
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
            // 先按状态码定性质，再看错误文本。
            // 反过来会让所有带 error 字段的失败都被当成 NETWORK，
            // 连带把「生成超时」这种上游慢也算成账号失败，攒够阈值就冷却。
            val kind = when {
                result.status == 401 || result.status == 403 -> ApiError.Kind.AUTH
                result.status == 504 -> ApiError.Kind.UPSTREAM
                result.status in 400..499 -> ApiError.Kind.CLIENT
                result.status >= 500 -> ApiError.Kind.UPSTREAM
                result.error.isNotEmpty() -> ApiError.Kind.NETWORK
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
     * 图片和视频都落盘后给本地路径：VantaCode 的 Markdown 渲染器只认本地
     * 文件路径，`data:` URL 和 `://` 开头的一律不渲染，内联了也看不到。
     */
    private fun buildMarkdown(media: Media, expect: String): String {
        val mime = media.mime.ifEmpty { if (expect == "video") "video/mp4" else "image/png" }
        val sizeMb = media.size / 1048576.0
        val isVideo = mime.startsWith("video/")
        val ext = when {
            mime.contains("webm") -> ".webm"
            mime.contains("quicktime") -> ".mov"
            mime.contains("png") -> ".png"
            mime.contains("jpeg") || mime.contains("jpg") -> ".jpg"
            mime.contains("gif") -> ".gif"
            mime.contains("webp") -> ".webp"
            else -> if (isVideo) ".mp4" else ".png"
        }
        val path = MediaProxy.saveBase64(media.base64, ext)
            ?: return "已生成${if (isVideo) "视频" else "图片"}（${String.format("%.1f", sizeMb)} MB），但写入本地失败。"
        // 两种产物都用 Markdown 图片语法：客户端只认 ![](...)，
        // 裸路径会当普通文字显示，视频播放器也不会出现。
        val label = if (isVideo) "muse 视频" else "muse 生成"
        return "![$label]($path)"
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
