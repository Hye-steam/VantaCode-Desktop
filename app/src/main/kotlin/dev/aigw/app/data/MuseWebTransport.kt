package dev.aigw.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import com.google.gson.Gson
import dev.aigw.provider.WebTransport
import dev.aigw.provider.WebTransportResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * 用隐藏 WebView 驱动 muse.ai 生成图片/视频。
 *
 * muse.ai 的生成只对页面内 JS 开放：VM 端点是内网地址（公网 NXDOMAIN），
 * `/api/session` 只负责续期与唤醒。因此这里复刻真人操作：
 *
 * 1. 注入登录 cookie，打开 `muse.ai/thread/new`
 * 2. 等页面水合完成（`data-hatch-shell-hydration-state=hydrated`）
 * 3. 用 React 原型 setter 写入 textarea 并派发 input/change
 * 4. 点发送按钮
 * 5. 监听新增的附件容器，把 blob 读成 base64 回传
 *
 * 与智谱通道的区别：那边是「页面内 fetch 拿响应」，这边是「操作 DOM 等产物」，
 * 所以 [post] 的 path 只用来区分任务类型。
 */
class MuseWebTransport(private val context: Context) : WebTransport {

    private val main = Handler(Looper.getMainLooper())
    private val gson = Gson()

    /** 每次生成一个 id，页面回调按 id 投递。 */
    private val queues = ConcurrentHashMap<String, LinkedBlockingQueue<Event>>()

    @Volatile
    private var webView: WebView? = null

    /** 承载 WebView 的窗口管理器，释放时要先把 view 摘下来。 */
    @Volatile
    private var hostWindow: android.view.WindowManager? = null

    @Volatile
    private var ready = false

    /** 登录 cookie（由账号 secret 提供），首次 [post] 时注入。 */
    @Volatile
    private var cookie: String? = null

    fun warmUp() {
        main.post { ensureWebView() }
    }

    override fun prepare(cookies: String): Boolean {
        cookie = cookies
        main.post { ensureWebView()?.let { injectCookies(it, cookies) } }
        return awaitReady() != null
    }

