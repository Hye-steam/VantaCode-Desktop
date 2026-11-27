package com.vantacode.feature.plugin.cef

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.plugin.runtime.PluginWebViewBrowserFactory
import com.vantacode.feature.plugin.runtime.PluginWebViewBrowserHandle
import com.vantacode.feature.plugin.runtime.PluginWebViewCookieStore
import com.vantacode.feature.plugin.runtime.PluginWebViewException
import com.vantacode.feature.plugin.runtime.PluginWebViewLoginOutcome
import com.vantacode.feature.plugin.runtime.PluginWebViewLoginRequest
import com.vantacode.feature.plugin.runtime.PluginWebViewLoginWindowFactory
import com.vantacode.feature.plugin.runtime.PluginWebViewLoginWindowHandle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefCompletionCallback
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefCookieVisitor

/**
 * 插件 webview 会话的 JCEF 实现（对照 VantaGate 的 `JcefBrowser` + `LoginWindow`）。
 *
 * - [CefPluginWebViewBrowser]：一个独立 CefClient/CefBrowser 的封装。JS 求值回传走
 *   CefMessageRouter（`window.__vcWebviewQuery`），与 VantaGate 的 `evalJson` 同款做法；
 *   弹窗（window.open / OAuth 新窗口）直接在当前页打开，避免多窗口管理。
 * - [CefPluginWebViewCookieStore]：全局 CookieManager 的封装；读之前先 flush 再读
 *   （flushStore + visitUrlCookies），含 HttpOnly cookie。
 * - [CefPluginWebViewLoginWindowFactory]：可见登录窗口（JFrame + JCEF），对照 VantaGate
 *   `LoginWindow`：标题栏（标题 + 完成按钮 + 关闭按钮）+ 浏览器；用户点「完成登录」
 *   或关窗时给 service 一个确定结果，绝不挂起。
 */
class CefPluginWebViewBrowserFactory : PluginWebViewBrowserFactory {
    override fun create(startUrl: String, userAgent: String?): PluginWebViewBrowserHandle =
        CefPluginWebViewBrowser(startUrl, userAgent)
}

class CefPluginWebViewBrowser(
    startUrl: String,
    // userAgent：JCEF 的 UA 是 CefSettings 全局项（PluginCef.app() 建时定死），
    // 单个 browser 无法覆盖；参数保留只为与 Android 签名对齐，桌面忽略。
    @Suppress("UNUSED_PARAMETER") userAgent: String?
) : PluginWebViewBrowserHandle {

    private val evalFutures = ConcurrentHashMap<String, CompletableFuture<String?>>()
    private val closed = AtomicBoolean(false)

    @Volatile
    var lastLoadError: String? = null
        private set

    override var currentUrl: String = startUrl
        private set

    override var ready: Boolean = false
        private set

    override var onBridgeMessage: ((method: String, args: List<String>) -> Unit)? = null

    private val routerHandler = object : CefMessageRouterHandlerAdapter() {
        override fun onQuery(
            browser: CefBrowser?,
            frame: CefFrame?,
            queryId: Long,
            request: String?,
            persistent: Boolean,
            callback: CefQueryCallback?
        ): Boolean {
            try {
                val obj = runCatching { Json.parseToJsonElement(request ?: "").jsonObject }.getOrNull()
                    ?: return false
                when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                    "eval" -> {
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return true
                        val future = evalFutures.remove(id) ?: return true
                        if (obj.containsKey("err")) {
                            future.completeExceptionally(
                                IllegalStateException(
                                    obj["err"]?.jsonPrimitive?.contentOrNull ?: "eval failed"
                                )
                            )
                        } else {
                            val v = obj["v"]
                            future.complete(if (v == null || v is JsonNull) "null" else v.toString())
                        }
                    }
                    "bridge" -> {
                        val method = obj["method"]?.jsonPrimitive?.contentOrNull ?: return true
                        val args = (obj["args"] as? JsonArray)
                            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull }
                            ?: emptyList()
                        runCatching { onBridgeMessage?.invoke(method, args) }
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

    private val client: CefClient = run {
        val created = PluginCef.app().createClient()
        val router = CefMessageRouter.create(
            CefMessageRouter.CefMessageRouterConfig("__vcWebviewQuery", "__vcWebviewCancel")
        )
        router.addHandler(routerHandler, true)
        created.addMessageRouter(router)
        created.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    ready = true
                    currentUrl = browser?.url ?: currentUrl
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: Int,
                errorText: String?,
                failedUrl: String?
            ) {
                if (frame?.isMain == true) {
                    lastLoadError = "加载失败 $errorCode：${errorText.orEmpty()}（$failedUrl）"
                }
            }

            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (!isLoading) currentUrl = browser?.url ?: currentUrl
            }
        })
        // 弹窗直接在当前页打开，避免多窗口管理（与 VantaGate JcefBrowser 同款）。
        created.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?
            ): Boolean {
                val url = targetUrl.orEmpty()
                if (url.isNotBlank()) runCatching { browser?.loadURL(url) }
                return true
            }
        })
        created
    }

    private val browser: CefBrowser = client.createBrowser(startUrl, true, false)

    /** 嵌进 Swing 容器用的组件（OSR 渲染）。 */
    val component: Component get() = browser.uiComponent

    override fun load(url: String) {
        ready = false
        lastLoadError = null
        browser.loadURL(url)
    }

    /** 执行 JS 表达式并取回值的 JSON 文本；超时/异常返回 null。 */
    override fun evalJson(js: String, timeoutMs: Long): String? {
        if (closed.get()) return null
        val id = UUID.randomUUID().toString()
        val future = CompletableFuture<String?>()
        evalFutures[id] = future
        val script = "(function(){try{var r=($js);" +
            "window.__vcWebviewQuery({request:JSON.stringify({type:'eval',id:${jsonQuote(id)}," +
            "v:(typeof r==='undefined'?null:r)}),onSuccess:function(){},onFailure:function(){}});" +
            "}catch(e){window.__vcWebviewQuery({request:JSON.stringify({type:'eval',id:${jsonQuote(id)}," +
            "err:String(e)}),onSuccess:function(){},onFailure:function(){}});}})();"
        runCatching { browser.executeJavaScript(script, browser.url ?: "", 0) }
            .onFailure { evalFutures.remove(id); return null }
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            evalFutures.remove(id)
            null
        }
    }

    /** 执行 JS 语句，不取回值。 */
    override fun evalVoid(js: String) {
        if (closed.get()) return
        runCatching { browser.executeJavaScript("try{$js}catch(e){}", browser.url ?: "", 0) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { browser.close(true) }
        runCatching { client.dispose() }
    }

    /** 把字符串转成 JS 字符串字面量（与 PluginCefBridgeHandler.jsString 同款转义）。 */
    private fun jsonQuote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(c)
            }
        }
        append('"')
    }
}

