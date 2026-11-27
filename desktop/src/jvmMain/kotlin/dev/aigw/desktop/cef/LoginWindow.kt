package dev.aigw.desktop.cef

import com.google.gson.JsonObject
import dev.aigw.desktop.ProviderMeta
import dev.aigw.gateway.Engine
import dev.aigw.provider.DeviceAuthPoll
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.codebuddy.WechatQrLogin
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import java.net.URL
import javax.swing.JButton
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * 网页登录窗口（Swing JFrame + JCEF），替代 Android 的 WebLoginActivity /
 * JimengLoginActivity / DoubaoLoginActivity。
 *
 * 三种模式（与 Android 的 MODE_* 一致）：
 * - [LoginHooks.MODE_DEVICE]：WorkBuddy 设备授权，轮询 state 换凭证。
 * - [LoginHooks.MODE_CALLBACK]：Trae / ChatGPT / 小浣熊，回调嗅探到
 *   authorization_code 后调 [Engine.completeBrowserLogin]。
 * - [LoginHooks.MODE_TOKEN]：DeepSeek / ChatGLM / MiMo / muse / 即梦 / 豆包，
 *   登录后从 Cookie/localStorage 抓 token 或凭证。
 *
 * 成功时调 [onAccount]（账号已由 Engine 写进池），失败/关闭调 [onDone]。
 * 所有耗时轮询都在后台线程；Swing 操作走 EDT。
 */
