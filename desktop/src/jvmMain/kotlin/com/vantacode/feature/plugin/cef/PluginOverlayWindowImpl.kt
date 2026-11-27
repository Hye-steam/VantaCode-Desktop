package com.vantacode.feature.plugin.cef

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.plugin.runtime.OverlayDragRegion
import com.vantacode.feature.plugin.runtime.PluginOverlayWindowFactory
import com.vantacode.feature.plugin.runtime.PluginOverlayWindowHandle
import com.vantacode.feature.plugin.runtime.PluginOverlayWindowRequest
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 桌面浮层窗口的 JCEF 实现（对照 Android `PluginOverlayWindow` + `PluginOverlayManager.createWindow`）。
 *
 * 窗口技术：AWT [JFrame]（而不是 Compose Window）——JCEF 的 OSR 浏览器组件是 Swing 组件，
 * 直接放在 JFrame 里最稳定；Compose Window + SwingPanel 的透明组合在 Windows 上容易黑底。
 * - 无边框、背景全透明、置顶、`Window.Type.UTILITY`（不在任务栏出现）、
 *   `focusableWindowState = false`（默认不抢焦点、不出现在 Alt-Tab 切换里）。
 * - 浏览器：沿用插件页的 [PluginCefRequestHandler]（同一虚拟 origin、同一 aicode.js 桥 shim），
 *   `createBrowser(url, isOffscreen = true, isTransparent = true)`，页面透明底才能透出桌宠形状。
 * - 拖拽：拖拽区上盖一层透明 [JPanel] 手柄（Android 的 dragHandle 同款思路），
 *   只有手柄区域拦截鼠标，其余区域正常交给页面；按下不动松手时按 Android 同款
 *   以 `{type:"click"}` 事件回传给页面，而不是把点击吞掉；按住 520ms 不动发 `longPress`。
 * - 位置/尺寸/可见性的唯一事实来源是 [com.vantacode.feature.plugin.runtime.PluginOverlayManager]，
 *   这里只执行 `apply*`；拖拽结束的位置由 Manager 决定是否吸附、持久化。
 *
 * 只在插件调用 `host.overlay.create` 时建窗，启动时不出现，也没有内置桌宠。
 */
class CefPluginOverlayWindowFactory(
    private val hostAsset: (String) -> ByteArray?,
    private val themeCss: () -> String
) : PluginOverlayWindowFactory {

    override suspend fun create(request: PluginOverlayWindowRequest): PluginOverlayWindowHandle {
        val window = PluginOverlayWindowImpl(request, hostAsset, themeCss)
        window.create()
        return window
    }
}

