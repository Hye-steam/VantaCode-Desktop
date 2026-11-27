package dev.aigw.desktop.cef

import com.google.gson.Gson
import dev.aigw.provider.WebTransport
import dev.aigw.provider.WebTransportResult
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * muse.ai 的页面通道：生成能力只对真实页面开放，靠 JCEF 操作 DOM。
 *
 * 由 Android 的 MuseWebTransport 移植：单实例会话、一个进程同时只允许一个
 * 浏览器（多开会相互干扰）、视频生成给足 20 分钟。
 * Android 的 Handler/Looper 与 WindowManager 挂载（保活 WebView）改为
 * 隐藏 OSR 浏览器（无需窗口即可加载页面与执行 JS）。
 */
class MuseWebTransport : WebTransport {

    private val gson = Gson()

    /** 登录 cookie（由账号 secret 提供）。 */
    @Volatile
    private var cookie: String? = null

    /** 单实例会话：一个进程同时只允许一个浏览器，多开会相互干扰。 */
    @Volatile
    private var shared: Session? = null

    fun warmUp() {
        // 预热只提前建 cookie，不提前建浏览器：
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
        // 不在这里建浏览器：prepare 常在工作线程被调，
        // 提前建会与真实请求撞上，两边都排队。只记 cookie，首次 post 再建。
        return true
    }

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

