package dev.aigw.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.aigw.app.AiGatewayApp
import dev.aigw.provider.DeviceAuthPoll
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 供应商登录页：独立 Activity 承载 WebView。
 *
 * 两种登录方式：
 * - **设备授权**（WorkBuddy）：按 state 轮询换取凭证；
 * - **浏览器回调**（Trae）：授权完成后上游重定向到本机回调端口，由网关接住并换 token。
 *
 * 放在独立 Activity 而不是 Compose Dialog 里，是为了避开 Dialog 内 WebView 与输入法的
 * composition 冲突（表现为输入的数字被倒序插入）。
 */
class WebLoginActivity : Activity() {

    private var polling = true
    private var webView: WebView? = null
    private var statusView: TextView? = null
    private lateinit var app: AiGatewayApp

    /** 回调只处理一次：页面可能多次触发同一跳转（history 与真实导航各一次）。 */
    private var callbackHandled = false

    /** 微信授权链接只复制一次，避免页面反复跳转时刷屏到剪贴板。 */
    private var wechatLinkHandled = false

    /** CLI 授权流程里的 state；页面登录后要用它把网页会话换成凭证。 */
    private var deviceState = ""

    private var mode = MODE_DEVICE

    /**
     * 是否强制桌面模式。
     *
     * WorkBuddy 的登录页按视口宽度分流，移动端只给按钮不给扫码界面；
     * 其余供应商的登录页按手机版设计，强上桌面模式反而难看。
     */
    private var forceDesktop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val state = intent.getStringExtra(EXTRA_STATE).orEmpty()
        val region = intent.getStringExtra(EXTRA_REGION) ?: "cn"
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_DEVICE
        this.mode = mode
        deviceState = state
        forceDesktop = intent.getBooleanExtra(EXTRA_DESKTOP, false)
        val providerId = intent.getStringExtra(EXTRA_PROVIDER).orEmpty()
        if (url.isEmpty() || (mode == MODE_DEVICE && state.isEmpty())) {
            finish()
            return
        }

        app = application as AiGatewayApp

        // 海外站点（X/Google）授权必须走代理；直连时先把风险写进状态栏，
        // 否则用户只看到「卡死」，不知道是网络问题还是页面问题。
        val proxyHint = region == "global" && !networkProxied()

