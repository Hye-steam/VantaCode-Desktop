package dev.aigw.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.aigw.gateway.ProxyType
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * 设置（Android SettingsScreen 的桌面版）：
 * 网络端口 / 并发与容错 / 代理 / 请求改写 / 系统提示词 / 悬浮球 /
 * 外观主题 / 反馈工单 / 检查更新 / 关于。
 */
@Composable
fun SettingsScreen(vm: GateViewModel, theme: ThemePalette, dataDir: File) {
    val s = vm.state
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { NetworkSection(vm, theme) }
        item { ConcurrencySection(vm, theme) }
        item { ProxySection(vm, theme) }
        item { RewriteSection(vm, theme) }
        item { SystemPromptSection(vm, theme) }
        item { BubbleSection(vm, theme) }
        item { ThemeSection(vm, theme) }
        item { FeedbackSection(vm, theme) }
        item { UpdateSection(vm, theme) }
        item {
            LoopCard(theme) {
                SectionTitle(theme, "关于")
                Text("VantaGate 桌面版 ${vm.version}（对应 Android 2.17 功能集）", color = theme.ink2, fontSize = 14.sp)
                Text("数据目录：${dataDir.absolutePath}", color = theme.ink2, fontSize = 13.sp)
                Text(
                    "媒体输出：${File(System.getProperty("user.home"), "Downloads/VantaGate/media").absolutePath}",
                    color = theme.ink2,
                    fontSize = 13.sp,
                )
                Text("反馈 QQ 群：1094669183", color = theme.ink2, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NetworkSection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var port by remember(s.port) { mutableStateOf(s.port.toString()) }
    var apiKey by remember(s.apiKey) { mutableStateOf(s.apiKey) }
    var allowNoKey by remember(s.allowNoKey) { mutableStateOf(s.allowNoKey) }
    var bindLan by remember(s.bindLan) { mutableStateOf(s.bindLan) }
    LoopCard(theme) {
        SectionTitle(theme, "网络端口")
        LoopTextField(theme, port, { port = it }, "端口")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, apiKey, { apiKey = it }, "API Key")
        Spacer(Modifier.height(8.dp))
        SwitchRow(theme, "允许无 Key 调用", "开启后任何客户端无需鉴权即可调用", allowNoKey) { allowNoKey = it }
        SwitchRow(theme, "绑定局域网", "开启后局域网内其它设备也可接入", bindLan) { bindLan = it }
        Spacer(Modifier.height(8.dp))
        RowButtons(
            {
                PrimaryButton(theme, "保存") {
                    val p = port.toIntOrNull()
                    if (p == null || p !in 1..65535) {
                        vm.notify("端口无效")
                        return@PrimaryButton
                    }
                    vm.saveSettings(
                        port = p,
                        apiKey = apiKey,
                        allowNoKey = allowNoKey,
                        bindLan = bindLan,
                        balanceRefreshSeconds = s.balanceRefreshSeconds,
                        maxConcurrentPerAccount = s.maxConcurrentPerAccount,
                        maxConcurrentGlobal = s.maxConcurrentGlobal,
                        failThreshold = s.failThreshold,
                        cooldownSeconds = s.cooldownSeconds,
                        modelCooldownSeconds = s.modelCooldownSeconds,
                        sessionSticky = s.sessionSticky,
                    )
                }
            },
            { GhostButton(theme, "重新生成 Key") { vm.generateApiKey() } },
        )
    }
}

@Composable
private fun ConcurrencySection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var perAccount by remember(s.maxConcurrentPerAccount) { mutableStateOf(s.maxConcurrentPerAccount.toString()) }
    var global by remember(s.maxConcurrentGlobal) { mutableStateOf(s.maxConcurrentGlobal.toString()) }
    var failThreshold by remember(s.failThreshold) { mutableStateOf(s.failThreshold.toString()) }
    var cooldown by remember(s.cooldownSeconds) { mutableStateOf(s.cooldownSeconds.toString()) }
    var modelCooldown by remember(s.modelCooldownSeconds) { mutableStateOf(s.modelCooldownSeconds.toString()) }
    var balanceInterval by remember(s.balanceRefreshSeconds) { mutableStateOf(s.balanceRefreshSeconds.toString()) }
    var sessionSticky by remember(s.sessionSticky) { mutableStateOf(s.sessionSticky) }
    var strategy by remember(s.accountStrategy) { mutableStateOf(s.accountStrategy) }
    LoopCard(theme) {
        SectionTitle(theme, "并发与容错")
        LoopTextField(theme, perAccount, { perAccount = it }, "单账号并发上限")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, global, { global = it }, "全网关并发上限（0 = 不限）")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, failThreshold, { failThreshold = it }, "连续失败几次后冷却")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, cooldown, { cooldown = it }, "冷却时长（秒）")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, modelCooldown, { modelCooldown = it }, "模型级冷却（秒）")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, balanceInterval, { balanceInterval = it }, "余额自动刷新间隔（秒）")
        Spacer(Modifier.height(8.dp))
        SwitchRow(theme, "会话粘黏", "同一会话固定走同一账号", sessionSticky) { sessionSticky = it }
        Spacer(Modifier.height(8.dp))
        Text("账号选择策略", color = theme.ink2, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ORDER", "ROTATE", "USAGE", "PACE").forEach { st ->
                GhostButton(theme, if (strategy == st) "$st ✓" else st) { strategy = st }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "保存") {
            val nums = listOf(perAccount, global, failThreshold, cooldown, modelCooldown, balanceInterval)
                .map { it.toIntOrNull() }
            if (nums.any { it == null || it < 0 }) {
                vm.notify("请填写有效的非负整数")
                return@PrimaryButton
            }
            vm.saveSettings(
                port = s.port,
                apiKey = s.apiKey,
                allowNoKey = s.allowNoKey,
                bindLan = s.bindLan,
                balanceRefreshSeconds = nums[5]!!,
                maxConcurrentPerAccount = nums[0]!!,
                maxConcurrentGlobal = nums[1]!!,
                failThreshold = nums[2]!!,
                cooldownSeconds = nums[3]!!,
                modelCooldownSeconds = nums[4]!!,
                sessionSticky = sessionSticky,
            )
            vm.setAccountStrategy(strategy)
        }
    }
}

