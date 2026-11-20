package dev.aigw.gateway

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.pool.AccountPool
import dev.aigw.pool.AccountTransfer
import dev.aigw.provider.AccountActivitySupport
import dev.aigw.provider.ApiError
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.DisplayNameSupport
import dev.aigw.provider.CredentialImportSupport
import dev.aigw.provider.CustomProvider
import dev.aigw.provider.DeviceAuthPoll
import dev.aigw.provider.DeviceAuthTicket
import dev.aigw.provider.DeviceCodeSupport
import dev.aigw.provider.ModelCatalogSupport
import dev.aigw.provider.ModelInfo
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.RegionAwareSupport
import dev.aigw.provider.SseSink
import dev.aigw.provider.TokenRefreshSupport
import dev.aigw.provider.UsageTokens
import dev.aigw.provider.WebCallbackSupport
import dev.aigw.provider.WebTokenSupport
import dev.aigw.provider.WebTransport
import dev.aigw.provider.ProviderActionSupport
import dev.aigw.provider.ProviderActionResult
import dev.aigw.provider.SmsLoginSupport
import dev.aigw.provider.cachedTokensOf
import dev.aigw.provider.codebuddy.CodeBuddyProvider
import dev.aigw.provider.chatglm.ChatglmProvider
import dev.aigw.provider.deepseek.DeepSeekProvider
import dev.aigw.provider.doubao.DoubaoProvider
import dev.aigw.provider.chatgpt.ChatGptProvider
import dev.aigw.provider.jimeng.JimengProvider
import dev.aigw.provider.freepool.FreePoolProvider
import dev.aigw.provider.loomy.LoomyProvider
import dev.aigw.provider.mimo.MimoProvider
import dev.aigw.provider.raccoon.RaccoonProvider
import dev.aigw.provider.trae.TraeConstants
import dev.aigw.provider.trae.TraeProvider
import dev.aigw.util.Diagnostics
import dev.aigw.util.Http
import dev.aigw.util.Log
import dev.aigw.util.arrayOrNull
import dev.aigw.util.firstString
import java.io.File
import java.net.Authenticator
import java.net.ProxySelector
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * 网关引擎：持有账号池/供应商/设置，负责路由解析、选号、凭证失效硬禁用
 * 与跨候选 Failover。Provider 边界 = OpenAI 进、OpenAI 出。
 */
class Engine(val dataDir: File) {

    /** 账号迁移信封导入的逐条结果，供界面汇总展示。 */
    data class AccountImportResult(
        val added: List<String>,
        val updated: List<String>,
        val skipped: List<String>,
        val unknown: List<String>,
    )

    val gson = Gson()
    val settings = Settings.load(dataDir)
    val pool = AccountPool(dataDir)
    val callLog = CallLog(File(dataDir, "logs"))
    val eventLog = EventLog(File(dataDir, "logs"))

    /** 远程后台客户端（心跳 / 公告 / 更新 / 工单）。 */
    private val adminClient = RemoteAdminClient()

    @Volatile
    var proxySettings: ProxySettings = ProxySettings.load(dataDir)
        private set

    /** 网页环境请求通道（由 App 层注入 WebView 实现）；为空时 DeepSeek 只走原生 HTTP。 */
    @Volatile
    var deepSeekWebTransport: WebTransport? = null

    /** 智谱清言的页面通道（App 层 WebView 实现，WAF 签名由页面环境注入）。 */
    @Volatile
    var chatglmWebTransport: WebTransport? = null

    private val providers: List<Provider> = listOf(
        CustomProvider(),
        CodeBuddyProvider(),
        // refreshToken 每次换 token 都会轮换，拿到新凭证要立刻回写账号池
        TraeProvider(onAccountUpdated = { account -> pool.add(account) }),
        DeepSeekProvider(transport = { deepSeekWebTransport }),
        LoomyProvider(),
        MimoProvider(),
        RaccoonProvider(onAccountUpdated = { account -> pool.add(account) }),
        ChatglmProvider(
            transport = { chatglmWebTransport },
            onAccountUpdated = { account -> pool.add(account) },
        ),
        FreePoolProvider(),
        DoubaoProvider(),
        JimengProvider(),
        ChatGptProvider(),
    )
    private val providerById: Map<String, Provider> = providers.associateBy { it.id }
    private val gates = ConcurrentHashMap<String, Semaphore>()
    private val gatePermits = ConcurrentHashMap<String, Int>()

    /** 模型级冷却：换号也撞同一道墙的错误（如「该模型已超限」）按供应商+模型冷却。 */
    private val modelCooldowns = ModelCooldownTracker()

    /** 供应商熔断器：上游整体失败时避免每个请求都去撞前端。 */
    private val breakers = ConcurrentHashMap<String, ProviderBreaker>()
    private val breakerThresholds = ConcurrentHashMap<String, Int>()

    /** 全局在途计数（maxConcurrentGlobal > 0 时生效）。 */
    private val globalLock = Object()
    private var globalInFlight = 0

    // ---------- 悬浮球状态信号（App 层 MonitorBubbleOverlay 每 1s 轮询） ----------

    /** 当前在途请求数（进入路由循环前 +1，所有出口 -1）。 */
    @Volatile
    var activeRequests = 0

    /** 最近一次请求事件：`结束`/`换号`/`失败`/`模型上限`；空串表示无。 */
    @Volatile
    var lastBubbleEvent = ""

    /** 最近一次请求事件时间戳（毫秒）；悬浮球在其后 3.5s 内展示该事件。 */
    @Volatile
    var lastBubbleEventAt = 0L

