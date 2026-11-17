package dev.aigw.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** sheet 380ms 入场缓动（cubic-bezier(.22,1,.36,1)）。 */
val LoopSheetEase: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * Loop 底部弹层（对应设计稿 .sheet）。
 *
 * 遮罩半透明深蓝，点按关闭；拖拽把手下滑超过 90px（联动遮罩透明度）松手关闭。
 * 弹层高度自适应内容，最高为屏幕一半，超过时内容在弹层内部上下滚动；
 * 底部预留系统导航栏/手势区，键盘弹出时整体上推。
 */
@Composable
fun LoopSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onVisibilityChanged: (Boolean) -> Unit = {},
    sheetContent: @Composable ColumnScope.() -> Unit,
) {
    var dragOffset by remember { mutableStateOf(0f) }
    // 弹层关闭后拖拽偏移需要复位
    if (!visible && dragOffset != 0f) dragOffset = 0f

    // 同步「是否有弹层打开」给导航层：打开时隐藏底部导航。弹层从树上移除时也要复位。
    androidx.compose.runtime.LaunchedEffect(visible) { onVisibilityChanged(visible) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { onVisibilityChanged(false) }
    }

    val maskAlpha = ((1f - dragOffset / 400f).coerceIn(0f, 1f)) * 0.45f
    // 高度跟随内容；上限参照设计稿（sheet-body 65vh + 头部/把手），取屏幕的 75%；超出则内部滚动
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val sheetMaxHeight = (config.screenHeightDp * 0.75f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        // 遮罩：拖拽时透明度随偏移衰减
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = maskAlpha }
                    .background(Color(0xFF0F1932).copy(alpha = 0.45f)),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(380, easing = LoopSheetEase),
            ) + fadeIn(tween(120)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(280, easing = LoopSheetEase),
            ) + fadeOut(tween(180)),
            // 键盘弹出时整体上推（不压缩弹层内容），避免内容被挤成一小条
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMaxHeight)
                    .graphicsLayer { translationY = dragOffset }
                    .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), ambientColor = Color(0x2E14285A))
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(LoopColors.surface)
                    .navigationBarsPadding(),
            ) {
                // 拖拽把手（固定在顶部，不随内容滚动）：下滑 > 90px 松手关闭
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggable(
                            state = rememberDraggableState { delta ->
                                dragOffset = (dragOffset + delta).coerceAtLeast(0f)
                            },
                            orientation = Orientation.Vertical,
                            onDragStopped = {
                                if (dragOffset > 90f) {
                                    onDismiss()
                                    dragOffset = 0f
                                } else {
                                    dragOffset = 0f
                                }
                            },
                        )
                        .padding(top = 12.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(48.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LoopColors.handleBar),
                    )
                }
                // 内容区：高度跟随内容，超过弹层上限时内部上下滚动（不用 weight，避免柱状弹层被撑满）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    sheetContent()
                }
            }
        }
    }
}

/** 面板头部：图标徽章 + 标题 + 右侧按压缩放关闭按钮（hover 旋转 90 度近似为按压旋转）。 */
@Composable
fun LoopSheetHeader(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
        Spacer(Modifier.weight(1f))
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val rotation by animateFloatAsState(
            targetValue = if (pressed) 90f else 0f,
            animationSpec = tween(200),
            label = "closeRotation",
        )
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(CircleShape)
                .background(if (pressed) LoopColors.danger.copy(alpha = 0.1f) else LoopColors.closeBg)
                .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "关闭",
                tint = if (pressed) LoopColors.danger else LoopColors.muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** info-row：标签 + 彩色圆点 + 右侧蓝色数值。 */
@Composable
fun LoopInfoRow(label: String, value: String, dotColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.fieldBg)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = LoopColors.text)
        Spacer(Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LoopColors.brand)
    }
}

