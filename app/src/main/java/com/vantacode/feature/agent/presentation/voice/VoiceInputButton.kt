package com.vantacode.feature.agent.presentation.voice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vantacode.R
import compose.icons.FeatherIcons
import compose.icons.feathericons.Mic

/**
 * 输入框里的语音按钮。
 *
 * 录音中时图标底色变成强调色并做呼吸缩放，给出「正在听」的反馈——
 * 系统识别没有音量回调可视化，只能靠动画让用户知道没卡住。
 */
@Composable
internal fun VoiceInputButton(
    enabled: Boolean,
    isListening: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voice-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 620),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voice-pulse-scale"
    )

    IconButton(
        onClick = onToggle,
        enabled = enabled,
        modifier = modifier.size(36.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .scale(if (isListening) pulse else 1f)
                .background(
                    color = if (isListening) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = FeatherIcons.Mic,
                contentDescription = stringResource(
                    if (isListening) R.string.chat_voice_stop else R.string.chat_voice_input
                ),
                tint = if (isListening) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
