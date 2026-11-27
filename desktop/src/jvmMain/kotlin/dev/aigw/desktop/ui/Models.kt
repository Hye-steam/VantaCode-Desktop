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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.desktop.GateViewModel
import dev.aigw.desktop.ModelUi
import dev.aigw.desktop.ProviderMeta

/**
 * 模型浏览器（Android ModelsScreen 的桌面版）：搜索、按供应商筛选、
 * 类型徽标、上下文/最大输出、详情、单测/批量探测。
 */
@Composable
fun ModelsScreen(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var query by remember { mutableStateOf("") }
    var providerFilter by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<ModelUi?>(null) }

    val providers = s.models.map { it.providerId }.distinct().sorted()
    val filtered = s.models.filter {
        (providerFilter == null || it.providerId == providerFilter) &&
            (query.isBlank() || it.id.contains(query, ignoreCase = true) ||
                it.displayName.contains(query, ignoreCase = true))
    }

    Column(Modifier.padding(16.dp)) {
        LoopTextField(theme, query, { query = it }, "搜索模型…")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(theme, if (providerFilter == null) "全部供应商 ✓" else "全部供应商") { providerFilter = null }
            providers.forEach { pid ->
                GhostButton(
                    theme,
                    if (providerFilter == pid) "${ProviderMeta.displayName(pid)} ✓" else ProviderMeta.displayName(pid),
                ) { providerFilter = pid }
            }
        }
        Spacer(Modifier.height(8.dp))
        RowButtons(
            { PrimaryButton(theme, if (s.loadingModels) "刷新中…" else "刷新模型", enabled = !s.loadingModels) { vm.refreshModels() } },
            {
                PrimaryButton(
                    theme,
                    if (s.probingModels != null) "探测中 ${s.probingModels!!.first}/${s.probingModels!!.second}" else "探测全部模型",
                    enabled = s.probingModels == null && s.models.isNotEmpty(),
                ) { vm.probeModels() }
            },
        )
        Spacer(Modifier.height(8.dp))
        Text("${filtered.size} 个模型", color = theme.ink3, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered) { model ->
                val probe = s.probeResults[model.id]
                LoopCard(theme) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { detail = model },
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(model.id, color = theme.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                buildString {
                                    append(ProviderMeta.displayName(model.providerId))
                                    if (model.type.isNotBlank()) append(" · ${model.type}")
                                    if (model.displayName.isNotBlank()) append(" · ${model.displayName}")
                                    if (model.contextWindow > 0) append(" · 上下文 ${model.contextWindow}")
                                    if (model.region != null) append(" · ${model.region}")
                                    if (model.tags.isNotEmpty()) append(" · ${model.tags.joinToString(",")}")
                                },
                                color = theme.ink2,
                                fontSize = 12.sp,
                            )
                            if (probe != null) {
                                Text(
                                    if (probe.ok) "探测：可用" else "探测失败：${probe.message}",
                                    color = if (probe.ok) theme.success else theme.danger,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        GhostButton(theme, "探测") { vm.probeSingle(model) }
                    }
                }
            }
        }
    }

    val d = detail
    if (d != null) {
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(d.id, color = theme.ink) },
            text = {
                Text(
                    "供应商：${ProviderMeta.displayName(d.providerId)}\n" +
                        "显示名：${d.displayName.ifBlank { "—" }}\n" +
                        "类型：${d.type.ifBlank { "—" }}\n" +
                        "上下文窗口：${if (d.contextWindow > 0) d.contextWindow.toString() else "—"}\n" +
                        "最大输出：${if (d.maxOutputTokens > 0) d.maxOutputTokens.toString() else "—"}\n" +
                        "区域：${d.region ?: "—"}\n" +
                        "标签：${if (d.tags.isEmpty()) "—" else d.tags.joinToString(", ")}",
                    color = theme.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.probeSingle(d); detail = null }) { Text("探测", color = theme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { detail = null }) { Text("关闭", color = theme.ink2) }
            },
        )
    }
}