/** 全局 CookieManager 的封装：读之前先 flush 再读（含 HttpOnly），与 VantaGate JcefBrowser 同款。 */
object CefPluginWebViewCookieStore : PluginWebViewCookieStore {

    /** 读指定 URL 的全部 Cookie（含 HttpOnly），拼成 `k=v; k2=v2`。 */
    override fun getCookies(url: String, timeoutMs: Long): String {
        val mgr = runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return ""
        runCatching { flush() }
        val latch = CountDownLatch(1)
        val list = Collections.synchronizedList(mutableListOf<CefCookie>())
        runCatching {
            mgr.visitUrlCookies(url, true, object : CefCookieVisitor {
                override fun visit(cookie: CefCookie, count: Int, total: Int, delete: BoolRef): Boolean {
                    list.add(cookie)
                    if (total <= 0 || count >= total - 1) latch.countDown()
                    return true
                }
            })
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return list.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** 把一个 `k=v` 写进指定 URL（domain 为空时取 URL 的 host）。 */
    override fun setCookie(url: String, name: String, value: String, domain: String?) {
        val mgr = runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return
        val host = domain ?: runCatching { URI(url).host }.getOrNull() ?: return
        if (name.isBlank()) return
        val secure = url.startsWith("https", ignoreCase = true)
        val cookie = CefCookie(name, value, host, "/", secure, false, null, null, false, null)
        runCatching { mgr.setCookie(url, cookie) }
    }

    /** 把内存里的 cookie 写盘；失败只记日志，不阻塞调用方。 */
    override fun flush() {
        val mgr = runCatching { CefCookieManager.getGlobalManager() }.getOrNull() ?: return
        val latch = CountDownLatch(1)
        val ok = runCatching {
            mgr.flushStore(object : CefCompletionCallback {
                override fun onComplete() {
                    latch.countDown()
                }
            })
            true
        }.getOrDefault(false)
        if (!ok) return
        runCatching { latch.await(1, TimeUnit.SECONDS) }
    }

    /** 清空全部 Cookie（登录窗打开前可用，避免上次登录态直接通过；service 目前不在 open 时清，由插件自己决定）。 */
    override fun clearAllCookies() {
        runCatching { CefCookieManager.getGlobalManager()?.deleteCookies("", "") }
    }
}

/**
 * 可见登录窗口工厂：浏览器先在调用线程建（CEF 首次解包放 IO 线程，不冻住 EDT，
 * 与 PluginPageHostDesktop 同款做法），窗口组装放 EDT。
 */
class CefPluginWebViewLoginWindowFactory : PluginWebViewLoginWindowFactory {
    override fun create(request: PluginWebViewLoginRequest): PluginWebViewLoginWindowHandle {
        val browser = try {
            CefPluginWebViewBrowser(request.url, null)
        } catch (e: Throwable) {
            throw PluginWebViewException("WEBVIEW_OFF", "webview unavailable: ${e.message}")
        }
        return try {
            CefPluginWebViewLoginWindow(request, browser).also { it.show() }
        } catch (e: Throwable) {
            runCatching { browser.close() }
            throw e
        }
    }
}

/**
 * 网页登录窗口（JFrame + JCEF，对照 VantaGate `LoginWindow`）。
 *
 * - 标题栏：标题 + 「完成登录」（finishText）+ 「关闭」（showClose 为 false 时不显示）；
 *   点「完成登录」即确认（结果带当前 URL），点关闭或窗口 X 即取消。
 * - 结果只定一次：`outcomeFuture` 先完成者为准；窗口关闭后 service 还会调 [close]，
 *   那时不再重复触发回调。
 * - 所有 Swing 操作走 EDT；[awaitOutcome] 在 IO 线程等结果，不阻塞 EDT。
 */
internal class CefPluginWebViewLoginWindow(
    private val request: PluginWebViewLoginRequest,
    override val browser: CefPluginWebViewBrowser
) : PluginWebViewLoginWindowHandle {

    private val outcomeFuture = CompletableFuture<PluginWebViewLoginOutcome>()
    private val closedFlag = AtomicBoolean(false)
    private lateinit var frame: JFrame

    fun show() {
        if (SwingUtilities.isEventDispatchThread()) {
            buildAndShow()
        } else {
            SwingUtilities.invokeAndWait { buildAndShow() }
        }
    }

    private fun buildAndShow() {
        val f = JFrame(request.title)
        frame = f
        f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
        f.layout = BorderLayout()
        f.minimumSize = Dimension(900, 640)
        val screen = Toolkit.getDefaultToolkit().screenSize
        f.setSize(1000, 720)
        f.setLocation((screen.width - 1000) / 2, (screen.height - 720) / 2)

        val header = JPanel(BorderLayout())
        val titlePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        titlePanel.add(JLabel(request.title))
        val actions = JPanel(FlowLayout(FlowLayout.RIGHT))
        val finish = JButton(request.finishText)
        finish.addActionListener { finish(confirm = true) }
        actions.add(finish)
        if (request.showClose) {
            val closeBtn = JButton("关闭")
            closeBtn.addActionListener { finish(confirm = false) }
            actions.add(closeBtn)
        }
        titlePanel.add(actions)
        header.add(titlePanel, BorderLayout.NORTH)
        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        statusPanel.add(JLabel(request.url))
        header.add(statusPanel, BorderLayout.SOUTH)
        f.add(header, BorderLayout.NORTH)

        f.add(browser.component, BorderLayout.CENTER)
        f.addWindowListener(object : WindowAdapter() {
            override fun windowClosed(e: WindowEvent?) {
                finish(confirm = false)
            }
        })
        f.isVisible = true
        FileLogger.d("PluginWebView", "登录窗口已打开：${request.url}")
    }

    private fun finish(confirm: Boolean) {
        val outcome = if (confirm) {
            PluginWebViewLoginOutcome.Confirmed(browser.currentUrl)
        } else {
            PluginWebViewLoginOutcome.Canceled
        }
        outcomeFuture.complete(outcome)
    }

    override suspend fun awaitOutcome(timeoutMs: Long): PluginWebViewLoginOutcome =
        withContext(Dispatchers.IO) {
            try {
                outcomeFuture.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (_: java.util.concurrent.TimeoutException) {
                PluginWebViewLoginOutcome.TimedOut
            } catch (_: Exception) {
                PluginWebViewLoginOutcome.Canceled
            }
        }

    override fun close() {
        if (!closedFlag.compareAndSet(false, true)) return
        // 关窗本身不触发 onCancel：结果已由 finish 定过，或 service 已拿到结果；
        // windowClosed 里 finish 会因 outcomeFuture 已完成而成为 no-op。
        outcomeFuture.complete(PluginWebViewLoginOutcome.Canceled)
        if (!::frame.isInitialized) return
        if (SwingUtilities.isEventDispatchThread()) {
            runCatching { frame.dispose() }
        } else {
            SwingUtilities.invokeLater { runCatching { frame.dispose() } }
        }
    }
}
