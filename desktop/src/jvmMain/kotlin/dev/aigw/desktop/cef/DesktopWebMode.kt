package dev.aigw.desktop.cef

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefCompletionCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 桌面模式伪装（由 Android 版 DesktopWebMode 原样移植）。
 *
 * 桌面 UA + 1280 viewport + navigator/screen 伪装脚本，让按视口分流的页面
 * 出桌面布局（WorkBuddy 出二维码登录而不是微信-only 移动页）。
 * JCEF 里分两层实现：
 * - UA：全局 CefSettings.user_agent（GateCef 已设）。
 * - HTML 注入：[DesktopModeRequestHandler] 拦截导航 GET 请求，用 HttpURLConnection
 *   取回 HTML、注入 [inject]，再以 CefResourceHandler 吐给页面（与 Android 的
 *   shouldInterceptRequest 同款逻辑，含 Cookie 透传）。
 * - 运行时兜底：[viewportFixJs] 在页面加载后执行，收敛 viewport meta。
 */
object DesktopWebMode {

    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** 页面按 1070px 分界，取 1280 留出余量。 */
    private const val DESKTOP_WIDTH = 1280

    private val VIEWPORT_META_RE =
        Regex("<meta[^>]*name\\s*=\\s*[\"']?viewport[\"']?[^>]*>", RegexOption.IGNORE_CASE)

    private const val VIEWPORT_META = "<meta name=\"viewport\" content=\"width=$DESKTOP_WIDTH\">"

    /**
     * 把伪装脚本与 1280 viewport 注入页面 HTML。
     *
     * 顺序很重要：脚本在 `<head>` 开头（先于页面脚本），viewport 在 `</head>` 前
     * （成为最后一条，覆盖页面自带的）。
     */
    fun inject(html: String): String {
        val cleaned = VIEWPORT_META_RE.replace(html, "")
        val withScript = insertAfterHeadOpen(cleaned, hintScript)
        val end = withScript.lowercase().lastIndexOf("</head>")
        return if (end >= 0) {
            withScript.substring(0, end) + VIEWPORT_META + withScript.substring(end)
        } else {
            withScript + VIEWPORT_META
        }
    }

    private fun insertAfterHeadOpen(html: String, snippet: String): String {
        val lower = html.lowercase()
        for (tag in listOf("<head>", "<head ")) {
            val i = lower.indexOf(tag)
            if (i >= 0) {
                val close = html.indexOf('>', i)
                if (close >= 0) return html.substring(0, close + 1) + snippet + html.substring(close + 1)
            }
        }
        return snippet + html
    }

    /**
     * 运行时兜底：页面脚本可能动态写回 viewport。
     *
     * 现代浏览器只认最后一条 viewport meta，所以这里做两件事：收敛成唯一一条
     * 1280 宽的，并用 MutationObserver 盯住 `<head>`，把后续写回的覆盖掉。
     */
    val viewportFixJs: String = buildString {
        append("function vfix(){try{")
        append("var ms=document.querySelectorAll('meta[name=\"viewport\"]'),keep=null,i,m,c;")
        append("for(i=0;i<ms.length;i++){m=ms[i];c=m.getAttribute('content')||'';")
        append("if(/width\\s*=\\s*1280/.test(c)){if(keep){m.parentNode&&m.parentNode.removeChild(m);}else{keep=m;}}")
        append("else{m.parentNode&&m.parentNode.removeChild(m);}}")
        append("if(!keep&&document.head){m=document.createElement('meta');m.setAttribute('name','viewport');")
        append("m.setAttribute('content','width=1280');document.head.appendChild(m);}}catch(e){}}")
        append("vfix();window.__aigwVfix=vfix;")
        append("try{if(window.MutationObserver&&document.head){var vn=0;")
        append("new MutationObserver(function(){if(vn<200){vn++;vfix();}}).observe(document.head,")
        append("{childList:true,subtree:true,attributes:true,attributeFilter:['content','name']});}}catch(e){}")
    }

