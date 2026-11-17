package dev.aigw.provider.trae

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.Http
import dev.aigw.util.objOrNull

/** 签到状态。 */
data class CheckinStatus(
    val checkedIn: Boolean,
    val credits: Long,
    val enabled: Boolean,
)

/** 签到结果。 */
enum class ClaimOutcome {
    /** 本次成功领取。 */
    CLAIMED,

    /** 账号今天已经签过（不算失败）。 */
    ALREADY_CHECKED_IN,
}

/** 一个额度包（Trae 的奖励按包发放）。 */
data class CreditPack(
    val name: String,
    val group: String,
    val limit: Long,
    val used: Long,
    val remain: Long,
    val expireAt: Long,
)

/** 额度明细；[parsed] 表示响应里确实解析到了权益包列表。 */
data class EntUsage(
    val remain: Long,
    val limit: Long,
    val used: Long,
    val packs: Int,
    val endpoint: String = "",
    val parsed: Boolean = false,
    val rawPreview: String = "",
    val details: List<CreditPack> = emptyList(),
)

/**
 * 凭证刷新与账号信息。
 *
 * ExchangeToken 每次都会轮换 refreshToken，刷新成功后调用方必须立刻写回。
 */
class TraeAuthClient(private val version: TraeVersion) {

    /** 鉴权 host：凭证自带的 apiHost 优先，缺省时按区域取 OAuth host。 */
    internal fun authHost(account: TraeAccount): String {
        val region = TraeRegion.infer(account.domain, account.apiHost)
        val host = account.apiHost.ifEmpty { region.oauthHost }
        return if (region == TraeRegion.INTL && host == TraeRegion.CN.oauthHost) region.oauthHost else host
    }

    /** refreshToken → 新 accessToken，并轮换 refreshToken。 */
    fun exchangeToken(account: TraeAccount): TraeAccount {
        if (account.refreshToken.isBlank()) {
            throw TraeHttpException(0, TraeErrorKind.SESSION_DEAD, "缺少 refreshToken，需要重新登录")
        }
        val body = JsonObject().apply {
            addProperty("ClientID", TraeConstants.CLIENT_ID)
            addProperty("RefreshToken", account.refreshToken)
            addProperty("ClientSecret", "-")
            addProperty("UserID", "")
        }
        val result = Http.postJson(
            authHost(account) + TraeConstants.EP_EXCHANGE,
            TraeHeaders.oauth(version),
            body.toString(),
            TIMEOUT_MS,
        )
        if (!result.ok) {
            throw TraeHttpException(
                result.status,
                TraeErrors.fromStatus(result.status, result.body),
                result.body,
                TraeErrors.extractCode(result.body),
            )
        }
        val parsed = runCatching { JsonParser.parseString(result.body).asJsonObject }.getOrNull()
            ?: throw TraeHttpException(result.status, TraeErrorKind.SERVER, "ExchangeToken 响应无法解析")
        val data = parsed.objOrNull("Result") ?: parsed.objOrNull("result") ?: parsed
        val token = data.str("Token")
        if (token.isEmpty()) {
            throw TraeHttpException(result.status, TraeErrorKind.SESSION_DEAD, "ExchangeToken 未返回 Token，需要重新登录")
        }
        return account.withTokens(
            accessToken = token,
            refreshToken = data.str("RefreshToken").ifEmpty { account.refreshToken },
            expiresAt = expiresAtOf(data.long("TokenExpireAt"), data.long("TokenExpireDuration"), nowSeconds()),
        )
    }

    /** 补全 / 刷新账号身份信息。 */
    fun getUserInfo(account: TraeAccount): TraeAccount {
        val body = JsonObject().apply {
            addProperty("ReqSource", "IDE")
            addProperty("IDEVersion", version.ideVersion)
        }
        val headers = TraeHeaders.oauth(version).toMutableMap().apply {
            put("X-Cloudide-Token", account.accessToken)
        }
        val result = Http.postJson(
            authHost(account) + TraeConstants.EP_USER_INFO,
            headers,
            body.toString(),
            TIMEOUT_MS,
        )
        if (!result.ok) {
            throw TraeHttpException(
                result.status,
                TraeErrors.fromStatus(result.status, result.body),
                result.body,
                TraeErrors.extractCode(result.body),
            )
        }
        val parsed = runCatching { JsonParser.parseString(result.body).asJsonObject }.getOrNull()
            ?: throw TraeHttpException(result.status, TraeErrorKind.SERVER, "GetUserInfo 响应无法解析")
        val data = parsed.objOrNull("Result") ?: parsed.objOrNull("result") ?: parsed
        return account.withIdentity(
            uid = data.str("UserID"),
            nickname = data.str("ScreenName"),
            enterpriseId = data.str("EnterpriseID"),
        )
    }

