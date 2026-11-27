package com.vantacode.feature.plugin.data.index

import com.vantacode.feature.plugin.domain.LocalizedText
import com.vantacode.feature.plugin.domain.PluginManifestParser
import com.vantacode.feature.plugin.domain.SemVer
import kotlinx.serialization.decodeFromString

/** 索引静态校验；不依赖 Android，便于单元测试。 */
internal object PluginIndexParser {

    // 发布者允许中文等 Unicode 字母，与清单里的 publisher 规则保持一致。
    private val AUTHOR = Regex("^[\\p{L}][\\p{L}\\p{N}_-]{0,31}$")
    private val LANGUAGE_TAG = Regex("^[a-z]{2,3}(-[a-z0-9]{2,8})*$")

    fun parse(bytes: ByteArray): PluginIndexDocument {
        if (bytes.size > MAX_INDEX_BYTES) throw PluginIndexException("index.json 超过 2 MiB")
        val index = try {
            PluginIndexJson.decodeFromString<PluginIndexDocument>(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw PluginIndexException("index.json 解析失败", e)
        }
        if (index.schemaVersion != 1) throw PluginIndexException("不支持的 schema_version=${index.schemaVersion}")
        if (index.plugins.size > MAX_PLUGINS) throw PluginIndexException("插件数量超过 $MAX_PLUGINS")
        // 逐条校验：单条不合法只跳过这条，不再整份拒绝。
        // 上游同步脚本曾把 A 的 version 写到 B 名下，一条脏数据就让商店
        // 整个打不开（甚至缓存的旧索引也一起失效），代价太大。
        val ids = HashSet<String>()
        val accepted = ArrayList<PluginIndexPlugin>(index.plugins.size)
        val rejected = ArrayList<String>()
        index.plugins.forEach { plugin ->
            val problem: String? = runCatching<String?> {
                if (!PluginManifestParser.isValidPluginId(plugin.id)) return@runCatching "非法插件 ID（${plugin.id}）"
                if (!ids.add(plugin.id)) return@runCatching "插件 ID 重复"
                validateLocalized(plugin.id, "name", plugin.name, MAX_NAME, allowNewlines = false)
                validateLocalized(plugin.id, "description", plugin.description, MAX_DESCRIPTION, allowNewlines = true)
                if (!AUTHOR.matches(plugin.author)) return@runCatching "author 非法"
                val repository = java.net.URI(plugin.repository)
                if (repository.scheme != "https" || repository.host != "github.com") {
                    return@runCatching "repository 不是 GitHub 地址"
                }
                plugin.version?.let { version ->
                    if (version.startsWith("v") || SemVer.parse(version) == null) {
                        return@runCatching "version 非法（$version）"
                    }
                }
                plugin.minAppVersion?.let {
                    if (SemVer.parse(it) == null) return@runCatching "min_app_version 非法"
                }
                if (plugin.tags.any { it !in PluginTags.ALL }) return@runCatching "tags 含未知值"
                if (plugin.tags.size > 4 || plugin.tags.distinct().size != plugin.tags.size) {
                    return@runCatching "tags 数量或重复项非法"
                }
                plugin.logo?.let { requireHttps(it, "${plugin.id} logo") }
                plugin.homepage?.let { requireHttps(it, "${plugin.id} homepage") }
                null
            }.getOrElse { e -> e.message ?: "校验异常" }
            if (problem == null) {
                accepted.add(plugin)
            } else {
                // 跳过的条目要把 id 释放掉，否则后面同 id 的合法条目会被判成重复
                ids.remove(plugin.id)
                rejected.add("${plugin.id}：$problem")
            }
        }
        // 全被拒才报错；只要还剩可用条目就照常展示
        if (accepted.isEmpty() && rejected.isNotEmpty()) {
            throw PluginIndexException("索引里没有可用插件（${rejected.take(3).joinToString("；")}）")
        }
        return index.copy(plugins = accepted)
    }

    private fun validateLocalized(
        pluginId: String,
        field: String,
        text: LocalizedText,
        maxLength: Int,
        allowNewlines: Boolean
    ) {
        if (text.values.size > MAX_LOCALES) {
            throw PluginIndexException("$pluginId 的 $field 语言数量超过 $MAX_LOCALES")
        }
        text.values.forEach { (tag, value) ->
            if (tag != LocalizedText.DEFAULT_KEY && !LANGUAGE_TAG.matches(tag)) {
                throw PluginIndexException("$pluginId 的 $field 含非法语言标签: $tag")
            }
            if (value.isEmpty() || value.length > maxLength || hasControlChars(value, allowNewlines)) {
                throw PluginIndexException("$pluginId 的 $field 文本非法: $tag")
            }
        }
    }

    private fun hasControlChars(text: String, allowNewlines: Boolean) =
        text.any { it.isISOControl() && !(allowNewlines && it == '\n') }

    private fun requireHttps(url: String, field: String) {
        if (!url.startsWith("https://")) throw PluginIndexException("$field 必须是 https 地址")
    }

    private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
    private const val MAX_PLUGINS = 5000
    private const val MAX_NAME = 120
    private const val MAX_DESCRIPTION = 2000
    private const val MAX_LOCALES = 16
}
