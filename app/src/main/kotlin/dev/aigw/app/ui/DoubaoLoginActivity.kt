package dev.aigw.app.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.JsonObject
import dev.aigw.app.AiGatewayApp
import dev.aigw.provider.ProviderAccount
import dev.aigw.util.Log

/**
 * 豆包一键登录。
 *
 * 用户只需要在页面里正常登录，登录态就位后自动从 WebView 里取三样东西：
 * - Cookie（CookieManager 全量，含 HttpOnly 的 sessionid）
 * - device_id（localStorage 的 samantha_web_web_id.web_id）
 * - web_id（localStorage 的 __tea_cache_tokens_497858.web_id）
 *
 * 三者必须同源，所以不能让用户从别处粘贴 Cookie 再配另一台设备的值。
 * 提取完成后直接写入账号池并关闭页面。
 */
class DoubaoLoginActivity : Activity() {

    private var web: WebView? = null
    private var hint: TextView? = null
    private var polling = true
    private var saved = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        hint = TextView(this).apply {
            text = "登录后自动保存账号；已清除上次的登录状态，可登录其他账号"
            textSize = 13f
            setTextColor(Color.parseColor("#444444"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.parseColor("#F5F5F7"))
        }
        root.addView(hint, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))

        val view = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                // 豆包登录页会检测 UA，不要伪造成别的浏览器
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) {
                    Log.info("豆包登录页：$url")
                }

                override fun onReceivedError(
                    v: WebView,
                    request: android.webkit.WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    // 只关心主文档：子资源失败不影响登录
                    if (request.isForMainFrame) {
                        hint?.text = "页面加载失败：${error.description}（检查网络或代理）"
                    }
                }
            }
            // 上次登录的 Cookie 会留在 WebView 里，直接打开页面就是已登录状态，
            // 加第二个账号时会被顶回旧账号。这里先清干净再进去。
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            clearCache(true)
            clearHistory()
            clearFormData()
            loadUrl("https://www.doubao.com/chat/")
        }
        root.addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f,
        ))
        setContentView(root)
        web = view

        // 轮询等登录态：sessionid 出现就说明登录成功
        Thread {
            repeat(300) {
                if (!polling || saved) return@Thread
                Thread.sleep(2000)
                if (!polling || saved) return@Thread
                val cookie = CookieManager.getInstance().getCookie("https://www.doubao.com")
                if (cookie != null && cookie.contains("sessionid=")) {
                    runOnUiThread { extractAndSave(cookie) }
                    return@Thread
                }
            }
        }.start()
    }

    /** 从页面 localStorage 取 device_id / web_id，与 Cookie 同源。 */
    private fun extractAndSave(cookie: String) {
        val view = web ?: return
        hint?.text = "已登录，正在保存…"
        view.evaluateJavascript(
            "(function(){try{" +
                "var d=JSON.parse(localStorage.getItem('samantha_web_web_id')||'{}');" +
                "var t=JSON.parse(localStorage.getItem('__tea_cache_tokens_497858')||'{}');" +
                "return JSON.stringify({device_id:(d.web_id||''),web_id:(t.web_id||'')});" +
                "}catch(e){return '{}';}})()",
        ) { result ->
            val raw = result?.trim()?.removeSurrounding("\"")?.replace("\\\"", "\"") ?: "{}"
            val obj = runCatching {
                com.google.gson.JsonParser.parseString(raw).asJsonObject
            }.getOrNull() ?: JsonObject()
            val deviceId = obj.get("device_id")?.asString.orEmpty()
            val webId = obj.get("web_id")?.asString.orEmpty()

            if (deviceId.isBlank() || webId.isBlank()) {
                hint?.text = "登录已成功，但页面还没写入设备标识，请稍等或在页面里点一下再试"
                // 页面刚登录时 localStorage 可能还没落盘，隔几秒重试一次
                view.postDelayed({ if (!saved) extractAndSave(cookie) }, 2500)
                return@evaluateJavascript
            }
            persist(cookie, deviceId, webId)
        }
    }

    private fun persist(cookie: String, deviceId: String, webId: String) {
        if (saved) return
        saved = true
        val app = application as AiGatewayApp
        val secret = JsonObject().apply {
            addProperty("cookie", cookie)
            addProperty("device_id", deviceId)
            addProperty("web_id", webId)
            addProperty("local_app_id", "582478")
        }.toString()
        // 同一个 device_id 视为同一账号，重复登录时覆盖而不是堆叠
        val uid = deviceId.hashCode().toUInt().toString(16)
        // 用上游的 screen_name 做显示名；取不到就退回 device_id 尾号，不影响保存
        val name = runCatching { fetchScreenName(cookie) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: ("豆包 " + deviceId.takeLast(6))
        runCatching {
            app.engine.pool.add(
                ProviderAccount(
                    providerId = "doubao",
                    uid = uid,
                    displayName = name,
                    apiKey = "",
                    baseUrl = "",
                    secret = secret,
                ),
            )
        }.onFailure {
            Log.warn("保存豆包账号失败：${it.message}")
        }
        hint?.text = "账号已保存"
        finish()
    }

    /** 从 `/passport/account/info` 取真实昵称（豆包默认名形如「用户6324…」）。 */
    private fun fetchScreenName(cookie: String): String? {
        val conn = (java.net.URL("https://www.doubao.com/passport/account/info")
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
            val obj = runCatching {
                com.google.gson.JsonParser.parseString(text).asJsonObject
            }.getOrNull() ?: return null
            obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("screen_name")?.takeIf { it.isJsonPrimitive }?.asString
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    override fun onDestroy() {
        polling = false
        web?.let { w ->
            runCatching { w.stopLoading() }
            runCatching { w.destroy() }
        }
        web = null
        super.onDestroy()
    }
}
