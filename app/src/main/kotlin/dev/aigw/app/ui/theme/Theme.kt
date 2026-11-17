package dev.aigw.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import dev.aigw.app.ui.LoopColors

/**
 * Material 色板跟随 [LoopColors] 生成。
 *
 * 之前这里写死了一套浅色方案，而界面实际用的是 [LoopColors] 那 5 套可切换主题。
 * 两套不同步的直接后果：深色主题下 OutlinedTextField 的文字仍取浅色方案的
 * onSurface（深灰），落在深色输入框里几乎看不见——粘贴的内容「进去了但看不见」。
 *
 * 用 darkColorScheme 只是借它的参数结构，所有颜色值都显式给，
 * 所以浅色主题同样适用；关键是 onSurface 等语义色要跟着 LoopColors 走。
 */
fun loopColorScheme() = darkColorScheme(
    primary = LoopColors.brand,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = LoopColors.brandSoft,
    onPrimaryContainer = LoopColors.brand,
    secondary = LoopColors.purple,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = LoopColors.purpleSoft,
    onSecondaryContainer = LoopColors.purple,
    background = LoopColors.bg,
    onBackground = LoopColors.text,
    surface = LoopColors.surface,
    onSurface = LoopColors.text,
    surfaceVariant = LoopColors.surfaceAlt,
    onSurfaceVariant = LoopColors.subText,
    outline = LoopColors.line,
    outlineVariant = LoopColors.line,
    error = LoopColors.danger,
    onError = androidx.compose.ui.graphics.Color.White,
    errorContainer = LoopColors.dangerSoft,
    onErrorContainer = LoopColors.danger,
)

@Composable
fun AiGatewayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = loopColorScheme(),
        content = content,
    )
}
