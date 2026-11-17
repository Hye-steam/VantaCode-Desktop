package dev.aigw.provider.trae

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.objOrNull
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Trae 两种发行版的上游地址。
 *
 * 国内（trae.cn）与海外（trae.ai）是三套不同的 host：对话走 agent host，
 * 额度/签到走 ug host，换 token 走 oauth host。国际版额度接口是 v1（国内 v2），
 * 且没有签到制度。
 */
enum class TraeRegion(
    val id: String,
    val label: String,
    val agentHost: String,
    val ugHost: String,
    val oauthHost: String,
    val consoleHost: String,
) {
    CN(
        id = "cn",
        label = "国内版",
        agentHost = "https://trae-api-cn.mchost.guru",
        ugHost = "https://api.trae.cn",
        oauthHost = "https://api.trae.com.cn",
        consoleHost = "https://www.trae.cn",
    ),
    INTL(
        id = "global",
        label = "国际版",
        agentHost = "https://trae-api-sg.mchost.guru",
        ugHost = "https://ug-normal.trae.ai",
        oauthHost = "https://api.trae.ai",
        consoleHost = "https://www.trae.ai",
    ),
    ;

    /** 额度/支付接口的版本段：国内 v2，国际 v1。 */
    val payVersion: String get() = if (this == INTL) "v1" else "v2"

    /** 国际版没有签到制度。 */
    val supportsCheckin: Boolean get() = this == CN

    companion object {
        fun of(id: String?): TraeRegion =
            entries.firstOrNull { it.id.equals(id?.trim(), ignoreCase = true) } ?: CN

        /** 凭证里的 domain 与 apiHost 都可能带区域信息，两个都看。 */
        fun infer(explicit: String?, domain: String?): TraeRegion {
            val values = listOf(explicit, domain).map { it?.trim().orEmpty() }
            if (values.any { it.equals(CN.id, ignoreCase = true) }) return CN
            if (values.any { it.equals(INTL.id, ignoreCase = true) }) return INTL
            return if (values.any { it.lowercase().contains("trae.ai") }) INTL else CN
        }
    }
}

/** Trae 上游常量。取值来自实测，改动需重新验证。 */
object TraeConstants {
    const val CLIENT_ID = "en1oxy7wnw8j9n"
    const val APP_ID = "6eefa01c-1036-4c7e-9ca5-d891f63bfcd8"

    /** 上游只放行该 function 取值，work / solo / work_lite 均无效。 */
    const val FUNCTION = "solo_work_lite"

    const val EP_CHAT = "/api/agent/v3/llm_utils_chat"
    const val EP_MODELS = "/api/ide/v1/get_detail_param"
    const val EP_EXCHANGE = "/cloudide/api/v3/trae/oauth/ExchangeToken"
    const val EP_USER_INFO = "/cloudide/api/v3/trae/GetUserInfo"
    const val EP_CHECKIN_STATUS = "/trae/api/v2/ug/checkin_credits/status"
    const val EP_CHECKIN_CLAIM = "/trae/api/v2/ug/checkin_credits/claim"

    /** 额度候选接口（国内 v2）：网页版响应最完整，放最前。 */
    const val EP_ENT_USAGE_WEB = "/trae/api/v2/pay/web_user_ent_usage"
    const val EP_ENT_USAGE = "/trae/api/v2/pay/ide_user_ent_usage"
    const val EP_ENTITLEMENT_LIST = "/trae/api/v2/pay/user_current_entitlement_list"

    /** 国际版（v1）额度接口，没有 web 版。 */
    const val EP_ENTITLEMENT_INTL = "/trae/api/v1/pay/user_current_entitlement_list"
    const val EP_ENT_USAGE_INTL = "/trae/api/v1/pay/ide_user_ent_usage"

    const val DEFAULT_CONFIG_NAME = "glm-5.2"
    const val REGION_CN = "trae.cn"

    /** 登录回调端口（Trae 客户端注册的固定端口）。 */
    const val CALLBACK_PORT = 51120
    const val CALLBACK_PATH = "/authorize"
}

/** 客户端版本号：上游按 X-Ide-Version 决定模型可用性，过旧会返回 4001。 */
data class TraeVersion(
    val ideVersion: String = "0.1.52",
    val ideVersionCode: String = "20260811",
    val pluginVersion: String = "2.3.62834",
    val deviceBrand: String = "83DG",
    val osVersion: String = "Windows 11 Pro",
)

