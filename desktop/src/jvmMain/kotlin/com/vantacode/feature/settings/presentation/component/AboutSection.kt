package com.vantacode.feature.settings.presentation.component

import org.jetbrains.compose.resources.decodeToImageBitmap
import com.vantacode.core.platform.DesktopContext

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vantacode.core.platform.LocalDesktopContext
import com.vantacode.core.theme.Spacing
import com.vantacode.core.theme.semanticColors
import com.vantacode.core.ui.Strings
import com.vantacode.di.AppGraph
import com.vantacode.feature.settings.data.remote.UpdateCheckResult
import com.vantacode.feature.settings.data.repository.UpdateCheckSettingsRepository
import compose.icons.FeatherIcons
import compose.icons.feathericons.Heart
import compose.icons.feathericons.Star
import compose.icons.feathericons.Tag
import compose.icons.feathericons.MessageCircle
import compose.icons.feathericons.MessageSquare
import compose.icons.feathericons.Lock
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Github
import compose.icons.feathericons.GitBranch
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.RefreshCw
import com.vantacode.core.platform.AppClipboard
import com.vantacode.core.platform.DesktopAssets
import com.vantacode.core.platform.PlatformToast

private const val REPO_URL = "https://github.com/Hye-steam/VantaCode-Desktop"
private const val UPSTREAM_URL = "https://github.com/jieapi/AiCode"
private const val RELEASES_URL = "https://github.com/Hye-steam/VantaCode-Desktop/releases"
private const val ISSUES_URL = "https://github.com/Hye-steam/VantaCode-Desktop/issues"

/**
 * 关于页：应用信息卡 + 版本 + QQ 交流群（点击复制）。
 * 下面一组：GitHub 仓库 / 上游项目 AiCode / 更新日志 / 反馈问题 /
 * 开源许可证 / 用户协议 / 隐私政策。都是点开即可用的真入口。
 */
@Composable
internal fun AboutSection(
    updateRepository: UpdateCheckSettingsRepository = AppGraph.updateCheckSettingsRepository
) {
    val context = LocalDesktopContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    var showStarDialog by remember { mutableStateOf(false) }

    // “不再提示”持久化：与更新设置同款 SharedPreferences。
    // 手动点开关于页入口时不受此标志影响（用户明确想看）；供以后自动弹出 Star 提示时读取。
    val supportPrefs = remember {
        context.getSharedPreferences(SUPPORT_PREFS, DesktopContext.MODE_PRIVATE)
    }

    // 赞赏码：对话框打开时才读（145KB JPG，打包进 jar，与 icon.png 同款加载方式）。
    val donateQrBitmap = remember(showDonateDialog) {
        if (showDonateDialog) loadDonateQrBitmap() else null
    }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }

    // 应用图标：用 PackageManager.loadIcon 加载自适应图标，避免 mipmap-anydpi 的 XML
    // 在 Compose painterResource 中不被支持的问题（Only VectorDrawables and rasterized types）。
    val appIcon = remember { loadAppIconBitmap(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        AboutHeaderCard(appName = Strings.app_name, appIcon = appIcon)

        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Tag,
                title = Strings.about_version,
                trailing = {
                    Text(
                        text = "v${com.vantacode.core.platform.PlatformInfo.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.RefreshCw,
                title = Strings.about_check_update,
                onClick = {
                    showUpdateDialog = true
                    checkingUpdate = true
                    updateResult = null
                    scope.launch {
                        updateResult = runCatching {
                            updateRepository.checkForUpdate(
                                com.vantacode.core.platform.PlatformInfo.VERSION_NAME
                            )
                        }.getOrElse { UpdateCheckResult.Error(it.message ?: Strings.about_network_error) }
                        checkingUpdate = false
                    }
                },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.MessageCircle,
                title = Strings.about_qq_group,
                onClick = {
                    AppClipboard.setText(QQ_GROUP_NUMBER)
                    PlatformToast.show(Strings.about_qq_group_copied)
                },
                trailing = {
                    Text(
                        text = QQ_GROUP_NUMBER,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
        }

        SettingsGroupHeader(text = Strings.settings_about)
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Github,
                title = Strings.about_github_repo,
                onClick = { runCatching { uriHandler.openUri(REPO_URL) } },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.GitBranch,
                title = Strings.about_upstream,
                onClick = { runCatching { uriHandler.openUri(UPSTREAM_URL) } },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Tag,
                title = Strings.about_releases,
                onClick = { runCatching { uriHandler.openUri(RELEASES_URL) } },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.MessageSquare,
                title = Strings.about_feedback,
                onClick = { runCatching { uriHandler.openUri(ISSUES_URL) } },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.FileText,
                title = Strings.about_license,
                onClick = { showLicenseDialog = true },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.FileText,
                title = Strings.agreement_terms,
                onClick = { runCatching { uriHandler.openUri(Strings.agreement_terms_url) } },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Lock,
                title = Strings.agreement_privacy,
                onClick = { runCatching { uriHandler.openUri(Strings.agreement_privacy_url) } },
                trailing = { Chevron() }
            )
        }

        SettingsGroupHeader(text = "支持")
        SettingsGroup {
            SettingsRow(
                icon = FeatherIcons.Heart,
                title = "赞赏作者",
                onClick = { showDonateDialog = true },
                trailing = { Chevron() }
            )
            SettingsDivider()
            SettingsRow(
                icon = FeatherIcons.Star,
                title = "支持 VantaCode",
                onClick = { showStarDialog = true },
                trailing = { Chevron() }
            )
        }

        Text(
            text = "GPL-3.0 · VantaCode Desktop\nFork 自 AiCode，对应源码见本仓库（${REPO_URL}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.semanticColors.subtleText,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
        )
    }

    if (showLicenseDialog) {
        val licenseText = rememberLicenseText()
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(Strings.about_license) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = licenseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) { Text(Strings.common_close) }
            }
        )
    }

    if (showUpdateDialog) {
        UpdateCheckDialog(
            checking = checkingUpdate,
            result = updateResult,
            currentVersion = com.vantacode.core.platform.PlatformInfo.VERSION_NAME,
            onDismiss = { showUpdateDialog = false },
            onOpenRelease = { url -> runCatching { uriHandler.openUri(url) } }
        )
    }

    if (showDonateDialog) {
        AlertDialog(
            onDismissRequest = { showDonateDialog = false },
            title = { Text("赞赏作者") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (donateQrBitmap != null) {
                        Image(
                            bitmap = donateQrBitmap,
                            contentDescription = "辉夜 的赞赏码（微信收款码）",
                            modifier = Modifier.size(280.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "赞赏码加载失败，请稍后重试",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "微信扫一扫，支持作者",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDonateDialog = false }) { Text(Strings.common_close) }
            }
        )
    }

    if (showStarDialog) {
        StarSupportDialog(
            appIcon = appIcon,
            onOpenGithub = { runCatching { uriHandler.openUri(REPO_URL) } },
            onDismissForever = {
                supportPrefs.edit { putBoolean(KEY_STAR_PROMPT_DISMISSED, true) }
                showStarDialog = false
            },
            onDismissOnce = { showStarDialog = false },
            onDismissRequest = { showStarDialog = false }
        )
    }
}

