package dev.aigw.provider.jimeng

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
import dev.aigw.provider.WebTokenSupport
import dev.aigw.util.Http
import dev.aigw.util.Log
import java.security.MessageDigest
import kotlin.random.Random

/**
 * 即梦（jimeng.jianying.com）图片生成。
 *
 * 官方网页版走 `/mweb/v1/` 系列接口，鉴权只有两样东西：
 * 1. 登录 Cookie（sessionid 为主，ttwid / s_v_web_id 一起带上更稳）
 * 2. 每条请求一个 MD5 签名 `md5("9e2c|<uri末7位>|7|5.8.0|<毫秒时间戳>||11ac")`
 *
 * 图片这条链路不需要浏览器端 a_bogus 签名（视频 Seedance 才要），所以纯 HTTP 就能跑。
 *
 * 生成是异步的：先提交拿 history_record_id，再轮询 get_history_by_ids 直到出图。
 * 返回的 image_url 是临时的（几小时失效），调用方必须立刻下载转存。
 */
class JimengProvider : Provider, WebTokenSupport, AccountActivitySupport, DisplayNameSupport {

    override val id = ID
    override val displayName = "即梦"

    override val loginPageUrl = "https://jimeng.jianying.com/"

    // ---------- 常量 ----------

    companion object {
        const val ID = "jimeng"

        private const val HOST = "https://jimeng.jianying.com"
        const val AID = "513695"
        const val PLATFORM_CODE = "7"
        const val VERSION_CODE = "5.8.0"
        const val DRAFT_VERSION = "3.3.20"
        const val WEB_VERSION = "7.5.0"
        const val DRAFT_CONTENT_VERSION = "3.0.2"
        const val MIN_VERSION = "3.0.2"

        /** 固定的 19 位 webId：上游只当设备标识用，不需要跟账号绑定。 */
        val WEB_ID: String = (7_000_000_000_000_000_000L + Random.nextLong(999_999_999_999_999_999L)).toString()

        val IMAGE_MODELS = linkedMapOf(
            "jimeng-image-5.0-lite" to "high_aes_general_v50",
            "jimeng-image-5.0-pro" to "high_aes_general_v50p_large",
            "jimeng-image-4.7" to "high_aes_general_v43",
            "jimeng-image-4.6" to "high_aes_general_v42",
            "jimeng-image-4.5" to "high_aes_general_v40l",
            "jimeng-image-4.1" to "high_aes_general_v41",
            "jimeng-image-4.0" to "high_aes_general_v40",
            "jimeng-image-3.1" to "high_aes_general_v30l_art_fangzhou:general_v3.0_18b",
            "jimeng-image-3.0" to "high_aes_general_v30l:general_v3.0_18b",
            "jimeng-image-2.0-pro" to "high_aes_general_v20_L:general_v2.0_L",
        )

        /** 处理中状态。 */
        val PROCESSING = setOf(20, 42, 45)
        const val FAIL_STATE = 30
    }

    // ---------- 登录 ----------

    /** [token] 传完整 Cookie 串（WebView 里拿到的那份）。 */
    override fun loginWithToken(token: String): ProviderAccount {
        val cookie = token.trim()
        require(cookie.contains("sessionid=")) { "Cookie 里没有 sessionid，请确认已登录即梦" }
        val sid = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("sessionid=") }
            ?.substringAfter("sessionid=")
            ?.trim()
            .orEmpty()
        require(sid.isNotEmpty()) { "sessionid 为空" }

        val secret = JsonObject().apply { addProperty("cookie", cookie) }.toString()
        val uid = sid.take(16)
        // 昵称从上游取；拿不到时退回 sid 前缀，不影响登录
        val name = runCatching { fetchUserName(cookie) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: ("即梦 " + sid.take(6))
        return ProviderAccount(
            providerId = ID,
            uid = uid,
            displayName = name,
            secret = secret,
        )
    }

