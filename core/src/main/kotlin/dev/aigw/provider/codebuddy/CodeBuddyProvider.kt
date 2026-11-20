package dev.aigw.provider.codebuddy

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import dev.aigw.provider.AccountActivitySupport
import dev.aigw.provider.ApiError
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.ChatRequest
import dev.aigw.provider.ChatResult
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.CredentialImportSupport
import dev.aigw.provider.DeviceAuthPoll
import dev.aigw.provider.DeviceAuthTicket
import dev.aigw.provider.DeviceCodeSupport
import dev.aigw.provider.ModelCatalogSupport
import dev.aigw.provider.ModelInfo
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.RegionAwareSupport
import dev.aigw.provider.SseSink
import dev.aigw.provider.parseUsageTokens
import dev.aigw.util.Http
import dev.aigw.util.Log
import dev.aigw.util.arrayOrNull
import dev.aigw.util.firstLong
import dev.aigw.util.firstString
import dev.aigw.util.formatTokenCount
import dev.aigw.util.jwtClaim
import dev.aigw.util.objOrNull
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * 腾讯 WorkBuddy / CodeBuddy：国内 `copilot.tencent.com`，国际 `www.workbuddy.ai`。
 *
 * 登录是设备授权：`POST /v2/plugin/auth/state?platform=CLI` 拿授权链接，
 * 用户在浏览器里登录后，`GET /v2/plugin/auth/token?state=…` 轮询拿 token。
 * 对话走 `/v2/chat/completions`（OpenAI 兼容，但上游拒绝非流式，必须强制 stream:true）。
 */
