package dev.aigw.desktop.update

import com.google.gson.JsonParser
import java.net.HttpURLConnection
import java.net.URL

/**
 * 从 GitHub Releases 查有没有新版本（桌面版）。
 *
 * 由 Android 版 UpdateChecker 移植，改动（计划已定）：
 * - tag 前缀 `vantagate-v` → `vantagate-desktop-v`；
 * - 资产取 `.exe`（不再是 `.apk`）；
 * - `org.json`（Android 自带，纯 JVM 没有）→ Gson；
 * - `per_page=30` → `per_page=100`（仓库已有 39 个 Release，30 条会漏掉旧产品）；
 * - 修大小写 quirk：过滤与剥离都大小写不敏感（旧代码过滤忽略大小写、
 *   剥离却大小写敏感，`VantaGate-v2.5` 会残留前缀进版本号）。
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/Hye-steam/VantaCode-Desktop/releases?per_page=100"
    private const val TAG_PREFIX = "vantagate-desktop-v"

    data class Release(
        val version: String,
        val tag: String,
        val pageUrl: String,
        val exeUrl: String?,
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

    internal fun parseLatest(body: String, current: String): Release? {
        val arr = JsonParser.parseString(body).asJsonArray
        var best: Release? = null
        for (element in arr) {
            val o = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (o.bool("draft") || o.bool("prerelease")) continue
            val tag = o.string("tag_name")
            if (!tag.startsWith(TAG_PREFIX, ignoreCase = true)) continue
            // 大小写不敏感剥离：过滤已忽略大小写，剥离必须同样忽略，否则
            // `VantaGate-desktop-v2.5` 会残留前缀进版本号。
            val version = tag.substring(TAG_PREFIX.length).trim()
            if (version.isEmpty()) continue

            // 下载地址优先取 assets 里的 exe，退回到 release 页面
            var exe: String? = null
            val assets = o.getAsJsonArray("assets")
            if (assets != null) {
                for (asset in assets) {
                    val a = asset.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val name = a.string("name")
                    if (name.endsWith(".exe", ignoreCase = true)) {
                        exe = a.string("browser_download_url").takeIf { it.isNotEmpty() }
                        break
                    }
                }
            }
            val rel = Release(
                version = version,
                tag = tag,
                pageUrl = o.string("html_url"),
                exeUrl = exe,
                notes = o.string("body"),
            )
            if (best == null || compare(version, best.version) > 0) best = rel
        }
        if (best == null) return null
        return if (compare(best.version, current) > 0) best else null
    }

    /** 逐段数值比较，避免 "2.10" 被当成小于 "2.9"。 */
    internal fun compare(a: String, b: String): Int {
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

    private fun com.google.gson.JsonObject.string(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun com.google.gson.JsonObject.bool(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
}
