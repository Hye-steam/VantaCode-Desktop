package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
internal fun SkillMarketOnlineScreen(
    repository: MarketRepository,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<MarketEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var installMsg by remember { mutableStateOf<String?>(null) }
    var sortByStars by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableStateOf(0) }
    var page by remember { mutableStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 关键词变了就重新查在线接口；空关键词走注册表/话题浏览。输入停顿 450ms 再发请求，省配额。
    LaunchedEffect(searchQuery, reloadKey) {
        if (searchQuery.isNotBlank()) kotlinx.coroutines.delay(450)
        isLoading = true
        page = 1
        entries = if (searchQuery.isBlank()) {
            repository.loadSkillRegistry()
        } else {
            repository.searchSkillsOnline(searchQuery, page = 1)
        }
        hasMore = entries.isNotEmpty()
        isLoading = false
    }

    val filtered = remember(searchQuery, entries, sortByStars) {
        val matched = repository.searchSkills(entries, searchQuery)
        if (sortByStars) matched.sortedByDescending { it.stars } else matched.sortedBy { it.name }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skill_market_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
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

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.market_total_skills, entries.size) +
                        (if (searchQuery.isNotBlank()) context.getString(R.string.market_matched, filtered.size) else ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { sortByStars = !sortByStars }) {
                    Text(
                        text = if (sortByStars) stringResource(R.string.market_sort_by_stars) else stringResource(R.string.market_sort_by_name),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

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
                    if (hasMore && !isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(Spacing.md), contentAlignment = Alignment.Center) {
                                TextButton(
                                    enabled = !loadingMore,
                                    onClick = {
                                        scope.launch {
                                            loadingMore = true
                                            val next = page + 1
                                            val more = repository.searchSkillsOnline(searchQuery, page = next)
                                            if (more.isNotEmpty()) {
                                                page = next
                                                entries = entries + more
                                            }
                                            hasMore = more.isNotEmpty()
                                            loadingMore = false
                                        }
                                    }
                                ) {
                                    Text(if (loadingMore) stringResource(R.string.market_loading_more) else stringResource(R.string.market_load_more))
                                }
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
private fun SkillEntryCard(
    entry: MarketEntry,
    onInstall: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.description.isNotBlank()) {
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val meta = buildList {
                        if (entry.author.isNotBlank()) add(entry.author)
                        if (entry.stars > 0) add("★ ${entry.stars}")
                        if (entry.version.isNotBlank()) add("v${entry.version}")
                        if (entry.category.isNotBlank()) add(entry.category)
                    }.joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                FilledTonalButton(onClick = onInstall) {
                    Text(stringResource(R.string.market_install), style = MaterialTheme.typography.labelMedium)
                }
            }

            if (!expanded) {
                Text(
                    text = stringResource(R.string.market_expand_detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = Spacing.xs)
                )
            } else {
                HorizontalDivider(Modifier.padding(vertical = Spacing.sm))
                SkillDetailRow(stringResource(R.string.skill_detail_name), entry.name)
                if (entry.displayName.isNotBlank()) SkillDetailRow(stringResource(R.string.skill_detail_display_name), entry.displayName)
                if (entry.author.isNotBlank()) SkillDetailRow(stringResource(R.string.skill_detail_author), entry.author)
                if (entry.stars > 0) SkillDetailRow(stringResource(R.string.skill_detail_stars), entry.stars.toString())
                if (entry.version.isNotBlank()) SkillDetailRow(stringResource(R.string.skill_detail_version), entry.version)
                if (entry.category.isNotBlank()) SkillDetailRow(stringResource(R.string.skill_detail_category), entry.category)
                if (entry.packageName.isNotBlank()) SkillDetailRow(stringResource(R.string.skill_detail_package), entry.packageName)
                if (entry.tags.isNotEmpty()) SkillDetailRow(stringResource(R.string.skill_detail_tags), entry.tags.joinToString("、"))
                if (entry.downloadUrl.isNotBlank()) SkillDetailRow(stringResource(R.string.skill_detail_download), entry.downloadUrl)
                Text(
                    text = stringResource(R.string.skill_install_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.sm)
                )
            }
        }
    }
}

/** 技能详情里的「标签 — 值」行。 */
@Composable
private fun SkillDetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f)
        )
    }
}