    override fun post(
        path: String,
        headers: Map<String, String>,
        body: String,
        stream: Boolean,
        onLine: ((String) -> Boolean)?,
    ): WebTransportResult {
        val view = awaitReady() ?: return WebTransportResult(0, "", "muse WebView 未就绪")
        val expect = if (path.contains("expect=video")) "video" else "image"
        val prompt = runCatching {
            gson.fromJson(body, Map::class.java)?.get("prompt")?.toString().orEmpty()
        }.getOrDefault("")
        if (prompt.isBlank()) return WebTransportResult(400, "", "缺少 prompt")

        val id = UUID.randomUUID().toString()
        val queue = LinkedBlockingQueue<Event>()
        queues[id] = queue

        try {
            // 先确认当前在新建对话页。旧对话里的产物会让「新产物计数」永不增加，
            // 且历史上下文会影响生成结果。
            ensureFreshThread(view)

            // 再确认 __museGen 真的在页面上下文里。
            // 页面发生导航（客户端重定向/重载）会重建 JS 上下文，注入过的脚本随之消失，
            // 直接 evaluate 会静默失效，请求就一直挂着没有回调。
            var attempts = 0
            var started = false
            val startDeadline = System.currentTimeMillis() + 180_000
            while (!started && System.currentTimeMillis() < startDeadline) {
                val latch = java.util.concurrent.CountDownLatch(1)
                var verdict = ""
                main.post {
                    runCatching {
                        view.evaluateJavascript(jsStart(id, prompt, expect)) { r ->
                            verdict = r.orEmpty().trim('"')
                            latch.countDown()
                        }
                    }.onFailure {
                        verdict = "err:" + it.message
                        latch.countDown()
                    }
                }
                latch.await(20, TimeUnit.SECONDS)
                if (verdict == "ok") {
                    started = true
                } else {
                    attempts++
                    android.util.Log.w("MUSE", "muse 脚本未就绪（$verdict），重注入第 $attempts 次")
                    // 导航后页面要重新加载并水合，等久一点再注入
                    Thread.sleep(3000)
                    main.post {
                        runCatching {
                            view.evaluateJavascript(jsHelper, null)
                        }
                    }
                }
            }
            if (!started) {
                return WebTransportResult(502, "", "页面脚本注入失败（重试 $attempts 次）")
            }

            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (true) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0) return WebTransportResult(504, "", "生成超时")
                val event = queue.poll(remain, TimeUnit.MILLISECONDS) ?: continue
                when (event) {
                    is Event.Ok -> return WebTransportResult(200, event.json)
                    is Event.Fail -> return WebTransportResult(502, "", event.message)
                }
            }
        } finally {
            queues.remove(id)
        }
    }

    /**
     * 确保页面停在新建对话页。
     *
     * 导航会重建 JS 上下文，所以必须在调用脚本之前做，并等新页面加载完。
     */
    private fun ensureFreshThread(view: WebView) {
        val latch = java.util.concurrent.CountDownLatch(1)
        var url = ""
        main.post {
            runCatching {
                view.evaluateJavascript("(location.pathname||'') + '|' + (typeof window.__museGen === 'function')") { r ->
                    url = r.orEmpty().trim('"')
                    latch.countDown()
                }
            }.onFailure { latch.countDown() }
        }
        latch.await(10, TimeUnit.SECONDS)

        val onNew = url.startsWith("/thread/new")
        if (onNew && url.endsWith("|true")) return

        android.util.Log.i("MUSE", "muse 当前页面 $url，重新打开新对话")
        ready = false
        val loaded = java.util.concurrent.CountDownLatch(1)
        main.post { runCatching { view.loadUrl(HOME) } }
        // 等页面就绪（onPageFinished 会把 ready 置位并注入脚本）
        val deadline = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < deadline) {
            if (ready) break
            Thread.sleep(200)
        }
        runCatching { loaded.countDown() }
        Thread.sleep(1500)
    }

    private fun awaitReady(): WebView? {
        if (ready) return webView
        main.post { ensureWebView() }
        val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (ready) return webView
            Thread.sleep(120)
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
                settings.userAgentString = UA_DESKTOP
                // 桌面版页面信息密度更高，且不会被移动端分支降级
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                // muse 的登录态跨子域传递，第三方 cookie 必须放行
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(Bridge(), "MuseBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        // 尽早装错误收集：页面 bundle 抛错通常发生在 onPageFinished 之前
                        v.evaluateJavascript(JS_ERROR_HOOK, null)
                    }

                    override fun onPageFinished(v: WebView, url: String?) {
                        android.util.Log.i("MUSE", "muse 页面就绪：$url")
                        // 页面水合是异步的，注入辅助脚本后交给它自己轮询就绪
                        v.evaluateJavascript(jsHelper) { r ->
                            android.util.Log.i("MUSE", "muse 辅助脚本注入：${r?.take(80)}")
                        }
                        ready = true
                    }

                    override fun onReceivedError(
                        v: WebView,
                        req: android.webkit.WebResourceRequest,
                        err: android.webkit.WebResourceError,
                    ) {
                        if (req.isForMainFrame) {
                            android.util.Log.w("MUSE", "muse 页面加载失败：${req.url} ${err.description}")
                        }
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        val text = msg.message()
                        if (text.contains("error", true) ||
                            msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR
                        ) {
                            android.util.Log.w("MUSE", "muse console: ${text.take(200)}")
                        }
                        return true
                    }
                }
                cookie?.let { injectCookies(this, it) }
                loadUrl(HOME)
            }
            webView = view
            attachToWindow(view)
            android.util.Log.i("MUSE", "muse WebView 已创建")
            view
        } catch (e: Exception) {
            android.util.Log.w("MUSE", "muse WebView 创建失败：${e.message}")
            null
        }
    }

    /**
     * 把 WebView 挂到一个 1x1 的透明悬浮窗上。
     *
     * 未挂窗口的 WebView 不产生合成帧，requestAnimationFrame 永不回调；
     * muse.ai 是 Next.js + Suspense，水合链路由 rAF 驱动，页面会一直停在
     * 服务端渲染的骨架（`<!--$-->` 占位）上。窗口尺寸取 1x1 且透明，用户不可见。
     */
    private fun attachToWindow(view: WebView) {
        runCatching {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            val lp = android.view.WindowManager.LayoutParams(
                400,
                800,
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    android.view.WindowManager.LayoutParams.TYPE_PHONE
                },
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT,
            )
            lp.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            // 移出可视区域：仍参与渲染与 rAF，但用户看不到
            lp.x = -500
            lp.y = -900
            // 不用 alpha=0：部分机型会因此跳过合成，rAF 又不跑了
            view.alpha = 0.01f
            wm.addView(view, lp)
            hostWindow = wm
        }.onFailure {
            android.util.Log.w("MUSE", "muse WebView 挂窗失败：${it.message}")
        }
    }

    private fun injectCookies(view: WebView, raw: String) {
        val cm = CookieManager.getInstance()
        raw.split(';').forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) return@forEach
            val name = pair.substring(0, idx).trim()
            val value = pair.substring(idx + 1).trim()
            if (name.isEmpty()) return@forEach
            // hatch_sess 等在主域；带点前缀的写 .muse.ai
            runCatching { cm.setCookie("https://muse.ai", "$name=$value; path=/; secure") }
            runCatching { cm.setCookie("https://muse.ai", "$name=$value; domain=.muse.ai; path=/; secure") }
        }
        runCatching { cm.flush() }
    }

    private sealed class Event {
        class Ok(val json: String) : Event()
        class Fail(val message: String) : Event()
    }

    inner class Bridge {
        @JavascriptInterface
        fun onOk(id: String, json: String) {
            queues[id]?.put(Event.Ok(json))
        }

        @JavascriptInterface
        fun onFail(id: String, message: String) {
            queues[id]?.put(Event.Fail(message))
        }
    }

    private companion object {
        const val HOME = "https://muse.ai/thread/new"
        const val READY_TIMEOUT_MS = 25_000L
        /** 视频生成慢，给足 20 分钟。 */
        const val TIMEOUT_MS = 1_200_000L
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        /** 不带 `wv` 标记的桌面 UA：系统默认 UA 里的 wv 会让站点识别为内嵌 WebView。 */
        const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        /** 尽早装的错误收集：onPageStarted 注入，能捕到 bundle 早期抛错。 */
        const val JS_ERROR_HOOK = """
(function(){
  if (window.__museErr) return;
  window.__museErr = [];
  window.__museConsole = [];
  window.addEventListener('error', function(e){
    window.__museErr.push(String(e.message || e.error || 'err') + ' @' + (e.filename||'') + ':' + (e.lineno||0));
    if (window.__museErr.length > 10) window.__museErr.shift();
  }, true);
  window.addEventListener('unhandledrejection', function(e){
    window.__museErr.push('reject:' + String(e.reason));
    if (window.__museErr.length > 10) window.__museErr.shift();
  });
})();
"""
    }

    /**
     * 页面内辅助脚本。
     *
     * 只装一次：等水合、填字、点发送、抓产物都在这里。
     * 抓产物用轮询而非 MutationObserver——附件容器出现后内容还要再渲染一次，
     * 轮询更容易拿到已经填好 src 的元素。
     */
    private val jsHelper: String = """
(function(){
  if (window.__museGen) return;
  // 记录页面 JS 错误：水合失败时这是唯一线索
  if (!window.__museErr) {
    window.__museErr = [];
    window.addEventListener('error', function(e){
      window.__museErr.push(String(e.message||e.error||'err'));
      if (window.__museErr.length > 8) window.__museErr.shift();
    });
    window.addEventListener('unhandledrejection', function(e){
      window.__museErr.push('reject:' + String(e.reason));
      if (window.__museErr.length > 8) window.__museErr.shift();
    });
  }
  var bridge = window.MuseBridge;
  function log(){}
  function hydrated(){
    var h = document.querySelector('[data-hatch-shell-hydration-state]');
    if (h && h.getAttribute('data-hatch-shell-hydration-state') !== 'hydrated') return false;
    var b = document.body ? (document.body.innerText||'') : '';
    if (b.indexOf('Connecting...') !== -1) return false;
    return !!document.querySelector('textarea');
  }
  function diag(){
    var h = document.querySelector('[data-hatch-shell-hydration-state]');
    var st = h ? h.getAttribute('data-hatch-shell-hydration-state') : 'none';
    var txt = document.body ? (document.body.innerText||'') : '';
    return '(url=' + location.pathname + ' hydration=' + st +
      ' ta=' + (!!document.querySelector('textarea')) +
      ' len=' + txt.length +
      ' err=' + ((window.__museErr||[]).slice(-2).join(';') || 'none') + ')';
  }
  function setText(ta, text){
    ta.focus();
    var proto = ta.tagName === 'TEXTAREA' ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(ta, text);
    ta.dispatchEvent(new Event('input', {bubbles:true}));
    ta.dispatchEvent(new Event('change', {bubbles:true}));
  }
  function findSend(){
    var bs = [].slice.call(document.querySelectorAll('button'));
    for (var i=0;i<bs.length;i++){
      var b = bs[i];
      var tid = (b.getAttribute('data-testid')||'');
      var al = (b.getAttribute('aria-label')||'');
      if (/send/i.test(tid) || /send|发送/i.test(al)) {
        if (!b.disabled) return b;
      }
    }
    return null;
  }
  function collect(expect){
    // 按 muse2api 的采集规则：排除用户自己发的附件（否则会抓到我们上传/引用的东西），
    // 并用 currentSrc —— blob: 地址常常只在 currentSrc 里可用。
    var out = [];
    var seen = new Set();
    function addEl(el, tid){
      if(!el || seen.has(el)) return;
      seen.add(el);
      if(el.closest('form, [class*=chat-user-bubble], [class*="group/msg"]')) return;
      var v = el.querySelector('video') || (el.tagName === 'VIDEO' ? el : null);
      var img = el.querySelector('img') || (el.tagName === 'IMG' ? el : null);
      var isVid = (tid || '').indexOf('video') !== -1 || !!v;
      var primary = isVid ? (v || img) : (img || v);
      var src = primary ? (primary.currentSrc || primary.src || '') : '';
      if(!src || src.indexOf('avatar') !== -1 || src.indexOf('emoji') !== -1) return;
      if(seen.has(src)) return;
      seen.add(src);
      out.push({src: src, isVideo: isVid});
    }
    var atts = document.querySelectorAll('[data-testid^="hatch-chat-attachment-presentation-"]');
    for (var i=0;i<atts.length;i++){
      addEl(atts[i], atts[i].getAttribute('data-testid'));
    }
    var medias = document.querySelectorAll('div[class*="hatch-agent-bubble-bg"] img, div[class*="hatch-agent-bubble-bg"] video');
    for (var j=0;j<medias.length;j++){
      addEl(medias[j].parentElement || medias[j], 'agent-media');
    }
    // 只要期望类型的产物
    return out.filter(function(x){ return expect === 'video' ? x.isVideo : !x.isVideo; });
  }
  async function toB64(url){
    var r = await fetch(url);
    if (!r.ok) throw new Error('media-http-' + r.status);
    var b = await r.blob();
    var ab = await b.arrayBuffer();
    var bytes = new Uint8Array(ab);
    var s = '';
    for (var i=0;i<bytes.length;i+=65536){
      s += String.fromCharCode.apply(null, bytes.subarray(i, i+65536));
    }
    return {mime: b.type || '', size: b.size, b64: btoa(s)};
  }
  window.__museGen = async function(id, prompt, expect){
    try {
      // 每次生成都回到新对话：旧对话里的产物会让「新产物计数」永远不增加，
      // 而且历史上下文会影响生成结果。导航交给 Kotlin 侧做——
      // 在 JS 里改 location 会立刻销毁上下文，回调传不回来。
      var t0 = Date.now();
      while (Date.now() - t0 < 45000 && !hydrated()){
        await new Promise(function(r){setTimeout(r,500)});
      }
      if (!hydrated()) { bridge.onFail(id, '页面未就绪 ' + diag()); return; }
      var before = collect(expect).length;
      var ta = document.querySelector('textarea');
      if (!ta) { bridge.onFail(id, '找不到输入框 ' + diag()); return; }
      setText(ta, prompt);
      await new Promise(function(r){setTimeout(r,300)});
      var btn = findSend();
      if (!btn) { bridge.onFail(id, '找不到发送按钮 ' + diag()); return; }
      btn.click();
      // 等新产物出现；生成视频可能几分钟。
      // 视频任务先出封面图（presentation-image），<video> 要等渲染完才挂上，
      // 所以这里必须按「真的有 video 元素」判成，不能把封面当成品。
      var deadline = Date.now() + 900000;
      var lastProbe = '';
      while (Date.now() < deadline){
        await new Promise(function(r){setTimeout(r,1500)});
        var now = collect(expect);
        if (now.length > before){
          var url = now[now.length-1].src;
          try {
            var m = await toB64(url);
            bridge.onOk(id, JSON.stringify({ok:true, mime:m.mime, size:m.size, b64:m.b64, url:url}));
            return;
          } catch(e) {
            bridge.onFail(id, '读取产物失败：' + e);
            return;
          }
        }
        if (expect === 'video') lastProbe = diag();
      }
      bridge.onFail(id, '等待产物超时 ' + (expect === 'video' ? lastProbe : diag()));
    } catch(e) {
      bridge.onFail(id, '执行异常：' + e);
    }
  };
})();
"""

    /** 带确认的启动：返回 ok / missing / error，用于判断是否需要补注入。 */
    private fun jsStart(id: String, prompt: String, expect: String): String =
        "(function(){try{" +
            "if (typeof window.__museGen !== 'function') return 'missing';" +
            "window.__museGen(" + gson.toJson(id) + ", " + gson.toJson(prompt) +
            ", " + gson.toJson(expect) + ");" +
            "return 'ok';" +
            "}catch(e){return 'error:' + e}})()"
}
