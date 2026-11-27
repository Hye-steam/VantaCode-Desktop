package com.vantacode.feature.settings.presentation.component

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.vantacode.core.ui.AdaptiveModalBottomSheet
import androidx.compose.material3.Surface
import com.vantacode.core.ui.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.vantacode.core.ui.saveImageToGallery
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.core.theme.Spacing
import com.vantacode.core.theme.semanticColors
import com.vantacode.core.ui.AppTextField
import com.vantacode.core.ui.dialogTextFieldColors
import com.vantacode.feature.settings.data.repository.ALT_DOWNLOAD_MIRROR
import com.vantacode.feature.settings.data.repository.DEFAULT_DOWNLOAD_MIRROR
import com.vantacode.feature.settings.data.repository.UpdateChannel
import com.vantacode.feature.settings.data.repository.isValidDownloadMirror
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertCircle
import compose.icons.feathericons.Book
import compose.icons.feathericons.Check
import compose.icons.feathericons.Download
import compose.icons.feathericons.Github
import compose.icons.feathericons.Globe
import compose.icons.feathericons.RefreshCw
import compose.icons.feathericons.Tag
import compose.icons.feathericons.MessageCircle
import compose.icons.feathericons.Heart
import compose.icons.feathericons.Star

/**
 * 关于页：顶部应用信息、版本号（点击手动检查更新）、自动检查更新开关、更新通道、
 * 下载镜像源、GitHub 仓库、开源许可证、用户协议与隐私政策。检查更新逻辑与弹窗由全局 [SettingsViewModel]
 * 承载，本页只负责触发与展示设置项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutSection(
    updateCheckEnabled: Boolean,
    updateCheckChannel: UpdateChannel,
    updateCheckMirror: String,
    onToggleUpdateCheck: (Boolean) -> Unit,
    onSelectChannel: (UpdateChannel) -> Unit,
    onSelectMirror: (String) -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var showMirrorDialog by remember { mutableStateOf(false) }
    var showDonateDialog by remember { mutableStateOf(false) }
    // 进关于页时看是否该提醒 Star：用户点过「不再提示」或已去过 GitHub 就不再弹。
    // 「下次一定」只关一次，但同一个进程里不再反复提，否则来回切页会一直弹。
    var showStarDialog by remember {
        mutableStateOf(!shouldSkipStarPrompt(context) && !starPromptDismissedThisSession)
    }
    val termsUrl = stringResource(R.string.agreement_terms_url)
    val privacyUrl = stringResource(R.string.agreement_privacy_url)

    // 通过 PackageManager 读取 versionName/versionCode（项目未开启 BuildConfig）
    val appInfo = remember {
        runCatching {
            val pm = context.packageManager
            val info = pm.getPackageInfo(context.packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            AppVersion(name = info.versionName ?: "unknown", code = code)
        }.getOrDefault(AppVersion("unknown", 0L))
    }

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
        AboutHeaderCard(appName = stringResource(R.string.app_name), appIcon = appIcon)

        SettingsGroup {
            // 版本
            SettingsRow(
                icon = FeatherIcons.Tag,
                title = stringResource(R.string.about_version),
                trailing = {
                    Text(
                        text = "v${appInfo.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
            )
            SettingsDivider()
            // 交流群：点一下把群号复制走
            SettingsRow(
                icon = FeatherIcons.MessageCircle,
                title = stringResource(R.string.about_qq_group),
                subtitle = QQ_GROUP_NUMBER,
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(QQ_GROUP_NUMBER))
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.about_qq_group_copied),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
            SettingsDivider()
            // 自动检查更新
            SettingsRow(
                icon = FeatherIcons.RefreshCw,
                title = stringResource(R.string.about_auto_update),
                subtitle = stringResource(R.string.about_auto_update_desc),
                trailing = {
                    AppSwitch(
                        checked = updateCheckEnabled,
                        onCheckedChange = onToggleUpdateCheck
                    )
                }
            )
            SettingsDivider()
            // 下载镜像源
            SettingsRow(
                icon = FeatherIcons.Download,
                title = stringResource(R.string.about_update_mirror),
                subtitle = updateCheckMirror,
                onClick = { showMirrorDialog = true }
            )
            SettingsDivider()
            // GitHub 仓库
            SettingsRow(
                icon = FeatherIcons.Github,
                title = stringResource(R.string.about_github),
                onClick = {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(GITHUB_REPO_URL)
                    ))
                }
            )
            SettingsDivider()
            // 赞赏：弹出赞赏码
            SettingsRow(
                icon = FeatherIcons.Heart,
                title = stringResource(R.string.about_donate),
                subtitle = stringResource(R.string.about_donate_desc),
                onClick = { showDonateDialog = true }
            )
            SettingsDivider()
            // 开源许可证
            SettingsRow(
                icon = FeatherIcons.Book,
                title = stringResource(R.string.about_license),
                onClick = {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(LICENSE_URL)
                    ))
                }
            )
            SettingsDivider()
            // 用户协议
            SettingsRow(
                icon = FeatherIcons.AlertCircle,
                title = stringResource(R.string.about_terms),
                onClick = {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(termsUrl)
                    ))
                }
            )
            SettingsDivider()
            // 隐私政策
            SettingsRow(
                icon = FeatherIcons.AlertCircle,
                title = stringResource(R.string.about_privacy),
                onClick = {
                    context.startActivity(android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(privacyUrl)
                    ))
                }
            )
        }
    }

    if (showMirrorDialog) {
        MirrorSourceDialog(
            current = updateCheckMirror,
            onConfirm = {
                onSelectMirror(it)
                showMirrorDialog = false
            },
            onDismiss = { showMirrorDialog = false }
        )
    }

    if (showDonateDialog) {
        DonateDialog(onDismiss = { showDonateDialog = false })
    }

    if (showStarDialog) {
        StarPromptDialog(
            appIcon = appIcon,
            onOpenGithub = {
                context.startActivity(android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(GITHUB_REPO_URL)
                ))
                // 去了就不再打扰，不然后面每次进来都弹
                markStarPromptDone(context)
                showStarDialog = false
            },
            onNeverAgain = {
                markStarPromptDone(context)
                showStarDialog = false
            },
            onLater = {
                starPromptDismissedThisSession = true
                showStarDialog = false
            }
        )
    }
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
                    text = stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 更新通道底部弹窗：稳定版 / 最新版 单选，样式对齐语言切换。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateChannelSheet(
    current: UpdateChannel,
    onSelect: (UpdateChannel) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    AdaptiveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = stringResource(R.string.about_update_channel),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.md)
            )

            listOf(
                UpdateChannel.STABLE to stringResource(R.string.update_channel_stable),
                UpdateChannel.LATEST to stringResource(R.string.update_channel_latest)
            ).forEach { (channel, label) ->
                val selected = channel == current
                Surface(
                    onClick = {
                        onDismiss()
                        onSelect(channel)
                    },
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = stringResource(
                                    if (channel == UpdateChannel.STABLE) {
                                        R.string.update_channel_stable_desc
                                    } else {
                                        R.string.update_channel_latest_desc
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = FeatherIcons.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 镜像源快捷项：预置常用节点 + 直连，省得用户手打前缀。 */
private data class MirrorPreset(val labelRes: Int, val value: String)

