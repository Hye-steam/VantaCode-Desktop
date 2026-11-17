package dev.aigw.app.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Loop 设计稿色板：随外观主题切换。
 *
 * 字段为可变全局状态（[apply] 由 AppRoot 在主题变化时同步），
 * 配合 `key(themeName)` 整树重建，保证切换主题后所有界面即时换色。
 */
object LoopColors {
    // ---- 基础 ----
    var bg = Color(0xFFF5F7FB)
    var surface = Color(0xFFFFFFFF)
    /** 次级容器：图标底、统计卡图标底、内嵌区块。 */
    var surfaceAlt = Color(0xFFF1F4F9)
    var line = Color(0xFFECEFF4)
    /** 强调描边：需要比普通卡片更“立”的时候用。 */
    var lineStrong = Color(0xFFDCE2EC)
    var muted = Color(0xFF8A94A6)
    var faint = Color(0xFFB4BDCB)
    var subText = Color(0xFF6B7280)
    var text = Color(0xFF1F2937)
    var fieldBg = Color(0xFFF7F9FD)
    var handleBar = Color(0xFFE2E7F0)
    var closeBg = Color(0xFFF2F5FA)

    // ---- 品牌 ----
    var brand = Color(0xFF2F6BFF)
    var brandDeep = Color(0xFF1F57E0)
    var brandLight = Color(0xFF5B8CFF)
    var brandSoft = Color(0xFFEEF3FF)

    // ---- 语义色（各主题基本一致，仅深色主题下微调） ----
    var success = Color(0xFF22C55E)
    var successDeep = Color(0xFF16A34A)
    var successSoft = Color(0xFFEEF9F1)
    var danger = Color(0xFFEF4444)
    var dangerDeep = Color(0xFFDC2626)
    var dangerSoft = Color(0xFFFDECEC)
    var warning = Color(0xFFF59E0B)
    var warningDeep = Color(0xFFD97706)
    var warningSoft = Color(0xFFFDF2E4)

    // ---- 图表/统计卡 ----
    var purple = Color(0xFF8B5CF6)
    var purpleLight = Color(0xFFC084FC)
    var purpleSoft = Color(0xFFF5F3FF)
    var greenLight = Color(0xFF4ADE80)
    var orangeLight = Color(0xFFFBBF24)

    // ---- 供应商图标浅底 ----
    var accentGreenSoft = Color(0xFFE8F9EF)
    var accentCream = Color(0xFFFBF3E4)
    var accentBlueSoft = Color(0xFFE8F0FE)
    var accentOrangeSoft = Color(0xFFFFF7ED)
    var accentGreenLight = Color(0xFFEFFCF3)

    fun apply(themeName: String) {
        when (themeName) {
            "深海" -> abyss()
            "靛夜" -> indigo()
            "墨林" -> moss()
            "炭焰" -> ember()
            "白昼" -> daylight()
            // 旧主题名保留映射，避免已保存的设置切换后失效
            "暗夜蓝" -> abyss()
            "薄荷紫" -> indigo()
            "日落橙" -> ember()
            "樱花粉" -> ember()
            else -> daylight()
        }
    }

    /** 石墨：中性灰黑，强调色也取灰，整个界面只剩明暗关系和语义色。 */
    private fun graphite() {
        bg = Color(0xFF09090B); surface = Color(0xFF131316); line = Color(0xFF27272A)
        surfaceAlt = Color(0xFF1A1A1F); lineStrong = Color(0xFF3F3F46)
        muted = Color(0xFF83838D); faint = Color(0xFF62626C); subText = Color(0xFFA1A1AA)
        text = Color(0xFFFAFAFA); fieldBg = Color(0xFF17171B)
        handleBar = Color(0xFF3F3F46); closeBg = Color(0xFF17171B)
        brand = Color(0xFF63636B); brandDeep = Color(0xFF4A4A52)
        brandLight = Color(0xFF9A9AA5); brandSoft = Color(0xFF1F1F24)
        success = Color(0xFF4ADE80); successDeep = Color(0xFF22C55E); successSoft = Color(0xFF11251A)
        danger = Color(0xFFF87171); dangerDeep = Color(0xFFEF4444); dangerSoft = Color(0xFF2C1414)
        warning = Color(0xFFFBBF24); warningDeep = Color(0xFFF59E0B); warningSoft = Color(0xFF2A2110)
        purple = Color(0xFFA78BFA); purpleLight = Color(0xFFC4B5FD); purpleSoft = Color(0xFF1E1A2E)
        greenLight = Color(0xFF4ADE80); orangeLight = Color(0xFFFBBF24)
        accentGreenSoft = Color(0xFF11251A); accentCream = Color(0xFF2A2110)
        accentBlueSoft = Color(0xFF14202F); accentOrangeSoft = Color(0xFF2A2110)
        accentGreenLight = Color(0xFF11251A)
    }

