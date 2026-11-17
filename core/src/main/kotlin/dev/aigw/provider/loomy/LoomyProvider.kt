package dev.aigw.provider.loomy

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.AccountActivitySupport
import dev.aigw.provider.ApiError
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.ModelCatalogSupport
import dev.aigw.provider.ModelInfo
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.ProviderActionSupport
import dev.aigw.provider.ProviderActionResult
import dev.aigw.provider.SmsLoginSupport
import dev.aigw.provider.SseSink
import dev.aigw.provider.parseUsageTokens
import dev.aigw.util.Log
import java.nio.charset.StandardCharsets

/**
 * Loomy（讯飞）免费积分通道。
 *
 * 上游 `/api/v1/chat/completions` 本身就是 OpenAI 兼容协议，所以这里几乎不做协议改写：
 * 选号 → 补认证/追踪头 → 转发 → 原样回传 SSE。需要注意上游对鉴权失败返回的是
 * **HTTP 200 + 业务码**（不是 4xx），必须按业务码判定，否则会把错误 JSON 当正常响应透传。
 */
class LoomyProvider(
    private val defaultModel: () -> String = { "" },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : Provider, SmsLoginSupport, AccountActivitySupport, ModelCatalogSupport, ProviderActionSupport {

    override val id: String = ID
    override val displayName: String = "Loomy"

    private val authClient = LoomyAuthClient(nowMillis = nowMillis)
    private val chatClient = LoomyChatClient()
    private val pointsClient = LoomyPointsClient()
    private val taskClient = LoomyTaskClient()
    private val catalog = LoomyModelCatalog(nowMillis = nowMillis)

    // ------------------------------------------------------------------ 模型

    override fun listModels(account: ProviderAccount): Result<List<String>> {
        val session = parseOrNull(account)?.session
        val models = catalog.models(session)
        return Result.success(models.map { it.id })
    }

    override fun modelCatalog(account: ProviderAccount): List<ModelInfo> {
        val session = parseOrNull(account)?.session
        return catalog.models(session).map { model ->
            val tags = mutableListOf<String>()
            if (model.reasoning) tags += "深度思考"
            if (model.toolCall) tags += "工具调用"
            if (model.attachment) tags += "附件"
            ModelInfo(
                id = model.id,
                displayName = model.name,
                contextWindow = model.contextLength,
                maxOutputTokens = model.outputLength,
                tags = tags,
            )
        }
    }

    private fun resolveModel(requested: String): String {
        val model = requested.trim()
        if (model.isEmpty() || model == "auto") return defaultModel()
        return model.substringAfter('/').trim().ifEmpty { model }
    }

    // ------------------------------------------------------------------ 对话

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val loomy = parseOrNull(account)
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "账号凭证无法解析"))
        val model = resolveModel(request.model)
        val streaming = request.stream && sink != null

        val call = try {
            chatClient.openStream(loomy, if (streaming) withIncludeUsage(request.body.toString()) else request.body.toString())
        } catch (e: LoomyApiException) {
            return ChatResult.Failure(classify(e.status, e.body))
        }
        if (call.status !in 200..299) {
            call.close()
            return ChatResult.Failure(classify(call.status, call.errorBody))
        }

        // 上游回 JSON：可能是正常非流式响应，也可能是「HTTP 200 + 业务错误码」
        if (!call.isEventStream) {
            val text = runCatching { call.stream?.use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrNull() ?: call.errorBody
            call.close()
            val code = LoomyErrors.extractCode(text)
            if (code.isNotEmpty() && code != LoomyConstants.CODE_OK) {
                return ChatResult.Failure(classify(200, text))
            }
            if (streaming) {
                // 客户端要流式但上游回了 JSON：包成单 chunk + [DONE]
                val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                if (obj != null) {
                    sink.onData(obj.toString())
                    sink.onData("[DONE]")
                    return ChatResult.Streamed(1)
                }
                return ChatResult.Failure(classify(200, text))
            }
            val parsed = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                ?: return ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, 200, "上游返回空响应"))
            return ChatResult.Aggregated(parsed)
        }

        if (streaming) {
            var events = 0
            var inputTokens = 0
            var outputTokens = 0
            var cachedTokens = 0
            val stream = call.stream
            if (stream == null) {
                call.close()
                return ChatResult.Failure(classify(call.status, call.errorBody))
            }
            try {
                stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") {
                            sink.onData("[DONE]")
                            break
                        }
                        events++
                        parseUsageTokens(data)?.let { usage ->
                            inputTokens = usage.input
                            outputTokens = usage.output
                            cachedTokens = usage.cached
                        }
                        sink.onData(data)
                    }
                }
            } catch (e: Exception) {
                Log.warn("loomy upstream stream error: ${e.message}")
                call.close()
                return ChatResult.Failure(ApiError(ApiError.Kind.NETWORK, 0, (e.message ?: "流式读取失败").take(300)))
            } finally {
                call.close()
            }
            sink.onDone()
            return ChatResult.Streamed(events, inputTokens, outputTokens, cachedTokens)
        }

        // 非流式但上游回 SSE：聚合
        val aggregated = try {
            LoomySseAggregator.aggregate(call.stream!!, model)
        } catch (e: Exception) {
            Log.warn("loomy aggregate error: ${e.message}")
            call.close()
            return ChatResult.Failure(ApiError(ApiError.Kind.NETWORK, 0, (e.message ?: "聚合失败").take(300)))
        } finally {
            call.close()
        }
        val parsed = runCatching { JsonParser.parseString(aggregated).asJsonObject }.getOrNull()
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.EMPTY, 200, "上游返回空响应"))
        return ChatResult.Aggregated(parsed)
    }

    private fun classify(status: Int, body: String): ApiError {
        val kind = LoomyErrors.fromStatus(status, body)
        val apiKind = when (kind) {
            LoomyErrorKind.SESSION_DEAD -> ApiError.Kind.AUTH
            LoomyErrorKind.QUOTA, LoomyErrorKind.SOFT_RATE, LoomyErrorKind.SERVER -> ApiError.Kind.UPSTREAM
            LoomyErrorKind.NOT_FOUND, LoomyErrorKind.CLIENT -> ApiError.Kind.CLIENT
            LoomyErrorKind.NETWORK -> ApiError.Kind.NETWORK
        }
        val message = LoomyErrors.extractMessage(body).ifEmpty { "上游 HTTP $status" }
        return ApiError(apiKind, status, message)
    }

    // ------------------------------------------------------------------ 额度 / 签到

    /** Loomy 没有签到体系，直接跳过（不记失败、不记签到时间）。 */
    override fun checkin(account: ProviderAccount): Result<CheckinResult> =
        Result.success(CheckinResult.Skipped)

    override fun balance(account: ProviderAccount): Result<BalanceInfo> {
        val loomy = parseOrNull(account)
            ?: return Result.failure(IllegalStateException("账号凭证无法解析，请重新登录"))
        return try {
            val snapshot = pointsClient.balance(loomy.session)
            // 对话扣的是当日赠送账本（实测会随消耗下降），而 balance 字段是永久账本不动；
            // 可用总余额 = 主账本 + 当日剩余，两者都来自 records。
            // 当日剩余的取值必须与 creditPacks 的「当日赠送」行完全一致：
            // 上游实测只回 dailyBalance 不回 dailyRemainingPoints，这里漏兑底就会出现
            // 「额度包有 2992、余额还是一万」的不同步。
            val daily = when {
                snapshot.dailyRemaining >= 0 -> snapshot.dailyRemaining
                snapshot.dailyBalance >= 0 -> snapshot.dailyBalance
                else -> 0L
            }
            Result.success(
                BalanceInfo(
                    remain = (snapshot.balance + daily).toDouble(),
                    enterprise = false,
                ),
            )
        } catch (e: LoomyApiException) {
            Result.failure(IllegalStateException(e.message ?: "刷新积分失败"))
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "刷新积分失败"))
        }
    }

    // ------------------------------------------------------------------ 专属动作

    override fun performAction(account: ProviderAccount, action: String, payload: JsonObject): ProviderActionResult {
        val loomy = parseOrNull(account) ?: return ProviderActionResult.failure("账号凭证无法解析")
        return try {
            when (action) {
                ACTION_REDEEM_INVITE -> {
                    val code = payload.get("inviteCode")?.asString.orEmpty().trim()
                    if (code.isEmpty()) return ProviderActionResult.failure("邀请码不能为空")
                    pointsClient.activate(loomy.session, code)
                    ProviderActionResult.success("邀请码兑换成功")
                }

                ACTION_REDEEM_CODE -> {
                    val code = payload.get("code")?.asString.orEmpty().trim()
                    if (code.isEmpty()) return ProviderActionResult.failure("兑换码不能为空")
                    pointsClient.redeemCode(loomy.session, code)
                    ProviderActionResult.success("兑换码兑换成功")
                }

                ACTION_COMPLETE_TASK -> {
                    val key = payload.get("key")?.asString.orEmpty()
                    val result = taskClient.complete(loomy.session, key)
                    val title = LoomyTaskClient.titleOf(key)
                    if (result.alreadyCompleted) {
                        ProviderActionResult.success("$title：此前已完成")
                    } else {
                        ProviderActionResult.success("$title：已完成")
                    }
                }

                ACTION_COMPLETE_ALL_TASKS -> {
                    val pending = taskClient.tasks(loomy.session).tasks.filter { !it.completed }
                    if (pending.isEmpty()) return ProviderActionResult.success("所有任务都已完成")
                    val lines = pending.map { task ->
                        val result = taskClient.complete(loomy.session, task.key)
                        "${task.title}：${if (result.alreadyCompleted) "此前已完成" else "已完成"}"
                    }
                    ProviderActionResult.success(lines.joinToString("；"))
                }

                else -> ProviderActionResult.unsupported(action)
            }
        } catch (e: LoomyApiException) {
            ProviderActionResult.failure(e.message ?: "操作失败")
        } catch (e: Exception) {
            ProviderActionResult.failure(e.message ?: "操作失败")
        }
    }

    // ------------------------------------------------------------------ 登录

    override fun sendSmsCode(phone: String, ccode: String): String {
        val normalized = phone.trim()
        validatePhone(normalized, ccode)
        return authClient.sendSmsCode(normalized, ccode)
    }

    override fun loginBySmsCode(phone: String, code: String, msgid: String, ccode: String): ProviderAccount {
        val account = authClient.loginBySmsCode(phone.trim(), code.trim(), msgid, ccode)
        var nickname = ""
        runCatching { nickname = authClient.nicknameOf(authClient.getUserInfo(account.session)) }
        // 上游没给 userid 时退回手机号当 uid，账号至少可用且可重复登录覆盖
        val uid = account.uid.ifEmpty { account.phone }
        return toProviderAccount(account.copy(uid = uid, nickname = nickname))
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 校验手机号。
     *
     * 大陆号严格要求 11 位；其它国家码只校验位数区间——
     * 各国家号码长度不一（香港 8 位、日本 10 位…），写死大陆规则会把它们全挡掉。
     */
    private fun validatePhone(phone: String, ccode: String) {
        val pattern = if (ccode == LoomyConstants.DEFAULT_CCODE) CN_PHONE else GENERIC_PHONE
        if (!pattern.matches(phone)) {
            throw IllegalArgumentException(
                if (ccode == LoomyConstants.DEFAULT_CCODE) "手机号格式不正确（大陆号码为 11 位）"
                else "手机号格式不正确（当前国家码 +$ccode，应为 4–15 位数字）",
            )
        }
    }

    private fun toProviderAccount(account: LoomyAccount): ProviderAccount =
        ProviderAccount(
            providerId = id,
            uid = account.uid,
            displayName = account.nickname.ifEmpty { account.phone },
            secret = account.toJson().toString(),
        )

    private fun parseOrNull(account: ProviderAccount): LoomyAccount? =
        runCatching { LoomyAccount.parse(account.secret) }.getOrNull()

    companion object {
        const val ID = "loomy"

        /** Loomy 的 provider 专属动作，UI 组件与 provider 约定一致。 */
        const val ACTION_REDEEM_INVITE = "redeem_invite"
        const val ACTION_REDEEM_CODE = "redeem_code"
        const val ACTION_COMPLETE_TASK = "complete_task"
        const val ACTION_COMPLETE_ALL_TASKS = "complete_all_tasks"

        /** 中国大陆手机号：11 位、1 开头。 */
        private val CN_PHONE = Regex("1[3-9]\\d{9}")

        /** 其它地区比较宽松：4–15 位纯数字（国际号码标准上限是 15 位）。 */
        private val GENERIC_PHONE = Regex("\\d{4,15}")
    }
}
