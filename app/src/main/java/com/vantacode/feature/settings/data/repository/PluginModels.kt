package com.vantacode.feature.settings.data.repository

import kotlinx.serialization.Serializable

/** 插件清单（manifest.json）。 */
@Serializable
data class PluginManifest(
    val name: String,
    val displayName: String = "",
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val skillCount: Int = 0,
    val toolCount: Int = 0,
    val mcpCount: Int = 0
)

/** 已安装的插件。 */
@Serializable
data class InstalledPlugin(
    val manifest: PluginManifest,
    val installPath: String,
    val enabled: Boolean = true,
    val installedAt: Long = 0L
)

/** 插件安装结果。 */
sealed class PluginInstallResult {
    data class Success(val plugin: InstalledPlugin) : PluginInstallResult()
    data class Failure(val reason: String) : PluginInstallResult()
}
