package dev.aigw.gateway

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.GatewayManagedSelector
import dev.aigw.util.arrayOrNull
import java.io.File
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/** 代理协议类型。 */
enum class ProxyType(val label: String) {
    HTTP("HTTP"),
    SOCKS("SOCKS5");

    fun toJavaType(): Proxy.Type = when (this) {
        HTTP -> Proxy.Type.HTTP
        SOCKS -> Proxy.Type.SOCKS
    }
}

/**
 * 代理设置：总开关 + 按供应商排除 + 按地址排除。
 * 打开后默认所有上游走代理；国内域名永远直连（见 [GatewayProxySelector.DOMESTIC_SUFFIXES]）。
 */
data class ProxySettings(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
    val excludedProviderIds: Set<String> = emptySet(),
    val excludedHosts: List<String> = DEFAULT_EXCLUDED_HOSTS,
) {
    val usable: Boolean get() = enabled && host.isNotBlank() && port in 1..65535

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("enabled", enabled)
        addProperty("type", type.name)
        addProperty("host", host)
        addProperty("port", port)
        addProperty("username", username)
        addProperty("password", password)
        add("excludedProviderIds", JsonArray().apply { excludedProviderIds.forEach { add(it) } })
        add("excludedHosts", JsonArray().apply { excludedHosts.forEach { add(it) } })
    }

    companion object {
        const val STORE_KEY = "proxy.json"

        val DEFAULT_EXCLUDED_HOSTS = listOf(
            "localhost", "127.0.0.1", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
        )

        fun load(dataDir: File): ProxySettings {
            val f = File(dataDir, STORE_KEY)
            if (!f.exists()) return ProxySettings()
            return fromJson(runCatching { f.readText() }.getOrNull())
        }

        fun save(dataDir: File, settings: ProxySettings) {
            dataDir.mkdirs()
            File(dataDir, STORE_KEY).writeText(settings.toJson().toString())
        }

        fun fromJson(raw: String?): ProxySettings {
            if (raw.isNullOrEmpty()) return ProxySettings()
            val obj = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: return ProxySettings()
            val excluded = obj.arrayOrNull("excludedProviderIds")
                ?.mapNotNull { runCatching { it.asString }.getOrNull() }
                ?.toSet()
                .orEmpty()
            val excludedHosts = obj.get("excludedHosts")?.takeIf { it.isJsonArray }
                ?.let { array -> array.asJsonArray.mapNotNull { runCatching { it.asString }.getOrNull() } }
                ?: DEFAULT_EXCLUDED_HOSTS
            return ProxySettings(
                enabled = obj.get("enabled")?.asBoolean ?: false,
                type = obj.get("type")?.asString
                    ?.let { name -> runCatching { ProxyType.valueOf(name) }.getOrNull() }
                    ?: ProxyType.HTTP,
                host = obj.get("host")?.asString.orEmpty().trim(),
                port = obj.get("port")?.asInt ?: 8080,
                username = obj.get("username")?.asString.orEmpty(),
                password = obj.get("password")?.asString.orEmpty(),
                excludedProviderIds = excluded,
                excludedHosts = excludedHosts,
            )
        }
    }
}

/**
 * 按目标域名决定是否走代理（全局 [ProxySelector]）。
 *
 * 之所以用全局 ProxySelector 而不是逐请求传 Proxy：各 provider 的 HTTP 客户端都是
 * 自己 new HttpURLConnection，逐个改造成本高且容易漏；「按供应商」本质就是按域名判断。
 */
