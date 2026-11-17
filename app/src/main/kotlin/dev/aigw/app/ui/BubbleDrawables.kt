package dev.aigw.app.ui

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * 悬浮球绘制：纯色圆球 + 呼吸缩放 + 柔和光晕 + 缓慢旋转的虚线装饰环。
 *
 * 动效语言：节奏慢、幅度小，待机时球体做轻微呼吸（缩放 1.0 → 1.05），
 * 外圈光晕随呼吸同步脉动，一条虚线环以约 20 秒/圈的速度缓缓旋转，
 * 避免生硬的机械动效，视觉上像一枚「贴在屏幕上的呼吸贴纸」。
 */
class CircleDrawable(private val color: Int) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CircleDrawable.color }

    /** 白色细边，让圆球在任意背景上都有清晰轮廓。 */
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0x55FFFFFF
        strokeWidth = 2f
    }

    /** 外圈柔和光晕。 */
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    /** 旋转的虚线装饰环：与球体同色，深度更深，在浅色背景上也清晰可见。 */
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = this@CircleDrawable.color
        strokeWidth = 1.5f
    }

    private val ringRect = RectF()

    /** 呼吸相位 0..1，[ValueAnimator] 无限循环驱动。 */
    private var breathePhase = 0f
    private var animator: ValueAnimator? = null

    /** 呼吸缩放：1.0 → 1.05，3.2s 一周期，ease-in-out。 */
    private fun breatheScale(phase: Float): Float {
        // 用 (1 - cos(2π·phase)) / 2 得到 0..1 的平滑脉冲
        val wave = (1f - kotlin.math.cos(2f * Math.PI.toFloat() * phase)) / 2f
        return 1f + 0.05f * wave
    }

    /** 光晕强度随呼吸同步脉动：0.10 → 0.28。 */
    private fun glowAlpha(phase: Float): Int {
        val wave = (1f - kotlin.math.cos(2f * Math.PI.toFloat() * phase)) / 2f
        return ((0.10f + 0.18f * wave) * 255).toInt().coerceIn(0, 255)
    }

    /** 虚线环旋转角度：20s/圈，由时间驱动，与呼吸动画解耦。 */
    private fun ringAngle(): Float =
        ((System.currentTimeMillis() % 20_000L) / 20_000f) * 360f

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        ensureAnimator()
    }

    private fun ensureAnimator() {
        if (animator != null) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 3200L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                breathePhase = a.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val fullR = min(b.width(), b.height()) / 2f
        // 圆球本体只占 bounds 的一部分，四周透明区用来容纳光晕与虚线环；
        // 光晕半径严格等于 bounds 内切圆，避免超出被 View 裁成方形四角。
        val r = fullR * CORE_RATIO
        val scale = breatheScale(breathePhase)

        canvas.save()
        // 以球心为缩放中心做呼吸
        canvas.scale(scale, scale, cx, cy)

        // 1) 外圈光晕：从球体边缘淡出到 bounds 边缘，完整圆形不外扩
        val glowRadius = fullR
        val glowShader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(alphaColor(color, glowAlpha(breathePhase)), alphaColor(color, 0)),
            floatArrayOf(CORE_RATIO, 1f),
            Shader.TileMode.CLAMP,
        )
        glowPaint.shader = glowShader
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // 2) 旋转虚线环
        val ringR = r * 1.18f
        ringRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
        ringPaint.pathEffect = DashPathEffect(floatArrayOf(6f, 10f), 0f)
        canvas.save()
        canvas.rotate(ringAngle(), cx, cy)
        canvas.drawArc(ringRect, 0f, 360f, false, ringPaint)
        canvas.restore()

        // 3) 圆球本体 + 白色细边
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawCircle(cx, cy, r - 1.5f, rimPaint)

        canvas.restore()
    }

    @Deprecated("Deprecated in Java")
    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        rimPaint.alpha = alpha
        glowPaint.alpha = alpha
        ringPaint.alpha = alpha
    }

    @Deprecated("Deprecated in Java")
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        rimPaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
        ringPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        /** 圆球本体占 bounds 直径的比例；其余留给光晕与虚线环的透明边。 */
        const val CORE_RATIO = 0.72f

        /** 把颜色换成带指定 alpha 的版本（RGB 不变）。 */
        fun alphaColor(color: Int, alpha: Int): Int =
            (color and 0x00FFFFFF) or (alpha shl 24)
    }
}
