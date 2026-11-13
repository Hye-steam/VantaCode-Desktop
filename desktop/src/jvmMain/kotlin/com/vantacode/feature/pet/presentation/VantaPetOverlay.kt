/*
 * VantaCode Desktop — Vanta 桌宠 overlay。
 * Canvas 即时绘制（零层缓存、零亚像素平移）：拖拽 / 出场 / 重新定位都在同一个 draw 里完成。
 * 气泡在宠物上方居中。显示开关在设置 → 通用 → 桌宠。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.feature.pet.presentation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vantacode.core.platform.DesktopAssets
import com.vantacode.core.ui.Strings
import com.vantacode.di.AppGraph
import com.vantacode.feature.agent.presentation.AgentUIState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

private const val VANTA_SIZE_DP = 116
private const val SPRITE_MAX_EDGE = 384

/** 解码 assets/pet/vanta.png 并 downscale 到 SPRITE_MAX_EDGE，失败返回 null。 */
private suspend fun loadVantaSprite(): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val src = DesktopAssets.open("pet/vanta.png").use { input -> ImageIO.read(input) }
            ?: return@runCatching null
        val scale = minOf(1f, SPRITE_MAX_EDGE.toFloat() / maxOf(src.width, src.height))
        val target = if (scale < 1f) {
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB).apply {
                val g = createGraphics()
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                g.drawImage(src, 0, 0, w, h, null)
                g.dispose()
            }
        } else src
        ByteArrayOutputStream().use { bos ->
            ImageIO.write(target, "png", bos)
            bos.toByteArray().decodeToImageBitmap()
        }
    }.getOrNull()
}

private fun expressionOf(state: AgentUIState): PetExpression = when (state) {
    AgentUIState.Idle -> PetExpression.IDLE
    AgentUIState.Loading -> PetExpression.LOOKING
    AgentUIState.Streaming -> PetExpression.STREAMING
    is AgentUIState.Result -> PetExpression.PLEASED
    is AgentUIState.Error -> PetExpression.DOWNCAST
}

private fun bubbleOf(expression: PetExpression): String = when (expression) {
    PetExpression.IDLE -> Strings.pet_bubble_idle
    PetExpression.LOOKING -> Strings.pet_bubble_looking
    PetExpression.STREAMING -> Strings.pet_bubble_streaming
    PetExpression.PLEASED -> Strings.pet_bubble_pleased
    PetExpression.DOWNCAST -> Strings.pet_bubble_downcast
}

/**
 * 桌宠 overlay。全屏透明 overlay（只拦截桌宠区域的触摸），
 * Canvas 立即模式绘制——拖拽多少帧都不蹭层缓存，无亚像素采样。
 */
