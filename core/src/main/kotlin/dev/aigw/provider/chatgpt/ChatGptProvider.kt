package dev.aigw.provider.chatgpt

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
import dev.aigw.provider.DisplayNameSupport
import dev.aigw.provider.Provider
import dev.aigw.provider.ProviderAccount
import dev.aigw.provider.SseSink
import dev.aigw.provider.TokenRefreshSupport
import dev.aigw.provider.WebCallbackSupport
import dev.aigw.util.Http
import dev.aigw.util.Log
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * ChatGPT（Codex 后端）。
 *
 * 走的是 Codex CLI 那条通道，不是 OpenAI 官方 API：
 *
 * - 鉴权：`auth.openai.com` 的 OAuth（PKCE），换出的 access_token 当 Bearer
 * - 端点：`chatgpt.com/backend-api/codex/responses`
 * - 好处：订阅额度独立计算，免费账号也有调用量，不需要付费 API Key
 *
 * 请求体不是标准 chat/completions，而是 Responses API 形态：`messages` 要拆成
 * `input[]`，system 变 `developer`，tool 调用要拆成独立条目。这段转换见 [toCodexBody]，
 * 是整个实现里最容易出错的地方。
 *
 * 图片生成不可用：Codex 后端只有推理端点，没有生图接口。
 */
