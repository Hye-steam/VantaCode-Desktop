package com.vantacode.feature.plugin.runtime

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** webview 宿主调用失败；[code] 是插件可判断的稳定错误码（对照 Android PluginWebViewService）。 */
class PluginWebViewException(val code: String, message: String) : Exception(message)

/**
 * 一个 webview 会话的浏览器句柄：隐藏会话与登录窗口共用，由 cef 侧实现。
 * JS→宿主的消息走 [onBridgeMessage]（页面调 `MuseBridge`/`AicodeBridge` 的 `onOk`/`onFail`
 * 时由实现方翻译过来，与 Android 的 `Bridge.onOk/onFail` 同款）。
 */
interface PluginWebViewBrowserHandle {
    val currentUrl: String
    val ready: Boolean
    fun load(url: String)

    /** 执行 JS 表达式并取回值的 JSON 文本；超时/异常返回 null（与 Android evaluateJavascript 的回传一致）。 */
    fun evalJson(js: String, timeoutMs: Long): String?

    fun evalVoid(js: String)
    var onBridgeMessage: ((method: String, args: List<String>) -> Unit)?
    fun close()
}

/** 浏览器句柄工厂：默认实现见 cef 侧的 [com.vantacode.feature.plugin.cef.CefPluginWebViewBrowserFactory]。 */
interface PluginWebViewBrowserFactory {
    fun create(startUrl: String, userAgent: String?): PluginWebViewBrowserHandle
}

/** 全局 Cookie 管理器的薄封装：登录窗口与隐藏会话共享同一份 cookie（CEF 全局 CookieManager）。 */
interface PluginWebViewCookieStore {
    fun getCookies(url: String, timeoutMs: Long = 2_000L): String
    fun setCookie(url: String, name: String, value: String, domain: String? = null)
    fun flush()
    fun clearAllCookies()
}

/** 可见登录窗口的打开参数（对照插件调 `host.webview.login` / `aicode.webview.login` 时传的字段）。 */
data class PluginWebViewLoginRequest(
    val url: String,
    val title: String,
    val finishText: String,
    val showClose: Boolean = true
)

/** 登录窗口的结果：确认时带上当时的最终 URL；关窗/点关闭为取消；超时未确认则为超时。 */
sealed interface PluginWebViewLoginOutcome {
    data class Confirmed(val finalUrl: String) : PluginWebViewLoginOutcome
    object Canceled : PluginWebViewLoginOutcome
    object TimedOut : PluginWebViewLoginOutcome
}

/**
 * 可见登录窗口句柄：浏览器由窗口持有。
 * [awaitOutcome] 可多次调用（结果一旦确定就立即返回）；[close] 只关窗口，浏览器由 service 的 destroy 关。
 */
interface PluginWebViewLoginWindowHandle {
    val browser: PluginWebViewBrowserHandle
    suspend fun awaitOutcome(timeoutMs: Long): PluginWebViewLoginOutcome
    fun close()
}

/** 登录窗口工厂：默认实现见 cef 侧的 [com.vantacode.feature.plugin.cef.CefPluginWebViewLoginWindowFactory]。 */
interface PluginWebViewLoginWindowFactory {
    fun create(request: PluginWebViewLoginRequest): PluginWebViewLoginWindowHandle
}