/**
 * 一个 Trae 账号的凭证。
 *
 * accessToken 即 `Cloud-IDE-JWT` 头；refreshToken 每次 ExchangeToken 都会轮换，
 * 因此刷新成功后必须写回。
 */
data class TraeAccount(
    val uid: String,
    val accessToken: String,
    val refreshToken: String,
    /** accessToken 过期时刻（Unix 秒），0 表示未知。 */
    val expiresAt: Long,
    val nickname: String,
    val enterpriseId: String,
    val machineId: String,
    val deviceId: String,
    val domain: String = TraeConstants.REGION_CN,
    /** 鉴权 host；空串时按区域取该区域的 OAuth host。 */
    val apiHost: String = "",
) {
    fun needsRefresh(withinSeconds: Long, nowSeconds: Long): Boolean =
        expiresAt <= 0L || nowSeconds + withinSeconds >= expiresAt

    fun withTokens(accessToken: String, refreshToken: String, expiresAt: Long): TraeAccount =
        copy(accessToken = accessToken, refreshToken = refreshToken, expiresAt = expiresAt)

    fun withIdentity(uid: String, nickname: String, enterpriseId: String): TraeAccount =
        copy(
            uid = uid.ifEmpty { this.uid },
            nickname = nickname.ifEmpty { this.nickname },
            enterpriseId = enterpriseId.ifEmpty { this.enterpriseId },
        )

    fun toJson(): JsonObject {
        val auth = JsonObject().apply {
            addProperty("accessToken", accessToken)
            addProperty("refreshToken", refreshToken)
            addProperty("expiresAt", expiresAt)
            addProperty("domain", domain)
            addProperty("apiHost", apiHost)
            addProperty("machineId", machineId)
            addProperty("deviceId", deviceId)
        }
        val account = JsonObject().apply {
            addProperty("uid", uid)
            addProperty("enterpriseId", enterpriseId)
            addProperty("nickname", nickname)
        }
        return JsonObject().apply {
            add("auth", auth)
            add("account", account)
        }
    }

    companion object {
        /** 同时兼容嵌套形 `{auth:{...},account:{...}}` 与扁平形。 */
        fun parse(raw: String): TraeAccount {
            val root = JsonParser.parseString(raw).asJsonObject
            val nested = root.has("auth")
            val auth = if (nested) root.objOrNull("auth") ?: JsonObject() else root
            val account = if (nested) root.objOrNull("account") ?: JsonObject() else root

            val accessToken = auth.str("accessToken")
            if (accessToken.isEmpty()) throw IllegalArgumentException("凭证缺少 accessToken")

            return TraeAccount(
                uid = account.str("uid"),
                accessToken = accessToken,
                refreshToken = auth.str("refreshToken"),
                expiresAt = auth.long("expiresAt"),
                nickname = account.str("nickname"),
                enterpriseId = account.str("enterpriseId"),
                machineId = auth.str("machineId"),
                deviceId = auth.str("deviceId"),
                domain = auth.str("domain").ifEmpty { TraeConstants.REGION_CN },
                apiHost = auth.str("apiHost"),
            )
        }
    }
}

internal fun JsonObject.str(key: String): String {
    val v = get(key) ?: return ""
    if (v.isJsonNull) return ""
    return runCatching { v.asString }.getOrDefault("")
}

internal fun JsonObject.long(key: String): Long {
    val v = get(key) ?: return 0L
    if (v.isJsonNull) return 0L
    return runCatching { v.asLong }.getOrDefault(0L)
}

internal fun JsonObject.bool(key: String): Boolean? =
    get(key)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asBoolean }.getOrNull() }

/** 三类上游请求所需的头部，规则来自逆向实测，缺一项即可能被拒。 */
object TraeHeaders {

    private fun ua(v: TraeVersion) = "Trae/" + v.ideVersion

