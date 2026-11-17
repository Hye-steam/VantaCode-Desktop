package dev.aigw.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 全局外观色板。
 *
 * 一套主题同时定义 Compose 页面与 View 系统悬浮球使用的颜色，
 * 保证切换主题后界面与悬浮球视觉一致。
 */
data class ThemePalette(
    val name: String,
    val isDark: Boolean,
    val pageBg: Color,
    val card: Color,
    val navBg: Color,
    val primary: Color,
    val primaryDeep: Color,
    val primarySoft: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val cardBorder: Color,
    val fieldBg: Color,
    val danger: Color,
    val success: Color,
    val warning: Color,
    val info: Color,
    val bubbleOff: Color,
    val bubbleIdle: Color,
    val bubbleOk: Color,
    val bubbleFail: Color,
    val bubbleWarn: Color,
) {
    /** 悬浮球是 View 系统，需要 Int 颜色。 */
    val bubbleOffInt: Int get() = bubbleOff.toArgb()
    val bubbleIdleInt: Int get() = bubbleIdle.toArgb()
    val bubbleOkInt: Int get() = bubbleOk.toArgb()
    val bubbleFailInt: Int get() = bubbleFail.toArgb()
    val bubbleWarnInt: Int get() = bubbleWarn.toArgb()
}

enum class ThemeName(val displayName: String) {
    Default("默认"),
    Midnight("暗夜蓝"),
    Mint("薄荷紫"),
    Sunset("日落橙"),
    Sakura("樱花粉"),
}

fun themeByName(name: String?): ThemePalette = when (name) {
    ThemeName.Midnight.displayName -> midnightTheme()
    ThemeName.Mint.displayName -> mintTheme()
    ThemeName.Sunset.displayName -> sunsetTheme()
    ThemeName.Sakura.displayName -> sakuraTheme()
    else -> defaultTheme()
}

fun defaultTheme() = ThemePalette(
    name = ThemeName.Default.displayName,
    isDark = false,
    pageBg = Color(0xFFF7F8FA),
    card = Color.White,
    navBg = Color.White,
    primary = Color(0xFF10B981),
    primaryDeep = Color(0xFF059669),
    primarySoft = Color(0xFFECFDF5),
    ink = Color(0xFF1A1D23),
    ink2 = Color(0xFF5A6270),
    ink3 = Color(0xFF9AA2AE),
    line = Color(0xFFECEEF3),
    cardBorder = Color(0xFFE6EAF0),
    fieldBg = Color(0xFFF8FAFC),
    danger = Color(0xFFF43F5E),
    success = Color(0xFF059669),
    warning = Color(0xFFD97706),
    info = Color(0xFF0284C7),
    bubbleOff = Color(0xFF64748B),
    bubbleIdle = Color(0xFF0EA5E9),
    bubbleOk = Color(0xFF10B981),
    bubbleFail = Color(0xFFF43F5E),
    bubbleWarn = Color(0xFFD97706),
)

fun midnightTheme() = ThemePalette(
    name = ThemeName.Midnight.displayName,
    isDark = true,
    pageBg = Color(0xFF0B1120),
    card = Color(0xFF151D2E),
    navBg = Color(0xFF151D2E),
    primary = Color(0xFF38BDF8),
    primaryDeep = Color(0xFF0EA5E9),
    primarySoft = Color(0xFF0C4A6E),
    ink = Color(0xFFF1F5F9),
    ink2 = Color(0xFFCBD5E1),
    ink3 = Color(0xFF94A3B8),
    line = Color(0xFF1E293B),
    cardBorder = Color(0xFF26334A),
    fieldBg = Color(0xFF1A2538),
    danger = Color(0xFFFB7185),
    success = Color(0xFF34D399),
    warning = Color(0xFFFBBF24),
    info = Color(0xFF38BDF8),
    bubbleOff = Color(0xFF94A3B8),
    bubbleIdle = Color(0xFF38BDF8),
    bubbleOk = Color(0xFF34D399),
    bubbleFail = Color(0xFFFB7185),
    bubbleWarn = Color(0xFFFBBF24),
)

fun mintTheme() = ThemePalette(
    name = ThemeName.Mint.displayName,
    isDark = false,
    pageBg = Color(0xFFF3F0FF),
    card = Color.White,
    navBg = Color.White,
    primary = Color(0xFF8B5CF6),
    primaryDeep = Color(0xFF7C3AED),
    primarySoft = Color(0xFFEDE9FE),
    ink = Color(0xFF1A1D23),
    ink2 = Color(0xFF5A6270),
    ink3 = Color(0xFF9AA2AE),
    line = Color(0xFFE7E3F9),
    cardBorder = Color(0xFFDDD8F4),
    fieldBg = Color(0xFFF6F4FF),
    danger = Color(0xFFF43F5E),
    success = Color(0xFF10B981),
    warning = Color(0xFFD97706),
    info = Color(0xFF0284C7),
    bubbleOff = Color(0xFF64748B),
    bubbleIdle = Color(0xFF8B5CF6),
    bubbleOk = Color(0xFF10B981),
    bubbleFail = Color(0xFFF43F5E),
    bubbleWarn = Color(0xFFD97706),
)

fun sunsetTheme() = ThemePalette(
    name = ThemeName.Sunset.displayName,
    isDark = false,
    pageBg = Color(0xFFFFF7ED),
    card = Color.White,
    navBg = Color.White,
    primary = Color(0xFFF97316),
    primaryDeep = Color(0xFFEA580C),
    primarySoft = Color(0xFFFFEDD5),
    ink = Color(0xFF1A1D23),
    ink2 = Color(0xFF5A6270),
    ink3 = Color(0xFF9AA2AE),
    line = Color(0xFFFFE4CC),
    cardBorder = Color(0xFFFED7AA),
    fieldBg = Color(0xFFFFF8F1),
    danger = Color(0xFFF43F5E),
    success = Color(0xFF10B981),
    warning = Color(0xFFD97706),
    info = Color(0xFF0284C7),
    bubbleOff = Color(0xFF64748B),
    bubbleIdle = Color(0xFFF97316),
    bubbleOk = Color(0xFF10B981),
    bubbleFail = Color(0xFFF43F5E),
    bubbleWarn = Color(0xFFD97706),
)

fun sakuraTheme() = ThemePalette(
    name = ThemeName.Sakura.displayName,
    isDark = false,
    pageBg = Color(0xFFFFF1F2),
    card = Color.White,
    navBg = Color.White,
    primary = Color(0xFFFB7185),
    primaryDeep = Color(0xFFE11D48),
    primarySoft = Color(0xFFFFE4E6),
    ink = Color(0xFF1A1D23),
    ink2 = Color(0xFF5A6270),
    ink3 = Color(0xFF9AA2AE),
    line = Color(0xFFFEDDE2),
    cardBorder = Color(0xFFFECDD3),
    fieldBg = Color(0xFFFFF5F6),
    danger = Color(0xFFF43F5E),
    success = Color(0xFF10B981),
    warning = Color(0xFFD97706),
    info = Color(0xFF0284C7),
    bubbleOff = Color(0xFF64748B),
    bubbleIdle = Color(0xFFFB7185),
    bubbleOk = Color(0xFF10B981),
    bubbleFail = Color(0xFFF43F5E),
    bubbleWarn = Color(0xFFD97706),
)
