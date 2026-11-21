package com.vantacode.feature.settings.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vantacode.core.theme.Spacing
import com.vantacode.core.ui.Strings
import com.vantacode.feature.plugin.domain.PluginPage

/**
 * 插件 HTML 页面宿主的桌面实现。
 *
 * 当前版本：插件页面是 HTML + Vue + aicode.js 桥，依赖浏览器 DOM；桌面端暂无 WebView/JCEF，
 * 因此显示明确降级提示而非静默白屏。后续接入 JCEF 时，只需替换本 composable 的实现。
 */
@Composable
internal fun PluginPageHostDesktop(
    page: PluginPage,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        TextButton(onClick = onBack) { Text(Strings.common_back) }
        Text(
            text = page.title.resolve(if (Strings.current == "en") "en" else "zh-CN"),
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = Strings.plugin_page_unsupported,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = page.pageFile,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