@Composable
fun VantaPetOverlay(
    agentState: AgentUIState,
    modifier: Modifier = Modifier,
) {
    val petRepository = AppGraph.petSettingsRepository
    val enabled by petRepository.enabledFlow.collectAsStateWithLifecycle(
        initialValue = com.vantacode.feature.pet.data.PetSettingsRepository.DEFAULT_ENABLED
    )
    val position by petRepository.positionFlow.collectAsStateWithLifecycle(
        initialValue = com.vantacode.feature.pet.data.PetSettingsRepository.DEFAULT_X to
            com.vantacode.feature.pet.data.PetSettingsRepository.DEFAULT_Y
    )
    val appear by animateFloatAsState(
        targetValue = if (enabled) 1f else 0f,
        animationSpec = tween(350),
        label = "pet-appear"
    )
    if (appear < 0.01f) return

    val sprite by produceState<ImageBitmap?>(initialValue = null) { value = loadVantaSprite() }

    var transient by remember { mutableStateOf<PetExpression?>(null) }
    LaunchedEffect(agentState) {
        transient = when (agentState) {
            is AgentUIState.Result -> PetExpression.PLEASED
            is AgentUIState.Error -> PetExpression.DOWNCAST
            else -> null
        }
        if (transient != null) {
            delay(3000)
            transient = null
        }
    }
    val expression = transient ?: expressionOf(agentState)

    var bubbleUntil by remember { mutableLongStateOf(0L) }
    val showBubble = bubbleUntil > System.currentTimeMillis() || expression != PetExpression.IDLE
    LaunchedEffect(bubbleUntil) {
        val remain = bubbleUntil - System.currentTimeMillis()
        if (remain > 0) {
            delay(remain + 50L)
            bubbleUntil = 0L
        }
    }

    val draggingState = remember { mutableStateOf(false) }
    // 拖动中完全不显示桌宠——零拖影、零残影；放手后立刻显现并落到位。
    val dragAlpha by animateFloatAsState(
        targetValue = if (draggingState.value) 0f else 1f,
        animationSpec = tween(80),
        label = "pet-drag-dim"
    )

    BoxWithConstraints(modifier = modifier) {
        val containerW = constraints.maxWidth
        val containerH = constraints.maxHeight
        val density = LocalDensity.current
        val petSizePx = with(density) { VANTA_SIZE_DP.dp.toPx() }.roundToInt()
        val travelX = (containerW - petSizePx).coerceAtLeast(1)
        val travelY = (containerH - petSizePx).coerceAtLeast(1)

        // 位置 state 不随 DataStore 回声重置：外部变更才同步
        var posX by remember { mutableFloatStateOf(position.first) }
        var posY by remember { mutableFloatStateOf(position.second) }
        var lastSynced by remember { mutableStateOf(position) }
        LaunchedEffect(position) {
            if (position.first != lastSynced.first || position.second != lastSynced.second) {
                posX = position.first
                posY = position.second
                lastSynced = position
            }
        }

        val tapSlop = LocalViewConfiguration.current.touchSlop
        val scope = rememberCoroutineScope()

        val petOffsetX = (posX * travelX).roundToInt()
        val petOffsetY = (posY * travelY).roundToInt()

        // 全屏透明画布，绝对坐标绘制——避免子画布移动导致的重绘缺漏（残影）
        Canvas(modifier = Modifier.fillMaxSize()) {
            val img = sprite ?: return@Canvas
            drawImage(
                image = img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(img.width, img.height),
                dstOffset = IntOffset(petOffsetX, petOffsetY),
                dstSize = IntSize(petSizePx, petSizePx),
                alpha = appear * dragAlpha,
            )
        }

        // 拖拽手柄：透明 Box，不绘制像素，跟随桌宠位置
        Box(
            modifier = Modifier
                .offset { IntOffset(petOffsetX, petOffsetY) }
                .size(VANTA_SIZE_DP.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val downTime = System.currentTimeMillis()
                        var totalDrag = Offset.Zero
                        var dragging = false
                        var lastPos: Offset = down.position
                        draggingState.value = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            val delta = change.position - lastPos
                            lastPos = change.position
                            totalDrag += delta
                            if (!dragging && totalDrag.getDistance() > tapSlop) {
                                dragging = true
                                draggingState.value = true
                            }
                            if (dragging) {
                                posX = (posX + delta.x / travelX).coerceIn(0f, 1f)
                                posY = (posY + delta.y / travelY).coerceIn(0f, 1f)
                                change.consume()
                            }
                        }
                        draggingState.value = false
                        if (dragging) {
                            scope.launch { petRepository.savePosition(posX, posY) }
                        } else if (System.currentTimeMillis() - downTime < 400) {
                            bubbleUntil = System.currentTimeMillis() + 2200
                        }
                    }
                }
        )

        // 气泡：画布之上单独一行（Canvas 画文字麻烦，直接用 Compose 文本）
        if (showBubble) {
            val bubbleW = 112
            val bubbleH = 26
            val bubbleX = ((posX * travelX).roundToInt() + petSizePx / 2 - bubbleW / 2)
                .coerceIn(0, maxOf(0, containerW - bubbleW))
            val bubbleY = ((posY * travelY).roundToInt() - bubbleH - 6).coerceAtLeast(0)
            Box(
                modifier = Modifier
                    .offset { IntOffset(bubbleX, bubbleY) }
                    .size(bubbleW.dp, bubbleH.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bubbleOf(expression),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
