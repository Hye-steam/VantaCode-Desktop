package dev.aigw.gateway

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.ChatResult
import dev.aigw.provider.SseSink
import dev.aigw.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.nio.charset.StandardCharsets
import dev.aigw.util.Http

/**
 * 网关 HTTP 服务：NanoHTTPD 实现（Android 上验证过的纯 Java 服务端，
 * 不用 JDK 的 com.sun.net.httpserver——后者在 Android 运行时实现不可靠）。
 */
class GatewayHttpServer(private val engine: Engine) {

    private var server: NanoServer? = null

    /** 本次运行开始时间（毫秒）；未运行时为 0。 */
    @Volatile
    var startedAt: Long = 0L
        private set

    val port: Int get() = engine.settings.port

    fun isRunning(): Boolean = server != null

    fun start() {
        if (server != null) return
        val s = engine.settings
        // 媒体中转要用真实端口拼本地地址
        MediaProxy.port = s.port
        val host = if (s.bindLan) "0.0.0.0" else "127.0.0.1"
        val nano = NanoServer(host, s.port, engine)
        nano.start(300_000, true)
        server = nano
        startedAt = System.currentTimeMillis()
        engine.eventLog.beginSession()
        engine.eventLog.info("网关", "已启动 http://$host:${s.port}/v1")
        Log.info("网关已启动: http://$host:${s.port}/v1")
    }

    fun stop() {
        val running = server ?: return
        // 先摘掉引用：isRunning() 立刻变 false，UI 不必等后台收尾。
        // NanoHTTPD.stop() 会同步等待连接线程退出（默认最长 5 秒），
        // 留在主线程会直接冻住界面；它内部会先关监听 socket，端口随即释放，
        // 所以放到后台线程收尾是安全的，立刻重启也不会撞端口。
        server = null
        startedAt = 0L
        engine.eventLog.info("网关", "已停止")
        Log.info("网关已停止")
        Thread {
            runCatching { running.stop() }
                .onFailure { Log.warn("停止网关时出错：${it.message}") }
        }.start()
    }

    private class NanoServer(
        host: String,
        port: Int,
        private val engine: Engine,
    ) : NanoHTTPD(host, port) {

        override fun serve(session: IHTTPSession): Response {
            return try {
                if (session.method == Method.OPTIONS) {
                    return cors(newFixedLengthResponse(Response.Status.NO_CONTENT, "text/plain", ""))
                }
                // 图片/视频中转必须在鉴权之前：Markdown 里内嵌的资源请求不带 Authorization 头。
                // 安全性由 MediaProxy 的域名白名单保证。
                if (session.method == Method.GET && session.uri == "/v1/media") {
                    return handleMedia(session)
                }
                if (session.method == Method.GET && session.uri.startsWith("/v1/media/blob/")) {
                    return handleBlob(session)
                }
                if (!authorized(session)) {
                    engine.eventLog.debug("网关", "鉴权失败：${session.method} ${session.uri}")
                    return jsonResponse(Response.Status.UNAUTHORIZED, errorJson("invalid_api_key", "Invalid API key provided"))
                }
                val uri = session.uri
                when {
                    session.method == Method.POST && uri == "/v1/chat/completions" -> handleChat(session)
                    session.method == Method.POST &&
                        (uri == "/v1/images/generations" || uri == "/images/generations") -> handleImages(session)
                    session.method == Method.GET && uri == "/v1/models" -> {
                        val force = session.queryParameterString?.contains("force=1") == true
                        jsonResponse(Response.Status.OK, engine.listModels(force))
                    }
                    session.method == Method.GET && (uri == "/healthz" || uri == "/health") ->
                        cors(newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "ok"))
                    session.method == Method.GET && (uri == "/v1/credits" || uri == "/credits") -> handleCredits()
                    else -> {
                        // 客户端请求了未实现的接口时，日志里要能直接看到路径（如 /v1/files）
                        engine.eventLog.warn("网关", "未实现的接口：${session.method} $uri")
                        jsonResponse(Response.Status.NOT_FOUND, errorJson("not_found", "Not Found: $uri"))
                    }
                }
            } catch (e: Exception) {
                Log.error("请求处理异常: ${e.message}")
                engine.eventLog.error(
                    "网关",
                    "${session.method} ${session.uri} 处理异常：${e.javaClass.simpleName}: ${e.message}\n" +
                        e.stackTraceToString().take(1200),
                )
                jsonResponse(
                    Response.Status.INTERNAL_ERROR,
                    errorJson("internal_error", "internal error: ${e.message}"),
                )
            }
        }

