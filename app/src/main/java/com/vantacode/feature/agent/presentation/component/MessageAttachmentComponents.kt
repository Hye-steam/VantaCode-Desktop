package com.vantacode.feature.agent.presentation.component

import com.vantacode.feature.agent.presentation.isVideo
import com.vantacode.feature.agent.presentation.isHtml
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vantacode.R
import com.vantacode.feature.agent.presentation.AgentAttachment
import com.vantacode.feature.workspace.domain.FileAccessProvider
import compose.icons.FeatherIcons
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Image
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 附件缩略图边长：一行一个文件时左侧的小图，够看清是截图还是照片即可。 */
private val AttachmentThumbSize = 44.dp

/**
 * 已发送 / 已生成附件的列表：**一行一个文件**。
 */
@Composable
internal fun MessageAttachmentList(
    attachments: List<AgentAttachment>,
    onClick: ((AgentAttachment) -> Unit)? = null,
    /** 用户自己发出的附件：只给缩略图行，不就地展开播放器与页面渲染。 */
    compact: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        attachments.forEach { attachment ->
            when {
                compact -> MessageAttachmentRow(attachment = attachment, onClick = onClick)
                attachment.isVideo -> VideoClip(attachment)
                attachment.isHtml -> HtmlClip(attachment)
                else -> MessageAttachmentRow(attachment = attachment, onClick = onClick)
            }
        }
    }
}

