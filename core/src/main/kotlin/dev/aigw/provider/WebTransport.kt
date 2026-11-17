package dev.aigw.provider

/**
 * 在网页环境里发请求的通道（由 App 层用 WebView 实现）。
 *
 * 某些上游的接口除了 token 还校验浏览器侧的风控参数（cookie / JS 生成的请求头），
 * 原生 HTTP 会被 403。此时把请求交给页面内的 fetch，等价于真实浏览器发起的请求。
 */
interface WebTransport {

    /**
     * 写入登录态并等待页面就绪。
     *
     * 只有需要账号 cookie 才能工作的通道（豆包）才实现；其余返回默认 true。
     */
    fun prepare(cookies: String): Boolean = true

    /**
     * 以页面身份 POST [path]（同源相对路径）。
     *
     * @param stream true 时逐行回调 [onLine]（收到完整一行才回调），返回 false 可提前结束
     */
    fun post(
        path: String,
        headers: Map<String, String>,
        body: String,
        stream: Boolean,
        onLine: ((String) -> Boolean)? = null,
    ): WebTransportResult
}

/** [error] 非空表示通道本身失败（WebView 未就绪、超时等）。 */
data class WebTransportResult(
    val status: Int,
    val body: String,
    val error: String = "",
) {
    val ok: Boolean get() = error.isEmpty() && status in 200..299
}