class GatewayProxySelector(
    private val settings: () -> ProxySettings,
    private val providerHosts: () -> Map<String, List<String>>,
) : ProxySelector(), GatewayManagedSelector {

    @Volatile
    private var ruleCacheSource: List<String>? = null
    @Volatile
    private var ruleCache: List<CompiledRule> = emptyList()

    /**
     * 代理开关关闭时交给系统（VPN / Wi-Fi 代理）。
     *
     * 不能直接返回 NO_PROXY：这个选择器会顶替掉 JVM 默认实现，直连意味着
     * 连系统代理设置一起被忽略，用户开的 VPN 对 App 请求就完全不生效。
     */
    private val systemSelector: ProxySelector? = runCatching { ProxySelector.getDefault() }
        .getOrNull()
        ?.takeIf { it !is GatewayManagedSelector }

    override fun select(uri: URI): List<Proxy> {
        val config = settings()
        if (!config.usable) return systemSelector?.select(uri) ?: NO_PROXY
        val host = uri.host?.lowercase() ?: return NO_PROXY
        if (host == "127.0.0.1" || host == "localhost" || host == "::1") return NO_PROXY
        if (isDomestic(host)) return NO_PROXY
        if (isExcluded(host, config)) return NO_PROXY
        if (isExcludedByRule(host, uri, config.excludedHosts)) return NO_PROXY
        return listOf(Proxy(config.type.toJavaType(), InetSocketAddress(config.host, config.port)))
    }

    private fun isDomestic(host: String): Boolean =
        DOMESTIC_SUFFIXES.any { host == it || host.endsWith(".$it") }

    private fun isExcluded(host: String, config: ProxySettings): Boolean {
        if (config.excludedProviderIds.isEmpty()) return false
        for ((providerId, suffixes) in providerHosts()) {
            if (providerId !in config.excludedProviderIds) continue
            if (suffixes.any { host == it || host.endsWith(".$it") }) return true
        }
        return false
    }

    private fun isExcludedByRule(host: String, uri: URI, rules: List<String>): Boolean =
        compiledRules(rules).any { it.matches(host, uri) }

    private fun compiledRules(rules: List<String>): List<CompiledRule> {
        val cachedSource = ruleCacheSource
        if (cachedSource == rules) return ruleCache
        val compiled = rules.mapNotNull { compileRule(it) }
        ruleCacheSource = rules
        ruleCache = compiled
        return compiled
    }

    private open class CompiledRule(
        val hostRule: String,
        val port: Int?,
        val regex: Regex?,
    ) {
        open fun matches(host: String, uri: URI): Boolean {
            if (port != null && port != effectivePort(uri)) return false
            val re = regex
            return if (re != null) re.matches(host) else host == hostRule || host.endsWith(".$hostRule")
        }

        private companion object {
            fun effectivePort(uri: URI): Int =
                uri.port.takeIf { it != -1 } ?: if (uri.scheme.equals("http", ignoreCase = true)) 80 else 443
        }
    }

    private inner class CidrRule(private val rule: String) : CompiledRule(hostRule = "", port = null, regex = null) {
        override fun matches(host: String, uri: URI): Boolean = matchesCidr(host, rule)
    }

    private fun compileRule(raw: String): CompiledRule? {
        val rule = raw.trim().lowercase()
        if (rule.isEmpty()) return null
        if (rule.contains('/')) return CidrRule(rule)
        val portSuffix = rule.substringAfterLast(':', "")
        val rulePort = portSuffix.toIntOrNull()
        val hostRule = if (rulePort != null) rule.substringBeforeLast(':') else rule
        val regex = if (hostRule.contains('*')) {
            Regex(hostRule.split('*').joinToString(".*") { Regex.escape(it) })
        } else {
            null
        }
        return CompiledRule(hostRule, rulePort, regex)
    }

    private fun matchesCidr(host: String, rule: String): Boolean {
        val slash = rule.indexOf('/')
        if (slash <= 0) return false
        val network = ipv4ToInt(rule.substring(0, slash)) ?: return false
        val prefix = rule.substring(slash + 1).toIntOrNull() ?: return false
        if (prefix !in 0..32) return false
        val ip = ipv4ToInt(host) ?: return false
        if (prefix == 0) return true
        val mask = -(1 shl (32 - prefix))
        return (ip and mask) == (network and mask)
    }

    private fun ipv4ToInt(value: String): Int? {
        val parts = value.split('.')
        if (parts.size != 4) return null
        var result = 0
        for (part in parts) {
            val octet = part.toIntOrNull() ?: return null
            if (octet !in 0..255 || (part.length > 1 && part.startsWith('0'))) return null
            result = (result shl 8) or octet
        }
        return result
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        systemSelector?.connectFailed(uri, sa, ioe)
    }

    /**
     * 日志用：说明该 URL 的实际路由。
     *
     * 代理开关关闭时流量交给系统（VPN / Wi-Fi 代理），这里直接问系统选择器，
     * 并把「命中国内域名规则」也显式标出来，否则看不出为何没走代理。
     */
    override fun describeRoute(uri: URI): String {
        val config = settings()
        if (!config.usable) {
            val system = systemSelector?.select(uri)?.firstOrNull()
            val address = system?.address() as? InetSocketAddress
            if (system == null || system.type() == Proxy.Type.DIRECT || address == null) {
                return "直连（网关代理未启用）"
            }
            return "经系统代理 ${address.hostString}:${address.port}"
        }
        val host = uri.host?.lowercase().orEmpty()
        return when {
            isDomestic(host) -> "直连（命中国内域名，不走网关代理）"
            isExcluded(host, config) -> "直连（该供应商已排除代理）"
            isExcludedByRule(host, uri, config.excludedHosts) -> "直连（命中排除规则）"
            else -> "经网关代理 ${config.type.name} ${config.host}:${config.port}"
        }
    }

    companion object {
        private val NO_PROXY = listOf(Proxy.NO_PROXY)

        /** 国内上游域名后缀：命中即直连，不受代理开关与供应商排除项影响。 */
        val DOMESTIC_SUFFIXES = setOf(
            "copilot.tencent.com",
            "codebuddy.cn",
            "tencent.com",
            "qq.com",
            "api.trae.com.cn",
            "api.trae.cn",
            "trae-api-cn.mchost.guru",
            "xfinfr.com",
            "xunfei.cn",
            "xiaohuanxiong.com",
        )
    }
}

/** 代理需要用户名密码时给 HttpURLConnection 提供凭据（仅对代理主机生效）。 */
class GatewayProxyAuthenticator(
    private val settings: () -> ProxySettings,
) : Authenticator() {

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        val config = settings()
        if (!config.usable || config.username.isEmpty()) return null
        if (!requestingHost.equals(config.host, ignoreCase = true)) return null
        return PasswordAuthentication(config.username, config.password.toCharArray())
    }
}
