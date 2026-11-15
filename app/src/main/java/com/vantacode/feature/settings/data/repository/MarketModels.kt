package com.vantacode.feature.settings.data.repository

import kotlinx.serialization.Serializable

/** DSH 插件目录条目（format 1）。 */
@Serializable
data class DshCatalogEntry(
    val name: String = "",
    val title: String = "",
    val description: DshLocalizedString? = null,
    val homepage: String = "",
    val repository: DshRepository? = null,
    val metadata: DshEntryMetadata? = null,
    val versions: List<DshVersion> = emptyList()
) {
    val displayName: String get() = title.ifBlank { name }
    val descriptionZh: String get() = description?.zh ?: description?.en ?: ""
    val category: String get() = metadata?.category ?: ""
    val latestVersion: String get() = versions.firstOrNull()?.version ?: ""
    val repoUrl: String get() = repository?.url ?: ""
    val installTarget: String get() = name.ifBlank { title }
}

@Serializable
data class DshLocalizedString(val en: String = "", val zh: String = "")

@Serializable
data class DshRepository(val url: String = "", val directory: String = "")

@Serializable
data class DshEntryMetadata(
    val category: String = "",
    val keywords: List<String> = emptyList(),
    val labels: List<String> = emptyList()
)

@Serializable
data class DshVersion(
    val version: String = "",
    val publishTimestamp: Long = 0,
    val source: DshSource? = null
)

@Serializable
data class DshSource(val type: String = "")

/** DSH 插件目录顶层（format 1）。 */
@Serializable
data class DshCatalog(
    val format: Int = 1,
    val title: DshLocalizedString? = null,
    val description: DshLocalizedString? = null,
    val metadata: DshCatalogMetadata? = null,
    val plugins: List<DshCatalogEntry> = emptyList()
)

@Serializable
data class DshCatalogMetadata(
    val categories: Map<String, DshCategoryInfo> = emptyMap()
)

@Serializable
data class DshCategoryInfo(val title: DshLocalizedString? = null)

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

/** 在线注册表（技能市场用，简化格式）。 */
@Serializable
data class MarketRegistry(
    val version: Int = 1,
    val updatedAt: String = "",
    val skills: List<MarketEntry> = emptyList()
)
