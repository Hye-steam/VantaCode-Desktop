package dev.aigw.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.desktop.GateViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页仪表盘（Android LoopDashboard 的桌面版）：
 * 网关启停、状态卡（运行时长/端口/今日请求/成功/失败/平均延迟/今日 Token）、
 * 积分合计、账号数、本地 + 局域网接入地址、API Key 复制。
 */
@Composable
fun DashboardScreen(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LoopCard(theme) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (s.running) "网关运行中" else "网关已停止",
                            color = if (s.running) theme.success else theme.ink2,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (s.running) "已运行 ${formatDuration(System.currentTimeMillis() - s.startedAt)} · 端口 ${s.port}"
                            else "点击启动后，客户端用下面的地址接入",
                            color = theme.ink3,
                            fontSize = 13.sp,
                        )
                    }
                    if (s.running) {
                        GhostButton(theme, "停止") { vm.stopGateway() }
                    } else {
                        PrimaryButton(theme, "启动网关") { vm.startGateway() }
                    }
                }
                if (s.startError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("启动失败：${s.startError}", color = theme.danger, fontSize = 13.sp)
                }
            }
        }
        item {
            LoopCard(theme) {
                SectionTitle(theme, "今日统计")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(theme, "请求", "${s.todayRequests}")
                    Stat(theme, "成功", "${s.todaySuccess}")
                    Stat(theme, "失败", "${s.todayFail}")
                    Stat(theme, "平均延迟", "${s.avgLatencyMs}ms")
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat(theme, "Token", "${s.todayTokens}")
                    Stat(theme, "输入", "${s.todayInputTokens}")
                    Stat(theme, "输出", "${s.todayOutputTokens}")
                    Stat(theme, "缓存命中", "${s.todayCachedTokens}")
                }
            }
        }
        item {
            LoopCard(theme) {
                SectionTitle(theme, "账号与积分")
                Text(
                    "账号 ${s.accountEnabled}/${s.accountTotal} 启用 · 积分合计 " +
                        if (s.creditsKnown > 0) "%.1f（${s.creditsKnown} 个账号有余额数据）".format(s.totalCredits) else "—（暂无余额数据）",
                    color = theme.ink2,
                    fontSize = 14.sp,
                )
            }
        }
        item {
            LoopCard(theme) {
                SectionTitle(theme, "接入地址")
                val base = "http://127.0.0.1:${s.port}/v1"
                UrlRow(theme, "本地", base) { vm.copyText(base, "Base URL") }
                Spacer(Modifier.height(6.dp))
                if (s.bindLan && s.lanAddresses.isNotEmpty()) {
                    s.lanAddresses.forEach { lan ->
                        val url = "http://$lan:${s.port}/v1"
                        UrlRow(theme, "局域网", url) { vm.copyText(url, "局域网地址") }
                        Spacer(Modifier.height(6.dp))
                    }
                } else if (!s.bindLan) {
                    Text("局域网接入未开启（设置 → 网络端口 → 绑定局域网）", color = theme.ink3, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                }
                UrlRow(theme, "模型列表", "$base/models") { vm.copyText("$base/models", "模型列表地址") }
                Spacer(Modifier.height(6.dp))
                UrlRow(theme, "健康检查", "http://127.0.0.1:${s.port}/healthz") {
                    vm.copyText("http://127.0.0.1:${s.port}/healthz", "健康检查地址")
                }
            }
        }
        item {
            LoopCard(theme) {
                SectionTitle(theme, "API Key")
                Row {
                    Text(
                        if (s.allowNoKey) "当前允许无 Key 调用" else maskKey(s.apiKey),
                        color = theme.ink2,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(theme, "复制") { vm.copyText(s.apiKey, "API Key") }
                }
                Spacer(Modifier.height(8.dp))
                Text("客户端请求头：Authorization: Bearer <key>，或 ?key= 参数。", color = theme.ink3, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Stat(theme: ThemePalette, label: String, value: String) {
    Column {
        Text(value, color = theme.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = theme.ink3, fontSize = 12.sp)
    }
}

@Composable
private fun UrlRow(theme: ThemePalette, label: String, url: String, onCopy: () -> Unit) {
    Row {
        Text(label, color = theme.ink2, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
        Text(url, color = theme.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        GhostButton(theme, "复制", onClick = onCopy)
    }
}

private fun maskKey(key: String): String =
    if (key.length <= 8) key else key.take(6) + "…" + key.takeLast(4)

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d小时%02d分".format(h, m) else "%d分%02d秒".format(m, s)
}

fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
