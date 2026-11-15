package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.core.theme.Spacing
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import com.vantacode.feature.settings.data.repository.*

/**
 * DSH 插件市场（在线）：真实 DSH 目录，搜索 + 分类 + 安装命令。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DshMarketScreen(
    repository: MarketRepository,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<DshCatalogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var installMsg by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    var installedPlugins by remember { mutableStateOf<List<com.vantacode.feature.settings.data.repository.DshGoPlugin>>(emptyList()) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        entries = repository.loadDshCatalog()
        isLoading = false
    }

    LaunchedEffect(refreshTrigger) {
        installedPlugins = withContext(kotlinx.coroutines.Dispatchers.IO) {
            try { repository.listDshGoPlugins() } catch (_: Exception) { emptyList() }
        }
    }

    val categories = remember(entries) {
        entries.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered = remember(searchQuery, selectedCategory, entries) {
        repository.searchDsh(
            repository.filterByCategory(entries, selectedCategory),
            searchQuery
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plugin_market_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 搜索栏
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.market_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 分类标签
            if (categories.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory.isEmpty(),
                            onClick = { selectedCategory = "" },
                            label = { Text("全部", style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = if (selectedCategory == cat) "" else cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // 统计
            Text(
                text = "${filtered.size} 个插件" + if (entries.isNotEmpty()) "（共 ${entries.size}）" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            )

            // 安装提示
            installMsg?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { installMsg = null }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xl)
                ) {
                    items(filtered.take(200)) { entry ->
                        DshEntryCard(
                            entry = entry,
                            onInstall = {
                                if (entry.repoUrl.isNotBlank()) {
                                    kotlinx.coroutines.MainScope().launch {
                                        installMsg = "正在安装 ${entry.displayName}..."
                                        when (val r = repository.installDshPluginFromGithub(entry.repoUrl)) {
                                            is com.vantacode.feature.settings.data.repository.DshPluginInstallResult.Success -> {
                                                installMsg = "安装成功: ${r.items.joinToString(" / ")}"
                                            }
                                            is com.vantacode.feature.settings.data.repository.DshPluginInstallResult.Failure -> {
                                                installMsg = r.reason
                                            }
                                        }
                                    }
                                } else {
                                    val cmd = repository.dshInstallCommand(entry)
                                    clipboardManager.setText(AnnotatedString(cmd))
                                    installMsg = "安装命令已复制: $cmd"
                                }
                            }
                        )
                    }
                    if (filtered.size > 200) {
                        item {
                            Text(
                                text = "仅显示前 200 条，请用搜索缩小范围",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(Spacing.lg)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 技能市场（在线）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillMarketOnlineScreen(
    repository: MarketRepository,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<MarketEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var installMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        entries = repository.loadSkillRegistry("")
        isLoading = false
    }

    val filtered = remember(searchQuery, entries) {
        repository.searchSkills(entries, searchQuery)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skill_market_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.market_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            installMsg?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = { installMsg = null }) { Text(stringResource(R.string.ok)) }
                    }
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xl)
                ) {
                    items(filtered) { entry ->
                        SkillEntryCard(
                            entry = entry,
                            onInstall = {
                                kotlinx.coroutines.MainScope().launch {
                                    installMsg = "正在安装 ${entry.displayName.ifBlank { entry.name }}..."
                                    val url = entry.downloadUrl
                                    if (url.isBlank()) {
                                        installMsg = "没有下载地址"
                                        return@launch
                                    }
                                    when (val r = repository.installFromUrl(url)) {
                                        is SkillInstallResult.Success -> {
                                            installMsg = "安装成功: ${r.skillNames.joinToString()}"
                                        }
                                        is SkillInstallResult.Failure -> {
                                            installMsg = r.reason
                                        }
                                    }
                                }
                            }
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = Spacing.xl), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.market_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── 卡片 ───

@Composable
private fun DshEntryCard(
    entry: DshCatalogEntry,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.descriptionZh.isNotBlank()) {
                    Text(
                        text = entry.descriptionZh,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val meta = buildList {
                    // DSH Go 插件类型标识
                    val typeIcon = when {
                        entry.name.contains("theme") || entry.category == "theme" -> "[主题] "
                        entry.name.contains("pet") || entry.name.contains("桌宠") -> "[桌宠] "
                        entry.name.contains("background") || entry.name.contains("wallpaper") -> "[背景] "
                        entry.name.contains("dshgo") || entry.name.contains("dsh-go") -> "[DSHGo] "
                        else -> ""
                    }
                    add(typeIcon + entry.category)
                    if (entry.latestVersion.isNotBlank()) add("v${entry.latestVersion}")
                    if (entry.repoUrl.isNotBlank()) {
                        add(entry.repoUrl.removePrefix("https://github.com/"))
                    }
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalButton(onClick = onInstall) {
                Text(stringResource(R.string.market_install), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SkillEntryCard(
    entry: MarketEntry,
    onInstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.displayName.ifBlank { entry.name }.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.displayName.ifBlank { entry.name },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.description.isNotBlank()) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val meta = buildList {
                    if (entry.author.isNotBlank()) add(entry.author)
                    if (entry.stars > 0) add("★ ${entry.stars}")
                    if (entry.version.isNotBlank()) add("v${entry.version}")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalButton(onClick = onInstall) {
                Text(stringResource(R.string.market_install), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
