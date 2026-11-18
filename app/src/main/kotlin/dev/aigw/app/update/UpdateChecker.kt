package dev.aigw.app.update

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 查有没有新版本。
 *
 * 仓库里同时放着桌面端、VantaCode 和本项目的产物，release tag 用 `vantagate-v`
 * 前缀区分，避免把别的产品的版本当成自己的更新推给用户。
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/Hye-steam/VantaCode-Desktop/releases?per_page=30"
    private const val TAG_PREFIX = "vantagate-v"

    data class Release(
        val version: String,
        val tag: String,
        val pageUrl: String,
        val apkUrl: String?,
        val notes: String,
    )

    /** 查询失败（网络/解析）用 [Result.failure]；成功但没有新版用 success(null)。 */
    fun check(current: String): Result<Release?> = runCatching {
        val body = fetch(API)
        parseLatest(body, current)
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "VantaGate")
        }
        try {
            if (conn.responseCode !in 200..299) error("GitHub 返回 ${conn.responseCode}")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            runCatching { conn.disconnect() }
        }
    }

    private fun parseLatest(body: String, current: String): Release? {
        val arr = JSONArray(body)
        var best: Release? = null
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optBoolean("draft") || o.optBoolean("prerelease")) continue
            val tag = o.optString("tag_name")
            if (!tag.startsWith(TAG_PREFIX, ignoreCase = true)) continue
            val version = tag.removePrefix(TAG_PREFIX).trim()
            if (version.isEmpty()) continue

            // 下载地址优先取 assets 里的 apk，退回到 release 页面
            var apk: String? = null
            o.optJSONArray("assets")?.let { assets ->
                for (j in 0 until assets.length()) {
                    val a = assets.optJSONObject(j) ?: continue
                    val name = a.optString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apk = a.optString("browser_download_url").takeIf { it.isNotEmpty() }
                        break
                    }
                }
            }
            val rel = Release(
                version = version,
                tag = tag,
                pageUrl = o.optString("html_url"),
                apkUrl = apk,
                notes = o.optString("body"),
            )
            if (best == null || compare(version, best.version) > 0) best = rel
        }
        if (best == null) return null
        return if (compare(best.version, current) > 0) best else null
    }

    /** 逐段数值比较，避免 "2.10" 被当成小于 "2.9"。 */
    private fun compare(a: String, b: String): Int {
        val pa = segments(a)
        val pb = segments(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun segments(v: String): List<Int> =
        v.split('.', '-', '+')
            .map { it.takeWhile(Char::isDigit) }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toIntOrNull() }
}