private val MIRROR_PRESETS = listOf(
    MirrorPreset(R.string.about_update_mirror_preset_cdn, DEFAULT_DOWNLOAD_MIRROR),
    MirrorPreset(R.string.about_update_mirror_preset_v6, ALT_DOWNLOAD_MIRROR),
    MirrorPreset(R.string.about_update_mirror_preset_direct, "")
)

/**
 * 下载镜像源编辑弹窗：预置快捷项 + 自定义前缀 + 恢复默认。
 *
 * 约定是「前缀拼接」（`https://镜像/https://github.com/...`），含 `{url}` 占位符时按占位符
 * 替换，所以第三方 gh-proxy 实例与自建节点都能直接填；留空 = 直连 GitHub。
 * 「恢复默认」只把输入框填回默认值，仍需点「保存」才落盘。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MirrorSourceDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(current) { mutableStateOf(current) }
    val valid = isValidDownloadMirror(text)
    val trimmed = text.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_update_mirror)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(
                    text = stringResource(R.string.about_update_mirror_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MIRROR_PRESETS.forEach { preset ->
                    val selected = trimmed == preset.value
                    Surface(onClick = { text = preset.value }, color = Color.Transparent) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(preset.labelRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (selected) {
                                Icon(
                                    imageVector = FeatherIcons.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                AppTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = DEFAULT_DOWNLOAD_MIRROR,
                    isError = !valid,
                    supportingText = if (valid) {
                        null
                    } else {
                        { Text(stringResource(R.string.about_update_mirror_invalid)) }
                    },
                    colors = dialogTextFieldColors()
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                TextButton(onClick = { text = DEFAULT_DOWNLOAD_MIRROR }) {
                    Text(stringResource(R.string.about_update_mirror_default))
                }
                TextButton(onClick = { onConfirm(trimmed) }, enabled = valid) {
                    Text(stringResource(R.string.common_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

/** GitHub tag 形如 v1.7.0 / 1.7.0 / v1.7.0-rc1，提取出纯版本号。 */
internal fun parseVersionTag(tag: String): String? {
    val raw = tag.trim().removePrefix("v")
    val seg = raw.substringBefore(' ')
    return seg.ifBlank { null }
}