    /** 仅在 token 将过期时刷新；返回 null 表示无需刷新。 */
    fun refreshIfNeeded(account: TraeAccount, withinSeconds: Long): TraeAccount? {
        if (!account.needsRefresh(withinSeconds, nowSeconds())) return null
        return exchangeToken(account)
    }

    companion object {
        private const val TIMEOUT_MS = 30_000

        fun expiresAtOf(tokenExpireAt: Long, tokenExpireDuration: Long, now: Long): Long = when {
            tokenExpireAt > 0L -> normalizeExpireAt(tokenExpireAt)
            tokenExpireDuration > 0L -> now + tokenExpireDuration
            else -> 0L
        }
    }
}

/**
 * 签到与额度（ug host）。
 *
 * 上游这几个接口在不同入口/客户端版本上结构不一致（顶层字段 vs `data` 包裹、
 * `enable` vs `enabled`），所以解析全部走容错分支，并把命中的接口带回，
 * 避免出现「HTTP 200 但静默显示 0」。
 */
class TraeCheckinClient(
    private val version: TraeVersion,
    private val host: String,
    private val region: TraeRegion,
) {

    fun status(account: TraeAccount): CheckinStatus {
        val raw = post(account, TraeConstants.EP_CHECKIN_STATUS, "{}")
        return TraeUgParse.parseStatus(raw)
            ?: throw TraeHttpException(200, TraeErrorKind.SERVER, "签到状态响应无法解析：${raw.take(PREVIEW_LIMIT)}")
    }

    /**
     * 执行签到。上游即使业务失败也返回 HTTP 200，所以必须看 body 里的 code：
     * 1001 或含「已签到」类文案视为今天已签；其余非成功码按失败抛出。
     */
    fun claim(account: TraeAccount): ClaimOutcome {
        val raw = post(account, TraeConstants.EP_CHECKIN_CLAIM, "{}")
        if (TraeUgParse.succeeded(raw)) return ClaimOutcome.CLAIMED
        if (TraeUgParse.alreadyCheckedIn(raw)) return ClaimOutcome.ALREADY_CHECKED_IN

        val code = TraeErrors.extractCode(raw)
        val message = TraeErrors.extractMessage(raw)
        throw TraeHttpException(200, TraeErrorKind.CLIENT, message.ifEmpty { "签到失败（code=$code）" }, code)
    }

    /** 查询额度：按候选接口顺序尝试，任一接口解析出权益包列表就返回。 */
    fun entUsage(account: TraeAccount): EntUsage {
        var preview = ""
        var lastError: Exception? = null

        val candidates = if (region == TraeRegion.INTL) ENT_ENDPOINTS_INTL else ENT_ENDPOINTS_CN
        for (endpoint in candidates) {
            val raw = try {
                post(account, endpoint.path, endpoint.body)
            } catch (e: Exception) {
                lastError = e
                continue
            }
            if (preview.isEmpty()) preview = raw.take(PREVIEW_LIMIT)

            val usage = TraeUgParse.aggregateEntitlement(raw) ?: continue
            return usage.copy(endpoint = endpoint.path, parsed = true, rawPreview = raw.take(PREVIEW_LIMIT))
        }

        if (preview.isEmpty() && lastError != null) throw lastError
        return EntUsage(remain = 0, limit = 0, used = 0, packs = 0, rawPreview = preview)
    }

    private fun post(account: TraeAccount, path: String, body: String): String {
        val result = Http.postJson(host + path, TraeHeaders.ug(account, version, region), body, TIMEOUT_MS)
        if (!result.ok) {
            throw TraeHttpException(
                result.status,
                TraeErrors.fromStatus(result.status, result.body),
                result.body,
                TraeErrors.extractCode(result.body),
            )
        }
        return result.body
    }

    private companion object {
        const val TIMEOUT_MS = 30_000
        const val PREVIEW_LIMIT = 2_000

        data class EntEndpoint(val path: String, val body: String)

        val ENT_ENDPOINTS_CN = listOf(
            EntEndpoint(TraeConstants.EP_ENT_USAGE_WEB, """{"require_usage":true}"""),
            EntEndpoint(TraeConstants.EP_ENT_USAGE, "{}"),
            EntEndpoint(TraeConstants.EP_ENTITLEMENT_LIST, """{"require_usage":true}"""),
        )

        val ENT_ENDPOINTS_INTL = listOf(
            EntEndpoint(TraeConstants.EP_ENTITLEMENT_INTL, """{"require_usage":true}"""),
            EntEndpoint(TraeConstants.EP_ENT_USAGE_INTL, "{}"),
        )
    }
}

/** 签到与额度接口的响应解析，抽出来便于单测容错分支。 */
internal object TraeUgParse {

    /** 上游把「今天已签」也用 1001 表示。 */
    private const val ALREADY_CODE = 1001L

    private val ALREADY_MARKERS = listOf(
        "已签到", "已经签到", "明日再来", "今日已完成", "已领取", "already", "checked",
    )