    /** 深海：蓝黑底 + 沉蓝强调，偏工程工具的气质。 */
    private fun abyss() {
        bg = Color(0xFF05080F); surface = Color(0xFF0B1220); line = Color(0xFF1E2940)
        surfaceAlt = Color(0xFF121A2B); lineStrong = Color(0xFF2A3852)
        muted = Color(0xFF71839E); faint = Color(0xFF5A6A85); subText = Color(0xFF9FB0C9)
        text = Color(0xFFE8EDF7); fieldBg = Color(0xFF0E1626)
        handleBar = Color(0xFF2A3852); closeBg = Color(0xFF0E1626)
        brand = Color(0xFF2563EB); brandDeep = Color(0xFF1D4ED8)
        brandLight = Color(0xFF60A5FA); brandSoft = Color(0xFF10213D)
        success = Color(0xFF34D399); successDeep = Color(0xFF10B981); successSoft = Color(0xFF0E2E24)
        danger = Color(0xFFF87171); dangerDeep = Color(0xFFEF4444); dangerSoft = Color(0xFF331416)
        warning = Color(0xFFFBBF24); warningDeep = Color(0xFFF59E0B); warningSoft = Color(0xFF2E2410)
        purple = Color(0xFFA78BFA); purpleLight = Color(0xFFC4B5FD); purpleSoft = Color(0xFF1C1836)
        greenLight = Color(0xFF34D399); orangeLight = Color(0xFFFBBF24)
        accentGreenSoft = Color(0xFF0E2E24); accentCream = Color(0xFF2E2410)
        accentBlueSoft = Color(0xFF10213D); accentOrangeSoft = Color(0xFF2E2410)
        accentGreenLight = Color(0xFF0E2E24)
    }

    /** 靛夜：紫黑底 + 靛蓝，偏合成器/赛博的冷色。 */
    private fun indigo() {
        bg = Color(0xFF08070F); surface = Color(0xFF111020); line = Color(0xFF262345)
        surfaceAlt = Color(0xFF181730); lineStrong = Color(0xFF332F5C)
        muted = Color(0xFF7D769B); faint = Color(0xFF6A6588); subText = Color(0xFFADA6C9)
        text = Color(0xFFEDEAF7); fieldBg = Color(0xFF141228)
        handleBar = Color(0xFF332F5C); closeBg = Color(0xFF141228)
        brand = Color(0xFF4F46E5); brandDeep = Color(0xFF4338CA)
        brandLight = Color(0xFF818CF8); brandSoft = Color(0xFF1A1838)
        success = Color(0xFF34D399); successDeep = Color(0xFF10B981); successSoft = Color(0xFF0E2E24)
        danger = Color(0xFFF87171); dangerDeep = Color(0xFFEF4444); dangerSoft = Color(0xFF331416)
        warning = Color(0xFFFBBF24); warningDeep = Color(0xFFF59E0B); warningSoft = Color(0xFF2E2410)
        purple = Color(0xFFC4B5FD); purpleLight = Color(0xFFDDD6FE); purpleSoft = Color(0xFF221C42)
        greenLight = Color(0xFF34D399); orangeLight = Color(0xFFFBBF24)
        accentGreenSoft = Color(0xFF0E2E24); accentCream = Color(0xFF2E2410)
        accentBlueSoft = Color(0xFF1A1838); accentOrangeSoft = Color(0xFF2E2410)
        accentGreenLight = Color(0xFF0E2E24)
    }

    /** 墨林：墨绿黑底 + 翠绿，安静、低对比、久看不累。 */
    private fun moss() {
        bg = Color(0xFF05100B); surface = Color(0xFF0B1A13); line = Color(0xFF1B3A2A)
        surfaceAlt = Color(0xFF10241A); lineStrong = Color(0xFF26503A)
        muted = Color(0xFF6F907E); faint = Color(0xFF587A68); subText = Color(0xFF9FC4AF)
        text = Color(0xFFE6F5EC); fieldBg = Color(0xFF0E2018)
        handleBar = Color(0xFF26503A); closeBg = Color(0xFF0E2018)
        brand = Color(0xFF047857); brandDeep = Color(0xFF036B4E)
        brandLight = Color(0xFF34D399); brandSoft = Color(0xFF0D2B20)
        success = Color(0xFF34D399); successDeep = Color(0xFF10B981); successSoft = Color(0xFF0D2B20)
        danger = Color(0xFFF87171); dangerDeep = Color(0xFFEF4444); dangerSoft = Color(0xFF331416)
        warning = Color(0xFFFBBF24); warningDeep = Color(0xFFF59E0B); warningSoft = Color(0xFF2E2410)
        purple = Color(0xFFA78BFA); purpleLight = Color(0xFFC4B5FD); purpleSoft = Color(0xFF1C1836)
        greenLight = Color(0xFF34D399); orangeLight = Color(0xFFFBBF24)
        accentGreenSoft = Color(0xFF0D2B20); accentCream = Color(0xFF2E2410)
        accentBlueSoft = Color(0xFF0F2436); accentOrangeSoft = Color(0xFF2E2410)
        accentGreenLight = Color(0xFF0D2B20)
    }

