package com.vantacode.feature.settings.data.repository

import kotlinx.serialization.Serializable

/** 技能条目（简化格式，与插件共用搜索）。 */
@Serializable
data class MarketEntry(
    val name: String,
    val displayName: String = "",
    val description: String = "",
    val author: String = "",
    val stars: Int = 0,
    val packageName: String = "",
    val tags: List<String> = emptyList(),
    val downloadUrl: String = "",
    val category: String = "skill",
    val version: String = "1.0.0"
)

/** 在线注册表（技能市场用，简化格式）；兼容 skills / plugins 两种字段名。 */
@Serializable
data class MarketRegistry(
    val version: Int = 1,
    val updatedAt: String = "",
    val skills: List<MarketEntry> = emptyList(),
    val plugins: List<MarketEntry> = emptyList()
) {
    val entries: List<MarketEntry> get() = skills.ifEmpty { plugins }
}
