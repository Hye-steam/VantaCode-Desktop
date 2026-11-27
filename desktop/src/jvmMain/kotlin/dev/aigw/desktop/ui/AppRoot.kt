package dev.aigw.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.desktop.GateViewModel
import java.awt.Desktop
import java.io.File
import java.net.URI

/**
 * 主界面：左侧导航（首页 / 供应商 / 模型 / 日志 / 设置，Android AppRoot 的 5 Tab），
 * 右侧内容区；供应商详情与添加账号为子页；底部消息条；更新/公告弹窗。
 */
@Composable
fun AppRoot(vm: GateViewModel, dataDir: File) {
    val theme = vm.theme()
    val s = vm.state
    var tab by remember { mutableStateOf(Tab.Home) }
    var selectedProvider by remember { mutableStateOf<String?>(null) }
    var addAccountProvider by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(theme.pageBg)) {
        Row(Modifier.weight(1f)) {
            // 左侧导航
            Column(
                Modifier.width(148.dp).fillMaxHeight().background(theme.navBg).padding(12.dp),
            ) {
                Text("VantaGate", color = theme.ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(vm.version, color = theme.ink3, fontSize = 12.sp)
                Spacer(Modifier.padding(top = 16.dp))
                Tab.values().forEach { t ->
                    val selected = tab == t && selectedProvider == null && addAccountProvider == null
                    GhostButton(theme, if (selected) "${t.label} ✓" else t.label) {
                        tab = t
                        selectedProvider = null
                        addAccountProvider = null
                    }
                    Spacer(Modifier.padding(top = 4.dp))
                }
            }
            // 右侧内容
            Box(Modifier.weight(1f)) {
                val addPid = addAccountProvider
                val detailPid = selectedProvider
                when {
                    addPid != null -> AddAccountScreen(vm, theme, addPid) {
                        addAccountProvider = null
                    }
                    detailPid != null -> ProviderDetailScreen(
                        vm = vm,
                        theme = theme,
                        providerId = detailPid,
                        onBack = { selectedProvider = null },
                        onAddAccount = { addAccountProvider = it },
                    )
                    else -> when (tab) {
                        Tab.Home -> DashboardScreen(vm, theme)
                        Tab.Providers -> ProvidersScreen(
                            vm = vm,
                            theme = theme,
                            onSelect = { selectedProvider = it },
                            onAddAccount = { addAccountProvider = it },
                        )
                        Tab.Models -> ModelsScreen(vm, theme)
                        Tab.Logs -> LogsScreen(vm, theme)
                        Tab.Settings -> SettingsScreen(vm, theme, dataDir)
                    }
                }
            }
        }
        // 底部消息条
        val message = s.message
        if (message != null) {
            Row(
                Modifier.fillMaxWidth().background(theme.primarySoft).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = theme.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.clearMessage() }) { Text("关闭", color = theme.primary) }
            }
        }
    }

    // 更新弹窗
    val update = s.updateInfo
    if (update != null) {
        AlertDialog(
            onDismissRequest = { vm.consumeUpdate() },
            title = { Text("发现新版本 ${update.version}", color = theme.ink) },
            text = { Text(update.notes.ifBlank { "点击下载获取最新版。" }, color = theme.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { Desktop.getDesktop().browse(URI(update.url)) }
                    vm.consumeUpdate()
                }) { Text("下载", color = theme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { vm.consumeUpdate() }) { Text("忽略", color = theme.ink2) }
            },
        )
    }

    // 公告弹窗（远程公告在 :core 已被上游禁用，正常不会出现；保留展示通道）
    val announcement = s.announcement
    if (announcement != null) {
        AlertDialog(
            onDismissRequest = { vm.consumeAnnouncement() },
            title = { Text(announcement.title, color = theme.ink) },
            text = { Text(announcement.content, color = theme.ink2) },
            confirmButton = {
                TextButton(onClick = { vm.consumeAnnouncement() }) { Text("知道了", color = theme.primary) }
            },
        )
    }
}

enum class Tab(val label: String) {
    Home("首页"),
    Providers("供应商"),
    Models("模型"),
    Logs("日志"),
    Settings("设置"),
}
