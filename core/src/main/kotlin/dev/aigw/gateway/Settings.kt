package dev.aigw.gateway

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.aigw.util.Log
import java.io.File
import java.security.SecureRandom

/** 全局网关设置，持久化在 data/settings.json。 */
class Settings {
    var port: Int = 8790
    var apiKey: String = generateApiKey()
    var allowNoKey: Boolean = false
    var bindLan: Boolean = false
    var modelsCacheSeconds: Int = 60    /** 余额自动刷新间隔（秒），首页可见时生效。 */
    var balanceRefreshSeconds: Int = 300
    /** 单账号并发上限（同一账号同时在途请求数）。 */
    var maxConcurrentPerAccount: Int = 1
    /** 全网关同时在途请求上限；0 = 不限。 */
    var maxConcurrentGlobal: Int = 0
    /** 连续失败达到该次数后进入冷却。 */
    var failThreshold: Int = 3
    /** 冷却时长（秒），冷却期内该账号不被选中，到期自动恢复。 */
    var cooldownSeconds: Int = 300
    /**
     * 模型级冷却时长（秒）。
     *
     * 归因于「该模型超限」的错误换号也没用，按 `供应商|模型` 冷却这段时间，
     * 周期内该模型直接跳过，不把整个账号拖进冷却。
     */
    var modelCooldownSeconds: Int = 600
    /** 会话粘黏：同一会话（x-session-id / user）固定用同一账号，提高上游缓存命中。 */
    var sessionSticky: Boolean = false
    /**
     * 供应商调用区域偏好：providerId → "cn"/"global"。
     * 在供应商添加账号弹层里切换；空/缺失 = 国内外自由调用。
     * 带 -cn/-global 显式前缀的调用不受此偏好影响。
     * 可为 null：旧版 settings.json 没有该字段。
     */
    var regionPrefs: MutableMap<String, String>? = mutableMapOf()
    /** 悬浮监测球开关（App 层设置，不进 core 也能独立工作；仅为落盘方便放这里）。 */
    var monitorBubble: Boolean = false

    /** 桌面宠物：开启后用它替代监测球（两者互斥，宠物优先）。 */
    var petBubble: Boolean = true

    /** 网关层统一注入的系统提示词；为空表示不注入。 */
    var systemPrompt: String = ""

    /** 悬浮监测球透明度（0.3-1.0，App 层）。 */
    var bubbleOpacity: Float = 1f

    /** 外观主题名称（App 层）。 */
    var themeName: String = "默认"

    /** 已停用的供应商：模型不进列表，也无法通过前缀调用。 */
    var disabledProviders: MutableSet<String> = mutableSetOf()

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        /** 默认密钥：`sk-` + 32 位大小写字母与数字。 */
        fun generateApiKey(): String {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = SecureRandom()
            return buildString {
                append("sk-")
                repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
        }

        fun load(dataDir: File): Settings {
            val f = File(dataDir, "settings.json")
            // 首次启动即落盘，保证默认随机密钥在重启后不变
            if (!f.exists()) return Settings().also { save(dataDir, it) }
            return try {
                val text = f.readText()
                val settings = gson.fromJson(text, Settings::class.java)
                // 旧版字段名迁移：disabledAutoRoute → disabledProviders
                runCatching {
                    val legacy = JsonParser.parseString(text).asJsonObject
                        .getAsJsonArray("disabledAutoRoute")
                        ?: return@runCatching
                    legacy.forEach { element ->
                        runCatching { settings.disabledProviders.add(element.asString) }
                    }
                }
                // 旧文件没有新字段时，Gson 不会执行字段初始化，补回默认值
                if (settings.balanceRefreshSeconds <= 0) settings.balanceRefreshSeconds = 300
                if (settings.maxConcurrentPerAccount <= 0) settings.maxConcurrentPerAccount = 1
                if (settings.maxConcurrentGlobal < 0) settings.maxConcurrentGlobal = 0
                if (settings.modelCooldownSeconds <= 0) settings.modelCooldownSeconds = 600
                if (settings.failThreshold <= 0) settings.failThreshold = 3
                if (settings.cooldownSeconds <= 0) settings.cooldownSeconds = 300
                if (settings.themeName.isNullOrBlank()) settings.themeName = "默认"
                if (settings.bubbleOpacity <= 0f) settings.bubbleOpacity = 1f
                if (settings.regionPrefs == null) settings.regionPrefs = mutableMapOf()
                settings
            } catch (e: Exception) {
                Log.warn("settings.json 损坏，使用默认：${e.message}")
                Settings()
            }
        }

        fun save(dataDir: File, s: Settings) {
            dataDir.mkdirs()
            File(dataDir, "settings.json").writeText(gson.toJson(s))
        }
    }
}