class ChatGptProvider : Provider, WebCallbackSupport, TokenRefreshSupport,
    AccountActivitySupport, DisplayNameSupport {

    override val id = ID
    override val displayName = "ChatGPT"

    companion object {
        const val ID = "chatgpt"

        private const val AUTH_HOST = "https://auth.openai.com"
        private const val API_HOST = "https://chatgpt.com"

        /** Codex CLI 的公开 client_id。 */
        private const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"

        /** Codex 后端按 UA 识别客户端，必须伪装成官方 CLI。 */
        const val USER_AGENT = "codex_cli_rs/0.163.0 (Linux; Android) aigw-gateway"

        private const val CALLBACK_PORT = 1455
        private const val CALLBACK_PATH = "/auth/callback"

        /** 对外模型名 → 上游模型名。 */
        val MODELS = linkedMapOf(
            "chatgpt-gpt-5.1-codex" to "gpt-5.1-codex",
            "chatgpt-gpt-5" to "custom_model_gpt-5",
        )

        /** 登录时暂存的 code_verifier，key 是 state。 */
        private val pendingVerifiers = java.util.concurrent.ConcurrentHashMap<String, String>()

        /**
         * 最近一次登录用的 verifier。
         *
         * 用户可能先关掉 app 再去浏览器登录，回来时 state 已经对不上了；
         * 留一个"最近一次"就能在只粘贴 code 时兜底，每次开始登录都会覆盖。
         */
        @Volatile
        private var lastVerifier: String = ""

        internal fun randomUrlSafe(bytes: Int): String {
            val buf = ByteArray(bytes)
            SecureRandom().nextBytes(buf)
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf)
        }

        internal fun s256(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        private fun redirectUri() = "http://localhost:$CALLBACK_PORT$CALLBACK_PATH"
    }

    override val callbackPort get() = CALLBACK_PORT
    override val callbackPath get() = CALLBACK_PATH

    // ---------- 登录 ----------

    /**
     * 拼授权页。
     *
     * code_verifier 与 state 分开存：state 由上游原样回传，verifier 留在本地表里。
     * 回调用 state 找回 verifier，再换 token。
     */
    override fun beginWebLogin(region: String, callbackUrl: String): String {
        val verifier = randomUrlSafe(48)
        val state = randomUrlSafe(16)
        // 登录很少发生，表里最多几条
        pendingVerifiers[state] = verifier
        lastVerifier = verifier
        return buildString {
            append("$AUTH_HOST/oauth/authorize?")
            append("response_type=code")
            append("&client_id=").append(CLIENT_ID)
            append("&redirect_uri=").append(URLEncoder.encode(redirectUri(), "UTF-8"))
            append("&scope=").append(URLEncoder.encode("openid profile email offline_access", "UTF-8"))
            append("&code_challenge=").append(s256(verifier))
            append("&code_challenge_method=S256")
            append("&state=").append(URLEncoder.encode(state, "UTF-8"))
            append("&id_token_add_organizations=true")
            append("&codex_cli_simplified_flow=true")
            append("&originator=codex_cli_rs")
        }
    }

    override fun completeWebLogin(callbackUrl: String): ProviderAccount {
        val uri = runCatching { java.net.URI(callbackUrl) }.getOrNull()
            ?: error("回调地址无法解析")
        val query = parseQuery(uri.rawQuery.orEmpty())

        query["error"]?.let { err ->
            val desc = query["error_description"] ?: ""
            error("授权失败：$err $desc".trim())
        }
        val code = query["code"] ?: error("回调里没有 code")
        val state = query["state"] ?: error("回调里没有 state")
        // 优先从本地表取；进程被回收时表会空，再退到最近一次登录的 verifier
        val verifier = pendingVerifiers.remove(state)
            ?: state.substringAfter('|', "").takeIf { it.isNotEmpty() }
            ?: lastVerifier.takeIf { it.isNotEmpty() }
            ?: error("找不到 code_verifier，请重新登录")

        val form = buildString {
            append("grant_type=authorization_code")
            append("&client_id=").append(CLIENT_ID)
            append("&code=").append(URLEncoder.encode(code, "UTF-8"))
            append("&redirect_uri=").append(URLEncoder.encode(redirectUri(), "UTF-8"))
            append("&code_verifier=").append(URLEncoder.encode(verifier, "UTF-8"))
        }
        val r = Http.postForm(
            "$AUTH_HOST/oauth/token",
            mapOf("Accept" to "application/json"),
            form,
        )
        if (!r.ok) error("换取 token 失败（HTTP ${r.status}）：${r.body.take(200)}")

        val json = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: error("token 响应不是合法 JSON")
        val access = json.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
            ?: error("响应里没有 access_token")
        val refresh = json.get("refresh_token")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val idToken = json.get("id_token")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val accountId = accountIdOf(access) ?: accountIdOf(idToken).orEmpty()
        val expiresIn = json.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong ?: 3600L

        return buildAccount(access, refresh, accountId, expiresIn)
    }

    private fun buildAccount(
        access: String,
        refresh: String,
        accountId: String,
        expiresIn: Long,
    ): ProviderAccount {
        val secret = JsonObject().apply {
            addProperty("accessToken", access)
            addProperty("refreshToken", refresh)
            addProperty("accountId", accountId)
            addProperty("expiresAt", System.currentTimeMillis() + expiresIn * 1000)
        }.toString()
        val uid = accountId.ifEmpty { access.takeLast(16) }
        return ProviderAccount(
            providerId = ID,
            uid = uid,
            displayName = "ChatGPT " + uid.takeLast(6),
            secret = secret,
        )
    }

    /** access_token 是 JWT，其中的 chatgpt_account_id 用于 `ChatGPT-Account-ID` 头。 */
    private fun accountIdOf(token: String): String? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val decoded = runCatching {
            String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val obj = runCatching { JsonParser.parseString(decoded).asJsonObject }.getOrNull()
            ?: return null
        return obj.get("https://api.openai.com/auth")
            ?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("chatgpt_account_id")?.takeIf { it.isJsonPrimitive }?.asString
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split('&').mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else runCatching {
                URLDecoder.decode(pair.substring(0, idx), "UTF-8") to
                    URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
            }.getOrNull()
        }.toMap()

    // ---------- 凭证刷新 ----------

    override fun refreshCredentials(account: ProviderAccount): ProviderAccount {
        val cred = credOf(account) ?: error("凭证缺失，请重新登录")
        if (cred.refreshToken.isEmpty()) error("refresh_token 为空，无法刷新")

        val form = buildString {
            append("grant_type=refresh_token")
            append("&client_id=").append(CLIENT_ID)
            append("&refresh_token=").append(URLEncoder.encode(cred.refreshToken, "UTF-8"))
        }
        val r = Http.postForm("$AUTH_HOST/oauth/token", mapOf("Accept" to "application/json"), form)
        if (!r.ok) {
            // invalid_grant = refresh_token 被吊销，只能重新登录
            if (r.body.contains("invalid_grant")) error("refresh_token 已被上游吊销，请重新登录")
            error("刷新失败（HTTP ${r.status}）：${r.body.take(200)}")
        }
        val json = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: error("刷新响应不是合法 JSON")
        val access = json.get("access_token")?.takeIf { it.isJsonPrimitive }?.asString
            ?: error("刷新响应里没有 access_token")
        val refresh = json.get("refresh_token")?.takeIf { it.isJsonPrimitive }?.asString
            ?: cred.refreshToken
        val accountId = accountIdOf(access) ?: cred.accountId
        val expiresIn = json.get("expires_in")?.takeIf { it.isJsonPrimitive }?.asLong ?: 3600L

        return account.copy(
            displayName = account.displayName,
            secret = JsonObject().apply {
                addProperty("accessToken", access)
                addProperty("refreshToken", refresh)
                addProperty("accountId", accountId)
                addProperty("expiresAt", System.currentTimeMillis() + expiresIn * 1000)
            }.toString(),
        )
    }

    // ---------- 对话 ----------

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        Result.success(MODELS.keys.toList())

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val cred = credOf(account) ?: return ChatResult.Failure(
            ApiError(ApiError.Kind.AUTH, 401, "凭证缺失，请重新登录 ChatGPT"),
        )
        val realModel = MODELS[request.model] ?: request.model
        val body = toCodexBody(request.body, realModel)

        val headers = mutableMapOf(
            "Authorization" to "Bearer ${cred.accessToken}",
            "originator" to "codex_cli_rs",
            "User-Agent" to USER_AGENT,
            "Accept" to "text/event-stream",
        )
        if (cred.accountId.isNotEmpty()) headers["ChatGPT-Account-ID"] = cred.accountId

        var events = 0
        val r = runCatching {
            Http.postStream(
                url = "$API_HOST/backend-api/codex/responses",
                headers = headers,
                json = body.toString(),
                timeoutMs = 180_000,
                onLine = { line ->
                    events++
                    sink?.onData(line)
                    true
                },
                onDone = { sink?.onDone() },
            )
        }.getOrElse { e ->
            return ChatResult.Failure(ApiError(ApiError.Kind.NETWORK, 0, "连接失败：${e.message}"))
        }

        if (!r.ok) {
            val kind = when {
                r.status == 401 || r.status == 403 -> ApiError.Kind.AUTH
                r.status in 400..499 -> ApiError.Kind.CLIENT
                else -> ApiError.Kind.UPSTREAM
            }
            Log.warn("ChatGPT 上游 ${r.status}：${r.body.take(200)}")
            return ChatResult.Failure(ApiError(kind, r.status, r.body.take(300)))
        }
        return ChatResult.Streamed(events)
    }

    /**
     * OpenAI `chat/completions` → Codex `responses`。
     *
     * 差异点：
     * 1. `messages` → `input[]`，每条 `{type:message, role, content:[片段]}`
     * 2. `system` → `developer`，文本片段叫 `input_text`
     * 3. assistant 的 `tool_calls` → `function_call`；`tool` 角色 → `function_call_output`
     * 4. 图片 `image_url` → `input_image`
     * 5. `store=false` 且必须请求 `reasoning.encrypted_content`
     */
    private fun toCodexBody(body: JsonObject, model: String): JsonObject {
        val input = JsonArray()
        val messages = body.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray

        messages?.forEach { el ->
            val msg = runCatching { el.asJsonObject }.getOrNull() ?: return@forEach
            val role = msg.get("role")?.takeIf { it.isJsonPrimitive }?.asString
                ?.lowercase() ?: return@forEach
            val content = msg.get("content")

            if (role == "assistant") {
                msg.get("tool_calls")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { callEl ->
                    val call = runCatching { callEl.asJsonObject }.getOrNull() ?: return@forEach
                    val fn = call.get("function")?.takeIf { it.isJsonObject }?.asJsonObject
                    input.add(JsonObject().apply {
                        addProperty("type", "function_call")
                        addProperty("call_id", call.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
                        addProperty("name", fn?.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
                        addProperty("arguments", fn?.get("arguments")?.takeIf { it.isJsonPrimitive }?.asString ?: "{}")
                    })
                }
            }

            if (role == "tool") {
                input.add(JsonObject().apply {
                    addProperty("type", "function_call_output")
                    addProperty("call_id", msg.get("tool_call_id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
                    addProperty("output", textOf(content))
                })
                return@forEach
            }

            val parts = JsonArray()
            if (content != null && content.isJsonArray) {
                content.asJsonArray.forEach { partEl ->
                    val part = runCatching { partEl.asJsonObject }.getOrNull() ?: return@forEach
                    when (part.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
                        "text" -> parts.add(JsonObject().apply {
                            addProperty("type", "input_text")
                            addProperty("text", part.get("text")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
                        })
                        "image_url" -> {
                            val url = part.get("image_url")?.let { iu ->
                                if (iu.isJsonObject) iu.asJsonObject.get("url")?.takeIf { it.isJsonPrimitive }?.asString
                                else iu.takeIf { it.isJsonPrimitive }?.asString
                            }
                            if (!url.isNullOrBlank()) parts.add(JsonObject().apply {
                                addProperty("type", "input_image")
                                addProperty("image_url", url)
                            })
                        }
                    }
                }
            } else {
                val text = textOf(content)
                if (text.isNotBlank()) parts.add(JsonObject().apply {
                    addProperty("type", "input_text")
                    addProperty("text", text)
                })
            }
            if (parts.size() == 0) return@forEach

            input.add(JsonObject().apply {
                addProperty("type", "message")
                addProperty("role", if (role == "system") "developer" else role)
                add("content", parts)
            })
        }

        return JsonObject().apply {
            addProperty("model", model)
            add("input", input)
            addProperty("stream", true)
            addProperty("store", false)
            addProperty("parallel_tool_calls", true)
            // Codex 要求带回思考过程的加密块，否则多轮丢上下文
            add("include", JsonArray().apply { add("reasoning.encrypted_content") })

            val effort = body.get("reasoning_effort")?.takeIf { it.isJsonPrimitive }?.asString
                ?: body.get("reasoning")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("effort")?.takeIf { it.isJsonPrimitive }?.asString
            add("reasoning", JsonObject().apply {
                addProperty("effort", effort ?: "medium")
                addProperty("summary", "auto")
            })

            body.get("tools")?.takeIf { it.isJsonArray }?.let { add("tools", it) }
            body.get("tool_choice")?.let { add("tool_choice", it) }
        }
    }

    /** 把 content（字符串或片段数组）压成纯文本。 */
    private fun textOf(content: JsonElement?): String {
        if (content == null) return ""
        if (content.isJsonPrimitive) return content.asString
        if (!content.isJsonArray) return ""
        return content.asJsonArray.mapNotNull { partEl ->
            runCatching { partEl.asJsonObject }.getOrNull()
                ?.get("text")?.takeIf { it.isJsonPrimitive }?.asString
        }.joinToString("")
    }

    // ---------- 凭证 ----------

    private data class Cred(
        val accessToken: String,
        val refreshToken: String,
        val accountId: String,
        val expiresAt: Long,
    )

    private fun credOf(account: ProviderAccount): Cred? {
        if (account.secret.isBlank()) return null
        val obj = runCatching { JsonParser.parseString(account.secret).asJsonObject }.getOrNull()
            ?: return null
        val access = obj.get("accessToken")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        if (access.isEmpty()) return null
        return Cred(
            accessToken = access,
            refreshToken = obj.get("refreshToken")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            accountId = obj.get("accountId")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            expiresAt = obj.get("expiresAt")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0,
        )
    }

    private fun authHeaders(cred: Cred): Map<String, String> {
        val headers = mutableMapOf(
            "Authorization" to "Bearer ${cred.accessToken}",
            "originator" to "codex_cli_rs",
            "User-Agent" to USER_AGENT,
        )
        if (cred.accountId.isNotEmpty()) headers["ChatGPT-Account-ID"] = cred.accountId
        return headers
    }

    // ---------- 账号运营 ----------

    /** Codex 后端没有签到概念，额度按订阅计划自动派发。 */
    override fun checkin(account: ProviderAccount): Result<CheckinResult> =
        Result.success(CheckinResult.Skipped)

    /**
     * 查 Codex 用量。
     *
     * 结构随上游版本变，取值全部宽松处理：拿不到就返回未知（remain=-1），不影响调用。
     */
    override fun balance(account: ProviderAccount): Result<BalanceInfo> {
        val cred = credOf(account) ?: return Result.failure(IllegalStateException("凭证缺失"))
        val r = runCatching { Http.get("$API_HOST/backend-api/wham/usage", authHeaders(cred)) }
            .getOrNull() ?: return Result.success(BalanceInfo(remain = -1.0))
        if (!r.ok) return Result.success(BalanceInfo(remain = -1.0))
        val root = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: return Result.success(BalanceInfo(remain = -1.0))
        val remain = root.get("rate_limit")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("remaining")?.takeIf { it.isJsonPrimitive }?.asDouble
            ?: root.get("remaining")?.takeIf { it.isJsonPrimitive }?.asDouble
            ?: return Result.success(BalanceInfo(remain = -1.0))
        return Result.success(BalanceInfo(remain = remain))
    }

    override fun fetchDisplayName(account: ProviderAccount): String? {
        val cred = credOf(account) ?: return null
        val r = runCatching { Http.get("$API_HOST/backend-api/me", authHeaders(cred)) }
            .getOrNull() ?: return null
        if (!r.ok) return null
        val root = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: return null
        return root.get("email")?.takeIf { it.isJsonPrimitive }?.asString
            ?: root.get("name")?.takeIf { it.isJsonPrimitive }?.asString
    }
}
