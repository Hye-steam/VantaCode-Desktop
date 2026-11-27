package dev.aigw.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.desktop.cef.ChatglmWebTransport
import dev.aigw.desktop.cef.DeepSeekWebTransport
import dev.aigw.desktop.cef.LoginHooks
import dev.aigw.desktop.cef.LoginWindow
import dev.aigw.desktop.cef.MuseWebTransport
import dev.aigw.desktop.update.UpdateChecker
import dev.aigw.gateway.Engine
import dev.aigw.gateway.GatewayHttpServer
import dev.aigw.gateway.ProxySettings
import dev.aigw.gateway.ProxyType
import dev.aigw.gateway.Settings
import dev.aigw.provider.CheckinResult
import dev.aigw.provider.DeviceAuthPoll
import dev.aigw.provider.ProviderAccount
import dev.aigw.desktop.util.LanAddresses
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.URI
import java.security.SecureRandom

// ---------------------------------------------------------------- UI 数据

data class AccountUi(
    val providerId: String,
    val uid: String,
    val displayName: String,
    val balance: Double?,
    val enabled: Boolean,
    val lastError: String?,
    val checkedInToday: Boolean,
    val inCooldown: Boolean,
    val enterprise: Boolean,
    val disabledReason: String?,
    val region: String?,
)

data class ModelUi(
    val id: String,
    val providerId: String,
    val displayName: String,
    val type: String,
    val contextWindow: Long,
    val maxOutputTokens: Long,
    val tags: List<String>,
    val region: String?,
)

data class EventUi(val time: Long, val level: String, val tag: String, val message: String)

data class CallUi(
    val time: Long,
    val model: String,
    val via: String,
    val ok: Boolean,
    val latencyMs: Long,
    val detail: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val cachedTokens: Int,
)

data class ProxyUi(
    val enabled: Boolean = false,
    val type: ProxyType = ProxyType.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val username: String = "",
    val password: String = "",
)

data class WbLoginState(
    val active: Boolean = false,
    val region: String = "cn",
    val url: String = "",
    val state: String = "",
    val status: String = "",
    val error: String? = null,
)

data class SmsLoginState(
    val active: Boolean = false,
    val phone: String = "",
    val ccode: String = "86",
    val msgid: String = "",
    val status: String = "",
    val error: String? = null,
)

data class AnnouncementUi(val title: String, val content: String)

data class UpdateUi(val version: String, val url: String, val force: Boolean, val notes: String)

data class UiState(
    val running: Boolean = false,
    val startedAt: Long = 0,
    val port: Int = 8790,
    val accounts: List<AccountUi> = emptyList(),
    val accountEnabled: Int = 0,
    val accountTotal: Int = 0,
    val totalCredits: Double = 0.0,
    val creditsKnown: Int = 0,
    val todayRequests: Int = 0,
    val todaySuccess: Int = 0,
    val todayFail: Int = 0,
    val avgLatencyMs: Long = 0,
    val todayTokens: Long = 0,
    val todayInputTokens: Long = 0,
    val todayOutputTokens: Long = 0,
    val todayCachedTokens: Long = 0,
    val models: List<ModelUi> = emptyList(),
    val loadingModels: Boolean = false,
    val probingModels: Pair<Int, Int>? = null,
    val probeResults: Map<String, Engine.ProbeResult> = emptyMap(),
    val providerEnabled: Map<String, Boolean> = emptyMap(),
    val regionPrefs: Map<String, String> = emptyMap(),
    val monitorBubble: Boolean = false,
    val systemPrompt: String = "",
    val modelAliases: Map<String, String> = emptyMap(),
    val stripThinkTags: Boolean = false,
    val accountStrategy: String = "ORDER",
    val bubbleOpacity: Float = 1f,
    val events: List<EventUi> = emptyList(),
    val calls: List<CallUi> = emptyList(),
    val apiKey: String = "",
    val allowNoKey: Boolean = false,
    val bindLan: Boolean = false,
    val balanceRefreshSeconds: Int = 300,
    val maxConcurrentPerAccount: Int = 1,
    val maxConcurrentGlobal: Int = 0,
    val failThreshold: Int = 3,
    val cooldownSeconds: Int = 300,
    val modelCooldownSeconds: Int = 600,
    val sessionSticky: Boolean = false,
    val lanAddresses: List<String> = emptyList(),
    val startError: String? = null,
    val proxy: ProxyUi = ProxyUi(),
    val wbLogin: WbLoginState = WbLoginState(),
    val loomyLogin: SmsLoginState = SmsLoginState(),
    val chatglmLogin: SmsLoginState = SmsLoginState(),
    val message: String? = null,
    val themeName: String = "默认",
    val announcement: AnnouncementUi? = null,
    val updateInfo: UpdateUi? = null,
)

