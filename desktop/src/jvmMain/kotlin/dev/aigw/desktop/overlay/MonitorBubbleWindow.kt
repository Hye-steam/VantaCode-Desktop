package dev.aigw.desktop.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import dev.aigw.desktop.ui.ThemePalette
import dev.aigw.desktop.util.UiPrefs
import dev.aigw.gateway.Engine
import kotlinx.coroutines.delay

/**
 * 悬浮监测球（桌面版）：无边框置顶透明窗口，可拖动。
 *
 * 与 Android 的 MonitorBubble 一致：每 1s 轮询 Engine.activeRequests /
 * lastBubbleEvent；有在途请求时显示数量，最近 3.5s 内的事件（结束/换号/
 * 失败/模型上限）优先显示；无事件时显示待机色。位置持久化到 UiPrefs。
 * 与桌宠互斥（桌宠开启时不显示）。
 */
@Composable
fun MonitorBubbleWindow(
    engine: Engine,
    theme: ThemePalette,
    prefs: UiPrefs,
    visible: Boolean,
    onClose: () -> Unit,
) {
    if (!visible) return
    var active by remember { mutableStateOf(0) }
    var event by remember { mutableStateOf("") }
    var eventAt by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            active = engine.activeRequests
            event = engine.lastBubbleEvent
            eventAt = engine.lastBubbleEventAt
            delay(1000)
        }
    }

    val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
    val defaultX = (screenSize.width - 120).coerceAtLeast(0)
    val defaultY = 120
    val startX = prefs.getInt("bubble_x", defaultX)
    val startY = prefs.getInt("bubble_y", defaultY)
    val state = rememberWindowState(
        position = WindowPosition(startX.dp, startY.dp),
        size = DpSize(76.dp, 76.dp),
    )

    Window(
        onCloseRequest = onClose,
        state = state,
        visible = true,
        title = "VantaGate 监测球",
        undecorated = true,
        transparent = true,
        alwaysOnTop = true,
        resizable = false,
    ) {
        val now = System.currentTimeMillis()
        val showEvent = event.isNotBlank() && now - eventAt < 3500
        val (base, label) = when {
            showEvent && event.contains("失败") -> theme.bubbleFail to event
            showEvent && event.contains("模型上限") -> theme.bubbleWarn to event
            showEvent -> theme.bubbleOk to event
            active > 0 -> theme.bubbleIdle to "$active"
            else -> theme.bubbleOff to "待机"
        }
        var posX by remember { mutableStateOf(startX.dp) }
        var posY by remember { mutableStateOf(startY.dp) }
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(base, base.copy(alpha = 0.75f))))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = {
                            with(density) {
                                prefs.putInt("bubble_x", posX.toPx().toInt())
                                prefs.putInt("bubble_y", posY.toPx().toInt())
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
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = if (label.length <= 2) 20.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
