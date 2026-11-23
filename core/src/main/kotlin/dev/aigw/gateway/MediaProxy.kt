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

    /**
     * 媒体缓存目录。App 层启动时通过 [setCacheDir] 注入可写路径；
     * 没注入时回退到共享存储（桌面端 / 有权限的设备）。
     */
    @Volatile
    private var cacheDir: java.io.File? = null

    fun setCacheDir(dir: java.io.File) {
        cacheDir = dir
    }

    private val CACHE_DIR: java.io.File get() =
        cacheDir ?: java.io.File("/sdcard/VantaGate/media")

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

    /**
     * 把 base64 字节直接写成媒体文件，返回绝对路径。
     *
     * 用于 `blob:` 这类只在页面会话内有效的产物：视频动辄几十 MB，
     * 塞进 chat 响应会把客户端和网关一起撞爆，只能落盘后给本地路径。
     */
    fun saveBase64(base64: String, ext: String): String? {
        return try {
            if (!CACHE_DIR.isDirectory && !CACHE_DIR.mkdirs()) return null
            val safeExt = if (ext.startsWith(".")) ext else ".$ext"
            val name = "muse-" + System.currentTimeMillis() + safeExt
            val file = java.io.File(CACHE_DIR, name)
            val bytes = java.util.Base64.getDecoder().decode(base64)
            java.io.FileOutputStream(file).use { it.write(bytes) }
            if (file.isFile && file.length() > 0L) file.absolutePath else null
        } catch (e: Exception) {
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

    // ---------- 本地缓存（blob） ----------

    /**
     * 上游链接会过期的媒体（即梦的图片带 x-expires 签名）先落到本地，
     * 再把网关自己的地址交给客户端，避免几小时后整段对话变成破图。
     *
     * 目录取 tmpdir：Android 上就是应用缓存目录，系统会自己回收。
     */
    private val blobDir: java.io.File by lazy {
        java.io.File(System.getProperty("java.io.tmpdir") ?: ".", "aigw-blob").apply { mkdirs() }
    }

    /** 存一段媒体字节，返回可直接拼进 `/v1/media/blob/<id>` 的 id。 */
    fun store(bytes: ByteArray, ext: String = "jpg"): String? {
        return runCatching {
            val id = java.util.UUID.randomUUID().toString().replace("-", "") + "." + ext
            val file = java.io.File(blobDir, id)
            file.writeBytes(bytes)
            // 顺手清掉一天前的缓存，避免无限增长
            val cutoff = System.currentTimeMillis() - 24 * 3600_000L
            blobDir.listFiles()?.forEach { f ->
                if (f.isFile && f.lastModified() < cutoff) runCatching { f.delete() }
            }
            id
        }.getOrNull()
    }

    /** 按 id 取回媒体字节；不存在返回 null。 */
    fun load(id: String): ByteArray? {
        // 只允许「32 位十六进制 + 扩展名」，挡住路径穿越
        if (!id.matches(Regex("^[0-9a-f]{32}\\.[a-z0-9]{2,5}$"))) return null
        val file = java.io.File(blobDir, id)
        if (!file.isFile) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    /** 网关自身地址，用于生成 blob 链接。 */
    fun blobUrl(id: String): String = "http://127.0.0.1:$port/v1/media/blob/$id"
}