        /**
         * 把上游媒体取回来原样转发。
         *
         * 走的是同一条 HTTP 路径给客户端，好处是绕开客户端对外域名的各种策略，
         * 也不受签名 URL 的长度和特殊字符影响。
         */
        /** 网关本地缓存的媒体（即梦这类上游链接会过期的图片）。 */
        private fun handleBlob(session: IHTTPSession): Response {
            val id = session.uri.removePrefix("/v1/media/blob/").substringBefore('?')
            val bytes = MediaProxy.load(id)
                ?: return jsonResponse(Response.Status.NOT_FOUND, errorJson("not_found", "blob missing"))
            val type = when {
                id.endsWith(".png") -> "image/png"
                id.endsWith(".webp") -> "image/webp"
                else -> "image/jpeg"
            }
            return cors(
                newFixedLengthResponse(
                    Response.Status.OK,
                    type,
                    java.io.ByteArrayInputStream(bytes),
                    bytes.size.toLong(),
                ),
            )
        }

        private fun handleMedia(session: IHTTPSession): Response {
            val raw = session.parameters["u"]?.firstOrNull().orEmpty()
            if (raw.isEmpty()) {
                return jsonResponse(Response.Status.BAD_REQUEST, errorJson("invalid_request", "missing u"))
            }
            // NanoHTTPD 解析 query 时已经解过一次码，这里不能再用 URLDecoder：
            // 二次解码会把签名里的 %3D 变成 =，上游直接 403。
            val target = raw.trim()
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                return jsonResponse(Response.Status.BAD_REQUEST, errorJson("invalid_request", "bad scheme"))
            }
            if (!MediaProxy.allowed(target)) {
                engine.eventLog.warn("网关", "拒绝中转非白名单域名：${target.take(120)}")
                return jsonResponse(Response.Status.FORBIDDEN, errorJson("forbidden", "host not allowed"))
            }

