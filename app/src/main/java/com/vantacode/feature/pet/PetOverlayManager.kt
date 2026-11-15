package com.vantacode.feature.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.vantacode.core.util.FileLogger
import kotlin.math.abs

/**
 * 桌面宠物悬浮窗：WindowManager + TYPE_APPLICATION_OVERLAY。
 * 16 帧动画按 agent 状态切换，支持拖动、点击挥手、气泡文案。
 */
object PetOverlayManager {
    private const val TAG = "PetOverlay"

    enum class PetExpression { IDLE, LOOKING, STREAMING, PLEASED, DOWNCAST, CODING, ERROR, DONE, WAITING }

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var petView: ImageView? = null
    private var bubbleView: TextView? = null
    private var frames: List<Bitmap> = emptyList()
    private var expression: PetExpression = PetExpression.IDLE
    private var lastActivityAt = System.currentTimeMillis()
    private var tapWaveUntil = 0L
    private var isShowing = false

    private val handler = Handler(Looper.getMainLooper())
    private val frameIntervalMs = 500L

    // 位置（屏幕比例，非绝对像素）
    private var fracX = 0.8f
    private var fracY = 0.72f

    // 气泡文案
    private val idleBubbles = listOf("喵~", "在呢", "摸鱼中...", "需要帮忙吗？", "今天也要加油哦", "有点困了...")
    private val happyBubbles = listOf("搞定！", "太棒了！", "完美~", "耶！")
    private val sadBubbles = listOf("呜呜...", "出错了", "好难过", "再来一次？")
    private val codingBubbles = listOf("写代码中…", "正在敲键盘…", "啪啪啪…", "编码 ing…")
    private val errorBubbles = listOf("出错了！", "报错啦…", "呜呜出问题了", "遇到错误了")
    private val doneBubbles = listOf("搞定！", "完成啦~", "搞完了", "耶，做好了")
    private val waitingBubbles = listOf("等你授权…", "需要你确认", "快点啦~", "等你点头")

    // 随机待机帧序列
    private val idleFrames = listOf(0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 13, 14)
    private val codingFrames = listOf(5, 6, 7, 8)
    private val pleasedFrames = listOf(1, 2, 3, 0)

    /** 显示桌宠。 */
    fun show(context: Context) {
        if (isShowing) return
        // 悬浮窗权限检查
        if (!android.provider.Settings.canDrawOverlays(context)) {
            FileLogger.w(TAG, "悬浮窗权限未授予，跳过显示")
            try {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + context.packageName)
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {}
            return
        }
        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            // 加载帧（一次性）
            if (frames.isEmpty()) {
                frames = (0..15).map { i ->
                    val input = context.assets.open("pet/frames/frame_%02d.png".format(i))
                    BitmapFactory.decodeStream(input).copy(Bitmap.Config.ARGB_8888, false)
                }
                FileLogger.i(TAG, "Loaded ${frames.size} pet frames")
            }

            // 密度提前获取（气泡和窗口都需要）
            val density = context.resources.displayMetrics.density

            // 根布局
            val root = FrameLayout(context)
            rootView = root

            // 宠物图片
            val pet = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(frames[0])
            }
            petView = pet