/** 快捷按钮（key-action-btn）。 */
@Composable
fun LoopKeyActionButton(
    text: String,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(150),
        label = "keyActionScale",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                when {
                    filled && pressed -> LoopColors.brand
                    filled -> LoopColors.brandSoft
                    else -> LoopColors.fieldBg
                },
            )
            .border(
                1.dp,
                when {
                    filled -> LoopColors.brand.copy(alpha = 0f)
                    else -> LoopColors.line
                },
                RoundedCornerShape(14.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = when {
                filled && pressed -> Color.White
                filled -> LoopColors.brand
                else -> LoopColors.muted
            },
        )
    }
}

/** 复制行：只读文本 + 复制按钮（copy-row），对应 fKey focus 态近似为按压态。 */
@Composable
fun LoopCopyRow(
    value: String,
    modifier: Modifier = Modifier,
    onCopy: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.fieldBg)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value,
            fontSize = 14.sp,
            color = LoopColors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (pressed) LoopColors.brand else LoopColors.brandSoft)
                .clickable(interactionSource = interaction, indication = null, onClick = onCopy)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(
                "复制",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (pressed) Color.White else LoopColors.brand,
            )
        }
    }
}

/** 表单输入框（form-input）：聚焦时白底 + 蓝色描边。 */
@Composable
fun LoopFormInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(fontSize = 15.sp, color = LoopColors.text),
        cursorBrush = SolidColor(LoopColors.brand),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(if (focused) LoopColors.surface else LoopColors.fieldBg)
            .border(
                1.dp,
                if (focused) LoopColors.brand.copy(alpha = 0.4f) else LoopColors.line,
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 15.sp, color = LoopColors.faint)
                }
                inner()
            }
        },
    )
}

/** 保存/执行按钮（save-btn）：全宽渐变按钮。 */
@Composable
fun LoopSaveButton(
    text: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(150),
        label = "saveScale",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                10.dp,
                RoundedCornerShape(18.dp),
                spotColor = LoopColors.brand.copy(alpha = 0.35f * if (enabled) 1f else 0f),
            )
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (enabled) {
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(LoopColors.brand, LoopColors.brandDeep),
                    )
                } else {
                    androidx.compose.ui.graphics.SolidColor(LoopColors.surfaceAlt)
                },
            )
            .border(
                1.dp,
                if (enabled) Color.Transparent else LoopColors.line,
                RoundedCornerShape(18.dp),
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (enabled) Color.White else LoopColors.muted,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else LoopColors.muted,
            )
        }
    }
}

/**
 * 添加供应商弹层（设计稿 sheetAdd）。
 *
 * 名称/接口地址/密钥三项，名称与地址必填后保存才可用。
 */
@Composable
fun AddProviderSheet(
    onDismiss: () -> Unit,
    onVisibilityChanged: (Boolean) -> Unit = {},
    onSave: (String, String, String) -> Boolean,
    /** 预填的 Base URL（豆包指向本机反代），空表示让用户自己填。 */
    defaultUrl: String = "",
    /** 预填的账号名。 */
    defaultName: String = "",
) {
    var name by remember { mutableStateOf(defaultName) }
    var url by remember { mutableStateOf(defaultUrl) }
    var key by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LoopSheet(visible = true, onDismiss = onDismiss, onVisibilityChanged = onVisibilityChanged) {
        LoopSheetHeader(
            icon = Icons.Filled.Add,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "添加供应商",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LoopFormInput(value = name, onValueChange = { name = it }, placeholder = "名称")
            LoopFormInput(value = url, onValueChange = { url = it }, placeholder = "OpenAI / 接口")
            LoopFormInput(value = key, onValueChange = { key = it }, placeholder = "API密钥", password = true)
            error?.let { Text(it, fontSize = 12.sp, color = LoopColors.danger) }
            LoopSaveButton(
                text = "保存",
                enabled = name.isNotBlank() && url.isNotBlank() && key.isNotBlank(),
                icon = Icons.Outlined.Check,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (!onSave(name, url, key)) {
                    error = "添加失败"
                } else {
                    error = null
                }
            }
        }
    }
}
