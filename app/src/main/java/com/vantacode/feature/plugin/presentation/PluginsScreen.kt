package com.vantacode.feature.plugin.presentation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vantacode.R
import compose.icons.FeatherIcons
import compose.icons.feathericons.Trash2
import com.vantacode.feature.plugin.domain.InstalledPlugin
import com.vantacode.feature.plugin.domain.PluginStatus
import com.vantacode.feature.plugin.presentation.PluginStoreItem
import androidx.compose.ui.graphics.luminance

/**
 * 插件根页面：列表 / 商店 / 开发三个页签由 [PluginsSection] 自带，
 * 这里只负责详情层级的切换与返回。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PluginsScreen(
    onBack: () -> Unit,
    viewModel: PluginsViewModel = hiltViewModel()
) {
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val devMode by viewModel.devMode.collectAsStateWithLifecycle()
    val devProjects by viewModel.devProjects.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val devRefreshing by viewModel.devRefreshing.collectAsStateWithLifecycle()
    val shizukuGrantedIds by viewModel.shizukuGrantedPluginIds.collectAsStateWithLifecycle()
    val overlayGrantedIds by viewModel.overlayGrantedPluginIds.collectAsStateWithLifecycle()
    val backupGrantedIds by viewModel.backupGrantedPluginIds.collectAsStateWithLifecycle()
    val shizukuState by viewModel.shizukuState.collectAsStateWithLifecycle()
    val dependencyStates by viewModel.dependencyStates.collectAsStateWithLifecycle()
    val indexSourceText by viewModel.indexSourceText.collectAsStateWithLifecycle()
    val downloadMirror by viewModel.downloadMirror.collectAsStateWithLifecycle()

    var detailPluginId by rememberSaveable { mutableStateOf<String?>(null) }
    var devProject by remember { mutableStateOf<PluginDevProject?>(null) }
    var storeItem by remember { mutableStateOf<PluginStoreItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showUninstallConfirm by remember { mutableStateOf(false) }

    val inDetail = detailPluginId != null || devProject != null || storeItem != null
    // 本地导入：绕过商店下载，直接选一个 zip 安装
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::import) }
    BackHandler(enabled = inDetail) {
        when {
            storeItem != null -> storeItem = null
            devProject != null -> devProject = null
            else -> detailPluginId = null
        }
    }

    val title = when {
        storeItem != null -> stringResource(R.string.plugin_detail_title)
        devProject != null -> stringResource(R.string.plugins_dev_section)
        detailPluginId != null -> stringResource(R.string.plugin_detail_title)
        else -> stringResource(R.string.plugins_section_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (!inDetail) onBack() else {
                                storeItem = null
                                devProject = null
                                detailPluginId = null
                            }
                        }
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                actions = {
                    if (detailPluginId != null) {
                        IconButton(onClick = { showUninstallConfirm = true }) {
                            Icon(
                                FeatherIcons.Trash2,
                                contentDescription = stringResource(R.string.plugins_uninstall),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (storeItem == null && devProject == null) {
                        IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.plugins_import)
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings_title)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val store = storeItem
            val dev = devProject
            val detailId = detailPluginId
            when {
                store != null -> PluginStoreDetailSection(item = store, onShowPreview = viewModel::showPreview)
                dev != null -> PluginDevDetailSection(
                    project = dev,
                    plugins = plugins,
                    devRootPath = viewModel.devRootPath,
                    onInstall = { viewModel.installDevProject(dev) }
                )
                detailId != null -> {
                    val plugin = plugins.firstOrNull { it.id == detailId }
                    if (plugin == null) {
                        detailPluginId = null
                    } else {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        val languageTag = currentLanguageTag()
                        PluginDetailSection(
                            plugin = plugin,
                            onToggleEnabled = { viewModel.setEnabled(plugin.id, it) },
                            // 配置页由详情内部内联渲染，必须给 bridgeFactory，否则「提供的内容」里的页面点不开。
                            bridgeFactory = { page, post, onClosePage ->
                                viewModel.createWebBridge(
                                    page = page,
                                    pluginVersion = page.pluginVersion,
                                    languageTag = languageTag,
                                    theme = { if (isDark) "dark" else "light" },
                                    onClosePage = onClosePage,
                                    post = post
                                )
                            },
                            onClosePage = { detailPluginId = null },
                            dependencyState = dependencyStates[plugin.id],
                            onPrepareDependencies = { viewModel.prepareDependencies(plugin.id) },
                            shizukuGranted = shizukuGrantedIds.contains(plugin.id),
                            shizukuState = shizukuState,
                            onSetShizukuGranted = { viewModel.setShizukuGranted(plugin, it) },
                            overlayGranted = overlayGrantedIds.contains(plugin.id),
                            onSetOverlayGranted = { viewModel.setOverlayGranted(plugin, it) },
                            backupGranted = backupGrantedIds.contains(plugin.id),
                            onSetBackupGranted = { viewModel.setBackupGranted(plugin, it) }
                        )
                    }
                }
                else -> PluginsSection(
                    plugins = plugins,
                    loaded = loaded,
                    devMode = devMode,
                    devProjects = devProjects,
                    devRootPath = viewModel.devRootPath,
                    refreshing = refreshing,
                    devRefreshing = devRefreshing,
                    onRefresh = viewModel::refresh,
                    onRefreshDevProjects = viewModel::refreshDevProjects,
                    onOpenDevDetail = { devProject = it },
                    onSetDevEnabled = { project, enabled ->
                        val installed = plugins.firstOrNull { it.id == project.pluginId }
                        when {
                            installed != null && installed.status == PluginStatus.OK ->
                                viewModel.setEnabled(installed.id, enabled)
                            enabled && project.error == null ->
                                viewModel.installDevProject(project)
                        }
                    },
                    onDeleteDevProject = viewModel::deleteDevProject,
                    onOpenDetail = { detailPluginId = it.id },
                    onToggleEnabled = { plugin, enabled -> viewModel.setEnabled(plugin.id, enabled) },
                    onShowStorePreview = viewModel::showPreview,
                    onOpenStoreDetail = { storeItem = it }
                )
            }
        }
    }

    // 安装确认框：商店下载完成与本地导入共用这一个宿主，必须常驻在页面顶层，
    // 否则详情页里点安装只会静默停在「已下载待确认」。
    PluginImportHost(viewModel = viewModel)

    val detailPlugin = detailPluginId?.let { id -> plugins.firstOrNull { it.id == id } }
    if (showUninstallConfirm && detailPlugin != null) {
        AlertDialog(
            onDismissRequest = { showUninstallConfirm = false },
            title = { Text(stringResource(R.string.plugins_uninstall_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.plugins_uninstall_confirm_message,
                        detailPlugin.displayName(currentLanguageTag())
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showUninstallConfirm = false
                    viewModel.uninstall(detailPlugin.id)
                    detailPluginId = null
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showSettings) {
        PluginSettingsSheet(
            devMode = devMode,
            devRootPath = viewModel.devRootPath,
            indexSourceText = indexSourceText,
            downloadMirror = downloadMirror,
            onSetIndexSourceText = viewModel::setIndexSourceText,
            onSetDownloadMirror = viewModel::setDownloadMirror,
            onAddIndexSource = viewModel::addIndexSource,
            onRemoveIndexSource = viewModel::removeIndexSource,
            onSetDevMode = viewModel::setDevMode,
            onDismiss = {
                viewModel.savePluginSettings()
                showSettings = false
            }
        )
    }
}