    /** 炭焰：暖炭黑底 + 琥珀，唯一偏暖的深色。 */
    private fun ember() {
        bg = Color(0xFF0C0A08); surface = Color(0xFF171311); line = Color(0xFF2E2822)
        surfaceAlt = Color(0xFF1F1A16); lineStrong = Color(0xFF40382F)
        muted = Color(0xFF8E8175); faint = Color(0xFF6E6358); subText = Color(0xFFC4B5A6)
        text = Color(0xFFF5EFE8); fieldBg = Color(0xFF1B1613)
        handleBar = Color(0xFF40382F); closeBg = Color(0xFF1B1613)
        brand = Color(0xFFB45309); brandDeep = Color(0xFF92400E)
        brandLight = Color(0xFFFBBF24); brandSoft = Color(0xFF2A1F0F)
        success = Color(0xFF34D399); successDeep = Color(0xFF10B981); successSoft = Color(0xFF12281F)
        danger = Color(0xFFF87171); dangerDeep = Color(0xFFEF4444); dangerSoft = Color(0xFF331416)
        warning = Color(0xFFFCD34D); warningDeep = Color(0xFFF59E0B); warningSoft = Color(0xFF2E2410)
        purple = Color(0xFFA78BFA); purpleLight = Color(0xFFC4B5FD); purpleSoft = Color(0xFF1C1836)
        greenLight = Color(0xFF34D399); orangeLight = Color(0xFFFCD34D)
        accentGreenSoft = Color(0xFF12281F); accentCream = Color(0xFF2A1F0F)
        accentBlueSoft = Color(0xFF14202F); accentOrangeSoft = Color(0xFF2A1F0F)
        accentGreenLight = Color(0xFF12281F)
    }

    /** 白昼：浅灰白底 + 中性蓝，全套里唯一的浅色。 */
    private fun daylight() {
        bg = Color(0xFFF6F6F8); surface = Color(0xFFFFFFFF); line = Color(0xFFE6E6EB)
        surfaceAlt = Color(0xFFF0F0F4); lineStrong = Color(0xFFD5D5DC)
        muted = Color(0xFF6C6C78); faint = Color(0xFF8A8A96); subText = Color(0xFF51515C)
        text = Color(0xFF17171A); fieldBg = Color(0xFFF7F7FA)
        handleBar = Color(0xFFD5D5DC); closeBg = Color(0xFFF0F0F4)
        brand = Color(0xFF2563EB); brandDeep = Color(0xFF1D4ED8)
        // 浅底上 brandLight 必须够深，否则"全部试调"这类强调文字会看不清
        brandLight = Color(0xFF1E56D6); brandSoft = Color(0xFFE8EFFD)
        success = Color(0xFF0A7D45); successDeep = Color(0xFF086338); successSoft = Color(0xFFE6F4EC)
        danger = Color(0xFFC0261A); dangerDeep = Color(0xFFA11F15); dangerSoft = Color(0xFFFDEBEA)
        warning = Color(0xFFB45309); warningDeep = Color(0xFF92400E); warningSoft = Color(0xFFFDF0E3)
        purple = Color(0xFF7C3AED); purpleLight = Color(0xFFA78BFA); purpleSoft = Color(0xFFF1EAFE)
        greenLight = Color(0xFF16A34A); orangeLight = Color(0xFFD97706)
        accentGreenSoft = Color(0xFFE6F4EC); accentCream = Color(0xFFFDF0E3)
        accentBlueSoft = Color(0xFFE8EFFD); accentOrangeSoft = Color(0xFFFDF0E3)
        accentGreenLight = Color(0xFFE6F4EC)
    }

    // ---- 渐变 ----

    /** play-button / save-btn 的主渐变。 */
    fun brandGradient() = Brush.linearGradient(listOf(brand, brandDeep))

    /** nav-orb 渐变。 */
    fun orbGradient() = Brush.linearGradient(listOf(brand, brandLight))

    /** 进度条渐变（蓝/绿/紫/橙）。 */
    fun progressBlue() = Brush.horizontalGradient(listOf(brand, brandLight))
    fun progressGreen() = Brush.horizontalGradient(listOf(success, greenLight))
    fun progressPurple() = Brush.horizontalGradient(listOf(purple, purpleLight))
    fun progressOrange() = Brush.horizontalGradient(listOf(warning, orangeLight))
}
