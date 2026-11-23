package dev.aigw.app.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.app.AiGatewayApp
import dev.aigw.app.data.LanAddresses
import dev.aigw.app.service.GatewayService
import dev.aigw.util.Log
import dev.aigw.gateway.AccountSelector
import dev.aigw.gateway.Engine
import dev.aigw.gateway.ProxySettings
import dev.aigw.gateway.ProxyType
import dev.aigw.gateway.RequestRewriter
import dev.aigw.gateway.Settings
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.DeviceAuthPoll
import dev.aigw.provider.codebuddy.CodeBuddyProvider
import dev.aigw.provider.codebuddy.WechatQrLogin
import dev.aigw.provider.ProviderAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 企业号「不限量」哨兵值（上游 limitNum < 0 时的映射），不计入剩余积分汇总。 */
private const val UNLIMITED_CREDITS = 1099511627776.0

data class AccountUi(
    val providerId: String,
    val uid: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean,
    val disabledReason: String?,
    val region: String? = null,
    /** 积分余额；null 表示未知（还没查过或上游未返回）。 */
    val balance: Double? = null,
    val lastBalanceAt: Long = 0,
    val lastCheckinAt: Long = 0,
    val checkedInToday: Boolean = false,
    val lastError: String? = null,
    val lastErrorAt: Long = 0,
    val enterprise: Boolean = false,
    /** 冷却截止时间戳；未冷却为 0。 */
    val cooldownUntil: Long = 0,
    /** 最小凭证 JSON（access_token/refresh_token，本应用与第三方工具粘贴用）。 */
    val minimalJson: String = "",
    /** 账号头像 URL（智谱等网页登录供应商提供）。 */
    val avatarUrl: String = "",
)

data class EventUi(
    val time: Long,
    val level: String,
    val tag: String,
    val message: String,
)

/** 一次 API 调用的记录（调用记录页签的数据源）。 */
data class CallUi(
    val time: Long,
    val model: String,
    /** 路由标识，形如 `codebuddy:uid`，前缀为供应商 id。 */
    val via: String,
    val ok: Boolean,
    /** 反馈信息（成功为 streamed/json，失败为错误原因）。 */
    val detail: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
) {
    val providerId: String get() = via.substringBefore(':')
    val account: String get() = via.substringAfter(':', "").ifBlank { "-" }
}

data class ProxyUi(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: String = "8080",
    val username: String = "",
    val password: String = "",
)

data class WbLoginState(
    val active: Boolean = false,
    val region: String = "cn",
    /** 上游返回的授权链接（失败后可用系统浏览器打开，绕开 WebView 的代理差异）。 */
    val url: String = "",
    /** 授权 state（与 url 同次返回；浏览器登录完成后用它轮询凭证）。 */
    val state: String = "",
    val status: String = "",
    val error: String? = null,
)

/** Loomy 短信登录状态（发送验证码后拿到的 msgid 暂存在这里，登录时回传）。 */
data class LoomyLoginState(
    val msgid: String = "",
    val sending: Boolean = false,
    val loggingIn: Boolean = false,
    val error: String? = null,
    /** 登录成功次数；UI 监听它清零手机号/验证码输入框。 */
    val resetCounter: Int = 0,
)

/** 智谱清言短信登录状态（结构同 Loomy，上游无需回传 msgid）。 */
data class ChatglmLoginState(
    val sending: Boolean = false,
    val loggingIn: Boolean = false,
    val error: String? = null,
    val resetCounter: Int = 0,
)

/** 模型页的一条：完整调用名、归属供应商、裸模型名与展示元数据。 */
data class ModelUi(
    val id: String,
    val providerId: String,
    val model: String,
    val displayName: String = "",
    /** 模型类型：text / image / video。 */
    val modelType: String = "text",
    val contextWindow: Long = 0,
    val maxOutputTokens: Long = 0,
    val tags: List<String> = emptyList(),
    /** 探测结果：null=未探测；true=可调用；false=不可用（原因见 [probeError]）。 */
    val probeOk: Boolean? = null,
    val probeError: String = "",
    /** 正在探测中（模型列表里的单个测试按钮触发）。 */
    val probing: Boolean = false,
)

data class UiState(
    val running: Boolean = false,
    /** 本次网关启动时间（毫秒）；未运行时为 0。 */
    val startedAt: Long = 0,
    val port: Int = 8790,
    val accounts: List<AccountUi> = emptyList(),
    val accountEnabled: Int = 0,
    val accountTotal: Int = 0,
    /** 所有账号（不分供应商）的积分合计；企业号不限量不计入。 */
    val totalCredits: Double = 0.0,
    /** 有余额数据的账号数（>0 时才能把 [totalCredits] 当成汇总值）。 */
    val creditsKnown: Int = 0,
    val todayRequests: Int = 0,
    val todaySuccess: Int = 0,
    val todayFail: Int = 0,
    val avgLatencyMs: Long = 0,
    val todayTokens: Long = 0,
    val todayInputTokens: Long = 0,
    val todayOutputTokens: Long = 0,
    /** 今日提示词缓存命中 token（上游未上报则为 0）。 */
    val todayCachedTokens: Long = 0,
    val models: List<ModelUi> = emptyList(),
    val loadingModels: Boolean = false,
    /** 模型可用性检测进度：null=未在检测，否则为「已完成/总数」。 */
    val probingModels: Pair<Int, Int>? = null,
    /** 每个供应商是否启用（停用后模型不进列表、无法调用）。 */
    val providerEnabled: Map<String, Boolean> = emptyMap(),
    /** 供应商调用区域偏好：providerId → "cn"/"global"；缺失 = 自由调用。 */
    val regionPrefs: Map<String, String> = emptyMap(),
    /** 悬浮监测球开关（默认关闭）。 */
    val monitorBubble: Boolean = false,
    /** 桌面宠物开关（开启后替代监测球）。 */
    val petBubble: Boolean = false,
    /** 网关层统一注入的系统提示词。 */
    val systemPrompt: String = "",
    /** 模型别名表（客户端模型名 → 真实模型名）。 */
    val modelAliases: Map<String, String> = emptyMap(),
    /** 是否剥离 assistant 历史里的 think 标签。 */
    val stripThinkTags: Boolean = false,
    /** 多账号选择策略：ORDER / ROTATE / USAGE / PACE。 */
    val accountStrategy: String = "ORDER",
    /** 悬浮球透明度（0.3-1.0）。 */
    val bubbleOpacity: Float = 1f,
    /** 是否已授予「显示在其它应用上层」权限。 */
    val canDrawOverlays: Boolean = false,
    val events: List<EventUi> = emptyList(),
    /** 调用记录明细（最近 200 条，新的在后）。 */
    val calls: List<CallUi> = emptyList(),
    val apiKey: String = "",
    val allowNoKey: Boolean = false,
    val bindLan: Boolean = false,
    /** 余额自动刷新间隔（秒）。 */
    val balanceRefreshSeconds: Int = 300,
    /** 单账号并发上限。 */
    val maxConcurrentPerAccount: Int = 1,
    /** 全网关并发上限；0 = 不限。 */
    val maxConcurrentGlobal: Int = 0,
    /** 连续失败达到该次数后进入冷却。 */
    val failThreshold: Int = 3,
    /** 冷却时长（秒）。 */
    val cooldownSeconds: Int = 300,
    /** 会话粘黏开关。 */
    val sessionSticky: Boolean = false,
    val lanAddresses: List<String> = emptyList(),
    val startError: String? = null,
    val proxy: ProxyUi = ProxyUi(),
    val wbLogin: WbLoginState = WbLoginState(),
    /** Loomy 短信登录状态。 */
    val loomyLogin: LoomyLoginState = LoomyLoginState(),
    /** 智谱清言短信登录状态。 */
    val chatglmLogin: ChatglmLoginState = ChatglmLoginState(),
    /** 需要跳到指定页签（如无账号时跳到供应商页）。 */
    val pendingTab: String? = null,
    val message: String? = null,
    /** 当前外观主题名称。 */
    val themeName: String = "白昼",
    /** 待展示的远程公告（弹窗一次后清空）。 */
    val announcement: AnnouncementUi? = null,
    /** 待提示的远程更新（下载或忽略后清空）。 */
    val updateInfo: UpdateUi? = null,
    /** 是否有底部弹层打开（打开时隐藏底部导航，避免遮挡）。 */
    val sheetOpen: Boolean = false,
)

