package dev.aigw.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.gson.Gson
import dev.aigw.provider.WebTransport
import dev.aigw.provider.WebTransportResult
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
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
 * 每个请求独占一个 WebView 与一条旁聊：muse 每次发消息都会新开旁聊，
 * 互不影响，共用同一个 WebView 才是错的——第二个请求既要等前一个释放，
 * 又会因为 `ensureFreshThread` 导航把前一个的 JS 上下文毁掉。
 */
class MuseWebTransport(private val context: Context) : WebTransport {

    private val main = Handler(Looper.getMainLooper())
    private val gson = Gson()

    /** 登录 cookie（由账号 secret 提供）。 */
    @Volatile
    private var cookie: String? = null

    /** 常驻的预热会话，只用来让首个请求不必从零等页面；不承载真实任务。 */
    @Volatile
    private var warm: Session? = null

    /** 单实例会话：一个进程同时只允许一个 WebView，多开会相互干扰。 */
    @Volatile
    private var shared: Session? = null

    fun warmUp() {
        // 预热只提前建 cookie，不提前建 WebView：
        // 提前建会与首个真实请求撞车，两边一起卡在创建上。
    }

    private fun session(): Session {
        shared?.let { return it }
        synchronized(this) {
            shared?.let { return it }
            val s = Session()
            s.start()
            shared = s
            return s
        }
    }

    override fun prepare(cookies: String): Boolean {
        cookie = cookies
        // 不在这里建 WebView：prepare 常在工作线程被调，
        // 提前建会与真实请求撞上，两边都排队。只记 cookie，首次 post 再建。
        return true
    }

    /** 全局串行：WebView 构造、导航、销毁都必须在主线程上按序完成。
     *  并发请求在这里排队，而不是各自往主线程塞任务把队列堵死。 */
    private val serialGate: java.util.concurrent.Semaphore = java.util.concurrent.Semaphore(1, true)

    override fun post(
        path: String,
        headers: Map<String, String>,
        body: String,
        stream: Boolean,
        onLine: ((String) -> Boolean)?,
    ): WebTransportResult {
        val expect = if (path.contains("expect=video")) "video" else "image"
        val prompt = runCatching {
            gson.fromJson(body, Map::class.java)?.get("prompt")?.toString().orEmpty()
        }.getOrDefault("")
        if (prompt.isBlank()) return WebTransportResult(400, "", "缺少 prompt")

        // 等前一个生成结束：同一个 WebView、同一条旁聊链，不能并行。
        if (!serialGate.tryAcquire(10, TimeUnit.SECONDS)) {
            return WebTransportResult(429, "", "上一个生成还没结束，请稍后重试")
        }
        return try {
            val session = session()
            session.generate(prompt, expect)
        } finally {
            serialGate.release()
        }
    }

