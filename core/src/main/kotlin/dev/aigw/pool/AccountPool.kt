package dev.aigw.pool

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.AccountState
import dev.aigw.provider.BalanceInfo
import dev.aigw.provider.ProviderAccount
import dev.aigw.util.Log
import java.io.File

/** 账号池：账号凭证与状态独立落盘（一账号一文件），内存缓存，改动即写。 */
class AccountPool(private val dataDir: File) {

    data class Entry(val account: ProviderAccount, val state: AccountState)

    private val gson = Gson()
    private val accountsDir = File(dataDir, "accounts")
    private val stateDir = File(dataDir, "pool/state")
    private val entries = LinkedHashMap<String, Entry>() // key = "$providerId/$uid"
    private var lastSignature = ""
    private var lastScanAt = 0L

    init {
        accountsDir.mkdirs()
        stateDir.mkdirs()
        load()
        lastSignature = scanSignature()
    }

    /** 目录变化检测：CLI 或外部进程新增/删除账号时热重载，运行中的服务无需重启。 */
    private fun maybeReload() {
        val now = System.currentTimeMillis()
        if (now - lastScanAt < 500) return
        lastScanAt = now
        val sig = scanSignature()
        if (sig != lastSignature) {
            lastSignature = sig
            load()
        }
    }

    private fun scanSignature(): String {
        val sb = StringBuilder()
        accountsDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".json") }
            .sortedBy { it.path }
            .forEach { sb.append(it.path).append(':').append(it.lastModified()).append(';') }
        return sb.toString()
    }

    fun all(): List<Entry> {
        maybeReload()
        return entries.values.toList()
    }

    fun accounts(providerId: String): List<Entry> {
        maybeReload()
        return entries.values.filter { it.account.providerId == providerId }
    }

    fun enabled(providerId: String): List<Entry> {
        maybeReload()
        return accounts(providerId).filter { it.state.enabled }
    }

    /** 可用账号：启用且不在冷却期内（路由选号用）。 */
    fun available(providerId: String): List<Entry> {
        maybeReload()
        return accounts(providerId).filter { it.state.enabled && !it.state.inCooldown() }
    }

    fun get(providerId: String, uid: String): Entry? {
        maybeReload()
        return entries["$providerId/$uid"]
    }

    fun add(account: ProviderAccount, state: AccountState = AccountState()) {
        entries["${account.providerId}/${account.uid}"] = Entry(account, state)
        persistAccount(account)
        persistState(account.providerId, account.uid, state)
    }

    /** 只更新凭证，保留已有运行状态（余额/启用/冷却等）。 */
    fun updateAccount(account: ProviderAccount) {
        val key = "${account.providerId}/${account.uid}"
        val existing = entries[key]
        entries[key] = if (existing != null) existing.copy(account = account) else Entry(account, AccountState())
        persistAccount(account)
    }

    fun remove(providerId: String, uid: String) {
        entries.remove("$providerId/$uid")
        File(accountsDir, "$providerId/$uid.json").delete()
        File(stateDir, "$providerId/$uid.json").delete()
    }

    fun setDisabled(providerId: String, uid: String, reason: String) {
        get(providerId, uid)?.let {
            it.state.enabled = false
            it.state.disabledReason = reason
            it.state.consecutiveFailures = 0
            it.state.cooldownUntil = 0
            persistState(providerId, uid, it.state)
        }
    }

    fun setEnabled(providerId: String, uid: String) {
        get(providerId, uid)?.let {
            it.state.enabled = true
            it.state.disabledReason = null
            it.state.consecutiveFailures = 0
            it.state.cooldownUntil = 0
            persistState(providerId, uid, it.state)
        }
    }

    /** 调用成功：清零连续失败。 */
    fun markSuccess(providerId: String, uid: String) {
        get(providerId, uid)?.let {
            if (it.state.consecutiveFailures != 0) {
                it.state.consecutiveFailures = 0
                persistState(providerId, uid, it.state)
            }
        }
    }

    /**
     * 调用失败（非凭证失效）：累计连续失败，达到阈值进入冷却。
     * 返回是否刚刚进入冷却。
     */
    fun recordFailure(providerId: String, uid: String, threshold: Int, cooldownMs: Long): Boolean {
        val entry = get(providerId, uid) ?: return false
        entry.state.consecutiveFailures++
        var entered = false
        if (entry.state.consecutiveFailures >= threshold) {
            entry.state.cooldownUntil = System.currentTimeMillis() + cooldownMs
            entry.state.consecutiveFailures = 0
            entered = true
        }
        persistState(providerId, uid, entry.state)
        return entered
    }

    fun updateBalance(providerId: String, uid: String, info: BalanceInfo) {
        get(providerId, uid)?.let {
            // 未知余额不覆盖已知值：一次失败查询不能把积分静默抹成 0
            if (info.known) it.state.balance = info.remain
            it.state.enterprise = info.enterprise
            it.state.lastBalanceAt = System.currentTimeMillis()
            persistState(providerId, uid, it.state)
        }
    }

    fun markCheckin(providerId: String, uid: String, at: Long = System.currentTimeMillis()) {
        get(providerId, uid)?.let {
            it.state.lastCheckinAt = at
            persistState(providerId, uid, it.state)
        }
    }

    fun setLastError(providerId: String, uid: String, err: String?) {
        get(providerId, uid)?.let {
            it.state.lastError = err
            it.state.lastErrorAt = if (err != null) System.currentTimeMillis() else 0
            persistState(providerId, uid, it.state)
        }
    }

    /** 选号：余额最高者优先（无余额信息则并列按添加顺序）。 */
    fun pick(providerId: String): Entry? = enabled(providerId)
        .maxWithOrNull(compareBy<Entry> { it.state.balance ?: Double.MIN_VALUE }
            .thenBy { it.state.balance == null })

    private fun load() {
        accountsDir.walkTopDown().filter { it.isFile && it.name.endsWith(".json") }.forEach { f ->
            try {
                val providerId = f.parentFile?.name ?: return@forEach
                val uid = f.name.removeSuffix(".json")
                val account = gson.fromJson(f.readText(), ProviderAccount::class.java)
                val state = readState(providerId, uid)
                entries["$providerId/$uid"] = Entry(account, state)
            } catch (e: Exception) {
                Log.warn("跳过损坏的账号文件 ${f.path}: ${e.message}")
            }
        }
    }

    private fun readState(providerId: String, uid: String): AccountState {
        val f = File(stateDir, "$providerId/$uid.json")
        return try {
            if (f.exists()) gson.fromJson(f.readText(), AccountState::class.java) else AccountState()
        } catch (e: Exception) {
            Log.warn("状态文件损坏，重置 ${f.path}")
            AccountState()
        }
    }

    private fun persistAccount(account: ProviderAccount) {
        val dir = File(accountsDir, account.providerId)
        dir.mkdirs()
        File(dir, "${account.uid}.json").writeText(gson.toJson(account))
    }

    private fun persistState(providerId: String, uid: String, state: AccountState) {
        val dir = File(stateDir, providerId)
        dir.mkdirs()
        File(dir, "$uid.json").writeText(gson.toJson(state))
    }
}
