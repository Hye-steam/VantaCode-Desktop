package dev.aigw.util

import dev.aigw.provider.ApiError
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 标记：该 [ProxySelector] 由网关自己安装。
 *
 * 网关需要在日志里说清流量到底走了哪条路（网关代理 / 系统代理 / 直连），
 * 否则连不上时无法判断该关哪一个。
 */
interface GatewayManagedSelector {

    /** 按当前设置说明该 URL 的实际路由，用于错误信息。 */
    fun describeRoute(uri: URI): String
}

/** 最小 HTTP 客户端（HttpURLConnection），供 Provider 打上游。 */
object Http {

    class Result(val status: Int, val body: String) {
        val ok: Boolean get() = status in 200..299
    }

    fun get(
        url: String,
        headers: Map<String, String>,
        timeoutMs: Int = 30_000,
        direct: Boolean = false,
    ): Result {
        val conn = open(url, timeoutMs, direct)
        try {
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val status = conn.responseCode
            val body = readBody(conn)
            return Result(status, body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * [direct] 为 true 时绕开全局选择器强制直连。
     *
     * 代理链路（节点失效、fake-ip 分流规则异常）出问题时，某些域名直连反而是通的；
     * 调用方需要一个不依赖代理的单次尝试，而不是只能接受代理失败的结果。
     */
    fun postJson(
        url: String,
        headers: Map<String, String>,
        json: String,
        timeoutMs: Int = 30_000,
        direct: Boolean = false,
    ): Result {
        val conn = open(url, timeoutMs, direct)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val out = conn.outputStream
            out.write(json.toByteArray(StandardCharsets.UTF_8))
            out.flush()
            out.close()
            val status = conn.responseCode
            val body = readBody(conn)
            return Result(status, body)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * POST `application/x-www-form-urlencoded`。[body] 需已按 `k=v&k=v` 并完成 URL 编码。
     *
     * 单独提供此方法而不是复用 [postJson]：表单接口（如第三方工单服务）对
     * Content-Type 敏感，用 JSON 会被拒。
     */
    fun postForm(url: String, headers: Map<String, String>, body: String, timeoutMs: Int = 30_000): Result {
        val conn = open(url, timeoutMs)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val out = conn.outputStream
            out.write(body.toByteArray(StandardCharsets.UTF_8))
            out.flush()
            out.close()
            val status = conn.responseCode
            val response = readBody(conn)
            return Result(status, response)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * POST JSON 并以流方式逐行读回（SSE 场景）。
     * 读到 [onLine] 返回 false 时停止读取（客户端断开）。
     * 结束后（EOF 或提前中断）执行 [onDone]。
     */
    fun postStream(
        url: String,
        headers: Map<String, String>,
        json: String,
        timeoutMs: Int = 300_000,
        onLine: (String) -> Boolean,
        onDone: () -> Unit,
    ): Result {
        val conn = open(url, timeoutMs)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val out = conn.outputStream
            out.write(json.toByteArray(StandardCharsets.UTF_8))
            out.flush()
            out.close()
            val status = conn.responseCode
            if (status !in 200..299) {
                return Result(status, readBody(conn))
            }
            val reader = conn.inputStream.bufferedReader(StandardCharsets.UTF_8)
            try {
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) continue
                    if (!onLine(line)) break
                }
            } finally {
                reader.close()
                onDone()
            }
            return Result(status, "")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * POST multipart/form-data（单文件字段）。上游文件上传接口不收 JSON，
     * 这里单独实现，避免把二进制塞进字符串。
     */
    fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fieldName: String,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        timeoutMs: Int = 120_000,
    ): Result {
        val boundary = "----aigw" + System.nanoTime().toString(16)
        val safeName = fileName.replace("\"", "_").replace("\r", "").replace("\n", "")
        val conn = open(url, timeoutMs)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            headers.forEach { (k, v) ->
                if (!k.equals("content-type", ignoreCase = true)) conn.setRequestProperty(k, v)
            }
            val out = conn.outputStream
            val head = "--$boundary\r\n" +
                "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$safeName\"\r\n" +
                "Content-Type: $contentType\r\n\r\n"
            out.write(head.toByteArray(StandardCharsets.UTF_8))
            out.write(bytes)
            out.write("\r\n--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
            out.flush()
            out.close()
            val status = conn.responseCode
            val body = readBody(conn)
            return Result(status, body)
        } finally {
            conn.disconnect()
        }
    }

    /** multipart 的单个文件字段。 */
    class FilePart(
        val fieldName: String,
        val fileName: String,
        val contentType: String,
        val bytes: ByteArray,
    )

    /**
     * POST multipart/form-data（多个文本字段 + 多个文件字段）。
     *
     * 工单接口要同时带文本参数与多张图片，而只接受文件上传的接口
     * （如第三方后台的 `images[]`）不收 base64，这里一次性组装完整 body。
     */
    fun postMultipartFields(
        url: String,
        headers: Map<String, String>,
        fields: List<Pair<String, String>>,
        files: List<FilePart>,
        timeoutMs: Int = 120_000,
    ): Result {
        val boundary = "----aigw" + System.nanoTime().toString(16)
        val buf = ByteArrayOutputStream()
        for ((name, value) in fields) {
            buf.write(
                ("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"$name\"\r\n\r\n" +
                    value + "\r\n").toByteArray(StandardCharsets.UTF_8),
            )
        }
        for (f in files) {
            val safeName = f.fileName.replace("\"", "_").replace("\r", "").replace("\n", "")
            buf.write(
                ("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"${f.fieldName}\"; filename=\"$safeName\"\r\n" +
                    "Content-Type: ${f.contentType}\r\n\r\n").toByteArray(StandardCharsets.UTF_8),
            )
            buf.write(f.bytes)
            buf.write("\r\n".toByteArray(StandardCharsets.UTF_8))
        }
        buf.write("--$boundary--\r\n".toByteArray(StandardCharsets.UTF_8))
        val payload = buf.toByteArray()

        val conn = open(url, timeoutMs)
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            headers.forEach { (k, v) ->
                if (!k.equals("content-type", ignoreCase = true)) conn.setRequestProperty(k, v)
            }
            conn.setFixedLengthStreamingMode(payload.size)
            val out = conn.outputStream
            out.write(payload)
            out.flush()
            out.close()
            val status = conn.responseCode
            val body = readBody(conn)
            return Result(status, body)
        } finally {
            conn.disconnect()
        }
    }

    /** 把非 2xx 上游响应分类成 ApiError；2xx 返回 null。 */
    fun classify(result: Result): ApiError? {
        if (result.ok) return null
        return when (result.status) {
            401, 403 -> ApiError(ApiError.Kind.AUTH, result.status, result.body.take(500))
            in 400..499 -> ApiError(ApiError.Kind.CLIENT, result.status, result.body.take(500))
            else -> ApiError(ApiError.Kind.UPSTREAM, result.status, result.body.take(500))
        }
    }

    fun networkError(e: Exception): ApiError =
        ApiError(ApiError.Kind.NETWORK, 0, (e.message ?: e.javaClass.simpleName).take(300))

    /**
     * 该 URL 按全局 ProxySelector 走哪条路由。
     *
     * 连接类异常（尤其是开了 VPN 之后才出现的）分不清是上游拒绝还是本地代理/隧道掐断，
     * 把实际路由拼进错误信息，日志里才能直接判断该关代理还是换网络。
     */    fun routeOf(url: String): String {
        val uri = runCatching { URI(url) }.getOrNull() ?: return "地址无效"
        val selector = runCatching { ProxySelector.getDefault() }.getOrNull()
        val route = if (selector is GatewayManagedSelector) {
            runCatching { selector.describeRoute(uri) }.getOrDefault("未知")
        } else {
            describePlain(selector, uri)
        }
        // 解析结果一并带上：VPN 以 TUN + fake-ip 接管时这里会是 198.18.x.x 之类的内网段，
        // 与真实 CDN 地址一模一样地「连不上」时，这是区分两类问题的直接证据
        val address = uri.host?.let { runCatching { InetAddress.getByName(it).hostAddress }.getOrNull() }
        return if (address == null) route else "$route，解析 $address"
    }

    private fun describePlain(selector: ProxySelector?, uri: URI): String {
        val proxy = runCatching { selector?.select(uri)?.firstOrNull() }.getOrNull()
        val address = proxy?.address() as? InetSocketAddress
        if (proxy == null || proxy.type() == Proxy.Type.DIRECT || address == null) return "直连"
        return "经代理 ${proxy.type()} ${address.hostString}:${address.port}"
    }

    private fun open(url: String, timeoutMs: Int, direct: Boolean = false): HttpURLConnection {
        val target = URL(url)
        val raw = if (direct) target.openConnection(Proxy.NO_PROXY) else target.openConnection()
        val conn = (raw as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            // 上游重定向会打乱 SSE 解析与鉴权判断，显式关闭（与原项目一致）
            instanceFollowRedirects = false
            // 流式路径强制不压缩：Android 的 HttpURLConnection 默认发 Accept-Encoding: gzip，
            // 上游若对 SSE 响应做了 gzip，首字节会因压缩缓冲而明显延迟，表现为「卡住后一次性吐出」。
            setRequestProperty("Accept-Encoding", "identity")
        }
        return conn
    }

    private fun readBody(conn: HttpURLConnection): String {
        val stream: InputStream = try {
            conn.inputStream
        } catch (_: Exception) {
            conn.errorStream
        } ?: return ""
        return try {
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val n = stream.read(chunk)
                if (n < 0) break
                buf.write(chunk, 0, n)
            }
            buf.toString("UTF-8")
        } finally {
            stream.close()
        }
    }
}
