package dev.aigw.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat

import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import dev.aigw.gateway.Engine

/**
 * 真正的系统悬浮窗监测球：WindowManager + TYPE_APPLICATION_OVERLAY，
 * 挂在 GatewayService 生命周期上，App 退到后台或其它应用之上仍然显示。
 * 需要用户授予「显示在其它应用上层」权限（Settings.canDrawOverlays）。
 *
 * 球体可自由拖动，松手停在当前位置（不贴边）；颜色随网关状态切换；
 * 待机时带呼吸缩放、柔和光晕与旋转虚线环动效。
 */
class MonitorBubbleOverlay(
    private val context: Context,
    private val engine: Engine,
) {
    private val main = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var rootView: LinearLayout? = null

    /** 拖动参数：屏幕上的绝对位置（内容区坐标）。 */
    private var x = 40
    private var y = 300
    private var downX = 0f
    private var downY = 0f
    private var viewXAtDown = 0
    private var viewYAtDown = 0
    private var moved = false
    /** 双击返回 APP：记录上次抬手时间与位置。 */
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
    }

    private var showing = false
    private var tickRunning = false

    fun start() {
        if (showing) return
        if (!Settings.canDrawOverlays(context)) return
        main.post {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager = wm
                val root = buildRootView()
                rootView = root
                wm.addView(root, params)
                // 初始位置也夹回屏幕内（防止分辨率变化后旧坐标落到屏外）
                clamp()
                applyPosition()
                showing = true
                lastAppliedState = null
                lastAppliedText = null
                startTick()
            } catch (e: Exception) {
                showing = false
                // 失败不静默：进网关事件日志，UI「日志」页可直接看到原因
                runCatching { engine.eventLog.warn("悬浮球", "显示失败：${e.message}") }
            }
        }
    }

    fun stop() {
        stopTick()
        main.post {
            try {
                rootView?.let { windowManager?.removeView(it) }
            } catch (_: Exception) {
            }
            rootView = null
            showing = false
        }
    }

    /** 设置变更（透明度等）后即时应用，无需重建悬浮窗。 */
    fun refreshAppearance() {
        if (!showing) return
        main.post {
            (rootView?.getChildAt(0))?.alpha = engine.settings.bubbleOpacity.coerceIn(0.3f, 1f)
        }
    }

    // ------------------------------------------------------------------ 视图构建

    @SuppressLint("ClickableViewAccessibility")
    private fun buildRootView(): LinearLayout {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val dot = TextView(context).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = android.view.Gravity.CENTER
            setSingleLine(true)
            // 背景画布含光晕/虚线环透明边，球体本体只占约 72%，这里放大画布保持球体视觉大小不变
            layoutParams = LinearLayout.LayoutParams(dp(78), dp(78))
            background = CircleDrawable(currentTheme().bubbleOffInt)
            alpha = engine.settings.bubbleOpacity.coerceIn(0.3f, 1f)
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(0), dp(0), dp(0), dp(0))
            addView(dot)
        }

        // 拖动换位：用按下位置差区分点击与拖动
        dot.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    viewXAtDown = x
                    viewYAtDown = y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (moved || dx * dx + dy * dy > 25 * 25) {
                        moved = true
                        x = viewXAtDown + dx
                        y = viewYAtDown + dy
                        clamp()
                        applyPosition()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 不贴边：松手停在当前位置，但必须夹回屏幕内
                    moved = false
                    clamp()
                    applyPosition()
                    // 双击（未拖动且两次点击在 300ms / 60px 内）→ 返回 App 主界面
                    val now = System.currentTimeMillis()
                    val tapX = event.rawX
                    val tapY = event.rawY
                    if (now - lastTapAt < 300 &&
                        kotlin.math.abs(tapX - lastTapX) < 60 &&
                        kotlin.math.abs(tapY - lastTapY) < 60
                    ) {
                        lastTapAt = 0L
                        runCatching {
                            context.startActivity(
                                android.content.Intent(context, dev.aigw.app.MainActivity::class.java)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    } else {
                        lastTapAt = now
                        lastTapX = tapX
                        lastTapY = tapY
                    }
                    true
                }
                else -> false
            }
        }

        return root
    }

    // ------------------------------------------------------------------ 状态与刷新

    private data class Snap(
        val time: Long,
        val model: String,
        val via: String,
        val ok: Boolean,
        val latencyMs: Long,
    )

    @Volatile
    private var lastSnapshot: Snap? = null

    /** 上次已应用的 UI 状态：无变化不重绘。 */
    private var lastAppliedState: BubbleUiState? = null
    private var lastAppliedText: Snap? = null

    private fun startTick() {
        if (tickRunning) return
        tickRunning = true
        Thread {
            while (tickRunning) {
                try {
                    val running = serverRunning()
                    val record = engine.callLog.recent(1).lastOrNull()
                    val snap = record?.let {
                        Snap(it.time, it.model, it.via, it.ok, it.latencyMs)
                    }
                    lastSnapshot = snap
                    val state = stateOf(snap, running)
                    // 状态与内容都没变时不投递主线程任务，空转零开销
                    if (state != lastAppliedState || snap != lastAppliedText) {
                        main.post { updateUi(state, snap, running) }
                    }
                } catch (_: Exception) {
                }
                try {
                    Thread.sleep(1000)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun stopTick() {
        tickRunning = false
    }

    /** 悬浮窗挂在服务上；服务活着时网关一定在运行，这里直接读内存标记。 */
    private fun serverRunning(): Boolean = true

    private fun currentTheme(): ThemePalette = themeByName(engine.settings.themeName)

    private fun stateOf(snap: Snap?, running: Boolean = true): BubbleUiState {
        val theme = currentTheme()
        if (!running) return BubbleUiState("未运行", theme.bubbleOffInt)

        // 账号可用性优先：全部禁用或无账号时立即警示
        val accounts = engine.pool.all()
        val total = accounts.size
        val enabled = accounts.count { it.state.enabled }
        val available = accounts.count { it.state.enabled && !it.state.inCooldown() }
        val busy = engine.activeRequests > 0
        val eventFresh = System.currentTimeMillis() - engine.lastBubbleEventAt < 3_500
        val event = engine.lastBubbleEvent

        return when {
            total == 0 || enabled == 0 -> BubbleUiState("无账号", theme.bubbleOffInt)
            eventFresh && event == "模型上限" -> BubbleUiState("模型上限", theme.bubbleWarnInt)
            available == 0 && !busy -> BubbleUiState("无账号", theme.bubbleOffInt)
            busy -> BubbleUiState("请求中", theme.bubbleIdleInt)
            eventFresh -> when (event) {
                "换号" -> BubbleUiState("换号", theme.bubbleWarnInt)
                "失败" -> BubbleUiState("失败", theme.bubbleFailInt)
                "结束" -> BubbleUiState("结束", theme.bubbleOkInt)
                else -> BubbleUiState("待命", theme.bubbleIdleInt)
            }
            else -> BubbleUiState("待命", theme.bubbleIdleInt)
        }
    }

    private fun updateUi(state: BubbleUiState, snap: Snap?, running: Boolean) {
        val root = rootView ?: return
        val dot = root.getChildAt(0) as? TextView ?: return
        // 状态没变就跳过重绘，避免每 500ms 无谓刷背景造成卡顿
        // 但主题可能已切换，因此始终以当前主题色为准
        val changed = lastAppliedState != state
        if (!changed && lastAppliedText == snap) return
        lastAppliedState = state
        lastAppliedText = snap
        dot.background = CircleDrawable(state.baseColor)
        dot.text = state.label
        // 进入异常状态时记一条日志，用户可在「日志」页看到提示；只在状态变化时记，避免刷屏
        if (changed && state.label in WARNING_LABELS) {
            runCatching { engine.eventLog.warn("悬浮球", "账号状态：${state.label}") }
        }
    }

    // ------------------------------------------------------------------ 位置

    private fun clamp() {
        val wm = windowManager ?: return
        val metrics = wm.currentWindowMetricsCompat()
        // 画布宽高固定为 78dp（与 buildRootView 一致），不依赖动态测量，避免首帧测量失败导致右侧可拖出屏幕
        val density = context.resources.displayMetrics.density
        val w = (78 * density).toInt().coerceAtLeast(1)
        val h = w
        val maxX = (metrics.widthPixels - w).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - h).coerceAtLeast(0)
        x = x.coerceIn(0, maxX)
        y = y.coerceIn(0, maxY)
    }

    private fun applyPosition() {
        params.x = x
        params.y = y
        runCatching { windowManager?.updateViewLayout(rootView, params) }
    }

    private fun WindowManager.currentWindowMetricsCompat(): android.util.DisplayMetrics {
        val metrics = android.util.DisplayMetrics()
        // API 30+ 用 currentWindowMetrics 拿真实可用区域；低版本回退 defaultDisplay。
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val bounds = currentWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            defaultDisplay.getMetrics(metrics)
        }
        return metrics
    }

    private companion object {
        /** 需要警示的异常状态标签，进入时记日志提示。 */
        val WARNING_LABELS = setOf("无账号", "失败", "换号", "模型上限")
    }
}

    private data class BubbleUiState(
        val label: String,
        val baseColor: Int,
    )
