package com.vantacode.feature.plugin.runtime

import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 插件 WebView 服务单测：参数校验、错误码、会话归属、登录超时/取消、saveBlob 落盘，
 * 全部走假浏览器/假 cookie/假登录窗，不建 JFrame、不碰 JCEF。
 * 真实登录流程（窗口实际弹出、muse 取数）只能在 Windows 真机上验证。
 */
class PluginWebViewServiceTest {

    private class FakeBrowser(
        startUrl: String = "about:blank"
    ) : PluginWebViewBrowserHandle {
        override var currentUrl: String = startUrl
        override var ready: Boolean = true
        val loadedUrls = mutableListOf<String>()
        val evaledScripts = mutableListOf<String>()
        var evalHandler: ((String) -> String?)? = null
        var closed = false
        override var onBridgeMessage: ((String, List<String>) -> Unit)? = null

        override fun load(url: String) {
            loadedUrls += url
            currentUrl = url
        }

        override fun evalJson(js: String, timeoutMs: Long): String? {
            evaledScripts += js
            return evalHandler?.invoke(js)
        }

        override fun evalVoid(js: String) {
            evaledScripts += js
        }

        override fun close() {
            closed = true
        }
    }

    private class FakeBrowserFactory : PluginWebViewBrowserFactory {
        val created = mutableListOf<FakeBrowser>()
        var next: FakeBrowser? = null
        override fun create(startUrl: String, userAgent: String?): PluginWebViewBrowserHandle {
            val b = next ?: FakeBrowser(startUrl)
            next = null
            b.currentUrl = startUrl
            created += b
            return b
        }
    }

    private class FakeCookieStore : PluginWebViewCookieStore {
        val cookiesByUrl = mutableMapOf<String, String>()
        val setCalls = mutableListOf<String>()
        var flushCount = 0
        override fun getCookies(url: String, timeoutMs: Long): String = cookiesByUrl[url].orEmpty()
        override fun setCookie(url: String, name: String, value: String, domain: String?) {
            setCalls += "$url|$name=$value|${domain ?: ""}"
        }

        override fun flush() {
            flushCount++
        }

        override fun clearAllCookies() {
            cookiesByUrl.clear()
        }
    }

    private class FakeLoginWindow(
        override val browser: PluginWebViewBrowserHandle,
        var outcome: PluginWebViewLoginOutcome = PluginWebViewLoginOutcome.Confirmed("https://example.com/done")
    ) : PluginWebViewLoginWindowHandle {
        var closed = false
        override suspend fun awaitOutcome(timeoutMs: Long): PluginWebViewLoginOutcome = outcome
        override fun close() {
            closed = true
        }
    }

    private class FakeLoginWindowFactory : PluginWebViewLoginWindowFactory {
        val requests = mutableListOf<PluginWebViewLoginRequest>()
        var nextOutcome: PluginWebViewLoginOutcome =
            PluginWebViewLoginOutcome.Confirmed("https://example.com/done")
        val windows = mutableListOf<FakeLoginWindow>()
        override fun create(request: PluginWebViewLoginRequest): PluginWebViewLoginWindowHandle {
            requests += request
            val w = FakeLoginWindow(FakeBrowser(request.url), nextOutcome)
            windows += w
            return w
        }
    }

    private lateinit var browserFactory: FakeBrowserFactory
    private lateinit var cookieStore: FakeCookieStore
    private lateinit var loginFactory: FakeLoginWindowFactory
    private lateinit var service: PluginWebViewService
    private lateinit var tmpDir: File

    @Before
    fun setUp() {
        browserFactory = FakeBrowserFactory()
        cookieStore = FakeCookieStore()
        loginFactory = FakeLoginWindowFactory()
        service = PluginWebViewService(browserFactory, cookieStore, loginFactory)
        tmpDir = Files.createTempDirectory("webview-test").toFile()
    }

    @After
    fun tearDown() {
        runCatching { tmpDir.deleteRecursively() }
    }

    private fun params(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
        pairs.forEach { (k, v) ->
            when (v) {
                is String -> put(k, v)
                is Boolean -> put(k, v)
                is Number -> put(k, v)
                else -> put(k, v.toString())
            }
        }
    }

    private fun sessionOf(result: JsonObject): String =
        result["session"]?.jsonPrimitive?.contentOrNull.orEmpty()

