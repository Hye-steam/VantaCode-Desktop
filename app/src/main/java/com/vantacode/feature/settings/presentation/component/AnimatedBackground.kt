package com.vantacode.feature.settings.presentation.component

import android.graphics.Movie
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.FileInputStream

/**
 * GIF 背景：android.graphics.Movie 逐帧绘制到 Compose Canvas。
 * 零第三方依赖、零 .so，兼容 ABI flavor。
 */
@Composable
internal fun GifBackgroundView(path: String, alpha: Float, modifier: Modifier = Modifier) {
    val movie by produceState<Movie?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching { FileInputStream(path).use { Movie.decodeStream(it) } }.getOrNull()
        }
    }
    val gif = movie ?: return
    val duration = gif.duration().coerceAtLeast(100)

    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(path) {
        val start = android.os.SystemClock.uptimeMillis()
        while (isActive) {
            elapsedMs = android.os.SystemClock.uptimeMillis() - start
            delay(33)
        }
    }

    Canvas(modifier = modifier.fillMaxSize().alpha(alpha)) {
        val frameTime = (elapsedMs % duration).toInt()
        gif.setTime(frameTime)
        val scaleX = size.width / gif.width().coerceAtLeast(1)
        val scaleY = size.height / gif.height().coerceAtLeast(1)
        val scale = maxOf(scaleX, scaleY)
        val dx = (size.width - gif.width() * scale) / 2f
        val dy = (size.height - gif.height() * scale) / 2f
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(dx, dy)
            canvas.nativeCanvas.scale(scale, scale)
            gif.draw(canvas.nativeCanvas, 0f, 0f)
            canvas.nativeCanvas.restore()
        }
    }
}

/**
 * MP4 视频背景：MediaPlayer + TextureView，静音循环播放。
 *
 * 用 TextureView 而非 SurfaceView：SurfaceView 有独立渲染层，
 * Compose 的 alpha 修饰符管不到它，会全不透明盖住 UI。
 *
 * 换视频时只重建 MediaPlayer，复用 TextureView 的 Surface：
 * DisposableEffect(path, surface) 在任一 key 变化时释放旧 MP、建新 MP，
 * 不会卡在旧帧或黑屏。
 */
@Composable
internal fun VideoBackgroundView(path: String, alpha: Float, modifier: Modifier = Modifier) {
    var currentSurface by remember { mutableStateOf<Surface?>(null) }

    // path 或 surface 任一变化就重建 MediaPlayer
    DisposableEffect(path, currentSurface) {
        val surf = currentSurface
        if (surf != null) {
            val mp = MediaPlayer()
            runCatching {
                mp.setDataSource(path)
                mp.setSurface(surf)
                mp.isLooping = true
                mp.setVolume(0f, 0f)
                mp.setOnPreparedListener { it.start() }
                mp.prepareAsync()
            }
            onDispose {
                runCatching { mp.stop(); mp.release() }
            }
        } else {
            onDispose { }
        }
    }

    // 最终销毁时释放 Surface
    DisposableEffect(Unit) {
        onDispose {
            runCatching { currentSurface?.release() }
            currentSurface = null
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize().alpha(alpha),
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surface: android.graphics.SurfaceTexture, w: Int, h: Int
                    ) {
                        currentSurface = Surface(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surface: android.graphics.SurfaceTexture, w: Int, h: Int
                    ) = Unit

                    override fun onSurfaceTextureDestroyed(
                        surface: android.graphics.SurfaceTexture
                    ): Boolean {
                        currentSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(
                        surface: android.graphics.SurfaceTexture
                    ) = Unit
                }
            }
        }
    )
}
