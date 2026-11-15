package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.core.theme.Spacing
import com.vantacode.feature.settings.data.repository.SkillMarketEntry
import com.vantacode.feature.settings.presentation.InstallState
import com.vantacode.feature.settings.presentation.SkillMarketViewModel

/**
 * 技能市场页面：浏览远程注册表、搜索、从 URL 安装、管理本地已安装技能。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillMarketScreen(
    viewModel: SkillMarketViewModel,
    onBack: () -> Unit
) {
    val marketSkills by viewModel.marketSkills.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val installState by viewModel.installState.collectAsState()
    val installedSkills by viewModel.installedSkills.collectAsState()
    var urlInput by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var showPreview by remember { mutableStateOf<String?>(null) }

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
                    TextButton(onClick = { showUrlDialog = true }) {
                        Text(stringResource(R.string.skill_market_install_url))
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
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text(stringResource(R.string.skill_market_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 安装状态提示
            when (val state = installState) {
                is InstallState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is InstallState.Success -> {
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
                                text = stringResource(R.string.skill_market_install_success, state.names.joinToString()),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearInstallState() }) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    }
                }
                is InstallState.Error -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.md),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.clearInstallState() }) {
                                Text(stringResource(R.string.ok))
                            }
                        }
                    }
                }
                else -> {}
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = Spacing.xl)
            ) {
                // 市场技能
                val filtered = viewModel.filteredSkills()
                if (filtered.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.skill_market_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.sm
                            )
                        )
                    }
                    items(filtered) { entry ->
                        SkillMarketCard(
                            entry = entry,
                            isInstalled = entry.name in installedSkills,
                            onInstall = { viewModel.installFromMarket(entry) }
                        )
                    }
                }

                // 已安装
                if (installedSkills.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.skill_market_installed_section),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = Spacing.lg,
                                vertical = Spacing.sm
                            )
                        )
                    }
                    items(installedSkills) { name ->
                        InstalledSkillCard(
                            name = name,
                            onPreview = { showPreview = name },
                            onDelete = { showDeleteDialog = name }
                        )
                    }
                }

                // 空状态
                if (filtered.isEmpty() && installedSkills.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.skill_market_empty),
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
            title = { Text(stringResource(R.string.skill_market_install_url)) },
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
                        viewModel.installFromUrl(urlInput.trim())
                        showUrlDialog = false
                        urlInput = ""
                    }
                ) {
                    Text(stringResource(R.string.skill_market_install))
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
            title = { Text(stringResource(R.string.skill_market_delete_title)) },
            text = { Text(stringResource(R.string.skill_market_delete_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSkill(name)
                        showDeleteDialog = null
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

    // 预览弹窗
    showPreview?.let { name ->
        val content = viewModel.readSkillContent(name)
        AlertDialog(
            onDismissRequest = { showPreview = null },
            title = { Text(name) },
            text = {
                if (content != null) {
                    Text(
                        text = content.take(2000),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 30,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(stringResource(R.string.skill_market_preview_empty))
                }
            },
            confirmButton = {
                TextButton(onClick = { showPreview = null }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}

@Composable
private fun SkillMarketCard(
    entry: SkillMarketEntry,
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
                        text = entry.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (entry.author.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.skill_market_by, entry.author),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isInstalled) {
                    AssistChip(
                        onClick = {},
                        label = { Text(stringResource(R.string.skill_market_installed)) }
                    )
                } else {
                    FilledTonalIconButton(onClick = onInstall) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = stringResource(R.string.skill_market_install)
                        )
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
            if (entry.tags.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    entry.tags.take(4).forEach { tag ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledSkillCard(
    name: String,
    onPreview: () -> Unit,
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
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            TextButton(onClick = onPreview) {
                Text(stringResource(R.string.skill_market_preview), style = MaterialTheme.typography.labelSmall)
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
