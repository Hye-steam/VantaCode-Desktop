package dev.aigw.gateway

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 图片/视频的外链中转。
 *
 * 豆包返回的媒体 URL 在 byteimg.com 上，客户端内嵌渲染时不一定加载得出来
 * （长 URL、签名参数、以及不同图片库对远端域名的策略差异）。这里把媒体换成本地
 * 网关地址，由网关去上游取回原始字节再转发——客户端只需要访问 127.0.0.1。
 *
 * 只允许白名单域名，避免变成一个能访问任意地址的开放代理。
 */
object MediaProxy {

    /** 由 Engine 在启动时写入，Provider 拼链接时要用。 */
    @Volatile
    var port: Int = 8790

    /** 允许中转的域名后缀。 */
    private val ALLOWED_HOSTS = listOf(
        "byteimg.com",
        "bytecdn.cn",
        "doubao.com",
        "volccdn.com",
    )

    fun allowed(target: String): Boolean {
        val host = runCatching { java.net.URI(target).host ?: return false }.getOrNull() ?: return false
        return ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
    }

    /** 图片缓存目录。放在共享存储，客户端能直接读。 */
    private val CACHE_DIR = java.io.File("/sdcard/VantaGate/media")

    /**
     * 把远端媒体落到本地文件，返回文件绝对路径。
     *
     * VantaCode 的 Markdown 图片渲染器只认本地路径（含 :// 的一律不渲染），
     * 所以这里必须先把字节拿到手再给路径。失败返回 null，由调用方退回原 URL。
     */
    fun localize(url: String): String? {
        val name = Integer.toHexString(url.hashCode()) + guessExt(url)
        val file = java.io.File(CACHE_DIR, name)
        if (file.isFile && file.length() > 0L) return file.absolutePath
        return try {
            if (!CACHE_DIR.isDirectory && !CACHE_DIR.mkdirs()) return null
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36")
                setRequestProperty("Referer", "https://www.doubao.com/")
            }
            try {
                if (conn.responseCode !in 200..299) return null
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(file).use { out -> input.copyTo(out) }
                }
            } finally {
                runCatching { conn.disconnect() }
            }
            if (file.isFile && file.length() > 0L) file.absolutePath else null
        } catch (e: Exception) {
            runCatching { file.delete() }
            null
        }
    }

    private fun guessExt(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".jpeg") || path.endsWith(".jpg") -> ".jpg"
            path.endsWith(".png") -> ".png"
            path.endsWith(".webp") -> ".webp"
            path.endsWith(".gif") -> ".gif"
            path.endsWith(".mp4") -> ".mp4"
            else -> ".jpg"
        }
    }

    /** 把上游媒体 URL 换成本地代理地址。 */
    fun wrap(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return url
        val enc = runCatching { URLEncoder.encode(url, "UTF-8") }.getOrNull() ?: return url
        return "http://127.0.0.1:$port/v1/media?u=$enc"
    }

    fun decode(raw: String): String? = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()
}
