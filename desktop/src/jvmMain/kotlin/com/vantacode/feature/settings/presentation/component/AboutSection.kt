package com.vantacode.feature.settings.presentation.component

import org.jetbrains.compose.resources.decodeToImageBitmap
import com.vantacode.core.platform.DesktopContext

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.vantacode.core.platform.LocalDesktopContext
import com.vantacode.core.theme.Spacing
import com.vantacode.core.theme.semanticColors
import com.vantacode.core.ui.Strings
import compose.icons.FeatherIcons
import compose.icons.feathericons.Tag
import compose.icons.feathericons.MessageCircle
import com.vantacode.core.platform.AppClipboard
import com.vantacode.core.platform.PlatformToast

/**
 * 关于页：顶部应用信息卡 + 版本号 + QQ交流群（点击复制群号）。
 */
@Composable
internal fun AboutSection() {
    val context = LocalDesktopContext.current

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
                    text = Strings.about_description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class AppVersion(val name: String, val code: Long)

private fun loadAppIconBitmap(@Suppress("UNUSED_PARAMETER") context: DesktopContext): ImageBitmap? = runCatching {
    // 桌面端没有自适应图标包；直接读打包进 jar 的 PNG。
    object {}.javaClass.getResourceAsStream("/icon.png")?.readBytes()?.decodeToImageBitmap()
}.getOrNull()

private const val QQ_GROUP_NUMBER = "1094669183"