    override fun fetchDisplayName(account: ProviderAccount): String? {
        val cookie = cookieOf(account) ?: return null
        return fetchUserName(cookie)
    }

    /** 取当前登录账号的昵称（上游默认名形如「用户2498…」）。 */
    private fun fetchUserName(cookie: String): String? {
        val r = call("POST", "/mweb/v1/get_user_info", cookie, JsonObject())
        if (!r.ok) return null
        val root = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull() ?: return null
        return root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("name")?.takeIf { it.isJsonPrimitive }?.asString
    }

    // ---------- 模型 ----------

    override fun listModels(account: ProviderAccount): Result<List<String>> =
        Result.success(IMAGE_MODELS.keys.toList())

    // ---------- 签到 / 余额 ----------

    /** 即梦每日赠送 66 积分，没有手动签到。 */
    override fun checkin(account: ProviderAccount): Result<CheckinResult> =
        Result.success(CheckinResult.Skipped)

    override fun balance(account: ProviderAccount): Result<BalanceInfo> {
        val cookie = cookieOf(account) ?: return Result.failure(IllegalStateException("凭证缺失"))
        // 进详情页看到的就是最新额度：先试着领一次当日赠送，已领过时上游返回 0 无副作用
        runCatching { ensureCredit(cookie) }
        val r = runCatching {
            call("POST", "/commerce/v1/benefits/user_credit", cookie, JsonObject().apply {
                addProperty("aid", AID.toInt())
            })
        }.getOrElse { return Result.failure(it) }
        if (!r.ok) return Result.failure(IllegalStateException("HTTP ${r.status}"))
        val root = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
            ?: return Result.failure(IllegalStateException("返回非 JSON"))
        // 结构：data.credit.gift_credit + data.credit.purchase_credit（单位：个）
        val credit = root.getAsJsonObject("data")?.getAsJsonObject("credit")
        val gift = credit?.get("gift_credit")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
        val purchase = credit?.get("purchase_credit")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
        return Result.success(BalanceInfo(remain = gift + purchase))
    }

    // ---------- 对话（图片生成） ----------

