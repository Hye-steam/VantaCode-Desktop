package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vantacode.core.dsh.DshRuntime
import com.vantacode.core.dsh.MarketPlugin
import com.vantacode.core.theme.Spacing
import com.vantacode.core.theme.semanticColors
import com.vantacode.core.ui.AppTextField
import com.vantacode.core.ui.Strings
import compose.icons.FeatherIcons
import compose.icons.feathericons.Box
import compose.icons.feathericons.Check
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.ExternalLink
import compose.icons.feathericons.Package
import compose.icons.feathericons.Search
import kotlinx.coroutines.launch

private sealed interface MarketUiState {
    data object Loading : MarketUiState
    data class Ready(val plugins: List<MarketPlugin>) : MarketUiState
    data object Error : MarketUiState
}

/**
 * 设置 →「DSH 插件」：安装/管理托管式 DeepSeek Harness 运行时，
 * 安装或卸载 DSH 插件，浏览插件市场，并打开 DSH 自带的模型配置页。
 *
 * 所有重操作（运行时安装、插件增删、市场拉取）都在协程里跑，UI 内联展示进度与结果。
 */
@Composable
internal fun DshSettingsSection() {
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf(DshRuntime.status()) }
    var plugins by remember { mutableStateOf(DshRuntime.listPlugins()) }
    var marketState by remember { mutableStateOf<MarketUiState>(MarketUiState.Loading) }
    var marketQuery by remember { mutableStateOf("") }
    var addSpec by remember { mutableStateOf("") }
    var busyWhat by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmUninstallRuntime by remember { mutableStateOf(false) }

    fun refresh() {
        status = DshRuntime.status()
        plugins = DshRuntime.listPlugins()
    }

    fun installRuntime() {
        if (busyWhat != null) return
        busyWhat = "runtime"
        message = null
        scope.launch {
            val result = runCatching { DshRuntime.install { } }
            refresh()
            busyWhat = null
            message = result.fold(
                onSuccess = { null },
                onFailure = { Strings.formatted(Strings.dsh_install_failed, it.message ?: it.javaClass.simpleName) }
            )
        }
    }

    fun uninstallRuntime() {
        DshRuntime.uninstallRuntime()
        refresh()
        message = Strings.dsh_uninstalled
    }

    fun pluginAction(add: Boolean, spec: String) {
        val trimmed = spec.trim()
        if (trimmed.isEmpty() || busyWhat != null) return
        busyWhat = "plugin:$trimmed"
        message = null
        scope.launch {
            val result = if (add) DshRuntime.installPlugin(trimmed) else DshRuntime.removePlugin(trimmed)
            refresh()
            busyWhat = null
            if (add) addSpec = ""
            message = result.fold(
                onSuccess = { null },
                onFailure = { Strings.formatted(Strings.dsh_plugin_install_failed, it.message ?: "?") }
            )
        }
    }

    fun loadMarket(force: Boolean = false) {
        scope.launch {
            marketState = MarketUiState.Loading
            val result = DshRuntime.fetchMarket(if (force) 0L else 24 * 3600 * 1000L)
            marketState = result.fold(
                onSuccess = { MarketUiState.Ready(it.plugins) },
                onFailure = { MarketUiState.Error }
            )
        }
    }

    LaunchedEffect(Unit) {
        refresh()
        loadMarket()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = Strings.dsh_intro,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)
        )

        // ── 运行时 ──
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Cpu,
                title = "Node.js",
                subtitle = status.node?.let { info ->
                    val version = info.version ?: "v20+"
                    if (info.bundled) Strings.formatted(Strings.dsh_node_bundled, version)
                    else Strings.formatted(Strings.dsh_node_system, version)
                } ?: Strings.dsh_node_missing,
                trailing = {
                    if (status.node == null) {
                        TextButton(onClick = {
                            if (busyWhat == null) {
                                busyWhat = "node"
                                scope.launch {
                                    runCatching { com.vantacode.core.dsh.NodeRuntime.ensure { } }
                                    refresh()
                                    busyWhat = null
                                }
                            }
                        }) {
                            Text(Strings.dsh_install_node)
                        }
                    } else {
                        Icon(
                            FeatherIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.semanticColors.success,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Box,
                title = "DSH",
                subtitle = status.dshVersion?.let { Strings.formatted(Strings.dsh_runtime_installed, it) }
                    ?: Strings.dsh_runtime_missing,
                trailing = {
                    if (status.dshInstalled) {
                        TextButton(onClick = { if (busyWhat == null) installRuntime() }) {
                            Text(Strings.dsh_update_runtime)
                        }
                    } else {
                        TextButton(onClick = { installRuntime() }) {
                            Text(Strings.dsh_install_runtime)
                        }
                    }
                }
            )
            if (status.dshInstalled) {
                SettingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { confirmUninstallRuntime = true }) {
                        Text(Strings.dsh_uninstall_runtime, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        busyWhat?.let {
            Text(
                text = Strings.dsh_installing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }
        message?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs)
            )
        }

        // ── 已安装插件 ──
        SettingsGroupHeader(text = Strings.dsh_plugins_title)
        SettingsGroup {
            if (plugins.isEmpty()) {
                Text(
                    text = Strings.dsh_plugins_empty,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
                )
            } else {
                plugins.forEachIndexed { index, plugin ->
                    if (index > 0) SettingsDivider()
                    SettingsRow(
                        icon = FeatherIcons.Package,
                        title = plugin.name,
                        subtitle = plugin.version,
                        trailing = {
                            TextButton(onClick = { pluginAction(false, plugin.name) }) {
                                Text(Strings.dsh_plugin_remove, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                }
            }
        }

        // ── 添加插件 ──
        SettingsGroupHeader(text = Strings.dsh_add_title)
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = Strings.dsh_add_hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    AppTextField(
                        value = addSpec,
                        onValueChange = { addSpec = it },
                        modifier = Modifier.weight(1f),
                        placeholder = Strings.dsh_add_input,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    TextButton(
                        onClick = { pluginAction(true, addSpec) },
                        enabled = addSpec.isNotBlank() && busyWhat == null
                    ) { Text(Strings.dsh_add_button) }
                }
            }
        }

        // ── 市场 ──
        SettingsGroupHeader(text = Strings.dsh_market_title)
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                AppTextField(
                    value = marketQuery,
                    onValueChange = { marketQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = Strings.dsh_market_search,
                    singleLine = true,
                    leadingIcon = {
                        Icon(FeatherIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
                when (val s = marketState) {
                    MarketUiState.Loading -> Text(
                        Strings.dsh_market_loading,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MarketUiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Strings.dsh_market_error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { loadMarket(force = true) }) { Text(Strings.dsh_market_retry) }
                    }
                    is MarketUiState.Ready -> {
                        val current = Strings.current
                        val query = marketQuery.trim().lowercase()
                        val filtered = s.plugins.asSequence()
                            .filter { plugin ->
                                if (query.isEmpty()) true
                                else listOf(
                                    plugin.name, plugin.author.orEmpty(),
                                    plugin.localizedTitle(current), plugin.localizedDescription(current)
                                ).any { it.lowercase().contains(query) }
                            }
                            .sortedByDescending { it.stars }
                            .take(80)
                            .toList()
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            items(filtered, key = { it.name }) { plugin ->
                                MarketPluginRow(
                                    plugin = plugin,
                                    current = current,
                                    onInstall = { pluginAction(true, plugin.name) },
                                    installing = busyWhat == "plugin:${plugin.name}"
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 模型配置 ──
        SettingsGroupHeader(text = Strings.dsh_config_title)
        SettingsGroup {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text(
                    text = Strings.dsh_config_body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    DshRuntime.spawnWebDetached().onFailure { err ->
                        message = Strings.formatted(Strings.dsh_open_config_failed, err.message ?: "?")
                    }
                }) {
                    Icon(FeatherIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(Strings.dsh_open_config, modifier = Modifier.padding(start = Spacing.xs))
                }
            }
        }

        Column(modifier = Modifier.heightIn(min = 48.dp)) {}
    }

    if (confirmUninstallRuntime) {
        AlertDialog(
            onDismissRequest = { confirmUninstallRuntime = false },
            title = { Text(Strings.dsh_uninstall_runtime) },
            text = { Text(Strings.dsh_intro) },
            confirmButton = {
                TextButton(onClick = { confirmUninstallRuntime = false; uninstallRuntime() }) {
                    Text(Strings.dsh_uninstall_runtime, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUninstallRuntime = false }) { Text(Strings.common_cancel) }
            }
        )
    }
}

@Composable
private fun MarketPluginRow(
    plugin: MarketPlugin,
    current: String,
    onInstall: () -> Unit,
    installing: Boolean
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.semanticColors.pageBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.localizedTitle(current),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                plugin.localizedDescription(current).takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    plugin.author?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "@$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (plugin.stars > 0) {
                        Text(
                            "★ ${plugin.stars}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        plugin.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(onClick = onInstall, enabled = !installing) {
                Text(if (installing) "…" else Strings.dsh_market_install)
            }
        }
    }
}
