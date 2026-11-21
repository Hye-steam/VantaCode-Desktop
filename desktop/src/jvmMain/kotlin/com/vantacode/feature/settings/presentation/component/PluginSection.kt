package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.vantacode.core.theme.Spacing
import com.vantacode.core.ui.AppTextField
import com.vantacode.core.ui.Strings
import com.vantacode.di.AppGraph
import com.vantacode.feature.plugin.data.index.PluginIndexClient
import com.vantacode.feature.plugin.data.index.PluginIndexDefaults
import com.vantacode.feature.plugin.data.index.PluginIndexPlugin
import com.vantacode.feature.plugin.domain.InstalledPlugin
import com.vantacode.feature.plugin.domain.PluginInstallResult
import com.vantacode.feature.plugin.domain.PluginManager
import com.vantacode.feature.plugin.domain.PluginPreviewResult
import kotlinx.coroutines.launch

private val lang: String get() = if (Strings.current == "en") "en" else "zh-CN"

/** 已安装版本是否低于仓库最新版本；任一方无法解析时返回 null（不提示更新）。 */
private fun updateVersion(installed: String?, latest: String?): String? {
    if (installed == null || latest == null) return null
    val a = com.vantacode.feature.plugin.domain.SemVer.parse(installed) ?: return null
    val b = com.vantacode.feature.plugin.domain.SemVer.parse(latest) ?: return null
    return if (b > a) latest else null
}

/**
 * 插件管理页：已安装插件列表（点击查看面板）+ 在线插件市场 + 从 URL 安装。
 */
