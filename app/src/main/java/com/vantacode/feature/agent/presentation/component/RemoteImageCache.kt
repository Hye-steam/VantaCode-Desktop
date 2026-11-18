package com.vantacode.feature.agent.presentation.component

import android.content.Context
import com.vantacode.core.util.FileLogger
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "RemoteImageCache"

/** 单张网络图片上限：超过不当图片处理，避免模型贴个大文件把聊天卡住。 */
private const val MAX_IMAGE_BYTES = 12L * 1024 * 1024

/**
 * Markdown 正文里的网络图片（`http/https`）落盘缓存。
 *
 * 渲染器本身只认本地文件，所以这里统一把远端图片下成宿主缓存文件再交给解码器：
 * 正文、全屏查看器共用同一份文件，点开看大图不必重新下载。
 *
 * 落地磁盘而不是只进内存：聊天记录滚回来会重新组合，内存缓存命中率低，
 * 而一张图片几百 KB 常驻内存反而更疼。
 */
internal class RemoteImageCache(context: Context) {

    private val dir = File(context.cacheDir, "md-image").apply { mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** url → 缓存文件。命中即返回，避免同一张图在重组时反复发请求。 */
    private val memo = ConcurrentHashMap<String, File>()

    /** 正在下载的 url，防同屏多张同源图并发拉取。 */
    private val inflight = ConcurrentHashMap<String, Any>()

    suspend fun localFile(url: String): File? = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        if (normalized.isEmpty()) return@withContext null
        memo[normalized]?.takeIf { it.isFile && it.length() > 0L }?.let { return@withContext it }

        val target = File(dir, sha256(normalized) + ".img")
        if (target.isFile && target.length() > 0L) {
            memo[normalized] = target
            return@withContext target
        }

        val lock = inflight.getOrPut(normalized) { Any() }
        synchronized(lock) {
            runCatching {
                // 并发等待期间可能已被别的协程写好。
                if (target.isFile && target.length() > 0L) return@synchronized target

                val request = Request.Builder()
                    .url(normalized)
                    .header("Accept", "image/*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        FileLogger.w(TAG, "图片下载失败 ${response.code}: $normalized")
                        return@synchronized null
                    }
                    val body = response.body ?: return@synchronized null
                    if (body.contentLength() > MAX_IMAGE_BYTES) {
                        FileLogger.w(TAG, "图片超过上限 ${body.contentLength()}: $normalized")
                        return@synchronized null
                    }
                    val bytes = body.bytes()
                    if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@synchronized null

                    // 先写临时文件再改名：解码失败时不会在缓存里留下半个文件。
                    val tmp = File(dir, target.name + ".part")
                    tmp.writeBytes(bytes)
                    if (!tmp.renameTo(target)) {
                        tmp.delete()
                        return@synchronized null
                    }
                    memo[normalized] = target
                    target
                }
            }.getOrNull().also { inflight.remove(normalized) }
        }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }
}
