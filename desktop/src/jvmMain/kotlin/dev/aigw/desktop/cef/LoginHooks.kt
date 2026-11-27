package dev.aigw.desktop.cef

import com.google.gson.JsonParser

/**
 * 网页登录钩子 JS（由 Android 版 WebLoginActivity 原样移植）。
 *
 * Android 用 `addJavascriptInterface(LoginBridge, "__aigwBridge")`；JCEF 没有
 * JavascriptInterface，改为 CefMessageRouter：页面调用
 * `window.__aigwBridgeQuery({request: ...})`，宿主在 [JcefBrowser.onBridge]
 * 收到。下面的 [BRIDGE_SHIM_JS] 在页面加载后注入，把旧桥对象适配到路由。
 */
object LoginHooks {

    const val MODE_DEVICE = "device"
    const val MODE_CALLBACK = "callback"
    const val MODE_TOKEN = "token"

    /**
     * 把 Android 的桥对象适配到 CefMessageRouter。
     * `__aigwBridge.onPayload(text)` / `DSBridge.onStatus(id, code)` 等调用
     * 全部转成 `{type:'bridge', bridge, method, args}` 的路由查询。
     */
    const val BRIDGE_SHIM_JS = """
(function(){
  if (window.__aigwShim) return; window.__aigwShim = true;
  function send(bridge, method, args){
    try {
      window.__aigwBridgeQuery({request: JSON.stringify({type:'bridge', bridge:bridge, method:method, args:args}),
        persistent:false, onSuccess:function(){}, onFailure:function(){}});
    } catch(e){}
  }
  window.__aigwBridge = { onPayload: function(t){ send('aigw','onPayload',[String(t)]); } };
  window.DSBridge = {
    onStatus: function(id, code){ send('ds','onStatus',[String(id), String(code)]); },
    onChunk: function(id, text){ send('ds','onChunk',[String(id), String(text)]); },
    onDone: function(id){ send('ds','onDone',[String(id)]); },
    onError: function(id, msg){ send('ds','onError',[String(id), String(msg)]); }
  };
  window.ChatglmBridge = {
    onStatus: function(id, code){ send('chatglm','onStatus',[String(id), String(code)]); },
    onChunk: function(id, text){ send('chatglm','onChunk',[String(id), String(text)]); },
    onDone: function(id){ send('chatglm','onDone',[String(id)]); },
    onError: function(id, msg){ send('chatglm','onError',[String(id), String(msg)]); }
  };
  window.MuseBridge = {
    onOk: function(id, json){ send('muse','onOk',[String(id), String(json)]); },
    onFail: function(id, msg){ send('muse','onFail',[String(id), String(msg)]); }
  };
})();
"""

    /** 授权完成后的回调嗅探：拦截 window.open / fetch / XHR / location，把授权码回传。 */
    const val CALLBACK_HOOK_JS = """
(function(){
  if (window.__aigwHooked) return; window.__aigwHooked = true;
  var report = function(t){
    try { if (t && String(t).indexOf('authorization_code') >= 0) window.__aigwBridge.onPayload(String(t)); } catch(e){}
  };
  try {
    var rawOpen = window.open;
    window.open = function(u){ report(u); return rawOpen ? rawOpen.apply(window, arguments) : null; };
  } catch(e){}
  try {
    var rawFetch = window.fetch;
    if (rawFetch) window.fetch = function(){
      var p = rawFetch.apply(this, arguments);
      try { p.then(function(r){ try { r.clone().text().then(report); } catch(e){} }); } catch(e){}
      return p;
    };
  } catch(e){}
  try {
    var rawSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(){
      var self = this;
      this.addEventListener('load', function(){ try { report(self.responseText); } catch(e){} });
      return rawSend.apply(this, arguments);
    };
  } catch(e){}
  setInterval(function(){ report(location.href); }, 500);
})();
"""

    /** 检测登录页是否渲染出了「页面已过期」（state 超时未完成授权）。 */
    const val EXPIRED_CHECK_JS =
        "(function(){var b=document.body;" +
            "return b&&b.innerText&&b.innerText.indexOf('页面已过期')>=0?'true':'false';})()"

    /** 优先读请求头截到的 token，回退 localStorage（结构为 {value:...}）。 */
    const val TOKEN_READ_JS =
        "(function(){if(window.__dsToken)return window.__dsToken;" +
            "try{var v=localStorage.getItem('userToken');if(!v)return '';" +
            "var o=JSON.parse(v);return (o&&o.value)?o.value:v;}catch(e){return '';}})()"

    /** 钩住页面 fetch / XHR，从真实请求头里取 Bearer token。 */
    const val TOKEN_HOOK_JS = """
(function(){
  if (window.__dsTokenHook) return;
  window.__dsTokenHook = true;
  function capture(headers){
    try{
      if(!headers) return;
      var auth = null;
      if (typeof headers.get === 'function') auth = headers.get('authorization');
      if(!auth && typeof headers === 'object'){
        for(var k in headers){ if(String(k).toLowerCase() === 'authorization') auth = headers[k]; }
      }
      if(auth && String(auth).indexOf('Bearer ') === 0) window.__dsToken = String(auth).substring(7);
    }catch(e){}
  }
  var origFetch = window.fetch;
  if(origFetch){
    window.fetch = function(input, init){
      try{ if(init && init.headers) capture(init.headers); }catch(e){}
      return origFetch.apply(this, arguments);
    };
  }
  var origSet = XMLHttpRequest.prototype.setRequestHeader;
  XMLHttpRequest.prototype.setRequestHeader = function(name, value){
    try{
      if(String(name).toLowerCase() === 'authorization' && String(value).indexOf('Bearer ') === 0){
        window.__dsToken = String(value).substring(7);
      }
    }catch(e){}
    return origSet.apply(this, arguments);
  };
})();
"""

    /** 从 chatglm.cn cookie 串里提取凭证，拼成 JSON 交给 provider 解析。 */
    fun chatglmCredentialJson(cookie: String): String {
        val map = HashMap<String, String>()
        cookie.split(';').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) map[kv.substring(0, i).trim()] = kv.substring(i + 1).trim()
        }
        val token = map["chatglm_token"] ?: return ""
        val refresh = map["chatglm_refresh_token"] ?: ""
        val uid = map["chatglm_user_id"] ?: ""
        // 完整 cookie 一并存下：WAF 指纹（acw_tc / ssxmod 等）部分接口需要
        val cookieEscaped = cookie.replace("\\", "\\\\").replace("\"", "\\\"")
        return "{\"provider\":\"chatglm\",\"token\":\"$token\",\"refresh\":\"$refresh\",\"uid\":\"$uid\",\"cookie\":\"$cookieEscaped\"}"
    }

    /** evaluateJavascript 返回的是 JSON 字面量（字符串或 null），这里还原成文本。 */
    fun parseJsString(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null") return null
        val parsed = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return null
        if (!parsed.isJsonPrimitive) return null
        val primitive = parsed.asJsonPrimitive
        if (!primitive.isString) return null
        return primitive.asString.ifBlank { null }
    }
}
