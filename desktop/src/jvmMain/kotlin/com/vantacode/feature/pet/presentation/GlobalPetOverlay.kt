/*
 * VantaCode Desktop — 全局桌宠 overlay：独立无边框透明 always-on-top 小窗口，
 * 显示在所有应用之上、可拖到屏幕任意位置；应用内 VantaPetOverlay 已移除。
 * 使用 assets/pet/sticker.gif（16 帧透明贴纸序列）作为本体，Java2D 即时绘制。
 * 动画：待机 f0↔f9 呼吸、久闲睡 f4、完工 f1、失败 f10、交互挥手 f15；叠层整身微动（呼吸/歪头/完工弹跳）。
 * 开关在设置 → 通用 → 桌宠，通过 enabledFlow 实时显示/隐藏，不需要重启。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.feature.pet.presentation

import com.vantacode.core.platform.DesktopAssets
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.presentation.AgentUIState
import com.vantacode.feature.pet.data.PetRuntimeState
import com.vantacode.feature.pet.data.PetSettingsRepository
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 全局桌宠：AWT 线程自治的透明小窗口。窗口=宠物大小（气泡显示时向上多出一条），
 * 透明区域点穿——只在点到宠物本体时才拦截。
 * 开关由 enabledFlow 驱动；agent 状态经 [PetRuntimeState] 同步。
 */
object GlobalPetOverlay {
    private const val TAG = "GlobalPetOverlay"
    private const val PET_SIZE_DP = 116
    private const val BUBBLE_DP = 32
    private const val SPRITE_MAX_EDGE = 384
    private const val DRAG_TAP_SLOP_PX = 6

    // 贴纸帧号（sticker.gif 16 帧）
    private const val FRAME_IDLE_A = 0        // 待机正面
    private const val FRAME_PLEASED = 1       // 开心大笑
    private const val FRAME_SLEEP = 4         // 打盹（Z）
    private const val FRAME_LOOKING = 9       // 平视打量
    private const val FRAME_SAD = 10          // 委屈（失败）
    private const val FRAME_WAVE = 15         // 挥手
    private const val FRAME_IDLE_B = 9        // 待机交替

    const val DEFAULT_X = 0.86f
    const val DEFAULT_Y = 0.78f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var window: PetOverlayWindow? = null
    private var timer: Timer? = null
    private var frames: List<BufferedImage> = emptyList()
    private var staticSprite: BufferedImage? = null

    @Volatile private var fractionX = DEFAULT_X
    @Volatile private var fractionY = DEFAULT_Y
    @Volatile private var transientExpression: PetExpression? = null
    @Volatile private var transientSince = 0L
    @Volatile private var bubbleVisible = false
    @Volatile private var bubbleUntil = 0L
    @Volatile private var tapWaveUntil = 0L
    @Volatile private var lastActivityAt = System.currentTimeMillis()

    /** 启动 overlay（幂等）。 */
    fun start() {
        if (timer != null) return
        val repository = com.vantacode.di.AppGraph.petSettingsRepository
        scope.launch {
            repository.enabledFlow.collect { enabled ->
                SwingUtilities.invokeLater { if (enabled) showWindow() else hideWindow() }
            }
        }
        scope.launch {
            repository.positionFlow.collect { (x, y) ->
                fractionX = x
                fractionY = y
                SwingUtilities.invokeLater { applyBounds() }
            }
        }
        scope.launch {
            PetRuntimeState.state.collect { state ->
                if (state != AgentUIState.Idle) lastActivityAt = System.currentTimeMillis()
                val transient = when (state) {
                    is AgentUIState.Result -> PetExpression.PLEASED
                    is AgentUIState.Error -> PetExpression.DOWNCAST
                    else -> null
                }
                transientExpression = transient
                transientSince = if (transient != null) System.currentTimeMillis() else 0L
                SwingUtilities.invokeLater { window?.panel?.repaint() }
            }
        }
    }