/** 远程公告（UI 展示用）。 */
data class AnnouncementUi(val title: String, val content: String)

/** 远程版本信息（UI 展示用）。 */
data class UpdateUi(val version: String, val url: String, val force: Boolean, val notes: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val gateway = app as AiGatewayApp
    private val engine get() = gateway.engine

    var state by mutableStateOf(UiState())
        private set

    /** 取当前主题色板（供 Composable 直接使用）。 */
    fun theme(): ThemePalette = themeByName(state.themeName)

    /** 刷新防重入：定时刷新与手动刷新的 IO 都在后台线程，不阻塞主线程。 */
    private var refreshing = false

    /** 最近一次系统公告：弹过一次后仍保留，供仪表盘公告入口再次查看。 */
    private var lastAnnouncement: AnnouncementUi? = null

    init {
        refreshAll()
    }

    fun refreshAll() {
        maybeAutoRefreshBalances()
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
                val snap = withContext(Dispatchers.IO) { buildSnapshot() }
                state = state.copy(
                    running = snap.running,
                    startedAt = snap.startedAt,
                    port = snap.port,
                    accounts = snap.accounts,
                    accountEnabled = snap.accountEnabled,
                    accountTotal = snap.accountTotal,
                    totalCredits = snap.totalCredits,
                    creditsKnown = snap.creditsKnown,
                    todayRequests = snap.todayRequests,
                    todaySuccess = snap.todaySuccess,
                    todayFail = snap.todayFail,
                    avgLatencyMs = snap.avgLatencyMs,
                    todayTokens = snap.todayTokens,
                    todayInputTokens = snap.todayInputTokens,
                    todayOutputTokens = snap.todayOutputTokens,
                    todayCachedTokens = snap.todayCachedTokens,
                    providerEnabled = snap.providerEnabled,
                    regionPrefs = snap.regionPrefs,
                    monitorBubble = snap.monitorBubble,
                    petBubble = snap.petBubble,
                    systemPrompt = snap.systemPrompt,
                    modelAliases = snap.modelAliases,
                    stripThinkTags = snap.stripThinkTags,
                    accountStrategy = snap.accountStrategy,
                    bubbleOpacity = snap.bubbleOpacity,
                    canDrawOverlays = snap.canDrawOverlays,
                    events = snap.events,
                    calls = snap.calls,
                    apiKey = snap.apiKey,
                    allowNoKey = snap.allowNoKey,
                    bindLan = snap.bindLan,
                    balanceRefreshSeconds = snap.balanceRefreshSeconds,
                    maxConcurrentPerAccount = snap.maxConcurrentPerAccount,
                    maxConcurrentGlobal = snap.maxConcurrentGlobal,
                    failThreshold = snap.failThreshold,
                    cooldownSeconds = snap.cooldownSeconds,
                    sessionSticky = snap.sessionSticky,
                    lanAddresses = snap.lanAddresses,
                    startError = snap.startError,
                    proxy = snap.proxy,
                    themeName = snap.themeName,
                    announcement = snap.announcement,
                    updateInfo = snap.updateInfo,
                )
                snap.announcement?.let { lastAnnouncement = it }
            } finally {
                refreshing = false
            }
        }
    }

    private data class Snapshot(
        val running: Boolean,
        val startedAt: Long,
        val port: Int,
        val accounts: List<AccountUi>,
        val accountEnabled: Int,
        val accountTotal: Int,
        val totalCredits: Double,
        val creditsKnown: Int,
        val todayRequests: Int,
        val todaySuccess: Int,
        val todayFail: Int,
        val avgLatencyMs: Long,
        val todayTokens: Long,
        val todayInputTokens: Long,
        val todayOutputTokens: Long,
        val todayCachedTokens: Long,
        val providerEnabled: Map<String, Boolean>,
        val regionPrefs: Map<String, String>,
        val monitorBubble: Boolean,
        val petBubble: Boolean,
        val systemPrompt: String,
        val modelAliases: Map<String, String>,
        val stripThinkTags: Boolean,
        val accountStrategy: String,
        val bubbleOpacity: Float,
        val canDrawOverlays: Boolean,
        val events: List<EventUi>,
        val calls: List<CallUi>,
        val apiKey: String,
        val allowNoKey: Boolean,
        val bindLan: Boolean,
        val balanceRefreshSeconds: Int,
        val maxConcurrentPerAccount: Int,
        val maxConcurrentGlobal: Int,
        val failThreshold: Int,
        val cooldownSeconds: Int,
        val sessionSticky: Boolean,
        val lanAddresses: List<String>,
        val startError: String?,
        val proxy: ProxyUi,
        val themeName: String,
        val announcement: AnnouncementUi?,
        val updateInfo: UpdateUi?,
    )

    /** 余额自动刷新节流：首页「剩余积分」需要接近实时，但不能每 5 秒都打上游。 */
    private var lastAutoBalanceAt = 0L

    private fun maybeAutoRefreshBalances() {
        val intervalMs = (engine.settings.balanceRefreshSeconds * 1000L).coerceAtLeast(10_000L)
        val now = System.currentTimeMillis()
        if (now - lastAutoBalanceAt < intervalMs) return
        lastAutoBalanceAt = now
        viewModelScope.launch(Dispatchers.IO) {
            for (providerId in engine.activityProviderIds()) {
                for (entry in engine.pool.enabled(providerId)) {
                    runCatching { engine.refreshBalance(entry.account) }
                }
            }
        }
    }

    private fun buildSnapshot(): Snapshot {
        val allAccounts = engine.pool.all()
        val stats = engine.callLog.todayStats()
        // 展开后的账号行才是「用户看到的账号」：豆包在池里有几个号就有几行。
        // 仪表盘的数量/积分必须基于它，否则会和各供应商页的合计对不上。
        val rows = allAccounts.map { e ->
                AccountUi(
                    providerId = e.account.providerId,
                    uid = e.account.uid,
                    name = e.account.displayName,
                    baseUrl = e.account.baseUrl,
                    enabled = e.state.enabled,
                    disabledReason = e.state.disabledReason,
                    region = if (e.account.providerId == "codebuddy" || e.account.providerId == "trae") {
                        engine.regionOf(e.account)
                    } else {
                        null
                    },
                    balance = e.state.balance,
                    lastBalanceAt = e.state.lastBalanceAt,
                    lastCheckinAt = e.state.lastCheckinAt,
                    checkedInToday = e.state.checkedInToday(),
                    lastError = e.state.lastError,
                    lastErrorAt = e.state.lastErrorAt,
                    enterprise = e.state.enterprise,
                    cooldownUntil = e.state.cooldownUntil,
                    minimalJson = runCatching { engine.exportMinimalCredential(e.account.providerId, e.account.uid) }
                        .getOrDefault("{}"),
                    avatarUrl = e.account.extra["avatar"].orEmpty(),
                )

            }
        return Snapshot(
            running = gateway.server.isRunning(),
            startedAt = gateway.server.startedAt,
            port = engine.settings.port,
            accounts = rows,
            // 免费池是免登录通道，内部没有真实账号，不计入账号统计
            accountEnabled = rows.count { it.enabled && it.providerId != "freepool" },
            accountTotal = rows.count { it.providerId != "freepool" },
            totalCredits = rows.mapNotNull { it.balance }
                .filter { it < UNLIMITED_CREDITS }
                .sum(),
            creditsKnown = rows.mapNotNull { it.balance }
                .count { it < UNLIMITED_CREDITS },
            todayRequests = stats.total,
            todaySuccess = stats.success,
            todayFail = stats.fail,
            avgLatencyMs = stats.avgLatencyMs,
            todayTokens = stats.totalTokens,
            todayInputTokens = stats.inputTokens,
            todayOutputTokens = stats.outputTokens,
            todayCachedTokens = stats.cachedTokens,
            providerEnabled = engine.providerIds().associateWith { engine.isProviderEnabled(it) },
            regionPrefs = engine.settings.regionPrefs?.toMap().orEmpty(),
            monitorBubble = engine.settings.monitorBubble,
            petBubble = engine.settings.petBubble,
            systemPrompt = engine.settings.systemPrompt,
            modelAliases = engine.settings.modelAliases ?: emptyMap(),
            stripThinkTags = engine.settings.stripThinkTags,
            accountStrategy = engine.settings.accountStrategy,
            bubbleOpacity = engine.settings.bubbleOpacity.coerceIn(0.3f, 1f),
            canDrawOverlays = android.provider.Settings.canDrawOverlays(gateway),
            events = engine.eventLog.snapshot().asReversed().map { e ->
                EventUi(e.time, e.level, e.tag, e.message)
            },
            calls = engine.callLog.recent(200).map { r ->
                CallUi(
                    time = r.time,
                    model = r.model,
                    via = r.via,
                    ok = r.ok,
                    detail = r.detail,
                    inputTokens = r.inputTokens,
                    outputTokens = r.outputTokens,
                )
            },
            apiKey = engine.settings.apiKey,
            allowNoKey = engine.settings.allowNoKey,
            bindLan = engine.settings.bindLan,
            balanceRefreshSeconds = engine.settings.balanceRefreshSeconds,
            maxConcurrentPerAccount = engine.settings.maxConcurrentPerAccount,
            maxConcurrentGlobal = engine.settings.maxConcurrentGlobal,
            failThreshold = engine.settings.failThreshold,
            cooldownSeconds = engine.settings.cooldownSeconds,
            sessionSticky = engine.settings.sessionSticky,
            lanAddresses = LanAddresses.current(),
            startError = gateway.lastStartError,
            proxy = engine.proxySettings.let { p ->
                ProxyUi(p.enabled, p.type, p.host, p.port.toString(), p.username, p.password)
            },
            themeName = engine.settings.themeName,
            announcement = engine.pendingAnnouncement?.let { AnnouncementUi(it.title, it.content) },
            updateInfo = engine.pendingUpdate?.let { UpdateUi(it.version, it.url, it.force, it.notes) },
        )
    }

    // ---------- WorkBuddy 设备授权登录 ----------

    private var loginJob: Job? = null
    /** 浏览器收凭证轮询（打开外部浏览器后独立运行，不依赖登录页 Activity）。 */
    private var collectJob: Job? = null

    /**
     * WorkBuddy 登录。
     *
     * [wechat] 为 true 时不走登录页，直接构造 keycloak 的 OIDC 授权地址并带
     * `kc_idp_hint=weixinwebchat`：keycloak 会转给微信 broker，WebView 里就是扫码界面。
     * 两种方式拿到的 state 共用同一个轮询接口。
     */
    fun startWorkbuddyLogin(region: String, wechat: Boolean = false) {
        loginJob?.cancel()
        state = state.copy(wbLogin = WbLoginState(active = true, region = region, status = "正在获取授权链接…"))
        loginJob = viewModelScope.launch {
            val pair = withContext(Dispatchers.IO) {
                engine.setCodeBuddyAuthPlatform(CodeBuddyProvider.PLATFORM_CLI)
                runCatching {
                    val ticket = engine.startDeviceAuth("codebuddy", region)
                    if (wechat) {
                        // 微信 broker 的回调固定落在 codebuddy.cn（服务端决定），
                        // 而 state 在 chatBase（copilot.tencent.com）申请，跨域后
                        // /console/login/enterprise 拿不到同域会话，一直是 401。
                        // 因此扫码这条路径整个改到 codebuddy.cn 走一遍。
                        val host = if (region == "global") "https://www.workbuddy.ai" else "https://www.codebuddy.cn"
                        val st = withContext(Dispatchers.IO) {
                            WechatQrLogin.requestState(host)
                        }
                        WechatQrLogin.urlOf(host, st.second) to st.first
                    } else {
                        val ticket = engine.startDeviceAuth("codebuddy", region)
                        ticket.loginUrl to ticket.state
                    }
                }
            }.getOrElse {
                state = state.copy(wbLogin = WbLoginState(active = false, region = region, status = "获取授权链接失败", error = it.message))
                return@launch
            }
            val (loginUrl, loginState) = pair
            state = state.copy(wbLogin = state.wbLogin.copy(url = loginUrl, state = loginState))
            val ctx = getApplication<Application>()
            runCatching {
                ctx.startActivity(
                    Intent(ctx, WebLoginActivity::class.java)
                        .putExtra(WebLoginActivity.EXTRA_URL, loginUrl)
                        .putExtra(WebLoginActivity.EXTRA_STATE, loginState)
                        .putExtra(WebLoginActivity.EXTRA_REGION, region)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                state = state.copy(
                    wbLogin = WbLoginState(
                        active = false,
                        region = region,
                        url = loginUrl,
                        state = loginState,
                        status = "打开登录页失败",
                        error = it.message,
                    ),
                )
                return@launch
            }
            state = state.copy(wbLogin = WbLoginState())
        }
    }

    /**
     * 授权链接交给系统浏览器（Chrome 等）打开。
     *
     * 浏览器自带代理/分流对海外站点（X 授权）比 App 内 WebView 可靠，
     * 页面登录完成后由网关轮询 state 收凭证。
     */
    fun openWorkbuddyLoginUrl() {
        val login = state.wbLogin
        val url = login.url
        val authState = login.state
        if (url.isBlank() || authState.isBlank()) return
        runCatching {
            getApplication<Application>().startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            state = state.copy(wbLogin = login.copy(status = "打开浏览器失败", error = it.message))
            return
        }
        // 收凭证轮询独立于登录页 Activity：浏览器里完成授权后，网关轮询 state 拿到 token
        state = state.copy(wbLogin = login.copy(active = false, status = "浏览器已打开，等待完成授权后自动收凭证…"))
        collectJob?.cancel()
        collectJob = viewModelScope.launch {
            while (true) {
                val poll = withContext(Dispatchers.IO) {
                    runCatching { engine.pollDeviceAuth("codebuddy", authState, login.region) }
                        .getOrElse { DeviceAuthPoll.Failed(it.message ?: "网络错误") }
                }
                when (poll) {
                    is DeviceAuthPoll.Pending -> delay(2_000)
                    is DeviceAuthPoll.Success -> {
                        runCatching { engine.saveAccount(poll.account) }
                        state = state.copy(
                            wbLogin = WbLoginState(),
                            message = "登录成功：${poll.account.displayName}",
                        )
                        refreshAll()
                        return@launch
                    }
                    is DeviceAuthPoll.Failed -> {
                        state = state.copy(wbLogin = state.wbLogin.copy(status = "登录失败", error = poll.error))
                        return@launch
                    }
                }
            }
        }
    }

    fun cancelWorkbuddyLogin() {
        loginJob?.cancel()
        collectJob?.cancel()
        state = state.copy(wbLogin = WbLoginState())
    }

    // ---------- Loomy 短信登录 ----------

    /** 发送短信验证码；成功后把上游 msgid 暂存到状态，供登录时回传。 */
    fun sendLoomyCode(phone: String, ccode: String) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            state = state.copy(loomyLogin = state.loomyLogin.copy(sending = true, error = null))
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.sendSmsCode("loomy", phone.trim(), ccode.trim()) }
            }
            result.fold(
                onSuccess = { msgid ->
                    state = state.copy(loomyLogin = state.loomyLogin.copy(msgid = msgid, sending = false))
                },
                onFailure = {
                    state = state.copy(loomyLogin = state.loomyLogin.copy(sending = false, error = it.message))
                },
            )
        }
    }

    /** 用短信验证码登录；成功后账号落盘并刷新列表。 */
    fun loginLoomy(phone: String, code: String, ccode: String) {
        if (phone.isBlank() || code.isBlank()) return
        viewModelScope.launch {
            state = state.copy(loomyLogin = state.loomyLogin.copy(loggingIn = true, error = null))
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.completeSmsLogin("loomy", phone.trim(), code.trim(), state.loomyLogin.msgid, ccode.trim()) }
            }
            result.fold(
                onSuccess = { account ->
                    state = state.copy(
                        loomyLogin = LoomyLoginState(resetCounter = state.loomyLogin.resetCounter + 1),
                        message = "登录成功：${account.displayName}",
                    )
                    refreshAll()
                },
                onFailure = {
                    state = state.copy(loomyLogin = state.loomyLogin.copy(loggingIn = false, error = it.message))
                },
            )
        }
    }
    // ---------- 智谱清言短信登录 ----------

    /** 发送短信验证码（智谱清言，上游无需回传 msgid）。 */
    fun sendChatglmCode(phone: String, ccode: String) {
        if (phone.isBlank()) return
        viewModelScope.launch {
            state = state.copy(chatglmLogin = state.chatglmLogin.copy(sending = true, error = null))
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.sendSmsCode("chatglm", phone.trim(), ccode.trim()) }
            }
            result.fold(
                onSuccess = {
                    state = state.copy(chatglmLogin = state.chatglmLogin.copy(sending = false))
                },
                onFailure = {
                    state = state.copy(chatglmLogin = state.chatglmLogin.copy(sending = false, error = it.message))
                },
            )
        }
    }

    /** 用验证码登录智谱清言；成功后账号落盘并刷新列表。 */
    fun loginChatglm(phone: String, code: String, ccode: String) {
        if (phone.isBlank() || code.isBlank()) return
        viewModelScope.launch {
            state = state.copy(chatglmLogin = state.chatglmLogin.copy(loggingIn = true, error = null))
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.completeSmsLogin("chatglm", phone.trim(), code.trim(), "", ccode.trim()) }
            }
            result.fold(
                onSuccess = { account ->
                    state = state.copy(
                        chatglmLogin = ChatglmLoginState(resetCounter = state.chatglmLogin.resetCounter + 1),
                        message = "登录成功：${account.displayName}",
                    )
                    refreshAll()
                },
                onFailure = {
                    state = state.copy(chatglmLogin = state.chatglmLogin.copy(loggingIn = false, error = it.message))
                },
            )
        }
    }

    /**
     * 提交问题反馈工单。回调返回错误文案（null 表示成功）。
     *
     * 网络请求在 IO 线程执行，不阻塞 UI；失败不影响网关运行。
     */
    fun submitTicket(
        content: String,
        contactType: String,
        contact: String,
        images: List<String> = emptyList(),
        onDone: (String?) -> Unit,
    ) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    engine.submitTicket(
                        content = content,
                        contactType = contactType,
                        contact = contact,
                        images = images,
                        version = "1.9.27",
                    )
                }.getOrElse {
                    dev.aigw.gateway.RemoteAdminClient.TicketResult(false, 0, it.message ?: "提交失败")
                }
            }
            state = state.copy(message = result.message)
            onDone(if (result.ok) null else result.message)
        }
    }

    /** 执行供应商专属动作（兑换码、任务等）；完成后刷新余额。 */
    fun performProviderAction(providerId: String, uid: String, action: String, payload: JsonObject = JsonObject()) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.performAction(providerId, uid, action, payload) }
            state = state.copy(message = result.message)
            refreshAll()
        }
    }

    /** DeepSeek 网页登录：打开登录页，登录成功后从页面 localStorage 取 token。 */
    fun startDeepSeekLogin() {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { engine.startTokenLogin("deepseek") } }
                .getOrElse {
                    notify("打开登录页失败：${it.message}")
                    return@launch
                }
            val ctx = getApplication<Application>()
            runCatching {
                ctx.startActivity(
                    Intent(ctx, WebLoginActivity::class.java)
                        .putExtra(WebLoginActivity.EXTRA_URL, url)
                        .putExtra(WebLoginActivity.EXTRA_PROVIDER, "deepseek")
                        .putExtra(WebLoginActivity.EXTRA_MODE, WebLoginActivity.MODE_TOKEN)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { notify("打开登录页失败：${it.message}") }
        }
    }

    /** 智谱清言网页登录：打开 chatglm.cn，登录成功后从 cookie 取凭证存为账号。 */
    /** MiMo 网页登录：小米账号登录后从 cookie 里取凭证。 */
    fun startMimoLogin() {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.startActivity(
                Intent(ctx, WebLoginActivity::class.java)
                    .putExtra(WebLoginActivity.EXTRA_URL, "https://aistudio.xiaomi.com/")
                    .putExtra(WebLoginActivity.EXTRA_PROVIDER, "mimo")
                    .putExtra(WebLoginActivity.EXTRA_MODE, WebLoginActivity.MODE_TOKEN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { notify("打开 MiMo 登录页失败：${it.message}") }
    }


    fun startChatglmLogin() {
        // 提前预热页面通道：登录完成后对话可直接走 WebView
        gateway.chatglmTransport.warmUp()
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { engine.startTokenLogin("chatglm") } }
                .getOrElse {
                    notify("打开登录页失败：${it.message}")
                    return@launch
                }
            val ctx = getApplication<Application>()
            runCatching {
                ctx.startActivity(
                    Intent(ctx, WebLoginActivity::class.java)
                        .putExtra(WebLoginActivity.EXTRA_URL, url)
                        .putExtra(WebLoginActivity.EXTRA_PROVIDER, "chatglm")
                        .putExtra(WebLoginActivity.EXTRA_MODE, WebLoginActivity.MODE_TOKEN)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { notify("打开登录页失败：${it.message}") }
        }
    }
    /** 粘贴凭证（JSON 或裸 token）导入账号；完成后回调 null，失败回调错误文案。 */
    fun importCredentials(providerId: String, raw: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching { engine.importCredentials(providerId, raw) }.fold(
                    onSuccess = { null },
                    onFailure = { it.message ?: "导入失败" },
                )
            }
            if (error == null) refreshAll()
            onDone(error)
        }
    }

    /**
     * 导出账号迁移信封。[providerId] 为空时导出全部供应商的账号。
     * 成功时回调信封 JSON，失败时回调错误文案。
     */
    fun exportAccountEnvelope(providerId: String? = null, onDone: (String?, String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.exportAccountTransfer(providerId) }
            }
            result.fold(
                onSuccess = { onDone(it, null) },
                onFailure = { onDone(null, it.message ?: "导出失败") },
            )
        }
    }

    /**
     * 账号迁移信封导入：直接落池，不联网校验（token 失效也能恢复账号）。
     * 识别不到信封时返回 null，调用方回退到普通凭证导入。
     */
    fun importAccountEnvelope(raw: String, onDone: (Int, Int, String?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.importAccountTransfer(raw, overwrite = true) }
            }.getOrElse {
                onDone(0, 0, it.message ?: "导入失败")
                return@launch
            }
            refreshAll()
            val total = result.added.size + result.updated.size
            val error = when {
                result.unknown.isNotEmpty() ->
                    "${result.unknown.size} 个供应商未注册：${result.unknown.joinToString("、")}"
                result.skipped.isNotEmpty() -> "${result.skipped.size} 个账号已存在被跳过"
                else -> null
            }
            onDone(total, result.unknown.size, error)
        }
    }

    /**
     * 批量导入凭证：逐条导入，每完成一条回调一次进度；全部结束后回调汇总。
     * 单条失败不中断，继续导入下一条。
     */
    fun importCredentialsBatch(
        providerId: String,
        items: List<String>,
        onProgress: (done: Int, total: Int, lastError: String?) -> Unit,
        onDone: (success: Int, failed: Int, errors: List<String>) -> Unit,
    ) {
        viewModelScope.launch {
            var ok = 0
            var failed = 0
            var done = 0
            val errors = mutableListOf<String>()
            for (item in items) {
                val error = withContext(Dispatchers.IO) {
                    runCatching { engine.importCredentials(providerId, item) }.fold(
                        onSuccess = { null },
                        onFailure = { it.message ?: "导入失败" },
                    )
                }
                done++
                if (error == null) {
                    ok++
                } else {
                    failed++
                    errors += "第 $done 条：$error"
                }
                onProgress(done, items.size, error)
            }
            if (ok > 0) refreshAll()
            onDone(ok, failed, errors)
        }
    }

    // ---------- Trae 浏览器回调登录 ----------
    /**
     * Trae 登录：网关临时监听本机 51120 接回调，登录页在 WebView 里完成。
     * 回调到达后由网关把凭证存入账号池，界面轮询结果后自动关闭登录页。
     */
    /**
     * 通用浏览器回调登录：供应商自己实现 [WebCallbackSupport] 就能用这里。
     *
     * 与 [startTraeLogin] 同一套流程（开本地回调服务 + WebLoginActivity），
     * 区别只是 providerId 由调用方给。
     */
    fun startProviderLogin(providerId: String) {
        viewModelScope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin(providerId, "") }
            }.getOrElse {
                notify("打开 $providerId 登录失败：${it.message}")
                return@launch
            }
            val ctx = getApplication<Application>()
            runCatching {
                ctx.startActivity(
                    Intent(ctx, WebLoginActivity::class.java)
                        .putExtra(WebLoginActivity.EXTRA_URL, ticket.loginUrl)
                        .putExtra(WebLoginActivity.EXTRA_MODE, WebLoginActivity.MODE_CALLBACK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                notify("打开登录页失败：${it.message}")
            }
            watchBrowserLogin()
        }
    }

    /**
     * 生成带 PKCE 的授权链接并复制到剪贴板。
     *
     * 用户已在某个浏览器里登录了 ChatGPT 时，WebView 的 Cookie 是隔离的，
     * 在应用内登录还得重登一遍。把这条链接丢进那个浏览器，它会直接返回 code。
     */
    fun copyChatgptAuthUrl() {
        viewModelScope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin("chatgpt", "") }
            }.getOrElse {
                notify("生成授权链接失败：${it.message}")
                return@launch
            }
            val clipboard = getApplication<Application>()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText("ChatGPT 授权链接", ticket.loginUrl),
            )
            notify("授权链接已复制，去浏览器粘贴打开")
        }
    }

    /**
     * 从粘贴的回调地址（或裸 code）完成登录。
     *
     * 系统浏览器里跳出 localhost 时页面是打不开的，但地址栏里的 code 有效。
     * 用户把整条 URL 贴回来，这里直接交给引擎换 token。
     */
    fun pasteCallbackUrl(raw: String): Boolean {
        if (raw.isBlank()) {
            notify("请先粘贴回调地址")
            return false
        }
        return try {
            val account = engine.pasteBrowserCallback(raw)
            notify("登录成功：${account.displayName}")
            refreshAll()
            true
        } catch (e: Exception) {
            notify("登录失败：${e.message}")
            false
        }
    }

    /**
     * 添加 muse.ai 账号：账号本身就是一份登录 cookie。
     *
     * 必需 hatch_sess，其余（hatch_vml / hatch_gw）能带上就带，
     * 页面打开后会自动续期并回写。
     */
    fun addMuseAccount(rawCookie: String): Boolean {
        val ck = rawCookie.trim()
        if (ck.isBlank()) {
            notify("Cookie 不能为空")
            return false
        }
        if (!ck.contains("hatch_sess")) {
            notify("Cookie 里缺少 hatch_sess，请重新导出")
            return false
        }
        // uid 用 hatch_sess 前 8 位：同一账号重复导入会覆盖而不是新增
        val sess = Regex("hatch_sess=([^;\\s]+)").find(ck)?.groupValues?.get(1).orEmpty()
        val uid = sess.take(8).ifEmpty { "muse" + System.currentTimeMillis() % 100000 }
        engine.pool.add(
            dev.aigw.provider.ProviderAccount(
                providerId = "muse",
                uid = uid,
                displayName = "muse " + uid,
                secret = ck,
            ),
        )
        refreshAll()
        notify("muse 账号已添加")
        return true
    }

    fun startTraeLogin(region: String) {
        viewModelScope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin("trae", region) }
            }.getOrElse {
                notify("打开 Trae 登录失败：${it.message}")
                return@launch
            }
            val ctx = getApplication<Application>()
            runCatching {
                ctx.startActivity(
                    Intent(ctx, WebLoginActivity::class.java)
                        .putExtra(WebLoginActivity.EXTRA_URL, ticket.loginUrl)
                        .putExtra(WebLoginActivity.EXTRA_REGION, region)
                        .putExtra(WebLoginActivity.EXTRA_MODE, WebLoginActivity.MODE_CALLBACK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                notify("打开登录页失败：${it.message}")
            }
            watchBrowserLogin()
        }
    }

    // ---------- 小浣熊 浏览器回调登录 ----------
    /**
     * 登录页关闭后刷新账号列表。
     *
     * 登录 Activity 是独立进程内 Activity，没有回传结果，账号由引擎直接写入池；
     * 不主动刷一次的话，用户返回列表会看不到刚加的账号。
     */
    private fun watchBrowserLogin() {
        viewModelScope.launch {
            repeat(150) {
                delay(2000)
                if (engine.browserLoginSucceeded()) {
                    notify("登录成功：${engine.browserLoginResult.orEmpty()}")
                    refreshAll()
                    return@launch
                }
            }
        }
    }

    /** 小浣熊登录：授权页在小熊域名下，登录后回跳本机回调带 authorization_code。 */
    fun startRaccoonLogin() {
        viewModelScope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin("raccoon", "") }
            }.getOrElse {
                notify("打开小浣熊登录失败：${it.message}")
                return@launch
            }
            val ctx = getApplication<Application>()
            runCatching {
                ctx.startActivity(
                    Intent(ctx, WebLoginActivity::class.java)
                        .putExtra(WebLoginActivity.EXTRA_URL, ticket.loginUrl)
                        .putExtra(WebLoginActivity.EXTRA_MODE, WebLoginActivity.MODE_CALLBACK)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure {
                notify("打开登录页失败：${it.message}")
            }
            // 用浏览器完成授权时，最可靠的回传方式是复制地址栏再粘回来
            notify("授权完成后复制浏览器地址栏的链接，回来点「粘贴回调链接导入」")
            watchBrowserLogin()
        }
    }

    // ---------- 代理 ----------

    fun saveProxy(enabled: Boolean, type: ProxyType, host: String, port: String, user: String, pass: String): String? {
        if (enabled) {
            if (host.isBlank()) return "代理主机不能为空"
            if (port.toIntOrNull() !in 1..65535) return "代理端口不合法"
        }
        engine.updateProxySettings(
            ProxySettings(
                enabled = enabled,
                type = type,
                host = host.trim(),
                port = port.toIntOrNull() ?: 8080,
                username = user.trim(),
                password = pass,
            ),
        )
        refreshAll()
        state = state.copy(message = "代理设置已保存")
        return null
    }


    fun startGateway() {
        val ctx = getApplication<Application>()
        // 没有任何账号时网关无法路由请求，直接引导去添加账号
        if (engine.pool.all().isEmpty()) {
            state = state.copy(message = "还没有账号，请先在供应商页添加", pendingTab = "providers")
            return
        }
        ContextCompat.startForegroundService(ctx, Intent(ctx, GatewayService::class.java))
        // 服务启动是异步的，这里稍后再同步一次状态
        viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            refreshAll()
        }
    }

    fun consumePendingTab() {
        state = state.copy(pendingTab = null)
    }

    fun stopGateway() {
        val ctx = getApplication<Application>()
        // 先切 UI 状态：停服务要等 Service 走完 onDestroy 才反映到 isRunning()，
        // 期间按钮仍显示「停止网关」，用户会以为点了没反应。
        state = state.copy(running = false, startedAt = 0)
        ctx.stopService(Intent(ctx, GatewayService::class.java))
        viewModelScope.launch {
            delay(500)
            refreshAll()
        }
    }

    fun addAccount(name: String, baseUrl: String, key: String): Boolean =
        addAccount(name, baseUrl, key, "custom")

    /** 按供应商添加账号。[providerId] 决定归属的 provider（custom / doubao …）。 */
    fun addAccount(name: String, baseUrl: String, key: String, providerId: String): Boolean {
        if (name.isBlank() || baseUrl.isBlank() || key.isBlank()) return false
        val uid = (providerId + name + baseUrl).hashCode().toUInt().toString(16)
        engine.pool.add(
            ProviderAccount(
                providerId = providerId,
                uid = uid,
                displayName = name.trim(),
                apiKey = key.trim(),
                baseUrl = baseUrl.trim().trimEnd('/'),
            ),
        )
        refreshAll()
        return true
    }


    /**
     * 启用免费池。
     *
     * 这些渠道免登录、免密钥，但 Engine 仍要求供应商下有账号才能路由，
     * 所以建一个占位账号承载它；不需要用户填任何东西。
     */
    fun addFreePoolAccount(): Boolean {
        if (engine.pool.enabled("freepool").isNotEmpty()) {
            notify("免费池已启用")
            return true
        }
        engine.pool.add(
            dev.aigw.provider.ProviderAccount(
                providerId = "freepool",
                uid = "free",
                displayName = "免登录通道",
                apiKey = "",
                baseUrl = "",
            ),
        )
        refreshAll()
        notify("免费池已启用")
        return true
    }

    fun removeAccount(providerId: String, uid: String) {
        engine.pool.remove(providerId, uid)
        refreshAll()
    }

    fun toggleAccount(providerId: String, uid: String, enabled: Boolean) {
        if (enabled) engine.pool.setEnabled(providerId, uid)
        else engine.pool.setDisabled(providerId, uid, "手动停用")
        refreshAll()
    }



    fun refreshModels() {
        if (state.loadingModels) return
        state = state.copy(loadingModels = true, message = null)
        viewModelScope.launch {
            val json = withContext(Dispatchers.IO) { engine.listModels(forceRefresh = true) }
            val probed = withContext(Dispatchers.IO) { engine.probeResults() }
            val items = runCatching {
                json.getAsJsonArray("data").mapNotNull { element ->
                    val obj = element.asJsonObject
                    val id = obj.get("id")?.asString ?: return@mapNotNull null
                    val providerId = obj.get("owned_by")?.asString ?: return@mapNotNull null
                    // 只保留「前缀/模型」形式：前缀可能是 codebuddy、codebuddy-cn、codebuddy-global
                    if (!id.contains('/')) return@mapNotNull null
                    val prefix = id.substringBefore('/')
                    if (!prefix.startsWith(providerId)) return@mapNotNull null
                    val probe = probed[id]
                    ModelUi(
                        id = id,
                        providerId = providerId,
                        model = id.substringAfter('/'),
                        displayName = obj.get("display_name")?.asString.orEmpty(),
                        modelType = obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString ?: "text",
                        contextWindow = obj.get("context_length")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                        maxOutputTokens = obj.get("max_output_tokens")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0L,
                        tags = obj.get("tags")?.takeIf { it.isJsonArray }
                            ?.asJsonArray?.mapNotNull { tag -> runCatching { tag.asString }.getOrNull() }
                            .orEmpty(),
                        probeOk = probe?.ok,
                        probeError = probe?.message.orEmpty(),
                    )
                }
            }.getOrDefault(emptyList())
            state = state.copy(
                models = items,
                loadingModels = false,
                message = if (items.isEmpty()) "没有可用模型（请先添加并启用账号）" else null,
            )
        }
    }

    /**
     * 逐个探测模型能否真实调用。
     *
     * 模型出现在目录里不代表账号能用它（上游按账号等级拒绝、且目录接口不返回该限制），
     * 只能实际发一次最小请求确认。
     */
    fun probeModels() {
        if (state.probingModels != null) return
        val targets = state.models.map { it.id }
        if (targets.isEmpty()) return
        state = state.copy(probingModels = 0 to targets.size)
        viewModelScope.launch {
            val results = HashMap<String, Engine.ProbeResult>()
            targets.forEachIndexed { index, id ->
                val result = withContext(Dispatchers.IO) { engine.probeModel(id) }
                results[id] = result
                // 每完成一个就刷新一次，用户能看到结果陆续出现
                state = state.copy(
                    probingModels = (index + 1) to targets.size,
                    models = state.models.map { m ->
                        val r = results[m.id] ?: return@map m
                        m.copy(probeOk = r.ok, probeError = r.message)
                    },
                )
            }
            val failed = results.values.count { !it.ok }
            state = state.copy(
                probingModels = null,
                message = if (failed == 0) "检测完成：全部可用" else "检测完成：$failed 个不可用",
            )
        }
    }

    /** 探测单个模型（模型列表里的测试按钮），结果直接回写到该模型条目。 */
    fun probeSingle(model: ModelUi) {
        val current = state.models.firstOrNull { it.id == model.id } ?: return
        if (current.probing) return
        state = state.copy(models = state.models.map { if (it.id == model.id) it.copy(probing = true) else it })
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.probeModel(model.id) }
            state = state.copy(
                models = state.models.map {
                    if (it.id == model.id) {
                        it.copy(probing = false, probeOk = result.ok, probeError = result.message)
                    } else {
                        it
                    }
                },
                message = if (result.ok) {
                    "「${model.model}」可用"
                } else {
                    "「${model.model}」不可用：${result.message}"
                },
            )
        }
    }

    /** 启用/停用某个供应商（停用后其模型不进入列表，也无法调用）。 */
    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { engine.setProviderEnabled(providerId, enabled) }
            refreshModels()
            refreshAll()
        }
    }

    /** 签到：对指定供应商（不传则全部支持的供应商）的启用账号逐个签到。 */
    fun checkinAll(providerId: String? = null) {
        val accounts = if (providerId == null) {
            engine.activityProviderIds().flatMap { engine.pool.enabled(it) }
        } else {
            engine.pool.enabled(providerId)
        }
        val label = providerId?.let { providerLabel(it) } ?: "全部供应商"
        if (accounts.isEmpty()) {
            state = state.copy(message = "没有可签到的 $label 账号")
            return
        }
        state = state.copy(message = "正在签到…")
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) {
                var done = 0
                var skipped = 0
                var failed = 0
                var firstError: String? = null
                for (entry in accounts) {
                    engine.checkin(entry.account).fold(
                        onSuccess = { if (it is CheckinResult.Done) done++ else skipped++ },
                        onFailure = {
                            failed++
                            if (firstError == null) firstError = it.message
                        },
                    )
                }
                Triple(done, skipped, failed) to firstError
            }
            refreshAll()
            val (done, skipped, failed) = counts.first
            state = state.copy(
                message = when {
                    failed == 0 && skipped == 0 -> "签到完成：$done 个账号"
                    failed == 0 -> "签到完成：$done 个成功，$skipped 个跳过（不支持签到）"
                    else -> "签到：成功 $done，跳过 $skipped，失败 $failed（${counts.second}）"
                },
            )
        }
    }

    /** 刷新余额：不传供应商则刷新全部支持额度查询的账号。 */
    /** 豆包按池内账号缓存的额度（key = "doubao:<账号名>"）。 */
    private val doubaoBalances = mutableMapOf<String, Double>()

    /** 豆包按池内账号缓存的签到状态（key 同上）。 */
    private val doubaoCheckedIn = mutableMapOf<String, Boolean>()

    fun refreshCredits(providerId: String? = null) {
        val accounts = if (providerId == null) {
            engine.activityProviderIds().flatMap { engine.pool.enabled(it) }
        } else {
            engine.pool.enabled(providerId)
        }
        val label = providerId?.let { providerLabel(it) } ?: "全部供应商"
        if (accounts.isEmpty()) {
            state = state.copy(message = "没有可刷新余额的 $label 账号")
            return
        }
        state = state.copy(message = "正在刷新余额…")
        viewModelScope.launch {
            val counts = withContext(Dispatchers.IO) {
                var ok = 0
                var failed = 0
                var firstError: String? = null
                for (entry in accounts) {
                    engine.refreshBalance(entry.account).fold(
                        onSuccess = { ok++ },
                        onFailure = {
                            failed++
                            if (firstError == null) firstError = it.message
                        },
                    )
                }
                Triple(ok, failed, firstError)
            }
            refreshAll()
            state = state.copy(
                message = if (counts.second == 0) {
                    "余额已刷新：${counts.first} 个账号"
                } else {
                    "余额刷新：成功 ${counts.first}，失败 ${counts.second}（${counts.third}）"
                },
            )
        }
    }

    /** 单账号签到（供应商详情页用）。 */
    fun checkinAccount(providerId: String, uid: String) {
        val entry = engine.pool.get(providerId, uid)
        if (entry == null) {
            state = state.copy(message = "账号不存在")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.checkin(entry.account) }
            refreshAll()
            state = state.copy(
                message = result.fold(
                    onSuccess = { when (it) {
                        is CheckinResult.Done -> it.message
                        CheckinResult.Skipped -> "该账号不支持签到"
                    } },
                    onFailure = { it.message },
                ),
            )
        }
    }

    /** 切换供应商调用区域偏好（null = 自由调用）。 */
    fun setRegionPref(providerId: String, region: String?) {
        engine.setRegionPref(providerId, region)
        refreshAll()
        state = state.copy(
            message = when (region) {
                "cn" -> "该供应商将仅用国内账号调用"
                "global" -> "该供应商将仅用国际账号调用"
                else -> "该供应商恢复国内外自由调用"
            },
        )
    }

    private fun providerLabel(providerId: String): String = when (providerId) {
        "codebuddy" -> "WorkBuddy"
        "trae" -> "Trae"
        "deepseek" -> "DeepSeek"
        "loomy" -> "Loomy"
        "mimo" -> "MiMo"
        "raccoon" -> "小浣熊"
        "chatglm" -> "智谱清言"
        "custom" -> "自定义供应商"
        "doubao" -> "豆包"
        else -> providerId
    }

    /** 单账号刷新余额（供应商详情页用）。 */
    /**
     * 进详情页时把账号显示名刷新成上游当前值。
     *
     * 静默执行：取不到就保留原名，失败不提示用户，也不影响任何功能。
     */
    fun refreshDisplayNames(providerId: String) {
        viewModelScope.launch {
            val changed = withContext(Dispatchers.IO) {
                engine.pool.all()
                    .filter { it.account.providerId == providerId }
                    .count { engine.refreshDisplayName(it.account) }
            }
            if (changed > 0) refreshAll()
        }
    }

    fun refreshAccountBalance(providerId: String, uid: String) {
        val entry = engine.pool.get(providerId, uid)
        if (entry == null) {
            state = state.copy(message = "账号不存在")
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.refreshBalance(entry.account) }
            refreshAll()
            state = state.copy(
                message = result.fold(
                    onSuccess = { if (it.known) "余额已更新" else "上游未返回余额（未知）" },
                    onFailure = { it.message },
                ),
            )
        }
    }

    /**
     * 检查登录：逐个账号用模型列表探一次上游，判断凭证是否还有效，失效的自动禁用。
     *
     * 只有实现了 TokenRefreshSupport 的供应商（Trae、智谱清言）才会先用 refreshToken 换新
     * accessToken；其余供应商没有刷新接口，只能校验（WorkBuddy 凭证里虽有 refreshToken，
     * 但上游未提供刷新端点）。不传 [providerId] 时处理全部账号。
     */
    fun reloginAll(providerId: String? = null) {
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                engine.pool.all()
                    .filter { it.state.enabled && (providerId == null || it.account.providerId == providerId) }
                    .map { entry ->
                        val r = engine.refreshLogin(entry.account)
                        if (r.isFailure) {
                            val reason = r.exceptionOrNull()?.message ?: "凭证校验失败"
                            // 连不上不是凭证失效：本机反代没起、临时断网都会走到这里，
                            // 一律禁用会把账号永久踢出池子（豆包就这样被误杀过）。
                            val unreachable = reason.contains("connect", true) ||
                                reason.contains("timeout", true) ||
                                reason.contains("refused", true) ||
                                reason.contains("unreachable", true)
                            if (!unreachable) {
                                engine.pool.setDisabled(
                                    entry.account.providerId, entry.account.uid, reason,
                                )
                            } else {
                                Log.warn("${entry.account.displayName} 校验失败但像是连不上，不禁用：$reason")
                            }
                            Triple(entry.account.displayName, false, false)
                        } else {
                            Triple(entry.account.displayName, true, r.getOrNull()?.credentialsRefreshed == true)
                        }
                    }
            }
            refreshAll()
            val ok = results.count { it.second }
            val renewed = results.count { it.second && it.third }
            val failed = results.count { !it.second }
            state = state.copy(
                message = when {
                    results.isEmpty() -> "没有启用的账号"
                    failed == 0 && renewed == 0 -> "校验登录：$ok 个账号均有效"
                    failed == 0 -> "校验登录：$ok 个账号均有效（其中 $renewed 个顺带续期）"
                    else -> "校验登录：$ok 个有效（续期 $renewed），$failed 个失效已停用"
                },
            )
        }
    }

    fun saveSettings(
        port: Int,
        bindLan: Boolean,
        balanceRefreshSeconds: Int,
        maxConcurrentPerAccount: Int,
        maxConcurrentGlobal: Int,
        failThreshold: Int,
        cooldownSeconds: Int,
        sessionSticky: Boolean,
    ): String? {
        if (port !in 1024..65535) return "端口需在 1024-65535 之间"
        if (balanceRefreshSeconds !in 10..86400) return "余额刷新间隔需在 10-86400 秒之间"
        if (maxConcurrentPerAccount !in 1..50) return "单账号并发上限需在 1-50 之间"
        if (maxConcurrentGlobal !in 0..200) return "全局并发上限需在 0-200 之间"
        if (failThreshold !in 1..50) return "连续失败阈值需在 1-50 之间"
        if (cooldownSeconds !in 5..86400) return "冷却时长需在 5-86400 秒之间"
        val s = engine.settings
        val portChanged = s.port != port
        val bindLanChanged = s.bindLan != bindLan
        if (gateway.server.isRunning() && portChanged) {
            return "请先停止网关再修改端口"
        }
        s.port = port
        s.bindLan = bindLan
        if (s.balanceRefreshSeconds != balanceRefreshSeconds) {
            s.balanceRefreshSeconds = balanceRefreshSeconds
            // 间隔变化后立即按新间隔重新计时，下次刷新即可生效
            lastAutoBalanceAt = 0L
        }
        s.maxConcurrentPerAccount = maxConcurrentPerAccount
        s.maxConcurrentGlobal = maxConcurrentGlobal
        s.failThreshold = failThreshold
        s.cooldownSeconds = cooldownSeconds
        s.sessionSticky = sessionSticky
        Settings.save(engine.dataDir, s)
        // 监听局域网开关变化时，运行中的服务需重新绑定（NanoHTTPD 在 start 时读一次 host）
        if (bindLanChanged && gateway.server.isRunning()) {
            gateway.server.stop()
            gateway.server.start()
        }
        refreshAll()
        state = state.copy(message = "设置已保存")
        return null
    }

    /** 生成新的随机密钥，并切回「必须携带 Key」模式。 */
    fun generateApiKey() {
        val s = engine.settings
        s.apiKey = Settings.generateApiKey()
        s.allowNoKey = false
        Settings.save(engine.dataDir, s)
        refreshAll()
        state = state.copy(message = "已生成新的 API 密钥")
    }

    /** 无密钥连接开关；关闭时若还没有 Key，自动生成一个。 */
    fun setNoKeyEnabled(enabled: Boolean) {
        val s = engine.settings
        if (!enabled && s.apiKey.isBlank()) s.apiKey = Settings.generateApiKey()
        s.allowNoKey = enabled
        Settings.save(engine.dataDir, s)
        refreshAll()
        state = state.copy(message = if (enabled) "已开启无密钥连接" else "已关闭无密钥连接")
    }

    /**
     * 监听局域网开关：变化时立即落盘；若服务正在运行则重启服务以重新绑定
     * （NanoHTTPD 在启动时读一次 host，不重启不生效）。
     */
    fun setBindLan(enabled: Boolean) {
        val s = engine.settings
        val changed = s.bindLan != enabled
        s.bindLan = enabled
        Settings.save(engine.dataDir, s)
        if (changed && gateway.server.isRunning()) {
            gateway.server.stop()
            gateway.server.start()
        }
        refreshAll()
        state = state.copy(message = if (enabled) "已开启监听局域网" else "已关闭监听局域网")
    }

    /** 从引擎取最近一条调用记录；无记录返回 null。 */
    fun lastCallSnapshot(): CallSnapshot? {
        val record = engine.callLog.recent(1).lastOrNull() ?: return null
        return CallSnapshot(record.model, record.via, record.ok, record.latencyMs, record.time)
    }

    /** 最近一次调用的摘要（设置页/悬浮球用）。 */
    data class CallSnapshot(
        val model: String,
        val via: String,
        val ok: Boolean,
        val latencyMs: Long,
        val time: Long,
    )

    companion object {
        /** 悬浮球开关变化广播：GatewayService 收到后立即启停悬浮窗。 */
        const val ACTION_BUBBLE_CHANGED = "dev.aigw.app.BUBBLE_CHANGED"
    }

    /**
     * 开关桌面宠物。
     *
     * 直接控制悬浮窗，不走网关服务：桌宠是 app 级的东西，
     * 挂到 GatewayService 上会导致「网关没跑就永远不显示」。
     */
    /**
     * 添加豆包账号。
     *
     * 三个字段缺一不可：Cookie 决定身份，device_id / web_id 要与之同源，
     * 否则上游会把请求当成异常设备。三者一起塞进 secret 的 JSON 里。
     */
    fun addDoubaoAccount(name: String, cookie: String, deviceId: String, webId: String): Boolean {
        if (cookie.isBlank() || deviceId.isBlank() || webId.isBlank()) {
            notify("Cookie、device_id、web_id 都不能为空")
            return false
        }
        val secret = com.google.gson.JsonObject().apply {
            addProperty("cookie", cookie.trim())
            addProperty("device_id", deviceId.trim())
            addProperty("web_id", webId.trim())
            addProperty("local_app_id", "582478")
        }.toString()
        val uid = (name + deviceId).hashCode().toUInt().toString(16)
        engine.pool.add(
            dev.aigw.provider.ProviderAccount(
                providerId = "doubao",
                uid = uid,
                displayName = name.trim().ifBlank { "豆包账号" },
                apiKey = "",
                baseUrl = "",
                secret = secret,
            ),
        )
        refreshAll()
        notify("豆包账号已添加")
        return true
    }

    /** 设置网关层系统提示词；空串表示不注入。 */
    fun setSystemPrompt(text: String) {
        engine.settings.systemPrompt = text
        Settings.save(engine.dataDir, engine.settings)
        notify(if (text.isBlank()) "已关闭系统提示词注入" else "系统提示词已保存")
        refreshAll()
    }

    /**
     * 保存模型别名表与 think 标签开关。
     *
     * @param aliases 每行 `客户端模型名=真实模型名`
     */
    fun setRewriteRules(aliases: String, stripThink: Boolean) {
        val map = mutableMapOf<String, String>()
        aliases.lines().forEach { line ->
            val raw = line.trim()
            if (raw.isEmpty() || raw.startsWith("#")) return@forEach
            val idx = raw.indexOf('=')
            if (idx <= 0) return@forEach
            val from = raw.substring(0, idx).trim()
            val to = raw.substring(idx + 1).trim()
            if (from.isNotEmpty() && to.isNotEmpty()) map[from] = to
        }
        engine.settings.modelAliases = map
        engine.settings.stripThinkTags = stripThink
        Settings.save(engine.dataDir, engine.settings)
        notify("改写规则已保存（别名 ${map.size} 条）")
        refreshAll()
    }

    /** 设置多账号选择策略。 */
    fun setAccountStrategy(strategy: String) {
        engine.settings.accountStrategy = strategy
        Settings.save(engine.dataDir, engine.settings)
        AccountSelector.reset()
        notify("账号策略已改为 $strategy")
        refreshAll()
    }

    fun setPetBubble(enabled: Boolean) {
        engine.settings.petBubble = enabled
        if (enabled) engine.settings.monitorBubble = false
        Settings.save(engine.dataDir, engine.settings)
        val app = getApplication<Application>() as AiGatewayApp
        if (enabled) {
            if (!app.petOverlay.start()) {
                notify("桌宠启动失败，请检查悬浮窗权限")
            }
        } else {
            app.petOverlay.stop()
        }
        refreshAll()
    }

    /** 开关悬浮监测球：状态落盘并通知服务立即启停悬浮窗。 */
    fun setMonitorBubble(enabled: Boolean) {
        engine.settings.monitorBubble = enabled
        if (enabled) engine.settings.petBubble = false
        Settings.save(engine.dataDir, engine.settings)
        val ctx = getApplication<Application>()
        // 服务没在跑（网关未启动）时悬浮窗本来就不该出现，只存开关即可
        if (gateway.server.isRunning()) {
            ctx.sendBroadcast(Intent(ACTION_BUBBLE_CHANGED).setPackage(ctx.packageName))
        }
        refreshAll()
    }

    /** 悬浮球透明度（0.3-1.0）：落盘并通知服务即时应用。 */
    fun setBubbleOpacity(opacity: Float) {
        engine.settings.bubbleOpacity = opacity.coerceIn(0.3f, 1f)
        Settings.save(engine.dataDir, engine.settings)
        val ctx = getApplication<Application>()
        if (gateway.server.isRunning()) {
            ctx.sendBroadcast(Intent(ACTION_BUBBLE_CHANGED).setPackage(ctx.packageName))
        }
        refreshAll()
    }

    /** 切换外观主题并落盘。 */
    fun setTheme(name: String) {
        engine.settings.themeName = name
        Settings.save(engine.dataDir, engine.settings)
        refreshAll()
    }

    /** 跳到系统「显示在其它应用上层」授权页。 */
    fun openOverlayPermissionSettings() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /**
     * 从授权页返回后重新检测权限并同步悬浮球：
     * 授权是在 App 外完成的，服务不知道，必须在这里补一次启停。
     */
    fun recheckOverlayPermission() {
        val granted = android.provider.Settings.canDrawOverlays(getApplication<Application>())
        if (granted != state.canDrawOverlays) {
            if (gateway.server.isRunning()) {
                val ctx = getApplication<Application>()
                ctx.sendBroadcast(Intent(ACTION_BUBBLE_CHANGED).setPackage(ctx.packageName))
            }
        }
        refreshAll()
    }

    fun clearLogs() {
        engine.eventLog.clear()
        refreshAll()
    }

    /** 导出全部运行日志（供一键复制）。 */
    fun exportLogs(): String = engine.eventLog.exportText()

    /** 格式化单条日志，和 exportText 保持统一，供点击复制单条。 */
    fun formatEvent(event: EventUi): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
        return "${sdf.format(java.util.Date(event.time))} [${event.level}] [${event.tag}] ${event.message}"
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    /** 底部弹层打开/关闭同步（导航层据此隐藏底部导航）。 */
    fun setSheetOpen(open: Boolean) {
        if (state.sheetOpen != open) state = state.copy(sheetOpen = open)
    }

    /** 公告已展示，清掉待弹缓存避免重复弹；最近一次内容仍保留，供公告入口再次查看。 */
    fun consumeAnnouncement() {
        engine.consumeAnnouncement()
        state = state.copy(announcement = null)
    }

    /** 仪表盘公告入口：重新弹出最近一次系统公告。 */
    fun showLastAnnouncement() {
        val ann = lastAnnouncement
        if (ann == null) {
            state = state.copy(message = "暂无系统公告")
        } else {
            state = state.copy(announcement = ann)
        }
    }

    /** 更新提示已处理（下载或稍后），清掉缓存。 */
    fun consumeUpdate() {
        engine.consumeUpdate()
        state = state.copy(updateInfo = null)
    }

    /** 用系统浏览器打开 APK 下载地址（不用 WebView，也不在 App 内下载）。 */
    /**
     * 用系统浏览器打开 APK 下载地址。
     *
     * 后台允许只填裸域名（如 `www.baidu.com`），这种字符串直接 `Uri.parse` 会得到
     * 无 scheme 的 URI，系统找不到 Handler 并报 “No Activity found to handle Intent”。
     * 所以这里先补全成 `https://` 完整地址。
     */
    /**
     * 用系统浏览器打开下载地址。
     *
     * 后台可能只填裸域名（如 `www.baidu.com`），直接 `Uri.parse` 会得到无 scheme
     * 的 URI，系统找不到 Handler；这里统一补全协议头后再跳转。
     */
    fun openUpdateUrl(url: String) {
        val ctx = getApplication<Application>()
        val normalized = normalizeUrl(url)
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(normalized))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure { notify("没有可打开该链接的应用") }
        consumeUpdate()
    }

    /** 补全后台可能省略的协议头；空串时退化为 about:blank。 */
    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "about:blank"
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        return "https://$trimmed"
    }

    /** 只刷新远程公告/版本（日志页与首页轮询用，避免整页快照开销）。 */
    fun refreshRemoteMessages() {
        viewModelScope.launch {
            val (ann, upd) = withContext(Dispatchers.IO) {
                engine.pollAnnouncementAndUpdate(gateway.appVersion)
                engine.pendingAnnouncement?.let { AnnouncementUi(it.title, it.content) } to
                    engine.pendingUpdate?.let { UpdateUi(it.version, it.url, it.force, it.notes) }
            }
            if (ann != state.announcement || upd != state.updateInfo) {
                state = state.copy(announcement = ann, updateInfo = upd)
            }
            ann?.let { lastAnnouncement = it }
        }
    }

    /** 展示一条提示（Snackbar）。 */
    fun notify(message: String) {
        state = state.copy(message = message)
    }
}
