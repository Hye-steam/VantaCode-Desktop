package dev.aigw.util

import java.util.Locale

/** 把 token 数缩写成 1M / 131K 这种展示形式。 */
fun formatTokenCount(value: Long): String = when {
    value >= 1_000_000 -> "%sM".format(trimNumber(value / 1_000_000.0))
    value >= 1_000 -> "%sK".format(trimNumber(value / 1_000.0))
    else -> value.toString()
}

private fun trimNumber(value: Double): String =
    "%.1f".format(Locale.US, value).trimEnd('0').trimEnd('.')