    private suspend fun expectError(code: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: PluginWebViewException) {
            assertEquals(code, e.code)
            return
        }
        fail("expected PluginWebViewException($code)")
    }

    // ---- open ----

    @Test
    fun openRejectsNonHttpUrl() = runTest {
        expectError("WEBVIEW_URL_REJECTED") {
            service.open("p.test", params("url" to "ftp://example.com/x"))
        }
    }

    @Test
    fun openRequiresUrl() = runTest {
        expectError("PARAM_INVALID") { service.open("p.test", JsonObject(emptyMap())) }
    }

    @Test
    fun openInjectsCookiesBeforeLoad() = runTest {
        val browser = FakeBrowser()
        browserFactory.next = browser
        val result = service.open(
            "p.test",
            params("url" to "https://example.com/", "cookies" to "a=1; b=2")
        )
        assertTrue(sessionOf(result).isNotEmpty())
        // 先建 about:blank、注 cookie、再 load（首屏请求不能是登出态）。
        assertEquals(listOf("https://example.com/"), browser.loadedUrls)
        assertTrue(browser.currentUrl == "https://example.com/")
        assertTrue(cookieStore.setCalls.any { it.startsWith("https://example.com/|a=1|") })
        assertTrue(cookieStore.setCalls.any { it.startsWith("https://example.com/|b=2|") })
        // 裸域 + 带点子域两种写法都注了（Android / VantaGate 同款）。
        assertTrue(cookieStore.setCalls.any { it.endsWith("|.example.com") })
        assertTrue(cookieStore.flushCount >= 1)
    }

    @Test
    fun openEnforcesSessionLimit() = runTest {
        repeat(3) { service.open("p.test", params("url" to "https://example.com/")) }
        expectError("WEBVIEW_LIMIT") {
            service.open("p.test", params("url" to "https://example.com/"))
        }
        // 其它插件不受影响。
        service.open("p.other", params("url" to "https://example.com/"))
    }

    @Test
    fun openInteractiveStartsOutcomeRelay() = runTest {
        val result = service.open(
            "p.test",
            params("url" to "https://example.com/login", "interactive" to true)
        )
        val session = sessionOf(result)
        assertTrue(session.isNotEmpty())
        assertEquals(1, loginFactory.requests.size)
        assertEquals("https://example.com/login", loginFactory.requests[0].url)
        // 假窗口默认 Confirmed：awaitMessage 应收到 confirm。
        val msg = service.awaitMessage("p.test", params("session" to session, "timeoutMs" to 5_000L))
        assertEquals(true, msg["ok"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        assertTrue((msg["value"]?.jsonPrimitive?.contentOrNull.orEmpty()).contains("confirm"))
    }

    // ---- getCookies / setCookies ----

    @Test
    fun getCookiesReturnsRawString() = runTest {
        cookieStore.cookiesByUrl["https://muse.ai"] = "hatch_sess=AAA; other=1"
        val result = service.getCookies("p.test", params("url" to "https://muse.ai"))
        assertEquals("hatch_sess=AAA; other=1", result["cookies"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun getCookiesRejectsNonHttp() = runTest {
        expectError("PARAM_INVALID") {
            service.getCookies("p.test", params("url" to "javascript:alert(1)"))
        }
    }

    @Test
    fun getCookiesValidatesSessionOwnership() = runTest {
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        expectError("WEBVIEW_SESSION") {
            service.getCookies("p.other", params("url" to "https://example.com/", "session" to session))
        }
        expectError("WEBVIEW_SESSION") {
            service.getCookies("p.test", params("url" to "https://example.com/", "session" to "nope:1"))
        }
        // 归属正确时放行。
        service.getCookies("p.test", params("url" to "https://example.com/", "session" to session))
    }

    @Test
    fun setCookiesWritesPairs() = runTest {
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        val out = service.setCookies(
            "p.test",
            params("session" to session, "cookies" to "x=1; y=2", "url" to "https://example.com/")
        )
        assertEquals(true, out["ok"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        assertTrue(cookieStore.setCalls.any { it.contains("|x=1|") })
        assertTrue(cookieStore.setCalls.any { it.contains("|y=2|") })
    }

    // ---- eval / waitFor / awaitMessage ----

    @Test
    fun evalReturnsValueAndTimesOutOnNull() = runTest {
        val browser = FakeBrowser()
        browser.evalHandler = { "\"hello\"" }
        browserFactory.next = browser
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        val out = service.eval("p.test", params("session" to session, "js" to "1+1"))
        assertEquals("\"hello\"", out["value"]?.jsonPrimitive?.contentOrNull)
        assertTrue(browser.evaledScripts.any { it.contains("1+1") })

        browser.evalHandler = { null }
        expectError("WEBVIEW_TIMEOUT") {
            service.eval("p.test", params("session" to session, "js" to "1+1"))
        }
    }

    @Test
    fun waitForPollsUntilTruthy() = runTest {
        val browser = FakeBrowser()
        var calls = 0
        browser.evalHandler = {
            calls++
            // 前两次返回 "false"，第三次返回 "true"（外层 JSON 编码后的样子）。
            if (calls < 3) "\"false\"" else "\"true\""
        }
        browserFactory.next = browser
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        val out = service.waitFor(
            "p.test",
            params("session" to session, "expr" to "document.readyState==='complete'", "intervalMs" to 1L)
        )
        assertEquals("true", out["value"]?.jsonPrimitive?.contentOrNull)
        assertTrue(calls >= 3)
    }

    @Test
    fun waitForTimesOut() = runTest {
        val browser = FakeBrowser()
        browser.evalHandler = { "\"false\"" }
        browserFactory.next = browser
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        expectError("WEBVIEW_TIMEOUT") {
            service.waitFor(
                "p.test",
                params("session" to session, "expr" to "false", "timeoutMs" to 50L, "intervalMs" to 10L)
            )
        }
    }

    @Test
    fun awaitMessageMapsFailToErrorObject() = runTest {
        val browser = FakeBrowser()
        browserFactory.next = browser
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        browser.onBridgeMessage?.invoke("onFail", listOf("boom"))
        val out = service.awaitMessage("p.test", params("session" to session, "timeoutMs" to 5_000L))
        assertEquals(false, out["ok"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        assertEquals("boom", out["error"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun awaitMessageTimesOut() = runTest {
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        expectError("WEBVIEW_TIMEOUT") {
            service.awaitMessage("p.test", params("session" to session, "timeoutMs" to 50L))
        }
    }

    // ---- saveBlob ----

    @Test
    fun saveBlobFromPageVarChunked() = runTest {
        val payload = Base64.getEncoder().encodeToString("hello-video-bytes".toByteArray())
        val browser = FakeBrowser()
        browser.evalHandler = { js ->
            when {
                js.contains(".length") -> "\"${payload.length}\""
                js.contains(".slice(") -> {
                    // 切片起止在 JS 里：直接按整串返回（单块就够）。
                    "\"$payload\""
                }
                else -> "\"ok\""
            }
        }
        browserFactory.next = browser
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        val out = service.saveBlob(
            "p.test",
            params("session" to session, "dir" to tmpDir.absolutePath, "name" to "v.mp4")
        )
        val file = File(out["path"]?.jsonPrimitive?.contentOrNull.orEmpty())
        assertTrue(file.exists())
        assertEquals("hello-video-bytes", file.readText())
        assertEquals(17, out["size"]?.jsonPrimitive?.contentOrNull?.toInt())
        // 落盘后清掉页面里的副本。
        assertTrue(browser.evaledScripts.any { it.contains("__museBlob = null") })
    }

    @Test
    fun saveBlobRejectsEmptyVar() = runTest {
        val browser = FakeBrowser()
        browser.evalHandler = { "\"0\"" }
        browserFactory.next = browser
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        expectError("WEBVIEW_MEDIA") {
            service.saveBlob("p.test", params("session" to session, "dir" to tmpDir.absolutePath))
        }
    }

    @Test
    fun saveBlobRejectsBadVarName() = runTest {
        val result = service.open("p.test", params("url" to "https://example.com/"))
        val session = sessionOf(result)
        expectError("PARAM_INVALID") {
            service.saveBlob(
                "p.test",
                params("session" to session, "var" to "x; evil()", "dir" to tmpDir.absolutePath)
            )
        }
    }

    @Test
    fun saveBlobFromStandaloneData() = runTest {
        val data = Base64.getEncoder().encodeToString("direct-bytes".toByteArray())
        val out = service.saveBlob(
            "p.test",
            params("data" to "data:video/mp4;base64,$data", "dir" to tmpDir.absolutePath, "name" to "d.bin")
        )
        val file = File(out["path"]?.jsonPrimitive?.contentOrNull.orEmpty())
        assertEquals("direct-bytes", file.readText())
        assertEquals(12, out["size"]?.jsonPrimitive?.contentOrNull?.toInt())
    }

    @Test
    fun saveBlobRejectsPathTraversalName() = runTest {
        expectError("PARAM_INVALID") {
            service.saveBlob(
                "p.test",
                params("data" to Base64.getEncoder().encodeToString("x".toByteArray()), "dir" to tmpDir.absolutePath, "name" to "../evil.bin")
            )
        }
    }

    @Test
    fun saveBlobRequiresSessionDataOrUrl() = runTest {
        expectError("WEBVIEW_SESSION") {
            service.saveBlob("p.test", params("dir" to tmpDir.absolutePath))
        }
    }

    // ---- login ----

    @Test
    fun loginReturnsCookiesByUrlAndFinalUrl() = runTest {
        cookieStore.cookiesByUrl["https://github.com"] = "_gh_sess=AAA; logged_in=yes"
        cookieStore.cookiesByUrl["https://api.github.com"] = "api_tok=BBB"
        loginFactory.nextOutcome =
            PluginWebViewLoginOutcome.Confirmed("https://github.com/settings/tokens")
        val result = service.login(
            "p.test",
            buildJsonObject {
                put("url", "https://github.com/login")
                put("title", "登录 GitHub")
                put("finishText", "已登录")
                put("cookieUrls", kotlinx.serialization.json.buildJsonArray {
                    add(JsonPrimitive("https://github.com"))
                    add(JsonPrimitive("https://api.github.com"))
                })
            }
        )
        val cookies = result["cookies"]?.jsonObject ?: fail("no cookies")
        // 按 URL 分键；每串首对是哨兵（替真实会话 cookie 挡 github-mcp cookieHeader 的键前缀）。
        val gh = cookies["https://github.com"]?.jsonPrimitive?.contentOrNull.orEmpty()
        assertTrue(gh.startsWith("_vanta_host=1; "))
        assertTrue(gh.contains("_gh_sess=AAA"))
        assertTrue(gh.contains("logged_in=yes"))
        assertTrue(
            (cookies["https://api.github.com"]?.jsonPrimitive?.contentOrNull.orEmpty())
                .contains("api_tok=BBB")
        )
        assertEquals("https://github.com/settings/tokens", result["finalUrl"]?.jsonPrimitive?.contentOrNull)
        assertTrue((result["cookie"]?.jsonPrimitive?.contentOrNull.orEmpty()).contains("_gh_sess=AAA"))
        // 登录完窗口已关、会话已回收。
        assertTrue(loginFactory.windows.all { it.closed })
        assertEquals(1, loginFactory.requests.size)
        assertEquals("登录 GitHub", loginFactory.requests[0].title)
        assertEquals("已登录", loginFactory.requests[0].finishText)
    }

    @Test
    fun loginDefaultsCookieUrlsToLoginUrl() = runTest {
        cookieStore.cookiesByUrl["https://example.com/login"] = "s=1"
        val result = service.login("p.test", params("url" to "https://example.com/login"))
        val cookies = result["cookies"]?.jsonObject ?: fail("no cookies")
        assertTrue(cookies.containsKey("https://example.com/login"))
        assertTrue(
            (cookies["https://example.com/login"]?.jsonPrimitive?.contentOrNull.orEmpty()).contains("s=1")
        )
    }

    @Test
    fun loginCanceled() = runTest {
        loginFactory.nextOutcome = PluginWebViewLoginOutcome.Canceled
        try {
            service.login("p.test", params("url" to "https://example.com/login"))
            fail("expected LOGIN_CANCELED")
        } catch (e: PluginWebViewException) {
            assertEquals("LOGIN_CANCELED", e.code)
            // message 里带 LOGIN_CANCELED：插件按该字样判断取消。
            assertTrue(e.message.orEmpty().contains("LOGIN_CANCELED"))
        }
        assertTrue(loginFactory.windows.all { it.closed })
    }

    @Test
    fun loginTimesOut() = runTest {
        loginFactory.nextOutcome = PluginWebViewLoginOutcome.TimedOut
        expectError("WEBVIEW_TIMEOUT") {
            service.login("p.test", params("url" to "https://example.com/login", "timeoutMs" to 1_000L))
        }
        assertTrue(loginFactory.windows.all { it.closed })
    }

    @Test
    fun loginRejectsNonHttpUrl() = runTest {
        expectError("WEBVIEW_URL_REJECTED") {
            service.login("p.test", params("url" to "file:///etc/passwd"))
        }
    }

    // ---- close / closeAll ----

    @Test
    fun closeDestroysSessionAndCloseAllIsScopedToPlugin() = runTest {
        val b1 = FakeBrowser()
        browserFactory.next = b1
        val r1 = service.open("p.test", params("url" to "https://example.com/"))
        val s1 = sessionOf(r1)
        val r2 = service.open("p.other", params("url" to "https://example.com/"))
        val s2 = sessionOf(r2)

        val out = service.close("p.test", params("session" to s1))
        assertEquals(true, out["ok"]?.jsonPrimitive?.contentOrNull?.toBoolean())
        assertTrue(b1.closed)
        expectError("WEBVIEW_SESSION") {
            service.eval("p.test", params("session" to s1, "js" to "1"))
        }

        service.closeAll("p.other")
        expectError("WEBVIEW_SESSION") {
            service.eval("p.other", params("session" to s2, "js" to "1"))
        }
    }
}