        statusView = TextView(this).apply {
            text = if (proxyHint) "等待授权…（未检测到代理：需海外网络）" else "等待登录…"
            textSize = 12.5f
            setTextColor(BAR_SUBTLE)
            setPadding(0, 0, 0, 0)
        }
        val titleView = TextView(this).apply {
            text = "网页登录"
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(BAR_TEXT)
            setPadding(0, 0, 0, 6)
        }
        // 用浏览器打开对设备授权/回调登录都拿不回凭证，已移除；仅保留关闭
        val closeButton = TextView(this).apply {
            text = "✕"
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setTextColor(BAR_TEXT)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 999f
                setColor(BAR_CHIP)
            }
            setOnClickListener { finish() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(BAR_BG)
            setPadding(48, 40, 36, 32)
            addView(
                LinearLayout(this@WebLoginActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(titleView)
                    addView(statusView)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            // 当前页直接交给系统浏览器（轮询继续，不用重走登录）；海外站点在
            // Chrome 里的网络栈比 WebView 对 VPN/代理友好得多
            addView(
                TextView(this@WebLoginActivity).apply {
                    text = "浏览器打开"
                    textSize = 12f
                    setTextColor(BAR_ACCENT)
                    gravity = android.view.Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = 999f
                        setColor(BAR_CHIP)
                    }
                    setPadding(36, 18, 36, 18)
                    setOnClickListener {
                        val current = webView?.url ?: url
                        openInBrowser(current)
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = 20 },
            )
            addView(
                closeButton,
                LinearLayout.LayoutParams(80, 80),
            )
        }

        // 页面失样多半是内核版本问题（本机 WebView 只有 Chrome 96），不是 UA；
        // 桌面/手机版留给用户手动切，切完 reload 即可。
        var preferDesktop = false
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)
            settings.javaScriptCanOpenWindowsAutomatically = true
            addJavascriptInterface(LoginBridge(), "__aigwBridge")
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.userAgentString = when {
                forceDesktop -> DesktopWebMode.UA
                preferDesktop -> UA_DESKTOP
                else -> UA_MOBILE
            }
            // 登录跳转跨站（aistudio → account.xiaomi.com → sts 回调），第三方 Cookie 必须放行
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                    // 商汤授权页确认后会跳回本机端口；在这里接住，不交给 WebView 去加载白页
                    val target = request.url?.toString().orEmpty()
                    if (isCallbackUrl(target)) {
                        handleCallbackUrl(target)
                        return true
                    }
                    // weixin:// 是唤起微信 App 的自定义 scheme，WebView 里没有 handler，
                    // 继续加载只会得到一个错误页。
                    // 注意：open.weixin.qq.com/connect/qrconnect 是**扫码页本身**，
                    // 它会在任何浏览器里渲染二维码，绝不能拦。
                    if (target.startsWith("weixin://")) {
                        handleWechatAuthLink(target)
                        return true
                    }
                    return false
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    // 有些页面用 history.replaceState / pushState 跳转，不触发 shouldOverrideUrlLoading
                    if (url != null && isCallbackUrl(url)) {
                        view.stopLoading()
                        handleCallbackUrl(url)
                    }
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url != null && isCallbackUrl(url)) {
                        view.stopLoading()
                        handleCallbackUrl(url)
                    }
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    // 截获页面真实请求头里的 token：比 localStorage 更可靠
                    if (mode == MODE_TOKEN) view.evaluateJavascript(TOKEN_HOOK_JS, null)
                    // 授权完成后页面可能白屏、也可能只是把 JSON 吐出来：两种都要能捞到
                    if (mode == MODE_CALLBACK) view.evaluateJavascript(CALLBACK_HOOK_JS, null)
                    // 扫码登录只是完成了网页会话，CLI 那条 state 还没兑。
                    // 页面自己的前端会调这个接口把会话换成 state 凭证，这里代它做。
                    if (deviceState.isNotEmpty()) {
                        view.evaluateJavascript(cliExchangeJs(deviceState), null)
                    }
                    // 「页面已过期」说明 state 没等到授权：把引导写进状态栏，而不是让用户干看着
                    if (mode == MODE_DEVICE) {
                        view.evaluateJavascript(EXPIRED_CHECK_JS) { detected ->
                            if (detected == "true") {
                                statusView?.text = "页面已过期：需要海外网络完成授权。开 VPN 后点「浏览器打开」，或关闭后重新登录"
                            }
                        }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?,
                    error: android.webkit.WebResourceError?,
                ) {
                    if (request?.isForMainFrame != true) return
                    statusView?.text = "页面加载失败（${error?.errorCode ?: "?"}）：海外站点需 VPN/代理，开 VPN 后点「浏览器打开」重试"
                }
            }.let { if (forceDesktop) DesktopWebMode.wrapClient(it) else it }
            // 授权页常把「确认授权」写成 window.open：不支持多窗口时弹窗拿不到，
            // 页面就停在白屏，回调永远不来
            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message,
                ): Boolean {
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                    // 不真开新窗口：拿一个临时 WebView 接住它的导航，再交回主 WebView 处理
                    val popup = WebView(this@WebLoginActivity)
                    popup.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(v: WebView, request: android.webkit.WebResourceRequest): Boolean {
                            val target = request.url?.toString().orEmpty()
                            v.stopLoading()
                            if (isCallbackUrl(target)) {
                                handleCallbackUrl(target)
                            } else {
                                val main = webView
                                if (main != null) runOnUiThread { main.loadUrl(target) }
                            }
                            popup.destroy()
                            return true
                        }
                    }
                    transport.webView = popup
                    resultMsg.sendToTarget()
                    return true
                }
            }
            // 每次登录都要干净会话，否则上次已登录的 Cookie 会让页面直接跳回调，
            // 看起来像「还带着上一个账号的数据」
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            clearCache(true)
            loadUrl(url)
        }
        webView = web

        val progress = android.widget.ProgressBar(this).apply {
            isIndeterminate = true
        }
        val hintText = TextView(this).apply {
            text = "登录完成后会自动返回并添加账号"
            textSize = 12f
            setTextColor(BAR_SUBTLE)
            setPadding(0, 28, 0, 28)
        }
        val uaToggle = TextView(this).apply {
            text = if (preferDesktop) "手机版" else "桌面版"
            textSize = 12f
            setTextColor(BAR_ACCENT)
            setPadding(28, 28, 28, 28)
            setOnClickListener {
                preferDesktop = !preferDesktop
                text = if (preferDesktop) "手机版" else "桌面版"
                web.settings.userAgentString = if (preferDesktop) UA_DESKTOP else UA_MOBILE
                web.reload()
            }
        }
        val hint = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(BAR_BG)
            addView(hintText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(uaToggle)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(PAGE_BG)
            addView(header)
            addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 4))
            addView(hint)
        }
        // targetSdk 35 强制边到边：不把状态栏/导航栏 inset 让出来，顶部按钮会落在
        // 系统状态栏的触摸热区里，看起来「点了没反应」
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        // 背景是白色，状态栏图标需用深色，否则看不见
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(root)

        if (mode == MODE_CALLBACK) {
            pollCallbackLogin(app)
        } else if (mode == MODE_TOKEN) {
            pollTokenLogin(app, providerId)
        } else {
            pollDeviceAuth(app, state, region)
        }
    }

    /** 当前网络是否走了代理（App 内代理或系统 VPN 下发）；用于海外登录前的提示。 */
    private fun networkProxied(): Boolean = runCatching {
        val proxy = ProxySelector.getDefault()
            ?.select(URI("https://www.workbuddy.ai"))
            ?.firstOrNull()
        proxy != null && proxy.type() != Proxy.Type.DIRECT
    }.getOrDefault(false)

    /** DeepSeek / 智谱清言网页登录：从页面 localStorage / cookie 里取凭证。 */
    private fun pollTokenLogin(app: AiGatewayApp, providerId: String) {
        val web = webView ?: return
        Thread {
            var lastCred: String? = null
            repeat(300) {
                Thread.sleep(2000)
                if (!polling) return@Thread


                if (providerId == "mimo") {
                    // MiMo（小米 AI Studio）：登录态就在 cookie 里，
                    // provider 侧 Credentials.parse 能直接吃 k=v; k=v 这种串。
                    var raw: String? = null
                    val latch = CountDownLatch(1)
                    runOnUiThread {
                        raw = CookieManager.getInstance()
                            .getCookie("https://aistudio.xiaomi.com").orEmpty()
                        latch.countDown()
                    }
                    latch.await(3, TimeUnit.SECONDS)
                    val cookie = raw.orEmpty()
                    val hasAll = cookie.contains("serviceToken=") &&
                        cookie.contains("userId=") &&
                        (cookie.contains("xiaomichatbot_ph=") || cookie.contains("ph_token="))
                    if (!hasAll) {
                        runOnUiThread { statusView?.text = "请在页面完成小米账号登录" }
                        return@repeat
                    }
                    if (cookie == lastCred) return@repeat
                    lastCred = cookie
                    val result = runCatching {
                        app.engine.completeTokenLogin(providerId, cookie)
                    }
                    result.onSuccess { finishWithAccount(it.displayName) }
                        .onFailure { e ->
                            runOnUiThread { statusView?.text = "登录失败：${e.message}" }
                        }
                    return@Thread
                }

                if (providerId == "chatglm") {
                    // 智谱清言：凭证在 cookie（chatglm_token 等），登录成功后读取并提交
                    var raw: String? = null
                    val latch = CountDownLatch(1)
                    runOnUiThread {
                        val cookie = CookieManager.getInstance().getCookie("https://chatglm.cn").orEmpty()
                        raw = chatglmCredentialJson(cookie)
                        latch.countDown()
                    }
                    latch.await(3, TimeUnit.SECONDS)
                    // 这里是普通 JSON 字符串，不是 evaluateJavascript 的字面量，直接判空即可
                    val cred = raw
                    if (cred.isNullOrBlank() || cred.length < 32) {
                        runOnUiThread { statusView?.text = "请在页面用手机号登录，完成后自动返回" }
                        return@repeat
                    }
                    // 访客态等失败后不能停止轮询：等 cookie 变化（真正登录后 token 会变）再重试
                    if (cred == lastCred) return@repeat
                    lastCred = cred
                    val result = runCatching { app.engine.completeTokenLogin(providerId, cred) }
                    result.onSuccess {
                        finishWithAccount(it.displayName)
                        return@Thread
                    }
                    result.onFailure { e ->
                        runOnUiThread {
                            statusView?.text = "登录失败：${e.message}（请在页面完成手机号登录）"
                        }
                    }
                    return@repeat
                }


                // DeepSeek：页面还停在登录页时，localStorage 里可能是旧 token，提交了也会 INVALID_TOKEN
                val pageUrl = runOnUiThreadValue(web) { it.url }.orEmpty()
                if (pageUrl.isBlank() || pageUrl.contains("/sign_in")) {
                    runOnUiThread { statusView?.text = "等待登录…" }
                    return@repeat
                }

                var raw: String? = null
                val latch = CountDownLatch(1)
                runOnUiThread {
                    web.evaluateJavascript(TOKEN_READ_JS) { value ->
                        raw = value
                        latch.countDown()
                    }
                }
                latch.await(3, TimeUnit.SECONDS)
                val token = parseJsString(raw)
                // 页面未登录时 localStorage 可能是空串或占位 JSON，不能拿去建账号
                if (token.isNullOrBlank() || token.length < 16) return@repeat
                val result = runCatching { app.engine.completeTokenLogin(providerId, token) }
                result.onSuccess { finishWithAccount(it.displayName) }
                    .onFailure { runOnUiThread { statusView?.text = "登录失败：${it.message}" } }
                return@Thread
            }
            runOnUiThread { statusView?.text = "等待登录超时，请重试" }
        }.start()
    }


    /**
     * 给新加入的豆包账号起个名字。
     *
     * 优先用 cookie 里的账号标识（uid_tt 之类），拿不到就用时间戳；
     * 用户之后可以在池里改名。
     */

    /** 从 chatglm.cn cookie 串里提取凭证，拼成 JSON 交给 provider 解析。 */
    private fun chatglmCredentialJson(cookie: String): String {
        val map = HashMap<String, String>()
        cookie.split(';').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) map[kv.substring(0, i).trim()] = kv.substring(i + 1).trim()
        }
        val token = map["chatglm_token"] ?: return ""
        val refresh = map["chatglm_refresh_token"] ?: ""
        val uid = map["chatglm_user_id"] ?: ""
        // 完整 cookie 一并存下：WAF 指纹（acw_tc / ssxmod 等）部分接口需要
        val cookieEscaped = cookie.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"provider\":\"chatglm\",\"token\":\"$token\",\"refresh\":\"$refresh\",\"uid\":\"$uid\",\"cookie\":\"$cookieEscaped\"}"
    }

    private fun <T> runOnUiThreadValue(web: WebView, block: (WebView) -> T): T? {
        var result: T? = null
        val latch = CountDownLatch(1)
        runOnUiThread {
            result = block(web)
            latch.countDown()
        }
        latch.await(2, TimeUnit.SECONDS)
        return result
    }

    /** evaluateJavascript 返回的是 JSON 字面量（字符串或 null），这里还原成文本。 */
    private fun parseJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val parsed = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return null
        if (!parsed.isJsonPrimitive) return null
        val primitive = parsed as JsonPrimitive
        if (!primitive.isString) return null
        return primitive.asString.ifBlank { null }
    }

    /** WorkBuddy 设备授权：轮询 state 拿 token。 */
    private fun pollDeviceAuth(app: AiGatewayApp, state: String, region: String) {
        Thread {
            var errors = 0
            repeat(300) {
                Thread.sleep(2000)
                if (!polling) return@Thread
                val poll = runCatching {
                    // state 不跨域：扫码那条在 codebuddy.cn 申请，必须回原域轮询
                    val at = pollBaseOf(intent.getStringExtra(EXTRA_URL).orEmpty())
                    if (at != null) {
                        app.engine.pollDeviceAuthAt("codebuddy", state, region, at)
                    } else {
                        app.engine.pollDeviceAuth("codebuddy", state, region)
                    }
                }.getOrElse { DeviceAuthPoll.Failed(it.message ?: "网络错误") }
                when (poll) {
                    is DeviceAuthPoll.Pending -> {
                        errors = 0
                        runOnUiThread { statusView?.text = "等待授权…" }
                    }
                    is DeviceAuthPoll.Success -> {
                        runCatching { app.engine.saveAccount(poll.account) }
                        finishWithAccount(poll.account.displayName)
                        return@Thread
                    }
                    is DeviceAuthPoll.Failed -> {
                        errors++
                        if (errors >= 3) {
                            runOnUiThread { statusView?.text = "登录失败：${poll.error}" }
                            return@Thread
                        }
                        runOnUiThread { statusView?.text = "网络波动，重试中…" }
                    }
                }
            }
            runOnUiThread { statusView?.text = "等待超时，请重试" }
        }.start()
    }

    /** Trae 回调登录：网关收到回调并换到 token 后，这里轮询结果。 */
    /** 是否本机回调地址且带授权码。 */
    private fun isCallbackUrl(url: String): Boolean {
        if (mode != MODE_CALLBACK) return false
        val looksLocal = url.contains("127.0.0.1") || url.contains("localhost")
        val hasCode = url.contains("authorization_code=") || url.contains("code=")
        return looksLocal && hasCode
    }

    /**
     * 在 WebView 里直接交回调：不依赖本地端口能否被页面命中。
     * 商汤授权页确认后的白屏就是因为跳转没落到回调服务，这里绕开了那一步。
     */
    private fun handleWechatAuthLink(url: String) {
        if (wechatLinkHandled) return
        wechatLinkHandled = true
        runOnUiThread {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("微信授权链接", url))
            // 链已复制，用户去微信里打开就能完成；本页继续轮询，不必等他回来点确认
            statusView?.text = "微信授权链接已复制：打开微信 → 粘贴到任意聊天 → 点开完成授权，成功后本页自动获取凭证"
            android.widget.Toast.makeText(
                this,
                "链接已复制，请到微信里粘贴打开",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }

    /**
     * 从登录地址推断要回哪个域轮询；返回 null 表示用默认域。
     *
     * 微信扫码那条件走的是 codebuddy.cn，与默认的 chatBase 不同域。
     */
    private fun pollBaseOf(url: String): String? = when {
        url.contains("codebuddy.cn") -> "https://www.codebuddy.cn"
        url.contains("workbuddy.ai") -> "https://www.workbuddy.ai"
        url.contains("workbuddy.cn") -> "https://www.workbuddy.cn"
        else -> null
    }

    /**
     * 把网页登录态换成 CLI state 凭证。
     *
     * 扫码只完成了网页会话（页面已经是「已登录」但停在登录页）。
     * 页面自己的前端在登录成功后会 POST `/console/login/enterprise?state=`
     * 把会话写进该 state，后台轮询才能拿到凭证；这里代它补上这一步。
     *
     * 定时重试而非只跑一次：注入发生在页面加载完成时，那一刻用户往往还没扫码，
     * 第一次必然拿不到会话。成功后停，失败继续（后台轮询会自己判定是否已入库）。
     */
    private fun cliExchangeJs(state: String): String =
        "(function(){" +
            "if(window.__aigwCliDone){return;}" +
            "if(window.__aigwCliTimer){clearInterval(window.__aigwCliTimer);}" +
            "var n=0;" +
            "var go=function(){" +
            "if(window.__aigwCliDone){return;}" +
            "if(n++>60){clearInterval(window.__aigwCliTimer);" +
            "try{window.__aigwBridge.onLog('超时：3 分钟内未完成登录');}catch(e){}" +
            "return;}" +
            "try{" +
            "fetch('/console/login/enterprise?state=" + state + "',{method:'POST'," +
            "credentials:'same-origin',headers:{'Content-Type':'application/json'},body:'{}'})" +
            ".then(function(r){return r.text().then(function(t){return {s:r.status,t:t};});})" +
            ".then(function(o){" +
            "var t=String(o.t||'');" +
            // 401 说明用户还没扫完，继续等；其余响应要么成功要么是真错误，都停下来上报
            "if(t.indexOf('invalid_token')>=0||o.s===401){return;}" +
            "window.__aigwCliDone=1;clearInterval(window.__aigwCliTimer);" +
            "try{window.__aigwBridge.onExchangeOk(t);}catch(e){}" +
            "try{window.__aigwBridge.onPayload(t);}catch(e){}" +
            "}).catch(function(e){" +
            "try{window.__aigwBridge.onLog('请求异常：'+e);}catch(e2){}" +
            ");" +
            "}catch(e){}" +
            "};" +
            "go();window.__aigwCliTimer=setInterval(go,3000);" +
            "})();"

    private fun handleCallbackUrl(url: String) {
        if (callbackHandled) return
        callbackHandled = true
        runOnUiThread { statusView?.text = "已收到授权回调，正在获取凭证…" }
        Thread {
            val result = runCatching { app.engine.completeBrowserLogin(url) }
            result.onSuccess { account -> finishWithAccount(account.displayName) }
            result.onFailure { e ->
                runOnUiThread {
                    statusView?.text = "登录失败：${e.message ?: "未知错误"}（可关闭后改用「粘贴回调链接」导入）"
                }
                callbackHandled = false
            }
        }.start()
    }

    /**
     * 从页面吐出的文本里捞授权码：可能是完整回调 URL，也可能只是一段 JSON。
     * 拿不到返回 null，不凭空造一个空回调去换凭证。
     */
    private fun extractCallbackUrl(payload: String): String? {
        if (payload.isBlank()) return null
        Regex("""https?://(?:127\.0\.0\.1|localhost):\d+/[^\s"'<>\\]*authorization_code=([A-Za-z0-9_\-%.]+)""")
            .find(payload)?.let { return it.value }
        val code = Regex("""authorization_code["']?\s*[:=]\s*["']?([A-Za-z0-9_\-.]{8,})""")
            .find(payload)?.groupValues?.get(1) ?: return null
        val base = runCatching { app.engine.browserCallbackBase() }.getOrNull() ?: return null
        return "$base?authorization_code=$code"
    }

    /** JS 接口：页面里嗅到回调/授权码时回传。 */
    private inner class LoginBridge {
        @android.webkit.JavascriptInterface
        fun onPayload(payload: String) {
            val url = extractCallbackUrl(payload) ?: return
            handleCallbackUrl(url)
        }

        /** 注入脚本的回传通道：成功/失败都写日志，否则失败时什么都看不到。 */
        @android.webkit.JavascriptInterface
        fun onLog(message: String) {
            app.engine.eventLog.info("登录", "扫码兑换：${message.take(200)}")
        }

        /** 兑换成功：直接刷新界面提示，后台轮询会跟着入库。 */
        @android.webkit.JavascriptInterface
        fun onExchangeOk(body: String) {
            app.engine.eventLog.success("登录", "扫码兑换成功：${body.take(160)}")
            runOnUiThread { statusView?.text = "已登录，正在添加账号…" }
        }
    }

    private fun pollCallbackLogin(app: AiGatewayApp) {
        Thread {
            repeat(300) {
                Thread.sleep(2000)
                if (!polling) return@Thread
                val result = app.engine.browserLoginResult ?: return@repeat
                if (app.engine.browserLoginSucceeded()) {
                    finishWithAccount(result)
                } else {
                    runOnUiThread { statusView?.text = "登录失败：$result" }
                }
                return@Thread
            }
            runOnUiThread { statusView?.text = "等待超时，请重试" }
        }.start()
    }

    /**
     * 用外部浏览器打开当前页。
     *
     * 优先夸克：系统默认浏览器可能被别的应用接管（比如被当成深链处理器的
     * 工具类 app），那样授权页会在错误的地方打开、回调也就收不到。
     */
    private fun openInBrowser(url: String) {
        val target = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return
        val base = Intent(Intent.ACTION_VIEW, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        for (pkg in PREFERRED_BROWSER_PACKAGES) {
            // 直接试：查 getPackageInfo 会受包可见性限制，没声明时一律返回「没装」
            if (runCatching { startActivity(Intent(base).setPackage(pkg)) }.isSuccess) return
        }
        runCatching { startActivity(base) }.onFailure {
            Toast.makeText(this, "没有可用的浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishWithAccount(name: String) {
        runOnUiThread {
            Toast.makeText(this, "登录成功：$name", Toast.LENGTH_LONG).show()
            setResult(RESULT_OK, Intent().putExtra(RESULT_UID, name))
            finish()
        }
    }

    override fun onDestroy() {
        polling = false
        webView?.let { runCatching { it.stopLoading(); it.destroy() } }
        webView = null
        super.onDestroy()
    }

    companion object {
        // 与 LoopColors 深色主题对齐，避免登录页从暗色 app 里跳出来像另一套产品
        private val PAGE_BG = Color.rgb(11, 14, 20)
        private val BAR_BG = Color.rgb(20, 24, 33)
        private val BAR_TEXT = Color.rgb(236, 239, 245)
        private val BAR_SUBTLE = Color.rgb(138, 147, 163)
        private val BAR_CHIP = Color.rgb(36, 43, 56)
        private val BAR_ACCENT = Color.rgb(108, 155, 255)

        // 本机系统 WebView 是 Chrome 96，UA 不要伪造更高版本：
        // 站点会据此下发跑不了的现代 JS/CSS（商汤页就是这样整页失样）。
        /** 优先尝试的外部浏览器，按顺序；都没装则回退系统默认。 */
        private val PREFERRED_BROWSER_PACKAGES = listOf(
            "com.quark.browser",
            "com.UCMobile",
            "com.android.chrome",
            "com.android.browser",
        )

        private const val UA_MOBILE =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/96.0.4664.104 Mobile Safari/537.36"
        private const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/96.0.4664.104 Safari/537.36"

        const val EXTRA_URL = "url"
        const val EXTRA_STATE = "state"
        const val EXTRA_REGION = "region"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PROVIDER = "provider"

        /** 强制桌面模式：伪装成桌面 Chrome，让按视口分流的页面出桌面布局。 */
        const val EXTRA_DESKTOP = "desktop"
        const val RESULT_UID = "uid"

        const val MODE_DEVICE = "device"
        const val MODE_CALLBACK = "callback"
        const val MODE_TOKEN = "token"

        /** 授权完成后的回调嗅探：拦截 window.open / fetch / XHR / location，把授权码回传。 */
        private const val CALLBACK_HOOK_JS = """
(function(){
  if (window.__aigwHooked) return; window.__aigwHooked = true;
  var report = function(t){
    try { if (t && String(t).indexOf('authorization_code') >= 0) window.__aigwBridge.onPayload(String(t)); } catch(e){}
  };
  try {
    var rawOpen = window.open;
    window.open = function(u){ report(u); return rawOpen ? rawOpen.apply(window, arguments) : null; };
  } catch(e){}
  try {
    var rawFetch = window.fetch;
    if (rawFetch) window.fetch = function(){
      var p = rawFetch.apply(this, arguments);
      try { p.then(function(r){ try { r.clone().text().then(report); } catch(e){} }); } catch(e){}
      return p;
    };
  } catch(e){}
  try {
    var rawSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(){
      var self = this;
      this.addEventListener('load', function(){ try { report(self.responseText); } catch(e){} });
      return rawSend.apply(this, arguments);
    };
  } catch(e){}
  setInterval(function(){ report(location.href); }, 500);
})();
"""

        /** 检测登录页是否渲染出了「页面已过期」（state 超时未完成授权）。 */
        private const val EXPIRED_CHECK_JS =
            "(function(){var b=document.body;" +
                "return b&&b.innerText&&b.innerText.indexOf('页面已过期')>=0?'true':'false';})()"

        /** 优先读请求头截到的 token，回退 localStorage（结构为 {value:...}）。 */
        private const val TOKEN_READ_JS =
            "(function(){if(window.__dsToken)return window.__dsToken;" +
                "try{var v=localStorage.getItem('userToken');if(!v)return '';" +
                "var o=JSON.parse(v);return (o&&o.value)?o.value:v;}catch(e){return '';}})()"

        /** 钩住页面 fetch / XHR，从真实请求头里取 Bearer token。 */
        private const val TOKEN_HOOK_JS = """
(function(){
  if (window.__dsTokenHook) return;
  window.__dsTokenHook = true;
  function capture(headers){
    try{
      if(!headers) return;
      var auth = null;
      if (typeof headers.get === 'function') auth = headers.get('authorization');
      if(!auth && typeof headers === 'object'){
        for(var k in headers){ if(String(k).toLowerCase() === 'authorization') auth = headers[k]; }
      }
      if(auth && String(auth).indexOf('Bearer ') === 0) window.__dsToken = String(auth).substring(7);
    }catch(e){}
  }
  var origFetch = window.fetch;
  if(origFetch){
    window.fetch = function(input, init){
      try{ if(init && init.headers) capture(init.headers); }catch(e){}
      return origFetch.apply(this, arguments);
    };
  }
  var origSet = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.setRequestHeader = function(name, value){
    try{
      if(String(name).toLowerCase() === 'authorization' && String(value).indexOf('Bearer ') === 0){
        window.__dsToken = String(value).substring(7);
      }
    }catch(e){}
    return origSet.apply(this, arguments);
  };
})();
"""
    }
}
