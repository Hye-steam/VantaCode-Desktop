package dev.aigw.desktop

/**
 * 供应商展示元数据（displayName 以 `:core` 各 Provider 的声明为准）。
 *
 * 登录方式总览（与分析 §1.1 一致）：
 * - codebuddy：国内/国际设备授权、微信扫码、JSON 导入
 * - trae：国内/国际网页回调登录、JSON 导入
 * - chatgpt：网页回调登录 / 粘贴回调链接 / 复制授权链接到外部浏览器
 * - deepseek：网页登录（抓 token）/ 粘贴 Token
 * - loomy：短信验证码登录
 * - mimo：网页登录 / 粘贴 Cookie
 * - chatglm：网页登录 / 粘贴 Cookie（另有短信登录）
 * - jimeng：网页登录（抓 sessionid Cookie）
 * - doubao：网页登录（抓 Cookie + deviceId + webId）
 * - muse：网页登录（抓 hatch_sess Cookie）/ 粘贴 Cookie
 * - raccoon：网页登录（Quark 回调；登录时不得传 login_source=desktop）
 * - custom：填 base URL + key
 * - freepool：一键启用，无需登录
 */
object ProviderMeta {

    fun displayName(providerId: String): String = when (providerId) {
        "custom" -> "自定义"
        "codebuddy" -> "WorkBuddy"
        "trae" -> "Trae"
        "deepseek" -> "DeepSeek"
        "loomy" -> "Loomy"
        "mimo" -> "MiMo"
        "raccoon" -> "小浣熊"
        "chatglm" -> "智谱清言"
        "freepool" -> "免费池"
        "doubao" -> "豆包"
        "jimeng" -> "即梦"
        "chatgpt" -> "ChatGPT"
        "muse" -> "muse.ai"
        else -> providerId
    }

    /** 该供应商在添加账号页提供的登录方式（用于 UI 文案与入口选择）。 */
    fun loginOptions(providerId: String): List<String> = when (providerId) {
        "codebuddy" -> listOf("国内授权", "国际授权", "微信扫码登录", "JSON 导入")
        "trae" -> listOf("国内授权", "国际授权", "JSON 导入")
        "chatgpt" -> listOf("网页登录", "粘贴回调链接导入", "复制授权链接到外部浏览器")
        "deepseek" -> listOf("网页登录", "粘贴 Token 登录")
        "loomy" -> listOf("短信验证码登录")
        "mimo" -> listOf("网页登录", "粘贴 Cookie 登录")
        "chatglm" -> listOf("网页登录", "粘贴 Cookie 登录", "短信验证码登录")
        "jimeng" -> listOf("网页登录")
        "doubao" -> listOf("添加豆包账号")
        "muse" -> listOf("网页登录", "粘贴 Cookie")
        "raccoon" -> listOf("网页登录")
        "custom" -> listOf("添加自定义账号")
        "freepool" -> listOf("一键启用，无需登录")
        else -> listOf("JSON 导入")
    }
}
