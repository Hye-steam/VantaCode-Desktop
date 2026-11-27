package dev.aigw.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import dev.aigw.desktop.ProviderMeta

/**
 * 供应商列表 + 详情（Android ProvidersScreen / ProviderDetailScreen 的桌面版）：
 * 每供应商卡片（账号数、启用开关、余额汇总），点进详情做账号管理
 * （签到、刷新余额/显示名、重新登录、停用/启用、删除、添加账号、导入/导出）。
 */
@Composable
fun ProvidersScreen(
    vm: GateViewModel,
    theme: ThemePalette,
    onSelect: (String) -> Unit,
    onAddAccount: (String) -> Unit,
) {
    val s = vm.state
    val providerIds = s.providerEnabled.keys.sorted()
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(providerIds) { providerId ->
            val accounts = s.accounts.filter { it.providerId == providerId }
            val enabled = s.providerEnabled[providerId] == true
            val balances = accounts.mapNotNull { it.balance }
            LoopCard(theme) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(providerId) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            ProviderMeta.displayName(providerId),
                            color = theme.ink,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "账号 ${accounts.count { it.enabled }}/${accounts.size} 启用" +
                                if (balances.isNotEmpty()) " · 积分合计 %.1f".format(balances.sum()) else "",
                            color = theme.ink2,
                            fontSize = 13.sp,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { vm.setProviderEnabled(providerId, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.card,
                            checkedTrackColor = theme.primary,
                            uncheckedThumbColor = theme.card,
                            uncheckedTrackColor = theme.line,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                RowButtons(
                    { GhostButton(theme, "详情") { onSelect(providerId) } },
                    { GhostButton(theme, "添加账号") { onAddAccount(providerId) } },
                    {
                        if (accounts.isNotEmpty()) {
                            GhostButton(theme, "签到全部") { vm.checkinAll(providerId) }
                        }
                    },
                    {
                        if (accounts.isNotEmpty()) {
                            GhostButton(theme, "刷新余额") { vm.refreshCredits(providerId) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun ProviderDetailScreen(
    vm: GateViewModel,
    theme: ThemePalette,
    providerId: String,
    onBack: () -> Unit,
    onAddAccount: (String) -> Unit,
) {
    val s = vm.state
    val accounts = s.accounts.filter { it.providerId == providerId }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var showExport by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row {
                GhostButton(theme, "← 返回") { onBack() }
                Spacer(Modifier.weight(1f))
                PrimaryButton(theme, "添加账号") { onAddAccount(providerId) }
            }
        }
        item {
            LoopCard(theme) {
                Text(ProviderMeta.displayName(providerId), color = theme.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                val enabled = s.providerEnabled[providerId] == true
                Text(
                    "供应商${if (enabled) "已启用" else "已停用"} · 账号 ${accounts.size} 个",
                    color = theme.ink2,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                RowButtons(
                    { GhostButton(theme, if (enabled) "停用供应商" else "启用供应商") { vm.setProviderEnabled(providerId, !enabled) } },
                    { GhostButton(theme, "导出账号") { showExport = true } },
                    { GhostButton(theme, "导入账号") { showImport = true } },
                    { GhostButton(theme, "刷新显示名") { vm.refreshDisplayNames(providerId) } },
                )
            }
        }
        if (showExport) {
            item {
                var text by remember { mutableStateOf(vm.exportAccountEnvelope(providerId)) }
                LoopCard(theme) {
                    SectionTitle(theme, "导出账号（JSON 信封）")
                    LoopTextField(theme, text, { text = it }, "账号信封 JSON", singleLine = false, minLines = 4)
                    Spacer(Modifier.height(8.dp))
                    RowButtons(
                        { PrimaryButton(theme, "复制") { vm.copyText(text, "账号信封") } },
                        { GhostButton(theme, "关闭") { showExport = false } },
                    )
                }
            }
        }
        if (showImport) {
            item {
                var text by remember { mutableStateOf("") }
                LoopCard(theme) {
                    SectionTitle(theme, "导入账号（粘贴 JSON 信封）")
                    LoopTextField(theme, text, { text = it }, "粘贴信封 JSON", singleLine = false, minLines = 4)
                    Spacer(Modifier.height(8.dp))
                    RowButtons(
                        {
                            PrimaryButton(theme, "导入") {
                                vm.importAccountEnvelope(text)
                                showImport = false
                            }
                        },
                        { GhostButton(theme, "关闭") { showImport = false } },
                    )
                }
            }
        }
        items(accounts) { account ->
            LoopCard(theme) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(account.displayName, color = theme.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "uid ${account.uid}" +
                                (account.region?.let { " · 区域 $it" } ?: "") +
                                (account.balance?.let { " · 积分 %.1f".format(it) } ?: " · 积分未知") +
                                (if (account.checkedInToday) " · 今日已签到" else "") +
                                (if (account.inCooldown) " · 冷却中" else "") +
                                (if (account.enterprise) " · 企业号" else ""),
                            color = theme.ink2,
                            fontSize = 13.sp,
                        )
                        if (account.lastError != null) {
                            Text("最近错误：${account.lastError}", color = theme.danger, fontSize = 12.sp)
                        }
                        if (account.disabledReason != null && !account.enabled) {
                            Text("停用原因：${account.disabledReason}", color = theme.warning, fontSize = 12.sp)
                        }
                    }
                    Switch(
                        checked = account.enabled,
                        onCheckedChange = { vm.toggleAccount(providerId, account.uid, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = theme.card,
                            checkedTrackColor = theme.primary,
                            uncheckedThumbColor = theme.card,
                            uncheckedTrackColor = theme.line,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                RowButtons(
                    { GhostButton(theme, "签到") { vm.checkinAccount(providerId, account.uid) } },
                    { GhostButton(theme, "刷新余额") { vm.refreshAccountBalance(providerId, account.uid) } },
                    { GhostButton(theme, "重新登录") { vm.reloginAll(providerId) } },
                    { DangerTextButton(theme, "删除") { confirmDelete = account.uid } },
                )
                if (confirmDelete == account.uid) {
                    Spacer(Modifier.height(8.dp))
                    Text("确定删除该账号？此操作不可撤销。", color = theme.danger, fontSize = 13.sp)
                    RowButtons(
                        {
                            DangerTextButton(theme, "确认删除") {
                                vm.removeAccount(providerId, account.uid)
                                confirmDelete = null
                            }
                        },
                        { GhostButton(theme, "取消") { confirmDelete = null } },
                    )
                }
            }
        }
        if (accounts.isEmpty()) {
            item {
                LoopCard(theme) {
                    Text("还没有账号，点击右上角「添加账号」。", color = theme.ink2, fontSize = 14.sp)
                }
            }
        }
    }
}
