package dev.aigw.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Loop 仪表盘（设计稿 viewDash）。
 *
 * 状态卡（呼吸灯/服务地址/运行按钮/时长）+ 2×2 统计卡 + 功能组 + 三张底部弹层。
 */
@Composable
fun LoopDashboard(vm: AppViewModel) {
    val state = vm.state
    val clipboard = LocalClipboardManager.current
    var openSheet by remember { mutableStateOf<LoopDashSheet?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshAll()
            delay(5000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(LoopColors.bg)
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "仪表盘",
                fontSize = 28.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.text,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            StatusCard(state) { if (state.running) vm.stopGateway() else vm.startGateway() }

            GroupLabel("今日")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "请求",
                    "${state.todayRequests}",
                    Modifier.weight(1f),
                )
                MetricTile(
                    "成功率",
                    if (state.todayRequests > 0) {
                        "${state.todaySuccess * 100 / state.todayRequests}%"
                    } else "—",
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "平均耗时",
                    formatLatency(state.avgLatencyMs),
                    Modifier.weight(1f),
                )
                MetricTile(
                    "Token",
                    if (state.todayTokens > 0) formatNumber(state.todayTokens.toDouble()) else "—",
                    Modifier.weight(1f),
                )
            }

            GroupLabel("账号")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricTile(
                    "可用账号",
                    "${state.accountEnabled}/${state.accountTotal}",
                    Modifier.weight(1f),
                )
                MetricTile(
                    "总积分",
                    if (state.creditsKnown > 0) formatNumber(state.totalCredits) else "—",
                    Modifier.weight(1f),
                    accent = LoopColors.warning,
                )
            }

            GroupLabel("快捷")
            FunctionGroup(onOpen = { openSheet = it }, onRelogin = { vm.reloginAll() })
            Spacer(Modifier.height(170.dp))
        }
    }

    when (openSheet) {
        LoopDashSheet.Sign -> SignSheet(vm, state, onDismiss = { openSheet = null })
        LoopDashSheet.Address -> AddressSheet(state, vm, clipboard, onDismiss = { openSheet = null })
        LoopDashSheet.Status -> StatusSheet(vm, state, onDismiss = { openSheet = null })
        null -> Unit
    }
}

private enum class LoopDashSheet { Sign, Address, Status }

// ------------------------------ 状态卡 ------------------------------

/**
 * 页面唯一的主焦点：服务是否在跑、地址在哪、怎么启停。
 * 其余数据都退到次级。
 */
@Composable
private fun StatusCard(state: UiState, onStartStop: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (state.running) LoopColors.success else LoopColors.warning),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.running) "运行中" else "未运行",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (state.running) LoopColors.success else LoopColors.subText,
            )
            Spacer(Modifier.weight(1f))
            if (state.running && state.startedAt > 0) {
                Text(
                    formatUptimeClock(System.currentTimeMillis() - state.startedAt),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = LoopColors.muted,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "127.0.0.1",
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.text,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                ":${state.port}",
                fontSize = 23.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.brandLight,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (state.running) {
                        androidx.compose.ui.graphics.SolidColor(LoopColors.surfaceAlt)
                    } else {
                        LoopColors.brandGradient()
                    },
                )
                .clickable(onClick = onStartStop),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!state.running) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                if (state.running) "停止网关" else "启动网关",
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (state.running) LoopColors.subText else Color.White,
            )
        }
    }
}

// ------------------------------ 指标 ------------------------------

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = LoopColors.muted,
        modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
    )
}

/**
 * 指标块：标签小、数值大。
 * 不用进度条——积分和 Token 没有天然上限，画一条永远满的条只会误导。
 */
@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = LoopColors.muted,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            fontSize = 21.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = accent ?: LoopColors.text,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

// ------------------------------ 功能组 ------------------------------

@Composable
private fun FunctionGroup(onOpen: (LoopDashSheet) -> Unit, onRelogin: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp)),
    ) {
        FuncRow("全量签到") { onOpen(LoopDashSheet.Sign) }
        HorizontalDivider(color = LoopColors.line, thickness = 1.dp)
        FuncRow("检查登录") { onRelogin() }
        HorizontalDivider(color = LoopColors.line, thickness = 1.dp)
        FuncRow("网关地址") { onOpen(LoopDashSheet.Address) }
        HorizontalDivider(color = LoopColors.line, thickness = 1.dp)
        FuncRow("运行状态") { onOpen(LoopDashSheet.Status) }
    }
}

@Composable
private fun FuncRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, lineHeight = 22.sp, color = LoopColors.text)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = LoopColors.faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ------------------------------ 格式化 ------------------------------

private fun formatNumber(value: Double): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
    value >= 1_000 -> String.format("%.1fK", value / 1_000)
    else -> value.toLong().toString()
}

/** 超过 1 秒转秒并保留一位小数，避免出现 45948 ms 这种难读的数。 */
private fun formatLatency(ms: Long): String = when {
    ms <= 0 -> "—"
    ms < 1000 -> "${ms}ms"
    else -> String.format("%.1fs", ms / 1000.0)
}