@Composable
private fun ProxySection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    val p = s.proxy
    var enabled by remember(p.enabled) { mutableStateOf(p.enabled) }
    var type by remember(p.type) { mutableStateOf(p.type) }
    var host by remember(p.host) { mutableStateOf(p.host) }
    var port by remember(p.port) { mutableStateOf(p.port.toString()) }
    var user by remember(p.username) { mutableStateOf(p.username) }
    var pass by remember(p.password) { mutableStateOf(p.password) }
    var error by remember { mutableStateOf<String?>(null) }
    LoopCard(theme) {
        SectionTitle(theme, "代理")
        SwitchRow(theme, "启用代理", "国内域名永远直连，不走代理", enabled) { enabled = it }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(theme, if (type == ProxyType.HTTP) "HTTP ✓" else "HTTP") { type = ProxyType.HTTP }
            GhostButton(theme, if (type == ProxyType.SOCKS) "SOCKS5 ✓" else "SOCKS5") { type = ProxyType.SOCKS }
        }
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, host, { host = it }, "代理主机")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, port, { port = it }, "代理端口")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, user, { user = it }, "用户名（可选）")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, pass, { pass = it }, "密码（可选）")
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error!!, color = theme.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "保存代理") {
            error = vm.saveProxy(enabled, type, host, port, user, pass)
        }
    }
}

@Composable
private fun RewriteSection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var aliases by remember(s.modelAliases) {
        mutableStateOf(s.modelAliases.entries.joinToString("\n") { "${it.key}=${it.value}" })
    }
    var stripThink by remember(s.stripThinkTags) { mutableStateOf(s.stripThinkTags) }
    LoopCard(theme) {
        SectionTitle(theme, "请求改写")
        Text("模型别名：每行一条，格式 客户端模型名=真实模型名", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, aliases, { aliases = it }, "别名表", singleLine = false, minLines = 4)
        Spacer(Modifier.height(8.dp))
        SwitchRow(theme, "剥离 <think> 标签", "去掉 assistant 历史里的 think 标签", stripThink) { stripThink = it }
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "保存改写规则") { vm.setRewriteRules(aliases, stripThink) }
    }
}

