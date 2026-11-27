package com.vantacode.feature.plugin.runtime

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.plugin.domain.PluginHostException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

/**
 * 插件可用的隐藏 WebView 通道。
 *
 * 有些站点（muse.ai 是最典型的）只对「浏览器里真实打开的页面」开放能力：生成入口挂在内网 VM 上，
 * 公网 HTTP 拿不到，必须驱动真实页面的 DOM。插件跑在 Node 进程里没有 WebView，只能由宿主代跑。
 *
 * 设计取舍：
 * - 只提供 `open/setCookies/eval/waitFor/close` 五个原语。页面细节（选择器、等待条件、取产物）
 *   全部由插件用 JS 自己写，宿主不认识任何站点，改版时插件升级即可。
 * - 每个会话独占一个 WebView。站点常按「一个页面一个上下文」设计，共享会互相踩。
 * - WebView 一律在主线程创建与销毁；JS 求值也回主线程。
 * - 域名白名单由插件清单声明，[open] 前校验，避免插件借宿主 WebView 打任意站点。
 *
 * 失败统一抛 [PluginHostException]，code 稳定：`WEBVIEW_OFF`、`WEBVIEW_SESSION`、
 * `WEBVIEW_LIMIT`、`WEBVIEW_URL_REJECTED`、`WEBVIEW_TIMEOUT`、`PARAM_INVALID`。
 */
