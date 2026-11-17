package dev.aigw.provider.raccoon

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.AccountActivitySupport
import dev.aigw.provider.ApiError
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.ChunkAggregator
import dev.aigw.provider.CredentialImportSupport
import dev.aigw.provider.ModelCatalogSupport
import dev.aigw.provider.ModelInfo
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.OpenAiChunks
import dev.aigw.provider.SseSink
import dev.aigw.provider.TokenRefreshSupport
import dev.aigw.provider.WebCallbackSupport
import dev.aigw.util.Http
import dev.aigw.util.Log
import dev.aigw.util.arrayOrNull
import dev.aigw.util.boolOrNull
import dev.aigw.util.firstString
import dev.aigw.util.jwtClaim
import dev.aigw.util.longOrNull
import dev.aigw.util.newCompletionId
import dev.aigw.util.objOrNull
import dev.aigw.util.stringOrNull
import java.net.URLEncoder

/**
 * 商汤「小浣熊」（xiaohuanxiong.com）插件账号通道。
 *
 * - 登录：浏览器打开 `/code/authorize?appname=Raccoon&redirect=<本地回调>`，
 *   登录后重定向回本地回调并带 `authorization_code`，再换 access/refresh token
 * - 对话：`/api/web/llm/v2/chat/completions`（404 回退 v1），上游强制流式，
 *   响应外层包 `{status, data}`，由 [RaccoonProtocol.SseTranslator] 拆回标准 SSE
 * - 余额/签到：web 通道的积分接口
 */
