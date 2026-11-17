package dev.aigw.pool

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.provider.ProviderAccount

/**
 * 账号迁移信封：导出账号时统一包一层，导入时凭 format 标记识别并直接落池，
 * 不再经过供应商凭证解析与联网校验——保证「导出 → 重新导入」闭环可用，
 * 即使 token 已在上游失效，账号也能恢复进池（调用时再按正常流程报错/刷新）。
 */
object AccountTransfer {

    /** 当前版本；导入时高于它的文件一律拒绝（宁可报错也不猜字段）。 */
    const val VERSION = 1

    const val FORMAT = "ai-gateway/account-transfer"

    /** 兼容 2.32.0 及更早版本的导出格式。 */
    private val COMPAT_FORMATS = setOf(FORMAT, "aigw-accounts")

    /**
     * 编码成迁移信封。
     *
     * 字段比 [decode] 要求的更全：apiKey / baseUrl / extra 一并带上，
     * 这样自定义供应商（如豆包指向本机反代）导出后能原样恢复；
     * 老版本 decode 会忽略这些字段，不影响向前兼容。
     */
    fun encode(accounts: List<ProviderAccount>): String {
        val array = JsonArray()
        for (account in accounts) {
            val item = JsonObject()
            item.addProperty("providerId", account.providerId)
            item.addProperty("uid", account.uid)
            item.addProperty("nickname", account.displayName)
            item.addProperty("displayName", account.displayName)
            item.addProperty("secret", account.secret)
            item.addProperty("apiKey", account.apiKey)
            item.addProperty("baseUrl", account.baseUrl)
            if (account.extra.isNotEmpty()) {
                val extra = JsonObject()
                for ((k, v) in account.extra) extra.addProperty(k, v)
                item.add("extra", extra)
            }
            array.add(item)
        }
        val root = JsonObject()
        root.addProperty("format", FORMAT)
        root.addProperty("version", VERSION)
        root.addProperty("exportedAt", System.currentTimeMillis())
        root.add("accounts", array)
        return root.toString()
    }

    /**
     * 解析导入内容。抛出的 [IllegalArgumentException] 消息是给用户看的原话。
     * [allowLegacy] 为 true 时兼容旧版 App 导出的单账号 JSON（无 format 标记，
     * 但有 providerId/secret）。
     */
    fun decode(json: String, allowLegacy: Boolean = false): List<ProviderAccount> {
        val text = json.trim()
        if (text.isEmpty()) throw IllegalArgumentException("内容为空，先粘贴导出内容或选择一个文件")
        val obj = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull()
            ?: throw IllegalArgumentException("不是合法的 JSON")

        val format = obj.get("format")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        if (format.isEmpty()) {
            if (!allowLegacy) throw IllegalArgumentException("不是本应用导出的账号文件")
            // 旧版单账号导出：{providerId, uid, secret...}
            val legacy = legacyAccount(obj)
                ?: throw IllegalArgumentException("不是本应用导出的账号文件")
            return listOf(legacy)
        }
        if (format !in COMPAT_FORMATS) throw IllegalArgumentException("不是本应用导出的账号文件")
        val version = obj.get("version")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (version <= 0 || version > VERSION) {
            throw IllegalArgumentException("文件版本 $version 不支持（当前支持到 $VERSION）")
        }
        val array = obj.get("accounts")
        if (array == null || !array.isJsonArray) throw IllegalArgumentException("文件里没有账号列表")

        val accounts = ArrayList<ProviderAccount>(array.asJsonArray.size())
        for (element in array.asJsonArray) {
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val providerId = item.str("providerId")
            val uid = item.str("uid")
            val secret = item.str("secret")
            if (providerId.isEmpty() || uid.isEmpty()) continue
            // secret 为空的老记录仍允许导入（自定义供应商可能只用 apiKey/baseUrl）
            if (secret.isEmpty() && item.str("apiKey").isEmpty() && item.str("baseUrl").isEmpty()) continue
            val extra = mutableMapOf<String, String>()
            item.getAsJsonObject("extra")?.entrySet()?.forEach { (k, v) ->
                if (v.isJsonPrimitive) extra[k] = v.asString
            }
            accounts.add(
                ProviderAccount(
                    providerId = providerId,
                    uid = uid,
                    displayName = item.str("nickname").ifEmpty { item.str("displayName") },
                    apiKey = item.str("apiKey"),
                    baseUrl = item.str("baseUrl"),
                    secret = secret,
                    extra = extra,
                ),
            )
        }
        if (accounts.isEmpty()) throw IllegalArgumentException("文件里没有可用的账号（凭证为空或字段缺失）")
        return accounts
    }

    private fun legacyAccount(obj: JsonObject): ProviderAccount? {
        val providerId = obj.str("providerId")
        val uid = obj.str("uid")
        val secret = obj.str("secret")
        if (providerId.isEmpty() || uid.isEmpty() || secret.isEmpty()) return null
        return ProviderAccount(
            providerId = providerId,
            uid = uid,
            displayName = obj.str("displayName").ifEmpty { obj.str("nickname") },
            secret = secret,
        )
    }

    private fun JsonObject.str(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}
