package dev.aigw.provider.codebuddy

import dev.aigw.util.Http
import java.net.URLEncoder

/**
 * WorkBuddy 的微信扫码登录入口。
 *
 * 登录页里的「微信扫码登录」不是页面自己画的二维码，而是走 keycloak 的 broker：
 * 前端请求 `/auth/realms/copilot/console/auth/wechat-url` 拿一条
 * `open.weixin.qq.com/connect/qrconnect?...` 链接再跳过去。那个接口要求同源会话，
 * 外部直接调不通（返回 `failed to create wechat authorization url`）。
 *
 * 但同一条链路可以用标准的 OIDC 授权请求走通：
 * `/auth/realms/copilot/protocol/openid-connect/auth` 带上 `kc_idp_hint=weixinwebchat`
 * 时，keycloak 会直接把请求转给微信 broker，页面就是二维码。
 *
 * 拿到 code 之后与 CLI 授权共用同一个轮询接口，所以这里只负责「把页面带到二维码」。
 */
object WechatQrLogin {

    private const val AUTH_PATH = "/auth/realms/copilot/protocol/openid-connect/auth"

    /** keycloak 里微信网页登录的 IdP 别名。 */
    const val IDP_HINT = "weixinwebchat"

    /**
     * 在指定域申请一条 state，返回 (state, 登录页地址)。
     *
     * 必须在与微信回调相同的域上申请：跨域的话，页面登录后拿不到申请 state 时的
     * 会话，`/console/login/enterprise` 会一直返回 401。
     */
    fun requestState(host: String): Pair<String, String> {
        val r = Http.postJson(
            "$host/v2/plugin/auth/state?platform=CLI",
            mapOf(
                "Content-Type" to "application/json",
                "User-Agent" to "CLI/2.63.2 CodeBuddy/2.63.2",
                // 登录只发一次，不值得复用连接池
                "Connection" to "close",
            ),
            "{}",
        )
        if (!r.ok) error("申请 state 失败（HTTP ${r.status}）：${r.body.take(160)}")
        val obj = runCatching { com.google.gson.JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: error("state 响应不是合法 JSON")
        val data = obj.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: error("state 响应缺少 data")
        val state = data.get("state")?.takeIf { it.isJsonPrimitive }?.asString
            ?: error("state 响应缺少 state")
        val authUrl = data.get("authUrl")?.takeIf { it.isJsonPrimitive }?.asString
            ?: error("state 响应缺少 authUrl")
        return state to authUrl
    }

    /**
     * 构造带 `kc_idp_hint` 的授权地址。
     *
     * [host] 用登录页自己的域（国内 `www.workbuddy.cn`）：broker 回调地址写死在该域下，
     * 换成别的域会让 `redirect_uri` 校验失败。
     * [returnUrl] 是授权完成后要回到的登录页，带 `state`，与 CLI 授权链一致。
     */
    fun urlOf(host: String, returnUrl: String): String {
        val redirect = URLEncoder.encode(returnUrl, "UTF-8")
        return buildString {
            append(host).append(AUTH_PATH)
            append("?client_id=login")
            append("&response_type=code")
            append("&self_redirect=true")
            append("&kc_idp_hint=").append(IDP_HINT)
            append("&prompt=login")
            append("&redirect_uri=").append(redirect)
        }
    }
}