class LoginWindow(
    private val engine: Engine,
    private val providerId: String,
    private val startUrl: String,
    private val mode: String,
    private val region: String = "cn",
    /** 设备授权的 state（MODE_DEVICE 时用）。 */
    private val deviceState: String = "",
    private val onAccount: (ProviderAccount) -> Unit,
    private val onDone: (String?) -> Unit,
) {
    private val frame = JFrame(ProviderMeta.displayName(providerId) + " 登录 — VantaGate")
    private val statusLabel = JLabel("正在打开登录页…")
    private val urlLabel = JLabel(startUrl)

    @Volatile
    private var finished = false

    @Volatile
    private var polling = true

    private var browser: JcefBrowser? = null

    fun show() {
        SwingUtilities.invokeLater {
            frame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            frame.layout = BorderLayout()
            frame.minimumSize = Dimension(900, 640)
            val screen = Toolkit.getDefaultToolkit().screenSize
            frame.setSize(1000, 720)
            frame.setLocation((screen.width - 1000) / 2, (screen.height - 720) / 2)

            val header = JPanel(BorderLayout())
            val title = JPanel(FlowLayout(FlowLayout.LEFT))
            title.add(JLabel(ProviderMeta.displayName(providerId) + " 登录"))
            val openExternal = JButton("在系统浏览器中打开")
            openExternal.addActionListener { openExternal() }
            val copyLink = JButton("复制链接")
            copyLink.addActionListener {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(startUrl), null)
                setStatus("链接已复制")
            }
            val close = JButton("关闭")
            close.addActionListener { finish(null) }
            val actions = JPanel(FlowLayout(FlowLayout.RIGHT))
            actions.add(openExternal)
            actions.add(copyLink)
            actions.add(close)
            title.add(actions)
            header.add(title, BorderLayout.NORTH)
            val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            statusPanel.add(statusLabel)
            statusPanel.add(JLabel("  "))
            statusPanel.add(urlLabel)
            header.add(statusPanel, BorderLayout.SOUTH)
            frame.add(header, BorderLayout.NORTH)

            val page = try {
                JcefBrowser(
                    startUrl = startUrl,
                    // 桌面模式拦截只挂在真正需要桌面视口的登录页（WorkBuddy 扫码页按视口分流）；
                    // 其他登录页不挂——安卓同款拦截在登录流程里从来不启用，挂上反而丢 Set-Cookie
                    // 导致会话建不起来。UA 与 viewportFixJs 兜底仍对所有页生效。
                    requestHandler = if (mode == LoginHooks.MODE_DEVICE) {
                        DesktopModeRequestHandler { url ->
                            runCatching { browser?.getCookies(url).orEmpty() }.getOrDefault("")
                        }
                    } else {
                        null
                    },
                    afterLoad = { b ->
                        b.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                        b.evalVoid(DesktopWebMode.viewportFixJs)
                        when (mode) {
                            LoginHooks.MODE_TOKEN -> b.evalVoid(LoginHooks.TOKEN_HOOK_JS)
                            LoginHooks.MODE_CALLBACK -> b.evalVoid(LoginHooks.CALLBACK_HOOK_JS)
                        }
                        // 扫码登录要把网页会话兑换进 CLI state（安卓 onPageFinished 同款，
                        // 3 分钟重试在脚本内）。
                        if (deviceState.isNotEmpty()) b.evalVoid(LoginHooks.cliExchangeJs(deviceState))
                        // EXPIRED_CHECK_JS 走 evalJson 会在 CEF UI 线程上等桥回包：afterLoad
                        // 本就跑在 UI 线程上，5 秒必然卡死。放后台线程查，结果只写状态栏。
                        // 只在设备授权模式查（安卓同款）。
                        if (mode == LoginHooks.MODE_DEVICE) {
                            Thread {
                                val expired = LoginHooks.parseJsString(
                                    b.evalJson(LoginHooks.EXPIRED_CHECK_JS, 5_000),
                                )
                                if (expired == "true") setStatus("页面已过期，请重新打开登录")
                            }.apply { isDaemon = true }.start()
                        }
                    },
                )
            } catch (e: Throwable) {
                setStatus("JCEF 初始化失败：${e.message}。可用「在系统浏览器中打开」+ 粘贴回调链接导入。")
                frame.isVisible = true
                return@invokeLater
            }
            browser = page
            page.onBridge = { _, method, args ->
                when (method) {
                    "onPayload" -> {
                        val payload = args.getOrNull(0).orEmpty()
                        if (payload.contains("authorization_code")) {
                            Thread { completeCallback(payload) }.apply { isDaemon = true }.start()
                        }
                    }
                    "onLog" -> {
                        val msg = args.getOrNull(0).orEmpty()
                        runCatching { engine.eventLog.info("登录", "扫码兑换：${msg.take(200)}") }
                        if (msg.isNotBlank()) setStatus(msg)
                    }
                    "onExchangeOk" -> {
                        val body = args.getOrNull(0).orEmpty()
                        runCatching { engine.eventLog.success("登录", "扫码兑换成功：${body.take(160)}") }
                        setStatus("已登录，正在添加账号…")
                    }
                }
            }
            page.onPopup = { url -> handlePopup(page, url) }
            page.onExternalScheme = { url -> setStatus("已复制链接，请在对应应用中打开：$url") }
            page.clearCookiesFor(startUrl)
            frame.add(page.component, BorderLayout.CENTER)
            frame.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    finish(null)
                }
            })
            frame.isVisible = true
            setStatus("等待登录…")
            startPolling(page)
        }
    }

    private fun openExternal() {
        runCatching { Desktop.getDesktop().browse(URI(startUrl)) }
            .onFailure { setStatus("打开系统浏览器失败：${it.message}") }
    }

    /** 弹窗（window.open / OAuth 新窗口）：回调地址直接交主页面，其余开临时窗承接。 */
    private fun handlePopup(page: JcefBrowser, url: String) {
        if (isCallbackUrl(url)) {
            page.load(url)
            return
        }
        openPopupWindow(page, url)
    }

    /** 回调地址判定（安卓 WebLoginActivity.isCallbackUrl 原样移植）。 */
    private fun isCallbackUrl(url: String): Boolean {
        if (mode != LoginHooks.MODE_CALLBACK) return false
        val looksLocal = url.contains("127.0.0.1") || url.contains("localhost")
        val hasCode = url.contains("authorization_code=") || url.contains("code=")
        return looksLocal && hasCode
    }

    /** 临时弹窗：接住授权页的导航，落到回调地址时交回主页面（安卓 onCreateWindow 同款）。 */
    private fun openPopupWindow(mainPage: JcefBrowser, url: String) {
        SwingUtilities.invokeLater {
            val popupFrame = JFrame("授权 — VantaGate")
            popupFrame.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            popupFrame.layout = BorderLayout()
            popupFrame.setSize(900, 640)
            val screen = Toolkit.getDefaultToolkit().screenSize
            popupFrame.setLocation((screen.width - 900) / 2, (screen.height - 640) / 2)
            val popup = try {
                JcefBrowser(
                    startUrl = url,
                    afterLoad = { b ->
                        b.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                        val cur = b.currentUrl
                        if (isCallbackUrl(cur)) {
                            SwingUtilities.invokeLater { popupFrame.dispose() }
                            // 回调服务器在弹窗导航时已收到 code；还没入库才交主页面走钩子路径
                            if (!engine.browserLoginSucceeded()) mainPage.load(cur)
                        }
                    },
                )
            } catch (e: Throwable) {
                setStatus("弹窗打开失败：${e.message}")
                return@invokeLater
            }
            popup.onPopup = { nested -> handlePopup(mainPage, nested) }
            popup.onExternalScheme = { u -> setStatus("已复制链接，请在对应应用中打开：$u") }
            val bar = JPanel(FlowLayout(FlowLayout.RIGHT))
            val closeBtn = JButton("关闭")
            closeBtn.addActionListener { runCatching { popup.close() }; popupFrame.dispose() }
            bar.add(closeBtn)
            popupFrame.add(bar, BorderLayout.NORTH)
            popupFrame.add(popup.component, BorderLayout.CENTER)
            popupFrame.addWindowListener(object : java.awt.event.WindowAdapter() {
                override fun windowClosed(e: java.awt.event.WindowEvent?) {
                    runCatching { popup.close() }
                }
            })
            popupFrame.isVisible = true
        }
    }

    private fun setStatus(text: String) {
        SwingUtilities.invokeLater { statusLabel.text = text }
    }

    private fun finish(account: ProviderAccount?) {
        if (finished) return
        finished = true
        polling = false
        SwingUtilities.invokeLater {
            runCatching { browser?.close() }
            browser = null
            runCatching { frame.dispose() }
        }
        if (account != null) {
            runCatching { onAccount(account) }
        } else {
            runCatching { onDone(null) }
        }
    }

    private fun finishWithError(message: String) {
        if (finished) return
        finished = true
        polling = false
        setStatus(message)
        SwingUtilities.invokeLater {
            runCatching { browser?.close() }
            browser = null
            runCatching { frame.dispose() }
        }
        runCatching { onDone(message) }
    }

    // ------------------------------------------------------------------ 轮询

    private fun startPolling(page: JcefBrowser) {
        Thread {
            when (mode) {
                LoginHooks.MODE_DEVICE -> pollDeviceAuth()
                LoginHooks.MODE_CALLBACK -> watchBrowserLogin()
                LoginHooks.MODE_TOKEN -> pollTokenLogin(page)
            }
        }.apply { isDaemon = true }.start()
    }

    /** WorkBuddy 设备授权：轮询 state 拿 token（与 Android pollDeviceAuth 一致）。 */
    private fun pollDeviceAuth() {
        var errors = 0
        repeat(300) {
            if (!polling || finished) return
            Thread.sleep(2000)
            if (!polling || finished) return
            val poll = runCatching {
                // state 不跨域：扫码那条在 codebuddy.cn 申请，必须回原域轮询
                val at = pollBaseOf(startUrl)
                if (at != null) {
                    engine.pollDeviceAuthAt("codebuddy", deviceState, region, at)
                } else {
                    engine.pollDeviceAuth("codebuddy", deviceState, region)
                }
            }.getOrElse { DeviceAuthPoll.Failed(it.message ?: "网络错误") }
            when (poll) {
                is DeviceAuthPoll.Pending -> {
                    errors = 0
                    setStatus("等待授权…")
                }
                is DeviceAuthPoll.Success -> {
                    runCatching { engine.saveAccount(poll.account) }
                    setStatus("登录成功：${poll.account.displayName}")
                    finish(poll.account)
                    return
                }
                is DeviceAuthPoll.Failed -> {
                    errors++
                    if (errors >= 3) {
                        finishWithError("登录失败：${poll.error}")
                        return
                    }
                    setStatus("网络波动，重试中…")
                }
            }
        }
        finishWithError("等待超时，请重试")
    }

    /** 回调登录：等 Engine 的回调服务器收到 code（与 Android watchBrowserLogin 一致）。 */
    private fun watchBrowserLogin() {
        repeat(150) {
            Thread.sleep(2000)
            if (!polling || finished) return
            if (engine.browserLoginSucceeded()) {
                setStatus("登录成功：${engine.browserLoginResult.orEmpty()}")
                // 账号已由 Engine 写进池；finish(null) 会调 onDone(null) 让 UI 刷新
                finish(null)
                return
            }
        }
        setStatus("等待超时：授权完成后可复制浏览器地址栏链接，回来点「粘贴回调链接导入」")
    }

    @Volatile
    private var callbackHandling = false

    private fun completeCallback(payload: String) {
        if (finished) return
        // 回调双入口（拦截器 GET 与页面钩子）可能并发调进来：安卓同款 callbackHandled 门，
        // 失败时复位以便重试，成功后由 finish 终结。
        if (callbackHandling) return
        callbackHandling = true
        val result = runCatching { engine.completeBrowserLogin(payload) }
        result.onSuccess {
            setStatus("登录成功：${it.displayName}")
            finish(it)
        }.onFailure {
            callbackHandling = false
            setStatus("登录失败：${it.message}")
        }
    }

    /** Token 登录：按供应商从 Cookie/localStorage 抓凭证（与 Android pollTokenLogin 一致）。 */
    private fun pollTokenLogin(page: JcefBrowser) {
        // 上次已提交过的凭证：失败后不再重复提交，等凭证变化再试（安卓同款 lastCred）。
        var lastCred: String? = null
        repeat(300) {
            Thread.sleep(2000)
            if (!polling || finished) return
            val pageUrl = page.currentUrl
            if (pageUrl.isBlank()) return@repeat
            try {
                when (providerId) {
                    "muse" -> {
                        val cookie = runCatching { page.getCookies("https://muse.ai") }.getOrDefault("")
                        if (cookie.contains("hatch_sess")) {
                            val sess = Regex("hatch_sess=([^;\\s]+)").find(cookie)?.groupValues?.get(1).orEmpty()
                            val uid = sess.take(8).ifEmpty { "muse" + System.currentTimeMillis() % 100000 }
                            val account = ProviderAccount(
                                providerId = "muse",
                                uid = uid,
                                displayName = "muse $uid",
                                secret = JsonObject().apply { addProperty("cookie", cookie) }.toString(),
                            )
                            engine.pool.add(account)
                            setStatus("登录成功：${account.displayName}")
                            finish(account)
                            return
                        }
                    }
                    "chatglm" -> {
                        val cookie = runCatching { page.getCookies("https://chatglm.cn") }.getOrDefault("")
                        val cred = LoginHooks.chatglmCredentialJson(cookie)
                        if (cred.isBlank()) {
                            setStatus("请在页面用手机号登录，完成后自动返回")
                            return@repeat
                        }
                        // 访客态等失败后不能停止轮询：等 cookie 变化（真正登录后 token 会变）再重试
                        if (cred == lastCred) return@repeat
                        lastCred = cred
                        val result = runCatching { engine.completeTokenLogin(providerId, cred) }
                        result.onSuccess {
                            setStatus("登录成功：${it.displayName}")
                            finish(it)
                            return
                        }.onFailure {
                            setStatus("登录失败：${it.message}（请在页面完成手机号登录）")
                            return@repeat
                        }
                    }
                    "jimeng" -> {
                        val cookie = runCatching { page.getCookies("https://jimeng.jianying.com") }.getOrDefault("")
                        if (cookie.contains("sessionid=")) {
                            val account = persistJimeng(cookie) ?: return@repeat
                            setStatus("登录成功：${account.displayName}")
                            finish(account)
                            return
                        }
                    }
                    "doubao" -> {
                        val cookie = runCatching { page.getCookies("https://www.doubao.com") }.getOrDefault("")
                        if (cookie.contains("sessionid=")) {
                            val account = persistDoubao(page, cookie)
                            if (account != null) {
                                setStatus("登录成功：${account.displayName}")
                                finish(account)
                                return
                            }
                            // 设备标识还没落盘：等几秒再试（Android 同款重试）
                            setStatus("登录已成功，正在等页面写入设备标识…")
                        }
                    }
                    "mimo" -> {
                        val cookie = runCatching { page.getCookies("https://aistudio.xiaomi.com") }.getOrDefault("")
                        // 安卓同款：serviceToken + userId + (xiaomichatbot_ph 或 ph_token)
                        // 三件套齐了才提交，只凭 serviceToken 就提交必然失败。
                        val hasAll = cookie.contains("serviceToken=") &&
                            cookie.contains("userId=") &&
                            (cookie.contains("xiaomichatbot_ph=") || cookie.contains("ph_token="))
                        if (!hasAll) {
                            setStatus("请在页面完成小米账号登录")
                            return@repeat
                        }
                        if (cookie == lastCred) return@repeat
                        lastCred = cookie
                        val result = runCatching { engine.completeTokenLogin(providerId, cookie) }
                        result.onSuccess {
                            setStatus("登录成功：${it.displayName}")
                            finish(it)
                            return
                        }.onFailure {
                            setStatus("登录失败：${it.message}")
                            return@repeat
                        }
                    }
                    else -> {
                        // DeepSeek 等：优先读请求头截到的 token，回退 localStorage
                        if (pageUrl.contains("/sign_in")) {
                            setStatus("等待登录…")
                            return@repeat
                        }
                        val raw = page.evalJson(LoginHooks.TOKEN_READ_JS, 3_000)
                        val token = LoginHooks.parseJsString(raw)
                        if (token.isNullOrBlank() || token.length < 16) return@repeat
                        val account = engine.completeTokenLogin(providerId, token)
                        setStatus("登录成功：${account.displayName}")
                        finish(account)
                        return
                    }
                }
            } catch (e: Exception) {
                setStatus("登录失败：${e.message}")
                return
            }
        }
        finishWithError("等待登录超时，请重试")
    }

    // ------------------------------------------------------- 即梦 / 豆包落盘

    private fun persistJimeng(cookie: String): ProviderAccount? {
        val sid = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sessionid=") }
            ?.substringAfter("sessionid=")
            ?.trim()
            .orEmpty()
        if (sid.isEmpty()) return null
        val secret = JsonObject().apply { addProperty("cookie", cookie) }.toString()
        val account = ProviderAccount(
            providerId = "jimeng",
            uid = sid.take(16),
            displayName = "即梦 " + sid.take(6),
            secret = secret,
        )
        engine.pool.add(account)
        return account
    }

    private fun persistDoubao(page: JcefBrowser, cookie: String): ProviderAccount? {
        val js = "(function(){try{" +
            "var d=JSON.parse(localStorage.getItem('samantha_web_web_id')||'{}');" +
            "var t=JSON.parse(localStorage.getItem('__tea_cache_tokens_497858')||'{}');" +
            "return JSON.stringify({device_id:(d.web_id||''),web_id:(t.web_id||'')});" +
            "}catch(e){return '{}';}})()"
        val raw = LoginHooks.parseJsString(page.evalJson(js, 5_000)) ?: "{}"
        val obj = runCatching { com.google.gson.JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: JsonObject()
        val deviceId = obj.get("device_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val webId = obj.get("web_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (deviceId.isBlank() || webId.isBlank()) return null
        val secret = JsonObject().apply {
            addProperty("cookie", cookie)
            addProperty("device_id", deviceId)
            addProperty("web_id", webId)
            addProperty("local_app_id", "582478")
        }.toString()
        // 同一个 device_id 视为同一账号，重复登录时覆盖而不是堆叠
        val uid = deviceId.hashCode().toUInt().toString(16)
        val name = runCatching { fetchDoubaoScreenName(cookie) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: ("豆包 " + deviceId.takeLast(6))
        val account = ProviderAccount(providerId = "doubao", uid = uid, displayName = name, secret = secret)
        engine.pool.add(account)
        return account
    }

    private fun fetchDoubaoScreenName(cookie: String): String? {
        val conn = (URL("https://www.doubao.com/passport/account/info")
            .openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Cookie", cookie)
            setRequestProperty("Referer", "https://www.doubao.com/chat/")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
            )
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val text = conn.inputStream.readBytes().toString(Charsets.UTF_8)
            val obj = runCatching { com.google.gson.JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: return null
            obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("screen_name")?.takeIf { it.isJsonPrimitive }?.asString
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun pollBaseOf(url: String): String? = runCatching {
        val u = URL(url)
        "${u.protocol}://${u.host}"
    }.getOrNull()

    companion object {
        /** 微信扫码登录的授权页地址（WechatQrLogin.requestState + urlOf）。 */
        fun wechatLoginUrl(region: String): Pair<String, String> {
            val host = if (region == "global") "https://www.workbuddy.ai" else "https://www.codebuddy.cn"
            val st = WechatQrLogin.requestState(host)
            return WechatQrLogin.urlOf(host, st.second) to st.first
        }
    }
}
