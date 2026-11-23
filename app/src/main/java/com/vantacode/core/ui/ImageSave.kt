package com.vantacode.core.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 把视频文件存到相册。
 *
 * 视频不走解码：直接搬字节，所以不像图片那样有「存哪一张」的问题。
 * 单个视频可能几十 MB，写之前先看一眼剩余空间，免得写一半卡死。
 */
internal suspend fun saveVideoToGallery(
    context: Context,
    source: File,
    displayName: String
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        if (!source.isFile || source.length() == 0L) error("视频文件不存在或为空")
        val name = buildFileName(displayName, "mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveVideoViaMediaStore(context, source, name)
        } else {
            saveVideoViaLegacyDir(source, name)
        }
    }
}

private fun saveVideoViaMediaStore(context: Context, source: File, name: String): String {
    val values = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, name)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/VantaCode")
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建相册条目")
    try {
        val out = resolver.openOutputStream(uri) ?: error("无法写入相册")
        out.use { sink -> source.inputStream().use { it.copyTo(sink) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
    return uri.toString()
}

@Suppress("DEPRECATION")
private fun saveVideoViaLegacyDir(source: File, name: String): String {
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VantaCode")
    if (!dir.exists() && !dir.mkdirs()) error("无法创建目录 ${dir.absolutePath}")
    val target = File(dir, name)
    source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
    return target.absolutePath
}

/**
 * 把查看器里的图片存到相册。
 *
 * 存的是已经解码好的位图，不是原始文件 / 原 URL：查看器的来源有四类
 * （本地文件、容器路径、base64、网络地址），逐类去取原始字节要各写一套，
 * 而用户长按想留住的正是眼前这一张。代价是 PNG/JPEG 的元数据（EXIF）
 * 会丢，对聊天里的图片没有实际影响。
 */
internal suspend fun saveImageToGallery(
    context: Context,
    bitmap: ImageBitmap,
    displayName: String
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val android = bitmap.asAndroidBitmap()
        val safeName = buildFileName(displayName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, android, safeName)
        } else {
            saveViaLegacyDir(android, safeName)
        }
    }
}

private fun buildFileName(displayName: String, ext: String = "png"): String {
    val base = displayName
        .substringBeforeLast('.', displayName)
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_')
        .take(48)
        .ifEmpty { "image" }
    return "$base-${System.currentTimeMillis()}.$ext"
}

/** Android 10+：走 MediaStore，不需要任何存储权限。 */
private fun saveViaMediaStore(context: Context, bitmap: Bitmap, name: String): String {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/VantaCode")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建相册条目")
    try {
        val stream = resolver.openOutputStream(uri) ?: error("无法写入相册")
        stream.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (e: Throwable) {
        // 写入失败要把占位条目删掉，否则相册里留一条 0 字节的坏图
        runCatching { resolver.delete(uri, null, null) }
        throw e
    }
    return uri.toString()
}

/** Android 9 及以下：写公共图片目录。旧系统的相册扫描靠 MediaScanner。 */
@Suppress("DEPRECATION")
private fun saveViaLegacyDir(bitmap: Bitmap, name: String): String {
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "VantaCode")
    if (!dir.exists() && !dir.mkdirs()) error("无法创建目录 ${dir.absolutePath}")
    val file = File(dir, name)
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file.absolutePath
}