    /** 一次生成的独立页面上下文。 */
    private inner class Session {
        private val id = UUID.randomUUID().toString()
        private val queue = LinkedBlockingQueue<Event>()

        @Volatile
        private var view: WebView? = null

        @Volatile
        private var hostWindow: android.view.WindowManager? = null

        @Volatile
        private var ready = false

        @Volatile
        private var bridge: Bridge? = null

        fun start() {
            val tag = id.take(8)
            // 用单次往返拿结果：投递完再等一个带返回值的任务，
            // 能明确区分「还没执行」与「执行失败」。
            val latch = CountDownLatch(1)
            var err: Throwable? = null
            val posted = main.post {
                try {
                    createWebView()
                } catch (e: Throwable) {
                    err = e
                    android.util.Log.e("MUSE", "[$tag] 创建抛错：${e.javaClass.name}: ${e.message}")
                } finally {
                    latch.countDown()
                }
            }
            val done = latch.await(50, TimeUnit.SECONDS)
            if (!done) android.util.Log.w("MUSE", "[$tag] 建 WebView 超时")
        }

        @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
        private fun createWebView() {
            if (view != null) return
            android.util.Log.i("MUSE", "开始创建 WebView cookie=${if (cookie.isNullOrBlank()) "空" else "有(" + cookie!!.length + ")"}")
            val b = Bridge(id, queue)
            bridge = b
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.userAgentString = UA_DESKTOP
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                addJavascriptInterface(b, "MuseBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(v: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                        v.evaluateJavascript(JS_ERROR_HOOK, null)
                    }

                    override fun onPageFinished(v: WebView, url: String?) {
                        // 注入后即视为就绪：再发一次 evaluateJavascript 确认会把主线程
                        // 再排一轮队，而并发时队列本来就很长。
                        v.evaluateJavascript(jsHelper) { r ->
                            android.util.Log.i("MUSE", "辅助脚本注入：${r?.take(40)}")
                        }
                        ready = true
                    }

                    override fun onReceivedError(
                        v: WebView,
                        req: android.webkit.WebResourceRequest,
                        err: android.webkit.WebResourceError,
                    ) {
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || req.isForMainFrame) {
                            android.util.Log.w("MUSE", "页面加载失败：${req.url} ${err.description}")
                        }
                    }
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(msg: android.webkit.ConsoleMessage): Boolean {
                        val text = msg.message()
                        if (msg.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                            android.util.Log.w("MUSE", "console: ${text.take(160)}")
                        }
                        return true
                    }
                }
                cookie?.let { injectCookies(this, it) }
                loadUrl(HOME)
            }
            view = wv
            attachToWindow(wv)
        }

        private fun awaitReady(): Boolean {
            val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                if (ready) return true
                Thread.sleep(120)
            }
            return false
        }

        /** 导航回 `/thread/new` 并等页面重新就绪。 */
        private fun gotoNewThread(wv: WebView): Boolean {
            val cur = evalAndWait(wv, "(location.pathname||'')", 5_000).trim('"')
            if (cur.startsWith("/thread/new")) return true
            android.util.Log.i("MUSE", "当前 $cur，重新打开新对话")
            ready = false
            main.post { runCatching { wv.loadUrl(HOME) } }
            return awaitReady()
        }

        fun generate(prompt: String, expect: String): WebTransportResult {
            val wv = view ?: return WebTransportResult(0, "", "muse WebView 未就绪")
            if (!ready && !awaitReady()) {
                return WebTransportResult(504, "", "页面加载超时")
            }

            // 回到新建对话：旧对话里的产物会让「新产物计数」永不增加，
            // 历史上下文也会影响生成。导航会重建 JS 上下文，所以导航后要重新等 ready。
            if (!gotoNewThread(wv)) {
                return WebTransportResult(504, "", "重新打开对话失败")
            }

            // 页面导航会重建 JS 上下文，注入过的脚本随之消失，
            // 直接 evaluate 会静默失效、请求一直挂着。这里确认后补注入。
            var attempts = 0
            var started = false
            val startDeadline = System.currentTimeMillis() + 180_000
            while (!started && System.currentTimeMillis() < startDeadline) {
                val verdict = evalAndWait(wv, jsStart(id, prompt, expect), 20_000)
                if (verdict == "ok") {
                    started = true
                } else {
                    attempts++
                    android.util.Log.w("MUSE", "脚本未就绪（$verdict），重注入第 $attempts 次")
                    Thread.sleep(2000)
                    main.post { runCatching { wv.evaluateJavascript(jsHelper, null) } }
                }
            }
            if (!started) return WebTransportResult(502, "", "页面脚本注入失败（重试 $attempts 次）")

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
        }

        fun close() {
            val wv = view ?: return
            view = null
            ready = false
            // 同步销毁：投递到主线程会在请求堆积时把所有销毁任务堆在队尾，
            // 主线程被这些 destroy 拖住，下一个会话的创建任务反而排不上。
            val wm = hostWindow
            hostWindow = null
            if (Looper.myLooper() == Looper.getMainLooper()) {
                runCatching { wm?.removeView(wv) }
                runCatching { wv.stopLoading() }
                runCatching { wv.destroy() }
            } else {
                val latch = CountDownLatch(1)
                main.post {
                    runCatching { wm?.removeView(wv) }
                    runCatching { wv.stopLoading() }
                    runCatching { wv.destroy() }
                    latch.countDown()
                }
                latch.await(5, TimeUnit.SECONDS)
            }
        }

        private fun evalAndWait(wv: WebView, js: String, timeoutMs: Long): String {
            val latch = CountDownLatch(1)
            var verdict = ""
            main.post {
                runCatching {
                    wv.evaluateJavascript(js) { r ->
                        verdict = r.orEmpty().trim('"')
                        latch.countDown()
                    }
                }.onFailure {
                    verdict = "err:" + it.message
                    latch.countDown()
                }
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            return verdict
        }

        /**
         * 把 WebView 挂到一个透明的悬浮窗上。
         *
         * 未挂窗口的 WebView 不产生合成帧，requestAnimationFrame 永不回调；
         * muse.ai 是 Next.js + Suspense，水合链路由 rAF 驱动，页面会一直停在
         * 服务端渲染的骨架（`<!--$-->` 占位）上。
         */
        private fun attachToWindow(v: WebView) {
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
                lp.x = -500
                lp.y = -900
                // 不用 alpha=0：部分机型会因此跳过合成，rAF 又不跑了
                v.alpha = 0.01f
                wm.addView(v, lp)
                hostWindow = wm
            }.onFailure {
                android.util.Log.w("MUSE", "挂窗失败：${it.message}")
            }
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
            runCatching { cm.setCookie("https://muse.ai", "$name=$value; path=/; secure") }
            runCatching { cm.setCookie("https://.muse.ai", "$name=$value; path=/; secure") }
        }
        runCatching { cm.flush() }
    }

    private fun jsStart(id: String, prompt: String, expect: String): String =
        "(function(){try{" +
            "if (typeof window.__museGen !== 'function') return 'missing';" +
            "window.__museGen(" + gson.toJson(id) + ", " + gson.toJson(prompt) +
            ", " + gson.toJson(expect) + ");" +
            "return 'ok';" +
            "}catch(e){return 'error:' + e}})()"

    /** 页面回调只带 id，按 id 找队列，多会话互不干扰。 */
    inner class Bridge(
        private val sessionId: String,
        private val queue: LinkedBlockingQueue<Event>,
    ) {
        @JavascriptInterface
        fun onOk(id: String, json: String) {
            if (id != sessionId) return
            queue.offer(Event.Ok(json))
        }

        @JavascriptInterface
        fun onFail(id: String, message: String) {
            if (id != sessionId) return
            queue.offer(Event.Fail(message))
        }
    }

    sealed interface Event {
        data class Ok(val json: String) : Event
        data class Fail(val message: String) : Event
    }

    companion object {
        const val HOME = "https://muse.ai/thread/new"

        /** 视频生成慢，给足 20 分钟。 */
        const val TIMEOUT_MS = 1_200_000L

        const val READY_TIMEOUT_MS = 60_000L

        /**
         * 不带 `wv` 标记的桌面 UA：系统默认 UA 里的 wv 会让站点识别为内嵌 WebView。
         */
        const val UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Safari/537.36"

        /** 尽早装的错误收集：onPageStarted 注入，能捕到 bundle 早期抛错。 */
        const val JS_ERROR_HOOK = """
(function(){
  if (window.__museErr) return;
  window.__museErr = [];
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

        /**
         * 页面内辅助脚本：等水合、填字、点发送、抓产物。
         *
         * 抓产物用轮询而非 MutationObserver——附件容器出现后内容还要再渲染一次，
         * 轮询更容易拿到已经填好 src 的元素。
         */
        val jsHelper: String = """
(function(){
  if (window.__museGen) return;
  var bridge = window.MuseBridge;
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
  function hydrated(){
    var h = document.querySelector('[data-hatch-shell-hydration-state]');
    if (!h) return false;
    if (h.getAttribute('data-hatch-shell-hydration-state') !== 'hydrated') return false;
    var t = document.body ? (document.body.innerText||'') : '';
    return t.indexOf('Connecting...') === -1;
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
  function setText(el, value){
    var proto = el.tagName === 'TEXTAREA'
      ? window.HTMLTextAreaElement.prototype
      : window.HTMLInputElement.prototype;
    var setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
    setter.call(el, value);
    el.dispatchEvent(new Event('input', {bubbles:true}));
    el.dispatchEvent(new Event('change', {bubbles:true}));
  }
  function findSend(){
    var sels = [
      'button[data-testid="send-button"]',
      'button[aria-label*="Send"]',
      'button[aria-label*="发送"]',
      'button[type="submit"]'
    ];
    for (var i=0;i<sels.length;i++){
      var b = document.querySelector(sels[i]);
      if (b && !b.disabled) return b;
    }
    var all = document.querySelectorAll('button');
    for (var j=0;j<all.length;j++){
      var t = (all[j].getAttribute('aria-label')||'') + (all[j].textContent||'');
      if (/send|发送/i.test(t) && !all[j].disabled) return all[j];
    }
    return null;
  }
  function collect(expect){
    // 排除用户自己发的附件，并用 currentSrc —— blob: 地址常常只在 currentSrc 里可用。
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
    return out.filter(function(x){ return expect === 'video' ? x.isVideo : !x.isVideo; });
  }
  async function toB64(url){
    var r = await fetch(url);
    if (!r.ok) throw new Error('media-http-' + r.status);
    var b = await r.blob();
    var reader = new FileReader();
    var s = await new Promise(function(res, rej){
      reader.onload = function(){ res(String(reader.result).split(',')[1] || ''); };
      reader.onerror = function(){ rej(reader.error || new Error('read-failed')); };
      reader.readAsDataURL(b);
    });
    return {mime: b.type || '', size: b.size, b64: s};
  }
  window.__museGen = async function(id, prompt, expect){
    try {
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
      // 视频先出封面图（presentation-image），<video> 渲染完才挂上，
      // 所以必须按真实 video 元素判定，不能拿封面当成品。
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
    }
}
