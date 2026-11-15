package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vantacode.feature.settings.data.repository.MarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 插件管理页面：查看已安装的 DSH 插件、卸载。
 * 独立入口，与 DSH 市场分开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginManagerScreen(
    repository: MarketRepository,
    onBack: () -> Unit
) {
    var installedPlugins by remember { mutableStateOf<List<com.vantacode.feature.settings.data.repository.DshGoPlugin>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        installedPlugins = withContext(Dispatchers.IO) {
            try { repository.listDshGoPlugins() } catch (_: Exception) { emptyList() }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("插件管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (installedPlugins.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("暂无已安装插件", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(installedPlugins) { plugin ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plugin.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                if (plugin.description.isNotBlank()) {
                                    Text(
                                        text = plugin.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                if (plugin.author.isNotBlank()) {
                                    Text(
                                        text = "作者: ${plugin.author}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        repository.uninstallDshGoPlugin(plugin.name)
                                    }
                                    installedPlugins = withContext(Dispatchers.IO) {
                                        try { repository.listDshGoPlugins() } catch (_: Exception) { emptyList() }
                                    }
                                }
                            }) {
                                Text("卸载", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
