package dev.aigw.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Loop 设计稿通用缓动与动效组件。 */

/** riseIn / sheet / navIn 共用的 ease-out 曲线（cubic-bezier(.22,1,.36,1)）。 */
val LoopEase: androidx.compose.animation.core.Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** 设计稿 .card：白底 + 1px line 描边 + 22dp 圆角 + 轻投影。 */
@Composable
fun LoopCard(corner: Dp, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(corner), ambientColor = Color(0x1428505A))
            .clip(RoundedCornerShape(corner))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(corner)),
        content = content,
    )
}

/** live-dot：6dp 绿点 1.6s 呼吸。 */
@Composable
fun LiveDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "liveDot")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier
            .size(6.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(LoopColors.success, CircleShape),
    )
}

/** deco 箭头徽章：34dp 蓝底圆角方块 + 箭头（箭头徽章自身可点击时用 [interaction]）。 */
@Composable
fun ArrowBadge(interaction: MutableInteractionSource? = null, modifier: Modifier = Modifier) {
    val source = interaction ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(30.dp)
            .graphicsLayer {
                val s = if (pressed) 1.06f else 1f
                scaleX = s
                scaleY = s
                translationX = if (pressed) 3f else 0f
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (pressed) LoopColors.brand else LoopColors.surfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (pressed) Color.White else LoopColors.muted,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * 进度条（progress + progress-fill + shimmer）。
 *
 * 入场按 1.4s fillup 动画填充；填充层上叠加白色高光 2s 循环扫过。
 */
@Composable
fun LoopProgress(brush: Brush, progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(1400, easing = LoopEase),
        label = "fill",
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "shimmerX",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(LoopColors.brandSoft),
    ) {
        if (animated > 0.01f) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(brush)
                    .drawWithCacheCompat(shimmer),
            )
        }
    }
}

/** shimmer 高光：白色渐变随 [shimmer] 在 -100% ~ 200% 间平移。 */
private fun Modifier.drawWithCacheCompat(shimmer: Float): Modifier =
    this.drawBehind {
        val w = size.width
        val x = shimmer * w
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                startX = x - w * 0.5f,
                endX = x + w * 0.5f,
            ),
        )
    }

/** 右上数字徽章：40dp 白底圆框 + 蓝色数字。 */
@Composable
fun CountBadge(count: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(LoopColors.surfaceAlt),
        contentAlignment = Alignment.Center,
    ) {
        Text(count, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LoopColors.subText)
    }
}

/** 页面内容 rise 入场（针对整块内容）：opacity + translateY 24px。 */
@Composable
fun RiseIn(delayMillis: Int = 0, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visibleState = androidx.compose.runtime.remember {
            androidx.compose.animation.core.MutableTransitionState(false).apply { targetState = true }
        },
        enter = androidx.compose.animation.fadeIn(tween(700, delayMillis, easing = LoopEase)) +
            androidx.compose.animation.slideInVertically(
                initialOffsetY = { 24 },
                animationSpec = tween(700, delayMillis, easing = LoopEase),
            ),
    ) {
        content()
    }
}

/**
 * 空状态吉祥物：统一的云朵 logo（带轻微上下漂浮动画），用于各页空状态。
 */
@Composable
fun EmptyMascot(size: Dp = 100.dp, float: Boolean = true, modifier: Modifier = Modifier) {
    val transition = if (float) rememberInfiniteTransition(label = "mascot") else null
    val offset = transition?.let { t ->
        val value by t.animateFloat(
            initialValue = 0f,
            targetValue = -8f,
            animationSpec = infiniteRepeatable(
                tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                RepeatMode.Reverse,
            ),
            label = "mascotFloat",
        )
        return@let value
    } ?: 0f
    Image(
        painter = painterResource(dev.aigw.app.R.drawable.ic_empty_mascot),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationY = offset
                alpha = 0.95f
            },
    )
}

@Composable
fun StatCardPressable(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lift by animateFloatAsState(
        targetValue = if (pressed) -4f else 0f,
        animationSpec = tween(250),
        label = "lift",
    )
    Column(
        modifier = modifier
            .graphicsLayer { translationY = lift }
            .clip(RoundedCornerShape(18.dp))
            .background(LoopColors.surface)
            .border(
                1.dp,
                if (pressed) LoopColors.brand.copy(alpha = 0.35f) else LoopColors.line,
                RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = {}),
        content = content,
    )
}