    /** llm_utils_chat / get_detail_param 使用的 SOLO 专属头。 */
    fun solo(account: TraeAccount, v: TraeVersion, stream: Boolean): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", if (stream) "text/event-stream" else "application/json")
        put("User-Agent", ua(v))
        put("Authorization", "Cloud-IDE-JWT " + account.accessToken)
        put("X-Cloudide-Token", account.accessToken)
        put("X-Ide-Token", account.accessToken)
        if (account.uid.isNotEmpty()) put("X-Uid", account.uid)
        put("X-App-Id", TraeConstants.APP_ID)
        put("X-App-Version", "default")
        put("X-Ide-Version", v.ideVersion)
        put("X-Ide-Version-Code", v.ideVersionCode)
        put("X-App-Version-Code", v.ideVersionCode)
        put("X-Ide-Version-Type", "stable")
        put("X-Device-Type", "windows")
        put("X-OS-Version", v.osVersion)
        put("X-Device-Brand", v.deviceBrand)
        put("Request-Traffic-Type", "prod")
        if (account.machineId.isNotEmpty()) put("X-Machine-Id", account.machineId)
        if (account.deviceId.isNotEmpty()) put("x-device-id", account.deviceId)
    }

    /**
     * 签到 / 额度（ug host）使用的头。
     *
     * 设备指纹头是必须的：UG 接口会校验设备指纹，缺任一项上游会以 9074
     * 「当前参与用户太多」为由拒绝签到。
     */
    fun ug(account: TraeAccount, v: TraeVersion, region: TraeRegion = TraeRegion.CN): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        put("User-Agent", ua(v))
        put("Authorization", "Cloud-IDE-JWT " + account.accessToken)
        put("X-User-Region", if (region == TraeRegion.INTL) "US" else "CN")
        if (account.uid.isNotEmpty()) put("X-Uid", account.uid)
        put("x-device-brand", v.deviceBrand)
        put("x-device-type", "windows")
        put("x-os-version", v.osVersion)
        put("x-app-version", v.ideVersion)
        if (account.deviceId.isNotEmpty()) put("x-device-id", account.deviceId)
    }

    /** ExchangeToken / GetUserInfo 只校验 UA。 */
    fun oauth(v: TraeVersion): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        put("User-Agent", ua(v))
    }
}

/** 登录回调解析结果。 */
data class CallbackAccount(
    val refreshToken: String,
    val accessToken: String,
    val uid: String,
    val nickname: String,
    val enterpriseId: String,
    val expiresAt: Long,
)

/**
 * 登录 URL 构造与回调解析。
 *
 * Trae 登录页只回调到 127.0.0.1，应用要自己监听该端口接回调。
 * 回调不回传 machine_id/device_id，只回传派生的 login_trace_id，
 * 因此两枚指纹要用 [machineTraceId] 一一对应反查。
 */
object TraeLogin {

    private val random = SecureRandom()

    /**
     * 官方客户端的 x-device-id 是纯数字（非 hex）。
     * 用 hex 会被 UG 接口的设备校验拒绝，签到报 9074。
     */
    fun randomDeviceId(): String {
        val value = random.nextLong().let { if (it < 0) -it else it }
        return "%015d".format(value % 900_000_000_000_000L + 100_000_000_000_000L)
    }

    /** 机器码是 64 位 hex（32 字节）。 */
    fun newMachineId(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun looksLikeDeviceId(value: String): Boolean =
        value.length in 14..20 && value.all { it.isDigit() }

    /** machineId + deviceId 派生出的 login_trace_id（取拼接尾部 16 位）。 */
    fun machineTraceId(machineId: String, deviceId: String): String {
        val joined = machineId + deviceId
        if (joined.length >= 16) return joined.substring(joined.length - 16)
        return "0".repeat(16 - joined.length) + joined
    }

    /** 构造授权地址。参数集与 Trae 客户端一致，缺项会导致回调异常。 */
    fun buildLoginUrl(
        machineId: String,
        deviceId: String,
        callbackUrl: String,
        pluginVersion: String = TraeVersion().pluginVersion,
        region: TraeRegion = TraeRegion.CN,
    ): String {
        val base = linkedMapOf(
            "auth_from" to "solo",
            "auth_type" to "local",
            "client_id" to TraeConstants.CLIENT_ID,
            "login_channel" to "native_ide",
            "login_version" to "1",
            "plugin_version" to pluginVersion,
            "redirect" to "0",
        ).toSortedMap().entries.joinToString("&") { (k, v) -> "$k=" + enc(v) }

        val appType = if (region == TraeRegion.INTL) "trae" else "stable"
        val osVersion = if (region == TraeRegion.INTL) "Windows 11 Pro" else "1.0"

        val tail = listOf(
            "login_trace_id" to machineTraceId(machineId, deviceId),
            "auth_callback_url" to callbackUrl,
            "machine_id" to machineId,
            "device_id" to deviceId,
            "x_device_id" to deviceId,
            "x_machine_id" to machineId,
            "x_device_brand" to "PC",
            "x_device_type" to "PC",
            "x_os_version" to osVersion,
            "x_app_version" to pluginVersion,
            "x_app_type" to appType,
        ).joinToString("&") { (k, v) -> "$k=" + enc(v) }

        return region.consoleHost + "/authorization?" + base + "&" + tail
    }

    /**
     * 解析回调链接。refreshToken 优先；缺失时回退 `userJwt.RefreshToken`，
     * 再缺失则用 `userJwt.Token` 作为 accessToken。
     */
    fun parseCallback(rawUrl: String): CallbackAccount {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotEmpty()) { "回调链接为空" }
        val query = trimmed.substringAfter('?', "").ifEmpty { trimmed }
        val params = parseQuery(query)

        val refreshToken = params["refreshToken"].orEmpty()
        val userInfo = parseJsonParam(params["userInfo"].orEmpty())
        val userJwt = parseJsonParam(params["userJwt"].orEmpty())

        var effectiveRefresh = refreshToken
        if (effectiveRefresh.isEmpty()) effectiveRefresh = userJwt?.str("RefreshToken").orEmpty()

        var accessToken = ""
        var expiresAt = 0L
        if (effectiveRefresh.isEmpty()) {
            accessToken = userJwt?.str("Token").orEmpty()
            require(accessToken.isNotEmpty()) { "回调缺少 refreshToken 与 userJwt.Token" }
            val expireAt = userJwt?.long("TokenExpireAt") ?: 0L
            if (expireAt > 0L) expiresAt = normalizeExpireAt(expireAt)
        }

        val uid = userInfo?.str("UserID").orEmpty()
        return CallbackAccount(
            refreshToken = effectiveRefresh,
            accessToken = accessToken,
            uid = uid,
            nickname = cleanNickname(userInfo?.str("ScreenName").orEmpty(), uid),
            // 回调里的字段名是 TenantID，不是 EnterpriseID
            enterpriseId = userInfo?.str("TenantID").orEmpty(),
            expiresAt = expiresAt,
        )
    }

