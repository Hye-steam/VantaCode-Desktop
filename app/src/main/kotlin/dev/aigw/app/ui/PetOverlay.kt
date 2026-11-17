package dev.aigw.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import dev.aigw.util.Log
import kotlin.math.hypot

/**
 * 桌面宠物悬浮窗：用 WebView 播放 webm 帧动画。
 *
 * 渲染完全交给 assets/pet 下的 H5（dsh-pet 引擎），原生只负责开透明悬浮窗、
 * 把 WebView 放进去、把触摸翻译成宠物的动作指令。
 *
 * 两个必须照做的细节（否则要么拖不动、要么点了没反应）：
 * 1. ACTION_DOWN 必须返回 true 接管手势，返回 false 会让后续 MOVE/UP 全部丢失；
 * 2. WebView 和容器都要挂同一个 touch listener —— WebView 会先吃掉事件，
 *    只挂容器的话拖拽代码根本不会执行。
 */
class PetOverlay(
    private val context: Context,
    /** 点击宠物时显示的提示文案（当前用量）。 */
    private val tapHint: () -> String = { "" },
) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var container: FrameLayout? = null
    private var web: WebView? = null
    private var params: WindowManager.LayoutParams? = null

    companion object {
        private const val SIZE_DP = 150
        private const val MODEL_PATH = "film/dafy_video"
        private const val MODEL_NAME = "大肥鱼 · 视频版"
        /** 拖动判定阈值（dp）。低于它算点击，交给宠物做回应动作。 */
        private const val DRAG_SLOP_DP = 5
    }

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)

    fun isShowing(): Boolean = container != null

    private fun js(code: String) {
        web?.evaluateJavascript("try{$code}catch(e){}", null)
    }

    /**
     * 显示桌宠，返回是否真的挂上去了。
     *
     * 不用 Settings.canDrawOverlays() 做前置判断：MIUI 上它会在 appops 已允许时
     * 仍返回 false，导致开关点了没反应、还反复提示去授权。直接尝试挂载，
     * 以 addView 的实际结果为准。
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun start(): Boolean {
        if (container != null) return true

        val density = context.resources.displayMetrics.density
        val sizePx = (SIZE_DP * density).toInt()

        val root = FrameLayout(context).apply {
            clipChildren = false
            // 容器也要透明，否则 WebView 下面那层是默认白
            setBackgroundColor(Color.TRANSPARENT)
        }
        val view = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            // 不能用 LAYER_TYPE_SOFTWARE：那会让 video 完全不渲染（宠物直接消失）。
            // 透明靠窗口 TRANSLUCENT + 容器/WebView 背景色 + 下方 CSS 混合一起实现。
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                // 宠物是自动播放的动画，不能要求用户先点一下才解码
                mediaPlaybackRequiresUserGesture = false
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
            }
            @Suppress("DEPRECATION")
            runCatching {
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, url: String?) {
                    // pet.html 要先跑完 boot 才有 PetAPI；调用太早会被 film.js 的
                    // ready 检查挡掉，页面就一直停在初始态，宠物不出现。
                    v.postDelayed({
                        // 视频底色是纯黑（yuv420p 无 alpha）。screen 混合把黑变透明，
                        // 是唯一能在不重编码 52MB 视频的前提下做到"贴纸感"的办法。
                        v.evaluateJavascript(
                            "(function(){var s=document.createElement('style');" +
                                "s.textContent='video.petvid{mix-blend-mode:screen !important}" +
                                "html,body,#stage{background:transparent !important}';" +
                                "document.head.appendChild(s);})()",
                            null,
                        )
                        // 诊断：把 H5 侧真实状态打出来，否则只能看到"窗口在但空白"
                        v.evaluateJavascript(
                            "JSON.stringify({api:!!window.PetAPI,cfg:!!window.PET_CONFIG,status:window.__petStatus||'none',videos:document.getElementsByTagName('video').length})",
                        ) { r -> android.util.Log.w("AIGWPET", "before setModel: $r") }
                        js(
                            "if(window.PetAPI){" +
                                "if(PetAPI.setModel)PetAPI.setModel('$MODEL_PATH','$MODEL_NAME');" +
                                "if(PetAPI.setSize)PetAPI.setSize(1,1);" +
                                "}",
                        )
                        v.postDelayed({
                            v.evaluateJavascript(
                                "JSON.stringify({status:window.__petStatus||'none',videos:document.getElementsByTagName('video').length,err:(document.getElementById('err')||{}).textContent||''})",
                            ) { r -> android.util.Log.w("AIGWPET", "after setModel: $r") }
                        }, 1800)
                    }, 900)
                }
            }
            loadUrl("file:///android_asset/pet/pet.html")
            // 保底：淡入依赖 setModel 回调链，任何一环没走到就会永远透明。
            // 这里兜一手，2.5 秒后强行可见。
            postDelayed({
                if (alpha < 1f) runCatching { alpha = 1f }
            }, 2500)
        }
        // 加载期间整体不可见：WebView 默认白底、视频首帧是黑的，
        // 不藏起来的话每次启动/重启都会闪一下白或黑。
        view.alpha = 0f
        root.addView(view, FrameLayout.LayoutParams(sizePx, sizePx))

        val lp = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val screenW = context.resources.displayMetrics.widthPixels
            val screenH = context.resources.displayMetrics.heightPixels
            // 优先恢复用户上次拖到的位置；没有就落在右下角，避开主要内容
            val prefs = context.getSharedPreferences("pet_pos", Context.MODE_PRIVATE)
            val savedX = prefs.getInt("x", Int.MIN_VALUE)
            val savedY = prefs.getInt("y", Int.MIN_VALUE)
            if (savedX != Int.MIN_VALUE && savedY != Int.MIN_VALUE) {
                x = savedX.coerceIn(0, (screenW - sizePx).coerceAtLeast(0))
                y = savedY.coerceIn(0, (screenH - sizePx).coerceAtLeast(0))
            } else {
                x = screenW - sizePx - (16 * density).toInt()
                y = screenH - sizePx - (180 * density).toInt()
            }
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        val slop = DRAG_SLOP_DP * density

        val touch = View.OnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    runCatching { v.parent?.requestDisallowInterceptTouchEvent(true) }
                    js("if(window.PetAPI&&PetAPI.beginDrag)PetAPI.beginDrag()")
                    true // 必须接管，否则拿不到后续 MOVE/UP
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && hypot(dx, dy) > slop) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(root, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        js("if(window.PetAPI&&PetAPI.endDrag)PetAPI.endDrag()")
                        runCatching {
                            context.getSharedPreferences("pet_pos", Context.MODE_PRIVATE)
                                .edit().putInt("x", lp.x).putInt("y", lp.y).apply()
                        }
                    } else {
                        // 没移动过 = 点击：宠物做回应动作，同时报一下当前用量
                        js("if(window.PetAPI&&PetAPI.poke)PetAPI.poke()")
                        val hint = runCatching { tapHint() }.getOrNull()
                        if (!hint.isNullOrBlank()) {
                            runCatching {
                                android.widget.Toast.makeText(
                                    context, hint, android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    runCatching { v.parent?.requestDisallowInterceptTouchEvent(false) }
                    true
                }
                else -> true
            }
        }

        view.setOnTouchListener(touch)
        root.setOnTouchListener(touch)

        return runCatching {
            wm.addView(root, lp)
            container = root
            web = view
            params = lp
            Log.info("桌宠已显示")
            true
        }.getOrElse {
            Log.warn("桌宠挂载失败（多半是缺少悬浮窗权限）：${it.message}")
            runCatching { wm.removeView(root) }
            runCatching { view.destroy() }
            false
        }
    }

    fun stop() {
        val view = container ?: return
        web?.let { w ->
            runCatching { w.stopLoading() }
            runCatching { w.loadUrl("about:blank") }
            runCatching { w.destroy() }
        }
        runCatching { wm.removeView(view) }
        web = null
        container = null
        params = null
        Log.info("桌宠已关闭")
    }

    /** 尺寸变化时重建：WebView 的布局参数不能直接热改，重建最稳。 */
    fun restart(): Boolean {
        stop()
        return start()
    }
}