/**
 * 插件可用的隐藏 WebView 通道 + 网页登录窗口（桌面版，对照 Android PluginWebViewService）。
 *
 * Android 的能力分两层，桌面全部照搬：
 * - 原语：`open/setCookies/getCookies/eval/waitFor/awaitMessage/saveBlob/close`。
 *   每个会话独占一个浏览器；登录态必须在 load 之前落进 CookieManager（先注 cookie 再 load，
 *   与 Android `open` / VantaGate `MuseWebTransport.injectCookies` 同款顺序）。
 * - 登录：`login`（进程桥 `host.webview.login` 与页桥 `aicode.webview.login` 共用）。
 *   Android 其实没有 `webview.login` 这个方法——插件（bigorange.github-mcp、
 *   一束挽风.pojia-gateway）期望的是一次性登录调用：弹可见窗口、用户登录后点「完成登录」、
 *   宿主按 `cookieUrls` 取回各站点的 cookie。桌面用 JFrame + JCEF 登录窗口实现它，
 *   语义按插件的实际调用方式定（见 [login] 的注释）。
 *
 * 与 Android 的取舍（桌面环境所限）：
 * - `open` 的 `allowDomains` 白名单：桌面版插件清单没有该字段，无法校验，只校验 http(s) scheme。
 *   插件本身的调用仍受宿主其它能力约束。
 * - `open` 的 `userAgent`：JCEF 的 UA 是 CefSettings 全局项，单个 browser 无法覆盖，桌面忽略该参数。
 * - `saveBlob` 除了 Android 的 `{session, var, dir, name}` 形态，还接受 `{data|base64, dir, name}`
 *  （插件自带 base64 直接落盘）与 `{url, dir, name}`（http(s) 下载落盘），因为桌面无法确认
 *   vanta.gateway 的 muse 流程是否先经过 `open` 拿到了 session；三种形态都返回 `{path, size}`。
 * - `login` 的 `accentColor` 只影响窗口配色，桌面忽略；`showClose` 为 false 时不显示关闭按钮，
 *   但窗口右上角的 X 始终可以取消（取消即 `LOGIN_CANCELED`，绝不挂起）。
 *
 * 失败统一抛 [PluginWebViewException]，code 稳定：`WEBVIEW_OFF`、`WEBVIEW_SESSION`、
 * `WEBVIEW_LIMIT`、`WEBVIEW_URL_REJECTED`、`WEBVIEW_TIMEOUT`、`WEBVIEW_MEDIA`、
 * `LOGIN_CANCELED`、`PARAM_INVALID`、`IO_ERROR`。
 */