@Composable
private fun MessageAttachmentRow(
    attachment: AgentAttachment,
    onClick: ((AgentAttachment) -> Unit)? = null
) {
    val fallbackName = stringResource(R.string.common_file)
    val name = attachment.fileName.ifBlank { fallbackName }
    val actionLabel = stringResource(R.string.chat_attachment_open, name)
    val meta = remember(attachment.sizeBytes, attachment.containerPath) {
        listOf(formatBytes(attachment.sizeBytes), attachment.containerPath)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClickLabel = actionLabel, onClick = { onClick(attachment) })
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttachmentThumb(attachment = attachment)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClick != null) {
                Icon(
                    FeatherIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentThumb(attachment: AgentAttachment) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(AttachmentThumbSize)
    ) {
        if (attachment.isImage) {
            AttachmentImageThumb(attachment = attachment)
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    FeatherIcons.FileText,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AttachmentImageThumb(attachment: AgentAttachment) {
    val path = attachment.localPath
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }
                android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        ComposeImage(
            bitmap = loaded,
            contentDescription = attachment.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                FeatherIcons.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

fun interface AttachmentOpener {
    fun open(attachment: AgentAttachment)
}

internal val LocalAttachmentOpener = staticCompositionLocalOf { AttachmentOpener { } }

/** 附件读取用的 FileAccessProvider。 */
internal val LocalAttachmentFileAccess = staticCompositionLocalOf<FileAccessProvider?> { null }

internal suspend fun openSentAttachment(
    context: Context,
    attachment: AgentAttachment,
    fileAccess: FileAccessProvider
) {
    val local = withContext(Dispatchers.IO) {
        resolveLocalFile(attachment, fileAccess)
    }
    if (local == null) {
        Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
        return
    }
    if (isApk(attachment)) {
        val installable = withContext(Dispatchers.IO) {
            if (isShareable(context, local)) local else copyToShareCache(context, local)
        }
        if (installable == null) {
            Toast.makeText(context, "文件无法分享", Toast.LENGTH_SHORT).show()
            return
        }
        com.vantacode.core.util.ApkInstaller.install(context, installable)
        return
    }
    val shareable = withContext(Dispatchers.IO) {
        if (isShareable(context, local)) local else copyToShareCache(context, local)
    }
    val uri = shareable?.let {
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        }.getOrNull()
    }
    if (uri == null) {
        Toast.makeText(context, "文件无法分享", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, attachment.mimeType.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "没有可用的应用打开此文件", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun resolveLocalFile(attachment: AgentAttachment, fileAccess: FileAccessProvider): File? {
    attachment.localPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.isFile }?.let { return it }
    val containerPath = attachment.containerPath
    if (containerPath.isBlank()) return null
    return runCatching { fileAccess.copyToLocal(containerPath) }.getOrNull()?.takeIf { it.isFile }
}

private fun isShareable(context: Context, file: File): Boolean = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}.isSuccess

private fun copyToShareCache(context: Context, source: File): File? = runCatching {
    val dir = File(File(context.cacheDir, "sent_files"), source.absolutePath.hashCode().toString())
    dir.mkdirs()
    val target = File(dir, source.name.ifBlank { "file" })
    source.copyTo(target, overwrite = true)
    target
}.getOrNull()

private fun isApk(attachment: AgentAttachment): Boolean {
    if (attachment.mimeType.equals("application/vnd.android.package-archive", ignoreCase = true)) return true
    val name = attachment.fileName.lowercase()
    return name.endsWith(".apk") || name.endsWith(".apks") || name.endsWith(".xapk")
}

/**
 * 视频附件：直接内联播放。
 */
@Composable
private fun VideoClip(attachment: AgentAttachment) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileAccess = LocalAttachmentFileAccess.current
    var fullscreenUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var preparing by remember(attachment.localPath) { mutableStateOf(true) }
    var loadError by remember(attachment.localPath) { mutableStateOf<String?>(null) }

    val mediaUri by produceState<android.net.Uri?>(
        initialValue = null,
        attachment.localPath, attachment.containerPath, fileAccess
    ) {
        value = if (fileAccess == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "media-preview").apply { mkdirs() }
                val stamp = attachment.sizeBytes.toString() + "-" + attachment.fileName.hashCode()
                val target = File(dir, stamp + "-" + attachment.fileName.ifBlank { "clip.mp4" })
                if (!target.isFile || target.length() == 0L) {
                    val copied = runCatching { fileAccess.copyToLocal(attachment.containerPath) }
                        .getOrNull()
                        ?.takeIf { it.isFile && it.length() > 0L }
                    if (copied != null) {
                        copied.inputStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    } else {
                        val bytes = runCatching { fileAccess.readBytes(attachment.containerPath) }
                            .getOrNull()
                            ?.takeIf { it.isNotEmpty() }
                            ?: runCatching { fileAccess.readBytes(attachment.localPath) }.getOrNull()
                            ?: throw IllegalStateException("视频为空")
                        target.writeBytes(bytes)
                    }
                }
                android.net.Uri.fromFile(target)
            }.onFailure { loadError = it.message ?: it.javaClass.simpleName }.getOrNull()
        }
        preparing = false
    }

    val shown = mediaUri
    if (shown == null) {
        if (preparing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "正在准备视频…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            MessageAttachmentRow(attachment = attachment, onClick = null)
            Text(
                text = "无法读取视频文件：${loadError ?: "未知原因"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    } else {
        InlineVideoPlayer(
            mediaUri = shown,
            modifier = Modifier.padding(top = 4.dp),
            onFullscreen = { fullscreenUri = shown }
        )
    }
    fullscreenUri?.let { target ->
        FullscreenVideoDialog(mediaUri = target, onDismiss = { fullscreenUri = null })
    }
}

/** HTML 附件：内联 WebView 渲染，可切源码、可全屏。 */
@Composable
private fun HtmlClip(attachment: AgentAttachment) {
    val fileAccess = LocalAttachmentFileAccess.current
    var fullscreen by remember { mutableStateOf(false) }
    val html by produceState<String?>(
        initialValue = null,
        attachment.containerPath, fileAccess
    ) {
        value = if (fileAccess == null) null else withContext(Dispatchers.IO) {
            runCatching { fileAccess.copyToLocal(attachment.containerPath).readText() }.getOrElse {
                runCatching { fileAccess.readFile(attachment.containerPath) }.getOrElse {
                    runCatching { fileAccess.copyToLocal(attachment.localPath).readText() }.getOrNull()
                }
            }
        }
    }

    val content = html
    if (content == null) {
        MessageAttachmentRow(attachment = attachment, onClick = null)
    } else {
        InlineHtmlView(
            html = content,
            modifier = Modifier.padding(top = 4.dp),
            onFullscreen = { fullscreen = true }
        )
    }
    if (fullscreen && content != null) {
        FullscreenHtmlDialog(html = content, onDismiss = { fullscreen = false })
    }
}
