package dev.aigw.provider

import com.google.gson.JsonObject

/** 账号凭证：一个 uid 对应一份登录态/API Key。 */
data class ProviderAccount(
    val providerId: String,
    val uid: String,
    val displayName: String,
    val apiKey: String = "",
    val baseUrl: String = "",
    /** 复杂凭证（如 WorkBuddy 的 accessToken/refreshToken JSON）。 */
    val secret: String = "",
    val extra: Map<String, String> = emptyMap(),
)

/** 账号运行状态（额度 / 启用 / 禁用），独立持久化。 */
data class AccountState(
    var enabled: Boolean = true,
    var disabledReason: String? = null,
    /** 积分余额；null 表示未知（不能当 0 用）。 */
    var balance: Double? = null,
    var lastError: String? = null,
    /** 最近一次错误时间（0 表示没错误）。 */
    var lastErrorAt: Long = 0,
    var lastBalanceAt: Long = 0,
    var lastCheckinAt: Long = 0,
    /** 企业号：个人签到/成长体系不适用。 */
    var enterprise: Boolean = false,
    /** 连续失败次数（成功即清零）。 */
    var consecutiveFailures: Int = 0,
    /** 冷却截止时间戳；未冷却为 0。 */
    var cooldownUntil: Long = 0,
) {
    /** 是否在冷却期内（到期自动恢复，不硬禁用）。 */
    fun inCooldown(now: Long = System.currentTimeMillis()): Boolean = cooldownUntil > now

    /** 今天（本机时区）是否已签到。 */
    fun checkedInToday(now: Long = System.currentTimeMillis()): Boolean {
        if (lastCheckinAt <= 0) return false
        val zone = java.time.ZoneId.systemDefault()
        val last = java.time.Instant.ofEpochMilli(lastCheckinAt).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return last == today
    }
}

/** 积分余额查询结果。[remain] 为负表示上游没返回余额（未知），与 0 区分。 */
data class BalanceInfo(
    val remain: Double,
    val earliestAt: Long = 0,
    val earliestRemaining: Double = 0.0,
    val enterprise: Boolean = false,
) {
    val known: Boolean get() = remain >= 0
}

/** OpenAI 语义的上游请求。 */
class ChatRequest(
    val model: String,
    val body: JsonObject,
    val stream: Boolean,
)

/** 流式回调：上游 SSE 事件逐条送达（data 为原始行，不含 "data: " 前缀）。 */
interface SseSink {
    fun onData(data: String)
    fun onDone()
}

sealed class ChatResult {
    /** 流式完成；[inputTokens]/[outputTokens] 取自上游最后一个 usage 事件，拿不到则为 0。 */
    class Streamed(
        val events: Int,
        val inputTokens: Int = 0,
        val outputTokens: Int = 0,
        /** 提示词缓存命中 token；上游未上报则为 0。 */
        val cachedTokens: Int = 0,
    ) : ChatResult()
    class Aggregated(val json: JsonObject) : ChatResult()
    class Failure(val error: ApiError) : ChatResult()
}

/** 一次调用的 token 用量，含缓存命中。 */
data class UsageTokens(
    val input: Int = 0,
    val output: Int = 0,
    val cached: Int = 0,
)

class ApiError(
    val kind: Kind,
    val status: Int,
    val message: String,
) {
    enum class Kind {
        /** 401/403 等凭证失效：硬禁用该账号，换号。 */
        AUTH,
        /** 4xx 客户端错误：不换号，直接回给客户端。 */
        CLIENT,
        /** 上游 5xx 或业务级失败：换号。 */
        UPSTREAM,
        /** 网络不可达 / 超时：换号。 */
        NETWORK,
        /** HTTP 200 但空 body：按失败处理。 */
        EMPTY,
    }

    fun isAccountDead(): Boolean = kind == Kind.AUTH

    override fun toString(): String = "$kind($status): $message"
}

/** 设备授权登录（WorkBuddy）：打开 loginUrl，按 state 轮询换取凭证。 */
data class DeviceAuthTicket(val loginUrl: String, val state: String)

sealed interface DeviceAuthPoll {
    data object Pending : DeviceAuthPoll
    data class Success(val account: ProviderAccount) : DeviceAuthPoll
    data class Failed(val error: String) : DeviceAuthPoll
}

interface DeviceCodeSupport {
    fun startDeviceAuth(region: String = ""): DeviceAuthTicket
    fun pollDeviceAuth(state: String, region: String = ""): DeviceAuthPoll
}

