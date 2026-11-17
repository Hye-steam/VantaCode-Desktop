package dev.aigw.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 账号头像：有真实头像 URL 时加载网络图，否则回退供应商图标。 */
@Composable
private fun AccountAvatar(kind: DetailKind, avatarUrl: String) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
        initialValue = null,
        avatarUrl,
    ) {
        if (avatarUrl.isBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                java.net.URL(avatarUrl).openStream().use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(
            bitmap = loaded.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )
        return
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(providerIconBg(kind)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(kind.logoRes),
            contentDescription = null,
            // 豆包换成官方方形图标后要铺满容器，缩在中间会显得又小又飘
            modifier = Modifier.size(28.dp),
        )
    }
}

private val SwipeRevealWidth = 88.dp

/**
 * 账号卡：按住提示「往左侧试试哦」；左滑露出红色删除区（宽度 88dp），
 * 滑过一半停在删除位，点击删除区删除；点击空白或其它卡片复位。
 */
@Composable
internal fun SwipeableAccountCard(
    kind: DetailKind,
    account: AccountUi,
    vm: AppViewModel,
    isOpen: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
) {
    val density = LocalDensity.current
    val revealPx = with(density) { SwipeRevealWidth.toPx() }
    val offset = remember(account.uid) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // 外部复位（点空白/打开其它卡片）时收回
    LaunchedEffect(isOpen) {
        offset.animateTo(if (isOpen) -revealPx else 0f, tween(220))
    }

    val title = when (kind) {
        DetailKind.Relay -> account.name
        DetailKind.DeepSeek -> account.name.takeIf { it.isNotBlank() && it != "DeepSeek" } ?: account.uid
        DetailKind.Chatglm -> account.name.takeIf { it.isNotBlank() } ?: account.uid
        DetailKind.Raccoon -> account.name.takeIf { it.isNotBlank() } ?: account.uid
        DetailKind.Mimo -> account.name.takeIf { it.isNotBlank() } ?: account.uid
        DetailKind.WorkBuddy -> account.name.takeIf { it.isNotBlank() && it != "WorkBuddy" } ?: account.uid
        DetailKind.Trae -> account.name.takeIf { it.isNotBlank() && it != "Trae" } ?: account.uid
        else -> account.uid
    }
    val cooling = account.cooldownUntil > System.currentTimeMillis()
    val statusText = when {
        cooling -> "冷却中"
        account.enabled -> "正常"
        else -> "异常"
    }
    val okState = account.enabled && !cooling
    val roleText = when (kind) {
        DetailKind.Relay -> account.baseUrl.ifBlank { "自定义" }
        DetailKind.Trae -> if (account.region == "global") "国际版" else "国内版"
        DetailKind.DeepSeek -> "网页版"
        DetailKind.Loomy -> "讯飞"
        DetailKind.Chatglm -> "GLM · 智谱清言"
        DetailKind.Raccoon -> "商汤 · 小浣熊"
        DetailKind.Mimo -> "小米 AI Studio"
        else -> buildString {
            append(if (account.region == "global") "国际版" else "国内版")
            if (account.enterprise) append(" · 企业号")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x1A14285A))
            .clip(RoundedCornerShape(20.dp)),
    ) {
        // 底层删除区：铺满卡片，右侧放按钮，滑开时完整可见
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .matchParentSize()
                .background(Brush.linearGradient(listOf(LoopColors.danger, LoopColors.dangerDeep)))
                .clickable(enabled = offset.value < -1f) { onDelete() }
                .padding(end = 26.dp),
        ) {
            Text("删除", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // 卡片本体（与全应用统一的白卡风格）
        Column(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(LoopColors.surface)
                .border(1.dp, LoopColors.line, RoundedCornerShape(20.dp))
                .pointerInput(account.uid, isOpen) {
                    detectTapGestures(
                        onPress = {
                            // 点卡片本体也收起已滑开的删除区
                            if (isOpen) onOpenChanged(false)
                            val hint = scope.launch {
                                delay(320)
                                vm.notify("往左侧试试哦")
                            }
                            tryAwaitRelease()
                            hint.cancel()
                        },
                    )
                }
                .pointerInput(account.uid, revealPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val open = offset.value < -revealPx / 2f
                            onOpenChanged(open)
                            scope.launch { offset.animateTo(if (open) -revealPx else 0f, tween(220)) }
                        },
                        onDragCancel = {
                            onOpenChanged(false)
                            scope.launch { offset.animateTo(0f, tween(220)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offset.snapTo((offset.value + dragAmount).coerceIn(-revealPx, 0f))
                            }
                        },
                    )
                }
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccountAvatar(kind = kind, avatarUrl = account.avatarUrl)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LoopColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        roleText,
                        fontSize = 12.sp,
                        color = LoopColors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (okState) LoopColors.successSoft else LoopColors.danger.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (okState) LoopColors.success else LoopColors.danger),
                    )
                    Text(
                        statusText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (okState) LoopColors.success else LoopColors.danger,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LoopColors.line))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("积分 ", fontSize = 12.sp, color = LoopColors.muted)
                Text(balanceText(account), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
                if (account.checkedInToday) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "已签到",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LoopColors.success,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoopColors.successSoft)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "导出",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoopColors.brand,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(LoopColors.brandSoft)
                        .clickable(onClick = onExport)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (account.enabled) "禁用" else "启用",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (account.enabled) LoopColors.muted else LoopColors.success,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (account.enabled) LoopColors.fieldBg else LoopColors.successSoft)
                        .clickable {
                            vm.toggleAccount(account.providerId, account.uid, !account.enabled)
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