@Composable
private fun SystemPromptSection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var prompt by remember(s.systemPrompt) { mutableStateOf(s.systemPrompt) }
    LoopCard(theme) {
        SectionTitle(theme, "系统提示词")
        Text("网关层统一注入：替换所有客户端的 system 消息。留空表示不注入。", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, prompt, { prompt = it }, "系统提示词", singleLine = false, minLines = 4)
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "保存") { vm.setSystemPrompt(prompt) }
    }
}

@Composable
private fun BubbleSection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    var opacity by remember(s.bubbleOpacity) { mutableStateOf(s.bubbleOpacity) }
    LoopCard(theme) {
        SectionTitle(theme, "悬浮球")
        SwitchRow(theme, "悬浮监测球", "显示在途请求与最近事件", s.monitorBubble) { vm.setMonitorBubble(it) }
        Spacer(Modifier.height(8.dp))
        Text("悬浮球透明度：${"%.2f".format(opacity)}", color = theme.ink2, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.3f, 0.6f, 0.8f, 1f).forEach { v ->
                GhostButton(theme, if (opacity == v) "$v ✓" else "$v") {
                    opacity = v
                    vm.setBubbleOpacity(v)
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    LoopCard(theme) {
        SectionTitle(theme, "外观主题")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            allThemes().forEach { t ->
                GhostButton(theme, if (s.themeName == t.name) "${t.name} ✓" else t.name) {
                    vm.setTheme(t.name)
                }
            }
        }
    }
}

@Composable
private fun FeedbackSection(vm: GateViewModel, theme: ThemePalette) {
    var content by remember { mutableStateOf("") }
    var contactType by remember { mutableStateOf("qq") }
    var contact by remember { mutableStateOf("") }
    var images by remember { mutableStateOf(listOf<String>()) }
    LoopCard(theme) {
        SectionTitle(theme, "反馈 / 工单")
        Text("反馈 QQ 群：1094669183。也可以在这里直接提交工单（最多 8 张截图）。", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, content, { content = it }, "问题描述（至少 5 个字）", singleLine = false, minLines = 4)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("qq" to "QQ", "phone" to "手机号", "email" to "邮箱").forEach { (v, label) ->
                GhostButton(theme, if (contactType == v) "$label ✓" else label) { contactType = v }
            }
        }
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, contact, { contact = it }, "联系方式")
        Spacer(Modifier.height(8.dp))
        RowButtons(
            {
                GhostButton(theme, "添加截图（${images.size}/8）") {
                    val picked = pickImages()
                    if (picked.isNotEmpty()) images = (images + picked).take(8)
                }
            },
            { if (images.isNotEmpty()) GhostButton(theme, "清除截图") { images = emptyList() } },
        )
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "提交工单") {
            if (content.trim().length < 5) {
                vm.notify("问题描述至少 5 个字")
                return@PrimaryButton
            }
            vm.submitTicket(content, contactType, contact, images)
            content = ""
            images = emptyList()
        }
    }
}

private fun pickImages(): List<String> {
    val chooser = JFileChooser().apply {
        dialogTitle = "选择截图（最多 8 张）"
        isMultiSelectionEnabled = true
        fileFilter = FileNameExtensionFilter("图片", "png", "jpg", "jpeg", "webp", "gif")
    }
    if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return emptyList()
    return chooser.selectedFiles.mapNotNull { file ->
        runCatching {
            val bytes = file.readBytes()
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/png"
            }
            "data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(bytes)
        }.getOrNull()
    }
}

@Composable
private fun UpdateSection(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    LoopCard(theme) {
        SectionTitle(theme, "检查更新")
        Text("当前版本：${vm.version}。更新来自 GitHub Releases（vantagate-desktop-v*）。", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        RowButtons(
            { PrimaryButton(theme, "检查更新") { vm.checkUpdate() } },
            {
                val info = s.updateInfo
                if (info != null) {
                    GhostButton(theme, "下载 ${info.version}") {
                        runCatching { Desktop.getDesktop().browse(URI(info.url)) }
                        vm.consumeUpdate()
                    }
                }
            },
            { if (s.updateInfo != null) GhostButton(theme, "忽略") { vm.consumeUpdate() } },
        )
        val info = s.updateInfo
        if (info != null && info.notes.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(info.notes.take(500), color = theme.ink2, fontSize = 12.sp)
        }
    }
}