        // 不再自己加队列：Engine 已经按账号并发闸门逐个放行，
        // 这里再拦一道会让名额被占着等更久（视频要等好几分钟），
        // 后续请求全部撞「并发占满」而被判失败。
        return session().generate(prompt, expect)
    }

    /** 一次生成的独立页面上下文。 */
    private inner class Session {
        private val id = UUID.randomUUID().toString()
        private val queue = LinkedBlockingQueue<Event>()

        @Volatile
        private var browser: JcefBrowser? = null

        @Volatile
        private var started = false

        /** 挂浏览器组件的后台窗（见 attachToBackgroundWindow）。 */
        @Volatile
        private var hostFrame: JFrame? = null

        fun start() {
            if (started) return
            started = true
            val b = try {
                // 先建 about:blank：cookie 必须在首载之前注入并 flush（安卓同款），
                // 否则首载落在登录墙上，后续再注也救不回来。
                JcefBrowser(
                    startUrl = "about:blank",
                    afterLoad = { page ->
                        page.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                        page.evalVoid(JS_ERROR_HOOK)
                        page.evalVoid(jsHelper)
                    },
                )
            } catch (e: Throwable) {
                started = false
                throw IllegalStateException("muse 浏览器创建失败：${e.message}")
            }
            b.onBridge = onBridge@{ bridge, method, args ->
                if (bridge != "muse") return@onBridge
                if (args.getOrNull(0) != id) return@onBridge
                when (method) {
                    "onOk" -> queue.offer(Event.Ok(args.getOrNull(1).orEmpty()))
                    "onFail" -> queue.offer(Event.Fail(args.getOrNull(1).orEmpty()))
                }
            }
            browser = b
            try {
                // 未挂窗口的浏览器不产生合成帧，rAF 永不回调，Next.js 水合停在骨架上
                // （安卓同款：400×800 alpha 0.01 离屏窗）。
                attachToBackgroundWindow(b)
                // 先注入登录 cookie 再等页面就绪
                cookie?.let { injectCookies(b, it) }
                b.flushCookies()
                b.load(HOME)
                if (!b.awaitReady(READY_TIMEOUT_MS)) {
                    throw IllegalStateException("muse 页面未就绪")
                }
                b.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                b.evalVoid(JS_ERROR_HOOK)
                b.evalVoid(jsHelper)
            } catch (e: Throwable) {
                close()
                throw e
            }
        }

        /**
         * 把浏览器组件挂到一个后台窗口上，让页面拿到合成帧、rAF 正常跑。
         * 安卓同款做法（WindowManager 挂 400×800 alpha 0.01 离屏窗）：
         * 未挂窗口的 WebView 不产生合成帧，muse.ai 的水合链路由 rAF 驱动，
         * 页面会一直停在服务端渲染的骨架上。
         */
        private fun attachToBackgroundWindow(page: JcefBrowser) {
            val attach = {
                val f = JFrame()
                f.isUndecorated = true
                f.setSize(400, 800)
                f.setLocation(-500, -900)
                f.defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                f.add(page.component)
                // 不用完全透明：部分系统会因此跳过合成，rAF 又不跑了（安卓同款 0.01）
                runCatching { f.opacity = 0.01f }
                f.isVisible = true
                hostFrame = f
            }
            if (SwingUtilities.isEventDispatchThread()) {
                runCatching { attach() }
            } else {
                runCatching { SwingUtilities.invokeAndWait(Runnable { attach() }) }
            }
        }

        private fun injectCookies(page: JcefBrowser, raw: String) {
            raw.split(';').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) return@forEach
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (name.isEmpty()) return@forEach
                runCatching { page.setCookie("https://muse.ai", name, value) }
                runCatching { page.setCookie("https://muse.ai", name, value, domain = ".muse.ai") }
            }
        }

        private fun awaitReady(): Boolean {
            val page = browser ?: return false
            val deadline = System.currentTimeMillis() + READY_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val ok = page.evalJson("(typeof window.__museGen === 'function')", 5_000) == "true"
                if (ok) return true
                Thread.sleep(1000)
            }
            return false
        }

        private fun gotoNewThread(page: JcefBrowser): Boolean {
            val cur = LoginHooks.parseJsString(page.evalJson("(location.pathname||'')", 5_000)).orEmpty()
            if (cur.startsWith("/thread/new")) {
                // 登录态校验：首载若在注 cookie 前发生会停在登录墙，pathname 看着对但没登录。
                if (hasSession(page)) return true
                page.load(HOME)
                if (!page.awaitReady(READY_TIMEOUT_MS)) return false
                page.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                page.evalVoid(JS_ERROR_HOOK)
                page.evalVoid(jsHelper)
                return hasSession(page) && awaitReady()
            }
            page.load(HOME)
            if (!page.awaitReady(READY_TIMEOUT_MS)) return false
            page.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
            page.evalVoid(JS_ERROR_HOOK)
            page.evalVoid(jsHelper)
            return awaitReady()
        }

        /** 会话是否还在：muse.ai 登录态全在 hatch_sess cookie 里。 */
        private fun hasSession(page: JcefBrowser): Boolean =
            runCatching { page.getCookies("https://muse.ai") }.getOrDefault("")
                .contains("hatch_sess=")

        fun generate(prompt: String, expect: String): WebTransportResult {
            val page = browser ?: return WebTransportResult(0, "", "muse 浏览器未就绪")
            if (!gotoNewThread(page)) return WebTransportResult(0, "", "muse 页面未就绪")
            // 页面导航会重建 JS 上下文，注入过的脚本随之消失，直接调会静默失效、
            // 请求一直挂着。这里确认后补注入，180 秒内重试（安卓同款）。
            var attempts = 0
            var lastVerdict: String? = null
            var started = false
            val startDeadline = System.currentTimeMillis() + 180_000
            while (!started && System.currentTimeMillis() < startDeadline) {
                val verdict = LoginHooks.parseJsString(page.evalJson(jsStart(id, prompt, expect), 20_000))
                lastVerdict = verdict
                if (verdict == "ok") {
                    started = true
                } else {
                    attempts++
                    Thread.sleep(2000)
                    page.evalVoid(LoginHooks.BRIDGE_SHIM_JS)
                    page.evalVoid(JS_ERROR_HOOK)
                    page.evalVoid(jsHelper)
                }
            }
            if (!started) {
                return WebTransportResult(
                    0, "",
                    "muse 生成启动失败：页面脚本未就绪（重试 $attempts 次，${lastVerdict ?: "无响应"}）",
                )
            }
            // 生成启动后等产物：视频慢，给足 20 分钟
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                val remain = deadline - System.currentTimeMillis()
                val event = queue.poll(remain, TimeUnit.MILLISECONDS) ?: continue
                when (event) {
                    is Event.Ok -> return WebTransportResult(200, event.json)
                    is Event.Fail -> return WebTransportResult(0, "", event.message)
                }
            }
            return WebTransportResult(0, "", "muse 生成超时")
        }

        fun close() {
            runCatching { browser?.close() }
            browser = null
            started = false
            runCatching { hostFrame?.dispose() }
            hostFrame = null
        }
    }

    private fun jsStart(id: String, prompt: String, expect: String): String =
        "(function(){try{" +
            "if (typeof window.__museGen !== 'function') return 'missing';" +
            "window.__museGen(" + gson.toJson(id) + ", " + gson.toJson(prompt) +
            ", " + gson.toJson(expect) + ");" +
            "return 'ok';" +
            "}catch(e){return 'error:' + e}})()"

    /** 页面回调只带 id，按 id 找队列，多会话互不干扰。 */
    private sealed interface Event {
        data class Ok(val json: String) : Event
        data class Fail(val message: String) : Event
    }

    companion object {
        const val HOME = "https://muse.ai/thread/new"

        /** 视频生成慢，给足 20 分钟。 */
        const val TIMEOUT_MS = 1_200_000L

        const val READY_TIMEOUT_MS = 60_000L

        /** 不带 `wv` 标记的桌面 UA：系统默认 UA 里的 wv 会让站点识别为内嵌 WebView。 */
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
      // 判定必须看真实元素，不能看容器 id：视频未渲染完时容器里只有一个
      // 封面 <img>（presentation-image），按 id 判会把封面当成成品返回。
      var isVid = !!v;
      var primary = isVid ? v : img;
      var src = primary ? (primary.currentSrc || primary.src || '') : '';
      if(!src || src.indexOf('avatar') !== -1 || src.indexOf('emoji') !== -1) return;
      // 视频还在转时先别把封面当结果：有 poster 的 <video> 只有拿到真实源才算
      if (isVid && !(v.currentSrc || v.src)) return;
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
