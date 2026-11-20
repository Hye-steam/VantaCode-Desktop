package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vantacode.core.theme.Spacing
import com.vantacode.core.ui.Strings
import com.vantacode.di.AppGraph
import com.vantacode.feature.plugin.data.index.PluginIndexClient
import com.vantacode.feature.plugin.data.index.PluginIndexDefaults
import com.vantacode.feature.plugin.data.index.PluginIndexPlugin
import com.vantacode.feature.plugin.domain.InstalledPlugin
import com.vantacode.feature.plugin.domain.PluginInstallResult
import com.vantacode.feature.plugin.domain.PluginManager
import kotlinx.coroutines.launch

private val lang: String get() = if (Strings.current == "en") "en" else "zh-CN"

/**
 * 插件管理页：已安装插件列表（启用/禁用/卸载）+ 在线插件市场 + 从 URL 安装。
 * 插件自带 HTML 页面在桌面端降级为提示，其余能力（技能/工具/提示词）均可用。
 */
@Composable
internal fun PluginSection(
    manager: PluginManager = AppGraph.pluginManager,
    indexClient: PluginIndexClient = AppGraph.pluginIndexClient
) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var market by remember { mutableStateOf<List<PluginIndexPlugin>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var showInstallUrl by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    fun refresh() {
        scope.launch {
            loading = true
            manager.refresh()
            installed = manager.plugins.value
            runCatching {
                market = indexClient.fetchIndex(PluginIndexDefaults.official).index.plugins
            }.onFailure { message = it.message ?: Strings.plugin_web_page_failed }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        SettingsGroupHeader(text = Strings.plugin_installed_section)
        SettingsGroup {
            if (installed.isEmpty() && !loading) {
                SettingsRow(title = Strings.plugin_empty)
            } else {
                installed.forEachIndexed { idx, plugin ->
                    PluginRow(
                        plugin = plugin,
                        onToggle = {
                            scope.launch {
                                manager.setEnabled(plugin.id, !plugin.record.enabled)
                                refresh()
                            }
                        },
                        onUninstall = {
                            scope.launch {
                                manager.uninstall(plugin.id)
                                refresh()
                            }
                        }
                    )
                    if (idx != installed.lastIndex) SettingsDivider()
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedButton(onClick = { refresh() }) { Text(Strings.plugin_store_refresh) }
            OutlinedButton(onClick = { showInstallUrl = !showInstallUrl }) {
                Text(Strings.plugin_install_url)
            }
        }

        if (showInstallUrl) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                com.vantacode.core.ui.AppTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = Strings.plugin_search_hint,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = {
                        if (url.isBlank()) return@TextButton
                        scope.launch {
                            val result = runCatching {
                                val bytes = java.net.URL(url).readBytes()
                                val preview = manager.preview(java.io.ByteArrayInputStream(bytes))
                                when (preview) {
                                    is com.vantacode.feature.plugin.domain.PluginPreviewResult.Ready -> {
                                        val r = manager.install(preview.preview.token)
                                        message = when (r) {
                                            is PluginInstallResult.Installed ->
                                                Strings.formatted(Strings.plugin_store_install_success, r.pluginId)
                                            is PluginInstallResult.Failed -> r.error.detail.ifBlank { Strings.plugin_runtime_failed }
                                        }
                                    }
                                    is com.vantacode.feature.plugin.domain.PluginPreviewResult.Failed ->
                                        message = preview.errors.firstOrNull()?.detail ?: Strings.plugin_runtime_failed
                                }
                                refresh()
                            }.onFailure { message = it.message ?: Strings.plugin_runtime_failed }
                        }
                    }
                ) { Text(Strings.plugin_install) }
            }
        }

        if (message != null) {
            Text(
                text = message!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.sm)
            )
        }

        SettingsGroupHeader(text = Strings.plugin_store_title)
        SettingsGroup {
            if (market.isEmpty() && !loading) {
                SettingsRow(title = Strings.plugin_empty)
            } else {
                market.forEachIndexed { idx, p ->
                    SettingsRow(
                        icon = null,
                        title = p.name.resolve(lang),
                        subtitle = p.description.resolve(lang),
                        trailing = {
                            val installedIds = installed.map { it.id }
                            if (p.id in installedIds) {
                                Text(Strings.plugin_installed, style = MaterialTheme.typography.bodySmall)
                            } else {
                                TextButton(onClick = {
                                    scope.launch {
                                        runCatching {
                                            val asset = indexClient.resolveRelease(p)
                                            val cached = indexClient.download(asset, indexClient.rankCandidates(asset))
                                            val preview = manager.preview(java.io.FileInputStream(cached.file))
                                            when (preview) {
                                                is com.vantacode.feature.plugin.domain.PluginPreviewResult.Ready -> {
                                                    val r = manager.install(preview.preview.token)
                                                    message = when (r) {
                                                        is PluginInstallResult.Installed ->
                                                            Strings.formatted(Strings.plugin_store_install_success, r.pluginId)
                                                        is PluginInstallResult.Failed -> r.error.detail.ifBlank { Strings.plugin_runtime_failed }
                                                    }
                                                }
                                                is com.vantacode.feature.plugin.domain.PluginPreviewResult.Failed ->
                                                    message = preview.errors.firstOrNull()?.detail ?: Strings.plugin_runtime_failed
                                            }
                                            refresh()
                                        }.onFailure { message = it.message ?: Strings.plugin_runtime_failed }
                                    }
                                }) { Text(Strings.plugin_install) }
                            }
                        }
                    )
                    if (idx != market.lastIndex) SettingsDivider()
                }
            }
        }

        Text(
            text = Strings.plugin_page_unsupported,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    onToggle: () -> Unit,
    onUninstall: () -> Unit
) {
    val name = plugin.manifest?.name?.resolve(lang) ?: plugin.id
    val desc = plugin.manifest?.description?.resolve(lang).orEmpty()
    SettingsRow(
        icon = null,
        title = name,
        subtitle = listOf(desc, plugin.id).filter { it.isNotBlank() }.joinToString(" · "),
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle) {
                    Text(if (plugin.record.enabled) Strings.plugin_disable else Strings.plugin_enable)
                }
                TextButton(onClick = onUninstall) { Text(Strings.plugin_delete_title) }
            }
        }
    )
}