    private val hintScript: String = buildString {
        append("<script>(function(){")
        append("try{")
        append("var d=function(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v;},configurable:true});}catch(e){}};")
        append("d(navigator,'platform','Win32');")
        append("d(navigator,'maxTouchPoints',0);")
        append("d(navigator,'vendor','Google Inc.');")
        append("d(navigator,'hardwareConcurrency',8);")
        append("d(screen,'width',1920);d(screen,'height',1080);")
        append("d(screen,'availWidth',1920);d(screen,'availHeight',1080);")
        append("d(screen,'colorDepth',24);d(screen,'pixelDepth',24);")
        append("d(window,'devicePixelRatio',1);")
        append("d(window,'outerWidth',1920);d(window,'outerHeight',1080);")
        append("try{delete window.ontouchstart;}catch(e){")
        append("try{Object.defineProperty(window,'ontouchstart',{value:undefined,configurable:true});}catch(e2){}")
        append("}")
        // 部分站点用 window.chrome 是否存在来判真 Chrome
        append("try{if(!window.chrome){window.chrome={runtime:{},app:{},csi:function(){},loadTimes:function(){}};}}catch(e){}")
        // WebGL 渲染器串带移动 GPU 型号，同样是一眼识破的点
        append("try{var gp=WebGLRenderingContext.prototype.getParameter;")
        append("WebGLRenderingContext.prototype.getParameter=function(p){")
        append("if(p===37445){return 'Google Inc. (NVIDIA)';}")
        append("if(p===37446){return 'ANGLE (NVIDIA, NVIDIA GeForce GTX 1080 Direct3D11 vs_5_0 ps_5_0, D3D11)';}")
        append("return gp.apply(this,arguments);};}catch(e){}")
        append("}catch(e){}")
        append(viewportFixJs)
        append("})();</script>")
    }
}

/**
 * 登录窗口的请求拦截：只拦导航 GET（HTML 文档），取回后注入伪装再吐给 CEF。
 * POST/子资源走默认通道，避免破坏登录流程。
 *
 * 只在真正需要桌面视口的登录页启用（WorkBuddy 扫码页按视口分流，见 LoginWindow）；
 * 其他登录页不挂拦截器——安卓同款代码在登录流程里从来不启用（forceDesktop 无调用方）。
 *
 * 与安卓 shouldInterceptRequest 同款语义：
 * - 只处理 2xx 的 HTML 主文档；非 HTML、3xx、错误码、异常一律回退默认加载
 *   （返回 null），绝不因为注入失败把登录页搞成 502 死页；
 * - 不跟随跳转（instanceFollowRedirects = false）：302 由 CEF 自己跟，地址栏与内容
 *   保持同步，跳转目标的导航会再次被拦截注入；
 * - 服务器下发的 Set-Cookie 逐条写回 CEF cookie 仓（安卓同款，缺了它会话建不起来，
 *   页面会报「Scope 参数错误」这类缺上下文的错误页）；
 * - 本地回调地址（127.0.0.1/localhost）不拦截，避免拦截器先 GET 一次回调 URL，
 *   与页面钩子形成双入口竞态。
 */