@Singleton
class PluginWebViewService @Inject constructor(
    private val browserFactory: PluginWebViewBrowserFactory,
    private val cookieStore: PluginWebViewCookieStore,
    private val loginWindowFactory: PluginWebViewLoginWindowFactory
) {

    /** 一个会话就是一个独立浏览器 + 一条 JS→宿主的消息队列（与 Android 的 Session 同款）。 */
    private class Session(
        val id: String,
        val pluginId: String,
        val browser: PluginWebViewBrowserHandle,
        val events: LinkedBlockingQueue<Event>,
        /** 交互模式（可见登录窗）时持有窗口；隐藏模式为 null。 */
        val window: PluginWebViewLoginWindowHandle?
    ) {
        @Volatile
        var closed: Boolean = false

        /** 交互 open 时把窗口结果转发进 [events] 的后台任务；销毁时取消。 */
        var outcomeJob: Job? = null
    }

    /** 页面回调只带会话 id，按 id 找队列；JS 侧不需要也不应该知道别的东西。 */
    private sealed interface Event {
        data class Ok(val json: String) : Event
        data class Fail(val message: String) : Event
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 插件被停用/卸载时调用：把它名下的会话全销毁，避免浏览器后台常驻。 */
    fun closeAll(pluginId: String) {
        sessions.values.filter { it.pluginId == pluginId }.toList().forEach { destroy(it) }
    }

    suspend fun open(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val url = asStringOrThrow(obj["url"], "url")
        val userAgent = (obj["userAgent"] as? JsonPrimitive)?.contentOrNull
        // 交互模式：给用户真的能看到、能点的窗口（登录场景用）。
        // 默认是隐藏模式：只为了自动抓生成结果。
        val interactive = (obj["interactive"] as? JsonPrimitive)?.contentOrNull?.toBoolean() ?: false
        val cookies = (obj["cookies"] as? JsonPrimitive)?.contentOrNull
        val title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "登录"
        val finishText = (obj["finishText"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "完成登录"
        val session = openSession(pluginId, url, userAgent, interactive, cookies, title, finishText)
        if (interactive) {
            // 插件随后调 awaitMessage 等「用户点了完成」；把窗口结果转发进会话队列。
            // login() 不走这里——它直接 awaitOutcome，不需要转发。
            session.window?.let { window ->
                session.outcomeJob = scope.launch {
                    when (val outcome = window.awaitOutcome(Long.MAX_VALUE)) {
                        is PluginWebViewLoginOutcome.Confirmed ->
                            session.events.offer(Event.Ok("{\"confirm\":true}"))
                        PluginWebViewLoginOutcome.Canceled -> {
                            session.events.offer(Event.Fail(LOGIN_CANCELED_MESSAGE))
                            destroy(session)
                        }
                        PluginWebViewLoginOutcome.TimedOut -> Unit // 无限等，不会超时
                    }
                }
            }
        }
        return buildJsonObject { put("session", session.id) }
    }

    suspend fun setCookies(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val cookies = asStringOrThrow(obj["cookies"], "cookies")
        val url = asStringOrThrow(obj["url"], "url")
        withContext(Dispatchers.IO) { injectCookies(cookies, url) }
        return buildJsonObject { put("ok", true) }
    }

    /** 在页面里执行一段 JS，返回求值结果的 JSON 文本（与 Android 同款）。 */
    suspend fun eval(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val js = asStringOrThrow(obj["js"], "js")
        val timeoutMs = (obj["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: DEFAULT_EVAL_TIMEOUT_MS
        val value = evalValue(session, js, timeoutMs)
            ?: throw PluginWebViewException("WEBVIEW_TIMEOUT", "eval timed out")
        return buildJsonObject { put("value", value) }
    }

    /**
     * 反复求值 [expr]，直到它返回真值或超时（与 Android 同款）。
     * 返回命中时的那个值；超时抛 `WEBVIEW_TIMEOUT`。
     */
    suspend fun waitFor(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val expr = asStringOrThrow(obj["expr"], "expr")
        val timeoutMs = (obj["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: DEFAULT_WAIT_TIMEOUT_MS
        val intervalMs = (obj["intervalMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: DEFAULT_POLL_INTERVAL_MS

        // 表达式包一层 try 并把结果 JSON 化：求值回传的字符串本身又是 JSON 编码过的，
        // 所以判断前要先把外层引号剥掉（与 Android 同款）。
        val wrapped = "(function(){try{return JSON.stringify(($expr));}catch(e){return JSON.stringify({__err:String(e)})}})()"

        val hit = withTimeoutOrNull(timeoutMs) {
            while (true) {
                val raw = evalValue(session, wrapped, EVAL_STEP_TIMEOUT_MS)
                val parsed = unwrapJsonString(raw)
                val truthy = parsed != null && parsed != "null" && parsed != "false" &&
                    parsed != "\"\"" && !parsed.contains("__err")
                if (truthy) return@withTimeoutOrNull parsed
                delay(intervalMs)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        } ?: throw PluginWebViewException("WEBVIEW_TIMEOUT", "waitFor timed out after ${timeoutMs}ms")

        return buildJsonObject { put("value", hit) }
    }

    /**
     * 读取某站点在共享 CookieManager 里的 cookie（与 Android 同款）。
     *
     * 登录类流程拿到的会话常是 HttpOnly cookie，页面 JS 的 document.cookie 读不到，
     * 但 CookieManager 能看到全部。返回的串是 `a=1; b=2` 形式。
     * 有 session 就顺带校验归属，避免插件拿别人的会话 id 探 cookie；无 session 时只按域名读。
     */
    suspend fun getCookies(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val url = asStringOrThrow(obj["url"], "url")
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw PluginWebViewException("PARAM_INVALID", "url must be http(s): $url")
        }
        val sessionId = (obj["session"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        if (sessionId != null) requireSession(pluginId, sessionId)
        val raw = withContext(Dispatchers.IO) { cookieStore.getCookies(url) }
        return buildJsonObject { put("cookies", raw) }
    }

    /**
     * 等页面通过 JS 桥主动回调（`onOk`/`onFail`，与 Android 同款）。
     * 只要「跑一段异步脚本，完成时把结果推回来」的场景都靠它，宿主不需要知道页面细节。
     */
    suspend fun awaitMessage(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        val timeoutMs = (obj["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?: DEFAULT_WAIT_TIMEOUT_MS
        val event = withContext(Dispatchers.IO) {
            session.events.poll(timeoutMs, TimeUnit.MILLISECONDS)
        } ?: throw PluginWebViewException("WEBVIEW_TIMEOUT", "awaitMessage timed out after ${timeoutMs}ms")

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

    /**
     * 把页面里已挂好的 base64 直接写成文件，只回传路径（与 Android 同款，muse 视频那路）。
     *
     * 插件与宿主之间只有 stdin/stdout 一条 NDJSON 管道，宿主读插件输出时单行封顶 64K 字符。
     * 视频 base64 动辄 8MB+，无论怎么分块，只要运回插件就会被截断。
     * 所以字节不能过管道：宿主在本进程里把 window 上的 base64 取出来、解码后落盘，插件只收一个路径。
     *
     * Android 形态：`{session, var?, dir, name?}`，读 `window[var]`（默认 `__museBlob`）。
     * 桌面另外接受 `{data|base64, dir, name?}`（插件自带 base64 直接落盘）与
     * `{url, dir, name?}`（http(s) 下载落盘）；三种形态都返回 `{path, size}`。
     */
    suspend fun saveBlob(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val dir = asStringOrThrow(obj["dir"], "dir")
        val name = (obj["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "media.bin"
        if (name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
            throw PluginWebViewException("PARAM_INVALID", "invalid file name: $name")
        }
        val bytes: ByteArray = when {
            (obj["session"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true -> {
                val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
                val varName = (obj["var"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_BLOB_VAR
                if (!BLOB_VAR_NAME.matches(varName)) {
                    throw PluginWebViewException("PARAM_INVALID", "invalid var name: $varName")
                }
                readBlobFromPage(session, varName)
            }
            (obj["data"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true ||
                (obj["base64"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true -> {
                val raw = (obj["data"] as? JsonPrimitive)?.contentOrNull
                    ?: (obj["base64"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                decodeBase64(raw)
            }
            (obj["url"] as? JsonPrimitive)?.contentOrNull?.isNotBlank() == true -> {
                val url = asStringOrThrow(obj["url"], "url")
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw PluginWebViewException("PARAM_INVALID", "url must be http(s): $url")
                }
                downloadBytes(url)
            }
            else -> throw PluginWebViewException(
                "WEBVIEW_SESSION",
                "saveBlob needs a session, data/base64, or url"
            )
        }

        val target = withContext(Dispatchers.IO) {
            val d = File(dir)
            if (!d.isDirectory && !d.mkdirs()) {
                throw PluginWebViewException("IO_ERROR", "cannot create dir: $dir")
            }
            val f = File(d, name)
            try {
                f.outputStream().use { it.write(bytes) }
            } catch (e: Exception) {
                throw PluginWebViewException("IO_ERROR", "cannot write file: ${e.message}")
            }
            f
        }

        return buildJsonObject {
            put("path", target.absolutePath)
            put("size", bytes.size)
        }
    }

    /**
     * 网页登录：弹可见的 JCEF 登录窗口，加载插件给的 URL，让用户在里面完成登录，
     * 用户点「完成登录」后按 `cookieUrls` 取回各站点的 cookie。
     *
     * 参数（按 bigorange.github-mcp 与一束挽风.pojia-gateway 的实际调用）：
     * - `url`（必填）：登录页地址，必须是 http(s)。
     * - `title`（可选）：窗口标题，默认 `"登录"`。
     * - `finishText`（可选）：完成按钮文案，默认 `"完成登录"`。
     * - `cookieUrls`（可选）：要取 cookie 的 URL 列表；为空时默认只取 `url` 自身。
     * - `timeoutMs`（可选）：等用户确认的最长时长，默认 10 分钟，上限 30 分钟。
     * - `cookies`（可选）：打开前先注入的 cookie（`k=v; ...`），先注再 load，与 `open` 同款。
     * - `accentColor` / `showClose`：桌面忽略前者；后者为 false 时不显示关闭按钮，
     *   但窗口右上角的 X 始终可以取消。
     *
     * 返回 `{cookies, cookie, finalUrl}`：
     * - `cookies`：按 URL 分键的对象，`{ "<cookieUrl>": "k=v; k2=v2", ... }`。
     *   按 URL 分键而不是按 cookie 名分键，是因为 pojia-gateway 用
     *   `Object.values(result.cookies).join('; ')` 拼回完整 cookie 串交给
     *   `accountFromCookie` 解析 `k=v` 对；按名分键会丢掉名，只剩值。
     * - 每个 URL 的 cookie 串首对是哨兵 `_vanta_host=1`（见 [SENTINEL_COOKIE]）：
     *   github-mcp 的 `cookieHeader` 会把 map 的键（URL）当成 cookie 名拼成
     *   `key=value`，吃掉每串的第一个 `k=v` 对；哨兵替真实会话 cookie 挡这一刀，
     *   其它消费者把它当成一个无害的未知 cookie 忽略。
     * - `cookie`：各 URL 的 cookie 串用 `; ` 拼起来的总串（pojia-gateway 的拼法）。
     * - `finalUrl`：用户点「完成登录」时窗口的当前 URL。
     *
     * 错误：用户关窗/点关闭 → `LOGIN_CANCELED`（message 里带 `LOGIN_CANCELED`，
     * 插件按该字样判断取消）；超时未确认 → `WEBVIEW_TIMEOUT`；JCEF 不可用 → `WEBVIEW_OFF`。
     * 无论成功失败，登录窗口都会关闭，不留后台浏览器。
     */
    suspend fun login(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val url = asStringOrThrow(obj["url"], "url")
        val title = (obj["title"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "登录"
        val finishText = (obj["finishText"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "完成登录"
        val cookieUrls = (obj["cookieUrls"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() } }
            ?.filter { it.startsWith("http://") || it.startsWith("https://") }
            .orEmpty()
        val timeoutMs = (obj["timeoutMs"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
            ?.coerceIn(1_000L, MAX_LOGIN_TIMEOUT_MS)
            ?: DEFAULT_LOGIN_TIMEOUT_MS
        val cookies = (obj["cookies"] as? JsonPrimitive)?.contentOrNull

        val session = openSession(pluginId, url, null, true, cookies, title, finishText)
        try {
            val outcome = session.window?.awaitOutcome(timeoutMs)
                ?: throw PluginWebViewException("WEBVIEW_OFF", "login window unavailable")
            val finalUrl = when (outcome) {
                is PluginWebViewLoginOutcome.Confirmed -> outcome.finalUrl
                PluginWebViewLoginOutcome.Canceled ->
                    throw PluginWebViewException("LOGIN_CANCELED", LOGIN_CANCELED_MESSAGE)
                PluginWebViewLoginOutcome.TimedOut ->
                    throw PluginWebViewException("WEBVIEW_TIMEOUT", "login timed out after ${timeoutMs}ms")
            }
            val targets = if (cookieUrls.isEmpty()) listOf(url) else cookieUrls
            // 先在 builder 外面把 cookie 读出来：buildJsonObject / associateWith 的 lambda
            // 都不是 suspend，不能在里面 withContext；for 循环里可以。
            val cookieStrings = mutableMapOf<String, String>()
            for (targetUrl in targets) {
                cookieStrings[targetUrl] = withContext(Dispatchers.IO) { cookieStore.getCookies(targetUrl) }
            }
            val cookiesByUrl = buildJsonObject {
                cookieStrings.forEach { (targetUrl, raw) ->
                    put(targetUrl, withSentinel(raw))
                }
            }
            val cookieValues = targets.mapNotNull {
                (cookiesByUrl[it] as? JsonPrimitive)?.contentOrNull?.takeIf { s -> s.isNotBlank() }
            }
            return buildJsonObject {
                put("cookies", cookiesByUrl)
                put("cookie", cookieValues.joinToString("; "))
                put("finalUrl", finalUrl.ifBlank { url })
            }
        } finally {
            destroy(session)
        }
    }

    suspend fun close(pluginId: String, params: JsonElement): JsonElement {
        val obj = params.asObjectOrThrow()
        val session = requireSession(pluginId, asStringOrThrow(obj["session"], "session"))
        destroy(session)
        return buildJsonObject { put("ok", true) }
    }

    private suspend fun openSession(
        pluginId: String,
        url: String,
        userAgent: String?,
        interactive: Boolean,
        cookies: String?,
        title: String,
        finishText: String
    ): Session {
        val uri = runCatching { URI(url) }.getOrNull()
            ?: throw PluginWebViewException("PARAM_INVALID", "url is not a valid URI")
        if (uri.scheme !in ALLOWED_SCHEMES) {
            throw PluginWebViewException("WEBVIEW_URL_REJECTED", "only http(s) is allowed")
        }
        if (uri.host.isNullOrBlank()) {
            throw PluginWebViewException("PARAM_INVALID", "url has no host")
        }
        if (sessions.values.count { it.pluginId == pluginId } >= MAX_SESSIONS_PER_PLUGIN) {
            throw PluginWebViewException("WEBVIEW_LIMIT", "too many webviews for this plugin")
        }
        val id = "$pluginId:${System.nanoTime()}"
        val session = withContext(Dispatchers.IO) {
            createSession(pluginId, id, url, userAgent, interactive, cookies, title, finishText)
        }
        synchronized(sessions) {
            if (sessions.values.count { it.pluginId == pluginId } >= MAX_SESSIONS_PER_PLUGIN) {
                destroy(session)
                throw PluginWebViewException("WEBVIEW_LIMIT", "too many webviews for this plugin")
            }
            sessions[id] = session
        }
        return session
    }

    /**
     * 建会话（阻塞调用方线程，由调用处包在 Dispatchers.IO 里）。
     * 隐藏模式：有初始 cookie 时先建 about:blank、注 cookie、再 load（先注再 load，
     * 否则首屏请求就是登出态）；无 cookie 时直接建目标 URL。
     * 交互模式：建可见登录窗口（窗口自己持有浏览器）。
     */
    private fun createSession(
        pluginId: String,
        id: String,
        url: String,
        userAgent: String?,
        interactive: Boolean,
        cookies: String?,
        title: String,
        finishText: String
    ): Session {
        val events = LinkedBlockingQueue<Event>()
        if (!interactive) {
            val browser = try {
                if (!cookies.isNullOrBlank()) {
                    browserFactory.create("about:blank", userAgent).also { b ->
                        injectCookies(cookies, url)
                        b.load(url)
                    }
                } else {
                    browserFactory.create(url, userAgent)
                }
            } catch (e: PluginWebViewException) {
                throw e
            } catch (e: Throwable) {
                throw PluginWebViewException("WEBVIEW_OFF", "webview unavailable: ${e.message}")
            }
            val session = Session(id, pluginId, browser, events, null)
            browser.onBridgeMessage = { method, args ->
                when (method) {
                    "onOk" -> session.events.offer(Event.Ok(args.getOrNull(0).orEmpty()))
                    "onFail" -> session.events.offer(Event.Fail(args.getOrNull(0).orEmpty()))
                }
            }
            return session
        }
        val window = try {
            loginWindowFactory.create(
                PluginWebViewLoginRequest(url = url, title = title, finishText = finishText, showClose = true)
            )
        } catch (e: PluginWebViewException) {
            throw e
        } catch (e: Throwable) {
            throw PluginWebViewException("WEBVIEW_OFF", "webview unavailable: ${e.message}")
        }
        val session = Session(id, pluginId, window.browser, events, window)
        window.browser.onBridgeMessage = { method, args ->
            when (method) {
                "onOk" -> session.events.offer(Event.Ok(args.getOrNull(0).orEmpty()))
                "onFail" -> session.events.offer(Event.Fail(args.getOrNull(0).orEmpty()))
            }
        }
        return session
    }

    private fun destroy(session: Session) {
        if (session.closed) return
        session.closed = true
        session.outcomeJob?.cancel()
        session.outcomeJob = null
        sessions.remove(session.id)
        runCatching { session.window?.close() }
        runCatching { session.browser.close() }
    }

    private fun requireSession(pluginId: String, sessionId: String): Session {
        val s = sessions[sessionId]
            ?: throw PluginWebViewException("WEBVIEW_SESSION", "unknown session $sessionId")
        if (s.pluginId != pluginId) {
            throw PluginWebViewException("WEBVIEW_SESSION", "session belongs to another plugin")
        }
        if (s.closed) throw PluginWebViewException("WEBVIEW_SESSION", "session already closed")
        return s
    }

    /**
     * 把 `k=v; k2=v2` 形式的 cookie 串写入指定 URL。
     * 与 Android / VantaGate 同款：同时写给裸域和带点的子域形式，两者的匹配规则不一样，
     * 只写一种会出现「页面能进但接口 401」。
     */
    private fun injectCookies(cookies: String, url: String) {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty()
        cookies.split(';').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) return@forEach
            runCatching { cookieStore.setCookie(url, name, value) }
            if (host.isNotBlank() && !host.startsWith(".")) {
                runCatching { cookieStore.setCookie(url, name, value, domain = ".$host") }
            }
        }
        runCatching { cookieStore.flush() }
    }

    /** 每个 URL 的 cookie 串首对放一个哨兵，替真实会话 cookie 挡 github-mcp `cookieHeader` 的键前缀（见 [login]）。 */
    private fun withSentinel(raw: String): String {
        if (raw.isBlank()) return ""
        if (raw.trimStart().startsWith(SENTINEL_COOKIE)) return raw
        return "$SENTINEL_COOKIE; $raw"
    }

    private suspend fun evalValue(session: Session, js: String, timeoutMs: Long): String? =
        withContext(Dispatchers.IO) { session.browser.evalJson(js, timeoutMs) }

    private suspend fun readBlobFromPage(session: Session, varName: String): ByteArray {
        // 先量长度，避免把整串 base64 拉回宿主内存两次。
        val lenRaw = evalValue(
            session,
            "(function(){ try { return String((window.$varName || '').length); } catch(e) { return '0'; } })()",
            DEFAULT_EVAL_TIMEOUT_MS
        )
        val total = unwrapJsonString(lenRaw)?.toIntOrNull() ?: 0
        if (total <= 0) throw PluginWebViewException("WEBVIEW_MEDIA", "page variable is empty: $varName")
        if (total > MAX_BLOB_CHARS) {
            throw PluginWebViewException("WEBVIEW_MEDIA", "page variable too large: $total")
        }

        // 分次把 base64 拉回宿主内存（这一步不过插件管道，走浏览器求值回调）。
        val sb = StringBuilder(total)
        var offset = 0
        val step = 1024 * 1024
        while (offset < total) {
            val part = evalValue(
                session,
                "(function(){ return String((window.$varName || '').slice($offset, ${offset + step})); })()",
                DEFAULT_EVAL_TIMEOUT_MS
            ) ?: ""
            val clean = unwrapJsonString(part) ?: part
            if (clean.isEmpty()) break
            sb.append(clean)
            offset += step
        }

        val bytes = decodeBase64(sb.toString())

        // 落盘后清掉页面里的副本，十几 MB 挂 window 上很占内存。
        runCatching {
            evalValue(session, "(function(){ window.$varName = null; return 'ok'; })()", 5_000)
        }
        return bytes
    }

    private fun decodeBase64(raw: String): ByteArray {
        val trimmed = raw.trim()
        // 形如 data:...;base64,<payload> 时只留 payload；无逗号时取原串。
        val payload = if (trimmed.startsWith("data:") && trimmed.contains(",")) {
            trimmed.substringAfter(",")
        } else {
            trimmed
        }
        return try {
            Base64.getMimeDecoder().decode(payload)
        } catch (e: IllegalArgumentException) {
            throw PluginWebViewException("WEBVIEW_MEDIA", "base64 decode failed: ${e.message}")
        }
    }

    private suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", DESKTOP_UA)
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw PluginWebViewException("WEBVIEW_MEDIA", "download failed: HTTP $code")
            }
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            conn.inputStream.use { input ->
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) {
                        throw PluginWebViewException("WEBVIEW_MEDIA", "download too large")
                    }
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    /**
     * 剥掉求值回传值的引号与转义（与 Android 的 unwrapJsonString 同款）。
     * 求值回的是一个 JSON 字面量的字符串形式：JS 侧 `JSON.stringify(true)` 得到 `true`，
     * 再被包成 `"true"`。不剥就永远等不到命中。
     */
    private fun unwrapJsonString(raw: String?): String? {
        val s = raw?.trim() ?: return null
        if (s.length < 2 || !s.startsWith("\"") || !s.endsWith("\"")) return s
        return runCatching {
            Json.parseToJsonElement(s).jsonPrimitive.content
        }.getOrDefault(s.substring(1, s.length - 1))
    }

    private fun JsonElement?.asObjectOrThrow(): JsonObject =
        this?.jsonObject ?: throw PluginWebViewException("PARAM_INVALID", "params must be an object")

    /** 取参数里的必填字符串。显式返回非空，避免调用点被推断成 String?。 */
    private fun asStringOrThrow(element: JsonElement?, name: String): String {
        val s = (element as? JsonPrimitive)?.contentOrNull
        if (s.isNullOrBlank()) throw PluginWebViewException("PARAM_INVALID", "$name is required")
        return s
    }

    private companion object {
        const val MAX_SESSIONS_PER_PLUGIN = 3
        const val DEFAULT_EVAL_TIMEOUT_MS = 30_000L
        const val DEFAULT_WAIT_TIMEOUT_MS = 60_000L
        const val DEFAULT_POLL_INTERVAL_MS = 500L
        const val EVAL_STEP_TIMEOUT_MS = 15_000L

        /** 登录默认等用户 10 分钟；上限 30 分钟。 */
        const val DEFAULT_LOGIN_TIMEOUT_MS = 600_000L
        const val MAX_LOGIN_TIMEOUT_MS = 1_800_000L

        /** 页面 base64 变量的字符数上限（约 192MB 解码后）；muse 视频 base64 一般几 MB 到几十 MB。 */
        const val MAX_BLOB_CHARS = 256 * 1024 * 1024
        const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
        const val DEFAULT_BLOB_VAR = "__museBlob"
        val ALLOWED_SCHEMES = setOf("http", "https")
        val BLOB_VAR_NAME = Regex("^[A-Za-z_$][A-Za-z0-9_$]*$")

        /** message 里带 LOGIN_CANCELED：插件（github-mcp / pojia-gateway）按该字样判断用户取消。 */
        const val LOGIN_CANCELED_MESSAGE = "LOGIN_CANCELED: 用户取消了登录"

        /**
         * login 返回的每串 cookie 首对的哨兵。github-mcp 的 `cookieHeader` 会把 map 的键
         *（URL）当成 cookie 名拼成 `key=value`，吃掉每串的第一个 `k=v` 对；哨兵替真实
         * 会话 cookie 挡这一刀，其它消费者把它当成无害的未知 cookie 忽略。
         */
        const val SENTINEL_COOKIE = "_vanta_host=1"

        /** 不带 `wv` 标记的桌面 UA：系统默认 UA 里的 wv 会让站点识别为内嵌 WebView。 */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"
    }
}