/**
 * 语义化版本比较：判断最新版本 [latest] 是否严格大于当前版本 [current]。
 * 若 latest 不大于 current（即最新版本已安装或当前属于开发/测试版），返回 true 表示已是最新。
 */
internal fun isUpToDate(latest: String, current: String): Boolean {
    val cmp = compareVersions(latest, current)
    return cmp <= 0
}

/**
 * 语义化版本号比较器（SemVer 兼容）：
 * 返回 >0 表示 v1 > v2，<0 表示 v1 < v2，0 表示相等。
 * 规则：
 * 1. 优先比较主.次.修（如 1.7.0 > 1.6.9）
 * 2. 主次修相同时，正式版 > 预发布版（1.7.0 > 1.7.0-rc1 > 1.7.0-dev）
 * 3. 均为预发布版时，同标识比序号（rc2 > rc1、dev.10 > dev.9），不同标识按字典序
 */
internal fun compareVersions(v1: String, v2: String): Int {
    if (v1 == v2) return 0

    val (base1, pre1) = splitVersion(v1)
    val (base2, pre2) = splitVersion(v2)

    val parts1 = base1.split('.').mapNotNull { it.toIntOrNull() }
    val parts2 = base2.split('.').mapNotNull { it.toIntOrNull() }

    val maxLen = maxOf(parts1.size, parts2.size)
    for (i in 0 until maxLen) {
        val p1 = parts1.getOrElse(i) { 0 }
        val p2 = parts2.getOrElse(i) { 0 }
        if (p1 != p2) return p1.compareTo(p2)
    }

    // 主版本号相同的情况下：
    // 正式版 (pre为空) > 预发布版 (pre不为空)
    if (pre1.isEmpty() && pre2.isNotEmpty()) return 1
    if (pre1.isNotEmpty() && pre2.isEmpty()) return -1

    return comparePreRelease(pre1, pre2)
}

private val PRE_RELEASE_TOKEN = Regex("^(\\D*?)(\\d+)$")

/**
 * 预发布段比较：「同标识 + 序号」按数字比（rc10 > rc9），避免字典序把 rc10 排在 rc9 前；
 * 标识不同或不含尾随数字时退回字典序。
 */
private fun comparePreRelease(a: String, b: String): Int {
    val ma = PRE_RELEASE_TOKEN.matchEntire(a)
    val mb = PRE_RELEASE_TOKEN.matchEntire(b)
    if (ma != null && mb != null && ma.groupValues[1] == mb.groupValues[1]) {
        return ma.groupValues[2].toInt().compareTo(mb.groupValues[2].toInt())
    }
    return a.compareTo(b)
}

internal fun splitVersion(v: String): Pair<String, String> {
    val clean = v.substringBefore('+') // 去掉构建哈希如 +g04bc2fa
    val base = clean.substringBefore('-')
    val pre = if (clean.contains('-')) clean.substringAfter('-') else ""
    return base to pre
}

private data class AppVersion(val name: String, val code: Long)

/**
 * 通过 PackageManager.loadIcon 加载应用图标并转为 ImageBitmap，兼容自适应图标
 * (adaptive icon XML)。解决 painterResource(R.mipmap.ic_launcher) 在 v26+ 设备上
 * 因解析到 mipmap-anydpi-v26/ic_launcher.xml 而抛 IllegalArgumentException 的问题。
 * 失败返回 null（调用方预留占位）。
 */
private fun loadAppIconBitmap(context: Context): ImageBitmap? {
    return runCatching {
        val pm = context.packageManager
        val drawable: Drawable = pm.getApplicationInfo(context.packageName, 0).loadIcon(pm)
        val sizePx = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            ICON_PX_DP.toFloat(),
            context.resources.displayMetrics
        ).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, sizePx, sizePx)
        Canvas(bitmap).also { drawable.draw(it) }
        bitmap.asImageBitmap()
    }.getOrNull()
}