@Singleton
class PluginWebViewService @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    /** 一个会话就是一个独立 WebView + 一条 JS→宿主的消息队列。 */
    private class Session(
        val id: String,
        val pluginId: String,
        val webView: WebView,
        val events: LinkedBlockingQueue<Event>,
        /** 挂窗用的 WindowManager；未挂窗时为 null。 */
        var hostWindow: android.view.WindowManager? = null,
        /** 交互模式下实际挂上去的容器（包着 WebView + 完成按钮）；隐藏模式为 null。 */
        var hostView: android.view.View? = null,
    ) {
@Volatile
        var ready: Boolean = false

        @Volatile
        var closed: Boolean = false
    }

    private sealed interface Event {
        data class Ok(val json: String) : Event
        data class Fail(val message: String) : Event
    }

    /** 页面回调只带会话 id，按 id 找队列；JS 侧不需要也不应该知道别的东西。 */
    private inner class Bridge(private val sessionId: String) {
        @JavascriptInterface
        fun onOk(id: String, json: String) {
            if (id != sessionId) return
            sessions[sessionId]?.events?.offer(Event.Ok(json))
        }

        @JavascriptInterface
        fun onFail(id: String, message: String) {
            if (id != sessionId) return
            sessions[sessionId]?.events?.offer(Event.Fail(message))
        }
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 插件被停用/卸载时调用：把它名下的 WebView 全销毁，避免后台常驻吃内存。 */
    suspend fun closeAll(pluginId: String) = withContext(Dispatchers.Main) {
        sessions.values.filter { it.pluginId == pluginId }.forEach { destroy(it) }
        Unit
    }

    suspend fun open(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val url = asStringOrThrow(obj["url"], "url")
        val userAgent = obj["userAgent"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        val allow = obj["allowDomains"]?.asStringList().orEmpty()
        // 交互模式：给用户真的能看到、能点的窗口（登录场景用）。
        // 默认是隐藏模式：屏幕外 + 几乎透明，只为了自动抓生成结果。
        val interactive = (obj["interactive"] as? JsonPrimitive)?.contentOrNull?.toBoolean() ?: false
        // 登录态必须在 loadUrl 之前落进 CookieManager，否则首次请求就是登出状态，
        // 页面水合完也拿不到生成入口。所以这里允许开页时一并带 cookie。
        val cookies = obj["cookies"]?.let { (it as? JsonPrimitive)?.contentOrNull }

        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw PluginHostException("PARAM_INVALID", "url is not a valid URI")
        if (uri.scheme !in ALLOWED_SCHEMES) {
            throw PluginHostException("WEBVIEW_URL_REJECTED", "only http(s) is allowed")
        }
        val host = uri.host?.lowercase() ?: throw PluginHostException("PARAM_INVALID", "url has no host")
        if (allow.none { matchesDomain(host, it) }) {
            throw PluginHostException("WEBVIEW_URL_REJECTED", "domain $host not allowed by plugin manifest")
        }

        val mine = sessions.values.count { it.pluginId == pluginId }
        if (mine >= MAX_SESSIONS_PER_PLUGIN) {
            throw PluginHostException("WEBVIEW_LIMIT", "too many webviews for this plugin")
        }

        val id = "$pluginId:${System.nanoTime()}"
        withContext(Dispatchers.Main) {
            @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // 无手势也能加载/播放媒体。默认是 true，<video> 必须等用户点击
                // 才去拉流，页面上的视频卡片就永远是黑块，currentSrc 也一直是空。
                // 对应 Chromium 的 --autoplay-policy=no-user-gesture-required。
                settings.mediaPlaybackRequiresUserGesture = false
                if (!userAgent.isNullOrBlank()) settings.userAgentString = userAgent
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(Bridge(id), BRIDGE_NAME)
            }
            val session = Session(id, pluginId, wv, LinkedBlockingQueue())
            sessions[id] = session
            // 先写 cookie 再 loadUrl：反过来首屏请求就带着登出态出去了。
            // 写法对齐 VantaGate 独立版的 injectCookies：拆出 name/value 后补上
            // `path=/; secure`，并同时写给裸域和带点的子域形式——CookieManager
            // 对两者的匹配规则不一样，只写一种会出现「页面能进但接口 401」。
            if (!cookies.isNullOrBlank()) {
                val cm = CookieManager.getInstance()
                val host = uri.host ?: ""
                val origins = listOf("${uri.scheme}://$host", "${uri.scheme}://.$host")
                cookies.split(';').forEach { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) return@forEach
                    val name = pair.substring(0, idx).trim()
                    val value = pair.substring(idx + 1).trim()
                    if (name.isEmpty()) return@forEach
                    origins.forEach { origin ->
                        runCatching { cm.setCookie(origin, "$name=$value; path=/; secure") }
                    }
                }
                cm.flush()
            }
            // 必须挂到窗口上：不挂窗的 WebView 在很多机型上不跑 requestAnimationFrame，
            // 而 React hydration 依赖 rAF——表现出来就是页面永远停在未水合状态。
            // 屏幕外 + alpha 0.01（不能用 0，部分机型会因此跳过合成）。
            runCatching {
                val wm = context.getSystemService(android.view.WindowManager::class.java)
                // 交互模式要能触摸、能聚焦，隐藏模式则相反（它只是后台扒数据的窗口）。
                val baseFlags = if (interactive) {
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                } else {
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                }
                val lp = android.view.WindowManager.LayoutParams(
                    // 尺寸对齐 muse2api 的 --window-size=1440,2400：卡片太窄时
                    // muse 会把媒体当成不可见区域、不触发懒加载。
                    1440, 2400,
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        android.view.WindowManager.LayoutParams.TYPE_PHONE
                    },
                    baseFlags,
                    android.graphics.PixelFormat.TRANSLUCENT,
                ).apply {
                    if (interactive) {
                        width = android.view.WindowManager.LayoutParams.MATCH_PARENT
                        height = android.view.WindowManager.LayoutParams.MATCH_PARENT
                        gravity = android.view.Gravity.CENTER
                        // 登录要输账号密码：键盘弹出时把窗口顶上去，否则输入框被遮住看不见。
                        softInputMode = android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    } else {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.START
                        x = -500
                        y = -900
                    }
                }
                // 交互模式下必须完全不透明，否则用户看到蒙层却看不清内容
                wv.alpha = if (interactive) 1f else 0.01f
                if (interactive) {
                    // 盖一层「完成登录」按钮：登录完成的判据必须是用户自己确认，
                    // 不能靠 cookie 里出现某个字段——页面残留的过期 session 也会让那个判据成立。
                    val root = android.widget.FrameLayout(context)
                    root.addView(
                        wv,
                        android.widget.FrameLayout.LayoutParams(
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    val doneBtn = android.widget.Button(context).apply {
                        text = "完成登录"
                        setBackgroundColor(android.graphics.Color.parseColor("#6EA8FE"))
                        setTextColor(android.graphics.Color.WHITE)
                        setOnClickListener {
                            // 用 Ok 事件把「用户点了完成」通知给插件，插件再去读 cookie
                            session.events.offer(Event.Ok("{\"confirm\":true}"))
                        }
                    }
                    val btnLp = android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                        bottomMargin = 72
                    }
                    root.addView(doneBtn, btnLp)
                    wm.addView(root, lp)
                    session.hostView = root
                } else {
                    wm.addView(wv, lp)
                }
                session.hostWindow = wm
            }.onFailure { FileLogger.w(TAG, "WebView 挂窗失败（页面可能不会水合）", it) }
            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    session.ready = true
                }

                override fun onReceivedError(
                    view: WebView,
                    request: android.webkit.WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    // 子资源失败很常见，只记主文档的失败。
                    if (request.isForMainFrame) {
                        session.events.offer(Event.Fail("page-error:${error.errorCode}"))
                    }
                }
            }
            wv.loadUrl(url)
        }
        return buildJsonObject { put("session", id) }
    }

    suspend fun setCookies(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val sessionId = asStringOrThrow(obj["session"], "session")
        val cookies = asStringOrThrow(obj["cookies"], "cookies")
        val url = asStringOrThrow(obj["url"], "url")
        val session = requireSession(pluginId, sessionId)
        withContext(Dispatchers.Main) {
            val cm = CookieManager.getInstance()
            // 交给插件的 cookie 串通常是 `a=1; b=2`，逐条塞进去才能对同一域名生效。
            cookies.split(';').map { it.trim() }.filter { it.contains('=') }.forEach { pair ->
                cm.setCookie(url, pair)
            }
            cm.flush()
        }
        return buildJsonObject { put("ok", true) }
    }

    /** 在页面里执行一段 JS，返回 evaluateJavascript 的原始结果（多为 JSON 字符串）。 */
    suspend fun eval(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val js = asStringOrThrow(obj["js"], "js")
        val timeoutMs = obj["timeoutMs"]?.asLongOrNull() ?: DEFAULT_EVAL_TIMEOUT_MS
        val value = withContext(Dispatchers.Main) {
            val latch = CountDownLatch(1)
            var result: String? = null
            session.webView.evaluateJavascript(js) { r ->
                result = r
                latch.countDown()
            }
            withContext(Dispatchers.IO) {
                if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                    throw PluginHostException("WEBVIEW_TIMEOUT", "eval timed out")
                }
            }
            result
        }
        return buildJsonObject { put("value", value ?: "null") }
    }

    /**
     * 反复求值 [expr]，直到它返回真值或超时。
     *
     * 页面自动化里「等某个条件成立」比「睡固定时长」可靠得多：站点水合、产物出现都异步。
     * 返回命中时的那个值；超时抛 `WEBVIEW_TIMEOUT`。
     */
    suspend fun waitFor(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val expr = asStringOrThrow(obj["expr"], "expr")
        val timeoutMs = obj["timeoutMs"]?.asLongOrNull() ?: DEFAULT_WAIT_TIMEOUT_MS
        val intervalMs = obj["intervalMs"]?.asLongOrNull() ?: DEFAULT_POLL_INTERVAL_MS

        // 表达式包一层 try 并把结果 JSON 化：evaluateJavascript 回传的字符串本身又是
        // JSON 编码过的（true → "true" → 字面上带引号），所以判断前要先把外层引号剥掉。
        val wrapped = "(function(){try{return JSON.stringify((${expr}));}catch(e){return JSON.stringify({__err:String(e)})}})()"

        val hit = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val raw = evalValue(session, wrapped, EVAL_STEP_TIMEOUT_MS)
                val parsed = unwrapJsonString(raw)
                val truthy = parsed != null && parsed != "null" && parsed != "false" &&
                    parsed != "\"\"" && !parsed.contains("__err")
                if (truthy) return@withTimeoutOrNull parsed
                // 用 delay 而不是 Thread.sleep：withTimeoutOrNull 取消不了阻塞的 sleep，
                // 超时得等到这一轮睡完才生效。
                delay(intervalMs)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } ?: throw PluginHostException("WEBVIEW_TIMEOUT", "waitFor timed out after ${timeoutMs}ms")

        return buildJsonObject { put("value", hit) }
    }

    /**
     * 把页面里已挂好的 base64 直接写成文件，只回传路径。
     *
     * 插件与宿主之间只有 stdin/stdout 一条 NDJSON 管道，宿主读插件输出时
     * 单行封顶 64K 字符（BoundedLineReader）。视频 base64 动辄 8MB+，
     * 无论怎么分块，只要运回插件就会被截断。
     *
     * 所以字节不能过管道：宿主在本进程里把 window 上的 base64 取出来、
     * Base64 解码后落盘，插件只收一个路径。
     */
    suspend fun saveBlob(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val varName = obj["var"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "__museBlob"
        val dir = obj["dir"]?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: throw PluginHostException("PARAM_INVALID", "dir is required")
        val name = obj["name"]?.let { (it as? JsonPrimitive)?.contentOrNull } ?: "media.bin"

        // 先量长度，避免把整串 base64 拉回 Kotlin 两次。
        val lenRaw = evalValue(
            session,
            "(function(){ try { return String((window.$varName || '').length); } catch(e) { return '0'; } })()",
            DEFAULT_EVAL_TIMEOUT_MS,
        )
        val total = (lenRaw ?: "0").trim('"').toIntOrNull() ?: 0
        if (total <= 0) throw PluginHostException("WEBVIEW_MEDIA", "page variable is empty: $varName")

        // 分次把 base64 拉回宿主内存（这一步不过管道，走 evaluateJavascript 回调）。
        val sb = StringBuilder(total)
        var offset = 0
        val step = 1024 * 1024
        while (offset < total) {
            val part = evalValue(
                session,
                "(function(){ return String((window.$varName || '').slice($offset, ${offset + step})); })()",
                DEFAULT_EVAL_TIMEOUT_MS,
            ) ?: ""
            val clean = if (part.length >= 2 && part.startsWith("\"") && part.endsWith("\"")) {
                runCatching {
                    kotlinx.serialization.json.JsonPrimitive(part).content
                }.getOrDefault(part.substring(1, part.length - 1))
            } else part
            if (clean.isEmpty()) break
            sb.append(clean)
            offset += step
        }

        val bytes = try {
            android.util.Base64.decode(sb.toString(), android.util.Base64.DEFAULT)
        } catch (e: Exception) {
            throw PluginHostException("WEBVIEW_MEDIA", "base64 decode failed: ${e.message}")
        }

        val target = withContext(Dispatchers.IO) {
            val d = java.io.File(dir)
            if (!d.isDirectory && !d.mkdirs()) {
                throw PluginHostException("IO_ERROR", "cannot create dir: $dir")
            }
            val f = java.io.File(d, name)
            java.io.FileOutputStream(f).use { it.write(bytes) }
            f
        }

        // 落盘后清掉页面里的副本，十几 MB 挂 window 上很占内存。
        runCatching {
            evalValue(session, "(function(){ window.$varName = null; return 'ok'; })()", 5000)
        }

        return buildJsonObject {
            put("path", target.absolutePath)
            put("size", bytes.size)
        }
    }

    /**
     * 读取某站点在共享 CookieManager 里的 cookie。
     *
     * 登录类流程（如 muse）拿到的会话是 HttpOnly cookie，页面 JS 的 document.cookie 读不到，
     * 但 CookieManager 能看到全部。插件没权限直接碰宿主的前台，只能经这里取。
     *
     * 返回的串是 `a=1; b=2` 形式，与 [setCookies] 接受的格式一致，可以原样存为凭证。
     */
    suspend fun getCookies(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val url = asStringOrThrow(obj["url"], "url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw PluginHostException("PARAM_INVALID", "url must be http(s): $url")
        }
        // 有 session 就顺带校验归属，避免插件拿别人的会话 id 探 cookie；无 session 时只按域名读。
        val sessionId = obj["session"]?.let { asStringOrThrow(it, "session") }
        if (sessionId != null) requireSession(pluginId, sessionId)
        val raw = withContext(Dispatchers.Main) {
            val cm = CookieManager.getInstance()
            cm.flush()
            cm.getCookie(url) ?: ""
        }
        return buildJsonObject { put("cookies", raw) }
    }

    /**
     * 等页面通过 JS 桥主动回调（`onOk`/`onFail`）。
     *
     * 只要「跑一段异步脚本，完成时把结果推回来」的场景都靠它，宿主不需要知道页面细节。
     */
    suspend fun awaitMessage(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val timeoutMs = obj["timeoutMs"]?.asLongOrNull() ?: DEFAULT_WAIT_TIMEOUT_MS
        val event = withContext(Dispatchers.IO) {
            session.events.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } ?: throw PluginHostException("WEBVIEW_TIMEOUT", "awaitMessage timed out after ${timeoutMs}ms")

        return when (event) {
            is Event.Ok -> buildJsonObject {
                put("ok", true)
                put("value", event.json)
            }
            is Event.Fail -> buildJsonObject {
                put("ok", false)
                put("error", event.message)
            }
        }
    }

    suspend fun close(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        withContext(Dispatchers.Main) { destroy(session) }
        return buildJsonObject { put("ok", true) }
    }

    private suspend fun evalValue(session: Session, js: String, timeoutMs: Long): String? =
        withContext(Dispatchers.Main) {
            val latch = CountDownLatch(1)
            var result: String? = null
            session.webView.evaluateJavascript(js) { r ->
                result = r
                latch.countDown()
            }
            withContext(Dispatchers.IO) { latch.await(timeoutMs, TimeUnit.MILLISECONDS) }
            result
        }

    /**
     * 剥掉 evaluateJavascript 回传值的引号与转义。
     *
     * evaluateJavascript 回的是一个 JSON 字面量的字符串形式：JS 侧 `JSON.stringify(true)` 得到
     * `true`，Android 把它当字符串回传，再被包成 `"true"`。不剥就永远等不到命中。
     */
    private fun unwrapJsonString(raw: String?): String? {
        val s = raw?.trim() ?: return null
        if (s.length < 2 || !s.startsWith("\"") || !s.endsWith("\"")) return s
        return runCatching {
            kotlinx.serialization.json.Json.parseToJsonElement(s).jsonPrimitive.content
        }.getOrDefault(s.substring(1, s.length - 1))
    }

    private fun destroy(session: Session) {
        if (session.closed) return
        session.closed = true
        sessions.remove(session.id)
        runCatching {
            // 交互模式挂的是容器（FrameLayout），直接移除 webView 会抛
            // 「not a child of this ViewGroup」。
            session.hostWindow?.removeView(session.hostView ?: session.webView)
        }.onFailure { FileLogger.w(TAG, "WebView 移除窗口失败", it) }
        session.hostView = null
        session.hostWindow = null
        runCatching {
            session.webView.stopLoading()
            session.webView.removeJavascriptInterface(BRIDGE_NAME)
            session.webView.destroy()
        }.onFailure { FileLogger.w(TAG, "销毁 WebView 失败", it) }
    }

    private fun requireSession(pluginId: String, sessionId: String): Session {
        val s = sessions[sessionId]
            ?: throw PluginHostException("WEBVIEW_SESSION", "unknown session $sessionId")
        if (s.pluginId != pluginId) {
            throw PluginHostException("WEBVIEW_SESSION", "session belongs to another plugin")
        }
        if (s.closed) throw PluginHostException("WEBVIEW_SESSION", "session already closed")
        return s
    }

    /** 允许 `example.com` 匹配 `muse.ai` 的子域，但不允许 `evilmuse.ai` 命中 `muse.ai`。 */
    private fun matchesDomain(host: String, allowed: String): Boolean {
        val a = allowed.trim().lowercase().removePrefix("*.").removePrefix(".")
        if (a.isEmpty()) return false
        return host == a || host.endsWith(".$a")
    }

    private fun JsonElement?.asObjectOrThrow(): kotlinx.serialization.json.JsonObject =
        this?.jsonObject ?: throw PluginHostException("PARAM_INVALID", "params must be an object")

    /** 取参数里的必填字符串。显式返回非空，避免调用点被推断成 String?。 */
    private fun asStringOrThrow(element: JsonElement?, name: String): String {
        val s = (element as? JsonPrimitive)?.contentOrNull
        if (s.isNullOrBlank()) throw PluginHostException("PARAM_INVALID", "$name is required")
        return s
    }

    private fun JsonElement?.asLongOrNull(): Long? =
        (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

    @Suppress("UNCHECKED_CAST")
    private fun JsonElement.asStringList(): List<String> =
        (this as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

    private companion object {
        const val TAG = "PluginWebView"
        const val BRIDGE_NAME = "AicodeBridge"
        const val MAX_SESSIONS_PER_PLUGIN = 3
        const val DEFAULT_EVAL_TIMEOUT_MS = 30_000L
        const val DEFAULT_WAIT_TIMEOUT_MS = 60_000L
        const val DEFAULT_POLL_INTERVAL_MS = 500L
        const val EVAL_STEP_TIMEOUT_MS = 15_000L
        val ALLOWED_SCHEMES = setOf("http", "https")
    }
}