/** 区域感知供应商：账号可带区域（国内/国际），网关按区域筛选候选。 */
interface RegionAwareSupport {
    fun regionOf(account: ProviderAccount): String?
    fun regions(): List<String>
}

/** 签到结果。 */
sealed interface CheckinResult {
    /** 已签到（含幂等「今日已签到」）。 */
    data class Done(val message: String) : CheckinResult

    /** 该账号不适用（如企业号 / 国际版无签到），不算失败也不记签到时间。 */
    data object Skipped : CheckinResult
}

/** 账号运营能力：每日签到与积分余额。 */
interface AccountActivitySupport {
    /** 签到；返回结果描述（含「今日已签到」这类幂等成功）。 */
    fun checkin(account: ProviderAccount): Result<CheckinResult>

    /** 查询积分余额。 */
    fun balance(account: ProviderAccount): Result<BalanceInfo>
}

/**
 * 浏览器回调式登录（Trae）：打开 [beginWebLogin] 返回的授权页，
 * 登录完成后上游会重定向到本机回调地址，由 [completeWebLogin] 换出账号。
 */
interface WebCallbackSupport {
    /**
     * 本地回调端口。默认沿用 Trae 的 51120；上游会逐字符校验 redirect_uri 的
     * 供应商必须声明自己的固定端口与路径。
     */
    val callbackPort: Int get() = 51120

    /** 本地回调路径。默认是 Trae 的回调路径，其它供应商可覆盖。 */
    val callbackPath: String get() = "/authorize"

    /** 生成授权页 URL；[callbackUrl] 是本机回调地址（含端口与路径）。 */
    fun beginWebLogin(region: String, callbackUrl: String): String

    /** 处理回调链接，返回可落盘的账号。 */
    fun completeWebLogin(callbackUrl: String): ProviderAccount
}

/** 粘贴凭证导入：直接用已导出的凭证 JSON 换账号，省去浏览器登录。 */
interface CredentialImportSupport {
    fun importCredentials(raw: String): ProviderAccount
}

/** 网页登录后从页面里取 token 的供应商（如 DeepSeek）：没有 OAuth 回调。 */
interface WebTokenSupport {
    /** 登录页地址，由 WebView 打开。 */
    val loginPageUrl: String

    /** 用页面里取到的 token 换账号（需校验并补全用户信息）。 */
    fun loginWithToken(token: String): ProviderAccount
}

/** 模型的展示元数据（上游没给的字段一律留 0 / 空，不猜）。 */
data class ModelInfo(
    val id: String,
    val displayName: String = "",
    val contextWindow: Long = 0,
    val maxOutputTokens: Long = 0,
    val tags: List<String> = emptyList(),
)

/** 供应商可提供比模型 ID 更丰富的展示信息（上下文、倍率、能力标签）。 */
interface ModelCatalogSupport {
    fun modelCatalog(account: ProviderAccount): List<ModelInfo>
}

/** 供应商边界：OpenAI 进、OpenAI 出；上游协议差异全部封死在实现内。 */
interface Provider {
    val id: String
    val displayName: String
    fun listModels(account: ProviderAccount): Result<List<String>>
    fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult
}

/** 支持用已有凭证换新凭证（refreshToken → 新 accessToken）的供应商。 */
interface TokenRefreshSupport {
    /** 返回换新后的账号；无 refreshToken 或上游拒绝时抛错。 */
    fun refreshCredentials(account: ProviderAccount): ProviderAccount
}

/** 手机号 + 短信验证码登录（Loomy）。 */
interface SmsLoginSupport {
    /**
     * 发验证码，返回上游 msgid（登录时回传）。
     *
     * [ccode] 是国家码（不带 `+`，如中国大陆 `86`、香港 `852`）。
     * 上游按国家码路由短信通道，写死 86 会让非大陆号码收不到验证码。
     */
    fun sendSmsCode(phone: String, ccode: String = "86"): String

    fun loginBySmsCode(phone: String, code: String, msgid: String, ccode: String = "86"): ProviderAccount
}

/** provider 专属动作的结果。 */
data class ProviderActionResult(
    val ok: Boolean,
    val message: String,
    val unsupported: Boolean = false,
) {
    companion object {
        fun success(message: String = "") = ProviderActionResult(true, message)
        fun failure(message: String) = ProviderActionResult(false, message)
        fun unsupported(action: String) =
            ProviderActionResult(false, "该供应商不支持「$action」", unsupported = true)
    }
}

/** provider 专属动作（签到、兑换码、任务等）。 */
interface ProviderActionSupport {
    fun performAction(account: ProviderAccount, action: String, payload: JsonObject): ProviderActionResult
}