internal class PluginOverlayWindowImpl(
    private val request: PluginOverlayWindowRequest,
    private val hostAsset: (String) -> ByteArray?,
    private val themeCss: () -> String
) : PluginOverlayWindowHandle {

    override val overlayId: String get() = request.overlayId

    private lateinit var frame: JFrame
    private lateinit var client: org.cef.CefClient
    private lateinit var browser: CefBrowser
    private lateinit var browserComponent: Component
    private var dragPanel: JPanel? = null
    private lateinit var bridge: PluginOverlayCefBridgeHandler
    private val disposed = AtomicBoolean(false)

    @Volatile
    private var loaded = false
    private val pendingLock = Any()
    private val pendingScripts = mutableListOf<String>()

    @Volatile
    private var currentRegion: OverlayDragRegion =
        OverlayDragRegion(0f, 0f, request.width.toFloat(), request.height.toFloat())

    @Volatile
    private var currentWidth: Int = request.width

    @Volatile
    private var currentHeight: Int = request.height

    /** CEF 初始化（首次解包 ~163MiB）放 IO 线程；JFrame 组装放 AWT 线程。 */
    suspend fun create() {
        withContext(Dispatchers.IO) { PluginCef.app() }
        val requestHandler = PluginCefRequestHandler(
            installDir = request.page.installDir,
            hostAsset = hostAsset,
            themeCss = themeCss
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        bridge = PluginOverlayCefBridgeHandler(
            pluginId = request.page.pluginId,
            overlayId = request.overlayId,
            page = request.page,
            scope = scope,
            delegate = request.bridgeDelegate,
            createOverlay = request.createOverlay,
            updateOverlay = request.updateOverlay,
            authorized = { !disposed.get() }
        )
        val client = withContext(Dispatchers.IO) {
            val created = PluginCef.newClient(requestHandler, bridge)
            created.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain == true) flushPending()
                }
            })
            created
        }
        this.client = client
        val url = "${PluginCefRequestHandler.ORIGIN}${PluginCefRequestHandler.PLUGIN}${request.page.pageFile}"
        try {
            browser = withContext(Dispatchers.IO) {
                // OSR + 透明：桌宠页面透明底才能透出形状；配置页用的是 (url, true, false)。
                client.createBrowser(url, true, true)
            }
            runOnEdtAndWait { buildFrame() }
        } catch (e: Exception) {
            runCatching {
                if (::browser.isInitialized) PluginCef.closePage(client, browser) else client.dispose()
            }
            throw e
        }
    }

    private fun buildFrame() {
        frame = JFrame().apply {
            isUndecorated = true
            // 全透明 JFrame + 透明 OSR 浏览器：页面非透明区域正常绘制，透明区域透出桌面。
            background = Color(0, 0, 0, 0)
            type = Window.Type.UTILITY
            isAlwaysOnTop = true
            // 不抢焦点：点桌宠不会把主窗口顶掉，也不会出现在 Alt-Tab 里。
            focusableWindowState = false
            defaultCloseOperation = JFrame.DO_NOTHING_ON_CLOSE
            title = "VantaCode overlay ${request.overlayId}"
            setBounds(request.x, request.y, request.width, request.height)
        }
        browserComponent = browser.uiComponent
        browserComponent.setBounds(0, 0, request.width, request.height)
        frame.layeredPane.add(browserComponent, JLayeredPane.DEFAULT_LAYER)
        if (request.surface.draggable) {
            val panel = JPanel().apply {
                isOpaque = false
                setBounds(0, 0, request.width, request.height)
            }
            attachDrag(panel)
            dragPanel = panel
            frame.layeredPane.add(panel, JLayeredPane.PALETTE_LAYER)
        }
        frame.isVisible = true
    }

    private fun attachDrag(panel: JPanel) {
        var downScreen: Point? = null
        var downFramePos: Point? = null
        var dragging = false
        var longPressFired = false
        val longPressTimer = Timer(LONG_PRESS_MS) {
            if (!dragging && downScreen != null) {
                longPressFired = true
                request.listener.onLongPress(request.overlayId)
            }
        }.apply { isRepeats = false }
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e == null || disposed.get()) return
                downScreen = e.locationOnScreen
                downFramePos = frame.location
                dragging = false
                longPressFired = false
                longPressTimer.restart()
            }

            override fun mouseReleased(e: MouseEvent?) {
                longPressTimer.stop()
                if (downScreen == null) return
                if (dragging) {
                    request.listener.onDragEnd(request.overlayId, frame.x, frame.y)
                } else if (!longPressFired) {
                    // 未拖动：按 Android 同款把点击以事件回传给页面，而不是吞掉。
                    request.listener.onClick(request.overlayId)
                }
                dragging = false
                downScreen = null
                downFramePos = null
            }
        })
        panel.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent?) {
                val down = downScreen ?: return
                val start = downFramePos ?: return
                if (e == null || disposed.get()) return
                val dx = e.xOnScreen - down.x
                val dy = e.yOnScreen - down.y
                if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > DRAG_THRESHOLD_PX) {
                    dragging = true
                    longPressTimer.stop()
                    request.listener.onDragStart(request.overlayId)
                }
                if (dragging) {
                    val screen = Toolkit.getDefaultToolkit().screenSize
                    val nx = (start.x + dx).coerceIn(0, (screen.width - frame.width).coerceAtLeast(0))
                    val ny = (start.y + dy).coerceIn(0, (screen.height - frame.height).coerceAtLeast(0))
                    frame.setLocation(nx, ny)
                }
            }
        })
    }

    override fun postMessage(payload: JsonElement) {
        if (disposed.get()) return
        val script = "window.__aicodeOverlayMessage(${jsString(payload.toString())})"
        synchronized(pendingLock) {
            if (!loaded) {
                // 页面未加载完时先排队，加载完成后再发，避免插件启动消息丢失。
                pendingScripts += script
                return
            }
        }
        evaluate(script)
    }

    override fun applyGeometry(x: Int, y: Int, width: Int, height: Int) {
        currentWidth = width
        currentHeight = height
        runOnEdt {
            if (disposed.get() || !::frame.isInitialized) return@runOnEdt
            frame.setBounds(x, y, width, height)
            browserComponent.setBounds(0, 0, width, height)
            layoutDragPanel()
        }
    }

    override fun applyDragRegion(region: OverlayDragRegion?) {
        if (region != null) currentRegion = region
        runOnEdt {
            if (disposed.get() || !::frame.isInitialized) return@runOnEdt
            layoutDragPanel()
        }
    }

    override fun applyVisibility(visible: Boolean) {
        runOnEdt {
            if (disposed.get() || !::frame.isInitialized) return@runOnEdt
            frame.isVisible = visible
        }
    }

    override fun dispose() {
        if (!disposed.compareAndSet(false, true)) return
        runOnEdt {
            runCatching { bridge.markClosed() }
            runCatching {
                if (::client.isInitialized && ::browser.isInitialized) {
                    PluginCef.closePage(client, browser)
                }
            }
            runCatching { if (::frame.isInitialized) frame.dispose() }
        }
    }

    private fun layoutDragPanel() {
        val panel = dragPanel ?: return
        val region = currentRegion
        val left = region.left.toInt().coerceIn(0, currentWidth)
        val top = region.top.toInt().coerceIn(0, currentHeight)
        val width = region.width.toInt().coerceIn(0, currentWidth - left)
        val height = region.height.toInt().coerceIn(0, currentHeight - top)
        panel.setBounds(left, top, width, height)
    }

    private fun flushPending() {
        val scripts = synchronized(pendingLock) {
            loaded = true
            val copy = pendingScripts.toList()
            pendingScripts.clear()
            copy
        }
        scripts.forEach { evaluate(it) }
    }

    private fun evaluate(script: String) {
        SwingUtilities.invokeLater {
            if (disposed.get() || !::browser.isInitialized) return@invokeLater
            runCatching { browser.executeJavaScript(script, browser.url, 0) }
                .onFailure { FileLogger.w(TAG, "overlay evaluate failed: $overlayId", it) }
        }
    }

    private fun runOnEdt(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    private fun runOnEdtAndWait(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeAndWait(block)
    }

    /** 把字符串转成 JS 字面量（与桥同款）。 */
    private fun jsString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(c)
            }
        }
        append('"')
    }

    private companion object {
        const val TAG = "PluginOverlay"
        const val LONG_PRESS_MS = 520
        const val DRAG_THRESHOLD_PX = 4
    }
}