    override fun chat(account: ProviderAccount, request: ChatRequest, sink: SseSink?): ChatResult {
        val cookie = cookieOf(account)
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.AUTH, 401, "即梦凭证缺失，请重新登录"))

        val modelKey = request.body.get("model")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        val alias = modelKey.substringAfterLast('/')
        val realModel = IMAGE_MODELS[alias]
            ?: return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "未知的即梦模型「$modelKey」"))

        val prompt = extractPrompt(request.body)
        if (prompt.isBlank()) {
            return ChatResult.Failure(ApiError(ApiError.Kind.CLIENT, 400, "messages 里没有可用文本"))
        }

        // 会话标题生成会复用当前模型，别让它触发一次真实的图片消耗。
        if (extractSystem(request.body).contains("title generator", ignoreCase = true)) {
            return plainReply(prompt.take(24), request.stream, sink)
        }

        // 每日赠送的 66 积分要主动领，不领会一直 1006（积分不足或没有相关权益）。
        runCatching { ensureCredit(cookie) }

        lastError = ""
        val historyId = submit(cookie, realModel, prompt)
            ?: return ChatResult.Failure(
                ApiError(ApiError.Kind.UPSTREAM, 502, "即梦提交失败：${lastError.ifBlank { "未知原因" }}"),
            )
        val urls = poll(cookie, historyId)
        if (urls.isEmpty()) {
            return ChatResult.Failure(
                ApiError(
                    ApiError.Kind.UPSTREAM, 502,
                    "即梦未返回图片（状态 $lastStatus，历史 $historyId）：$lastPollBody",
                ),
            )
        }

        // 即梦的直链带 x-expires 签名，几小时后失效。先落到网关本地，
        // 再把不会过期的本地地址交给客户端。
        val localUrls = urls.map { raw -> downloadToBlob(raw) ?: raw }
        val lines = StringBuilder("已生成 ${localUrls.size} 张图（$alias）：\n\n")
        localUrls.forEach { u ->
            lines.append("![图片](").append(u).append(")\n\n")
        }
        return plainReply(lines.toString(), request.stream, sink)
    }

    // ---------- 内部 ----------

    private fun cookieOf(account: ProviderAccount): String? {
        if (account.secret.isBlank()) return null
        val obj = runCatching { JsonParser.parseString(account.secret).asJsonObject }.getOrNull() ?: return null
        return obj.get("cookie")?.asString?.takeIf { it.isNotBlank() }
    }

    /** submit 的失败原因，供上层写成响应正文（core 的日志不进 logcat）。 */
    private var lastError: String = ""

    /** 轮询最后一次看到的状态码，用于把「为什么没出图」说清楚。 */
    private var lastStatus: Int = -1

    /** 轮询最后一次响应原文，排查结构变化用。 */
    private var lastPollBody: String = ""

    /**
     * 余额为 0 时领取当日赠送积分。
     *
     * 即梦不会自动把每日 66 积分记到账上，必须打一次 `credit_receive`；
     * 已经领过时上游返回 receive_quota=0，重复调用无副作用。
     */
    private fun ensureCredit(cookie: String) {
        val creditBody = JsonObject()
        val credit = runCatching { call("POST", "/commerce/v1/benefits/user_credit", cookie, creditBody) }
            .getOrNull() ?: return
        if (!credit.ok) return
        val root = runCatching { JsonParser.parseString(credit.body).asJsonObject }.getOrNull() ?: return
        val c = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("credit")?.takeIf { it.isJsonObject }?.asJsonObject ?: return
        val gift = c.get("gift_credit")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
        val purchase = c.get("purchase_credit")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
        val vip = c.get("vip_credit")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 0.0
        if (gift + purchase + vip > 0) return

        val body = JsonObject().apply { addProperty("time_zone", "Asia/Shanghai") }
        val r = runCatching { call("POST", "/commerce/v1/benefits/credit_receive", cookie, body) }.getOrNull()
            ?: return
        Log.debug("即梦领取积分：HTTP ${r.status} ${r.body.take(120)}")
    }

    /**
     * 下载即梦返回的图片并存入网关本地缓存，返回不过期的网关地址。
     *
     * 走 URLConnection 而不是 Http 封装：这里要的是原始字节，不是文本。
     */
    private fun downloadToBlob(url: String): String? {
        return runCatching {
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
            try {
                if (conn.responseCode !in 200..299) {
                    lastError = "图片下载 ${conn.responseCode}"
                    return null
                }
                val bytes = conn.inputStream.readBytes()
                if (bytes.isEmpty()) return null
                if (bytes.size > 20 * 1024 * 1024) return null
                val ext = when {
                    bytes.size > 12 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
                    bytes.size > 12 && bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() -> "webp"
                    else -> "jpg"
                }
                val id = dev.aigw.gateway.MediaProxy.store(bytes, ext) ?: return null
                dev.aigw.gateway.MediaProxy.blobUrl(id)
            } finally {
                runCatching { conn.disconnect() }
            }
        }.getOrNull()
    }

    /** 提交生成任务，返回 history_record_id。 */
    private fun submit(cookie: String, model: String, prompt: String): String? {
        // 高分辨率模型（4.x/5.x）才吃 2k，其余走 1k
        val highRes = model.startsWith("high_aes_general_v4") || model.startsWith("high_aes_general_v5")
        val resolutionType = if (highRes) "2k" else "1k"
        val side = if (highRes) 2048 else 1328
        // 结构按上游 `/mweb/v1/aigc_draft/generate` 走：core_param 嵌在 abilities.generate 里，
        // 平铺到 abilities 下会返回 ret=1002（common error）。
        val coreParam = JsonObject().apply {
            addProperty("type", "")
            addProperty("id", uuid())
            addProperty("model", model)
            addProperty("prompt", prompt)
            addProperty("negative_prompt", "")
            addProperty("seed", 2_500_000_000L + Random.nextLong(100_000_000L))
            addProperty("sample_strength", 0.5)
            // 1:1 对应 8，其他比例见表；先把 1:1 跑通
            addProperty("image_ratio", 8)
            add("large_image_info", JsonObject().apply {
                addProperty("type", "")
                addProperty("id", uuid())
                // 2.0 / 3.x 只支持 1k（1328×1328），硬写 2k 会被上游静默丢弃任务
                addProperty("height", side)
                addProperty("width", side)
                addProperty("resolution_type", resolutionType)
            })
            addProperty("generate_type", 0)
        }

        val abilities = JsonObject().apply {
            addProperty("type", "")
            addProperty("id", uuid())
            add("generate", JsonObject().apply {
                addProperty("type", "")
                addProperty("id", uuid())
                add("core_param", coreParam)
                add("history_option", JsonObject().apply {
                    addProperty("type", "")
                    addProperty("id", uuid())
                })
            })
            add("gen_option", JsonObject().apply {
                addProperty("type", "")
                addProperty("id", uuid())
                addProperty("gen_count", 1)
                addProperty("generate_all", false)
            })
        }

        val componentId = uuid()
        val draftContent = JsonObject().apply {
            addProperty("type", "draft")
            addProperty("id", uuid())
            addProperty("min_version", MIN_VERSION)
            add("min_features", com.google.gson.JsonArray())
            addProperty("is_from_tsn", true)
            addProperty("version", DRAFT_CONTENT_VERSION)
            addProperty("main_component_id", componentId)
            add("component_list", com.google.gson.JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "image_base_component")
                    addProperty("id", componentId)
                    addProperty("min_version", MIN_VERSION)
                    add("metadata", JsonObject().apply {
                        addProperty("type", "")
                        addProperty("id", uuid())
                        addProperty("created_platform", 3)
                        addProperty("created_platform_version", "")
                        addProperty("created_time_in_ms", System.currentTimeMillis().toString())
                        addProperty("created_did", "")
                    })
                    addProperty("generate_type", "generate")
                    addProperty("aigc_mode", "workbench")
                    addProperty("gen_type", 1)
                    // abilities 是对象，不是数组：写成数组上游直接 1002
                    add("abilities", abilities)
                })
            })
        }

        // submit_id 与 metrics_extra.generateId 必须是同一个值，否则上游当脏请求拒掉。
        val submitId = uuid()
        val sceneOptions = com.google.gson.JsonArray().apply {
            add(JsonObject().apply {
                addProperty("type", "image")
                addProperty("scene", "ImageBasicGenerate")
                addProperty("modelReqKey", model)
                addProperty("resolutionType", resolutionType)
                add("abilityList", com.google.gson.JsonArray())
                addProperty("benefitCount", 1)
                add("reportParams", JsonObject().apply {
                    addProperty("enterSource", "generate")
                    addProperty("vipSource", "generate")
                    addProperty("extraVipFunctionKey", "$model-$resolutionType")
                    addProperty("useVipFunctionDetailsReporterHoc", true)
                })
            })
        }
        val metricsExtra = JsonObject().apply {
            addProperty("promptSource", "custom")
            addProperty("generateCount", 1)
            addProperty("enterFrom", "click")
            // 上游这里要的是「JSON 字符串里的 JSON 字符串」，不能直接塞对象
            addProperty("sceneOptions", sceneOptions.toString())
            addProperty("isBoxSelect", false)
            addProperty("isCutout", false)
            addProperty("generateId", submitId)
            addProperty("isRegenerate", false)
        }

        val body = JsonObject().apply {
            add("extend", JsonObject().apply { addProperty("root_model", model) })
            addProperty("submit_id", submitId)
            addProperty("metrics_extra", metricsExtra.toString())
            addProperty("draft_content", draftContent.toString())
            add("http_common_info", JsonObject().apply { addProperty("aid", AID.toInt()) })
        }

        val uri = "/mweb/v1/aigc_draft/generate?da_version=$DRAFT_VERSION" +
            "&web_component_open_flag=1&web_version=$WEB_VERSION"
        val r = runCatching { call("POST", uri, cookie, body) }.getOrElse {
            lastError = "提交异常：${it.message}"
            return null
        }
        if (!r.ok) {
            lastError = "提交 HTTP ${r.status}：${r.body.take(200)}"
            return null
        }
        val root = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
        if (root == null) {
            lastError = "提交返回非 JSON：${r.body.take(200)}"
            return null
        }
        val ret = root.get("ret")?.takeIf { it.isJsonPrimitive }?.asString
        if (ret != null && ret != "0") lastError = "上游 ret=$ret：${r.body.take(220)}"
        val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
        val aigc = data?.get("aigc_data")?.takeIf { it.isJsonObject }?.asJsonObject
        val id = aigc?.get("history_record_id")?.takeIf { it.isJsonPrimitive }?.asString
        if (id.isNullOrBlank()) {
            if (lastError.isBlank()) lastError = "无 history_record_id：${r.body.take(240)}"
            return null
        }
        return id
    }

    /** 轮询直到出图；返回图片直链。 */
    private fun poll(cookie: String, historyId: String): List<String> {
        val seenStatus = mutableListOf<Int>()
        lastStatus = -1
        repeat(90) {
            Thread.sleep(1_200)
            val body = JsonObject().apply {
                add("history_ids", com.google.gson.JsonArray().apply { add(historyId) })
            }
            val r = runCatching { call("POST", "/mweb/v1/get_history_by_ids", cookie, body) }.getOrNull()
                ?: return@repeat
            lastPollBody = r.body.take(1200)
            if (!r.ok) return@repeat
            val root = runCatching { JsonParser.parseString(r.body).asJsonObject }.getOrNull()
                ?: return@repeat
            // get_history_by_ids 的 data 是「以 history_id 为键的对象」，不是数组
            val data = root.get("data")?.takeIf { it.isJsonObject }?.asJsonObject ?: return@repeat
            val entry = data.entrySet().firstOrNull()?.value?.takeIf { it.isJsonObject }?.asJsonObject
                ?: return@repeat

            val item = entry
            val status = item.get("status")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
            if (status !in seenStatus) {
                seenStatus.add(status)
                Log.debug("即梦轮询状态: $status")
                lastStatus = status
            }
            if (status == FAIL_STATE) {
                val code = item.get("fail_code")?.takeIf { it.isJsonPrimitive }?.asString
                Log.warn("即梦生成失败 fail_code=$code")
                return emptyList()
            }

            val urls = mutableListOf<String>()
            val images = item.get("image")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("large_images")?.takeIf { it.isJsonArray }?.asJsonArray
            if (images != null) {
                for (el in images) {
                    val u = runCatching { el.asJsonObject }
                        .getOrNull()?.get("image_url")?.takeIf { it.isJsonPrimitive }?.asString
                    if (!u.isNullOrBlank()) urls.add(u)
                }
            }
            if (urls.isNotEmpty()) return urls
            val list = item.get("item_list")?.takeIf { it.isJsonArray }?.asJsonArray
            if (list != null) {
                for (el in list) {
                    val u = runCatching { el.asJsonObject }
                        .getOrNull()?.get("common_attr")?.takeIf { it.isJsonObject }?.asJsonObject
                        ?.get("cover_url")?.takeIf { it.isJsonPrimitive }?.asString
                    if (!u.isNullOrBlank()) urls.add(u)
                }
            }
            if (urls.isNotEmpty()) return urls
        }
        return emptyList()
    }

    /** 带签名的通用请求。 */
    private fun call(method: String, uri: String, cookie: String, body: JsonObject): Http.Result {
        val deviceTime = System.currentTimeMillis().toString()
        // 签名只取纯路径的末 7 位：把 query 也算进去会让 md5 完全对不上。
        val pathOnly = uri.substringBefore('?')
        val sign = md5("9e2c|${pathOnly.takeLast(7)}|$PLATFORM_CODE|$VERSION_CODE|$deviceTime||11ac")
        // webId 必须与 Cookie 里的 _tea_web_id 一致，否则上游按脏请求返回 ret=1002。
        val webId = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("_tea_web_id=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: WEB_ID
        val params = if (uri.contains("?")) "&" else "?"
        val url = "$HOST$uri${params}aid=$AID&device_platform=web&region=CN&webId=$webId"
        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Cookie" to cookie,
            "Device-Time" to deviceTime,
            "Sign" to sign,
            "Sign-Ver" to "1",
            "Accept" to "application/json, text/plain, */*",
            "Origin" to HOST,
            "Referer" to "$HOST/ai-tool/home",
            "Appid" to AID,
            "Appvr" to VERSION_CODE,
            "Pf" to PLATFORM_CODE,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        )
        return if (method == "POST") Http.postJson(url, headers, body.toString())
        else Http.get(url, headers)
    }

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun uuid(): String = java.util.UUID.randomUUID().toString()

    private fun extractPrompt(body: JsonObject): String {
        val messages = runCatching { body.getAsJsonArray("messages") }.getOrNull() ?: return ""
        var last = ""
        for (el in messages) {
            val o = runCatching { el.asJsonObject }.getOrNull() ?: continue
            if (runCatching { o.get("role")?.asString }.getOrNull() != "user") continue
            val c = o.get("content") ?: continue
            last = when {
                c.isJsonPrimitive -> c.asString
                c.isJsonArray -> buildString {
                    for (part in c.asJsonArray) {
                        val po = runCatching { part.asJsonObject }.getOrNull() ?: continue
                        val t = runCatching { po.get("text")?.asString }.getOrNull()
                        if (!t.isNullOrBlank()) append(t)
                    }
                }
                else -> ""
            }
        }
        return last
    }

    private fun extractSystem(body: JsonObject): String {
        val messages = runCatching { body.getAsJsonArray("messages") }.getOrNull() ?: return ""
        val sb = StringBuilder()
        for (el in messages) {
            val o = runCatching { el.asJsonObject }.getOrNull() ?: continue
            if (runCatching { o.get("role")?.asString }.getOrNull() != "system") continue
            o.get("content")?.takeIf { it.isJsonPrimitive }?.let { sb.append(it.asString).append('\n') }
        }
        return sb.toString()
    }

    private fun plainReply(text: String, stream: Boolean, sink: SseSink?): ChatResult {
        val id = "jimeng-${System.nanoTime()}"
        val json = com.google.gson.JsonPrimitive(text).toString()
        if (stream && sink != null) {
            sink.onData(
                """{"id":"$id","object":"chat.completion.chunk","created":${System.currentTimeMillis() / 1000},""" +
                    """"choices":[{"index":0,"delta":{"role":"assistant","content":$json},"finish_reason":null}]}""",
            )
            sink.onData(
                """{"id":"$id","object":"chat.completion.chunk","created":${System.currentTimeMillis() / 1000},""" +
                    """"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
            )
            sink.onData("[DONE]")
            sink.onDone()
            return ChatResult.Streamed(2)
        }
        return ChatResult.Aggregated(
            JsonParser.parseString(
                """{"id":"$id","object":"chat.completion","choices":[{"index":0,""" +
                    """"message":{"role":"assistant","content":$json},"finish_reason":"stop"}],""" +
                    """"usage":{"prompt_tokens":0,"completion_tokens":0,"total_tokens":0}}""",
            ).asJsonObject,
        )
    }
}
