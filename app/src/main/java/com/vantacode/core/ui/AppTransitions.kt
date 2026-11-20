package com.vantacode.core.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically

/**
 * 聊天页各类悬浮层的统一进出动画。
 *
 * 分两组而不是共用一个：输入区里的条状区域（附件栏、扩展面板）收起时要「向上缩」，
 * 底部弹出的面板（授权确认）要「向下退」，方向反了会看起来像被裁掉。
 */
object AppTransitions {

    private const val DURATION_FAST = 160
    private const val DURATION_NORMAL = 220

    /** 条状区域进场：淡入 + 纵向展开。 */
    val barEnter: EnterTransition =
        fadeIn(tween(DURATION_FAST, easing = FastOutSlowInEasing)) +
            expandVertically(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                expandFrom = androidx.compose.ui.Alignment.Top
            )

    /** 条状区域退场：淡出 + 纵向收缩。 */
    val barExit: ExitTransition =
        fadeOut(tween(DURATION_FAST, easing = FastOutSlowInEasing)) +
            shrinkVertically(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                shrinkTowards = androidx.compose.ui.Alignment.Top
            )

    /** 底部面板进场：淡入 + 自下而上展开。 */
    val panelEnter: EnterTransition =
        fadeIn(tween(DURATION_NORMAL, easing = FastOutSlowInEasing)) +
            expandVertically(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                expandFrom = androidx.compose.ui.Alignment.Bottom
            )

    /** 底部面板退场：淡出 + 向下收起。 */
    val panelExit: ExitTransition =
        fadeOut(tween(DURATION_FAST, easing = FastOutSlowInEasing)) +
            shrinkVertically(
                animationSpec = tween(DURATION_NORMAL, easing = FastOutSlowInEasing),
                shrinkTowards = androidx.compose.ui.Alignment.Bottom
            )
}