private fun formatUptimeClock(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val totalSec = ms / 1000
    return "%02d:%02d:%02d".format(totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
}

@Composable
private fun SignSheet(vm: AppViewModel, state: UiState, onDismiss: () -> Unit) {
    val pending = state.accounts.count { !it.checkedInToday && it.enabled }
    val lastCheckin = state.accounts.map { it.lastCheckinAt }.maxOrNull() ?: 0L
    LoopSheet(visible = true, onDismiss = onDismiss, onVisibilityChanged = { vm.setSheetOpen(it) }) {
        LoopSheetHeader(
            icon = Icons.Outlined.CheckCircle,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "全量签到",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoopInfoRow(
                label = "待签到账号",
                value = if (state.accountTotal == 0) "0 个" else "$pending 个",
                dotColor = LoopColors.brand,
            )
            LoopInfoRow(
                label = "上次执行",
                value = if (lastCheckin > 0) formatLoopClock(lastCheckin) else "尚未签到",
                dotColor = LoopColors.warning,
            )
            LoopSaveButton(
                text = "立即执行签到",
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                vm.checkinAll()
                onDismiss()
            }
        }
    }
}

// ------------------------------ 弹层：接入地址 ------------------------------

@Composable
private fun AddressSheet(state: UiState, vm: AppViewModel, clipboard: ClipboardManager, onDismiss: () -> Unit) {
    val local = "http://127.0.0.1:${state.port}/v1"
    val lan = if (state.bindLan) state.lanAddresses.firstOrNull()?.let { "http://$it:${state.port}/v1" } else null
    var showEnableLan by remember { mutableStateOf(false) }

    if (showEnableLan) {
        AlertDialog(
            onDismissRequest = { showEnableLan = false },
            containerColor = LoopColors.surface,
            title = {
                Text(
                    "开启监听局域网",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoopColors.text,
                )
            },
            text = {
                Text(
                    "你还没有开启局域网模式。开启后，同一 Wi-Fi 下的其它设备才能访问。现在开启吗？",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    color = LoopColors.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.setBindLan(true)
                    showEnableLan = false
                }) {
                    Text("开启", color = LoopColors.brand, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableLan = false }) {
                    Text("暂不", color = LoopColors.muted)
                }
            },
        )
    }

    LoopSheet(visible = true, onDismiss = onDismiss, onVisibilityChanged = { vm.setSheetOpen(it) }) {
        LoopSheetHeader(
            icon = Icons.Outlined.Link,
            iconBg = LoopColors.accentGreenLight,
            iconTint = LoopColors.success,
            title = "接入地址",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("本机 / URL", fontSize = 12.sp, color = LoopColors.muted, modifier = Modifier.padding(bottom = 2.dp))
            LoopCopyRow(value = local) {
                clipboard.setText(AnnotatedString(local))
                vm.notify("已复制到剪贴板")
            }
            Text("区域网 / URL", fontSize = 12.sp, color = LoopColors.muted, modifier = Modifier.padding(bottom = 2.dp))
            LoopCopyRow(
                value = lan ?: if (!state.bindLan) "未开启（点右侧复制开启监听局域网）" else "未连接 Wi-Fi / 未获取到局域网地址",
            ) {
                if (lan != null) {
                    clipboard.setText(AnnotatedString(lan))
                    vm.notify("已复制到剪贴板")
                } else if (!state.bindLan) {
                    showEnableLan = true
                } else {
                    vm.notify("暂无可复制的局域网地址")
                }
            }
            Text("API 密钥", fontSize = 12.sp, color = LoopColors.muted, modifier = Modifier.padding(bottom = 2.dp))
            LoopCopyRow(
                value = if (state.allowNoKey) "无密钥（开放访问）" else state.apiKey.ifBlank { "未设置" },
            ) {
                if (!state.allowNoKey && state.apiKey.isNotBlank()) {
                    clipboard.setText(AnnotatedString(state.apiKey))
                    vm.notify("已复制到剪贴板")
                } else {
                    vm.notify("当前无需密钥")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 12.dp)) {
                LoopKeyActionButton(text = "随机密钥", filled = true, modifier = Modifier.weight(1f)) {
                    vm.generateApiKey()
                }
                LoopKeyActionButton(text = "无密钥", filled = false, modifier = Modifier.weight(1f)) {
                    vm.setNoKeyEnabled(!state.allowNoKey)
                }
            }
        }
    }
}

// ------------------------------ 弹层：运行状态 ------------------------------

@Composable
private fun StatusSheet(vm: AppViewModel, state: UiState, onDismiss: () -> Unit) {
    LoopSheet(visible = true, onDismiss = onDismiss, onVisibilityChanged = { vm.setSheetOpen(it) }) {
        LoopSheetHeader(
            icon = Icons.Outlined.Schedule,
            iconBg = LoopColors.accentOrangeSoft,
            iconTint = LoopColors.warning,
            title = "运行状态",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LoopInfoRow(
                label = "运行时长",
                value = if (state.running && state.startedAt > 0) formatUptimeClock(System.currentTimeMillis() - state.startedAt) else "-",
                dotColor = LoopColors.brand,
            )
            LoopInfoRow(
                label = "今日成功率",
                value = if (state.todayRequests > 0) "%.0f%%".format(state.todaySuccess * 100.0 / state.todayRequests) else "-",
                dotColor = LoopColors.success,
            )
            LoopInfoRow(
                label = "缓存命中",
                value = if (state.todayCachedTokens > 0) {
                    "%.0f%%".format(state.todayCachedTokens * 100.0 / state.todayInputTokens.coerceAtLeast(1))
                } else "-",
                dotColor = LoopColors.purple,
            )
            LoopInfoRow(
                label = "平均延迟",
                value = if (state.todayRequests > 0) "${state.avgLatencyMs} ms" else "-",
                dotColor = LoopColors.warning,
            )
        }
    }
}

private fun formatLoopClock(ms: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        .withZone(java.time.ZoneId.systemDefault())
        .format(java.time.Instant.ofEpochMilli(ms))

private typealias ClipboardManager = androidx.compose.ui.platform.ClipboardManager