class CodeBuddyProvider : Provider, DeviceCodeSupport, RegionAwareSupport, AccountActivitySupport,
    CredentialImportSupport, ModelCatalogSupport {

    override val id: String = ID
    override val displayName: String = "WorkBuddy"

    override fun regions(): List<String> = listOf(REGION_CN, REGION_GLOBAL)

    override fun regionOf(account: ProviderAccount): String? = regionOf(parse(account))

    // ------------------------------------------------------------------ 模型

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        fetchModels(account).map { models -> models.map { it.id } }

    override fun modelCatalog(account: ProviderAccount): List<ModelInfo> =
        fetchModels(account).getOrElse {
            Log.warn("codebuddy 模型目录获取失败（${account.displayName}）：${it.message}")
            emptyList()
        }

    private fun fetchModels(account: ProviderAccount): Result<List<ModelInfo>> {
        val credential = parse(account) ?: return Result.failure(IllegalStateException("凭证无法解析"))
        val ep = endpoint(credential)
        val url = "${ep.chatBase}${ep.modelsPath}"
        val result = try {
            Http.get(url, modelsHeaders(credential), 15_000)
        } catch (e: Exception) {
            // 代理链路断了就直接重试：国内版接口本来就该直连，
            // 国际版也可能代理不通而直连可达，否则海外模型会成片消失
            val viaProxy = Http.routeOf(url)
            if (viaProxy.startsWith("直连")) {
                return Result.failure(IllegalStateException("模型接口（${ep.label}）请求失败：${e.message}（$viaProxy）", e))
            }
            runCatching { Http.get(url, modelsHeaders(credential), 15_000, direct = true) }
                .onSuccess { Log.warn("codebuddy models: $viaProxy 不可用，已改走直连") }
                .getOrElse {
                    return Result.failure(
                        IllegalStateException(
                            "模型接口（${ep.label}）请求失败：代理 ${e.message}；直连 ${it.message}",
                            e,
                        ),
                    )
                }
        }
        val status = result.status
        val text = result.body
        if (status !in 200..299) {
            Log.warn("codebuddy 模型接口（${ep.label}）HTTP $status（${account.displayName}）：${describeBody(text)}")
            return Result.failure(IllegalStateException("模型接口（${ep.label}）HTTP $status：${describeBody(text)}"))
        }
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: return Result.failure(IllegalStateException("模型响应不是合法 JSON"))
        if (obj.get("code")?.asLong != 0L) {
            return Result.failure(IllegalStateException("模型接口 code=${obj.get("code")}${describeBody(text)}"))
        }
        val data = obj.objOrNull("data") ?: return Result.success(emptyList())
        val regionLabel = if (regionOf(credential) == REGION_GLOBAL) "国际" else "CN"
        return Result.success(parseModelsCatalog(data, regionLabel))
    }

    /** 上游没返回的字段留 0 / 空，界面据此不展示。 */
    /**
     * 按「官方桌面客户端菜单口径」裁剪模型列表。
     *
     * `data.models` 是平台全量目录（含补全模型、内部路由变体、历史别名、已下架项）；
     * 客户端菜单只取默认 agent 的 `models`，再叠三条规则。照单全列等于把客户端
     * 根本不显示、点了还报 11102 的 id 暴露给下游，用户侧表现为「列表里混进一堆
     * 不可用 / 过时的模型」。
     *
     *  1. 默认 agent 的选择优先级：tags 含 default → name==cli → 首个 models 非空。
     *  2. `ModelRateLimitCap.lines[].paidId` 不单列（客户端并进 freeId 那一行）。
     *  3. fast/balanced/deep 三个速度预设照常进菜单；`auto`/`auto-chat` 是「自动模式」
     *     开关本身，不是菜单行。
     *
     * 口径缺失（上游没下发 agents）时不裁剪：无从得知客户端会显示什么，
     * 宁可照旧全列也不臆造白名单。
     */
    private fun parseModelsCatalog(data: JsonObject, regionLabel: String): List<ModelInfo> {
        val agentModels = defaultAgentModels(data)
        val paid = paidModelIds(data)
        val allowed = agentModels.toSet()

        val byId = LinkedHashMap<String, JsonObject>()
        data.arrayOrNull("models")?.forEach { element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEach
            val id = item.get("id")?.asString.orEmpty()
            if (id.isNotEmpty() && !byId.containsKey(id)) byId[id] = item
        }

        // 顺序：不在 agent 名单里的速度预设前置，其余按 agent 原序
        val ordered = LinkedHashSet<String>()
        if (agentModels.isNotEmpty()) {
            for (preset in SPEED_PRESET_IDS) if (preset !in allowed) ordered.add(preset)
        }
        if (agentModels.isNotEmpty()) ordered.addAll(agentModels) else ordered.addAll(byId.keys)

        val result = ArrayList<ModelInfo>()
        for (modelId in ordered) {
            if (modelId in AUTO_MODE_IDS || modelId in paid) continue
            if (agentModels.isNotEmpty() &&
                modelId !in allowed && modelId !in SPEED_PRESET_IDS
            ) continue
            val item = byId[modelId] ?: continue
            if (item.get("disabled")?.asBoolean == true) continue

            val context = item.firstLong("max_input_tokens", "max_context_tokens", "context_window")
                ?.takeIf { it > 0 } ?: 0L
            val maxOut = item.firstLong("max_output_tokens", "max_tokens")
                ?.takeIf { it > 0 } ?: 0L
            val tags = buildList {
                add(regionLabel)
                if (context > 0) add("上下文 ${formatTokenCount(context)}")
                if (maxOut > 0) add("输出 ${formatTokenCount(maxOut)}")
                item.arrayOrNull("capabilities")?.forEach { capability ->
                    if (capability.isJsonPrimitive) add(capability.asString)
                }
            }
            result.add(
                ModelInfo(
                    id = modelId,
                    displayName = item.firstString("display_name", "displayName").ifEmpty { modelId },
                    contextWindow = context,
                    maxOutputTokens = maxOut,
                    tags = tags,
                ),
            )
        }
        return result
    }

    /** 默认 agent 的 models（客户端选 agent 的同一条优先级）。缺失返回空。 */
    private fun defaultAgentModels(data: JsonObject): List<String> {
        val agents = data.arrayOrNull("agents")?.mapNotNull {
            runCatching { it.asJsonObject }.getOrNull()
        }.orEmpty()
        if (agents.isEmpty()) return emptyList()
        fun modelsOf(agent: JsonObject): List<String> =
            agent.arrayOrNull("models")?.mapNotNull { m ->
                runCatching { m.asString }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            }.orEmpty()
        val chosen = agents.firstOrNull { agent ->
            agent.arrayOrNull("tags")?.any {
                runCatching { it.asString }.getOrNull()?.trim()?.equals("default", true) == true
            } == true
        } ?: agents.firstOrNull {
            it.get("name")?.asString?.trim()?.equals("cli", true) == true
        } ?: agents.firstOrNull { modelsOf(it).isNotEmpty() }
        return chosen?.let { modelsOf(it) }.orEmpty()
    }

    /** 付费变体 id（客户端把它们并进 freeId 那一行，不单列）。 */
    private fun paidModelIds(data: JsonObject): Set<String> {
        val lines = data.objOrNull("productFeaturesConfig")
            ?.objOrNull("ModelRateLimitCap")
            ?.arrayOrNull("lines")
            ?: return emptySet()
        return lines.mapNotNull { el ->
            runCatching { el.asJsonObject.get("paidId")?.asString }.getOrNull()
                ?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet()
    }

    // ------------------------------------------------------------------ 对话

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val credential = parse(account) ?: return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "凭证无法解析"))
        val model = resolveModel(request.body.get("model")?.asString.orEmpty())
        val prepared = prepareCodeBuddyRequest(request.body)
        val ep = endpoint(credential)
        val url = "${ep.chatBase}$PATH_CHAT"
        val headers = chatHeaders(credential, prepared.body.toString(), ep)

        return try {
            if (request.stream && sink != null) {
                var events = 0
                var inputTokens = 0
                var outputTokens = 0
                var cachedTokens = 0
                // 思考标签跨 chunk 有状态，整条流共用一个实例
                val thinkTag = CodeBuddyThinkTag()
                fun emit(raw: String) {
                    sink.onData(restoreToolNames(rewriteThinkTag(raw, thinkTag), prepared.toolNameRestore))
                }
                val result = Http.postStream(
                    url = url,
                    headers = headers,
                    json = prepared.body.toString(),
                    timeoutMs = READ_TIMEOUT_MS,
                    onLine = { line ->
                        when {
                            line.startsWith("data:") -> {
                                val data = line.removePrefix("data:").trim()
                                when {
                                    data.isEmpty() -> true
                                    data == "[DONE]" -> {
                                        // 挂住的尾巴可能还有正文，收尾时补发一帧
                                        val (tailThink, tailBody) = thinkTag.flush()
                                        if (tailThink.isNotEmpty() || tailBody.isNotEmpty()) {
                                            emit(TRAILING_DELTA_TPL
                                                .replace("{think}", escapeJson(tailThink))
                                                .replace("{body}", escapeJson(tailBody)))
                                        }
                                        sink.onData("[DONE]")
                                        false
                                    }
                                    else -> {
                                        events++
                                        parseUsageTokens(data)?.let { usage ->
                                            inputTokens = usage.input
                                            outputTokens = usage.output
                                            cachedTokens = usage.cached
                                        }
                                        // 上游有时把多个事件拼在同一行，
                                        // 整块反序列化会失败并被丢弃（表现为丢字/工具调用消失）
                                        val frames = CodeBuddySseSplit.splitFrames(data)
                                        if (frames.isEmpty()) {
                                            emit(data)
                                        } else {
                                            for ((raw, _) in frames) emit(raw)
                                        }
                                        true
                                    }
                                }
                            }
                            else -> true
                        }
                    },
                    onDone = { sink.onDone() },
                )
                if (!result.ok) {
                    ChatResult.Failure(classify(result.status, result.body))
                } else {
                    ChatResult.Streamed(events, inputTokens, outputTokens, cachedTokens)
                }
            } else {
                val chunks = mutableListOf<JsonObject>()
                val result = Http.postStream(
                    url = url,
                    headers = headers,
                    json = prepared.body.toString(),
                    timeoutMs = READ_TIMEOUT_MS,
                    onLine = { line ->
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trim()
                            if (data.isNotEmpty() && data != "[DONE]") {
                                runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()
                                    ?.let { chunks.add(it) }
                            }
                        }
                        true
                    },
                    onDone = { },
                )
                if (!result.ok) {
                    ChatResult.Failure(classify(result.status, result.body))
                } else {
                    val aggregated = aggregate(chunks, model)
                    val restored = restoreToolNames(aggregated.toString(), prepared.toolNameRestore)
                    ChatResult.Aggregated(JsonParser.parseString(restored).asJsonObject)
                }
            }
        } catch (e: Exception) {
            Log.warn("codebuddy upstream error: ${e.message}")
            ChatResult.Failure(Http.networkError(e))
        }
    }

    private fun classify(status: Int, body: String): ApiError {
        val kind = when {
            status == 401 || status == 403 -> ApiError.Kind.AUTH
            status == 429 -> ApiError.Kind.UPSTREAM
            status in 500..599 -> ApiError.Kind.UPSTREAM
            status in 400..499 -> ApiError.Kind.CLIENT
            else -> ApiError.Kind.UPSTREAM
        }
        return ApiError(kind, status, extractMessage(body).ifEmpty { "上游 HTTP $status" })
    }

    private fun aggregate(chunks: List<JsonObject>, model: String): JsonObject {
        val content = StringBuilder()
        val toolCalls = LinkedHashMap<Int, JsonObject>()
        var id = ""
        var created = System.currentTimeMillis() / 1000
        var finish: String? = null
        var usage: JsonObject? = null

        for (ev in chunks) {
            ev.get("id")?.takeIf { it.isJsonPrimitive }?.asString?.let { id = it }
            ev.get("created")?.takeIf { it.isJsonPrimitive }?.asLong?.let { created = it }
            ev.objOrNull("usage")?.let { usage = it }
            val choices = ev.arrayOrNull("choices") ?: continue
            for (c in choices) {
                val choice = runCatching { c.asJsonObject }.getOrNull() ?: continue
                choice.get("finish_reason")?.takeIf { !it.isJsonNull }?.asString?.let { finish = it }
                val delta = choice.objOrNull("delta") ?: continue
                delta.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.let { content.append(it) }
                delta.arrayOrNull("tool_calls")?.forEach { tcEl ->
                    val tc = runCatching { tcEl.asJsonObject }.getOrNull() ?: return@forEach
                    val idx = tc.get("index")?.asInt ?: 0
                    val acc = toolCalls.getOrPut(idx) { JsonObject().apply { add("function", JsonObject()) } }
                    tc.get("id")?.asString?.takeIf { it.isNotEmpty() }?.let { acc.addProperty("id", it) }
                    tc.get("type")?.asString?.takeIf { it.isNotEmpty() }?.let { acc.addProperty("type", it) }
                    val fn = tc.objOrNull("function") ?: return@forEach
                    val accFn = acc.getAsJsonObject("function")
                    fn.get("name")?.asString?.takeIf { it.isNotEmpty() }?.let { accFn.addProperty("name", it) }
                    fn.get("arguments")?.asString?.let { accFn.addProperty("arguments", (accFn.get("arguments")?.asString ?: "") + it) }
                }
            }
        }

        val message = JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", content.toString())
            if (toolCalls.isNotEmpty()) {
                add("tool_calls", JsonArray().apply {
                    toolCalls.values.forEach { tc ->
                        val fn = tc.getAsJsonObject("function")
                        if (fn.get("arguments") == null) fn.addProperty("arguments", "{}")
                        if (tc.get("type") == null) tc.addProperty("type", "function")
                        add(tc)
                    }
                })
            }
        }
        return JsonObject().apply {
            addProperty("id", id.ifEmpty { "chatcmpl-${System.currentTimeMillis()}" })
            addProperty("object", "chat.completion")
            addProperty("created", created)
            addProperty("model", model)
            add("choices", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("index", 0)
                    add("message", message)
                    addProperty("finish_reason", finish ?: "stop")
                })
            })
            usage?.let { add("usage", it) }
        }
    }

    // ------------------------------------------------------------------ 凭证导入

    /** 粘贴凭证 JSON 导入；先拉一次模型目录确认凭证可用。 */
    override fun importCredentials(raw: String): ProviderAccount {
        val obj = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            ?: throw IllegalArgumentException("凭证不是合法 JSON")
        // 兼容多种形状：
        // 1) 裸凭证：顶层直接有 accessToken/access_token；
        // 2) cockpit tools 嵌套：`{tokens:{access_token…}}`；
        // 3) 账号导出 JSON：secret 是「内层凭证的 JSON 字符串」；
        // 4) 账号导出 JSON（已还原转义）：secret 直接是内层凭证对象。
        val keys = obj.keySet().joinToString(",")
        val credential = when {
            obj.has("accessToken") || obj.has("access_token") -> credentialOf(obj)
            obj.has("tokens") -> credentialOf(obj.objOrNull("tokens") ?: JsonObject())
            obj.has("secret") -> {
                val secretNode = obj.get("secret") ?: throw IllegalArgumentException(
                    "凭证缺少 accessToken（顶层键：$keys）",
                )
                val inner = when {
                    secretNode.isJsonObject -> secretNode.asJsonObject
                    secretNode.isJsonPrimitive -> {
                        val text = secretNode.asString
                        runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
                            ?: throw IllegalArgumentException("凭证缺少 accessToken（secret 不是合法 JSON）")
                    }
                    else -> throw IllegalArgumentException("凭证缺少 accessToken（secret 形态异常）")
                }
                credentialOf(inner)
            }
            else -> throw IllegalArgumentException("凭证缺少 accessToken（顶层键：$keys）")
        }
        if (credential.accessToken.isEmpty()) throw IllegalArgumentException("凭证缺少 accessToken（顶层键：$keys）")
        val account = toProviderAccount(credential)
        val models = listModels(account)
        if (models.isFailure) {
            throw IllegalStateException("凭证校验失败：${models.exceptionOrNull()?.message}")
        }
        return account
    }

    // ------------------------------------------------------------------ 签到 / 积分

    /**
     * 每日签到。企业号上游会返回 400 code 10001，直接跳过，不空发请求也不记失败。
     * 幂等：HTTP 2xx、或非 2xx 但 body 含「已签到」/"already" 都算成功。
     */
    override fun checkin(account: ProviderAccount): Result<CheckinResult> {
        val credential = parse(account) ?: return Result.failure(IllegalStateException("凭证无法解析"))
        if (credential.enterpriseId.isNotEmpty()) return Result.success(CheckinResult.Skipped)
        val r = try {
            billingCall(credential, PATH_CHECKIN, "{}")
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("签到请求失败：${e.message}", e))
        }
        if (r.ok) return Result.success(CheckinResult.Done("签到成功"))
        val lower = r.body.lowercase()
        if (r.body.contains("已签到") || lower.contains("already")) {
            return Result.success(CheckinResult.Done("今日已签到"))
        }
        return Result.failure(IllegalStateException("签到失败（HTTP ${r.status}）${describeBody(r.body)}"))
    }

    /**
     * 积分余额。企业号走 get-enterprise-user-usage（额度语义与个人相反），
     * 个人号走 get-user-resource。Accounts 缺失时返回 -1（未知），不能当 0。
     */
    override fun balance(account: ProviderAccount): Result<BalanceInfo> {
        val credential = parse(account) ?: return Result.failure(IllegalStateException("凭证无法解析"))
        return try {
            if (credential.enterpriseId.isNotEmpty()) enterpriseBalance(credential) else personalBalance(credential)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("余额查询失败：${e.message}", e))
        }
    }

    private fun personalBalance(credential: Credential): Result<BalanceInfo> {
        val body = JsonObject().apply {
            addProperty("PageNumber", 1)
            addProperty("PageSize", 100)
            addProperty("ProductCode", "p_tcaca")
            add("Status", JsonArray().apply { add(0); add(3) })
            addProperty("PackageEndTimeRangeBegin", gmt8Now())
            addProperty("PackageEndTimeRangeEnd", gmt8Now(101L * 365 * 24 * 3600 * 1000))
        }
        val envelope = readEnvelope(billingCall(credential, PATH_USER_RESOURCE, body.toString()), "余额")
            .getOrElse { return Result.failure(it) }
        val accounts = envelope.objOrNull("data")?.let { accountsOf(it) }
            ?: return Result.success(BalanceInfo(remain = -1.0))

        val now = System.currentTimeMillis() / 1000
        var remain = 0.0
        val byExpiry = sortedMapOf<Long, Double>()
        for (element in accounts) {
            val item = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val size = item.firstDouble("CycleCapacitySize") ?: 0.0
            val left: Double
            if (size > 0) {
                var cycleRemain = item.firstDouble("CycleCapacityRemain") ?: 0.0
                if (cycleRemain < 0) cycleRemain = 0.0
                if (cycleRemain > size) cycleRemain = size
                var used = size - cycleRemain
                val cycleUsed = item.firstDouble("CycleCapacityUsed") ?: 0.0
                if (cycleUsed > used) {
                    used = cycleUsed
                    if (size >= used) cycleRemain = size - used
                }
                left = cycleRemain
            } else {
                left = maxOf(item.firstDouble("CapacityRemain") ?: 0.0, 0.0)
            }
            remain += left
            if (left <= 0) continue
            val endSec = parseGmt8(item.firstString("CycleEndTime")).takeIf { it > 0 }
                ?: parseGmt8(item.firstString("PackageEndTime"))
            if (endSec <= now) continue
            byExpiry[endSec] = (byExpiry[endSec] ?: 0.0) + left
        }
        val earliest = byExpiry.entries.firstOrNull()
        return Result.success(
            BalanceInfo(
                remain = remain,
                earliestAt = earliest?.key ?: 0,
                earliestRemaining = earliest?.value ?: 0.0,
                enterprise = false,
            ),
        )
    }

    private fun enterpriseBalance(credential: Credential): Result<BalanceInfo> {
        val envelope = readEnvelope(billingCall(credential, PATH_ENTERPRISE_USAGE, "{}"), "企业额度")
            .getOrElse { return Result.failure(it) }
        val data = envelope.objOrNull("data") ?: return Result.success(BalanceInfo(remain = -1.0, enterprise = true))
        val limit = data.firstDouble("limitNum", "limit_num") ?: 0.0
        // enterprise 口径与个人相反：credit 是「已用」，不是剩余
        val used = data.firstDouble("credit", "used_num") ?: 0.0
        val remain = if (limit < 0) UNLIMITED_SENTINEL else maxOf(limit - kotlin.math.round(used), 0.0)
        val endSec = parseGmt8(data.firstString("cycleEndTime", "cycleResetTime"))
        val now = System.currentTimeMillis() / 1000
        val expiring = endSec > now && remain > 0
        return Result.success(
            BalanceInfo(
                remain = remain,
                earliestAt = if (expiring) endSec else 0,
                earliestRemaining = if (expiring) remain else 0.0,
                enterprise = true,
            ),
        )
    }

    /** 计费域调用：只有 404 才换备用路径（去 /v2 前缀），其它状态码原样返回。 */
    private fun billingCall(credential: Credential, path: String, body: String): Http.Result {
        val ep = endpoint(credential)
        val candidates = listOf(path, path.removePrefix("/v2"))
        var last: Http.Result? = null
        for (candidate in candidates) {
            val r = postWithRetry("${ep.billingBase}$candidate", billingHeaders(credential, ep), body)
            last = r
            if (r.status != 404) return r
        }
        return last ?: Http.Result(404, "")
    }

    /** 网络异常（如偶发 DNS 解析失败、VPN 切换导致的连接中断）重试一次，再失败抛出。 */
    private fun postWithRetry(url: String, headers: Map<String, String>, body: String): Http.Result =
        try {
            Http.postJson(url, headers, body, 20_000)
        } catch (first: Exception) {
            Thread.sleep(300)
            Http.postJson(url, headers, body, 20_000)
        }
    private fun readEnvelope(result: Http.Result, what: String): Result<JsonObject> {
        if (!result.ok) {
            return Result.failure(IllegalStateException("${what}接口 HTTP ${result.status}${describeBody(result.body)}"))
        }
        val obj = runCatching { JsonParser.parseString(result.body).asJsonObject }.getOrNull()
            ?: return Result.failure(IllegalStateException("${what}响应不是合法 JSON"))
        if (obj.get("code")?.asLong != 0L) {
            return Result.failure(IllegalStateException("${what}接口 code=${obj.get("code")}${describeBody(result.body)}"))
        }
        return Result.success(obj)
    }

    /** billing 头族：计费域用，和 chat 域的三段 UA / 会话头不能混用。 */
    private fun billingHeaders(credential: Credential, ep: Endpoint): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("Accept", "application/json")
        put("X-CodeBuddy-Request", "1")
        put("Accept-Language", if (regionOf(credential) == REGION_GLOBAL) "en-US" else "zh-CN")
        put("User-Agent", BILLING_UA)
        put("Authorization", "Bearer ${credential.accessToken}")
        put("X-User-Id", credential.uid)
        put("X-Machine-ID", stableId("machine", credential.uid))
        put("X-Session-ID", stableId("session", credential.uid))
        if (credential.enterpriseId.isNotEmpty()) {
            put("X-Enterprise-Id", credential.enterpriseId)
            put("X-Tenant-Id", credential.enterpriseId)
        } else {
            put("X-No-Enterprise-Id", "1")
        }
        put("X-Domain", credential.domain.ifEmpty { ep.chatBase.removePrefix("https://") })
        if (credential.deviceToken.isNotEmpty()) put("X-Device-Token", credential.deviceToken)
    }

    private fun accountsOf(data: JsonObject): JsonArray? {
        val inner = data.objOrNull("Response")?.objOrNull("Data")
            ?: data.objOrNull("Data")
            ?: data
        return inner.arrayOrNull("Accounts")
    }

    // ------------------------------------------------------------------ 设备授权登录

    /**
     * 登录页形态。GUI 里可在「CLI / 扫码」之间切，默认 CLI 与旧版一致。
     *
     * 扫码（WEB）能让已登录网页版的用户直接用手机会话授权，省去在
     * 小屏 WebView 里敲账号密码；但网页登录发的凭证是否与 CLI 等价
     * 还没验证，所以不设为默认。
     */
    @Volatile
    var authPlatform: String = PLATFORM_CLI
        private set

    /** 切换登录形态；传入非预期值时不改。 */
    fun switchAuthPlatform(platform: String) {
        authPlatform = if (platform == PLATFORM_WEB) PLATFORM_WEB else PLATFORM_CLI
    }

    override fun startDeviceAuth(region: String): DeviceAuthTicket {
        val base = endpointOf(region).chatBase
        // platform 决定登录页的形态：
        //   CLI → 浏览器里出「微信登录」按钮，走 CLI 授权流程
        //   WEB → 出二维码，可用手机扫码（与网页版一致）
        // 两者拿到的 state 都能用同一个轮询接口换凭证。
        val url = "$base$PATH_AUTH_STATE?platform=$authPlatform"
        // 登录只发一次，不值得复用连接池：VPN/代理开关切换后池里的旧 socket
        // 已经失效，复用会直接报 Software caused connection abort
        val headers = mapOf("Connection" to "close")
        val r = try {
            postWithRetry(url, headers, "{}")
        } catch (e: Exception) {
            // 这个接口直连可达（未开 VPN 时能拿到链接），代理链路断了就改走直连
            val viaProxy = Http.routeOf(url)
            if (viaProxy.startsWith("直连")) {
                throw IllegalStateException("请求授权链接失败：${e.message}（$viaProxy）")
            }
            runCatching { Http.postJson(url, headers, "{}", 20_000, direct = true) }
                .onSuccess { Log.warn("codebuddy auth: $viaProxy 不可用，已改走直连") }
                .getOrElse {
                    throw IllegalStateException(
                        "请求授权链接失败：代理 ${e.message}；直连 ${it.message}",
                    )
                }
        }
        if (!r.ok) throw IllegalStateException("请求授权链接失败（HTTP ${r.status}）${describeBody(r.body)}")
        val obj = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: throw IllegalStateException("授权响应异常")
        if (obj.get("code")?.asLong != 0L) throw IllegalStateException("授权失败${describeBody(r.body)}")
        val data = obj.objOrNull("data") ?: throw IllegalStateException("授权响应缺少 data")
        val state = data.get("state")?.asString.orEmpty()
        val authUrl = data.get("authUrl")?.asString.orEmpty()
        if (state.isEmpty() || authUrl.isEmpty()) throw IllegalStateException("授权响应缺少 state 或 authUrl")
        return DeviceAuthTicket(loginUrl = authUrl, state = state)
    }

    override fun pollDeviceAuth(state: String, region: String): DeviceAuthPoll {
        val base = endpointOf(region).chatBase
        return pollDeviceAuthAt(base, state)
    }

    /**
     * 在指定域查询 state 的登录结果。
     *
     * 扫码登录的 state 是在 codebuddy.cn 申请的，而 [pollDeviceAuth] 默认查 chatBase
     * （copilot.tencent.com）；state 不跨域，必须回到申请它的那个域去兑。
     */
    fun pollDeviceAuthAt(base: String, state: String): DeviceAuthPoll {
        val encoded = URLEncoder.encode(state, "UTF-8")
        val r = try {
            Http.get("$base$PATH_AUTH_TOKEN?state=$encoded", emptyMap(), 20_000)
        } catch (e: Exception) {
            return DeviceAuthPoll.Failed(e.message ?: "轮询失败")
        }
        if (!r.ok) return DeviceAuthPoll.Failed("轮询失败（HTTP ${r.status}）")
        val obj = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: return DeviceAuthPoll.Failed("轮询响应不是合法 JSON")
        val code = obj.get("code")?.asLong ?: -1L
        if (code == CODE_LOGIN_PENDING) return DeviceAuthPoll.Pending
        if (code != 0L) {
            val message = obj.get("msg")?.asString.orEmpty()
            return DeviceAuthPoll.Failed(message.ifEmpty { "登录未完成（code=$code）" })
        }
        val data = obj.objOrNull("data") ?: return DeviceAuthPoll.Failed("登录响应缺少 data")
        return DeviceAuthPoll.Success(toProviderAccount(credentialOf(data)))
    }

    // ------------------------------------------------------------------ 内部

    private data class Credential(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val domain: String,
        val uid: String,
        val enterpriseId: String,
        val nickname: String,
        val deviceToken: String = "",
    )

    internal data class Endpoint(
        val label: String,
        val chatBase: String,
        val billingBase: String,
        val origin: String,
        val modelsPath: String,
        val extraHeaders: Map<String, String> = emptyMap(),
    )

    private fun parse(account: ProviderAccount): Credential? {
        if (account.secret.isBlank()) return null
        val obj = runCatching { JsonParser.parseString(account.secret).asJsonObject }.getOrNull() ?: return null
        return credentialOf(obj).takeIf { it.accessToken.isNotEmpty() }
    }

    private fun credentialOf(obj: JsonObject): Credential {
        val accessToken = obj.firstString("accessToken", "access_token")
        return Credential(
            accessToken = accessToken,
            refreshToken = obj.firstString("refreshToken", "refresh_token"),
            expiresAt = obj.firstLong("expiresAt", "expires_at")?.takeIf { it > 0 }
                ?: obj.firstLong("expiresIn", "expires_in")?.takeIf { it > 0 }
                    ?.let { System.currentTimeMillis() / 1000 + it }
                ?: 0L,
            domain = obj.firstString("domain"),
            uid = obj.firstString("uid", "userId", "user_id")
                .ifEmpty { jwtClaim(accessToken, "user_id", "userId", "uid", "sub") },
            enterpriseId = obj.firstString("enterpriseId", "enterprise_id", "entId", "tenantId", "tenant_id")
                .ifEmpty { jwtClaim(accessToken, "tenant_id", "tenantId", "enterprise_id", "enterpriseId") },
            nickname = obj.firstString("nickname", "nickName", "name"),
            deviceToken = obj.firstString("deviceToken", "device_token"),
        )
    }

    private fun toProviderAccount(credential: Credential): ProviderAccount {
        val secret = JsonObject().apply {
            addProperty("accessToken", credential.accessToken)
            addProperty("refreshToken", credential.refreshToken)
            addProperty("expiresAt", credential.expiresAt)
            addProperty("domain", credential.domain)
            addProperty("uid", credential.uid)
            addProperty("enterpriseId", credential.enterpriseId)
            addProperty("nickname", credential.nickname)
            if (credential.deviceToken.isNotEmpty()) addProperty("deviceToken", credential.deviceToken)
        }.toString()
        val uid = credential.uid.ifEmpty {
            "codebuddy-" + credential.accessToken.hashCode().toUInt().toString(16)
        }
        return ProviderAccount(
            providerId = id,
            uid = uid,
            displayName = credential.nickname.ifEmpty { "WorkBuddy" },
            secret = secret,
        )
    }

    private fun regionOf(credential: Credential?): String {
        val issuer = jwtClaim(credential?.accessToken.orEmpty(), "iss", "issuer").lowercase()
        if (issuer.contains("workbuddy.ai") || issuer.contains("codebuddy.ai")) return REGION_GLOBAL
        val domain = credential?.domain.orEmpty().trim().lowercase()
        if (domain == "workbuddy.ai" || domain.endsWith(".workbuddy.ai") ||
            domain == "codebuddy.ai" || domain.endsWith(".codebuddy.ai")
        ) {
            return REGION_GLOBAL
        }
        if (domain.isNotEmpty()) return REGION_CN
        return REGION_CN
    }

    private fun endpoint(credential: Credential?): Endpoint = endpointOf(regionOf(credential))

    private fun chatHeaders(credential: Credential, body: String, ep: Endpoint): Map<String, String> = buildMap {
        putAll(commonHeaders(ep))
        put("Authorization", "Bearer ${credential.accessToken}")
        if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
        if (credential.enterpriseId.isNotEmpty()) {
            put("X-Enterprise-Id", credential.enterpriseId)
            put("X-Tenant-Id", credential.enterpriseId)
        }
        if (credential.domain.isNotEmpty()) put("X-Domain", credential.domain)
        put("X-Product", "SaaS")
        putAll(conversationHeaders(body))
    }

    private fun modelsHeaders(credential: Credential): Map<String, String> {
        val ep = endpoint(credential)
        return buildMap {
            putAll(commonHeaders(ep))
            put("Authorization", "Bearer ${credential.accessToken}")
            if (credential.uid.isNotEmpty()) put("X-User-Id", credential.uid)
            if (credential.enterpriseId.isNotEmpty()) {
                put("X-Enterprise-Id", credential.enterpriseId)
                put("X-Tenant-Id", credential.enterpriseId)
            }
            if (credential.domain.isNotEmpty()) put("X-Domain", credential.domain)
            put("X-Product", "SaaS")
            putAll(ep.extraHeaders)
        }
    }

    private fun commonHeaders(ep: Endpoint): Map<String, String> = mapOf(
        "Content-Type" to "application/json",
        "Accept" to "application/json, text/plain, */*",
        "X-Requested-With" to "XMLHttpRequest",
        "Origin" to ep.origin,
        "Referer" to "${ep.origin}/",
        "User-Agent" to CLIENT_UA,
    )

    /**
     * 会话头：`X-Conversation-ID` 由消息前缀确定性派生（同会话稳定，上游按它做提示词缓存分桶），
     * 其余请求/消息/trace id 每请求换新。
     */
    private fun conversationHeaders(body: String): Map<String, String> {
        val traceId = UUID.randomUUID().toString()
        val conversationId = stableConversationId(body).ifEmpty { FALLBACK_CONVERSATION_ID }
        return mapOf(
            "X-Conversation-ID" to conversationId,
            "X-Conversation-Request-ID" to UUID.randomUUID().toString(),
            "X-Conversation-Message-ID" to UUID.randomUUID().toString(),
            "X-Request-ID" to traceId,
            "X-Trace-ID" to traceId,
            "X-Agent-Intent" to "craft",
            "X-Agent-Purpose" to "conversation",
            "X-IDE-Type" to "CLI",
            "X-IDE-Name" to "CLI",
            "x-codebuddy-request" to "1",
        )
    }

    private fun resolveModel(requested: String): String =
        requested.trim().let { if (it.isEmpty() || it == "auto") DEFAULT_MODEL_ID else it }

    private fun extractMessage(body: String): String {
        if (body.isEmpty()) return ""
        val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull() ?: return ""
        obj.get("msg")?.asString?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.objOrNull("error")?.get("message")?.asString?.takeIf { it.isNotEmpty() }?.let { return it }
        obj.get("message")?.asString?.takeIf { it.isNotEmpty() }?.let { return it }
        return ""
    }

    companion object {
        const val ID = "codebuddy"
        const val REGION_CN = "cn"
        const val REGION_GLOBAL = "global"

        const val PATH_AUTH_STATE = "/v2/plugin/auth/state"
        const val PATH_AUTH_TOKEN = "/v2/plugin/auth/token"

        /** 登录页形态：CLI（按钮式）与 WEB（二维码）。 */
        const val PLATFORM_CLI = "CLI"
        const val PLATFORM_WEB = "WEB"
        const val PATH_CHAT = "/v2/chat/completions"
        const val PATH_CHECKIN = "/v2/billing/meter/daily-checkin"
        const val PATH_USER_RESOURCE = "/v2/billing/meter/get-user-resource"
        const val PATH_ENTERPRISE_USAGE = "/v2/billing/meter/get-enterprise-user-usage"
        const val BILLING_UA = "WorkBuddy/5.5.4"

        /** 企业号额度不限量时上游用 limitNum < 0 表示，这里映射成哨兵值。 */
        const val UNLIMITED_SENTINEL = 1099511627776.0
        const val PATH_MODELS_CN = "/console/enterprises/personal/models"
        const val PATH_MODELS_GLOBAL = "/v3/config"
        const val DEFAULT_MODEL_ID = "default-model"
        const val CODE_LOGIN_PENDING = 11217L
        const val CLIENT_UA = "CLI/2.63.2 CodeBuddy/2.63.2"

        private const val READ_TIMEOUT_MS = 300_000

        private val CN_ENDPOINT = Endpoint(
            label = "国内版",
            chatBase = "https://copilot.tencent.com",
            billingBase = "https://www.codebuddy.cn",
            origin = "https://www.codebuddy.cn",
            modelsPath = PATH_MODELS_CN,
        )

        private val GLOBAL_ENDPOINT = Endpoint(
            label = "国际版",
            chatBase = "https://www.workbuddy.ai",
            billingBase = "https://www.workbuddy.ai",
            origin = "https://www.workbuddy.ai",
            modelsPath = PATH_MODELS_GLOBAL,
            extraHeaders = mapOf("X-Agent-Type" to "main"),
        )

        internal fun endpointOf(region: String): Endpoint =
            if (region == REGION_GLOBAL) GLOBAL_ENDPOINT else CN_ENDPOINT

        fun describeBody(body: String): String {
            if (body.isBlank()) return ""
            val obj = runCatching { JsonParser.parseString(body).asJsonObject }.getOrNull()
            val message = obj?.firstString("msg", "message", "desc") ?: ""
            if (message.isNotEmpty()) return "：$message"
            return "：${body.trim().take(160)}"
        }

        fun stableConversationId(openAiBody: String): String {
            val obj = runCatching { JsonParser.parseString(openAiBody).asJsonObject }.getOrNull() ?: return ""
            val messages = obj.arrayOrNull("messages") ?: return ""
            val seed = StringBuilder()
            var firstUser: String? = null
            for (element in messages) {
                val message = runCatching { element.asJsonObject }.getOrNull() ?: continue
                val role = message.get("role")?.asString.orEmpty()
                if (role == "system" || role == "developer") {
                    seed.append(role).append(':').append(contentTextOf(message)).append('\n')
                } else if (role == "user" && firstUser == null) {
                    firstUser = contentTextOf(message)
                }
            }
            if (firstUser != null) seed.append("user:").append(firstUser).append('\n')
            if (seed.isEmpty()) return ""
            val digest = MessageDigest.getInstance("SHA-256").digest(seed.toString().toByteArray())
            val hex = digest.joinToString("") { "%02x".format(it) }
            return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
                "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
        }

        private fun contentTextOf(message: JsonObject): String {
            val content = message.get("content") ?: return ""
            if (content.isJsonPrimitive && content.asJsonPrimitive.isString) return content.asString
            val array = content as? JsonArray ?: return ""
            return array.mapNotNull { part ->
                runCatching { part.asJsonObject.get("text")?.asString }.getOrNull()
            }.joinToString("")
        }

        const val FALLBACK_CONVERSATION_ID = "00000000-0000-0000-0000-000000000000"
    }
}