/**
 * 桌面端状态机：Android AppViewModel（AndroidViewModel + viewModelScope）的
 * 平替——普通类 + kotlinx-coroutines，状态直接来自 Engine。
 *
 * 网页登录统一走 [LoginWindow]（JCEF）；登录成功后账号已由 Engine 写进池，
 * 这里只刷新状态。远程公告/强制更新在 `:core` 已被上游禁用
 * （Engine.pollAnnouncementAndUpdate 是空操作、checkUpdate 返回 null），
 * 更新只走 GitHub UpdateChecker。
 */
class GateViewModel(
    val engine: Engine,
    val server: GatewayHttpServer,
    val dataDir: File,
) {
    var state by mutableStateOf(UiState())
        private set

    val version = Main.VERSION

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loginJob: Job? = null
    private var collectJob: Job? = null
    private var refreshJob: Job? = null
    private var probeJob: Job? = null

    val deepSeekTransport = DeepSeekWebTransport()
    val chatglmTransport = ChatglmWebTransport()
    val museTransport = MuseWebTransport()

    init {
        engine.deepSeekWebTransport = deepSeekTransport
        engine.chatglmWebTransport = chatglmTransport
        engine.museWebTransport = museTransport
        refreshAll()
    }

    // ------------------------------------------------------------ 基础刷新

    fun notify(message: String) {
        state = state.copy(message = message)
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    fun theme() = dev.aigw.desktop.ui.themeByName(state.themeName)

    fun refreshAll() {
        val entries = engine.pool.all()
        val accounts = entries.map {
            AccountUi(
                providerId = it.account.providerId,
                uid = it.account.uid,
                displayName = it.account.displayName,
                balance = it.state.balance,
                enabled = it.state.enabled,
                lastError = it.state.lastError,
                checkedInToday = it.state.checkedInToday(),
                inCooldown = it.state.inCooldown(),
                enterprise = it.state.enterprise,
                disabledReason = it.state.disabledReason,
                region = engine.regionOf(it.account),
            )
        }
        val s = engine.settings
        val stats = engine.callLog.todayStats()
        val balances = entries.filter { !it.state.enterprise }.mapNotNull { it.state.balance }
        val proxy = engine.proxySettings
        state = state.copy(
            running = server.isRunning(),
            startedAt = server.startedAt,
            port = s.port,
            accounts = accounts,
            accountEnabled = entries.count { it.state.enabled },
            accountTotal = entries.size,
            totalCredits = balances.sum(),
            creditsKnown = balances.size,
            todayRequests = stats.total,
            todaySuccess = stats.success,
            todayFail = stats.fail,
            avgLatencyMs = stats.avgLatencyMs,
            todayTokens = stats.totalTokens,
            todayInputTokens = stats.inputTokens,
            todayOutputTokens = stats.outputTokens,
            todayCachedTokens = stats.cachedTokens,
            providerEnabled = engine.providerIds().associateWith { engine.isProviderEnabled(it) },
            regionPrefs = engine.providerIds().mapNotNull { id ->
                engine.regionPref(id)?.let { id to it }
            }.toMap(),
            monitorBubble = s.monitorBubble,
            systemPrompt = s.systemPrompt,
            modelAliases = s.modelAliases.orEmpty(),
            stripThinkTags = s.stripThinkTags,
            accountStrategy = s.accountStrategy,
            bubbleOpacity = s.bubbleOpacity,
            events = engine.eventLog.snapshot().map { EventUi(it.time, it.level, it.tag, it.message) },
            calls = engine.callLog.recent(200).map {
                CallUi(it.time, it.model, it.via, it.ok, it.latencyMs, it.detail, it.inputTokens, it.outputTokens, it.cachedTokens)
            },
            apiKey = s.apiKey,
            allowNoKey = s.allowNoKey,
            bindLan = s.bindLan,
            balanceRefreshSeconds = s.balanceRefreshSeconds,
            maxConcurrentPerAccount = s.maxConcurrentPerAccount,
            maxConcurrentGlobal = s.maxConcurrentGlobal,
            failThreshold = s.failThreshold,
            cooldownSeconds = s.cooldownSeconds,
            modelCooldownSeconds = s.modelCooldownSeconds,
            sessionSticky = s.sessionSticky,
            lanAddresses = LanAddresses.current(),
            proxy = ProxyUi(proxy.enabled, proxy.type, proxy.host, proxy.port, proxy.username, proxy.password),
            themeName = s.themeName,
        )
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            while (true) {
                delay(2000)
                refreshAll()
            }
        }
    }

    // ------------------------------------------------------------ 网关启停

    fun startGateway() {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                runCatching {
                    engine.seedFreePoolIfNeeded()
                    engine.eventLog.beginSession()
                    server.start()
                }.exceptionOrNull()
            }
            if (error != null) {
                state = state.copy(startError = error.message)
                notify("网关启动失败：${error.message}")
            } else {
                state = state.copy(startError = null)
                refreshAll()
            }
        }
    }

    fun stopGateway() {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { server.stop() } }
            refreshAll()
        }
    }

    fun generateApiKey() {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        val key = "sk-" + (1..32).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        engine.settings.apiKey = key
        Settings.save(dataDir, engine.settings)
        refreshAll()
        copyToClipboard(key)
        notify("新 API Key 已生成并复制")
    }

    fun setNoKeyEnabled(enabled: Boolean) {
        engine.settings.allowNoKey = enabled
        Settings.save(dataDir, engine.settings)
        refreshAll()
    }

    fun setBindLan(enabled: Boolean) {
        engine.settings.bindLan = enabled
        Settings.save(dataDir, engine.settings)
        refreshAll()
        if (server.isRunning()) {
            stopGateway()
            startGateway()
        }
    }

    // ------------------------------------------------------------ 账号管理

    fun removeAccount(providerId: String, uid: String) {
        engine.pool.remove(providerId, uid)
        refreshAll()
        notify("账号已删除")
    }

    fun toggleAccount(providerId: String, uid: String, enabled: Boolean) {
        if (enabled) engine.pool.setEnabled(providerId, uid) else engine.pool.setDisabled(providerId, uid, "手动停用")
        refreshAll()
    }

    fun setProviderEnabled(providerId: String, enabled: Boolean) {
        engine.setProviderEnabled(providerId, enabled)
        refreshAll()
    }

    fun setRegionPref(providerId: String, region: String?) {
        engine.setRegionPref(providerId, region)
        refreshAll()
    }

    fun checkinAccount(providerId: String, uid: String) {
        scope.launch {
            val account = withContext(Dispatchers.IO) { engine.pool.get(providerId, uid)?.account } ?: return@launch
            val result = withContext(Dispatchers.IO) { engine.checkin(account) }
            result.onSuccess {
                val text = when (it) {
                    is CheckinResult.Done -> it.message
                    CheckinResult.Skipped -> "该账号不适用签到，已跳过"
                }
                notify("签到：$text")
            }.onFailure { notify("签到失败：${it.message}") }
            refreshAll()
        }
    }

    fun checkinAll(providerId: String? = null) {
        scope.launch {
            val targets = withContext(Dispatchers.IO) {
                engine.pool.all().filter { providerId == null || it.account.providerId == providerId }
            }
            var ok = 0
            var fail = 0
            for (entry in targets) {
                val r = withContext(Dispatchers.IO) { engine.checkin(entry.account) }
                if (r.isSuccess) ok++ else fail++
            }
            notify("签到完成：成功 $ok，失败 $fail")
            refreshAll()
        }
    }

    fun refreshAccountBalance(providerId: String, uid: String) {
        scope.launch {
            val account = withContext(Dispatchers.IO) { engine.pool.get(providerId, uid)?.account } ?: return@launch
            val result = withContext(Dispatchers.IO) { engine.refreshBalance(account) }
            result.onSuccess { notify("余额：${it.remain}") }.onFailure { notify("刷新余额失败：${it.message}") }
            refreshAll()
        }
    }

    fun refreshCredits(providerId: String? = null) {
        scope.launch {
            val targets = withContext(Dispatchers.IO) {
                engine.pool.all().filter { providerId == null || it.account.providerId == providerId }
            }
            for (entry in targets) {
                withContext(Dispatchers.IO) { runCatching { engine.refreshBalance(entry.account) } }
            }
            notify("余额已刷新")
            refreshAll()
        }
    }

    fun refreshDisplayNames(providerId: String) {
        scope.launch {
            val targets = withContext(Dispatchers.IO) { engine.pool.accounts(providerId) }
            for (entry in targets) {
                withContext(Dispatchers.IO) { runCatching { engine.refreshDisplayName(entry.account) } }
            }
            refreshAll()
        }
    }

    fun reloginAll(providerId: String? = null) {
        scope.launch {
            val targets = withContext(Dispatchers.IO) {
                engine.pool.all().filter { providerId == null || it.account.providerId == providerId }
            }
            var ok = 0
            var fail = 0
            for (entry in targets) {
                val r = withContext(Dispatchers.IO) { engine.refreshLogin(entry.account) }
                if (r.isSuccess) ok++ else fail++
            }
            notify("重新登录完成：成功 $ok，失败 $fail")
            refreshAll()
        }
    }

    fun performProviderAction(providerId: String, uid: String, action: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { engine.performAction(providerId, uid, action) }
            notify(if (result.ok) "操作成功：${result.message}" else "操作失败：${result.message}")
            refreshAll()
        }
    }

    // ------------------------------------------------------------ 模型

    fun refreshModels() {
        scope.launch {
            state = state.copy(loadingModels = true)
            val obj = withContext(Dispatchers.IO) { runCatching { engine.listModels(true) }.getOrNull() }
            val models = mutableListOf<ModelUi>()
            obj?.getAsJsonArray("data")?.forEach { el ->
                val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val id = o.get("id")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (id.isBlank()) return@forEach
                val providerId = o.get("owned_by")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                val tags = o.getAsJsonArray("tags")?.mapNotNull { it.takeIf { t -> t.isJsonPrimitive }?.asString } ?: emptyList()
                models += ModelUi(
                    id = id,
                    providerId = providerId,
                    displayName = o.get("display_name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    type = o.get("type")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
                    contextWindow = o.get("context_length")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0,
                    maxOutputTokens = o.get("max_output_tokens")?.takeIf { it.isJsonPrimitive }?.asLong ?: 0,
                    tags = tags,
                    region = o.get("region")?.takeIf { it.isJsonPrimitive }?.asString,
                )
            }
            state = state.copy(models = models, loadingModels = false)
            refreshAll()
            state = state.copy(models = models, loadingModels = false)
        }
    }

    fun probeModels() {
        val models = state.models
        if (models.isEmpty()) {
            notify("请先刷新模型列表")
            return
        }
        probeJob?.cancel()
        probeJob = scope.launch {
            val results = mutableMapOf<String, Engine.ProbeResult>()
            state = state.copy(probingModels = 0 to models.size)
            for ((index, model) in models.withIndex()) {
                val result = withContext(Dispatchers.IO) { engine.probeModel(model.id) }
                results[model.id] = result
                state = state.copy(probingModels = index + 1 to models.size, probeResults = results.toMap())
            }
            state = state.copy(probingModels = null, probeResults = results.toMap())
            notify("模型探测完成")
        }
    }

    fun probeSingle(model: ModelUi) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { engine.probeModel(model.id) }
            state = state.copy(probeResults = state.probeResults + (model.id to result))
            notify("${model.id}：${if (result.ok) "可用" else result.message}")
        }
    }

    // ------------------------------------------------------------ 设置

    fun saveSettings(
        port: Int,
        apiKey: String,
        allowNoKey: Boolean,
        bindLan: Boolean,
        balanceRefreshSeconds: Int,
        maxConcurrentPerAccount: Int,
        maxConcurrentGlobal: Int,
        failThreshold: Int,
        cooldownSeconds: Int,
        modelCooldownSeconds: Int,
        sessionSticky: Boolean,
    ) {
        val s = engine.settings
        val portChanged = port != s.port || bindLan != s.bindLan
        s.port = port
        s.apiKey = apiKey.ifBlank { s.apiKey }
        s.allowNoKey = allowNoKey
        s.bindLan = bindLan
        s.balanceRefreshSeconds = balanceRefreshSeconds
        s.maxConcurrentPerAccount = maxConcurrentPerAccount
        s.maxConcurrentGlobal = maxConcurrentGlobal
        s.failThreshold = failThreshold
        s.cooldownSeconds = cooldownSeconds
        s.modelCooldownSeconds = modelCooldownSeconds
        s.sessionSticky = sessionSticky
        Settings.save(dataDir, s)
        refreshAll()
        notify("设置已保存")
        if (portChanged && server.isRunning()) {
            stopGateway()
            startGateway()
        }
    }

    fun saveProxy(enabled: Boolean, type: ProxyType, host: String, port: String, user: String, pass: String): String? {
        if (enabled) {
            if (host.isBlank()) return "代理主机不能为空"
            val p = port.toIntOrNull() ?: return "代理端口无效"
            if (p !in 1..65535) return "代理端口无效"
        }
        val settings = ProxySettings(
            enabled = enabled,
            type = type,
            host = host,
            port = port.toIntOrNull() ?: 8080,
            username = user,
            password = pass,
        )
        engine.updateProxySettings(settings)
        ProxySettings.save(dataDir, settings)
        refreshAll()
        notify("代理设置已保存")
        return null
    }

    fun setSystemPrompt(text: String) {
        engine.settings.systemPrompt = text
        Settings.save(dataDir, engine.settings)
        refreshAll()
    }

    fun setRewriteRules(aliasesText: String, stripThink: Boolean) {
        val map = mutableMapOf<String, String>()
        aliasesText.lines().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val i = t.indexOf('=')
            if (i > 0) map[t.substring(0, i).trim()] = t.substring(i + 1).trim()
        }
        engine.settings.modelAliases = map
        engine.settings.stripThinkTags = stripThink
        Settings.save(dataDir, engine.settings)
        refreshAll()
        notify("改写规则已保存")
    }

    fun setAccountStrategy(strategy: String) {
        engine.settings.accountStrategy = strategy
        Settings.save(dataDir, engine.settings)
        refreshAll()
    }

    fun setMonitorBubble(enabled: Boolean) {
        engine.settings.monitorBubble = enabled
        Settings.save(dataDir, engine.settings)
        refreshAll()
    }

    fun setBubbleOpacity(opacity: Float) {
        engine.settings.bubbleOpacity = opacity.coerceIn(0.3f, 1f)
        Settings.save(dataDir, engine.settings)
        refreshAll()
    }

    fun setTheme(name: String) {
        engine.settings.themeName = name
        Settings.save(dataDir, engine.settings)
        refreshAll()
    }

    // ------------------------------------------------------------ 反馈 / 日志

    fun submitTicket(content: String, contactType: String, contact: String, imagesBase64: List<String>) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                engine.submitTicket(content, contactType, contact, imagesBase64, version)
            }
            notify(if (result.ok) "工单已提交：${result.message}" else "工单提交失败：${result.message}")
            refreshAll()
        }
    }

    fun clearLogs() {
        engine.eventLog.clear()
        engine.callLog.clear()
        refreshAll()
    }

    fun exportLogsText(): String = engine.eventLog.exportText()

    // ------------------------------------------------------------ 更新检查

    fun checkUpdate() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateChecker.check(version) }
            result.onSuccess { rel ->
                if (rel == null) {
                    notify("已是最新版本（$version）")
                } else {
                    state = state.copy(updateInfo = UpdateUi(rel.version, rel.exeUrl ?: rel.pageUrl, force = false, notes = rel.notes))
                }
            }.onFailure {
                notify("检查更新失败：${it.message}")
            }
        }
    }

    fun consumeUpdate() {
        state = state.copy(updateInfo = null)
    }

    fun consumeAnnouncement() {
        state = state.copy(announcement = null)
    }

    // ------------------------------------------------------------ 导入 / 导出

    fun exportAccountEnvelope(providerId: String? = null): String =
        runCatching { engine.exportAccountTransfer(providerId) }.getOrDefault("")

    fun importAccountEnvelope(raw: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.importAccountTransfer(raw) }
            }
            result.onSuccess {
                notify("信封导入：新增 ${it.added.size}、覆盖 ${it.updated.size}、跳过 ${it.skipped.size}、未知 ${it.unknown.size}")
                refreshAll()
            }.onFailure {
                notify("导入失败：${it.message}")
            }
        }
    }

    fun importCredentials(providerId: String, raw: String) {
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { engine.importCredentials(providerId, raw) } }
            result.onSuccess {
                notify("账号已添加：${it.displayName}")
                refreshAll()
            }.onFailure {
                notify("导入失败：${it.message}")
            }
        }
    }

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
        val sess = Regex("hatch_sess=([^;\\s]+)").find(ck)?.groupValues?.get(1).orEmpty()
        val uid = sess.take(8).ifEmpty { "muse" + System.currentTimeMillis() % 100000 }
        engine.pool.add(
            ProviderAccount(
                providerId = "muse",
                uid = uid,
                displayName = "muse $uid",
                secret = JsonObject().apply { addProperty("cookie", ck) }.toString(),
            ),
        )
        refreshAll()
        notify("muse 账号已添加")
        return true
    }

    fun addDoubaoAccount(name: String, cookie: String, deviceId: String, webId: String): Boolean {
        if (cookie.isBlank() || deviceId.isBlank() || webId.isBlank()) {
            notify("Cookie / deviceId / webId 不能为空")
            return false
        }
        val secret = JsonObject().apply {
            addProperty("cookie", cookie)
            addProperty("device_id", deviceId)
            addProperty("web_id", webId)
            addProperty("local_app_id", "582478")
        }.toString()
        val uid = deviceId.hashCode().toUInt().toString(16)
        engine.pool.add(
            ProviderAccount(
                providerId = "doubao",
                uid = uid,
                displayName = name.ifBlank { "豆包 " + deviceId.takeLast(6) },
                secret = secret,
            ),
        )
        refreshAll()
        notify("豆包账号已添加")
        return true
    }

    fun addCustomAccount(name: String, baseUrl: String, key: String): Boolean {
        if (name.isBlank() || baseUrl.isBlank() || key.isBlank()) {
            notify("名称 / Base URL / Key 不能为空")
            return false
        }
        val uid = name.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }
            .takeIf { it.isNotBlank() } ?: ("custom" + System.currentTimeMillis() % 100000)
        engine.pool.add(
            ProviderAccount(providerId = "custom", uid = uid, displayName = name, apiKey = key, baseUrl = baseUrl),
        )
        refreshAll()
        notify("自定义账号已添加")
        return true
    }

    fun addFreePoolAccount(): Boolean {
        engine.seedFreePoolIfNeeded()
        refreshAll()
        val has = engine.pool.accounts("freepool").isNotEmpty()
        notify(if (has) "免费池已启用" else "免费池启用失败")
        return has
    }

    // ------------------------------------------------------------ 登录流程

    private fun openLogin(
        providerId: String,
        url: String,
        mode: String,
        region: String = "cn",
        deviceState: String = "",
    ) {
        LoginWindow(
            engine = engine,
            providerId = providerId,
            startUrl = url,
            mode = mode,
            region = region,
            deviceState = deviceState,
            onAccount = { account ->
                notify("登录成功：${account.displayName}")
                refreshAll()
            },
            onDone = { error ->
                if (error != null) notify(error)
                refreshAll()
            },
        ).show()
    }

    /** WorkBuddy 设备授权 / 微信扫码登录。 */
    fun startWorkbuddyLogin(region: String, wechat: Boolean = false) {
        loginJob?.cancel()
        state = state.copy(wbLogin = WbLoginState(active = true, region = region, status = "正在获取授权链接…"))
        loginJob = scope.launch {
            val pair = withContext(Dispatchers.IO) {
                engine.setCodeBuddyAuthPlatform(dev.aigw.provider.codebuddy.CodeBuddyProvider.PLATFORM_CLI)
                runCatching {
                    if (wechat) {
                        // 微信 broker 的回调固定落在 codebuddy.cn（服务端决定），
                        // 而 state 在 chatBase（copilot.tencent.com）申请，跨域后
                        // /console/login/enterprise 拿不到同域会话，一直是 401。
                        // 因此扫码这条路径整个改到 codebuddy.cn 走一遍。
                        LoginWindow.wechatLoginUrl(region)
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
            // 保留 wbLogin 的 url/state：登录窗里的「在系统浏览器中打开」与
            // openWorkbuddyLoginUrl 都要用到；取消时由 cancelWorkbuddyLogin 清空。
            openLogin("codebuddy", loginUrl, LoginHooks.MODE_DEVICE, region, loginState)
        }
    }

    /** 授权链接交给系统浏览器打开，网关轮询 state 收凭证。 */
    fun openWorkbuddyLoginUrl() {
        val login = state.wbLogin
        val url = login.url
        val authState = login.state
        if (url.isBlank() || authState.isBlank()) return
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .onFailure {
                state = state.copy(wbLogin = login.copy(status = "打开浏览器失败", error = it.message))
                return
            }
        state = state.copy(wbLogin = login.copy(active = false, status = "浏览器已打开，等待完成授权后自动收凭证…"))
        collectJob?.cancel()
        collectJob = scope.launch {
            while (true) {
                val poll = withContext(Dispatchers.IO) {
                    runCatching { engine.pollDeviceAuth("codebuddy", authState, login.region) }
                        .getOrElse { DeviceAuthPoll.Failed(it.message ?: "网络错误") }
                }
                when (poll) {
                    is DeviceAuthPoll.Pending -> delay(2_000)
                    is DeviceAuthPoll.Success -> {
                        runCatching { engine.saveAccount(poll.account) }
                        state = state.copy(wbLogin = WbLoginState(), message = "登录成功：${poll.account.displayName}")
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

    // ---------- 短信登录 ----------

    fun sendLoomyCode(phone: String, ccode: String) {
        if (phone.isBlank()) return
        scope.launch {
            val msgid = withContext(Dispatchers.IO) { runCatching { engine.sendSmsCode("loomy", phone, ccode) } }
            msgid.onSuccess {
                state = state.copy(loomyLogin = SmsLoginState(active = true, phone = phone, ccode = ccode, msgid = it, status = "验证码已发送"))
            }.onFailure {
                state = state.copy(loomyLogin = SmsLoginState(phone = phone, ccode = ccode, status = "发送失败", error = it.message))
            }
        }
    }

    fun loginLoomy(phone: String, code: String, ccode: String) {
        val msgid = state.loomyLogin.msgid
        if (msgid.isBlank()) {
            notify("请先发送验证码")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.completeSmsLogin("loomy", phone, code, msgid, ccode) }
            }
            result.onSuccess {
                state = state.copy(loomyLogin = SmsLoginState())
                notify("登录成功：${it.displayName}")
                refreshAll()
            }.onFailure {
                state = state.copy(loomyLogin = state.loomyLogin.copy(status = "登录失败", error = it.message))
            }
        }
    }

    fun sendChatglmCode(phone: String, ccode: String) {
        if (phone.isBlank()) return
        scope.launch {
            val msgid = withContext(Dispatchers.IO) { runCatching { engine.sendSmsCode("chatglm", phone, ccode) } }
            msgid.onSuccess {
                state = state.copy(chatglmLogin = SmsLoginState(active = true, phone = phone, ccode = ccode, msgid = it, status = "验证码已发送"))
            }.onFailure {
                state = state.copy(chatglmLogin = SmsLoginState(phone = phone, ccode = ccode, status = "发送失败", error = it.message))
            }
        }
    }

    fun loginChatglm(phone: String, code: String, ccode: String) {
        val msgid = state.chatglmLogin.msgid
        if (msgid.isBlank()) {
            notify("请先发送验证码")
            return
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { engine.completeSmsLogin("chatglm", phone, code, msgid, ccode) }
            }
            result.onSuccess {
                state = state.copy(chatglmLogin = SmsLoginState())
                notify("登录成功：${it.displayName}")
                refreshAll()
            }.onFailure {
                state = state.copy(chatglmLogin = state.chatglmLogin.copy(status = "登录失败", error = it.message))
            }
        }
    }

    // ---------- 网页登录（JCEF） ----------

    fun startDeepSeekLogin() {
        deepSeekTransport.warmUp()
        scope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { engine.startTokenLogin("deepseek") } }
                .getOrElse {
                    notify("打开登录页失败：${it.message}")
                    return@launch
                }
            openLogin("deepseek", url, LoginHooks.MODE_TOKEN)
        }
    }

    fun startMimoLogin() {
        // Android 写死 aistudio.xiaomi.com（与 MimoProvider loginPageUrl 的
        // aistudio.xiaomimimo.com 不同，以 AppViewModel 的实际行为为准）。
        openLogin("mimo", "https://aistudio.xiaomi.com/", LoginHooks.MODE_TOKEN)
    }

    fun startMuseLogin() {
        openLogin("muse", "https://muse.ai/", LoginHooks.MODE_TOKEN)
    }

    fun startChatglmLogin() {
        chatglmTransport.warmUp()
        scope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { engine.startTokenLogin("chatglm") } }
                .getOrElse {
                    notify("打开登录页失败：${it.message}")
                    return@launch
                }
            openLogin("chatglm", url, LoginHooks.MODE_TOKEN)
        }
    }

    fun startJimengLogin() {
        scope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { engine.startTokenLogin("jimeng") } }
                .getOrElse {
                    notify("打开登录页失败：${it.message}")
                    return@launch
                }
            openLogin("jimeng", url, LoginHooks.MODE_TOKEN)
        }
    }

    fun startDoubaoLogin() {
        scope.launch {
            val url = withContext(Dispatchers.IO) { runCatching { engine.startTokenLogin("doubao") } }
                .getOrElse {
                    notify("打开登录页失败：${it.message}")
                    return@launch
                }
            openLogin("doubao", url, LoginHooks.MODE_TOKEN)
        }
    }

    /** Trae / ChatGPT / 小浣熊：网页回调登录（本地回调服务器 127.0.0.1:51120）。 */
    fun startProviderLogin(providerId: String) {
        scope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin(providerId, "") }
            }.getOrElse {
                notify("打开 $providerId 登录失败：${it.message}")
                return@launch
            }
            openLogin(providerId, ticket.loginUrl, LoginHooks.MODE_CALLBACK)
            watchBrowserLogin()
        }
    }

    fun startTraeLogin(region: String) {
        scope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin("trae", region) }
            }.getOrElse {
                notify("打开 Trae 登录失败：${it.message}")
                return@launch
            }
            openLogin("trae", ticket.loginUrl, LoginHooks.MODE_CALLBACK, region)
            watchBrowserLogin()
        }
    }

    fun startRaccoonLogin() {
        scope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin("raccoon", "") }
            }.getOrElse {
                notify("打开小浣熊登录失败：${it.message}")
                return@launch
            }
            openLogin("raccoon", ticket.loginUrl, LoginHooks.MODE_CALLBACK)
            notify("授权完成后复制浏览器地址栏的链接，回来点「粘贴回调链接导入」")
            watchBrowserLogin()
        }
    }

    /** 登录页关闭后刷新账号列表（账号由引擎直接写入池）。 */
    private fun watchBrowserLogin() {
        scope.launch {
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

    /** 生成带 PKCE 的授权链接并复制到剪贴板（ChatGPT）。 */
    fun copyChatgptAuthUrl() {
        scope.launch {
            val ticket = withContext(Dispatchers.IO) {
                runCatching { engine.startBrowserLogin("chatgpt", "") }
            }.getOrElse {
                notify("获取授权链接失败：${it.message}")
                return@launch
            }
            copyToClipboard(ticket.loginUrl)
            notify("授权链接已复制：请在已登录 ChatGPT 的浏览器里打开")
            watchBrowserLogin()
        }
    }

    fun pasteCallbackUrl(raw: String): Boolean {
        if (raw.isBlank()) {
            notify("回调链接不能为空")
            return false
        }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { engine.pasteBrowserCallback(raw) } }
            result.onSuccess {
                notify("登录成功：${it.displayName}")
                refreshAll()
            }.onFailure {
                notify("导入失败：${it.message}")
            }
        }
        return true
    }

    // ------------------------------------------------------------ 工具

    fun copyToClipboard(text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    fun copyText(text: String, label: String) {
        copyToClipboard(text)
        notify("$label 已复制")
    }

    companion object {
        /** 导出账号 JSON 的解析（用于导入信封预览）；失败返回空列表。 */
        fun parseTransferPreview(json: String): List<String> {
            return try {
                val arr = JsonParser.parseString(json).asJsonObject
                    .getAsJsonArray("accounts") ?: return emptyList()
                arr.mapNotNull { el ->
                    val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                    val pid = o.get("providerId")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    val uid = o.get("uid")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    if (pid.isEmpty() || uid.isEmpty()) null else "$pid/$uid"
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
