package dev.aigw.provider.trae

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.AccountActivitySupport
import dev.aigw.provider.ApiError
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.CredentialImportSupport
import dev.aigw.provider.ModelCatalogSupport
import dev.aigw.provider.ModelInfo
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.RegionAwareSupport
import dev.aigw.provider.SseSink
import dev.aigw.provider.TokenRefreshSupport
import dev.aigw.provider.UsageTokens
import dev.aigw.provider.WebCallbackSupport
import dev.aigw.provider.cachedTokensOf
import dev.aigw.util.Http
import dev.aigw.util.Log
import dev.aigw.util.formatTokenCount
import java.util.UUID

/**
 * 字节 Trae（SOLO 通道）。
 *
 * 上游是私有协议：对话走 `llm_utils_chat` + 自定义 SSE，登录走浏览器授权回调到
 * 本机 51120，签到与额度走 ug host（设备指纹头必填，缺一项会被 9074 拒绝）。
 */
class TraeProvider(
    private val version: TraeVersion = TraeVersion(),
    /** refreshToken 每次换 token 都会轮换，刷新后必须写回账号池。 */
    private val onAccountUpdated: (ProviderAccount) -> Unit = {},
) : Provider, RegionAwareSupport, AccountActivitySupport, WebCallbackSupport, CredentialImportSupport,
    ModelCatalogSupport, TokenRefreshSupport {

    override val id: String = ID
    override val displayName: String = "Trae"

    private val authClient = TraeAuthClient(version)
    private val pending = object : LinkedHashMap<String, Pair<String, String>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>): Boolean =
            size > MAX_PENDING
    }

    // ------------------------------------------------------------------ 区域

    override fun regions(): List<String> = TraeRegion.entries.map { it.id }

    override fun regionOf(account: ProviderAccount): String? =
        regionOfAccount(parseOrNull(account)).id

    private fun regionOfAccount(account: TraeAccount?): TraeRegion =
        TraeRegion.infer(account?.domain, account?.apiHost)

    // ------------------------------------------------------------------ 模型

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        fetchModels(account).map { models -> models.map { it.id } }

    /** 模型页用的完整信息：名称、上下文、输出上限、倍率参考标签。 */
    override fun modelCatalog(account: ProviderAccount): List<ModelInfo> {
        val trae = parseOrNull(account) ?: return emptyList()
        val region = regionOfAccount(trae)
        val models = fetchModels(account).getOrElse { return emptyList() }
        return models.map { model ->
            val tags = mutableListOf<String>()
            tags += if (region == TraeRegion.INTL) "国际" else "CN"
            ModelRates.rateOf(model.id)?.let { tags += "${it.baseLabel()} 积分" }
            if (model.contextWindow > 0) tags += "上下文 ${formatTokenCount(model.contextWindow)}"
            if (model.maxOutputTokens > 0) tags += "输出 ${formatTokenCount(model.maxOutputTokens)}"
            ModelInfo(
                id = model.id,
                displayName = model.name,
                contextWindow = model.contextWindow,
                maxOutputTokens = model.maxOutputTokens,
                tags = tags,
            )
        }
    }

    private fun fetchModels(account: ProviderAccount): Result<List<TraeModel>> {
        val trae = parseOrNull(account) ?: return Result.failure(IllegalStateException("凭证无法解析"))
        val region = regionOfAccount(trae)
        val result = try {
            Http.postJson(
                region.agentHost + TraeConstants.EP_MODELS,
                TraeHeaders.solo(trae, version, stream = false),
                TraeModels.requestBody(),
                TIMEOUT_MS,
            )
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("模型接口请求失败：${e.message}", e))
        }
        if (!result.ok) {
            return Result.failure(
                IllegalStateException("模型接口 HTTP ${result.status}：${TraeErrors.extractMessage(result.body)}"),
            )
        }
        return Result.success(TraeModels.parse(result.body))
    }

    // ------------------------------------------------------------------ 对话

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val trae = parseOrNull(account)
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "凭证无法解析"))
        val region = regionOfAccount(trae)
        val model = request.body.get("model")?.asString.orEmpty().ifEmpty { TraeConstants.DEFAULT_CONFIG_NAME }
        val payload = TraePayload.prepare(request.body.toString())
        val url = region.agentHost + TraeConstants.EP_CHAT
        val headers = TraeHeaders.solo(trae, version, stream = true)

        if (request.stream && sink != null) {
            val parser = SoloSseParser()
            val translator = OpenAiSseTranslator(newCompletionId(), model)
            var events = 0
            var inputTokens = 0
            var outputTokens = 0
            var cachedTokens = 0
            var stopped = false
            val handleEvent: (SoloEvent) -> Boolean = { event ->
                events++
                reportUsage(event)?.let { usage ->
                    inputTokens = usage.input
                    outputTokens = usage.output
                    cachedTokens = usage.cached
                }
                for (data in translator.translate(event)) {
                    sink.onData(data)
                    if (data == "[DONE]") stopped = true
                }
                stopped
            }
            val result = try {
                Http.postStream(
                    url = url,
                    headers = headers,
                    json = payload,
                    timeoutMs = STREAM_TIMEOUT_MS,
                    onLine = { line ->
                        val event = parser.feed(line)
                        if (event != null && handleEvent(event)) return@postStream false
                        true
                    },
                    onDone = {
                        // 流结束：把最后一个没被空行/下一个 event 触发的 event 解析出来
                        parser.flush()?.let { handleEvent(it) }
                        if (!stopped) translator.close().forEach { sink.onData(it) }
                        sink.onDone()
                    },
                )
            } catch (e: Exception) {
                Log.warn("trae upstream error: ${e.message}")
                return ChatResult.Failure(Http.networkError(e))
            }
            if (!result.ok) return ChatResult.Failure(classify(result.status, result.body))
            return ChatResult.Streamed(events, inputTokens, outputTokens, cachedTokens)
        }

        val parser = SoloSseParser()
        val aggregator = OpenAiAggregator(newCompletionId(), model)
        val result = try {
            Http.postStream(
                url = url,
                headers = headers,
                json = payload,
                timeoutMs = STREAM_TIMEOUT_MS,
                onLine = { line ->
                    parser.feed(line)?.let { aggregator.accept(it) }
                    true
                },
                onDone = { parser.flush()?.let { aggregator.accept(it) } },
            )
        } catch (e: Exception) {
            Log.warn("trae upstream error: ${e.message}")
            return ChatResult.Failure(Http.networkError(e))
        }
        if (!result.ok) return ChatResult.Failure(classify(result.status, result.body))
        aggregator.failure?.let { failure ->
            return ChatResult.Failure(ApiError(errorKindOf(failure.kind), 200, failure.message.ifEmpty { "上游流内错误" }))
        }
        return ChatResult.Aggregated(aggregator.build())
    }

    private fun reportUsage(event: SoloEvent): UsageTokens? {
        if (event !is SoloEvent.Usage) return null
        fun pick(vararg keys: String): Int {
            for (key in keys) {
                val value = event.usage.get(key) ?: continue
                if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) return value.asInt
            }
            return 0
        }
        return UsageTokens(
            input = pick("input_tokens", "prompt_tokens"),
            output = pick("output_tokens", "completion_tokens"),
            cached = cachedTokensOf(event.usage),
        )
    }

    private fun classify(status: Int, body: String): ApiError =
        ApiError(
            errorKindOf(TraeErrors.fromStatus(status, body)),
            status,
            TraeErrors.extractMessage(body).ifEmpty { "上游 HTTP $status" },
        )

    private fun errorKindOf(kind: TraeErrorKind): ApiError.Kind = when (kind) {
        TraeErrorKind.SESSION_DEAD -> ApiError.Kind.AUTH
        TraeErrorKind.SERVER, TraeErrorKind.SOFT_RATE, TraeErrorKind.PLAN_LIMIT, TraeErrorKind.MODEL_LIMIT ->
            ApiError.Kind.UPSTREAM
        TraeErrorKind.NOT_FOUND, TraeErrorKind.CLIENT -> ApiError.Kind.CLIENT
    }

    // ------------------------------------------------------------------ 签到 / 额度

    override fun checkin(account: ProviderAccount): Result<CheckinResult> {
        val trae = parseOrNull(account) ?: return Result.failure(IllegalStateException("凭证无法解析"))
        val region = regionOfAccount(trae)
        if (!region.supportsCheckin) return Result.success(CheckinResult.Skipped)
        val client = TraeCheckinClient(version, region.ugHost, region)
        return try {
            val status = client.status(trae)
            if (status.checkedIn) return Result.success(CheckinResult.Done("今日已签到"))
            when (claimWithRetry(client, trae)) {
                ClaimOutcome.ALREADY_CHECKED_IN -> Result.success(CheckinResult.Done("今日已签到"))
                ClaimOutcome.CLAIMED -> Result.success(CheckinResult.Done("签到成功"))
            }
        } catch (e: TraeHttpException) {
            Result.failure(IllegalStateException(traeErrorMessage(e), e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "签到失败", e))
        }
    }

    /** 9074 是设备指纹/限流被拒，退避后重试一次。 */
    private fun claimWithRetry(client: TraeCheckinClient, account: TraeAccount): ClaimOutcome = try {
        client.claim(account)
    } catch (e: TraeHttpException) {
        if (e.upstreamCode != CHECKIN_BUSY_CODE) throw e
        Thread.sleep(CHECKIN_RETRY_DELAY_MS)
        client.claim(account)
    }

    override fun balance(account: ProviderAccount): Result<BalanceInfo> {
        val trae = parseOrNull(account) ?: return Result.failure(IllegalStateException("凭证无法解析"))
        val region = regionOfAccount(trae)
        return try {
            val usage = TraeCheckinClient(version, region.ugHost, region).entUsage(trae)
            if (usage.parsed && usage.limit > 0) {
                Result.success(BalanceInfo(remain = usage.remain.toDouble(), enterprise = false))
            } else {
                // 解析不到就是未知，不能当 0 覆盖已有余额
                Result.success(BalanceInfo(remain = -1.0))
            }
        } catch (e: TraeHttpException) {
            Result.failure(IllegalStateException(traeErrorMessage(e), e))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "额度查询失败", e))
        }
    }

    // ------------------------------------------------------------------ 登录

    override fun beginWebLogin(region: String, callbackUrl: String): String {
        val target = TraeRegion.of(region)
        // 对齐官方客户端指纹格式：machine_id 是 64 位 hex，device_id 是纯数字
        val machineId = TraeLogin.newMachineId()
        val deviceId = TraeLogin.randomDeviceId()
        val traceId = TraeLogin.machineTraceId(machineId, deviceId)
        synchronized(pending) { pending[traceId] = machineId to deviceId }
        return TraeLogin.buildLoginUrl(machineId, deviceId, callbackUrl, version.pluginVersion, target)
    }

    override fun completeWebLogin(callbackUrl: String): ProviderAccount {
        val callback = TraeLogin.parseCallback(callbackUrl)
        val query = TraeLogin.parseQuery(callbackUrl.substringAfter('?', ""))
        val traceId = query["loginTraceID"].orEmpty()
        val machine = synchronized(pending) { pending[traceId] } ?: (TraeLogin.newMachineId() to TraeLogin.randomDeviceId())
        // 回调里的 loginHost / host 能区分发行版；拿不到按国内版处理
        val region = TraeRegion.infer(
            query["loginHost"],
            callback.enterpriseId.ifEmpty { query["host"] },
        )
        var account = TraeAccount(
            uid = callback.uid,
            accessToken = callback.accessToken,
            refreshToken = callback.refreshToken,
            expiresAt = callback.expiresAt,
            nickname = callback.nickname,
            enterpriseId = callback.enterpriseId,
            machineId = machine.first,
            deviceId = machine.second,
            domain = region.id,
            apiHost = region.oauthHost,
        )
        if (account.refreshToken.isNotEmpty()) account = authClient.exchangeToken(account)
        account = authClient.getUserInfo(account)
        if (account.uid.isEmpty()) throw IllegalStateException("无法确定账号 uid，请确认登录已完成")
        val providerAccount = toProviderAccount(account)
        runCatching { onAccountUpdated(providerAccount) }
        return providerAccount
    }

    // ------------------------------------------------------------------ 凭证导入

    /** 粘贴 JSON 凭证导入：补齐设备指纹后换 token 并拉取用户信息。 */
    override fun importCredentials(raw: String): ProviderAccount {
        // 兼容「账号导出 JSON」：secret 字段里才是真正的凭证（字符串或对象形态都认）
        val effective = runCatching {
            val root = JsonParser.parseString(raw.trim()).asJsonObject
            val secret = root.get("secret")
            when {
                root.has("auth") || root.has("accessToken") -> raw.trim()
                secret != null && secret.isJsonPrimitive -> secret.asString
                secret != null && secret.isJsonObject -> secret.toString()
                else -> raw.trim()
            }
        }.getOrDefault(raw.trim())
        var account = ensureDeviceIds(TraeAccount.parse(effective))
        if (account.refreshToken.isNotEmpty()) account = authClient.exchangeToken(account)
        account = authClient.getUserInfo(account)
        if (account.uid.isEmpty()) throw IllegalStateException("无法确定账号 uid，请确认凭证完整")
        val providerAccount = toProviderAccount(account)
        runCatching { onAccountUpdated(providerAccount) }
        return providerAccount
    }

    // ------------------------------------------------------------------ 凭证刷新

    /** 用 refreshToken 换新的 accessToken（每次换都会轮换 refreshToken），再拉用户信息。 */
    override fun refreshCredentials(account: ProviderAccount): ProviderAccount {
        var trae = parseOrNull(account) ?: throw IllegalStateException("凭证无法解析")
        if (trae.refreshToken.isBlank()) throw IllegalStateException("缺少 refreshToken，需要重新登录")
        trae = authClient.exchangeToken(trae)
        trae = authClient.getUserInfo(trae)
        if (trae.uid.isEmpty()) throw IllegalStateException("刷新后无法确定账号 uid")
        return toProviderAccount(trae)
    }

    // ------------------------------------------------------------------ 工具

    private fun parseOrNull(account: ProviderAccount): TraeAccount? =
        runCatching { TraeAccount.parse(account.secret) }.getOrNull()

    private fun toProviderAccount(account: TraeAccount): ProviderAccount = ProviderAccount(
        providerId = id,
        uid = account.uid,
        displayName = account.nickname.ifEmpty { account.uid.take(12) },
        secret = account.toJson().toString(),
    )

    private fun newCompletionId(): String = "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").take(24)

    companion object {
        const val ID = "trae"

        private const val MAX_PENDING = 40
        private const val CHECKIN_BUSY_CODE = 9074L
        private const val CHECKIN_RETRY_DELAY_MS = 8_000L
        private const val TIMEOUT_MS = 30_000
        private const val STREAM_TIMEOUT_MS = 300_000
    }
}

/** 把不符合官方格式的设备指纹就地修正（machine_id 64 位 hex、device_id 纯数字）。 */
internal fun ensureDeviceIds(account: TraeAccount): TraeAccount {
    var result = account
    if (result.machineId.length != 64) result = result.copy(machineId = TraeLogin.newMachineId())
    if (!TraeLogin.looksLikeDeviceId(result.deviceId)) result = result.copy(deviceId = TraeLogin.randomDeviceId())
    return result
}

/** 上游异常转成一句人话；少数码补充「怎么做」。 */
internal fun traeErrorMessage(e: TraeHttpException): String = when (e.upstreamCode) {
    9074L -> "签到被拒：设备指纹未通过校验，请重新登录后重试"
    4001L -> "模型或客户端版本不被上游接受"
    else -> e.message ?: "上游错误"
}
