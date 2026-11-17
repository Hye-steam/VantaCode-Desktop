package dev.aigw.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import dev.aigw.provider.WebTransport
import dev.aigw.provider.WebTransportResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 用隐藏 WebView 充当智谱清言（chatglm.cn）的请求通道。
 *
 * 智谱网页接口除了 token 还校验 WAF 在浏览器环境里注入的签名头（x-sign 等），
 * 原生请求会被拒（40011）。把请求交给 chatglm.cn 页面内的 fetch 发出，
 * 页面里的安全 SDK 会自动补上这些参数，等价于真实浏览器请求。
 */
class ChatglmWebTransport(private val context: Context) : WebTransport {

    private val main = Handler(Looper.getMainLooper())
    private val queues = ConcurrentHashMap<String, LinkedBlockingQueue<Event>>()
    private val gson = Gson()

    @Volatile
    private var webView: WebView? = null

    @Volatile
    private var ready = false

    /** 预热：在主线程创建 WebView 并加载页面，避免首次对话等待。 */
    fun warmUp() {
        main.post { ensureWebView() }
    }

    override fun post(
        path: String,
        headers: Map<String, String>,
        body: String,
        stream: Boolean,
        onLine: ((String) -> Boolean)?,
    ): WebTransportResult {
        val view = awaitReady() ?: return WebTransportResult(0, "", "WebView 未就绪")
        // 页面内 fetch 会带浏览器 cookie：凭证 cookie 不再手工传
        val cleanHeaders = headers.filterKeys { it.lowercase() != "cookie" && it.lowercase() != "content-length" }

        val id = UUID.randomUUID().toString()
        val queue = LinkedBlockingQueue<Event>()
        queues[id] = queue
        val js = "window.__chatglmFetch(" +
            "${quote(id)}, ${quote(path)}, ${quote(gson.toJson(cleanHeaders))}, ${quote(body)}, $stream)"
        main.post { runCatching { view.evaluateJavascript(js, null) } }

        val buffer = StringBuilder()
        var status = 0
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        try {
            while (true) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0) return WebTransportResult(status, "", "WebView 请求超时")
                val event = queue.poll(remain, TimeUnit.MILLISECONDS) ?: continue
                when (event) {
                    is Event.Status -> status = event.code
                    is Event.Chunk -> {
                        if (!stream) {
                            buffer.append(event.text)
                        } else {
                            buffer.append(event.text)
                            var index = buffer.indexOf("\n")
                            while (index >= 0) {
                                val line = buffer.substring(0, index).trimEnd('\r')
                                buffer.delete(0, index + 1)
                                if (onLine?.invoke(line) == false) {
                                    return WebTransportResult(200, "")
                                }
                                index = buffer.indexOf("\n")
                            }
                        }
                    }
                    is Event.Done -> return WebTransportResult(status, buffer.toString())
                    is Event.Error -> return WebTransportResult(status, "", event.message)
                }
            }
        } finally {
            queues.remove(id)
        }
    }

    private fun awaitReady(): WebView? {
        if (ready) return webView
        main.post { ensureWebView() }
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (ready) return webView
            Thread.sleep(100)
        }
        return null
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun ensureWebView(): WebView? {
        webView?.let { return it }
        return try {
            val view = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = USER_AGENT
                addJavascriptInterface(Bridge(), "ChatglmBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(JS_HELPER, null)
                        ready = true
                    }
                }
                loadUrl(BASE + "/")
            }
            webView = view
            view
        } catch (e: Exception) {
            null
        }
    }

    private fun quote(value: String): String = gson.toJson(value)

    private sealed class Event {
        class Status(val code: Int) : Event()
        class Chunk(val text: String) : Event()
        data object Done : Event()
        class Error(val message: String) : Event()
    }

    inner class Bridge {
        @JavascriptInterface
        fun onStatus(id: String, code: Int) {
            queues[id]?.put(Event.Status(code))
        }

        @JavascriptInterface
        fun onChunk(id: String, text: String) {
            queues[id]?.put(Event.Chunk(text))
        }

        @JavascriptInterface
        fun onDone(id: String) {
            queues[id]?.put(Event.Done)
        }

        @JavascriptInterface
        fun onError(id: String, message: String) {
            queues[id]?.put(Event.Error(message))
        }
    }

    private companion object {
        const val BASE = "https://chatglm.cn"
        const val READY_TIMEOUT_MS = 15_000L
        const val TIMEOUT_MS = 300_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36"

        /** 页面内 fetch：cookie 与签名头由页面环境自动携带。 */
        const val JS_HELPER = """
(function(){
  if (window.__chatglmFetch) return;
  window.__chatglmFetch = async function(id, path, headersJson, body, stream){
    try {
      var headers = JSON.parse(headersJson);
      delete headers['cookie'];
      delete headers['content-length'];
      var resp = await fetch(path, {method:'POST', headers: headers, body: body, credentials:'include'});
      ChatglmBridge.onStatus(id, resp.status);
      var reader = resp.body.getReader();
      var dec = new TextDecoder();
      while (true) {
        var r = await reader.read();
        if (r.done) break;
        ChatglmBridge.onChunk(id, dec.decode(r.value, {stream:true}));
      }
      ChatglmBridge.onDone(id);
    } catch(e) {
      ChatglmBridge.onError(id, String(e));
    }
  };
})();
"""
    }
}