// ---------------------------------------------------------------------- 时间与稳定 ID

private val GMT8: ZoneId = ZoneId.of("Asia/Shanghai")

private fun gmt8Now(offsetMillis: Long = 0): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .format(Instant.now().plusMillis(offsetMillis).atZone(GMT8))

/** 上游到期时间固定按 GMT+8 解析（带时区的 ISO 串另行处理）。 */
private fun parseGmt8(text: String): Long {
    val value = text.trim()
    if (value.isEmpty()) return 0
    val patterns = listOf(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    )
    for (pattern in patterns) {
        runCatching { LocalDateTime.parse(value, pattern).atZone(GMT8).toEpochSecond() }
            .getOrNull()?.let { return it }
    }
    return runCatching { Instant.parse(value).epochSecond }
        .recoverCatching { OffsetDateTime.parse(value).toEpochSecond() }
        .getOrDefault(0)
}

/** 稳定 ID：sha256("wb2a:<purpose>:<uid>") 前 18 字节的 hex（共 36 字符），同账号跨重启恒定。 */
private fun stableId(purpose: String, uid: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest("wb2a:$purpose:$uid".toByteArray())
        .take(18)
        .joinToString("") { "%02x".format(it) }

private fun JsonObject.firstDouble(vararg keys: String): Double? {
    for (key in keys) {
        val value = get(key) ?: continue
        if (!value.isJsonPrimitive) continue
        val primitive = value.asJsonPrimitive
        if (primitive.isNumber) return primitive.asDouble
        primitive.asString.trim().toDoubleOrNull()?.let { return it }
    }
    return null
}

// ---------------------------------------------------------------------- 请求体归一

internal data class PreparedCodeBuddyRequest(
    val body: JsonObject,
    val toolNameRestore: Map<String, String>,
)

/**
 * 改写发往 WorkBuddy 的请求体：
 * 1. 强制 stream:true（上游拒绝非流式）；
 * 2. tool_choice 归一成字符串；
 * 3. 消息形状对齐（首条必须 system、developer 改 system、tool 后跟 user 时补一条非空 assistant）；
 * 4. max_completion_tokens → max_tokens；
 * 5. 去掉上游会拦的第三方 CLI 特征文本；
 * 6. 工具名与 schema 归一。
 */
/**
 * 把 delta.content 里混进的 `<think:SUF>` 标签拆到 reasoning_content。
 *
 * 无变化时直接返回原串，避免无谓的重新序列化。
 */
private fun rewriteThinkTag(json: String, tag: CodeBuddyThinkTag): String {
    if (!json.contains("<think:")) return json
    val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return json
    val choices = obj.getAsJsonArray("choices") ?: return json
    var changed = false
    for (i in 0 until choices.size()) {
        val choice = runCatching { choices[i].asJsonObject }.getOrNull() ?: continue
        val delta = choice.getAsJsonObject("delta") ?: continue
        val contentEl = delta.get("content") ?: continue
        if (!contentEl.isJsonPrimitive || !contentEl.asJsonPrimitive.isString) continue
        val raw = contentEl.asString
        val (think, body) = tag.feed(raw)
        if (think == "" && body == raw) continue
        delta.remove("content")
        delta.addProperty("content", body)
        if (think.isNotEmpty()) delta.addProperty("reasoning_content", think)
        changed = true
    }
    return if (changed) obj.toString() else json
}

/** 收尾补发帧的模板。 */
private val TRAILING_DELTA_TPL = """{"choices":[{"index":0,"delta":{"content":"{body}","reasoning_content":"{think}"}}]}"""

/** 把文本转成 JSON 字符串字面量的内部片段（不包外层引号）。 */
private fun escapeJson(s: String): String = buildString(s.length + 8) {
    for (c in s) {
        when {
            c == '"' -> append('\\').append('"')
            c == '\\' -> append('\\').append('\\')
            c == '\n' -> append('\\').append('n')
            c == '\r' -> append('\\').append('r')
            c == '\t' -> append('\\').append('t')
            c.code < 0x20 -> append("\\u").append("%04x".format(c.code))
            else -> append(c)
        }
    }
}

internal fun prepareCodeBuddyRequest(body: JsonObject): PreparedCodeBuddyRequest {
    body.addProperty("stream", true)
    normalizeToolChoice(body)
    normalizeMessages(body)
    normalizeMaxTokens(body)
    applyOutputFloor(body)
    unflagMessages(body)
    fillEmptyTextParts(body)
    val restore = normalizeTools(body)
    return PreparedCodeBuddyRequest(body, restore)
}

private const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."

private fun normalizeMaxTokens(obj: JsonObject) {
    val legacy = obj.get("max_completion_tokens") ?: return
    obj.remove("max_completion_tokens")
    if (!obj.has("max_tokens")) obj.add("max_tokens", legacy)
}

/** 客户端当作「自动模式」开关、不画成菜单行的 id。 */
private val AUTO_MODE_IDS = setOf("auto", "auto-chat")

/** 客户端的三个速度预设（菜单顶部「快速 / 均衡 / 极致」），顺序即客户端顺序。 */
private val SPEED_PRESET_IDS = listOf("fast-model", "balanced-model", "deep-model")

/** 探活级预算的下界：官方客户端用 max_tokens=1 做 key 探活，那种不能抬。 */
private const val STARVED_BUDGET_LOW = 16L

/** 抬到超过模型能力会被上游回 400，从「能用」变「硬失败」，这里封个顶。 */
private const val OUTPUT_FLOOR_CAP = 16384L

/**
 * 按思考档位抬输出预算下限。
 *
 * 上游把 reasoning 和正文算在**同一份** max_tokens 里。客户端指定高档位思考、
 * 又把 max_tokens 给得比这档思考起码要花的量还小时，额度先被思考吃光，
 * 正文写到一半就断（finish=length）。
 *
 * 实测（同一「写 3000+ 字文档」提示词）：
 * ```
 * effort=high + max_tokens=2048 → think=2048 正文=2048 finish=length
 * effort=high + 不给额度        → think=2998 正文=13722 finish=stop
 * ```
 *
 * 抬的是**上限**不是目标长度：模型写完自然停，不会因为上限变大多写一个字。
 * 只对 DeepSeek 系生效（其他模型的思考语义不同）；客户端显式 disabled 时不动。
 */
private fun applyOutputFloor(obj: JsonObject) {
    val model = obj.get("model")?.asString.orEmpty()
    if (!model.trim().lowercase().startsWith("deepseek")) return
    val thinking = obj.getAsJsonObject("thinking")
    if (thinking?.get("type")?.asString?.trim()?.equals("disabled", true) == true) return
    val effort = (
        obj.get("reasoning_effort")?.asString
            ?: obj.get("reasoningEffort")?.asString
        )?.trim()?.lowercase().orEmpty()
    val floor = when (effort) {
        "minimal", "low" -> 4096L
        "medium" -> 6144L
        "high" -> 8192L
        "xhigh" -> 12288L
        "max" -> 16384L
        else -> return
    }
    val current = obj.get("max_tokens")?.let { runCatching { it.asLong }.getOrNull() } ?: return
    if (current < STARVED_BUDGET_LOW) return
    val target = maxOf(current, minOf(floor, OUTPUT_FLOOR_CAP))
    if (target > current) {
        obj.addProperty("max_tokens", target)
        Log.warn("codebuddy 输出预算 $current -> $target（档位 $effort，抬上限不改答案长度）")
    }
}

/**
 * 补位多模态消息里的空 text 段。
 *
 * 上游对 `text` 为 ""/null/缺失的 text 段直接判 400（code=11151
 * "a message has empty content"）。补一个空格即可，且**只补值不删段** ——
 * 删段会改变段序与下标，反而让多图消息错位。
 */
private fun fillEmptyTextParts(obj: JsonObject) {
    val messages = obj.arrayOrNull("messages") ?: return
    for (element in messages) {
        val message = runCatching { element.asJsonObject }.getOrNull() ?: continue
        val content = message.get("content") ?: continue
        if (!content.isJsonArray) continue
        val parts = content.asJsonArray
        for (part in parts) {
            val partObj = runCatching { part.asJsonObject }.getOrNull() ?: continue
            val type = partObj.get("type")?.asString.orEmpty()
            if (type != "text") continue
            val text = partObj.get("text")
            val blank = text == null || text.isJsonNull ||
                (text.isJsonPrimitive && text.asJsonPrimitive.isString && text.asString.isBlank())
            if (blank) partObj.addProperty("text", " ")
        }
    }
}

private fun normalizeMessages(obj: JsonObject) {
    val messages = obj.arrayOrNull("messages") ?: return
    for (element in messages) {
        val message = runCatching { element.asJsonObject }.getOrNull() ?: continue
        if (message.get("role")?.asString == "developer") message.addProperty("role", "system")
        // 上游拒绝任何空 content（"a message has empty content"），而带 tool_calls 的
        // assistant 消息常被客户端回传为 content 空串 / null，这里补上非空占位。
        if (message.get("role")?.asString == "assistant" && isBlankContent(message)) {
            message.addProperty("content", "Done.")
        }
    }

    val rebuilt = JsonArray()
    var prevRole: String? = null
    for (element in messages) {
        val role = runCatching { element.asJsonObject.get("role")?.asString }.getOrNull()
        if (prevRole == "tool" && role == "user") {
            rebuilt.add(JsonObject().apply {
                addProperty("role", "assistant")
                // 上游拒绝任何空 content（"a message has empty content"），占位必须非空
                addProperty("content", "Done.")
            })
        }
        rebuilt.add(element)
        prevRole = role
    }

    val first = rebuilt.firstOrNull()?.let { runCatching { it.asJsonObject }.getOrNull() }
    if (first == null || first.get("role")?.asString != "system") {
        val withSystem = JsonArray()
        withSystem.add(JsonObject().apply {
            addProperty("role", "system")
            addProperty("content", DEFAULT_SYSTEM_PROMPT)
        })
        for (element in rebuilt) withSystem.add(element)
        obj.add("messages", withSystem)
    } else {
        obj.add("messages", rebuilt)
    }
}

private fun isBlankContent(message: JsonObject): Boolean {
    val content = message.get("content") ?: return true
    if (content.isJsonNull) return true
    return content.isJsonPrimitive && content.asJsonPrimitive.isString && content.asString.isBlank()
}

private fun normalizeToolChoice(obj: JsonObject) {
    val choice = obj.get("tool_choice") ?: return
    when {
        choice.isJsonPrimitive -> {
            if (choice.asString.equals("none", ignoreCase = true)) {
                obj.remove("tool_choice")
                obj.remove("tools")
                obj.remove("functions")
            }
        }
        choice.isJsonObject -> {
            val type = choice.asJsonObject.get("type")?.asString.orEmpty().lowercase()
            when (type) {
                "none" -> {
                    obj.remove("tool_choice")
                    obj.remove("tools")
                    obj.remove("functions")
                }
                "auto", "required" -> obj.addProperty("tool_choice", type)
                "function" -> {
                    val name = choice.asJsonObject.objOrNull("function")?.get("name")?.asString.orEmpty()
                    obj.addProperty("tool_choice", name.ifEmpty { "auto" })
                }
                else -> obj.remove("tool_choice")
            }
        }
        else -> obj.remove("tool_choice")
    }
}

private fun normalizeTools(obj: JsonObject): Map<String, String> {
    val tools = obj.arrayOrNull("tools") ?: return emptyMap()
    val restore = LinkedHashMap<String, String>()
    for (element in tools) {
        val tool = runCatching { element.asJsonObject }.getOrNull() ?: continue
        val function = tool.objOrNull("function") ?: continue
        function.get("name")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.let { name ->
            val original = name.asString
            val sanitized = sanitizeToolName(original)
            if (sanitized != original) {
                function.addProperty("name", sanitized)
                restore.putIfAbsent(sanitized, original)
            }
        }
        function.get("parameters")?.let { function.add("parameters", normalizeToolParameters(it)) }
    }
    return restore
}

private fun sanitizeToolName(name: String): String =
    name.map { ch -> if (ch == '_' || ch == '-' || (ch.code < 128 && ch.isLetterOrDigit())) ch else '_' }.joinToString("")

private val JSON_SCHEMA_TYPES =
    setOf("string", "number", "integer", "object", "array", "boolean", "null")

private val SCHEMA_MAP_KEYS = listOf("properties", "patternProperties", "definitions", "\$defs")
private val SCHEMA_LIST_KEYS = listOf("anyOf", "oneOf", "allOf")

internal fun normalizeToolParameters(parameters: JsonElement): JsonElement =
    if (parameters.isJsonObject) normalizeRoot(parameters.asJsonObject) else parameters

private fun normalizeRoot(src: JsonObject): JsonObject {
    val branches = ArrayList<JsonObject>().apply {
        add(src)
        SCHEMA_LIST_KEYS.forEach { key ->
            src.arrayOrNull(key)?.forEach { if (it.isJsonObject) add(it.asJsonObject) }
        }
    }
    val declared = normalizedTypeElement(src)
    if (declared?.isJsonPrimitive == true && declared.asString == "object" && branches.size == 1) {
        return normalizeSchemaObject(src)
    }
    if (declared?.isJsonPrimitive == true && declared.asString != "object") {
        return normalizeSchemaObject(src)
    }
    val out = JsonObject()
    out.addProperty("type", "object")
    val properties = JsonObject()
    val required = LinkedHashSet<String>()
    for (branch in branches) {
        branch.objOrNull("properties")?.entrySet()?.forEach { (name, schema) ->
            properties.add(name, normalizeSchema(schema))
        }
        branch.arrayOrNull("required")?.forEach { item ->
            if (item.isJsonPrimitive && item.asJsonPrimitive.isString) required.add(item.asString)
        }
    }
    if (properties.size() > 0) out.add("properties", properties)
    if (required.isNotEmpty()) {
        out.add("required", JsonArray().apply { required.forEach { add(JsonPrimitive(it)) } })
    }
    for ((key, value) in src.entrySet()) {
        if (key == "properties" || key == "required" || key in SCHEMA_LIST_KEYS) continue
        out.add(key, value)
    }
    return out
}

private fun normalizeSchemaObject(src: JsonObject): JsonObject {
    val out = JsonObject()
    normalizedTypeElement(src)?.let { out.add("type", it) }

    src.arrayOrNull("required")?.let { required ->
        val names = required.filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            .map { it.asString }
        if (names.isNotEmpty()) {
            out.add("required", JsonArray().apply { names.forEach { add(JsonPrimitive(it)) } })
        }
    }

    (src.get("pattern") as? JsonPrimitive)?.takeIf { it.isString }?.let { pattern ->
        out.addProperty("pattern", pattern.asString.replace("\\0", "\\u0000"))
    }

    src.get("items")?.let { items -> out.add("items", normalizeItems(items)) }

    SCHEMA_MAP_KEYS.forEach { key ->
        src.objOrNull(key)?.let { children ->
            out.add(
                key,
                JsonObject().apply { children.entrySet().forEach { (name, s) -> add(name, normalizeSchema(s)) } },
            )
        }
    }
    SCHEMA_LIST_KEYS.forEach { key ->
        src.arrayOrNull(key)?.let { list ->
            out.add(key, JsonArray().apply { list.forEach { add(normalizeSchema(it)) } })
        }
    }

    normalizeEnum(src, out)

    val handled = buildSet {
        add("type"); add("required"); add("pattern"); add("items"); add("enum")
        addAll(SCHEMA_MAP_KEYS); addAll(SCHEMA_LIST_KEYS)
    }
    for ((key, value) in src.entrySet()) {
        if (key !in handled) out.add(key, value)
    }
    return out
}

private fun normalizeItems(items: JsonElement): JsonElement = if (items.isJsonArray) {
    JsonObject().apply {
        add("anyOf", JsonArray().apply { items.asJsonArray.forEach { add(normalizeSchema(it)) } })
    }
} else {
    normalizeSchema(items)
}

private fun normalizeSchema(node: JsonElement): JsonElement {
    if (node.isJsonPrimitive && node.asJsonPrimitive.isBoolean) {
        return if (node.asBoolean) JsonObject() else JsonObject().apply { add("not", JsonObject()) }
    }
    if (!node.isJsonObject) return node
    return normalizeSchemaObject(node.asJsonObject)
}

private fun normalizeEnum(src: JsonObject, out: JsonObject) {
    val values = src.arrayOrNull("enum") ?: return
    if (values.size() == 0) return
    if (normalizedTypeElement(src) != null) {
        out.add("enum", values)
        return
    }
    val groups = LinkedHashMap<String, JsonArray>()
    for (value in values) groups.getOrPut(jsonTypeOf(value)) { JsonArray() }.add(value)
    val nonNull = groups.keys.filter { it != "null" }
    when {
        nonNull.isEmpty() -> out.add("enum", values)
        nonNull.size == 1 && groups.size == 1 -> {
            out.addProperty("type", nonNull[0])
            out.add("enum", values)
        }
        nonNull.size == 1 -> {
            out.add("type", JsonArray().apply {
                add(JsonPrimitive(nonNull[0]))
                add(JsonPrimitive("null"))
            })
            out.add("enum", values)
        }
        else -> out.add("anyOf", JsonArray().apply {
            groups.forEach { (type, typedValues) ->
                add(JsonObject().apply {
                    addProperty("type", type)
                    add("enum", typedValues)
                })
            }
        })
    }
}

private fun normalizedTypeElement(src: JsonObject): JsonElement? {
    val raw = src.get("type") ?: return null
    if (raw.isJsonPrimitive && raw.asJsonPrimitive.isString) {
        return JsonPrimitive(raw.asString.lowercase()).takeIf { it.asString in JSON_SCHEMA_TYPES }
    }
    if (raw.isJsonArray) {
        val kept = raw.asJsonArray
            .filter { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            .map { it.asString.lowercase() }
            .filter { it in JSON_SCHEMA_TYPES }
        if (kept.isEmpty()) return null
        return JsonArray().apply { kept.forEach { add(JsonPrimitive(it)) } }
    }
    return null
}

private fun jsonTypeOf(value: JsonElement): String = when {
    value.isJsonNull -> "null"
    value.isJsonArray -> "array"
    value.isJsonObject -> "object"
    value.isJsonPrimitive -> {
        val primitive = value.asJsonPrimitive
        when {
            primitive.isBoolean -> "boolean"
            primitive.isNumber -> "number"
            else -> "string"
        }
    }
    else -> "string"
}

internal fun restoreToolNames(json: String, restore: Map<String, String>): String {
    if (restore.isEmpty()) return json
    val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return json
    return if (rewriteToolCallNames(obj, restore)) obj.toString() else json
}

private fun rewriteToolCallNames(obj: JsonObject, restore: Map<String, String>): Boolean {
    var changed = false
    obj.arrayOrNull("choices")?.forEach { element ->
        val choice = element as? JsonObject ?: return@forEach
        for (key in listOf("message", "delta")) {
            val container = choice.objOrNull(key) ?: continue
            container.arrayOrNull("tool_calls")?.forEach { callElement ->
                val call = callElement as? JsonObject ?: return@forEach
                val function = call.objOrNull("function") ?: return@forEach
                val name = function.get("name")?.takeIf { it.isJsonPrimitive }?.asString ?: return@forEach
                restore[name]?.let {
                    function.addProperty("name", it)
                    changed = true
                }
            }
        }
    }
    return changed
}

/** 去掉上游会拦的第三方 CLI 特征文本（Claude Code / Codex CLI 系统提示词片段）。 */
private val FLAGGED_TEXT = listOf(
    Regex("x-anthropic-billing-header:[^\\n]*\\n?", RegexOption.IGNORE_CASE) to "",
    Regex("You are Claude Code, Anthropic['’]s official CLI for Claude", RegexOption.IGNORE_CASE) to
        "You are Claude Code, Anthropic's CLI for Claude",
    Regex("Main branch \\(you will usually use this for PRs\\)", RegexOption.IGNORE_CASE) to
        "Main branch (usually the base for PRs)",
    Regex("(?<!the )Codex CLI is an open source project led by OpenAI\\.", RegexOption.IGNORE_CASE) to
        "The Codex CLI is an open source project led by OpenAI.",
)

internal fun unflagText(text: String): String {
    var result = text
    for ((pattern, replacement) in FLAGGED_TEXT) result = pattern.replace(result, replacement)
    return result
}

private fun unflagMessages(obj: JsonObject) {
    val messages = obj.arrayOrNull("messages") ?: return
    for (element in messages) {
        val message = runCatching { element.asJsonObject }.getOrNull() ?: continue
        val content = message.get("content") ?: continue
        when {
            content.isJsonPrimitive && content.asJsonPrimitive.isString ->
                message.addProperty("content", unflagText(content.asString))
            content.isJsonArray -> {
                for (part in content.asJsonArray) {
                    val partObj = runCatching { part.asJsonObject }.getOrNull() ?: continue
                    val text = partObj.get("text") ?: continue
                    if (text.isJsonPrimitive && text.asJsonPrimitive.isString) {
                        partObj.addProperty("text", unflagText(text.asString))
                    }
                }
            }
        }
    }
}