class DesktopModeRequestHandler(
    private val cookieHeaderFor: (String) -> String = { "" },
) : CefRequestHandlerAdapter() {

    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef,
    ): CefResourceRequestHandler? {
        val url = request.url ?: return null
        if (!isNavigation) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        if (request.method?.uppercase() != "GET") return null
        if (isLocalCallback(url)) return null
        val fetched = fetchInjected(url) ?: return null
        disableDefaultHandling.set(true)
        return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(
                b: CefBrowser?,
                f: CefFrame?,
                r: CefRequest,
            ): org.cef.handler.CefResourceHandler {
                return BytesResourceHandler(fetched.bytes, fetched.mime, fetched.status)
            }
        }
    }

    private fun isLocalCallback(url: String): Boolean {
        val host = runCatching { URI(url).host }.getOrNull()?.lowercase().orEmpty()
        return host == "127.0.0.1" || host == "localhost" || host.endsWith(".localhost")
    }

    private data class Fetched(val bytes: ByteArray, val mime: String, val status: Int)

    /**
     * 取回页面并注入伪装。返回 null 表示「不介入」，由 CEF 默认加载。
     * 只在 2xx + HTML 时返回内容；其余一律 null。
     */
    private fun fetchInjected(url: String): Fetched? {
        val conn: HttpURLConnection
        try {
            conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", DesktopWebMode.UA)
            // 主文档要改写 HTML，必须拿到未压缩的原始字节（安卓同款）
            conn.setRequestProperty("Accept-Encoding", "identity")
            val cookies = cookieHeaderFor(url)
            if (cookies.isNotBlank()) conn.setRequestProperty("Cookie", cookies)
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            if (code !in 200..299) return null
            val type = conn.contentType ?: "text/html"
            if (!type.contains("html", ignoreCase = true)) return null
            val charset = charsetOf(type) ?: Charsets.UTF_8
            val raw = conn.inputStream.use { it.readBytes() }
            val injected = DesktopWebMode.inject(raw.toString(charset)).toByteArray(charset)
            // 页面靠 Set-Cookie 建立会话，漏同步会渲染出缺上下文的错误页（安卓同款）
            conn.headerFields["Set-Cookie"]?.forEach { rawCookie ->
                runCatching { writeSetCookie(url, rawCookie) }
            }
            runCatching {
                CefCookieManager.getGlobalManager()?.flushStore(
                    object : CefCompletionCallback {
                        override fun onComplete() {}
                    },
                )
            }
            return Fetched(injected, type.substringBefore(';').trim(), code)
        } catch (_: Exception) {
            return null
        }
    }

    /** 把一条原始 Set-Cookie 写回 CEF cookie 仓（属性原样保留）。 */
    private fun writeSetCookie(url: String, raw: String) {
        val mgr = CefCookieManager.getGlobalManager() ?: return
        val parts = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val nv = parts[0]
        val eq = nv.indexOf('=')
        if (eq <= 0) return
        val name = nv.substring(0, eq).trim()
        val value = nv.substring(eq + 1).trim()
        if (name.isEmpty()) return
        var domain: String? = null
        var path = "/"
        var expires: Date? = null
        var secure = url.startsWith("https", ignoreCase = true)
        var httpOnly = false
        for (attr in parts.drop(1)) {
            val i = attr.indexOf('=')
            val key = (if (i >= 0) attr.substring(0, i) else attr).trim().lowercase()
            val v = if (i >= 0) attr.substring(i + 1).trim() else ""
            when (key) {
                "domain" -> domain = v.removePrefix(".")
                "path" -> if (v.isNotEmpty()) path = v
                "expires" -> expires = parseHttpDate(v)
                "secure" -> secure = true
                "httponly" -> httpOnly = true
            }
        }
        val host = domain ?: runCatching { URI(url).host }.getOrNull() ?: return
        val cookie = CefCookie(name, value, host, path, secure, httpOnly, null, null, expires != null, expires)
        runCatching { mgr.setCookie(url, cookie) }
    }

    private fun parseHttpDate(v: String): Date? {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd-MMM-yy HH:mm:ss zzz",
        )
        for (f in formats) {
            runCatching {
                SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(v)
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun charsetOf(contentType: String?): Charset? {
        if (contentType == null) return null
        val m = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(contentType) ?: return null
        return runCatching { Charset.forName(m.groupValues[1]) }.getOrNull()
    }

    private class BytesResourceHandler(
        private val data: ByteArray,
        private val mime: String,
        private val status: Int,
    ) : CefResourceHandlerAdapter() {
        private var offset = 0

        override fun processRequest(request: CefRequest?, callback: CefCallback): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            response.status = status
            response.mimeType = mime
            response.setHeaderByName("Cache-Control", "no-store", true)
            responseLength.set(data.size)
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            if (offset >= data.size) {
                bytesRead.set(0)
                return false
            }
            val n = minOf(bytesToRead, data.size - offset)
            System.arraycopy(data, offset, dataOut, 0, n)
            offset += n
            bytesRead.set(n)
            return true
        }

        override fun cancel() {}
    }
}
