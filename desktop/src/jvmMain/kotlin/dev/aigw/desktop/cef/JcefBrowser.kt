package dev.aigw.desktop.cef

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandler
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefCookieVisitor
import java.awt.Component
import java.net.URI
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 一个 JCEF 浏览器实例的封装（可见登录窗与隐藏 WebTransport 共用）。
 *
 * 对应 Android 的 WebView 用法：
 * - JS 求值回传：[evalJson]（evaluateJavascript 的等价物，返回 JS 值的 JSON 文本）
 * - Cookie 读写：[getCookies] / [setCookiesFromHeader]（含 HttpOnly，
 *   CookieManager.visitUrlCookies 的等价物）
 * - JS→Java 桥：页面里调用 `window.__aigwBridgeQuery({request: ...})`
 *   （CefMessageRouter），[onBridge] 收到 `bridge/method/args` 后分发。
 *   注入的 shim（见 LoginHooks.BRIDGE_SHIM_JS）把 Android 的
 *   `__aigwBridge / DSBridge / ChatglmBridge / MuseBridge` 对象适配到这条路由。
 */
class JcefBrowser(
    startUrl: String,
    transparent: Boolean = false,
    requestHandler: CefRequestHandler? = null,
    /** 页面加载完成（主 frame）后调用，适合注入桥 shim 与钩子脚本。 */
    var afterLoad: ((JcefBrowser) -> Unit)? = null,
    /** 收到桥消息时调用：bridge 名、方法名、字符串参数。 */
    var onBridge: ((bridge: String, method: String, args: List<String>) -> Unit)? = null,
) {
    private val gson = Gson()
    private val evalFutures = ConcurrentHashMap<String, CompletableFuture<String?>>()

    @Volatile
    var ready: Boolean = false
        private set

    @Volatile
    var lastLoadError: String? = null
        private set

    @Volatile
    var currentUrl: String = startUrl
        private set

    private val routerHandler = object : CefMessageRouterHandlerAdapter() {
        override fun onQuery(
            browser: CefBrowser?,
            frame: CefFrame?,
            queryId: Long,
            request: String?,
            persistent: Boolean,
            callback: CefQueryCallback?,
        ): Boolean {
            try {
                val obj = runCatching { JsonParser.parseString(request).asJsonObject }.getOrNull()
                    ?: return false
                when (obj.get("type")?.asString) {
                    "eval" -> {
                        val id = obj.get("id")?.asString ?: return true
                        val future = evalFutures.remove(id) ?: return true
                        if (obj.has("err")) {
                            future.completeExceptionally(IllegalStateException(obj.get("err").asString))
                        } else {
                            val v = obj.get("v")
                            future.complete(if (v == null || v.isJsonNull) "null" else v.toString())
                        }
                    }
                    "bridge" -> {
                        val bridge = obj.get("bridge")?.asString ?: return true
                        val method = obj.get("method")?.asString ?: return true
                        val args = obj.getAsJsonArray("args")?.map { it.asString } ?: emptyList()
                        runCatching { onBridge?.invoke(bridge, method, args) }
                    }
                    else -> return false
                }
            } catch (_: Exception) {
                // 桥消息解析失败不影响页面
            } finally {
                runCatching { callback?.success("") }
            }
            return true
        }
    }

    private val client = GateCef.newClient(requestHandler, routerHandler)

    val browser: CefBrowser = client.createBrowser(startUrl, true, transparent)

    /** 嵌进 Swing 容器用的组件（OSR 渲染）。 */
    val component: Component get() = browser.uiComponent

    init {
        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    ready = true
                    currentUrl = browser?.url ?: currentUrl
                    runCatching { afterLoad?.invoke(this@JcefBrowser) }
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: Int,
                errorText: String?,
                failedUrl: String?,
            ) {
                if (frame?.isMain == true) {
                    lastLoadError = "加载失败 $errorCode：${errorText.orEmpty()}（$failedUrl）"
                }
            }

            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean,
            ) {
                if (!isLoading) currentUrl = browser?.url ?: currentUrl
            }
        })
        // 弹窗（window.open / OAuth 新窗口）直接在当前页打开，避免多窗口管理。
        client.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?,
            ): Boolean {
                val url = targetUrl.orEmpty()
                if (url.isNotBlank()) runCatching { browser?.loadURL(url) }
                return true
            }
        })
    }

    fun load(url: String) {
        ready = false
        lastLoadError = null
        browser.loadURL(url)
    }

    /** 等主 frame 加载完成；超时返回 false。 */
    fun awaitReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (ready) return true
            Thread.sleep(100)
        }
        return ready
    }

    /** 执行 JS 表达式并取回值的 JSON 文本；超时/异常返回 null。 */
    fun evalJson(code: String, timeoutMs: Long = 10_000): String? {
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<String?>()
        evalFutures[id] = future
        val js = "(function(){try{var r=($code);" +
            "window.__aigwBridgeQuery({request:JSON.stringify({type:'eval',id:${gson.toJson(id)}," +
            "v:(typeof r==='undefined'?null:r)}),onSuccess:function(){},onFailure:function(){}});" +
            "}catch(e){window.__aigwBridgeQuery({request:JSON.stringify({type:'eval',id:${gson.toJson(id)}," +
            "err:String(e)}),onSuccess:function(){},onFailure:function(){}});}})();"
        runCatching { browser.executeJavaScript(js, browser.url ?: "", 0) }
            .onFailure { evalFutures.remove(id); return null }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            evalFutures.remove(id)
            null
        }
    }

    /** 执行 JS 语句，不取回值。 */
    fun evalVoid(code: String) {
        runCatching { browser.executeJavaScript("try{$code}catch(e){}", browser.url ?: "", 0) }
    }

    /** 读指定 URL 的全部 Cookie（含 HttpOnly），拼成 `k=v; k2=v2`。 */
    fun getCookies(url: String, timeoutMs: Long = 2_000): String {
        val mgr = runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return ""
        val latch = CountDownLatch(1)
        val list = Collections.synchronizedList(mutableListOf<CefCookie>())
        runCatching {
            mgr.visitUrlCookies(url, true, object : CefCookieVisitor {
                override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef): Boolean {
                    list.add(cookie)
                    if (total > 0 && count >= total - 1) latch.countDown()
                    return true
                }
            })
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return list.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** 把 `k=v; k2=v2` 形式的 Cookie 串写入指定 URL。 */
    fun setCookiesFromHeader(url: String, raw: String) {
        raw.split(';').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) setCookie(url, kv.substring(0, i).trim(), kv.substring(i + 1).trim())
        }
    }

    fun setCookie(url: String, name: String, value: String, domain: String? = null) {
        val mgr = runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return
        val host = domain ?: runCatching { URI(url).host }.getOrNull() ?: return
        if (name.isBlank()) return
        val secure = url.startsWith("https", ignoreCase = true)
        val cookie = CefCookie(name, value, host, "/", secure, false, null, null, false, null)
        runCatching { mgr.setCookie(url, cookie) }
    }

    /** 清空全部 Cookie（登录窗打开时用，避免上次登录态直接通过）。 */
    fun clearAllCookies() {
        runCatching { CefCookieManager.getGlobalManager()?.deleteCookies("", "") }
    }

    fun close() {
        runCatching { browser.close(true) }
        runCatching { client.dispose() }
    }
}