    private fun markBubble(event: String) {
        lastBubbleEvent = event
        lastBubbleEventAt = System.currentTimeMillis()
    }
    private val modelCache = ConcurrentHashMap<String, ModelCacheEntry>()
    /** 模型探测结果缓存：模型名 → 结果。结果与账号等级绑定，账号变了应重新探测。 */
    private val probeCache = ConcurrentHashMap<String, ProbeResult>()
    /** 会话粘黏映射：sessionKey → 账号 via（LRU 封顶 1000 条）。 */
    private val sessionPins = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 1000
    }

    private companion object {
        const val CALLBACK_TIMEOUT_MS = 10 * 60 * 1000L
    }

    init {
        // 全局代理选择器：按域名决定上游是否走代理（国内域名永远直连）
        ProxySelector.setDefault(GatewayProxySelector({ proxySettings }, { providerHosts() }))
        Authenticator.setDefault(GatewayProxyAuthenticator({ proxySettings }))
        // Provider 不直接持有 EventLog，统一通过这个出口上报细节
        Diagnostics.install { level, tag, message ->
            val parsed = runCatching { EventLog.Level.valueOf(level) }.getOrDefault(EventLog.Level.INFO)
            eventLog.add(parsed, tag, message)
        }
    }

    fun updateProxySettings(next: ProxySettings) {
        proxySettings = next
        ProxySettings.save(dataDir, next)
    }

    private fun providerHosts(): Map<String, List<String>> {
        val hosts = mutableMapOf<String, List<String>>()
        hosts["codebuddy"] = listOf(
            "copilot.tencent.com", "codebuddy.cn", "workbuddy.cn", "codebuddy.ai", "workbuddy.ai",
        )
        hosts["trae"] = listOf(
            "trae-api-cn.mchost.guru", "trae-api-sg.mchost.guru", "api.trae.cn", "api.trae.com.cn",
            "ug-normal.trae.ai", "api.trae.ai",
        )
        hosts["deepseek"] = listOf("chat.deepseek.com")
        hosts["loomy"] = listOf("xfinfr.com", "xunfei.cn")
        hosts["mimo"] = listOf("aistudio.xiaomimimo.com", "xiaomimimo.com")
        hosts["raccoon"] = listOf("xiaohuanxiong.com")
        val customHosts = pool.all().mapNotNull { entry ->
            runCatching { java.net.URI(entry.account.baseUrl).host }.getOrNull()
        }.distinct()
        if (customHosts.isNotEmpty()) hosts["custom"] = customHosts
        return hosts
    }

    private class ModelCacheEntry(val timestamp: Long, val models: List<ModelInfo>)

    // ---------- 路由 ----------

    data class Route(val account: ProviderAccount, val actualModel: String, val via: String)

    /**
     * 模型名解析：
     * - `custom:<uid>/模型`、`custom/模型`：自定义中转站
     * - `codebuddy/模型`、`codebuddy-cn/模型`、`codebuddy-global/模型`：WorkBuddy（可按区域筛选）
     * - 无前缀：所有启用的自定义账号（中转站接受任意模型名）
     */
    fun resolveCandidates(model: String): List<Route> {
        val routes = mutableListOf<Route>()
        val slash = model.indexOf('/')
        val prefix = if (slash > 0) model.substring(0, slash) else null
        val actual = if (slash > 0) model.substring(slash + 1).ifEmpty { model } else model

        if (prefix != null && prefix.startsWith("custom:")) {
            if (!isProviderEnabled("custom")) return routes
            val uid = prefix.removePrefix("custom:")
            pool.get("custom", uid)?.let { e ->
                if (e.state.enabled) routes.add(Route(e.account, actual, "custom:$uid"))
            }
            return routes
        }
        if (prefix == "custom" || prefix == null) {
            val routes = mutableListOf<Route>()
            if (prefix == "custom" && !isProviderEnabled("custom")) return routes
            if (prefix == null) {
                // 无前缀：按内置供应商的模型目录匹配；已停用的供应商跳过
                for (provider in providers) {
                    if (provider.id == "custom") continue
                    if (!isProviderEnabled(provider.id)) continue
                    if (modelAvailable(provider.id, actual)) {
                        pool.available(provider.id).forEach { e ->
                            routes.add(Route(e.account, actual, "${provider.id}:${e.account.uid}"))
                        }
                    }
                }
            }
            // 自定义中转站兜底：接受任意模型名
            if (isProviderEnabled("custom")) {
                pool.available("custom").forEach { e ->
                    routes.add(Route(e.account, actual, "custom:${e.account.uid}"))
                }
            }
            return routes
        }
        if (prefix == "codebuddy" || prefix == "codebuddy-cn" || prefix == "codebuddy-global") {
            if (!isProviderEnabled(CodeBuddyProvider.ID)) return routes
            // 显式带区域后缀优先；裸前缀时看供应商的调用区域偏好（弹层里切换）
            val wantRegion = when (prefix) {
                "codebuddy-cn" -> CodeBuddyProvider.REGION_CN
                "codebuddy-global" -> CodeBuddyProvider.REGION_GLOBAL
                else -> regionPref(CodeBuddyProvider.ID)
            }
            val regionAware = providerById[CodeBuddyProvider.ID] as? RegionAwareSupport
            pool.available(CodeBuddyProvider.ID).forEach { e ->
                val region = regionAware?.regionOf(e.account)
                if (wantRegion == null || region == wantRegion) {
                    routes.add(Route(e.account, actual, "codebuddy:${e.account.uid}"))
                }
            }
            return routes
        }
        if (prefix == "trae" || prefix == "trae-cn" || prefix == "trae-global") {
            if (!isProviderEnabled(TraeProvider.ID)) return routes
            // 显式带区域后缀优先；裸前缀时看供应商的调用区域偏好（弹层里切换）
            val wantRegion = when (prefix) {
                "trae-cn" -> "cn"
                "trae-global" -> "global"
                else -> regionPref(TraeProvider.ID)
            }
            val regionAware = providerById[TraeProvider.ID] as? RegionAwareSupport
            pool.available(TraeProvider.ID).forEach { e ->
                val region = regionAware?.regionOf(e.account)
                if (wantRegion == null || region == wantRegion) {
                    routes.add(Route(e.account, actual, "trae:${e.account.uid}"))
                }
            }
            return routes
        }
        if (prefix == DeepSeekProvider.ID) {
            if (!isProviderEnabled(DeepSeekProvider.ID)) return routes
            pool.available(DeepSeekProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "deepseek:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == LoomyProvider.ID) {
            if (!isProviderEnabled(LoomyProvider.ID)) return routes
            pool.available(LoomyProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "loomy:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == MimoProvider.ID) {
            if (!isProviderEnabled(MimoProvider.ID)) return routes
            pool.available(MimoProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "mimo:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == RaccoonProvider.ID) {
            if (!isProviderEnabled(RaccoonProvider.ID)) return routes
            pool.available(RaccoonProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "raccoon:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == ChatglmProvider.ID) {
            if (!isProviderEnabled(ChatglmProvider.ID)) return routes
            pool.available(ChatglmProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "chatglm:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == DoubaoProvider.ID) {
            if (!isProviderEnabled(DoubaoProvider.ID)) return routes
            if (!modelAvailable(DoubaoProvider.ID, actual)) return routes
            pool.available(DoubaoProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "doubao:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == JimengProvider.ID) {
            if (!isProviderEnabled(JimengProvider.ID)) return routes
            if (!modelAvailable(JimengProvider.ID, actual)) return routes
            pool.available(JimengProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "jimeng:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == ChatGptProvider.ID) {
            if (!isProviderEnabled(ChatGptProvider.ID)) return routes
            if (!modelAvailable(ChatGptProvider.ID, actual)) return routes
            pool.available(ChatGptProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "chatgpt:${e.account.uid}"))
            }
            return routes
        }
        if (prefix == FreePoolProvider.ID) {
            if (!isProviderEnabled(FreePoolProvider.ID)) return routes
            // 免费池下挂多个渠道，模型名是「渠道/模型」两级；
            // 必须校验该渠道确实有这个模型，否则会拿 llm7 的模型去打 ovh。
            if (!modelAvailable(FreePoolProvider.ID, actual)) return routes
            pool.available(FreePoolProvider.ID).forEach { e ->
                routes.add(Route(e.account, actual, "freepool:${e.account.uid}"))
            }
            return routes
        }
        Log.debug("未识别的供应商前缀: $prefix")
        return routes
    }

    /**
     * 路由为空且原因只是「启用账号全在失败冷却」时，等最近的冷却到期再试一次（上限 120s，
     * 与并发闸门一致）。上下文压缩这类大请求容易把账号连续打进冷却，客户端随即重试只
     * 会拿到瞬时 404（客户端错误不重置冷却），会话就此卡死；等冷却则能自愈。
     * 每次轮询间重新解析，用户在界面重新启用账号后请求立即继续。
     */
    private fun awaitCooldown(model: String): List<Route> {
        val slash = model.indexOf('/')
        val prefix = if (slash > 0) model.substring(0, slash) else return emptyList()
        val providerId = prefixedProviderId(prefix) ?: return emptyList()
        if (!isProviderEnabled(providerId)) return emptyList()
        val deadline = System.currentTimeMillis() + 120_000
        while (true) {
            val routes = resolveCandidates(model)
            if (routes.isNotEmpty()) return routes
            val cooling = pool.enabled(providerId).filter { it.state.inCooldown() }
            if (pool.enabled(providerId).any { !it.state.inCooldown() } || cooling.isEmpty()) return emptyList()
            val wait = minOf(
                cooling.minOf { it.state.cooldownUntil } - System.currentTimeMillis(),
                deadline - System.currentTimeMillis(),
            )
            if (wait <= 0) return resolveCandidates(model)
            try {
                Thread.sleep(minOf(wait, 2_000))
            } catch (_: InterruptedException) {
                return emptyList()
            }
        }
    }

    /** 供应商前缀（codebuddy-cn / trae-global 等）对应的基础供应商 id；不识别则 null。 */
    private fun prefixedProviderId(prefix: String): String? = when {
        prefix.startsWith("custom") -> "custom"
        prefix.startsWith("codebuddy") -> CodeBuddyProvider.ID
        prefix.startsWith("trae") -> TraeProvider.ID
        prefix == DeepSeekProvider.ID -> DeepSeekProvider.ID
        prefix == LoomyProvider.ID -> LoomyProvider.ID
        prefix == MimoProvider.ID -> MimoProvider.ID
        prefix == RaccoonProvider.ID -> RaccoonProvider.ID
        prefix == ChatglmProvider.ID -> ChatglmProvider.ID
        prefix == FreePoolProvider.ID -> FreePoolProvider.ID
        prefix == DoubaoProvider.ID -> DoubaoProvider.ID
        prefix == JimengProvider.ID -> JimengProvider.ID
        prefix == ChatGptProvider.ID -> ChatGptProvider.ID
        else -> null
    }

    /** 路由为空时的人话解释：逐个说明供应商停用 / 账号禁用（含原因）/ 冷却剩余时间。 */
    private fun unavailableReason(model: String): String {
        val slash = model.indexOf('/')
        val prefix = if (slash > 0) model.substring(0, slash) else null
        val providerIds = if (prefix == null) {
            providers.map { it.id }
        } else {
            listOf(prefixedProviderId(prefix) ?: return "未识别的供应商前缀 '$prefix'")
        }
        val parts = providerIds.map { id ->
            if (!isProviderEnabled(id)) return@map "$id 已停用"
            val accounts = pool.accounts(id)
            if (accounts.isEmpty()) return@map "$id 未添加账号"
            val disabled = accounts.filter { !it.state.enabled }
            val cooling = accounts.filter { it.state.enabled && it.state.inCooldown() }
            val ready = accounts.size - disabled.size - cooling.size
            when {
                ready > 0 -> {
                    val regionTag = if (prefix != null && prefix != id) "该区域（$prefix）" else ""
                    "$id ${regionTag}有 $ready 个账号可用但未匹配到该模型"
                }
                disabled.isNotEmpty() -> {
                    val reason = disabled.first().state.disabledReason?.take(60)
                    "$id 全部 ${accounts.size} 个账号已禁用" +
                        (reason?.let { "（如：$it）" } ?: "")
                }
                cooling.isNotEmpty() -> {
                    val waitSec = (cooling.minOf { it.state.cooldownUntil } - System.currentTimeMillis()) / 1000
                    "$id 全部 ${accounts.size} 个账号冷却中，约 ${maxOf(waitSec, 1)}s 后恢复"
                }
                else -> "$id 无可用账号"
            }
        }
        return parts.joinToString("；")
    }

    // ---------- 模型目录匹配 ----------

    /** 该供应商的启用账号里是否有这个模型（优先缓存，缺失时实时拉一次目录）。 */
    private fun modelAvailable(providerId: String, model: String): Boolean {
        val accounts = pool.available(providerId)
        if (accounts.isEmpty()) return false
        val cached = accounts.any { e ->
            modelCache["$providerId:${e.account.uid}"]?.models?.any { it.id == model } == true
        }
        if (cached) return true
        val provider = providerById[providerId] ?: return false
        for (e in accounts) {
            val models = catalogOf(provider, e.account)
            modelCache["$providerId:${e.account.uid}"] = ModelCacheEntry(System.currentTimeMillis(), models)
            if (models.any { it.id == model }) return true
        }
        return false
    }

    /** 供应商能给出展示信息就用它，否则退化成只有 id 的列表。 */
    private fun catalogOf(provider: Provider, account: ProviderAccount): List<ModelInfo> {
        if (provider is ModelCatalogSupport) {
            // modelCatalog 自己吞掉错误返回空表，失败时这里补一条日志：
            // 否则「挂代理后海外模型消失」看起来像模型不存在，而不是接口挂了
            val models = provider.modelCatalog(account)
            if (models.isEmpty()) {
                eventLog.warn("模型", "${provider.id} · ${account.displayName} 模型目录为空（凭证失效或网络不通）")
            }
            return models
        }
        val models = provider.listModels(account).getOrElse {
            eventLog.warn("模型", "${provider.id} 模型列表获取失败：${it.message}")
            return emptyList()
        }.map { ModelInfo(id = it) }
        if (models.isEmpty()) {
            eventLog.warn("模型", "${provider.id} · ${account.displayName} 模型列表为空")
        }
        return models
    }

    /** 支持签到/积分查询的供应商 id 列表。 */
    fun activityProviderIds(): List<String> =
        providers.filter { it is AccountActivitySupport }.map { it.id }

    // ---------- 浏览器回调登录（Trae） ----------

    /** 浏览器登录票据：[loginUrl] 打开授权页，[port] 是本地回调端口。 */
    data class BrowserLoginTicket(val loginUrl: String, val port: Int)

    private var callbackServer: CallbackServer? = null

    /** 最近一次回调登录的结果文案；null 表示还没收到回调。 */
    @Volatile
    var browserLoginResult: String? = null
        private set

    @Volatile
    private var browserLoginOk = false

    /** 进行中的回调登录：供应商标识与已完成的账号，供 WebView 直接交回调时使用。 */
    @Volatile
    private var browserLoginProviderId: String? = null

    @Volatile
    private var browserLoginAccount: ProviderAccount? = null

    fun browserLoginSucceeded(): Boolean = browserLoginOk

    /**
     * 开始浏览器登录：临时监听本地 51120 接住 Trae 的回调。
     * 收到回调后换 token、存账号，并把结果写进 [browserLoginResult] 供界面轮询。
     */
    fun startBrowserLogin(providerId: String, region: String): BrowserLoginTicket {
        val provider = providerById[providerId] as? WebCallbackSupport
            ?: error("供应商 $providerId 不支持浏览器回调登录")
        stopCallbackServer()
        browserLoginResult = null
        browserLoginOk = false
        browserLoginProviderId = providerId
        browserLoginAccount = null

        val port = provider.callbackPort
        val path = provider.callbackPath
        val loginUrl = provider.beginWebLogin(region, "http://127.0.0.1:$port$path")

        lateinit var serverRef: CallbackServer
        val server = try {
            CallbackServer(port, path) { url ->
                // 与 WebView 直交回调走同一套逻辑：两条入口可能同时到，靠幂等去重
                runCatching { completeBrowserLogin(url) }
                // 回调页面已生成，稍等响应写回后释放端口，避免下次登录 EADDRINUSE
                if (callbackServer === serverRef) callbackServer = null
                Thread {
                    runCatching { Thread.sleep(1_500) }
                    runCatching { serverRef.stop() }
                }.start()
                loginResultPage(browserLoginOk, browserLoginResult.orEmpty())
            }.also { serverRef = it; it.startServer() }
        } catch (e: Exception) {
            throw IllegalStateException("无法监听本地回调端口 $port：${e.message}", e)
        }
        callbackServer = server
        eventLog.info("登录", "已监听本地回调 http://127.0.0.1:$port$path")

        // 用户长时间不完成登录时释放端口
        Thread {
            runCatching { Thread.sleep(CALLBACK_TIMEOUT_MS) }
            if (callbackServer === server) {
                callbackServer = null
                runCatching { server.stop() }
            }
        }.start()

        return BrowserLoginTicket(loginUrl, port)
    }

    /** 同步释放回调端口：开始新登录前调用，确保 51120 可立即复用。 */
    fun stopCallbackServer() {
        val server = callbackServer ?: return
        callbackServer = null
        runCatching { server.stop() }
    }

    /**
     * 进行中的回调登录对应的本地回调基址（不含查询串）。
     * WebView 只拿到授权码时，用它拼出完整回调 URL 去换凭证。
     */
    fun browserCallbackBase(): String? {
        val providerId = browserLoginProviderId ?: return null
        val provider = providerById[providerId] as? WebCallbackSupport ?: return null
        return "http://127.0.0.1:${provider.callbackPort}${provider.callbackPath}"
    }

    /**
     * 由登录页（WebView）直接拿到的回调 URL 完成登录。
     *
     * 与 [CallbackServer] 是两条并行入口：上游有些授权页用 `window.open` 或
     * history API 跳转，本机回调服务根本收不到，页面只会白屏。WebView 自带导航
     * 钩子能稳定拿到这次跳转，所以两边共用同一套换凭证逻辑。
     *
     * 幂等：同一轮登录已经成功时直接返回已有账号，避免回调服务与 WebView 重复换码
     * 把 refresh_token 轮换两次。
     */
    @Synchronized
    /**
     * 用粘贴回来的回调地址完成登录。
     *
     * 与 [completeBrowserLogin] 的区别：不依赖「本机回调服务已收到请求」这件事，
     * 也不要求登录流程还活着（进程重启过也能用）。
     *
     * 支持两种输入：完整 URL，或只给 code（此时用最近一次的登录上下文）。
     */
    fun pasteBrowserCallback(raw: String): ProviderAccount {
        // 进程可能重启过：没有进行中的登录时默认按 ChatGPT 处理（唯一提供粘贴入口的供应商）
        val providerId = browserLoginProviderId ?: "chatgpt"
        val provider = providerById[providerId] as? WebCallbackSupport
            ?: throw IllegalStateException("供应商 $providerId 不支持回调登录")

        val text = raw.trim()
        val url = when {
            text.startsWith("http") -> text
            // 只给了 code：补成完整回调地址，state 由供应商自己找回
            else -> "http://localhost:${provider.callbackPort}${provider.callbackPath}?code=$text"
        }
        val account = try {
            provider.completeWebLogin(url)
        } catch (e: Exception) {
            eventLog.error("登录", "粘贴回调失败：${e.message}")
            throw e
        }
        pool.add(account)
        browserLoginAccount = account
        browserLoginOk = true
        browserLoginResult = account.displayName
        eventLog.success("登录", "登录成功：${account.displayName}")
        return account
    }

    fun completeBrowserLogin(callbackUrl: String): ProviderAccount {
        browserLoginAccount?.let { return it }
        val providerId = browserLoginProviderId ?: throw IllegalStateException("没有进行中的浏览器登录")
        val provider = providerById[providerId] as? WebCallbackSupport
            ?: throw IllegalStateException("供应商 $providerId 不支持浏览器回调登录")
        val account = try {
            provider.completeWebLogin(callbackUrl)
        } catch (e: Exception) {
            browserLoginOk = false
            browserLoginResult = e.message ?: "登录失败"
            eventLog.error("登录", "登录失败：${e.message}")
            throw e
        }
        pool.add(account)
        browserLoginAccount = account
        browserLoginOk = true
        browserLoginResult = account.displayName
        eventLog.success("登录", "登录成功：${account.displayName}")
        return account
    }

    // ---------- 网页 token 登录（DeepSeek） ----------

    /** 登录页地址（由 WebView 打开，登录完成后从页面里取 token）。 */
    fun startTokenLogin(providerId: String): String {
        val provider = providerById[providerId] as? WebTokenSupport
            ?: error("供应商 $providerId 不支持网页 token 登录")
        eventLog.info("登录", "打开 $providerId 登录页")
        return provider.loginPageUrl
    }

    /** 用页面里取到的 token 换账号并写入账号池。 */
    fun completeTokenLogin(providerId: String, rawToken: String): ProviderAccount {
        val provider = providerById[providerId] as? WebTokenSupport
            ?: error("供应商 $providerId 不支持网页 token 登录")
        val account = try {
            provider.loginWithToken(rawToken)
        } catch (e: Exception) {
            eventLog.error("登录", "登录失败：${e.message}")
            throw e
        }
        pool.add(account)
        eventLog.success("登录", "登录成功：${account.displayName}")
        return account
    }

    // ---------- 设备授权登录 ----------

    fun startDeviceAuth(providerId: String, region: String): DeviceAuthTicket {
        val provider = providerById[providerId] as? DeviceCodeSupport
            ?: error("供应商 $providerId 不支持设备授权登录")
        eventLog.info("登录", "请求授权链接（$providerId / $region）")
        return try {
            provider.startDeviceAuth(region).also { eventLog.success("登录", "已获取授权链接") }
        } catch (e: Exception) {
            eventLog.error("登录", "获取授权链接失败：${e.message}")
            throw e
        }
    }

    fun pollDeviceAuth(providerId: String, state: String, region: String): DeviceAuthPoll {
        val provider = providerById[providerId] as? DeviceCodeSupport
            ?: return DeviceAuthPoll.Failed("供应商 $providerId 不支持设备授权登录")
        val result = provider.pollDeviceAuth(state, region)
        when (result) {
            is DeviceAuthPoll.Success -> eventLog.success("登录", "登录成功：${result.account.displayName}")
            is DeviceAuthPoll.Failed -> eventLog.error("登录", "登录失败：${result.error}")
            DeviceAuthPoll.Pending -> Unit // 未授权属于正常轮询，不写日志
        }
        return result
    }

    /** 新增账号（登录成功后调用）。 */
    fun saveAccount(account: ProviderAccount) {
        pool.add(account)
    }

    /** 粘贴凭证导入账号（Trae 等支持直接换 token 的供应商）。 */
    fun importCredentials(providerId: String, raw: String): ProviderAccount {
        val provider = providerById[providerId] as? CredentialImportSupport
            ?: error("供应商 $providerId 不支持凭证导入")
        val account = try {
            provider.importCredentials(raw)
        } catch (e: Exception) {
            eventLog.error("登录", "凭证导入失败：${e.message}")
            throw e
        }
        pool.add(account)
        eventLog.success("登录", "凭证导入成功：${account.displayName}")
        return account
    }

    // ---------- 账号迁移信封导入 ----------

    /**
     * 单账号导出的「最小凭证」（`{access_token, refresh_token}`，snake_case）。
     * 给 com.wb.api 等只认这两个字段的第三方工具粘贴用；拿不到 refreshToken 时只给 access_token。
     */
    fun exportMinimalCredential(providerId: String, uid: String): String {
        val account = pool.get(providerId, uid)?.account
            ?: throw IllegalArgumentException("账号不存在")
        val obj = runCatching { JsonParser.parseString(account.secret).asJsonObject }.getOrNull()
            ?: JsonObject()
        val access = obj.firstString("accessToken", "access_token")
        val refresh = obj.firstString("refreshToken", "refresh_token")
        if (access.isEmpty() && refresh.isEmpty()) {
            throw IllegalArgumentException("该账号没有可导出的 access_token / refresh_token")
        }
        return JsonObject().apply {
            addProperty("access_token", access)
            addProperty("refresh_token", refresh)
        }.toString()
    }

    /**
     * 导出某个供应商的全部账号（迁移信封）。
     *
     * [providerId] 为空时导出全部供应商的账号。
     * 凭证、apiKey、baseUrl 一并写入，导出的文件能原样导回。
     */
    fun exportAccountTransfer(providerId: String? = null): String {
        val entries = if (providerId.isNullOrBlank()) pool.all() else pool.accounts(providerId)
        if (entries.isEmpty()) {
            throw IllegalArgumentException(
                if (providerId.isNullOrBlank()) "当前没有可导出的账号" else "该供应商下没有账号",
            )
        }
        val json = AccountTransfer.encode(entries.map { it.account })
        eventLog.success("账号", "已导出 ${entries.size} 个账号")
        return json
    }

    /**
     * 导入账号迁移信封：直接落池，不经过凭证解析/联网校验，token 失效也能恢复账号。
     * [overwrite] 为 true 时同 (providerId, uid) 的已有账号被覆盖，否则跳过。
     * 未注册的 providerId 不落池，单独报出来。
     */
    fun importAccountTransfer(
        json: String,
        overwrite: Boolean = true,
        selection: Set<String> = emptySet(),
    ): AccountImportResult {
        val accounts = AccountTransfer.decode(json, allowLegacy = true)
        val picked = if (selection.isEmpty()) {
            accounts
        } else {
            accounts.filter { "${it.providerId}/${it.uid}" in selection }
        }
        val added = mutableListOf<String>()
        val updated = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val unknown = mutableListOf<String>()
        for (account in picked) {
            val key = "${account.providerId}/${account.uid}"
            if (providerById[account.providerId] == null) {
                unknown.add(key)
                continue
            }
            val existing = pool.get(account.providerId, account.uid)
            when {
                existing == null -> {
                    pool.add(account)
                    added.add(key)
                }
                overwrite -> {
                    pool.updateAccount(account)
                    updated.add(key)
                }
                else -> skipped.add(key)
            }
        }
        if (added.isNotEmpty() || updated.isNotEmpty()) {
            eventLog.success(
                "账号",
                "信封导入：新增 ${added.size}、覆盖 ${updated.size}、跳过 ${skipped.size}",
            )
        }
        return AccountImportResult(added, updated, skipped, unknown)
    }

    /** 账号所在区域（用于 UI 展示与区域筛选）。 */
    fun regionOf(account: ProviderAccount): String? =
        (providerById[account.providerId] as? RegionAwareSupport)?.regionOf(account)

    // ---------- 短信登录（Loomy） ----------

    /** 发送短信验证码，返回上游 msgid（登录时回传）。 */
    fun sendSmsCode(providerId: String, phone: String, ccode: String = "86"): String {
        val provider = providerById[providerId] as? SmsLoginSupport
            ?: error("供应商 $providerId 不支持短信登录")
        return try {
            provider.sendSmsCode(phone, ccode)
        } catch (e: Exception) {
            eventLog.error("登录", "发送验证码失败：${e.message}")
            throw e
        }
    }

    /** 用短信验证码换账号并写入账号池。 */
    fun completeSmsLogin(providerId: String, phone: String, code: String, msgid: String, ccode: String = "86"): ProviderAccount {
        val provider = providerById[providerId] as? SmsLoginSupport
            ?: error("供应商 $providerId 不支持短信登录")
        val account = try {
            provider.loginBySmsCode(phone, code, msgid, ccode)
        } catch (e: Exception) {
            eventLog.error("登录", "登录失败：${e.message}")
            throw e
        }
        pool.add(account)
        eventLog.success("登录", "登录成功：${account.displayName}")
        return account
    }

    // ---------- 供应商专属动作 ----------

    /** 执行供应商专属动作（兑换码、任务等）；成功后顺带刷新余额。 */
    fun performAction(providerId: String, uid: String, action: String, payload: JsonObject = JsonObject()): ProviderActionResult {
        val provider = providerById[providerId] as? ProviderActionSupport
            ?: return ProviderActionResult.unsupported(action)
        val entry = pool.get(providerId, uid)
            ?: return ProviderActionResult.failure("账号不存在")
        val result = provider.performAction(entry.account, action, payload)
        if (result.ok) {
            runCatching { refreshBalance(entry.account) }
        }
        return result
    }

    // ---------- 对话（含 Failover） ----------

    /** 请求摘要：日志里先看清客户端到底发了什么，再谈路由。 */
    private fun describeMessages(body: JsonObject): String {
        val messages = body.arrayOrNull("messages") ?: return "无 messages"
        val roles = mutableMapOf<String, Int>()
        val parts = mutableMapOf<String, Int>()
        var attachments = 0
        for (element in messages) {
            val message = element as? JsonObject ?: continue
            val role = message.get("role")?.asString ?: "unknown"
            roles[role] = (roles[role] ?: 0) + 1
            val content = message.get("content")
            if (content != null && content.isJsonArray) {
                for (part in content.asJsonArray) {
                    val type = (part as? JsonObject)?.get("type")?.asString ?: "unknown"
                    parts[type] = (parts[type] ?: 0) + 1
                    if (type == "image_url" || type == "image" || type == "file" || type == "input_file") {
                        attachments++
                    }
                }
            }
        }
        val roleText = roles.entries.joinToString(" / ") { "${it.key} ${it.value}" }
        val partText = if (parts.isEmpty()) "纯文本" else parts.entries.joinToString(",") { "${it.key}×${it.value}" }
        val toolCount = body.arrayOrNull("tools")?.size() ?: 0
        return "消息 ${messages.size()} 条（$roleText）· 内容 $partText · 工具 $toolCount 个" +
            if (attachments > 0) " · 附件 $attachments 个" else ""
    }

    sealed class Outcome {
        class Ok(val result: ChatResult) : Outcome()
        class Error(val error: ApiError, val attempts: List<String>) : Outcome()
    }

    /** 单个模型的探测结果：能否调用、失败时上游给的原因。 */
    data class ProbeResult(val model: String, val ok: Boolean, val message: String)

    /**
     * 探测模型是否真的能调（用最小请求，不产生有意义计费）。
     *
     * 模型能出现在目录里不等于账号能用它：上游会按账号等级拒绝（如
     * `not allowed for user type [personal_free_user]`），而目录接口并不返回该限制。
     * 只能实际发一次请求才能确认，所以做成显式探测而不是每次拉列表都跑。
     */
    fun probeModel(model: String): ProbeResult {
        val body = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_completion_tokens", 24)
            // 非流式：探测只要一个结果，流式会多出 SSE 收尾状态
            addProperty("stream", false)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "hi")
                })
            })
        }
        return when (val outcome = chat(body, null)) {
            is Outcome.Ok -> ProbeResult(model, true, "").also {
                probeCache[model] = it
                eventLog.success("模型", "探测 $model：可用")
            }
            is Outcome.Error -> ProbeResult(model, false, probeMessageOf(outcome.error)).also {
                probeCache[model] = it
                eventLog.error("模型", "探测 $model：不可用（${it.message}）")
            }
        }
    }

    /** 已探测过的模型结果（未探测的不在表里）。 */
    fun probeResults(): Map<String, ProbeResult> = probeCache.toMap()

    /** 清空探测缓存（账号或供应商变动后调用）。 */
    fun clearProbeResults() {
        probeCache.clear()
    }

    /** 把上游错误翻成一句能判断该换模型还是换账号的结论。 */
    private fun probeMessageOf(error: ApiError): String {
        val msg = error.message
        // 账号等级限制：同区域其他模型可能仍可用，不是网络/凭证问题。
        // 上游对同一件事有两种措辞，都要识别，否则检测会把不可用模型标成可用。
        if (msg.contains("is not allowed for user type", ignoreCase = true)) {
            val userType = Regex("user type \\[([^\\]]+)]").find(msg)?.groupValues?.getOrNull(1).orEmpty()
            return if (userType.isEmpty()) "账号等级不支持该模型" else "账号等级 $userType 不支持该模型"
        }
        if (msg.contains("only available for authorized users", ignoreCase = true)) {
            return "该模型需要更高权限（当前账号无权使用）"
        }
        if (msg.contains("service info not found", ignoreCase = true)) {
            return "该区域账号没有此模型"
        }
        return msg.ifEmpty { "HTTP ${error.status}" }
    }

    fun chat(bodyIn: JsonObject, sink: SseSink?, sessionId: String? = null): Outcome {
        // 1) 别名先做：它改的是模型名，必须在路由之前生效，
        //    否则客户端硬编码的 gpt-4o 会直接 404
        val rawModel = bodyIn.get("model")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty()
        val aliased = RequestRewriter.resolveAlias(rawModel, settings)
        if (aliased != rawModel) {
            eventLog.debug("别名", "$rawModel → $aliased")
            bodyIn.addProperty("model", aliased)
        }

        // 2) 统一在入口改写请求体：所有供应商、所有客户端都走这里，
        //    放上游各自实现会漏掉一半
        val body = RequestRewriter.apply(bodyIn, settings)
        val model = body.get("model")?.asString?.trim().orEmpty()
        if (model.isEmpty()) {
            return Outcome.Error(
                ApiError(ApiError.Kind.CLIENT, 400, "field 'model' is required"),
                emptyList(),
            )
        }
        val stream = body.get("stream")?.asBoolean ?: false
        eventLog.debug("请求", "$model · ${if (stream) "流式" else "非流式"} · ${describeMessages(body)}")
        val routes = resolveCandidates(model).ifEmpty { awaitCooldown(model) }
        if (routes.isEmpty()) {
            val detail = unavailableReason(model)
            eventLog.error("调用", "$model 无可用账号：$detail")
            return Outcome.Error(
                ApiError(ApiError.Kind.CLIENT, 404, "no available account for model '$model'（$detail）"),
                emptyList(),
            )
        }
        // 模型级冷却 / 供应商熔断：两类都是「换个号也没用」，提前过滤掉，
        // 否则每个请求都要把已知不可用的候选重试一遍
        val healthy = routes.filter { route ->
            !modelCooldowns.isCooling(route.account.providerId, route.actualModel) &&
                !breakerOf(route.account.providerId).isBlocked()
        }
        if (healthy.isEmpty()) {
            val cooling = routes.map { route ->
                val key = "${route.account.providerId}|${route.actualModel}"
                val remain = modelCooldowns.snapshot()[key]
                if (remain != null) "$key 冷却中（剩 ${remain / 1000}s）" else "${route.account.providerId} 熔断中"
            }.distinct().joinToString("；")
            eventLog.warn("调用", "$model 候选全部冷却/熔断：$cooling")
            return Outcome.Error(
                ApiError(ApiError.Kind.UPSTREAM, 429, "model '$model' 暂时不可用：$cooling"),
                emptyList(),
            )
        }
        val candidates = if (settings.sessionSticky && !sessionId.isNullOrBlank()) {
            preferSticky(healthy, sessionId)
        } else {
            healthy
        }
            // 有容量的账号排前面：避免所有请求先排到同一个满载的热号上（会拉高首字延迟）
            .sortedByDescending { gateFor(it.account).availablePermits() > 0 }
            // 再按用户选的策略微调：上面只保证「有容量优先」，
            // 同容量的多个号怎么排由策略决定
            .let { sorted ->
                AccountSelector.order(
                    routes = sorted,
                    key = model,
                    strategy = settings.accountStrategy,
                    remainOf = { route ->
                        pool.get(route.account.providerId, route.account.uid)?.state?.balance
                            ?.toLong()
                    },
                    usageOf = { route ->
                        // PACE 用「今天该账号被调用的次数」近似消耗速度：
                        // 调用越多说明消耗越快，越应该往后放
                        callLog.todayCountOf(route.account.providerId, route.account.uid).toLong()
                    },
                )
            }
        eventLog.debug("路由", "$model 候选 ${candidates.size} 个：${candidates.joinToString(" → ") { it.via }}")
        activeRequests++

        val start = System.currentTimeMillis()
        var lastErr: ApiError? = null
        val attempts = mutableListOf<String>()

        for (route in candidates) {
            val provider = providerById[route.account.providerId] ?: continue
            val gate = gateFor(route.account)
            if (!gate.tryAcquire(120, TimeUnit.SECONDS)) {
                attempts += "${route.via}: 并发占满（排队超时）"
                eventLog.warn("路由", "${route.via} 并发占满，跳过")
                continue
            }
            val globalAcquired = tryAcquireGlobal(120_000)
            if (settings.maxConcurrentGlobal > 0 && !globalAcquired) {
                gate.release()
                attempts += "${route.via}: 全局并发占满（排队超时）"
                eventLog.warn("路由", "${route.via} 全局并发占满，跳过")
                continue
            }
            eventLog.debug("路由", "尝试 ${route.via}（${route.account.displayName}）")
            try {
                val upstreamBody = body.deepCopy().apply { addProperty("model", route.actualModel) }
                val result = provider.chat(
                    route.account,
                    ChatRequest(route.actualModel, upstreamBody, stream),
                    sink,
                )
                when (result) {
                    is ChatResult.Streamed, is ChatResult.Aggregated -> {
                        val usage = usageOf(result)
                        val elapsed = System.currentTimeMillis() - start
                        callLog.record(
                            model = model, via = route.via, ok = true,
                            latencyMs = elapsed,
                            detail = if (result is ChatResult.Streamed) "streamed ${result.events} events" else "json",
                            inputTokens = usage.input,
                            outputTokens = usage.output,
                            cachedTokens = usage.cached,
                        )
                        eventLog.success(
                            "调用",
                            "$model · ${route.via} · ${elapsed}ms" +
                                (if (usage.input + usage.output > 0) " · token ${usage.input}/${usage.output}" else "") +
                                toolReplyInfo(result),
                        )
                        pool.markSuccess(route.account.providerId, route.account.uid)
                        breakerOf(route.account.providerId).recordSuccess()
                        modelCooldowns.clear(route.account.providerId, route.actualModel)
                        if (settings.sessionSticky && !sessionId.isNullOrBlank()) {
                            sessionPins[sessionId] = route.via
                        }
                        markBubble("结束")
                        activeRequests--
                        return Outcome.Ok(result)
                    }
                    is ChatResult.Failure -> {
                        val err = result.error
                        lastErr = err
                        attempts += "${route.via}: ${err.kind} ${err.status}"
                        eventLog.error("调用", "$model · ${route.via} 失败：${err.kind} ${err.status} ${err.message}")
                        if (err.isAccountDead()) {
                            pool.setDisabled(route.account.providerId, route.account.uid, err.message)
                            eventLog.warn("账号", "${route.via} 凭证失效，已禁用：${err.message}")
                            Log.warn("账号 ${route.via} 凭证失效，已禁用：${err.message}")
                        } else if (err.kind != ApiError.Kind.CLIENT) {
                            val entered = pool.recordFailure(
                                route.account.providerId, route.account.uid,
                                settings.failThreshold, settings.cooldownSeconds * 1000L,
                            )
                            if (entered) {
                                eventLog.warn("账号", "${route.via} 连续失败达 ${settings.failThreshold} 次，冷却 ${settings.cooldownSeconds}s")
                            }
                        }
                        // 上游整体性失败（5xx/网络）计入供应商熔断；客户端 4xx 与凭证失效不算
                        if (err.kind == ApiError.Kind.UPSTREAM || err.kind == ApiError.Kind.NETWORK) {
                            breakerOf(route.account.providerId).recordFailure()
                        }
                        // 模型维度错误：换号也没用，冷却该「供应商+模型」
                        if (isModelLevelError(err)) {
                            modelCooldowns.markCooling(
                                route.account.providerId, route.actualModel,
                                settings.modelCooldownSeconds * 1000L,
                            )
                            eventLog.warn(
                                "模型",
                                "${route.account.providerId}/${route.actualModel} 冷却 ${settings.modelCooldownSeconds}s：${err.message.take(120)}",
                            )
                        }
                        // 客户端 4xx 与账号无关，不换号直接回
                        if (err.kind == ApiError.Kind.CLIENT) {
                            callLog.record(model = model, via = route.via, ok = false,
                                latencyMs = System.currentTimeMillis() - start, detail = err.message)
                            markBubble("失败")
                            activeRequests--
                            return Outcome.Error(err, attempts)
                        }
                        // 该账号失败，还有其它候选时继续尝试：悬浮球标记「换号」
                        markBubble("换号")
                    }
                }
            } catch (e: Exception) {
                val err = Http.networkError(e)
                lastErr = err
                attempts += "${route.via}: 异常 ${e.javaClass.simpleName}: ${e.message}"
                markBubble("失败")
                eventLog.error(
                    "调用",
                    "$model · ${route.via} 异常：${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString().take(1200)}",
                )
                pool.recordFailure(
                    route.account.providerId, route.account.uid,
                    settings.failThreshold, settings.cooldownSeconds * 1000L,
                )
                breakerOf(route.account.providerId).recordFailure()
            } finally {
                gate.release()
                if (globalAcquired) releaseGlobal()
            }
        }
        callLog.record(model = model, via = candidates.first().via, ok = false,
            latencyMs = System.currentTimeMillis() - start, detail = lastErr?.message ?: "无可用账号")
        eventLog.error("调用", "$model 无可用账号或全部失败：${lastErr?.message ?: "no available account"}")
        // 全部失败：按错误信息区分「模型上限」与普通失败
        val limitHit = lastErr?.message?.let { m ->
            m.contains("上限", ignoreCase = true) ||
                m.contains("limit", ignoreCase = true) ||
                m.contains("429", ignoreCase = true) ||
                m.contains("quota", ignoreCase = true) ||
                m.contains("额度", ignoreCase = true)
        } == true
        markBubble(if (limitHit) "模型上限" else "失败")
        activeRequests--
        val finalErr = lastErr ?: ApiError(ApiError.Kind.UPSTREAM, 502, "no available account")
        return Outcome.Error(ApiError(finalErr.kind, 502, finalErr.message), attempts)
    }

    /** 聚合响应里是否带了工具调用，用于确认 DSML 转换是否生效。 */
    private fun toolReplyInfo(result: ChatResult): String {
        if (result !is ChatResult.Aggregated) return ""
        val count = runCatching {
            result.json.getAsJsonArray("choices")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("message")
                ?.getAsJsonArray("tool_calls")?.size() ?: 0
        }.getOrDefault(0)
        return if (count > 0) " · 工具调用 $count" else ""
    }

    /** 会话粘黏：已绑定的账号仍在候选里时，把它挪到最前优先尝试。 */
    private fun preferSticky(routes: List<Route>, sessionId: String): List<Route> {
        val pin = sessionPins[sessionId] ?: return routes
        val idx = routes.indexOfFirst { it.via == pin }
        if (idx <= 0) return routes
        val reordered = routes.toMutableList()
        reordered.add(0, reordered.removeAt(idx))
        return reordered
    }

    // ---------- 模型目录（流式/聚合均可，含缓存） ----------

    /** 从结果里取 token 用量：流式取 Provider 解析的 usage，聚合取响应 JSON 的 usage。 */
    private fun usageOf(result: ChatResult): UsageTokens {
        if (result is ChatResult.Streamed) {
            return UsageTokens(result.inputTokens, result.outputTokens, result.cachedTokens)
        }
        if (result !is ChatResult.Aggregated) return UsageTokens()
        val usage = result.json.get("usage")?.takeIf { it.isJsonObject }?.asJsonObject ?: return UsageTokens()
        fun pick(vararg keys: String): Int {
            for (key in keys) {
                val value = usage.get(key) ?: continue
                if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) return value.asInt
            }
            return 0
        }
        return UsageTokens(
            input = pick("prompt_tokens", "input_tokens"),
            output = pick("completion_tokens", "output_tokens"),
            cached = cachedTokensOf(usage),
        )
    }

    fun listModels(forceRefresh: Boolean): JsonObject {
        val out = JsonObject().apply {
            addProperty("object", "list")
            add("data", JsonArray())
        }
        val data = out.getAsJsonArray("data")
        val now = System.currentTimeMillis()
        // 去重键包含前缀：codebuddy/x 与 codebuddy-cn/x 是两条独立条目
        val seen = HashSet<String>()
        // (供应商, 区域) → 该账号组真实可用的模型。
        // 必须按账号分别取目录：国内版与国际版的上游模型集并不相同，
        // 混在一起列会让客户端选到该区域不支持的模型（上游 400 service info not found）。
        val byRegion = LinkedHashMap<Pair<String, String>, LinkedHashMap<String, ModelInfo>>()
        val byProvider = LinkedHashMap<String, LinkedHashMap<String, ModelInfo>>()

        fun modelsOf(entry: AccountPool.Entry): List<ModelInfo> {
            val key = "${entry.account.providerId}:${entry.account.uid}"
            if (forceRefresh) modelCache.remove(key)
            val cached = modelCache[key]
            if (cached != null && now - cached.timestamp < settings.modelsCacheSeconds * 1000L) {
                return cached.models
            }
            val provider = providerById[entry.account.providerId] ?: return emptyList()
            val fetched = catalogOf(provider, entry.account)
            // 空结果不缓存：拉取失败（代理不通、瞬时超时）会返回空表，
            // 缓存下来会让该账号的模型在缓存期内一直消失，看起来像「挂代理后海外模型没了」
            if (fetched.isNotEmpty()) {
                modelCache[key] = ModelCacheEntry(now, fetched)
            }
            return fetched
        }

        for (entry in pool.all()) {
            if (!entry.state.enabled) continue
            if (entry.state.inCooldown()) continue
            val providerId = entry.account.providerId
            if (!isProviderEnabled(providerId)) continue
            val provider = providerById[providerId] ?: continue
            val models = modelsOf(entry)
            val providerBucket = byProvider.getOrPut(providerId) { LinkedHashMap() }
            for (model in models) providerBucket.putIfAbsent(model.id, model)
            val region = (provider as? RegionAwareSupport)?.regionOf(entry.account) ?: continue
            val bucket = byRegion.getOrPut(providerId to region) { LinkedHashMap() }
            for (model in models) bucket.putIfAbsent(model.id, model)
        }

        // 1) 不带区域前缀：供应商的全部模型（跨区域合集，兼容老客户端）
        for ((providerId, models) in byProvider) {
            for ((modelId, model) in models) {
                if (seen.add("$providerId/$modelId")) {
                    data.add(modelEntry("$providerId/$modelId", providerId, model))
                }
            }
        }

        // 2) 带区域前缀：只列该区域账号真实可用的模型
        for ((key, models) in byRegion) {
            val (providerId, region) = key
            val prefix = regionPrefix(providerId, region) ?: continue
            for ((modelId, model) in models) {
                if (seen.add("$prefix/$modelId")) {
                    data.add(modelEntry("$prefix/$modelId", providerId, model, region))
                }
            }
        }
        return out
    }

    /** 区域前缀（`codebuddy` + `-cn`/`-global`）；未知区域返回 null。 */
    private fun regionPrefix(providerId: String, region: String): String? = when (region) {
        CodeBuddyProvider.REGION_GLOBAL, "global" -> "$providerId-global"
        CodeBuddyProvider.REGION_CN, "cn" -> "$providerId-cn"
        else -> null
    }

    /** 按模型名推断类型（图片 / 视频 / 文本）。 */
    private fun guessModelType(modelId: String): String {
        val m = modelId.lowercase()
        return when {
            m.contains("seedream") || m.contains("image") || m.contains("draw") -> "image"
            m.contains("seedance") || m.contains("video") -> "video"
            else -> "text"
        }
    }

    private fun modelEntry(
        id: String,
        providerId: String,
        info: ModelInfo,
        region: String? = null,
    ): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("object", "model")
        addProperty("owned_by", providerId)
        // 模型类型：图片 / 视频 / 文本。客户端按这个分组展示
        addProperty("type", guessModelType(id))
        // 区域标记：客户端不必解析前缀就能分组展示
        if (region != null) addProperty("region", region)
        // 以下为可选展示信息，上游没给就不写
        if (info.displayName.isNotEmpty()) addProperty("display_name", info.displayName)
        if (info.contextWindow > 0) addProperty("context_length", info.contextWindow)
        if (info.maxOutputTokens > 0) addProperty("max_output_tokens", info.maxOutputTokens)
        if (info.tags.isNotEmpty()) {
            add("tags", JsonArray().apply { info.tags.forEach { add(it) } })
        }
    }

    // ---------- 自动路由开关 ----------

    /** 所有已注册供应商的 id（内置 + 自定义）。 */
    fun providerIds(): List<String> = providers.map { it.id }

    /** 供应商总开关：关闭后模型不进入列表，也无法通过任何前缀调用。 */
    fun isProviderEnabled(providerId: String): Boolean = providerId !in settings.disabledProviders

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        if (enabled) settings.disabledProviders.remove(providerId) else settings.disabledProviders.add(providerId)
        Settings.save(dataDir, settings)
        eventLog.info("设置", "$providerId ${if (enabled) "已启用" else "已停用"}")
    }

    // ---------- 供应商调用区域偏好 ----------

    /** 供应商当前调用区域偏好：null = 国内外自由调用。 */
    fun regionPref(providerId: String): String? = settings.regionPrefs?.get(providerId)

    /** 切换供应商调用区域：cn / global / null（自由调用）。 */
    fun setRegionPref(providerId: String, region: String?) {
        val prefs = settings.regionPrefs ?: mutableMapOf<String, String>().also { settings.regionPrefs = it }
        if (region == null) prefs.remove(providerId) else prefs[providerId] = region
        Settings.save(dataDir, settings)
        val label = when (region) {
            "cn" -> "仅用国内账号"
            "global" -> "仅用国际账号"
            else -> "国内外自由调用"
        }
        eventLog.info("设置", "$providerId 调用区域：$label")
    }

    // ---------- 远程后台（心跳 / 公告 / 更新 / 工单） ----------

    /** 工单提交：把用户反馈发到远程后台（设置页「问题反馈」用）。 */
    fun submitTicket(
        content: String,
        contactType: String,
        contact: String,
        images: List<String> = emptyList(),
        version: String = "",
    ): RemoteAdminClient.TicketResult {
        val result = adminClient.submitTicket(
            content = content,
            contactType = contactType,
            contact = contact,
            images = images,
            deviceId = deviceId,
            version = version,
        )
        if (result.ok) {
            val count = images.count { it.isNotBlank() }
            val suffix = if (count > 0) "（含 $count 张截图）" else "（无截图）"
            eventLog.success("反馈", "工单已提交：${result.message}$suffix")
        } else {
            eventLog.error("反馈", "工单提交失败：${result.message}")
        }
        return result
    }

    /**
     * 一次远程同步：校验密钥 + 心跳，返回后台下发的间隔与域名列表。
     *
     * 失败静默返回 null，绝不抛异常——上报失败不能影响网关主流程。
     */
    fun syncRemote(
        version: String,
        failedDomain: String = "",
        running: Boolean = false,
        port: Int = 0,
    ): RemoteAdminClient.Result? {
        val result = adminClient.heartbeat(deviceId, version, failedDomain, running, port)
        if (!result.ok) {
            eventLog.warn("后台", "心跳失败（code=${result.code}）：${result.message}")
            return null
        }
        return result
    }

    /** 拉取远程公告；失败返回 null。 */
    fun fetchAnnouncement(): RemoteAdminClient.Result? =
        adminClient.announcement().takeIf { it.ok }

    /** 检查更新；失败返回 null。 */
    fun checkUpdate(version: String): RemoteAdminClient.Result? {
        // VantaGate: 禁用远程更新检查，避免作者服务器推送强制更新弹窗
        @Suppress("UNUSED_EXPRESSION")
        version
        return null
    }

    /**
     * 拉一次公告与更新，把结果缓存在内存里供 UI 取用（不写日志）。
     *
     * 公告/更新是给用户看的内容，属于 UI 职责；写进运行日志既没人看也点不开。
     * 由 App 层轮询 [pendingAnnouncement] / [pendingUpdate] 并弹窗展示。
     */
    fun pollAnnouncementAndUpdate(version: String) {
        // VantaGate: 公告与强制更新都已禁用（更新推送会拦路，公告走 QQ 群）
        if (true) return
        runCatching {
            val ann = adminClient.announcement()
            if (ann.ok && ann.bool("enabled")) {
                val title = ann.string("title")
                val content = ann.string("content")
                if (title.isNotBlank() || content.isNotBlank()) {
                    val key = "$title|$content"
                    if (key != lastAnnouncementKey) {
                        lastAnnouncementKey = key
                        pendingAnnouncement = Announcement(title, content, System.currentTimeMillis())
                    }
                }
            }
        }

        runCatching {
            val update = adminClient.update(version)
            if (update.ok && update.bool("has_update")) {
                val latest = update.string("latest_version")
                val url = update.string("update_url")
                if (latest.isNotBlank() && latest != lastNotifiedVersion) {
                    lastNotifiedVersion = latest
                    pendingUpdate = UpdateInfo(
                        version = latest,
                        url = url,
                        force = update.bool("force_update"),
                        notes = update.string("update_notes"),
                    )
                }
            }
        }
    }

    /** 待展示的公告；UI 弹过一次后调 [consumeAnnouncement] 清空。 */
    @Volatile
    var pendingAnnouncement: Announcement? = null
        private set

    /** 待提示的更新；UI 处理过（下载或忽略）后调 [consumeUpdate] 清空。 */
    @Volatile
    var pendingUpdate: UpdateInfo? = null
        private set

    fun consumeAnnouncement() {
        pendingAnnouncement = null
    }

    fun consumeUpdate() {
        pendingUpdate = null
    }

    /** 远程公告内容。 */
    data class Announcement(val title: String, val content: String, val receivedAt: Long)

    /** 远程版本信息。[force] 为 true 时应阻塞使用直到更新。 */
    data class UpdateInfo(
        val version: String,
        val url: String,
        val force: Boolean,
        val notes: String,
    )

    /** 上次已展示过的公告（标题|正文），用于去重。 */
    @Volatile
    private var lastAnnouncementKey: String? = null

    /** 上次已提示过的新版本号，避免重复提示。 */
    @Volatile
    private var lastNotifiedVersion: String? = null

    /**
     * 应用后台下发的配置。
     *
     * 目前只处理供应商远程开关：后台把某个供应商关掉时，本地也加入 disabledProviders，
     * 下次心跳若已恢复则移出。只动后台明确列出过的项，不碰其它供应商。
     */
    fun applyRemoteConfig(result: RemoteAdminClient.Result) {
        // 后台心跳响应里没有 overrides 字段时什么都不做（本后台实现如此，预留扩展）
        val overrides = result.data.get("provider_overrides")
            ?.takeIf { it.isJsonArray }?.asJsonArray ?: return
        for (element in overrides) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val providerId = obj.get("provider_id")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
            val enabled = obj.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: continue
            if (enabled == isProviderEnabled(providerId)) continue
            setProviderEnabled(providerId, enabled)
            eventLog.info("后台", "$providerId ${if (enabled) "远程启用" else "远程停用"}")
        }
    }

    /** 设备标识：首次生成并落盘，供远程后台识别同一设备。 */
    val deviceId: String by lazy {
        val file = File(dataDir, "device_id")
        val existing = runCatching { file.readText().trim() }.getOrDefault("")
        if (existing.isNotEmpty()) return@lazy existing
        val generated = java.util.UUID.randomUUID().toString().replace("-", "").take(32)
        runCatching {
            dataDir.mkdirs()
            file.writeText(generated)
        }
        generated
    }

    // ---------- 账号验证（刷新登录） ----------

    /** 用上游模型目录接口验证账号凭证是否仍有效。 */
    fun verifyAccount(account: ProviderAccount): Result<List<String>> {
        val provider = providerById[account.providerId]
            ?: return Result.failure(IllegalArgumentException("unknown provider ${account.providerId}"))
        return provider.listModels(account)
    }

    /** 刷新登录结果：新账号 + 是否真正换了凭证（无刷新接口的供应商只做校验）。 */
    data class LoginRefreshResult(val account: ProviderAccount, val credentialsRefreshed: Boolean)

    /**
     * 刷新登录：供应商支持时用 refreshToken 换新凭证并写回（保留余额/启用/冷却状态），
     * 然后调模型目录校验新凭证可用；不支持刷新的供应商仅校验。
     */
    fun refreshLogin(account: ProviderAccount): Result<LoginRefreshResult> {
        val provider = providerById[account.providerId]
            ?: return Result.failure(IllegalArgumentException("unknown provider ${account.providerId}"))
        val label = "${account.displayName}（${account.uid.take(8)}）"
        val refreshed = provider is TokenRefreshSupport
        val target = try {
            if (provider is TokenRefreshSupport) {
                provider.refreshCredentials(account).also { pool.updateAccount(it) }
            } else {
                account
            }
        } catch (e: Exception) {
            eventLog.error("登录", "$label 刷新凭证失败：${e.message}")
            return Result.failure<LoginRefreshResult>(e)
        }
        val verified = provider.listModels(target)
        if (verified.isFailure) {
            val err = verified.exceptionOrNull() ?: IllegalStateException("校验失败")
            eventLog.error("登录", "$label 校验失败：${err.message}")
            return Result.failure<LoginRefreshResult>(err)
        }
        pool.setLastError(account.providerId, account.uid, null)
        eventLog.success(
            "登录",
            if (refreshed) "$label 凭证已刷新" else "$label 凭证校验通过（该供应商无刷新接口）"
        )
        return Result.success(LoginRefreshResult(target, refreshed))
    }

    /**
     * 定时健康检查：探测每个启用的 DeepSeek 网页账号 token 是否失效，失效则禁用。
     * 由前台服务每小时调度一次，只影响 DeepSeek（其他供应商走各自的刷新接口）。
     */
    fun probeTokenHealth() {
        val deepseek = providers.filterIsInstance<DeepSeekProvider>().firstOrNull() ?: return
        for (entry in pool.all().filter { it.account.providerId == DeepSeekProvider.ID && it.state.enabled }) {
            if (deepseek.isTokenValid(entry.account)) continue
            val label = entry.account.displayName.ifBlank { entry.account.uid }
            pool.setDisabled(entry.account.providerId, entry.account.uid, "token 已过期，请重新登录")
            eventLog.error("账号", "DeepSeek $label token 已失效，已禁用，请重新登录")
        }
    }

    // ---------- 签到 / 积分余额 ----------

    /** 每日签到：成功后记 last_checkin_at，失败记 last_error（均落盘）。 */
    fun checkin(account: ProviderAccount): Result<CheckinResult> {
        val provider = providerById[account.providerId] as? AccountActivitySupport
            ?: return Result.failure(IllegalStateException("该供应商不支持签到"))
        val label = "${account.displayName}（${account.uid.take(8)}）"
        eventLog.info("签到", "$label 开始签到")
        val result = provider.checkin(account)
        result.onSuccess { outcome ->
            pool.setLastError(account.providerId, account.uid, null)
            when (outcome) {
                is CheckinResult.Done -> {
                    pool.markCheckin(account.providerId, account.uid)
                    eventLog.success("签到", "$label ${outcome.message}")
                }
                CheckinResult.Skipped -> eventLog.info("签到", "$label 企业号，跳过")
            }
        }
        result.onFailure {
            pool.setLastError(account.providerId, account.uid, it.message)
            eventLog.error("签到", "$label 失败：${it.message}")
        }
        return result
    }

    /** 刷新单个账号的积分余额；未知余额不会覆盖已存值。 */
    /**
     * 从上游刷新账号显示名，成功则回写账号池。
     *
     * 只在用户进详情页时触发，失败静默——名字过期不影响任何功能。
     */
    fun refreshDisplayName(account: ProviderAccount): Boolean {
        val provider = providerById[account.providerId] as? DisplayNameSupport ?: return false
        val name = runCatching { provider.fetchDisplayName(account) }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return false
        if (name == account.displayName) return false
        pool.add(account.copy(displayName = name))
        return true
    }

    fun refreshBalance(account: ProviderAccount): Result<BalanceInfo> {
        val provider = providerById[account.providerId] as? AccountActivitySupport
            ?: return Result.failure(IllegalStateException("该供应商不支持余额查询"))
        val label = "${account.displayName}（${account.uid.take(8)}）"
        eventLog.info("余额", "$label 开始刷新余额")
        val result = provider.balance(account)
        result.onSuccess { info ->
            pool.updateBalance(account.providerId, account.uid, info)
            // 刷新成功就清掉上一条错误，否则账号行会一直挂着旧报错
            pool.setLastError(account.providerId, account.uid, null)
            if (info.known) {
                eventLog.success(
                    "余额",
                    "$label 剩余 ${info.remain.toLong()}${if (info.enterprise) "（企业额度）" else ""}",
                )
            } else {
                eventLog.warn("余额", "$label 上游未返回余额（未知）")
            }
        }
        result.onFailure {
            pool.setLastError(account.providerId, account.uid, it.message)
            eventLog.error("余额", "$label 刷新失败：${it.message}")
        }
        return result
    }

    // ---------- 并发闸门（单账号并发上限可配置） ----------

    private fun breakerOf(providerId: String): ProviderBreaker {
        val threshold = settings.failThreshold.coerceAtLeast(1)
        val existing = breakers[providerId]
        if (existing != null && breakerThresholds[providerId] == threshold) return existing
        // 阈值改了就用新阈值重建（旧状态作废）：否则界面上调了趟数却对已有熔断器不生效
        val created = ProviderBreaker(failureThreshold = threshold)
        breakers[providerId] = created
        breakerThresholds[providerId] = threshold
        return created
    }

    /**
     * 取得全局并发许可。
     *
     * 限额可随时在设置里改，所以不用 Semaphore（固定许可数），而是带条件变量的计数：
     * 每次进入都按当前设置判断，超限就限时等待。返回 true 时调用方**必须**配对
     * [releaseGlobal]。
     */
    private fun tryAcquireGlobal(timeoutMs: Long): Boolean {
        val limit = settings.maxConcurrentGlobal
        if (limit <= 0) return false // 未启用：不占计数，调用方也不释放
        val deadline = System.currentTimeMillis() + timeoutMs
        synchronized(globalLock) {
            while (globalInFlight >= settings.maxConcurrentGlobal) {
                val remain = deadline - System.currentTimeMillis()
                if (remain <= 0) return false
                runCatching { globalLock.wait(remain) }
            }
            globalInFlight++
            return true
        }
    }

    private fun releaseGlobal() {
        synchronized(globalLock) {
            if (globalInFlight > 0) globalInFlight--
            globalLock.notifyAll()
        }
    }

    /**
     * 这个错误是否属于「模型维度」：换号无用，应该冷却该模型。
     *
     * 判据取「429 且提到具体模型」或「文案里同时有模型与超限/不可用」——
     * 宁可少判也不误伤：误判会把整个模型封 10 分钟。
     */
    private fun isModelLevelError(err: ApiError): Boolean {
        val message = err.message.lowercase()
        val mentionsModel = message.contains("model") || message.contains("模型")
        val modelLimited = message.contains("超限") || message.contains("上限") ||
            message.contains("limit") || message.contains("not allowed") ||
            message.contains("no access") || message.contains("不可用")
        return mentionsModel && (err.status == 429 || modelLimited)
    }

    private fun gateFor(account: ProviderAccount): Semaphore {
        val key = "${account.providerId}/${account.uid}"
        val permits = settings.maxConcurrentPerAccount.coerceAtLeast(1)
        val existing = gates[key]
        if (existing != null && gatePermits[key] == permits) return existing
        // 上限变更时换新闸门；旧闸门上的在途请求继续用旧引用，不阻塞
        val gate = Semaphore(permits, true)
        gates[key] = gate
        gatePermits[key] = permits
        return gate
    }

    // ---------- 调用日志 ----------

    data class CallRecord(
        val time: Long,
        val model: String,
        val via: String,
        val ok: Boolean,
        val latencyMs: Long,
        val detail: String,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        val cachedTokens: Int = 0,
    )

    class CallLog(private val dir: File) {
        private val records = ArrayDeque<CallRecord>()
        private val cap = 500
        private val statsFile = File(dir, "today-stats.json")

        // 当日累计：不受 500 条明细上限影响，保证「今日请求/Token」统计准确
        private var dayKey = todayStart()
        private var dayTotal = 0
        private var daySuccess = 0
        private var dayFail = 0
        private var dayLatencySum = 0L
        private var dayInputTokens = 0L
        private var dayOutputTokens = 0L
        private var dayCacheTokens = 0L

        init {
            loadToday()
        }

        /** 启动时恢复当日计数；跨天则丢弃旧数据（每天 0 点自然重置）。 */
        private fun loadToday() {
            val today = todayStart()
            val text = runCatching { statsFile.readText() }.getOrNull() ?: return
            val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
            val savedDay = obj.get("dayStart")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            if (savedDay != today) return
            dayKey = today
            dayTotal = obj.get("total")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            daySuccess = obj.get("success")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            dayFail = obj.get("fail")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            dayLatencySum = obj.get("latencySum")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            dayInputTokens = obj.get("inputTokens")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            dayOutputTokens = obj.get("outputTokens")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
            dayCacheTokens = obj.get("cacheTokens")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L
        }

        /** 写盘：统计量很小，每次调用后直接持久化，重启不会归零。 */
        private fun persist() {
            runCatching {
                dir.mkdirs()
                val obj = JsonObject().apply {
                    addProperty("dayStart", dayKey)
                    addProperty("total", dayTotal)
                    addProperty("success", daySuccess)
                    addProperty("fail", dayFail)
                    addProperty("latencySum", dayLatencySum)
                    addProperty("inputTokens", dayInputTokens)
                    addProperty("outputTokens", dayOutputTokens)
                    addProperty("cacheTokens", dayCacheTokens)
                }
                val tmp = File(dir, "today-stats.json.tmp")
                tmp.writeText(obj.toString())
                if (statsFile.exists()) statsFile.delete()
                tmp.renameTo(statsFile)
            }
        }

        @Synchronized
        fun record(
            model: String,
            via: String,
            ok: Boolean,
            latencyMs: Long,
            detail: String,
            inputTokens: Int = 0,
            outputTokens: Int = 0,
            cachedTokens: Int = 0,
        ) {
            rollDayIfNeeded()
            val r = CallRecord(
                System.currentTimeMillis(), model, via, ok, latencyMs, detail, inputTokens, outputTokens, cachedTokens,
            )
            records.addLast(r)
            while (records.size > cap) records.removeFirst()

            dayTotal++
            if (ok) daySuccess++ else dayFail++
            dayLatencySum += latencyMs
            dayInputTokens += inputTokens
            dayOutputTokens += outputTokens
            dayCacheTokens += cachedTokens
            persist()
        }

        private fun todayStart(): Long = java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        private fun rollDayIfNeeded() {
            val start = todayStart()
            if (start == dayKey) return
            dayKey = start
            dayTotal = 0
            daySuccess = 0
            dayFail = 0
            dayLatencySum = 0
            dayInputTokens = 0
            dayOutputTokens = 0
            dayCacheTokens = 0
            persist()
        }

        @Synchronized
        fun recent(n: Int): List<CallRecord> = records.toList().takeLast(n)

        /**
         * 当日内某账号被调用的次数，用于 PACE 策略估算消耗速度。
         *
         * 只数 500 条明细窗口内的记录：对「谁烧得快」的判断已经够用，
         * 也不必为每个账号额外维护一份持久化计数器。
         */
        fun todayCountOf(providerId: String, uid: String): Int {
            val prefix = "$providerId:$uid"
            return records.count { it.via == prefix || it.via.startsWith("$prefix:") }
        }

        @Synchronized
        fun clear() {
            records.clear()
        }

        data class TodayStats(
            val total: Int,
            val success: Int,
            val fail: Int,
            val avgLatencyMs: Long,
            val inputTokens: Long = 0,
            val outputTokens: Long = 0,
            val cachedTokens: Long = 0,
        ) {
            val totalTokens: Long get() = inputTokens + outputTokens
        }

        /** 今日调用统计（按本机时区的自然日）。 */
        @Synchronized
        fun todayStats(): TodayStats {
            rollDayIfNeeded()
            return TodayStats(
                total = dayTotal,
                success = daySuccess,
                fail = dayFail,
                avgLatencyMs = if (dayTotal == 0) 0L else dayLatencySum / dayTotal,
                inputTokens = dayInputTokens,
                outputTokens = dayOutputTokens,
                cachedTokens = dayCacheTokens,
            )
        }
    }
}
