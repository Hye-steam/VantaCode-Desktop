package dev.aigw.app.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * 让 WebView 对上游页面表现为一台桌面 Chrome。
 *
 * 背景：WorkBuddy 的登录页按视口宽度分流（≤1070px 判为移动端），移动端只给
 * 「微信登录」按钮、拿不到扫码界面；而 Android WebView 默认同时暴露
 * `navigator.platform=Linux armv8l`、`maxTouchPoints>0`、Adreno/Mali 的 WebGL
 * 渲染器串，光改 UA 压不住这些特征。
 *
 * 三处一起抹平才有效：
 * 1. UA 换成桌面 Chrome
 * 2. 主文档 HTML 改写：删掉页面自带的全部 viewport，在 `</head>` 前插一条
 *    `width=1280`——现代 WebView 只认最后一条 viewport，插在开头会被页面自己的覆盖
 * 3. 在 `<head>` 开头注入伪装脚本（必须先于页面任何 JS，否则特征检测已经跑完）
 *
 * 只对主文档 GET 生效；子资源原样放行，避免拖慢页面或影响 XHR。
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
        for (tag in listOf("<head", "<html")) {
            val i = lower.indexOf(tag)
            if (i < 0) continue
            val close = html.indexOf('>', i)
            if (close > 0) return html.substring(0, close + 1) + snippet + html.substring(close + 1)
        }
        return snippet + html
    }

    /**
     * 给已有 client 套一层「主文档桌面化」。
     *
     * 不改动其它回调，只接管 [WebViewClient.shouldInterceptRequest]，
     * 拦截失败时一律回退到默认加载——绝不能因为注入把登录页搞成白屏。
     */
    fun wrapClient(base: WebViewClient): WebViewClient = DesktopClient(base)

    private class DesktopClient(private val base: WebViewClient) : WebViewClient() {

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest,
        ): WebResourceResponse? {
            if (!request.isForMainFrame || request.method != "GET") return null
            return try {
                val conn = java.net.URL(request.url.toString()).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                request.requestHeaders.forEach { (k, v) ->
                    if (!k.startsWith(":") && k.lowercase() !in SKIP_HEADERS) {
                        conn.setRequestProperty(k, v)
                    }
                }
                conn.setRequestProperty("User-Agent", UA)
                // 主文档要改写 HTML，必须拿到未压缩的原始字节
                conn.setRequestProperty("Accept-Encoding", "identity")
                // WebView 自己维护的 Cookie 不会在 shouldInterceptRequest 的请求头里，
                // 漏掉它服务端拿不到会话，页面会当成新访客
                val cookie = runCatching {
                    CookieManager.getInstance().getCookie(request.url.toString())
                }.getOrNull()
                if (!cookie.isNullOrBlank()) conn.setRequestProperty("Cookie", cookie)
                conn.connect()

                val code = conn.responseCode
                val type = conn.contentType ?: "text/html"
                if (conn.contentType?.contains("html", true) != true) {
                    // 非 HTML 主文档（少见）不介入，交回默认加载
                    conn.disconnect()
                    return null
                }
                val charset = charsetOf(conn.contentType) ?: Charsets.UTF_8
                val html = conn.inputStream.use { it.readBytes().toString(charset) }
                val payload = inject(html).toByteArray(charset)

                // 页面靠 Set-Cookie 建立会话，漏同步会渲染出「Scope 参数错误」这类缺上下文的错误页
                conn.headerFields["Set-Cookie"]?.forEach { raw ->
                    runCatching { CookieManager.getInstance().setCookie(request.url.toString(), raw) }
                }
                conn.disconnect()

                val headers = HashMap<String, String>()
                conn.headerFields.forEach { (k, vs) ->
                    if (k == null) return@forEach
                    val lk = k.lowercase()
                    // 长度已变、编码已被解码、分块交给 WebResourceResponse 处理
                    if (lk !in SKIP_RESPONSE_HEADERS) headers[k] = vs.joinToString(", ")
                }
                WebResourceResponse(
                    type.substringBefore(';'),
                    charset.name(),
                    code,
                    if (code in 200..299) "OK" else "ERR",
                    headers,
                    ByteArrayInputStream(payload),
                )
            } catch (_: Throwable) {
                null
            }
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            base.shouldOverrideUrlLoading(view, request)

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) =
            base.doUpdateVisitedHistory(view, url, isReload)

        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) =
            base.onPageStarted(view, url, favicon)

        override fun onPageFinished(view: WebView, url: String?) = base.onPageFinished(view, url)

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: android.webkit.WebResourceError,
        ) = base.onReceivedError(view, request, error)

        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String?,
            failingUrl: String?,
        ) {
            @Suppress("DEPRECATION")
            base.onReceivedError(view, errorCode, description, failingUrl)
        }
    }

    private val SKIP_HEADERS = setOf(
        "host", "content-length", "connection", "accept-encoding",
    )

    private val SKIP_RESPONSE_HEADERS = setOf(
        "content-length", "content-encoding", "transfer-encoding", "connection",
    )

    private fun charsetOf(contentType: String?): java.nio.charset.Charset? {
        if (contentType == null) return null
        val m = Regex("charset=([\\w-]+)", RegexOption.IGNORE_CASE).find(contentType) ?: return null
        return runCatching { java.nio.charset.Charset.forName(m.groupValues[1]) }.getOrNull()
    }

    /**
     * 伪装脚本。
     *
     * 除视口宽度外，页面还会读 `navigator.platform`、`maxTouchPoints`、`devicePixelRatio`
     * 与 WebGL 渲染器串来判定设备类型，缺一项都可能被识破。
     */
    /**
     * 运行时兜底：页面脚本可能动态写回 viewport。
     *
     * 现代 WebView 只认最后一条 viewport meta，所以这里做两件事：收敛成唯一一条
     * 1280 宽的，并用 MutationObserver 盯住 `<head>`，把后续写回的覆盖掉。
     */
    private val viewportFixJs: String = buildString {
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