            // 气泡
            val bubble = TextView(context).apply {
                visibility = View.GONE
                textSize = 11f
                setTextColor(0xFF333333.toInt())
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xE6FFFFFF.toInt())
                    cornerRadius = 12 * context.resources.displayMetrics.density
                }
                background = bg
                setPadding(20, 10, 20, 10)
                alpha = 0.92f
            }
            bubbleView = bubble

            root.addView(pet, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
            root.addView(bubble, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = (100 * density).toInt() })

            // 窗口参数
            val w = (116 * density).toInt()
            val h = (150 * density).toInt()
            val params = WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (fracX * context.resources.displayMetrics.widthPixels).toInt()
                y = (fracY * context.resources.displayMetrics.heightPixels).toInt()
            }

            // 拖动 + 点击
            var dragStartX = 0f
            var dragStartY = 0f
            var startWinX = 0
            var startWinY = 0
            var isDragging = false

            root.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        dragStartX = event.rawX
                        dragStartY = event.rawY
                        startWinX = params.x
                        startWinY = params.y
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - dragStartX
                        val dy = event.rawY - dragStartY
                        if (abs(dx) > 10 || abs(dy) > 10) isDragging = true
                        if (isDragging) {
                            params.x = startWinX + dx.toInt()
                            params.y = startWinY + dy.toInt()
                            wm.updateViewLayout(root, params)
                            // 保存位置比例
                            fracX = params.x.toFloat() / context.resources.displayMetrics.widthPixels
                            fracY = params.y.toFloat() / context.resources.displayMetrics.heightPixels
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // 点击 → 挥手
                            tapWaveUntil = System.currentTimeMillis() + 2000
                            updateFrame()
                            showBubble("你好呀~")
                        }
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }

            wm.addView(root, params)
            isShowing = true
            FileLogger.i(TAG, "Pet overlay shown at ($fracX, $fracY)")

            // 状态循环
            handler.post(frameUpdater)
            handler.postDelayed(bubbleRandomizer, 15_000)

        } catch (e: Exception) {
            FileLogger.w(TAG, "Show failed: ${e.message}")
        }
    }

    /** 隐藏桌宠。 */
    fun hide() {
        if (!isShowing) return
        try {
            handler.removeCallbacks(frameUpdater)
            handler.removeCallbacks(bubbleRandomizer)
            rootView?.let { windowManager?.removeView(it) }
            isShowing = false
            FileLogger.i(TAG, "Pet overlay hidden")
        } catch (e: Exception) {
            FileLogger.w(TAG, "Hide failed: ${e.message}")
        }
    }

    /** 实时状态气泡：显示当前具体操作。 */
    fun showStatus(text: String) {
        lastActivityAt = System.currentTimeMillis()
        showBubble(text)
    }

    /** 更新 agent 表情状态。 */
    fun setExpression(expr: PetExpression) {
        expression = expr
        lastActivityAt = System.currentTimeMillis()
        updateFrame()
        // 自动重置 IDLE：状态表情 8 秒后回到待机，避免卡在单一动作
        handler.removeCallbacks(resetToIdle)
        handler.postDelayed(resetToIdle, 4000)
        when (expr) {
            PetExpression.PLEASED -> showBubble(happyBubbles.random())
            PetExpression.DOWNCAST -> showBubble(sadBubbles.random())
            PetExpression.CODING -> showBubble(codingBubbles.random())
            PetExpression.ERROR -> showBubble(errorBubbles.random())
            PetExpression.DONE -> showBubble(doneBubbles.random())
            PetExpression.WAITING -> showBubble(waitingBubbles.random())
            else -> {}
        }
    }

    /** 自动重置到 IDLE。 */
    private val resetToIdle = Runnable {
        if (expression != PetExpression.IDLE) {
            expression = PetExpression.IDLE
            updateFrame()
        }
    }

    /** 帧切换循环。 */
    private val frameUpdater = object : Runnable {
        override fun run() {
            updateFrame()
            handler.postDelayed(this, frameIntervalMs)
        }
    }

    /** 更新当前帧。 */
    private fun updateFrame() {
        val now = System.currentTimeMillis()
        val frameIdx = when (expression) {
            PetExpression.PLEASED -> pleasedFrames[((now / frameIntervalMs) % pleasedFrames.size).toInt()]
            PetExpression.DOWNCAST -> 10
            PetExpression.LOOKING -> 9
            PetExpression.STREAMING -> 9
            PetExpression.IDLE -> when {
                now - lastActivityAt > 45_000 -> 4   // 打盹
                now < tapWaveUntil -> 15              // 挥手
                else -> idleFrames[((now / frameIntervalMs) % idleFrames.size).toInt()]
            }
            PetExpression.CODING -> codingFrames[((now / frameIntervalMs) % codingFrames.size).toInt()]
            PetExpression.ERROR -> 10                  // 垂头
            PetExpression.DONE -> pleasedFrames[((now / frameIntervalMs) % pleasedFrames.size).toInt()]
            PetExpression.WAITING -> 9                 // 张望
        }
        petView?.setImageBitmap(frames.getOrNull(frameIdx))
    }

    /** 随机气泡。 */
    private val bubbleRandomizer = object : Runnable {
        override fun run() {
            if (expression == PetExpression.IDLE && System.currentTimeMillis() >= tapWaveUntil) {
                showBubble(idleBubbles.random())
            }
            handler.postDelayed(this, (15_000 + (Math.random() * 15_000).toLong()))
        }
    }

    /** 持续显示气泡（不自动消失，直到 hideBubble 或新气泡覆盖）。 */
    private var persistentBubble = false

    fun showPersistentBubble(text: String) {
        persistentBubble = true
        bubbleView?.let { b ->
            b.text = text
            b.visibility = View.VISIBLE
        }
    }

    fun hideBubble() {
        persistentBubble = false
        bubbleView?.visibility = View.GONE
    }

    /** 显示气泡 3 秒（持续气泡时不覆盖）。 */
    private fun showBubble(text: String) {
        if (persistentBubble) return
        bubbleView?.let { b ->
            b.text = text
            b.visibility = View.VISIBLE
            handler.postDelayed({ if (!persistentBubble) b.visibility = View.GONE }, 3000)
        }
    }

    fun isShowing() = isShowing
}
