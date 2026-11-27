package dev.aigw.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.desktop.GateViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * 日志（Android LogsScreen 的桌面版）：调用记录 + 请求记录（事件日志），
 * 清空、导出为文本文件。
 */
@Composable
fun LogsScreen(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var tab by remember { mutableStateOf(0) } // 0 调用记录，1 请求记录

    Column(Modifier.padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(theme, if (tab == 0) "调用记录 ✓" else "调用记录") { tab = 0 }
            GhostButton(theme, if (tab == 1) "请求记录 ✓" else "请求记录") { tab = 1 }
            Spacer(Modifier.weight(1f))
            GhostButton(theme, "导出") { exportLogs(vm) }
            GhostButton(theme, "清空") { vm.clearLogs() }
        }
        Spacer(Modifier.height(8.dp))
        if (tab == 0) {
            Text("最近 ${s.calls.size} 条（新的在后）", color = theme.ink3, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.calls.reversed()) { call ->
                    LoopCard(theme) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(call.model, color = theme.ink, fontSize = 14.sp)
                                Text(
                                    "${call.via} · ${call.latencyMs}ms · " +
                                        "输入 ${call.inputTokens} / 输出 ${call.outputTokens}" +
                                        if (call.cachedTokens > 0) " / 缓存 ${call.cachedTokens}" else "",
                                    color = theme.ink2,
                                    fontSize = 12.sp,
                                )
                                if (call.detail.isNotBlank()) {
                                    Text(call.detail, color = theme.ink3, fontSize = 12.sp)
                                }
                            }
                            Text(
                                if (call.ok) "成功" else "失败",
                                color = if (call.ok) theme.success else theme.danger,
                                fontSize = 13.sp,
                            )
                        }
                        Text(formatTime(call.time), color = theme.ink3, fontSize = 11.sp)
                    }
                }
            }
        } else {
            Text("最近 24 小时，最多 3000 条", color = theme.ink3, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(s.events.reversed()) { event ->
                    val color = when (event.level) {
                        "ERROR" -> theme.danger
                        "WARN" -> theme.warning
                        "SUCCESS" -> theme.success
                        else -> theme.ink2
                    }
                    LoopCard(theme) {
                        Row {
                            Text("[${event.level}] ", color = color, fontSize = 13.sp)
                            Text("[${event.tag}] ", color = theme.ink2, fontSize = 13.sp)
                            Text(event.message, color = theme.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                        Text(formatTime(event.time), color = theme.ink3, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

private fun exportLogs(vm: GateViewModel) {
    val dialog = FileDialog(null as Frame?, "导出日志", FileDialog.SAVE)
    dialog.file = "vantagate-logs.txt"
    dialog.isVisible = true
    val file = dialog.file ?: return
    val dir = dialog.directory ?: return
    runCatching { File(dir, file).writeText(vm.exportLogsText()) }
        .onSuccess { vm.notify("日志已导出") }
        .onFailure { vm.notify("导出失败：${it.message}") }
}