@Composable
private fun Chevron() {
    androidx.compose.material3.Icon(
        FeatherIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        modifier = Modifier.size(16.dp)
    )
}

@Composable
private fun rememberLicenseText(): String {
    var text by remember { mutableStateOf("…") }
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) {
            runCatching { DesktopAssets.open("LICENSE").use { it.readBytes().toString(Charsets.UTF_8) } }
                .getOrElse { "无法读取内置 LICENSE：${it.message}" }
        }
    }
    return text
}

/** 顶部信息卡：左 app 图标，右上 app 名，右下一句简介。 */
@Composable
private fun AboutHeaderCard(appName: String, appIcon: androidx.compose.ui.graphics.ImageBitmap?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.semanticColors.cardSurface,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                // 极端情况下图标加载失败的占位，保持布局占位
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.width(Spacing.lg))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = Strings.about_description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Star 支持弹窗（照 AiCode 关于页样式）。
 * 顶部 app 图标 + 金色星标，标题/正文居中，主按钮跳 GitHub，
 * 下方“不再提示”（写 SharedPreferences，供以后自动弹出时读取）与“下次一定”。
 */
@Composable
private fun StarSupportDialog(
    appIcon: ImageBitmap?,
    onOpenGithub: () -> Unit,
    onDismissForever: () -> Unit,
    onDismissOnce: () -> Unit,
    onDismissRequest: () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // app 图标 + 右下角金色星标
                Box(modifier = Modifier.size(64.dp)) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).align(Alignment.Center)
                        )
                    } else {
                        Spacer(Modifier.size(56.dp).align(Alignment.Center))
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color(0xFFFFC107)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            FeatherIcons.Star,
                            contentDescription = null,
                            tint = Color(0xFF1A1A1A),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "喜欢 VantaCode 吗？",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = "VantaCode 完全开源免费。如果它帮到了你的日常开发，欢迎去 GitHub 点个 Star 支持，让更多开发者看到这个项目。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(
                    onClick = onOpenGithub,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        FeatherIcons.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("前往 GitHub 点 Star")
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismissForever) {
                        Text("不再提示", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onDismissOnce) {
                        Text("下次一定", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private data class AppVersion(val name: String, val code: Long)

private fun loadAppIconBitmap(@Suppress("UNUSED_PARAMETER") context: DesktopContext): ImageBitmap? = runCatching {
    // 桌面端没有自适应图标包；直接读打包进 jar 的 PNG。
    object {}.javaClass.getResourceAsStream("/icon.png")?.readBytes()?.decodeToImageBitmap()
}.getOrNull()

/** 赞赏码：读打包进 jar 的 JPG（与 icon.png 同款加载方式）。 */
private fun loadDonateQrBitmap(): ImageBitmap? = runCatching {
    object {}.javaClass.getResourceAsStream("/donate-qrcode.jpg")?.readBytes()?.decodeToImageBitmap()
}.getOrNull()

private const val QQ_GROUP_NUMBER = "1094669183"

/** “不再提示”存这里；与更新设置同款 SharedPreferences。 */
private const val SUPPORT_PREFS = "support_prefs"
private const val KEY_STAR_PROMPT_DISMISSED = "star_prompt_dismissed"
