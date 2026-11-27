package dev.aigw.desktop.overlay

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.aigw.desktop.cef.JcefBrowser
import dev.aigw.desktop.util.UiPrefs
import kotlinx.coroutines.delay
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse

/**
 * 桌面宠物悬浮窗（桌面版）：无边框置顶透明窗口，JCEF 渲染桌宠 H5。
 *
 * 由 Android 的 PetOverlay 移植：同一套 `assets/pet`（pet.html + pixi/live2d
 * 库 + webm 影片），`PetAPI.setModel('film/dafy_video', '大肥鱼 · 视频版')`，
 * 视频底色纯黑用 `mix-blend-mode:screen` 做贴纸感透明。
 *
 * 资源来源：构建期 `syncPetAssets` 把分支内 `app/src/main/assets/pet` 拷进
 * classpath（见 desktop/build.gradle.kts）；[PetAssets] 用虚拟 origin
 * `https://vantagate-pet/` 按需提供，相对路径引用原样可用。
 * 资源缺失时窗口显示缺失提示而非空白（优雅降级）。
 *
 * 拖动：覆盖在浏览器组件上的透明层接管手势（页面本身不需要交互）；
 * 单击：显示当前积分合计（与 Android 的 tapHint 一致）。
 */
object PetAssets {
    const val ORIGIN = "https://vantagate-pet"

    fun available(): Boolean =
        PetAssets::class.java.getResource("/pet/pet.html") != null

    fun bytes(path: String): ByteArray? {
        val safe = path.trimStart('/').replace("\\", "/")
        if (safe.isEmpty() || safe.split('/').any { it == ".." || it == "." }) return null
        return PetAssets::class.java.getResourceAsStream("/pet/$safe")?.use { it.readBytes() }
    }

    fun mimeOf(name: String): String = when (name.substringAfterLast('.').lowercase()) {
        "html", "htm" -> "text/html"
        "js" -> "application/javascript"
        "css" -> "text/css"
        "json" -> "application/json"
        "webm" -> "video/webm"
        "mp4" -> "video/mp4"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "svg" -> "image/svg+xml"
        "wasm" -> "application/wasm"
        else -> "application/octet-stream"
    }
}

class PetResourceHandler : CefRequestHandlerAdapter() {
    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef,
    ): CefResourceRequestHandler? {
        val url = request.url ?: return null
        if (!url.startsWith(PetAssets.ORIGIN)) return null
        disableDefaultHandling.set(true)
        return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(
                b: CefBrowser?,
                f: CefFrame?,
                r: CefRequest,
            ): org.cef.handler.CefResourceHandler {
                val path = runCatching { java.net.URI(r.url).path }.getOrNull() ?: "/"
                val bytes = PetAssets.bytes(path.removePrefix("/"))
                return PetBytesHandler(bytes, PetAssets.mimeOf(path))
            }
        }
    }

    private class PetBytesHandler(
        private val data: ByteArray?,
        private val mime: String,
    ) : CefResourceHandlerAdapter() {
        private var offset = 0
        private val body = data ?: "桌宠资源缺失：pet.html 未打包（构建时需要 app/src/main/assets/pet）"
            .toByteArray(Charsets.UTF_8)

        override fun processRequest(request: CefRequest?, callback: CefCallback): Boolean {
            callback.Continue()
            return true
        }

        override fun getResponseHeaders(
            response: CefResponse,
            responseLength: IntRef,
            redirectUrl: StringRef,
        ) {
            response.status = if (data == null) 404 else 200
            response.mimeType = mime
            response.setHeaderByName("Cache-Control", "no-store", true)
            responseLength.set(body.size)
        }

        override fun readResponse(
            dataOut: ByteArray,
            bytesToRead: Int,
            bytesRead: IntRef,
            callback: CefCallback,
        ): Boolean {
            if (offset >= body.size) {
                bytesRead.set(0)
                return false
            }
            val n = minOf(bytesToRead, body.size - offset)
            System.arraycopy(body, offset, dataOut, 0, n)
            offset += n
            bytesRead.set(n)
            return true
        }

        override fun cancel() {}
    }
}

@Composable
fun PetWindow(
    tapHint: () -> String,
    prefs: UiPrefs,
    visible: Boolean,
    onClose: () -> Unit,
) {
    // 注意：不用 early-return 隐藏——Window 用 visible 参数控制显隐，组合保持，
    // 这样 JCEF 浏览器只建一次、不会反复泄漏。
    var hint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hint) {
        if (hint != null) {
            delay(3000)
            hint = null
        }
    }

    val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
    val sizePx = 300
    val defaultX = (screenSize.width - sizePx - 32).coerceAtLeast(0)
    val defaultY = (screenSize.height - sizePx - 360).coerceAtLeast(0)
    val startX = prefs.getInt("pet_x", defaultX)
    val startY = prefs.getInt("pet_y", defaultY)
    val state = rememberWindowState(
        position = WindowPosition(startX.dp, startY.dp),
        size = DpSize(sizePx.dp, sizePx.dp),
    )

    Window(
        onCloseRequest = onClose,
        state = state,
        visible = visible,
        title = "VantaGate 桌宠",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
    ) {
        var posX by remember { mutableStateOf(startX.dp) }
        var posY by remember { mutableStateOf(startY.dp) }
        Box(modifier = Modifier.size(sizePx.dp)) {
            if (!PetAssets.available()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("桌宠资源未打包\n（需要 app/src/main/assets/pet）", color = Color.White)
                }
            } else {
                val browser = remember {
                    runCatching {
                        JcefBrowser(
                            startUrl = PetAssets.ORIGIN + "/pet.html",
                            transparent = true,
                            requestHandler = PetResourceHandler(),
                            afterLoad = { page ->
                                // pet.html 要先跑完 boot 才有 PetAPI；调用太早会被
                                // film.js 的 ready 检查挡掉，页面就一直停在初始态。
                                Thread {
                                    Thread.sleep(900)
                                    // 视频底色是纯黑（yuv420p 无 alpha）。screen 混合把黑变透明，
                                    // 是唯一能在不重编码视频的前提下做到"贴纸感"的办法。
                                    page.evalVoid(
                                        "(function(){var s=document.createElement('style');" +
                                            "s.textContent='video.petvid{mix-blend-mode:screen !important}" +
                                            "html,body,#stage{background:transparent !important}';" +
                                            "document.head.appendChild(s);})()",
                                    )
                                    page.evalVoid(
                                        "try{if(window.PetAPI){" +
                                            "if(PetAPI.setModel)PetAPI.setModel('film/dafy_video','大肥鱼 · 视频版');" +
                                            "if(PetAPI.setSize)PetAPI.setSize(1,1);" +
                                            "}}catch(e){}",
                                    )
                                }.apply { isDaemon = true }.start()
                            },
                        )
                    }.getOrNull()
                }
                if (browser != null) {
                    SwingPanel(
                        factory = { browser.component },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            // 透明手势层：拖动窗口 + 单击显示积分（页面本身不需要交互）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { hint = tapHint() })
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                with(density) {
                                    prefs.putInt("pet_x", posX.toPx().toInt())
                                    prefs.putInt("pet_y", posY.toPx().toInt())
                                }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            with(density) {
                                posX += dragAmount.x.toDp()
                                posY += dragAmount.y.toDp()
                            }
                            state.position = WindowPosition(posX, posY)
                        }
                    },
                contentAlignment = Alignment.TopCenter,
            ) {
                if (hint != null) {
                    Text(
                        text = hint.orEmpty(),
                        color = Color.White,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