/**
 * Star 提醒弹窗。
 *
 * 照搬用户给的参考样式：图标右下角压个小星星、居中标题、三行说明、
 * 主按钮（去 GitHub）+ 底部两个文字按钮。
 *
 * 两个底部按钮的差别是「以后还提不提」：
 *   不再提示 → 永久标记，以后不再自动弹
 *   下次一定 → 只关这一次，下次启动还会问
 * 这是很多开源 App 的惯例，把选择权留给用户。
 */
@Composable
internal fun StarPromptDialog(
    appIcon: ImageBitmap?,
    onOpenGithub: () -> Unit,
    onNeverAgain: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        confirmButton = {},
        dismissButton = {},
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // 图标 + 右下角星徽
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                    } else {
                        Icon(
                            imageVector = FeatherIcons.Star,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(Color(0xFFFFC107)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Star,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = Color.White
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.star_prompt_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.star_prompt_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.semanticColors.subtleText,
                    textAlign = TextAlign.Center
                )

                // 主按钮：去 GitHub
                Surface(
                    onClick = onOpenGithub,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.semanticColors.cardSurface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = FeatherIcons.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = stringResource(R.string.star_prompt_action),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onNeverAgain) {
                        Text(
                            text = stringResource(R.string.star_prompt_never),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.semanticColors.subtleText
                        )
                    }
                    TextButton(onClick = onLater) {
                        Text(
                            text = stringResource(R.string.star_prompt_later),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.semanticColors.subtleText
                        )
                    }
                }
            }
        }
    )
}

/**
 * 赞赏弹窗：展示微信赞赏码。
 *
 * 码图从 assets 读，不依赖网络。长按保存到相册，方便在电脑上扫码或转发。
 * 用 AlertDialog 而非 bottom sheet：正方形图很高，sheet 会被手势拖拽误关。
 */
@Composable
private fun DonateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val qr = remember { loadAssetBitmap(context, "donate/wechat.png") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.about_donate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = stringResource(R.string.about_donate),
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                scope.launch {
                                    saveImageToGallery(context, qr, "VantaCode-donate")
                                    android.widget.Toast.makeText(
                                        context,
                                        context.getString(R.string.about_donate_saved),
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    )
                } else {
                    Text(
                        text = stringResource(R.string.about_donate_load_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.semanticColors.subtleText
                    )
                }
                Text(
                    text = stringResource(R.string.about_donate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.semanticColors.subtleText
                )
            }
        }
    )
}

/** 从 assets 读图并转成 ImageBitmap；安卓不支持直接 decode 流里的自适应 PNG，必须先缓冲。 */
private fun loadAssetBitmap(context: Context, path: String): ImageBitmap? = runCatching {
    context.assets.open(path).use { input ->
        android.graphics.BitmapFactory.decodeStream(input)?.asImageBitmap()
    }
}.getOrNull()

/** Star 提醒的持久标记：只记一个布尔值，存 SharedPreferences 而非 DataStore。 */
private const val STAR_PREFS = "star_prompt_prefs"
private const val STAR_PROMPT_NEVER = "never_again"

/**
 * 本次进程内用户已选过「下次一定」。
 *
 * 只用 remember 不够：离开关于页后组合被销毁，下次进来又弹，
 * 在页签间来回切会反复打扰。所以提到进程级变量。
 */
private var starPromptDismissedThisSession = false

/** 用户已明确表示不再需要提醒（点过「不再提示」或已经去过 GitHub）。 */
private fun shouldSkipStarPrompt(context: Context): Boolean =
    context.getSharedPreferences(STAR_PREFS, Context.MODE_PRIVATE)
        .getBoolean(STAR_PROMPT_NEVER, false)

/** 以后不再弹 Star 提醒。 */
private fun markStarPromptDone(context: Context) {
    context.getSharedPreferences(STAR_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(STAR_PROMPT_NEVER, true).apply()
}

/** 交流群号：点一下复制。 */
private const val QQ_GROUP_NUMBER = "1094669183"

private const val GITHUB_REPO_URL = "https://github.com/Hye-steam/VantaCode-Desktop"
private const val LICENSE_URL = "https://github.com/Hye-steam/VantaCode-Desktop/blob/vantacode-android/LICENSE"
private const val ICON_PX_DP = 48