    fun stop() {
        SwingUtilities.invokeLater { hideWindow() }
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun dpiScale(): Float =
        (java.awt.Toolkit.getDefaultToolkit().screenResolution / 96f).coerceIn(1f, 2.5f)

    private fun petSizePx(): Int = (PET_SIZE_DP * dpiScale()).roundToInt()

    private fun bubbleStripPx(): Int = (BUBBLE_DP * dpiScale()).roundToInt()

    private fun virtualBounds(): Rectangle {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        for (device in GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices) {
            val b = device.defaultConfiguration.bounds
            minX = minOf(minX, b.x); minY = minOf(minY, b.y)
            maxX = maxOf(maxX, b.x + b.width); maxY = maxOf(maxY, b.y + b.height)
        }
        return Rectangle(minX, minY, maxX - minX, maxY - minY)
    }

    /** 贴纸帧表：assets/pet/frames/frame_00..15.png（已是透明 PNG）。 */
    private val FRAME_ASSETS = (0 until 16).map { i -> "pet/frames/frame_%02d.png".format(i) }

    /** 从 assets/pet/frames 读帧；全部成功才用，否则回落 vanta.png 静态图。 */
    private fun loadFrames(): List<BufferedImage> {
        val loaded = FRAME_ASSETS.mapNotNull { path ->
            runCatching { DesktopAssets.open(path).use { ImageIO.read(it) } }
                .onFailure { FileLogger.w(TAG, "桌宠帧加载失败: $path (${it.message})") }
                .getOrNull()
        }
        frames = if (loaded.size == FRAME_ASSETS.size) loaded else emptyList()
        if (frames.isEmpty()) {
            FileLogger.w(TAG, "桌宠帧不完整，回落为静态图")
            staticSprite = loadStaticSprite()
        }
        return frames
    }

    private fun loadStaticSprite(): BufferedImage? = runCatching {
        val src = DesktopAssets.open("pet/vanta.png").use { input -> ImageIO.read(input) } ?: return null
        val scale = minOf(1f, SPRITE_MAX_EDGE.toFloat() / maxOf(src.width, src.height))
        if (scale >= 1f) return@runCatching src
        val w = maxOf(1, (src.width * scale).roundToInt())
        val h = maxOf(1, (src.height * scale).roundToInt())
        val target = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = target.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        target
    }.onFailure { FileLogger.e(TAG, "桌宠素材加载失败", it) }.getOrNull()

    private fun showWindow() {
        if (window != null) return
        loadFrames()
        if (frames.isEmpty() && staticSprite == null) return
        val w = PetOverlayWindow()
        w.panel.setFrameSources(frames, staticSprite)
        attachMouseListeners(w)
        window = w
        bubbleVisible = false
        lastActivityAt = System.currentTimeMillis()
        timer?.stop()
        timer = Timer(16) {
            if (bubbleVisible && System.currentTimeMillis() >= bubbleUntil) {
                bubbleVisible = false
                applyBounds()
            }
            window?.panel?.repaint()
        }.apply { start() }
        applyBoundsTo(w)
        w.isVisible = true
        FileLogger.i(TAG, "全局桌宠已显示（frames=${frames.size}, static=${staticSprite != null}）")
    }

    private fun hideWindow() {
        timer?.stop()
        timer = null
        bubbleVisible = false
        window?.dispose()
        window = null
        FileLogger.i(TAG, "全局桌宠已隐藏")
    }

    private fun attachMouseListeners(w: PetOverlayWindow) {
        w.panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                val src = e.component
                val origin = src.locationOnScreen
                w.dragAnchor = java.awt.Point(origin.x + e.x, origin.y + e.y)
                w.moved = false
                w.pressTime = System.currentTimeMillis()
                lastActivityAt = System.currentTimeMillis()
            }

            override fun mouseReleased(e: MouseEvent) {
                w.dragAnchor = null
                lastActivityAt = System.currentTimeMillis()
                if (w.moved) {
                    persistPosition(w)
                } else if (System.currentTimeMillis() - w.pressTime < 400) {
                    bubbleVisible = true
                    bubbleUntil = System.currentTimeMillis() + 2200
                    tapWaveUntil = System.currentTimeMillis() + 1800
                    applyBounds()
                    w.panel.repaint()
                }
            }
        })
        w.panel.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val anchor = w.dragAnchor ?: return
                val origin = (e.component as java.awt.Component).locationOnScreen
                val cur = java.awt.Point(origin.x + e.x, origin.y + e.y)
                val dx = cur.x - anchor.x
                val dy = cur.y - anchor.y
                if (!w.moved && Math.hypot(dx.toDouble(), dy.toDouble()) > DRAG_TAP_SLOP_PX) {
                    w.moved = true
                }
                if (w.moved) {
                    lastActivityAt = System.currentTimeMillis()
                    w.setLocation(w.x + dx, w.y + dy)
                    w.dragAnchor = cur
                }
            }
        })
    }

    private fun persistPosition(w: PetOverlayWindow) {
        val b = virtualBounds()
        val size = petSizePx()
        val strip = if (bubbleVisible) bubbleStripPx() else 0
        val fy = if (b.height - size - strip > 0) ((w.y - b.y).toFloat() / (b.height - size - strip)).coerceIn(0f, 1f) else DEFAULT_Y
        val fx = if (b.width - size > 0) ((w.x - b.x).toFloat() / (b.width - size)).coerceIn(0f, 1f) else DEFAULT_X
        fractionX = fx
        fractionY = fy
        val repository = com.vantacode.di.AppGraph.petSettingsRepository
        scope.launch { repository.savePosition(fx, fy) }
    }

    internal fun applyBounds() {
        val w = window ?: return
        applyBoundsTo(w)
    }

    private fun applyBoundsTo(w: PetOverlayWindow) {
        val size = petSizePx()
        val strip = if (bubbleVisible) bubbleStripPx() else 0
        val petScreenYBefore = w.y + (if (w.lastBubbleStrip != null) w.lastBubbleStrip!! else 0)
        val height = size + strip
        val b = virtualBounds()
        val x = (b.x + fractionX * (b.width - size)).roundToInt()
            .coerceIn(b.x, maxOf(b.x, b.x + b.width - size))
        var y: Int
        if (w.lastBubbleStrip == null) {
            y = (b.y + fractionY * (b.height - height)).roundToInt()
        } else {
            y = petScreenYBefore - strip
        }
        y = y.coerceIn(b.y, maxOf(b.y, b.y + b.height - height))
        w.setBounds(x, y, size, height)
        w.lastBubbleStrip = strip
    }

    internal fun currentExpression(): PetExpression {
        val transient = transientExpression
        if (transient != null && transientSince > 0L && System.currentTimeMillis() - transientSince < 3000) {
            return transient
        }
        return expressionOf(PetRuntimeState.state.value)
    }

    private fun bubbleActive(): Boolean {
        val now = System.currentTimeMillis()
        if (bubbleVisible && now < bubbleUntil) return true
        return currentExpression() != PetExpression.IDLE
    }

    /** 窗口：无边框透明 always-on-top，不占任务栏/Alt-Tab。 */
    internal class PetOverlayWindow : JWindow() {
        val panel = PetPanel()
        var dragAnchor: java.awt.Point? = null
        var moved = false
        var pressTime = 0L
        var lastBubbleStrip: Int? = null

        init {
            type = java.awt.Window.Type.UTILITY
            background = Color(0, 0, 0, 0)
            isAlwaysOnTop = true
            contentPane = panel
        }
    }

    /** 选帧+整体微动（身体呼吸/歪头/完工弹跳）。 */
    internal class PetPanel : JPanel() {
        @Volatile private var frames: List<BufferedImage> = emptyList()
        @Volatile private var staticSprite: BufferedImage? = null

        private var idleToggle = false
        private var nextIdleToggleAt = System.currentTimeMillis() + 2000L
        private var lastExpression = PetExpression.IDLE

        init {
            isOpaque = false
            background = Color(0, 0, 0, 0)
        }

        fun setFrameSources(frameList: List<BufferedImage>, staticImg: BufferedImage?) {
            frames = frameList
            staticSprite = staticImg
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2 = g as? Graphics2D ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

            val dpi = dpiScale()
            val petSize = petSizePx()
            val bubble = bubbleActive()
            val strip = if (bubble) bubbleStripPx() else 0
            val petX = (width - petSize) / 2
            val petY = strip + (height - strip - petSize) / 2

            val now = System.currentTimeMillis()
            val expression = currentExpression()
            val phase = (now % 2600L) / 2600.0
            val phaseSlow = (now % 4600L) / 4600.0

            // 待机交替帧
            if (now >= nextIdleToggleAt) {
                idleToggle = !idleToggle
                nextIdleToggleAt = now + 2200L + (Math.random() * 900).toLong()
            }

            val sleeping = expression == PetExpression.IDLE && now - lastActivityAt > 45_000L
            val frameIndex = when {
                now < tapWaveUntil -> FRAME_WAVE
                expression == PetExpression.PLEASED -> FRAME_PLEASED
                expression == PetExpression.DOWNCAST -> FRAME_SAD
                expression == PetExpression.LOOKING -> FRAME_LOOKING
                expression == PetExpression.STREAMING -> FRAME_LOOKING
                sleeping -> FRAME_SLEEP
                idleToggle -> FRAME_IDLE_B
                else -> FRAME_IDLE_A
            }

            val frameImage = frames.getOrNull(frameIndex) ?: staticSprite

            // 全身微动
            val bobPx = (sin(phase * 2 * PI) * 1.8f * dpi).toFloat()
            val breathe = 1f + 0.014f * sin(phase * 2 * PI).toFloat()
            val tiltDeg = (if (expression == PetExpression.LOOKING) sin(phase * 2 * PI).toFloat() * 1.4f else 0f)
            val droopRot = if (expression == PetExpression.DOWNCAST) 4f else 0f
            val hopPx = if (expression == PetExpression.PLEASED && transientSince > 0L) {
                val elapsed = (now - transientSince) / 1000.0
                if (elapsed < 1.6) -(abs(sin(elapsed / 0.42 * PI)) * 10f * dpi).toFloat() else 0f
            } else 0f
            val dropPx = if (expression == PetExpression.DOWNCAST) 6f * dpi else 0f
            val dim = if (expression == PetExpression.DOWNCAST) 0.82f else 1f

            val cx = petX + petSize / 2f
            val cy = petY + petSize / 2f + bobPx + hopPx + dropPx

            // STREAMING：紫光脉冲
            if (expression == PetExpression.STREAMING) {
                val pulse = (0.30f + 0.16f * sin(phase * 2 * PI * 1.5).toFloat()).coerceIn(0.05f, 1f)
                val oldComposite = g2.composite
                g2.composite = AlphaComposite.SrcOver.derive(pulse)
                g2.paint = java.awt.RadialGradientPaint(
                    java.awt.geom.Point2D.Float(cx, cy),
                    petSize * 0.95f,
                    floatArrayOf(0f, 0.65f, 1f),
                    arrayOf(Color(181, 123, 224), Color(181, 123, 224, 60), Color(0, 0, 0, 0))
                )
                g2.fillOval(
                    (cx - petSize * 0.95f).toInt(),
                    (cy - petSize * 0.95f).toInt(),
                    (petSize * 1.9f).toInt(),
                    (petSize * 1.9f).toInt()
                )
                g2.composite = oldComposite
            }

            if (frameImage != null) {
                val spriteG = g2.create() as Graphics2D
                spriteG.composite = AlphaComposite.SrcOver.derive(dim)
                spriteG.translate(cx.toDouble(), cy.toDouble())
                spriteG.rotate(Math.toRadians((tiltDeg + droopRot).toDouble()))
                spriteG.scale(breathe.toDouble(), breathe.toDouble())
                spriteG.translate(-cx.toDouble(), -cy.toDouble())
                val imgW = frameImage.width
                val imgH = frameImage.height
                val drawW = petSize
                val drawH = (petSize * imgH.toFloat() / imgW).roundToInt()
                val drawY = (petY + petSize - drawH + (bobPx + hopPx + dropPx).toInt())
                spriteG.drawImage(frameImage, petX, drawY, drawW, drawH, null)
                spriteG.dispose()
            }

            if (bubble) {
                drawBubble(g2, bubbleOf(expression), 5, 1, width - 10, strip - 4)
            }

            lastExpression = expression
        }

        private fun drawBubble(g2: Graphics2D, text: String, x: Int, y: Int, w: Int, h: Int) {
            if (w < 20 || h < 8) return
            val oldComposite = g2.composite
            g2.composite = AlphaComposite.SrcOver.derive(0.92f)
            g2.color = Color(35, 32, 58)
            g2.fillRoundRect(x, y, w, h, h, h)
            g2.composite = AlphaComposite.SrcOver.derive(1f)
            g2.color = Color(181, 123, 224, 120)
            g2.stroke = BasicStroke(1.1f)
            g2.drawRoundRect(x, y, w, h, h, h)
            val fontSize = (h * 0.42f).roundToInt().coerceAtLeast(10)
            g2.font = petFont(fontSize)
            g2.color = Color(232, 222, 255)
            val fm = g2.fontMetrics
            val trimmed = trimToWidth(text, fm, w - 14)
            val tx = x + (w - fm.stringWidth(trimmed)) / 2
            val ty = y + (h + fm.ascent - fm.descent) / 2
            g2.drawString(trimmed, tx, ty)
            g2.composite = oldComposite
        }

        private fun trimToWidth(text: String, fm: java.awt.FontMetrics, maxWidth: Int): String {
            if (fm.stringWidth(text) <= maxWidth) return text
            var end = text.length
            while (end > 0) {
                end--
                val candidate = text.substring(0, end) + "…"
                if (fm.stringWidth(candidate) <= maxWidth) return candidate
            }
            return "…"
        }

        private fun petFont(sizePx: Int): Font {
            val candidates = listOf("Microsoft YaHei UI", "Microsoft YaHei", "SimHei", Font.SANS_SERIF)
            for (name in candidates) {
                val font = Font(name, Font.PLAIN, sizePx)
                if (font.name != "Dialog") return font
            }
            return Font(Font.SANS_SERIF, Font.PLAIN, sizePx)
        }
    }
}
