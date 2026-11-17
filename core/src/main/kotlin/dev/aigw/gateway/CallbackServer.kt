package dev.aigw.gateway

import fi.iki.elonen.NanoHTTPD

/**
 * 临时本地回调服务：浏览器登录完成后，上游把页面重定向到本机端口，
 * 由它接住回调并交给供应商换 token。
 *
 * 登录期间临时占用一个本地端口，收到回调或超时后释放。
 */
class CallbackServer(
    port: Int,
    private val path: String,
    private val onCallback: (callbackUrl: String) -> String,
) : NanoHTTPD("127.0.0.1", port) {

    fun startServer() {
        start(SOCKET_READ_TIMEOUT_MS, false)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri.trimEnd('/').ifEmpty { "/" }
        if (uri != path.trimEnd('/')) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
        }
        val query = session.queryParameterString.orEmpty()
        val callbackUrl = "http://127.0.0.1:$listeningPort$path" + if (query.isEmpty()) "" else "?$query"
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            onCallback(callbackUrl),
        )
    }

    private companion object {
        const val SOCKET_READ_TIMEOUT_MS = 10_000
    }
}

/** 登录结果页：给浏览器一个可读的收尾，而不是白屏。 */
internal fun loginResultPage(ok: Boolean, detail: String): String {
    val title = if (ok) "登录成功" else "登录失败"
    val color = if (ok) "#10B981" else "#F43F5E"
    val safe = detail.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    return """
        <!doctype html><html><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title</title></head>
        <body style="margin:0;height:100vh;display:flex;align-items:center;justify-content:center;
                     font-family:-apple-system,sans-serif;background:#F7F8FA">
          <div style="text-align:center;padding:32px">
            <div style="width:64px;height:64px;border-radius:50%;background:$color;margin:0 auto 20px"></div>
            <div style="font-size:20px;font-weight:700;color:#1A1D23">$title</div>
            <div style="margin-top:8px;font-size:13px;color:#5A6270">$safe</div>
            <div style="margin-top:20px;font-size:12px;color:#9AA2AE">可以关闭此页面，返回 App</div>
          </div>
        </body></html>
    """.trimIndent()
}
