package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.core.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope
import com.vantacode.feature.settings.data.repository.InstalledPlugin
import com.vantacode.feature.settings.data.repository.PluginInstallResult
import com.vantacode.feature.settings.data.repository.PluginManifest
import com.vantacode.feature.settings.data.repository.PluginRepository

/**
 * 插件市场页面：浏览可安装插件、从 URL 安装、管理已安装插件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginMarketScreen(
    repository: PluginRepository,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var urlInput by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var installResult by remember { mutableStateOf<String?>(null) }

    val builtinPlugins = remember { repository.loadBuiltinPlugins() }
    var installedPlugins by remember { mutableStateOf(repository.listInstalled()) }

    // 过滤
    val filtered = builtinPlugins.filter {
        searchQuery.isBlank() ||
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.description.contains(searchQuery, ignoreCase = true) ||
            it.tags.any { tag -> tag.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugin_market_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { showUrlDialog = true }) {
                        Text(stringResource(R.string.plugin_install_url))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.plugin_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 安装结果提示
            installResult?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { installResult = null }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xl)
            ) {
                // 可安装插件
                if (filtered.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.plugin_market_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.sm
                            )
                        )
                    }
                    items(filtered) { entry ->
                        PluginCard(
                            entry = entry,
                            isInstalled = installedPlugins.any { it.manifest.name == entry.name },
                            onInstall = {
                                kotlinx.coroutines.MainScope().launch {
                                    when (val r = repository.installBuiltin(entry.name)) {
                                        is PluginInstallResult.Success -> {
                                            installResult = "安装成功: ${r.plugin.manifest.displayName}"
                                            installedPlugins = repository.listInstalled()
                                        }
                                        is PluginInstallResult.Failure -> installResult = r.reason
                                    }
                                }
                            }
                        )
                    }
                }

                // 已安装插件
                if (installedPlugins.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.plugin_installed_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.sm
                            )
                        )
                    }
                    items(installedPlugins) { plugin ->
                        InstalledPluginCard(
                            plugin = plugin,
                            onToggleEnabled = {
                                val newState = !plugin.enabled
                                // 同步更新本地列表
                                installedPlugins = installedPlugins.map {
                                    if (it.manifest.name == plugin.manifest.name) it.copy(enabled = newState) else it
                                }
                                // 异步保存
                                kotlinx.coroutines.MainScope().launch {
                                    repository.setEnabled(plugin.manifest.name, newState)
                                }
                            },
                            onDelete = { showDeleteDialog = plugin.manifest.name }
                        )
                    }
                }

                // 空状态
                if (filtered.isEmpty() && installedPlugins.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.plugin_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // URL 安装弹窗
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text(stringResource(R.string.plugin_install_url)) },
            text = {
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = { Text("https://github.com/user/repo/archive/main.zip") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUrlDialog = false
                        val url = urlInput.trim()
                        if (url.isNotBlank()) {
                            kotlinx.coroutines.MainScope().launch {
                                when (val result = repository.installFromUrl(url)) {
                                    is PluginInstallResult.Success -> {
                                        installResult = "安装成功: ${result.plugin.manifest.displayName}"
                                        installedPlugins = repository.listInstalled()
                                    }
                                    is PluginInstallResult.Failure -> {
                                        installResult = result.reason
                                    }
                                }
                            }
                        }
                        urlInput = ""
                    }
                ) {
                    Text(stringResource(R.string.plugin_install))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // 删除确认弹窗
    showDeleteDialog?.let { name ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.plugin_delete_title)) },
            text = { Text(stringResource(R.string.plugin_delete_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = null
                        kotlinx.coroutines.MainScope().launch {
                            repository.uninstall(name)
                            installedPlugins = repository.listInstalled()
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PluginCard(
    entry: PluginManifest,
    isInstalled: Boolean,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName.ifBlank { entry.name },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entry.author.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.plugin_by, entry.author),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isInstalled) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.plugin_installed)) }
                    )
                } else {
                    FilledTonalButton(onClick = onInstall) {
                        Text(stringResource(R.string.plugin_install))
                    }
                }
            }
            if (entry.description.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 统计标签
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.padding(top = Spacing.xs)
            ) {
                if (entry.skillCount > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${entry.skillCount} 技能", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (entry.toolCount > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${entry.toolCount} 工具", style = MaterialTheme.typography.labelSmall) }
                    )
                }
                if (entry.mcpCount > 0) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("${entry.mcpCount} MCP", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InstalledPluginCard(
    plugin: InstalledPlugin,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = plugin.manifest.displayName.ifBlank { plugin.manifest.name },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "v${plugin.manifest.version} · ${plugin.manifest.skillCount} 技能",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onToggleEnabled) {
                Text(
                    if (plugin.enabled) stringResource(R.string.plugin_disable)
                    else stringResource(R.string.plugin_enable),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