    /** 中文昵称在回调里可能被双重 URL 编码，这里退编码并做一次乱码回转。 */
    fun cleanNickname(raw: String, uid: String): String {
        if (raw.isEmpty()) return fallbackNickname(uid)
        var value = raw
        if (value.contains('%')) {
            value = try {
                URLDecoder.decode(value, "UTF-8")
            } catch (_: Exception) {
                value
            }
        }
        value = fixMojibake(value)
        if (value.isEmpty() || value.contains('\uFFFD')) return fallbackNickname(uid)
        return value
    }

    /** 把「UTF-8 字节被当成 Latin-1 读入」的乱码还原；不成立时原样返回。 */
    fun fixMojibake(value: String): String {
        if (value.isEmpty()) return value
        val bytes = ByteArray(value.length)
        for (i in value.indices) {
            val code = value[i].code
            if (code > 0xFF) return value
            bytes[i] = code.toByte()
        }
        return try {
            val decoded = String(bytes, Charsets.UTF_8)
            if (decoded.contains('\uFFFD')) value else decoded
        } catch (_: Exception) {
            value
        }
    }

    private fun fallbackNickname(uid: String): String =
        if (uid.length >= 4) "用户" + uid.substring(uid.length - 4) else "用户"

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun parseQuery(query: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val index = pair.indexOf('=')
            val key = if (index < 0) pair else pair.substring(0, index)
            val value = if (index < 0) "" else pair.substring(index + 1)
            out[dec(key)] = dec(value)
        }
        return out
    }

    private fun dec(value: String): String = try {
        URLDecoder.decode(value, "UTF-8")
    } catch (_: Exception) {
        value
    }

    /** 回调里的 JSON 参数本身被 URL 编码过，可能要再解一层才可解析。 */
    private fun parseJsonParam(raw: String): JsonObject? = runCatching {
        JsonParser.parseString(raw).asJsonObject
    }.getOrNull() ?: runCatching {
        JsonParser.parseString(URLDecoder.decode(raw, "UTF-8")).asJsonObject
    }.getOrNull()
}

/** 上游业务错误分类，决定是否换号/重新登录。 */
enum class TraeErrorKind {
    /** 1005：套餐权益拒绝，模型级——换号无用。 */
    MODEL_LIMIT,

    /** 4008：账号级额度不足。 */
    PLAN_LIMIT,

    /** 4011 / 429：限流，可退避重试。 */
    SOFT_RATE,

    /** 1001 / HTTP 401：凭证失效，需要重新登录。 */
    SESSION_DEAD,

    /** HTTP 404：上游路由不存在。 */
    NOT_FOUND,

    /** 参数或模型不合法。 */
    CLIENT,

