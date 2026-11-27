package dev.aigw.desktop.cef

import com.google.gson.Gson
import dev.aigw.provider.WebTransport
import dev.aigw.provider.WebTransportResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 隐藏浏览器页面通道基类（DeepSeek / ChatGLM）。
 *
 * 由 Android 的 DeepSeekWebTransport / ChatglmWebTransport 移植：
 * 常驻一个隐藏 JCEF 浏览器（OSR），页面内 fetch 发请求以继承 Cookie/WAF 签名，
 * 事件经桥（DSBridge / ChatglmBridge → CefMessageRouter）回流到队列。
 * Android 的 Handler/Looper 主线程投递改为直接调用（JCEF 方法内部会切到 CEF 线程）。
 */
abstract class JcefPageTransport(
    private val baseUrl: String,
    private val bridgeName: String,
    private val fetchFn: String,
    private val jsHelper: String,
    private val readyTimeoutMs: Long,
    private val timeoutMs: Long,
) : WebTransport {

    protected val gson = Gson()
    private val queues = ConcurrentHashMap<String, LinkedBlockingQueue<Event>>()

    @Volatile
    private var page: JcefBrowser? = null

    private val lock = Any()

    fun warmUp() {
        Thread { runCatching { ensureReady() } }.apply { isDaemon = true }.start()
    }

    private fun ensureReady(): JcefBrowser? {
        page?.let { return it }
        synchronized(lock) {
            page?.let { return it }
            val browser = try {
                JcefBrowser(
                    startUrl = baseUrl,
                    requestHandler = null,
                    afterLoad = { b ->
                        // 每次加载完成都重注：页面跳转/刷新会丢掉注入的函数
                        b.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                        b.evalVoid(DesktopWebMode.viewportFixJs)
                        b.evalVoid(jsHelper)
                    },
                )
            } catch (e: Throwable) {
                return null
            }
            browser.onBridge = { bridge, method, args ->
                if (bridge == bridgeName) onBridgeEvent(method, args)
            }
            if (!browser.awaitReady(readyTimeoutMs)) {
                browser.close()
                return null
            }
            // afterLoad 已注入 helper；确认函数存在
            val ok = browser.evalJson("(typeof window.$fetchFn === 'function')", 5_000) == "true"
            if (!ok) {
                browser.close()
                return null
            }
            page = browser
            return browser
        }
    }

    private fun onBridgeEvent(method: String, args: List<String>) {
        val id = args.getOrNull(0) ?: return
        val queue = queues[id] ?: return
        when (method) {
            "onStatus" -> queue.offer(Event.Status(args.getOrNull(1)?.toIntOrNull() ?: 0))
            "onChunk" -> queue.offer(Event.Chunk(args.getOrNull(1).orEmpty()))
            "onDone" -> queue.offer(Event.Done)
            "onError" -> queue.offer(Event.Error(args.getOrNull(1).orEmpty()))
        }
    }

    override fun post(
        path: String,
        headers: Map<String, String>,
        body: String,
        stream: Boolean,
        onLine: ((String) -> Boolean)?,
    ): WebTransportResult {
        val browser = ensureReady() ?: return WebTransportResult(0, "", "JCEF 页面通道未就绪")
        val cleanHeaders = headers.filterKeys { it.lowercase() != "cookie" && it.lowercase() != "content-length" }
        val id = UUID.randomUUID().toString()
        val queue = LinkedBlockingQueue<Event>()
        queues[id] = queue
        try {
            val js = "window.$fetchFn(" +
                gson.toJson(id) + ", " + gson.toJson(path) + ", " +
                gson.toJson(gson.toJson(cleanHeaders)) + ", " + gson.toJson(body) + ", " + stream + ");"
            browser.evalVoid(js)
            val buffer = StringBuilder()
            val deadline = System.currentTimeMillis() + timeoutMs
            var status = 0
            var stopped = false
            while (System.currentTimeMillis() < deadline && !stopped) {
                val remain = deadline - System.currentTimeMillis()
                val event = queue.poll(remain, TimeUnit.MILLISECONDS) ?: continue
                when (event) {
                    is Event.Status -> status = event.code
                    is Event.Chunk -> {
                        buffer.append(event.text)
                        if (stream && onLine != null) {
                            var index = buffer.indexOf('\n')
                            while (index >= 0) {
                                val line = buffer.substring(0, index).trimEnd('\r')
                                buffer.delete(0, index + 1)
                                if (!onLine(line)) {
                                    stopped = true
                                    break
                                }
                                index = buffer.indexOf('\n')
                            }
                        }
                    }
                    is Event.Done -> break
                    is Event.Error -> return WebTransportResult(0, "", event.message)
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                return WebTransportResult(0, "", "页面通道超时")
            }
            return WebTransportResult(status, buffer.toString())
        } finally {
            queues.remove(id)
        }
    }

    private sealed class Event {
        class Status(val code: Int) : Event()
        class Chunk(val text: String) : Event()
        data object Done : Event()
        class Error(val message: String) : Event()
    }
}

/** DeepSeek：原生 HTTP 403 时的回退通道。页面内 fetch：token 等请求头由调用方传入，cookie 由浏览器自动携带。 */
class DeepSeekWebTransport : JcefPageTransport(
    baseUrl = BASE,
    bridgeName = "ds",
    fetchFn = "__dsFetch",
    jsHelper = JS_HELPER,
    readyTimeoutMs = READY_TIMEOUT_MS,
    timeoutMs = TIMEOUT_MS,
) {
    companion object {
        const val BASE = "https://chat.deepseek.com"
        const val READY_TIMEOUT_MS = 10_000L
        const val TIMEOUT_MS = 120_000L

        const val JS_HELPER = """
(function(){
  if (window.__dsFetch) return;
  window.__dsFetch = async function(id, path, headersJson, body, stream){
    try {
      var headers = JSON.parse(headersJson);
      delete headers['cookie'];
      delete headers['content-length'];
      var resp = await fetch(path, {method:'POST', headers: headers, body: body, credentials:'include'});
      DSBridge.onStatus(id, resp.status);
      if (!stream) {
        var text = await resp.text();
        DSBridge.onChunk(id, text);
        DSBridge.onDone(id);
        return;
      }
      var reader = resp.body.getReader();
      var dec = new TextDecoder();
      while (true) {
        var r = await reader.read();
        if (r.done) break;
        DSBridge.onChunk(id, dec.decode(r.value, {stream:true}));
      }
      DSBridge.onDone(id);
    } catch(e) {
      DSBridge.onError(id, String(e));
    }
  };
})();
"""
    }
}

/** 智谱清言：请求走页面通道（WAF 签名由页面环境注入），是主通道而非回退。 */
class ChatglmWebTransport : JcefPageTransport(
    baseUrl = BASE,
    bridgeName = "chatglm",
    fetchFn = "__chatglmFetch",
    jsHelper = JS_HELPER,
    readyTimeoutMs = READY_TIMEOUT_MS,
    timeoutMs = TIMEOUT_MS,
) {
    companion object {
        const val BASE = "https://chatglm.cn"
        const val READY_TIMEOUT_MS = 15_000L
        const val TIMEOUT_MS = 300_000L

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
