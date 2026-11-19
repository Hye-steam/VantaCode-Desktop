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
import android.widget.LinearLayout
import android.widget.TextView
import com.google.gson.JsonObject
import dev.aigw.app.AiGatewayApp
import dev.aigw.provider.ProviderAccount
import dev.aigw.util.Log

/**
 * 即梦一键登录。
 *
 * 即梦的鉴权是 Cookie（sessionid 为主，ttwid / s_v_web_id 同源带上更稳），
 * 而 `/mweb/v1/` 的签名是固定算法，不依赖浏览器运行时，所以这里只要把登录后的
 * 完整 Cookie 抓下来就够，不用像豆包那样再取 localStorage 里的设备标识。
 */
class JimengLoginActivity : Activity() {

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
            text = "登录即梦后会自动保存账号"
            textSize = 13f
            setTextColor(Color.parseColor("#444444"))
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 36, 48, 36)
            setBackgroundColor(Color.parseColor("#F5F5F7"))
        }
        root.addView(
            hint,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        val view = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) {
                    Log.info("即梦登录页：$url")
                }

                override fun onReceivedError(
                    v: WebView,
                    request: android.webkit.WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        hint?.text = "页面加载失败：${error.description}（检查网络或代理）"
                    }
                }
            }
            loadUrl("https://jimeng.jianying.com/ai-tool/home")
        }
        root.addView(
            view,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        setContentView(root)
        web = view

        Thread {
            repeat(300) {
                if (!polling || saved) return@Thread
                Thread.sleep(2000)
                if (!polling || saved) return@Thread
                val cookie = CookieManager.getInstance().getCookie("https://jimeng.jianying.com")
                if (cookie != null && cookie.contains("sessionid=")) {
                    runOnUiThread { persist(cookie) }
                    return@Thread
                }
            }
        }.start()
    }

    private fun persist(cookie: String) {
        if (saved) return
        saved = true
        val app = application as AiGatewayApp
        val sid = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sessionid=") }
            ?.substringAfter("sessionid=")
            ?.trim()
            .orEmpty()
        if (sid.isEmpty()) {
            saved = false
            hint?.text = "抓到 Cookie 但没有 sessionid，请确认已登录"
            return
        }
        val secret = JsonObject().apply { addProperty("cookie", cookie) }.toString()
        runCatching {
            app.engine.pool.add(
                ProviderAccount(
                    providerId = "jimeng",
                    uid = sid.take(16),
                    displayName = "即梦 " + sid.take(6),
                    secret = secret,
                ),
            )
        }.onFailure {
            Log.warn("保存即梦账号失败：${it.message}")
        }
        hint?.text = "账号已保存"
        finish()
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