            return try {
                val conn = (java.net.URL(target).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                    setRequestProperty("Referer", "https://www.doubao.com/")
                }
                try {
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        return jsonResponse(
                            Response.Status.lookup(code) ?: Response.Status.INTERNAL_ERROR,
                            errorJson("upstream_error", "media upstream $code"),
                        )
                    }
                    val bytes = conn.inputStream.readBytes()
                    val type = conn.contentType?.substringBefore(';') ?: "image/jpeg"
                    cors(
                        newFixedLengthResponse(
                            Response.Status.OK,
                            type,
                            java.io.ByteArrayInputStream(bytes),
                            bytes.size.toLong(),
                        ),
                    )
                } finally {
                    runCatching { conn.disconnect() }
                }
            } catch (e: Exception) {
                engine.eventLog.warn("网关", "中转媒体失败：${e.javaClass.simpleName}: ${e.message}")
                jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("internal_error", "media fetch failed"))
            }
        }

        /**
         * 按 Content-Length 读原始请求体并按 UTF-8 解码。
         *
         * 不能用 NanoHTTPD 的 parseBody：它把 body 交给表单分支解析，
         * 中文会按表单 charset 解成 U+FFFD（上游收到乱码）；
         * 而 parseBody 之后 getInputStream() 已被消耗，也读不回原始字节。
         */
        private fun readUtf8Body(session: IHTTPSession): String {
            val len = session.headers["content-length"]?.trim()?.toIntOrNull() ?: 0
            if (len <= 0) return ""
            val buf = ByteArray(len)
            var read = 0
            try {
                val ins = session.inputStream
                while (read < len) {
                    val n = ins.read(buf, read, len - read)
                    if (n <= 0) break
                    read += n
                }
            } catch (_: Exception) {
                return ""
            }
            return String(buf, 0, read, StandardCharsets.UTF_8)
        }

        private fun handleChat(session: IHTTPSession): Response {
            val bodyText = readUtf8Body(session)
            val body = runCatching { JsonParser.parseString(bodyText).asJsonObject }.getOrElse {
                engine.eventLog.warn("网关", "请求体不是合法 JSON：${bodyText.take(200)}")
                return jsonResponse(Response.Status.BAD_REQUEST, errorJson("invalid_request", "body must be a JSON object"))
            }
            val stream = body.get("stream")?.asBoolean ?: false
            val sessionId = session.headers["x-session-id"]?.takeIf { it.isNotBlank() }

            if (!stream) {
                return when (val outcome = engine.chat(body, null, sessionId)) {
                    is Engine.Outcome.Ok -> {
                        val json = (outcome.result as? ChatResult.Aggregated)?.json
                        jsonResponse(Response.Status.OK, json ?: emptyChat())
                    }
                    is Engine.Outcome.Error -> jsonResponse(
                        statusOf(outcome.error.status),
                        errorJson("upstream_error", outcome.error.message),
                    )
                }
            }

            // 流式：管道把 Provider 的 SSE 事件推给 NanoHTTPD 的 chunked 响应
            val pipeOut = PipedOutputStream()
            val pipeIn = PipedInputStream(pipeOut, 64 * 1024)
            val response = newChunkedResponse(Response.Status.OK, "text/event-stream; charset=utf-8", pipeIn)
            response.addHeader("Cache-Control", "no-cache")
            cors(response)

            Thread {
                val sink = object : SseSink {
                    private var done = false

                    override fun onData(data: String) {
                        if (done) return
                        if (data == Sse.DONE) done = true
                        runCatching {
                            pipeOut.write(Sse.frame(data).toByteArray(StandardCharsets.UTF_8))
                            pipeOut.flush()
                        }
                    }

                    override fun onDone() {
                        if (done) return
                        done = true
                        runCatching {
                            pipeOut.write(Sse.frame(Sse.DONE).toByteArray(StandardCharsets.UTF_8))
                            pipeOut.flush()
                        }
                    }
                }
                try {
                    val outcome = engine.chat(body, sink, sessionId)
                    if (outcome is Engine.Outcome.Error) {
                        runCatching {
                            pipeOut.write(
                                Sse.frame(errorJson("upstream_error", outcome.error.message).toString())
                                    .toByteArray(StandardCharsets.UTF_8),
                            )
                            pipeOut.flush()
                        }
                    } else if (outcome is Engine.Outcome.Ok) {
                        // Provider 一次性给出完整结果时（生图/生视频这类），
                        // 它不会调 sink；这里补成标准的流式帧，否则客户端收不到任何东西。
                        val json = (outcome.result as? ChatResult.Aggregated)?.json
                        if (json != null) {
                            runCatching {
                                val content = json.getAsJsonArray("choices")
                                    ?.firstOrNull()?.asJsonObject
                                    ?.getAsJsonObject("message")
                                    ?.get("content")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                                if (content.isNotEmpty()) {
                                    pipeOut.write(Sse.frame(chunkJson(content)).toByteArray(StandardCharsets.UTF_8))
                                    pipeOut.flush()
                                }
                                pipeOut.write(Sse.frame(Sse.DONE).toByteArray(StandardCharsets.UTF_8))
                                pipeOut.flush()
                            }
                        }
                    }
                } finally {
                    runCatching { pipeOut.close() }
                }
            }.apply { isDaemon = true }.start()

            return response
        }

        /**
         * 图片生成（OpenAI 兼容）。
         *
         * 目前只有 OVHcloud 的 SDXL 支持这条路，匿名 2 次/分钟。
         * 它返回 b64_json，这里原样透传，客户端自己解码。
         */
        private fun handleImages(session: IHTTPSession): Response {
            val bodyText = readUtf8Body(session)
            val body = runCatching { JsonParser.parseString(bodyText).asJsonObject }.getOrElse {
                return jsonResponse(Response.Status.BAD_REQUEST, errorJson("invalid_request", "body must be a JSON object"))
            }
            val prompt = body.get("prompt")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (prompt.isBlank()) {
                return jsonResponse(Response.Status.BAD_REQUEST, errorJson("invalid_request", "prompt is required"))
            }
            val size = body.get("size")?.takeIf { it.isJsonPrimitive }?.asString ?: "1024x1024"
            val n = body.get("n")?.takeIf { it.isJsonPrimitive }?.asInt ?: 1

            val upstream = JsonObject().apply {
                addProperty("model", "stable-diffusion-xl-base-v10")
                addProperty("prompt", prompt)
                addProperty("n", n.coerceIn(1, 4))
                addProperty("size", size)
            }
            val sb = StringBuilder()
            val r = Http.postStream(
                url = "https://oai.endpoints.kepler.ai.cloud.ovh.net/v1/images/generations",
                headers = mapOf("Content-Type" to "application/json"),
                json = upstream.toString(),
                timeoutMs = 180_000,
                onLine = { line -> sb.append(line); true },
                onDone = {},
            )
            if (!r.ok) {
                engine.eventLog.warn("图片", "OVHcloud 返回 ${r.status}")
                return jsonResponse(
                    Response.Status.lookup(r.status) ?: Response.Status.INTERNAL_ERROR,
                    errorJson("upstream_error", "OVHcloud ${r.status}"),
                )
            }
            engine.eventLog.info("图片", "OVHcloud SDXL · ${prompt.take(40)} · $size · n=$n")
            val parsed = runCatching { JsonParser.parseString(sb.toString()).asJsonObject }.getOrNull()
                ?: return jsonResponse(Response.Status.INTERNAL_ERROR, errorJson("upstream_error", "upstream returned non-JSON"))
            return jsonResponse(Response.Status.OK, parsed)
        }

        private fun handleCredits(): Response {
            val arr = JsonArray()
            engine.pool.all().forEach { e ->
                arr.add(JsonObject().apply {
                    addProperty("provider", e.account.providerId)
                    addProperty("uid", e.account.uid)
                    addProperty("display_name", e.account.displayName)
                    addProperty("enabled", e.state.enabled)
                    addProperty("balance", e.state.balance)
                    addProperty("disabled_reason", e.state.disabledReason)
                })
            }
            return jsonResponse(Response.Status.OK, JsonObject().apply { add("accounts", arr) })
        }

        private fun authorized(session: IHTTPSession): Boolean {
            val s = engine.settings
            if (s.allowNoKey || s.apiKey.isEmpty()) return true
            val expected = s.apiKey
            val auth = session.headers["authorization"].orEmpty()
            if (auth.startsWith("Bearer ") && auth.removePrefix("Bearer ").trim() == expected) return true
            if (session.headers["x-goog-api-key"] == expected) return true
            if (session.parameters["key"]?.firstOrNull() == expected) return true
            return false
        }

        private fun statusOf(code: Int): Response.IStatus = when (code) {
            400 -> Response.Status.BAD_REQUEST
            401 -> Response.Status.UNAUTHORIZED
            403 -> Response.Status.FORBIDDEN
            404 -> Response.Status.NOT_FOUND
            500, 502, 503 -> Response.Status.INTERNAL_ERROR
            else -> Response.Status.OK
        }

        private fun jsonResponse(status: Response.IStatus, json: JsonObject): Response =
            cors(newFixedLengthResponse(status, "application/json; charset=utf-8", json.toString()))

        private fun cors(response: Response): Response {
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, x-goog-api-key")
            return response
        }

        private fun errorJson(code: String, message: String): JsonObject =
            JsonObject().apply {
                add("error", JsonObject().apply {
                    addProperty("message", message)
                    addProperty("type", "invalid_request_error")
                    addProperty("code", code)
                })
            }

        private fun emptyChat(): JsonObject =
            JsonObject().apply {
                addProperty("id", "chatcmpl-empty")
                addProperty("object", "chat.completion")
                addProperty("created", System.currentTimeMillis() / 1000)
                add("model", com.google.gson.JsonNull.INSTANCE)
                add("choices", JsonArray())
            }

        /** 把一次性结果包成 OpenAI 流式 delta 帧。 */
        private fun chunkJson(content: String): String =
            JsonObject().apply {
                addProperty("id", "chatcmpl-" + System.currentTimeMillis())
                addProperty("object", "chat.completion.chunk")
                addProperty("created", System.currentTimeMillis() / 1000)
                add("model", com.google.gson.JsonNull.INSTANCE)
                add("choices", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("index", 0)
                        add("delta", JsonObject().apply {
                            addProperty("role", "assistant")
                            addProperty("content", content)
                        })
                        add("finish_reason", com.google.gson.JsonNull.INSTANCE)
                    })
                })
            }.toString()
    }
}