    fun parseStatus(raw: String): CheckinStatus? {
        val root = parseObject(raw) ?: return null
        val scope = unwrap(root)
        return CheckinStatus(
            checkedIn = scope.bool("checked_in") ?: scope.bool("checkedIn") ?: false,
            credits = scope.long("credits"),
            enabled = scope.bool("enable") ?: scope.bool("enabled") ?: false,
        )
    }

    /** 聚合权益包：remain = Σ(credits_limit - credits_amount)。返回 null 表示没有权益包列表。 */
    fun aggregateEntitlement(raw: String): EntUsage? {
        val packs = findPacks(raw) ?: return null
        var remain = 0L
        var limit = 0L
        var used = 0L
        val details = ArrayList<CreditPack>()

        for (element in packs) {
            val pack = element?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val base = pack.objOrNull("entitlement_base_info")
            val creditsLimit = base?.objOrNull("quota")?.long("credits_limit") ?: 0L
            if (creditsLimit <= 0L) continue
            val creditsUsed = pack.objOrNull("usage")?.get("credits_amount")
                ?.let { runCatching { it.asDouble }.getOrDefault(0.0) }?.toLong() ?: 0L

            val group = pack.str("group_name")
            val name = pack.str("display_desc").ifEmpty { group }
                .ifEmpty { base?.objOrNull("product_extra")?.objOrNull("package_extra")?.str("package_name").orEmpty() }
                .ifEmpty { "额度包" }

            limit += creditsLimit
            used += creditsUsed
            remain += creditsLimit - creditsUsed
            details.add(
                CreditPack(
                    name = name,
                    group = group,
                    limit = creditsLimit,
                    used = creditsUsed,
                    remain = creditsLimit - creditsUsed,
                    expireAt = firstPositive(pack.long("expire_time"), base?.long("end_time") ?: 0L),
                ),
            )
        }
        return EntUsage(
            remain = remain,
            limit = limit,
            used = used,
            packs = details.size,
            details = details,
        )
    }

    /** 业务是否成功。没有 code / success / status 字段时按成功处理。 */
    fun succeeded(raw: String): Boolean {
        val root = parseObject(raw) ?: return true
        val code = root.long("code")
        if (code == 0L || code == 200L) return true
        if (root.bool("success") == true) return true
        if (root.str("status").equals("success", ignoreCase = true)) return true
        return !root.has("code") && !root.has("success") && !root.has("status")
    }

    fun alreadyCheckedIn(raw: String): Boolean {
        val code = TraeErrors.extractCode(raw)
        if (code == ALREADY_CODE) return true
        val message = TraeErrors.extractMessage(raw)
        return ALREADY_MARKERS.any { message.contains(it, ignoreCase = true) }
    }

    private fun findPacks(raw: String): JsonArray? {
        val root = parseObject(raw) ?: return null
        return root.get("user_entitlement_pack_list")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.objOrNull("data")?.get("user_entitlement_pack_list")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.objOrNull("result")?.get("user_entitlement_pack_list")?.takeIf { it.isJsonArray }?.asJsonArray
            ?: root.objOrNull("Result")?.get("user_entitlement_pack_list")?.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun unwrap(root: JsonObject): JsonObject =
        root.objOrNull("data") ?: root.objOrNull("result") ?: root.objOrNull("Result") ?: root

    private fun parseObject(raw: String): JsonObject? =
        runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()

    private fun firstPositive(vararg values: Long): Long = values.firstOrNull { it > 0 } ?: 0L
}

/** 上游模型目录的一项。 */
data class TraeModel(
    val id: String,
    val name: String,
    /** 上游 max_input_tokens；未给时为 0。 */
    val contextWindow: Long = 0,
    /** 上游 max_output_tokens；未给时为 0。 */
    val maxOutputTokens: Long = 0,
)

/** `get_detail_param` 的请求体与响应解析。 */
internal object TraeModels {

    fun requestBody(): String = JsonObject().apply {
        addProperty("function", TraeConstants.FUNCTION)
        add("config_names", null)
        addProperty("need_prompt", false)
        add("current_config_info", null)
        addProperty("poly_prompt", true)
        add("mode_type", null)
        add("agent_type", null)
    }.toString()

    fun parse(raw: String): List<TraeModel> {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
        val list = root.get("config_info_list")?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        val out = ArrayList<TraeModel>(list.size())
        for (element in list) {
            val cfg = element?.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val id = cfg.str("config_name")
            if (id.isEmpty()) continue
            val display = cfg.objOrNull("display_config")?.str("display_name").orEmpty()
            val context = cfg.long("max_input_tokens").takeIf { it > 0 }
                ?: cfg.long("max_context_tokens").takeIf { it > 0 }
                ?: 0L
            val maxOut = cfg.long("max_output_tokens").takeIf { it > 0 } ?: 0L
            out.add(TraeModel(id = id, name = display.ifEmpty { id }, contextWindow = context, maxOutputTokens = maxOut))
        }
        return out
    }
}