    /** 上游 5xx。 */
    SERVER,
}

/** SSE 流内的 event:error 业务错误。 */
class SoloStreamError(val code: Long, val message: String) {
    val kind: TraeErrorKind
        get() = when (code) {
            1001L -> TraeErrorKind.SESSION_DEAD
            1005L -> TraeErrorKind.MODEL_LIMIT
            4008L -> TraeErrorKind.PLAN_LIMIT
            4011L -> TraeErrorKind.SOFT_RATE
            else -> TraeErrorKind.CLIENT
        }

    override fun toString(): String = "solo error code=$code msg=$message"
}

/** 上游 HTTP 层错误；message 是可直接展示给用户的一句话。 */
class TraeHttpException(
    val status: Int,
    val kind: TraeErrorKind,
    val body: String,
    val upstreamCode: Long = 0L,
) : Exception(userMessage(status, kind, body, upstreamCode)) {

    fun debugSummary(): String = "upstream http $status kind=$kind " +
        (if (upstreamCode != 0L) "code=$upstreamCode " else "") +
        "body=${body.take(500)}"

    private companion object {
        fun userMessage(status: Int, kind: TraeErrorKind, body: String, code: Long): String {
            val upstream = TraeErrors.extractMessage(body).trim()
            if (isHumanReadable(upstream)) return upstream
            return when (code) {
                1001L -> "凭证已失效，需要重新登录"
                1005L -> "该模型当前不可用（套餐权益拒绝）"
                4008L -> "额度已耗尽，等每日重置或签到"
                4011L -> "请求过于频繁，已被上游限流"
                9074L -> "签到被拒：设备指纹未通过校验"
                4001L -> "模型或客户端版本不被上游接受"
                else -> when (kind) {
                    TraeErrorKind.SESSION_DEAD -> "凭证已失效，需要重新登录"
                    TraeErrorKind.MODEL_LIMIT -> "该模型当前不可用（套餐权益拒绝）"
                    TraeErrorKind.PLAN_LIMIT -> "额度已耗尽，等每日重置或签到"
                    TraeErrorKind.SOFT_RATE -> "请求过于频繁，已被上游限流"
                    TraeErrorKind.NOT_FOUND -> "上游接口不存在"
                    TraeErrorKind.SERVER -> "上游服务异常（HTTP $status）"
                    TraeErrorKind.CLIENT -> "上游拒绝了本次请求"
                }
            }
        }

        fun isHumanReadable(text: String): Boolean {
            if (text.isEmpty() || text.length > 200) return false
            if (text.startsWith("{") || text.startsWith("[") || text.startsWith("<")) return false
            return true
        }
    }
}

object TraeErrors {

    fun fromStatus(status: Int, body: String): TraeErrorKind {
        val code = extractCode(body)
        return when {
            status == 401 -> TraeErrorKind.SESSION_DEAD
            status == 404 -> TraeErrorKind.NOT_FOUND
            status == 429 -> TraeErrorKind.SOFT_RATE
            status in 500..599 -> TraeErrorKind.SERVER
            status in 400..499 -> when (code) {
                1001L, 20101L -> TraeErrorKind.SESSION_DEAD
                1005L -> TraeErrorKind.MODEL_LIMIT
                4008L -> TraeErrorKind.PLAN_LIMIT
                4011L -> TraeErrorKind.SOFT_RATE
                else -> TraeErrorKind.CLIENT
            }
            else -> TraeErrorKind.CLIENT
        }
    }

    fun extractCode(body: String): Long {
        if (body.isEmpty()) return 0L
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return 0L
        val direct = obj.long("code")
        if (direct != 0L) return direct
        return obj.objOrNull("data")?.long("code") ?: 0L
    }

    fun extractMessage(body: String): String {
        if (body.isEmpty()) return ""
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return body.take(300)
        for (key in listOf("message", "msg", "error_msg", "error")) {
            val v = obj.str(key)
            if (v.isNotEmpty()) return v
        }
        val data = obj.objOrNull("data")
        if (data != null) {
            for (key in listOf("message", "msg")) {
                val v = data.str(key)
                if (v.isNotEmpty()) return v
            }
        }
        return body.take(300)
    }
}

/** 毫秒级时间戳归一化为 Unix 秒：1.7e12 量级判定为毫秒。 */
internal fun normalizeExpireAt(raw: Long): Long = if (raw > 1e12) raw / 1000 else raw

internal fun nowSeconds(): Long = System.currentTimeMillis() / 1000