class RaccoonProvider(
    private val onAccountUpdated: (ProviderAccount) -> Unit = {},
) : Provider, ModelCatalogSupport, CredentialImportSupport, WebCallbackSupport,
    TokenRefreshSupport, AccountActivitySupport {

    override val id = ID
    override val displayName = "小浣熊"

    /** 小浣熊不校验固定端口，用一段独立端口避免与 Trae 抢。 */
    override val callbackPort: Int get() = 51121
    override val callbackPath: String get() = "/raccoon/callback"

    // ------------------------------------------------------------------ 模型

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        runCatching { fetchModels(credentialOf(account)).map { it.id } }

    override fun modelCatalog(account: ProviderAccount): List<ModelInfo> =
        runCatching { fetchModels(credentialOf(account)) }.getOrDefault(emptyList())

    private fun fetchModels(credential: RaccoonProtocol.Credential): List<ModelInfo> {
        val r = Http.get(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_MODELS}",
            jsonHeaders(credential),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        if (!r.ok) throw IllegalStateException("模型接口 HTTP ${r.status}")
        val root = runCatching { JsonParser.parseString(r.body) }.getOrNull()
            ?: throw IllegalStateException("模型响应不是合法 JSON")
        val array = RaccoonProtocol.findModelArray(root)
            ?: throw IllegalStateException("模型响应里找不到模型数组（顶层键：${root.asJsonObject.keySet().joinToString(",")}）")
        val seen = HashSet<String>()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val modelId = item.firstString("id", "model", "model_id", "modelId", "name", "code")
            if (modelId.isEmpty() || !seen.add(modelId)) return@mapNotNull null
            ModelInfo(
                id = modelId,
                displayName = item.firstString("display_name", "displayName", "title", "name", "model_name")
                    .ifEmpty { modelId },
                contextWindow = item.longOrNull("context_window")
                    ?: item.longOrNull("contextWindow")
                    ?: item.longOrNull("max_input_tokens") ?: 0L,
                tags = listOf("chat"),
            )
        }
    }

    private fun resolveModel(requested: String): String {
        val model = requested.trim().substringAfter('/').trim()
        if (model.isEmpty() || model.equals("auto", ignoreCase = true)) return RaccoonProtocol.DEFAULT_MODEL
        return model
    }

    // ------------------------------------------------------------------ 登录

    override fun beginWebLogin(region: String, callbackUrl: String): String =
        // 不要带 login_source=desktop：授权页会走 office-raccoon:// 自定义 scheme，手机上没有 App 能接
        "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_LOGIN}" +
            "?appname=" + enc(RaccoonProtocol.APP_NAME) +
            "&redirect=" + enc(callbackUrl)

    override fun completeWebLogin(callbackUrl: String): ProviderAccount {
        val code = queryParam(callbackUrl, "authorization_code").orEmpty()
        if (code.isEmpty()) throw IllegalStateException("回调链接缺少 authorization_code")
        val body = JsonObject().apply { addProperty("authorization_code", code) }
        val r = Http.postJson(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_LOGIN_WITH_CODE}",
            mapOf("Accept" to "application/json", "Content-Type" to "application/json"),
            body.toString(),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        if (!r.ok) {
            Log.warn("raccoon login HTTP ${r.status}: ${r.body.take(300)}")
            throw IllegalStateException(
                RaccoonProtocol.extractMessage(r.body).ifEmpty { "换取凭证失败（HTTP ${r.status}）" },
            )
        }
        // 上游失败时也可能回 HTTP 200，真正结果在 status.code 里
        val statusCode = runCatching {
            JsonParser.parseString(r.body).asJsonObject.objOrNull("status")?.longOrNull("code")
        }.getOrNull()
        if (statusCode != null && statusCode != 0L) {
            Log.warn("raccoon login code=$statusCode: ${r.body.take(300)}")
            throw IllegalStateException(
                RaccoonProtocol.extractMessage(r.body).ifEmpty { "换取凭证失败（上游 code=$statusCode）" },
            )
        }
        val envelope = RaccoonProtocol.envelopeData(r.body)
            ?: throw IllegalStateException("换取凭证响应异常：${r.body.take(200)}")
        val data = envelope.objOrNull("tokens") ?: envelope
        val access = data.firstString("access_token", "accessToken")
        val refresh = data.firstString("refresh_token", "refreshToken")
        if (access.isEmpty()) {
            Log.warn("raccoon login 缺少 access_token: ${r.body.take(300)}")
            throw IllegalStateException("换取凭证响应缺少 access_token：${r.body.take(120)}")
        }
        val (credential, avatar) = credentialFrom(access, refresh)
        return toAccount(credential, avatar)
    }

    /** 粘贴回调链接导入：与网页登录同一条换取链路。 */
    override fun importCredentials(raw: String): ProviderAccount = completeWebLogin(raw.trim())

    private fun credentialFrom(access: String, refresh: String): Pair<RaccoonProtocol.Credential, String> {
        var nickname = jwtClaim(access, "name")
        var orgCode = ""
        var avatar = ""
        val userInfo = runCatching { fetchUserInfo(access) }.getOrNull()
        if (userInfo != null) {
            nickname = userInfo.firstString("name", "nickname", "nick_name", "username").ifEmpty { nickname }
            orgCode = firstOrgCode(userInfo)
            avatar = userInfo.firstString("avatar", "avatar_url", "avatarUrl", "head_img", "headImg", "photo", "picture")
        }
        // 账号标识优先取 JWT 的用户声明；都没有时由 refresh_token 派生
        // （access_token 每次登录都轮换，拿它派生会让同一账号反复变成新条目）
        var userId = jwtClaim(access, "user_id", "userId", "uid", "sub")
        if (userId.isEmpty()) userId = userInfo?.firstString("userId", "user_id", "uid", "sub").orEmpty()
        if (userId.isEmpty()) {
            val material = refresh.ifEmpty { access }
            userId = "raccoon-" + material.hashCode().toUInt().toString(16)
        }
        return RaccoonProtocol.Credential(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = jwtClaim(access, "exp").toLongOrNull()?.times(1000) ?: 0L,
            userId = userId,
            nickname = nickname,
            orgCode = orgCode,
        ) to avatar
    }

    private fun toAccount(
        credential: RaccoonProtocol.Credential,
        avatar: String = "",
    ): ProviderAccount =
        ProviderAccount(
            providerId = id,
            uid = credential.userId,
            displayName = credential.nickname.ifEmpty { "小浣熊 ${credential.userId.takeLast(6)}" },
            secret = credential.toJson(),
            extra = if (avatar.isNotBlank()) mapOf("avatar" to avatar) else emptyMap(),
        )

    private fun fetchUserInfo(accessToken: String): JsonObject? {
        val r = Http.get(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_USER_INFO}",
            bearerHeaders(accessToken),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        if (!r.ok) return null
        return RaccoonProtocol.envelopeData(r.body)
    }

    private fun firstOrgCode(userInfo: JsonObject): String {
        val orgs = userInfo.arrayOrNull("orgs") ?: userInfo.arrayOrNull("organizations") ?: return ""
        for (element in orgs) {
            val org = element as? JsonObject ?: continue
            val code = org.firstString("code", "org_code", "orgCode", "id")
            if (code.isNotEmpty()) return code
        }
        return ""
    }

    // ------------------------------------------------------------------ 对话

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val credential = runCatching { credentialOf(account) }.getOrElse {
            return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "小浣熊凭证无法解析"))
        }
        val model = resolveModel(request.model)
        val body = runCatching { RaccoonProtocol.prepareBody(request.body) }
            .getOrElse { return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "请求体转换失败：${it.message}")) }
        body.addProperty("model", model)
        val streaming = request.stream && sink != null
        return try {
            if (streaming) streamChat(credential, body.toString(), model, sink!!)
            else completeChat(credential, body.toString(), model)
        } catch (e: Exception) {
            Log.warn("raccoon chat error: ${e.message}")
            ChatResult.Failure(ApiError(ApiError.Kind.NETWORK, 0, (e.message ?: "网络错误").take(300)))
        }
    }

    /** v2 优先；v2 不存在（404）时回退 v1。 */
    private fun postChat(
        credential: RaccoonProtocol.Credential,
        body: String,
        onLine: (String) -> Boolean,
    ): Http.Result {
        val headers = chatHeaders(credential) + mapOf("Content-Type" to "application/json")
        val v2 = Http.postStream(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_CHAT}",
            headers,
            body,
            RaccoonProtocol.READ_TIMEOUT_MS,
            onLine,
            {},
        )
        if (v2.status != 404) return v2
        return Http.postStream(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_CHAT_V1}",
            headers,
            body,
            RaccoonProtocol.READ_TIMEOUT_MS,
            onLine,
            {},
        )
    }

    private fun streamChat(
        credential: RaccoonProtocol.Credential,
        body: String,
        model: String,
        sink: SseSink,
    ): ChatResult {
        val translator = RaccoonProtocol.SseTranslator(model)
        var events = 0
        var closed = false
        val raw = StringBuilder()
        val r = postChat(credential, body) { line ->
            if (raw.length < MAX_RAW_SNIFF) raw.append(line).append('\n')
            for (chunk in translator.translate(line)) {
                if (chunk == "[DONE]") {
                    sink.onData("[DONE]")
                    closed = true
                    return@postChat false
                }
                events++
                sink.onData(chunk)
            }
            true
        }
        if (!r.ok) {
            classify(r.status, r.body)?.let { return ChatResult.Failure(it) }
        }
        if (events == 0) {
            // 上游可能直接给整段 JSON（非 SSE），或回了业务错误包：两种都不是「空回复」
            val fallback = jsonCompletion(raw.toString(), model)
            if (fallback != null) {
                val choice = fallback.getAsJsonArray("choices").first().asJsonObject
                val message = choice.objOrNull("message") ?: JsonObject()
                val delta = JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", message.stringOrNull("content").orEmpty())
                    message.stringOrNull("reasoning_content")?.takeIf { it.isNotEmpty() }
                        ?.let { addProperty("reasoning_content", it) }
                }
                val finish = choice.stringOrNull("finish_reason") ?: "stop"
                val id = newCompletionId()
                sink.onData(OpenAiChunks.chunk(id, model, delta))
                sink.onData(OpenAiChunks.chunk(id, model, JsonObject(), finish))
                sink.onData("[DONE]")
                sink.onDone()
                return ChatResult.Streamed(2)
            }
            sink.onDone()
            return ChatResult.Failure(
                ApiError(ApiError.Kind.EMPTY, r.status, failDetail(raw.toString(), r.body)),
            )
        }
        if (!closed) {
            for (chunk in translator.close()) {
                if (chunk == "[DONE]") sink.onData("[DONE]") else sink.onData(chunk)
            }
        }
        sink.onDone()
        return ChatResult.Streamed(events)
    }

    private fun completeChat(
        credential: RaccoonProtocol.Credential,
        body: String,
        model: String,
    ): ChatResult {
        val translator = RaccoonProtocol.SseTranslator(model)
        val aggregator = ChunkAggregator()
        val raw = StringBuilder()
        var frames = 0
        val r = postChat(credential, body) { line ->
            if (raw.length < MAX_RAW_SNIFF) raw.append(line).append('\n')
            for (chunk in translator.translate(line)) {
                if (chunk == "[DONE]") return@postChat false
                frames++
                runCatching { JsonParser.parseString(chunk).asJsonObject }.getOrNull()
                    ?.let { aggregator.consume(it) }
            }
            true
        }
        if (!r.ok) {
            classify(r.status, r.body)?.let { return ChatResult.Failure(it) }
        }
        if (aggregator.isEmpty()) {
            // 非 SSE 的整段 JSON：直接当 OpenAI 响应取走
            val completion = jsonCompletion(raw.toString(), model)
            if (completion != null) return ChatResult.Aggregated(completion)
            return ChatResult.Failure(
                ApiError(ApiError.Kind.EMPTY, r.status, emptyDetail(frames, raw.toString(), r.body)),
            )
        }
        return ChatResult.Aggregated(aggregator.completion(model))
    }

    // ------------------------------------------------------------------ 非 SSE 响应的兜底解析

    /** 把整段响应当 JSON 解成 chat.completion；解析不了返回 null。 */
    private fun jsonCompletion(raw: String, model: String): JsonObject? {
        val root = parseBody(raw) ?: return null
        val data = RaccoonProtocol.unwrapData(root)
        val choices = data.arrayOrNull("choices") ?: return null
        val choice = choices.firstOrNull() as? JsonObject ?: return null
        val message = choice.objOrNull("message") ?: choice.objOrNull("delta") ?: return null
        val content = textOf(message.get("content"))
        val reasoning = textOf(message.get("reasoning_content") ?: message.get("reasoningContent"))
        if (content.isEmpty() && reasoning.isEmpty() && message.get("tool_calls") == null) return null
        return JsonObject().apply {
            addProperty("id", data.stringOrNull("id") ?: newCompletionId())
            addProperty("object", "chat.completion")
            addProperty("created", System.currentTimeMillis() / 1000)
            addProperty("model", model)
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("message", JsonObject().apply {
                        addProperty("role", "assistant")
                        addProperty("content", content)
                        if (reasoning.isNotEmpty()) addProperty("reasoning_content", reasoning)
                        message.get("tool_calls")?.let { add("tool_calls", it) }
                    })
                    addProperty("finish_reason", choice.stringOrNull("finish_reason") ?: "stop")
                })
            })
        }
    }

    /** 内容字段可能是字符串，也可能是 [{type:text,text:…}] 数组。 */
    private fun textOf(element: JsonElement?): String {
        if (element == null || element.isJsonNull) return ""
        if (element.isJsonPrimitive) return element.asString
        if (!element.isJsonArray) return ""
        return element.asJsonArray.joinToString("") { part ->
            (part as? JsonObject)?.stringOrNull("text").orEmpty()
        }
    }

    /** 解析响应体：可能是 `data: {...}`，也可能是裸 JSON。 */
    private fun parseBody(raw: String): JsonObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        val candidate = if (text.contains("data:")) {
            text.lines().lastOrNull { it.trim().startsWith("data:") }
                ?.trim()?.removePrefix("data:")?.trim().orEmpty()
        } else {
            text
        }
        if (candidate.isEmpty() || candidate == "[DONE]") return null
        return runCatching { JsonParser.parseString(candidate).asJsonObject }.getOrNull()
    }

    /** 失败原因：优先用上游业务文案，没有就给响应片段（让日志能定位）。 */
    private fun emptyDetail(frames: Int, raw: String, httpBody: String): String {
        if (frames > 0) {
            return "小浣熊返回了 $frames 个不含正文的帧（可能是 max_tokens 过小，或模型只输出了思考内容）"
        }
        return failDetail(raw, httpBody)
    }

    private fun failDetail(raw: String, httpBody: String): String {
        val message = RaccoonProtocol.extractMessage(raw).ifEmpty {
            RaccoonProtocol.extractMessage(httpBody)
        }
        if (message.isNotEmpty()) return message
        val snippet = raw.ifBlank { httpBody }.trim().replace("\n", " ").take(300)
        return if (snippet.isEmpty()) "小浣熊未返回内容（响应为空）" else "小浣熊未返回内容，原始响应：$snippet"
    }

    // ------------------------------------------------------------------ 余额 / 签到

    override fun balance(account: ProviderAccount): Result<BalanceInfo> = runCatching {
        val credential = credentialOf(account)
        val r = Http.get(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_BALANCE}",
            jsonHeaders(credential),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        if (!r.ok) throw IllegalStateException("上游 HTTP ${r.status}")
        val data = RaccoonProtocol.envelopeData(r.body)
            ?: throw IllegalStateException("响应缺少 data")
        // 字段缺失与「真的是 0 积分」必须区分：缺失时返回未知，不能显示成 0
        val points = RaccoonProtocol.balancePoints(data)
            ?: return@runCatching BalanceInfo(remain = -1.0)
        BalanceInfo(remain = points.toDouble())
    }

    /**
     * 领取今日积分（签到）。
     *
     * 流程（对照 Buddy2api 的 quota.py）：
     * 1. 先拉今天的积分流水，算今日已到账多少；
     * 2. GET `/api/web/office/v3/setting_info` —— 它会触发上游每日发放；
     * 3. 再拉一次流水对比：变多就是本次领到，没变但有入账就是今日已领。
     */
    override fun checkin(account: ProviderAccount): Result<CheckinResult> = runCatching {
        val credential = credentialOf(account)
        val before = todayGrantedPoints(credential)
        val r = Http.get(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_SETTING_INFO}",
            jsonHeaders(credential) + desktopHeaders(),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        val payload = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
        val code = payload?.get("code")?.takeIf { it.isJsonPrimitive }?.asLong
        if (!r.ok || (code != null && code != 0L)) {
            throw IllegalStateException(
                RaccoonProtocol.extractMessage(r.body).ifEmpty { "每日发放失败（HTTP ${r.status}）" },
            )
        }
        val after = todayGrantedPoints(credential)
        val data = payload?.objOrNull("data")
        val triggered = data?.boolOrNull("point_grant_popups") == true ||
            data?.boolOrNull("point_grant_toast") == true
        when {
            after > before -> CheckinResult.Done("签到成功，+${after - before} 积分")
            after > 0 -> CheckinResult.Done("今日已领取（今日共 +$after 积分）")
            triggered -> CheckinResult.Done("已触发每日发放")
            else -> CheckinResult.Done("今日积分由服务端自动发放，暂无新入账")
        }
    }

    /** 今日已到账的正积分合计（按积分流水的 created_at 判日）。 */
    private fun todayGrantedPoints(credential: RaccoonProtocol.Credential): Long {
        val r = Http.get(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_BILLS}?paging.limit=20&paging.offset=0",
            jsonHeaders(credential),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        if (!r.ok) return 0L
        val data = RaccoonProtocol.envelopeData(r.body) ?: return 0L
        val rows = data.arrayOrNull("items") ?: data.arrayOrNull("bills") ?: data.arrayOrNull("list")
            ?: return 0L
        val today = java.time.LocalDate.now()
        var total = 0L
        for (element in rows) {
            val row = element as? JsonObject ?: continue
            val points = row.get("points")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
            if (points <= 0) continue
            val created = row.get("created_at")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if (created.isEmpty()) continue
            val day = runCatching {
                java.time.OffsetDateTime.parse(created).toLocalDate()
            }.recoverCatching {
                java.time.LocalDateTime.parse(created.replace(' ', 'T')).toLocalDate()
            }.getOrNull() ?: continue
            if (day == today) total += points
        }
        return total
    }

    // ------------------------------------------------------------------ 续期

    override fun refreshCredentials(account: ProviderAccount): ProviderAccount {
        val credential = credentialOf(account)
        if (credential.refreshToken.isEmpty()) throw IllegalStateException("没有 refresh_token，请重新登录")
        val r = Http.postJson(
            "${RaccoonProtocol.HOST}${RaccoonProtocol.PATH_REFRESH}",
            jsonHeaders(credential),
            JsonObject().apply { addProperty("refresh_token", credential.refreshToken) }.toString(),
            RaccoonProtocol.CONNECT_TIMEOUT_MS,
        )
        if (!r.ok) throw IllegalStateException("刷新失败（HTTP ${r.status}）")
        val data = RaccoonProtocol.envelopeData(r.body) ?: throw IllegalStateException("刷新响应缺少 data")
        val access = data.firstString("access_token", "accessToken")
        if (access.isEmpty()) throw IllegalStateException("刷新响应缺少 access_token")
        val refreshed = credential.copy(
            accessToken = access,
            refreshToken = data.firstString("refresh_token", "refreshToken").ifEmpty { credential.refreshToken },
            expiresAt = jwtClaim(access, "exp").toLongOrNull()?.times(1000) ?: credential.expiresAt,
        )
        val updated = toAccount(refreshed, account.extra["avatar"].orEmpty())
        onAccountUpdated(updated)
        return updated
    }

    // ------------------------------------------------------------------ 内部

    private fun credentialOf(account: ProviderAccount): RaccoonProtocol.Credential =
        RaccoonProtocol.Credential.parse(account.secret)
            ?: throw IllegalStateException("小浣熊凭证无法解析，请重新登录")

    private fun bearerHeaders(accessToken: String): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Authorization" to "Bearer $accessToken",
    )

    private fun jsonHeaders(credential: RaccoonProtocol.Credential): Map<String, String> = buildMap {
        putAll(bearerHeaders(credential.accessToken))
        put("User-Agent", RaccoonProtocol.CLIENT_UA)
        put("Accept-Language", "zh-Hans")
        // 官方桌面客户端会带它，缺了部分接口按非桌面端处理
        put("X-Raccoon-Language", "zh")
        if (credential.orgCode.isNotEmpty()) put("X-Org-Code", credential.orgCode)
    }

    /** 桌面客户端标识头：缺了上游不发登录奖励。 */
    private fun desktopHeaders(): Map<String, String> = mapOf(
        "X-Client-Platform" to RaccoonProtocol.CLIENT_PLATFORM,
        "X-Client-Version" to RaccoonProtocol.CLIENT_VERSION,
    )

    private fun chatHeaders(credential: RaccoonProtocol.Credential): Map<String, String> = buildMap {
        put("Accept", "text/event-stream, application/json")
        put("Authorization", "Bearer ${credential.accessToken}")
        put("User-Agent", RaccoonProtocol.CLIENT_UA)
        put("Accept-Language", "zh-Hans")
        if (credential.orgCode.isNotEmpty()) put("X-Org-Code", credential.orgCode)
    }

    private fun classify(status: Int, body: String): ApiError? {
        if (status in 200..299) return null
        val message = RaccoonProtocol.extractMessage(body).ifEmpty { "上游 HTTP $status：${body.take(160)}" }
        return when {
            status == 401 || status == 403 -> ApiError(ApiError.Kind.AUTH, status, message)
            status == 402 -> ApiError(ApiError.Kind.UPSTREAM, status, "小浣熊额度不足：$message")
            status == 404 -> ApiError(ApiError.Kind.UPSTREAM, status, message)
            status == 429 -> ApiError(ApiError.Kind.UPSTREAM, status, "小浣熊限流：$message")
            status in 500..599 -> ApiError(ApiError.Kind.UPSTREAM, status, "小浣熊服务端错误（HTTP $status）")
            else -> ApiError(ApiError.Kind.CLIENT, status, message)
        }
    }

    private fun queryParam(url: String, name: String): String? = runCatching {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return@runCatching null
        query.split('&').firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=')
            if (key != name) null
            else java.net.URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
        }
    }.getOrNull()

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val ID = "raccoon"

        /** 兜底解析与错误日志只保留这么长的原始响应。 */
        private const val MAX_RAW_SNIFF = 64 * 1024
    }
}