@Composable
internal fun PluginSection(
    manager: PluginManager = AppGraph.pluginManager,
    indexClient: PluginIndexClient = AppGraph.pluginIndexClient
) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var market by remember { mutableStateOf<List<PluginIndexPlugin>>(emptyList()) }
    // 在线索引里每个插件的最新版本，用于「可更新」提示
    var latestVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var showInstallUrl by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    // 当前打开的插件详情（null 表示列表页）
    var detail by remember { mutableStateOf<InstalledPlugin?>(null) }

    fun refresh() {
        scope.launch {
            loading = true
            manager.refresh()
            installed = manager.plugins.value
            // 详情对象也刷新，保证启停后状态同步
            detail = detail?.let { d -> installed.firstOrNull { it.id == d.id } }
            runCatching {
                val plugins = indexClient.fetchIndex(PluginIndexDefaults.official).index.plugins
                market = plugins
                latestVersions = plugins.mapNotNull { p -> p.version?.let { p.id to it } }.toMap()
            }.onFailure { message = it.message ?: Strings.plugin_web_page_failed }
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    if (detail != null) {
        PluginDetailSection(
            plugin = detail!!,
            manager = manager,
            indexClient = indexClient,
            market = market,
            latestVersion = latestVersions[detail!!.id],
            onBack = { detail = null },
            onChanged = { refresh() }
        )
        return
    }

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
                    SettingsRow(
                        icon = null,
                        title = plugin.manifest?.name?.resolve(lang) ?: plugin.id,
                        subtitle = listOf(
                            plugin.manifest?.description?.resolve(lang).orEmpty(),
                            plugin.id,
                            "v${plugin.manifest?.version ?: "?"}"
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        onClick = { detail = plugin },
                        trailing = {
                            val upd = updateVersion(plugin.manifest?.version?.toString(), latestVersions[plugin.id])
                            val marketItem = market.firstOrNull { it.id == plugin.id }
                            when {
                                upd != null && marketItem != null -> {
                                    TextButton(onClick = {
                                        scope.launch {
                                            message = installFromIndex(manager, indexClient, marketItem!!)
                                            refresh()
                                        }
                                    }) { Text(Strings.formatted(Strings.plugin_update_available, upd)) }
                                }
                                else -> Text(
                                    text = if (plugin.record.enabled) Strings.plugin_disable else Strings.plugin_enable,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                AppTextField(
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
                                installPreview(manager, java.io.ByteArrayInputStream(bytes))
                            }.onFailure { message = it.message ?: Strings.plugin_runtime_failed }
                            message = result.getOrNull()
                            refresh()
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
                            if (p.id in installed.map { it.id }) {
                                Text(Strings.plugin_installed, style = MaterialTheme.typography.bodySmall)
                            } else {
                                TextButton(onClick = {
                                    scope.launch {
                                        message = installFromIndex(manager, indexClient, p)
                                        refresh()
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

/** 从在线索引安装/更新一个插件，返回给 UI 展示的消息。 */
private suspend fun installFromIndex(
    manager: PluginManager,
    indexClient: PluginIndexClient,
    plugin: PluginIndexPlugin
): String = runCatching {
    val asset = indexClient.resolveRelease(plugin)
    val cached = indexClient.download(asset, indexClient.rankCandidates(asset))
    installPreview(manager, java.io.FileInputStream(cached.file))
}.getOrElse { it.message ?: Strings.plugin_runtime_failed }

/** 安装预览 + 提交，返回给 UI 展示的消息。 */
private suspend fun installPreview(manager: PluginManager, input: java.io.InputStream): String =
    when (val preview = manager.preview(input)) {
        is PluginPreviewResult.Ready -> when (val r = manager.install(preview.preview.token)) {
            is PluginInstallResult.Installed ->
                Strings.formatted(Strings.plugin_store_install_success, r.pluginId)
            is PluginInstallResult.Failed -> r.error.detail.ifBlank { Strings.plugin_runtime_failed }
        }
        is PluginPreviewResult.Failed -> preview.errors.firstOrNull()?.detail ?: Strings.plugin_runtime_failed
    }

/** 插件详情面板：原生信息 + 贡献列表 + 配置/工具页面入口。 */
@Composable
private fun PluginDetailSection(
    plugin: InstalledPlugin,
    manager: PluginManager,
    indexClient: PluginIndexClient,
    market: List<PluginIndexPlugin>,
    latestVersion: String?,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val updateTo = updateVersion(plugin.manifest?.version?.toString(), latestVersion)
    val marketItem = market.firstOrNull { it.id == plugin.id }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf<com.vantacode.feature.plugin.domain.PluginPage?>(null) }

    if (page != null) {
        PluginPageHostDesktop(page = page!!, onBack = { page = null })
        return
    }

    val manifest = plugin.manifest
    val content = plugin.content
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        TextButton(onClick = onBack) { Text(Strings.common_back) }
        Text(
            text = manifest?.name?.resolve(lang) ?: plugin.id,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )
        SettingsGroup {
            SettingsRow(
                title = Strings.about_version,
                subtitle = plugin.manifest?.version?.toString() ?: "?",
                trailing = {
                    if (updateTo != null && marketItem != null) {
                        TextButton(onClick = {
                            scope.launch {
                                com.vantacode.core.platform.PlatformToast.show(
                                    installFromIndex(manager, indexClient, marketItem)
                                )
                                onChanged()
                            }
                        }) { Text(Strings.formatted(Strings.plugin_update_available, updateTo)) }
                    } else if (latestVersion != null) {
                        Text(Strings.plugin_up_to_date, style = MaterialTheme.typography.bodySmall)
                    }
                }
            )
            SettingsDivider()
            SettingsRow(
                title = Strings.formatted(Strings.plugin_by, plugin.manifest?.publisher ?: ""),
                subtitle = plugin.id
            )
            SettingsDivider()
            SettingsRow(
                title = if (plugin.record.enabled) Strings.plugin_disable else Strings.plugin_enable,
                subtitle = "当前：${if (plugin.record.enabled) Strings.plugin_enable else Strings.plugin_disable}",
                onClick = {
                    scope.launch { manager.setEnabled(plugin.id, !plugin.record.enabled); onChanged() }
                }
            )
            SettingsDivider()
            SettingsRow(
                title = Strings.plugin_delete_title,
                onClick = {
                    scope.launch { manager.uninstall(plugin.id); onBack(); onChanged() }
                }
            )
        }

        manifest?.description?.resolve(lang)?.takeIf { it.isNotBlank() }?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md)
            )
        }

        // 页面入口：配置页 + 工作区工具页 + 槽位 PAGE 入口
        val pages = buildList {
            content.configPage?.let { add(it) }
            content.workspaceTools.forEach {
                add(com.vantacode.feature.plugin.domain.PluginPage(
                    pluginId = it.pluginId, localId = it.localId, title = it.title,
                    slot = null, pageFile = it.pageFile, icon = it.icon,
                    installDir = it.installDir, pluginVersion = it.pluginVersion
                ))
            }
            content.uiEntries.mapNotNull { it.page }.let { addAll(it) }
        }
        if (pages.isNotEmpty()) {
            SettingsGroupHeader(text = Strings.plugin_detail_title)
            SettingsGroup {
                pages.forEachIndexed { idx, p ->
                    SettingsRow(
                        icon = null,
                        title = p.title.resolve(lang),
                        subtitle = p.pageFile,
                        onClick = { page = p }
                    )
                    if (idx != pages.lastIndex) SettingsDivider()
                }
            }
        }

        // 贡献概览
        SettingsGroupHeader(text = Strings.plugin_market_section)
        SettingsGroup {
            val rows = buildList {
                if (content.skills.isNotEmpty()) add("技能" to "${content.skills.size}")
                if (content.tools.isNotEmpty()) add("AI 工具" to "${content.tools.size}")
                if (content.hooks.isNotEmpty()) add("Hook" to "${content.hooks.size}")
                if (content.agents.isNotEmpty()) add("子代理" to "${content.agents.size}")
                if (content.mcp.isNotEmpty()) add("MCP 服务" to "${content.mcp.size}")
                if (content.providers.isNotEmpty()) add("模型供应商" to "${content.providers.size}")
                if (content.workspaceTools.isNotEmpty()) add("工作区工具" to "${content.workspaceTools.size}")
            }
            if (rows.isEmpty()) {
                SettingsRow(title = Strings.plugin_empty)
            } else {
                rows.forEachIndexed { idx, (k, v) ->
                    SettingsRow(title = k, subtitle = v)
                    if (idx != rows.lastIndex) SettingsDivider()
                }
            }
        }
    }
}
