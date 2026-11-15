package com.vantacode.feature.settings.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * DSH Go 插件格式（.dshgo.json）。
 * 支持主题配色、背景图、桌面宠物、自定义指令。
 */
@Serializable
data class DshGoPlugin(
    val name: String = "",
    val displayName: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val type: String = "theme",
    val icon: String = "",
    val tags: List<String> = emptyList(),
    val theme: DshGoTheme? = null,
    val pet: DshGoPet? = null,
    val background: DshGoBackground? = null,
    val instructions: String = "",
    val extra: JsonObject? = null
)

@Serializable
data class DshGoTheme(
    val primary: String = "",
    val secondary: String = "",
    val background: String = "",
    val surface: String = "",
    val onPrimary: String = "",
    val onBackground: String = "",
    val accent: String = "",
    val darkMode: Boolean = true
)

@Serializable
data class DshGoPet(
    val name: String = "",
    val sprites: List<String> = emptyList(),
    val animations: Map<String, String> = emptyMap(),
    val scale: Float = 1.0f
)

@Serializable
data class DshGoBackground(
    val type: String = "image",
    val url: String = "",
    val blur: Float = 0f,
    val opacity: Float = 1.0f
)

/** DSH Go 插件市场条目。 */
@Serializable
data class DshGoMarketEntry(
    val name: String = "",
    val displayName: String = "",
    val description: String = "",
    val author: String = "",
    val type: String = "theme",
    val icon: String = "",
    val downloadUrl: String = "",
    val tags: List<String> = emptyList(),
    val version: String = "1.0.0"
)
