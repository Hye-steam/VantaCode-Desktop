package dev.aigw.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import dev.aigw.provider.loomy.LoomyProvider
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// 详情页使用的供应商元数据（与列表页一致）
internal enum class DetailKind(
    val title: String,
    val tagline: String,
    val providerId: String,
    val logoRes: Int,
) {
    WorkBuddy("WorkBuddy", "腾讯 AI 编程助手", "codebuddy", dev.aigw.app.R.drawable.ic_provider_workbuddy),
    Trae("Trae", "字节 AI IDE", "trae", dev.aigw.app.R.drawable.ic_provider_trae),
    DeepSeek("DeepSeek", "深度思考模式", "deepseek", dev.aigw.app.R.drawable.ic_provider_deepseek),
    Loomy("Loomy", "讯飞免费积分通道", "loomy", dev.aigw.app.R.drawable.ic_provider_loomy),
    Raccoon("小浣熊", "商汤网页登录，积分额度", "raccoon", dev.aigw.app.R.drawable.ic_provider_raccoon),
    Mimo("MiMo", "小米 AI Studio", "mimo", dev.aigw.app.R.drawable.ic_provider_mimo),
    Chatglm("智谱清言", "GLM 系列模型", "chatglm", dev.aigw.app.R.drawable.ic_provider_chatglm),
    FreePool("免费池", "免登录公共通道", "freepool", dev.aigw.app.R.drawable.ic_provider_freepool),
    Doubao("豆包", "Cookie 直连 · 工作模式", "doubao", dev.aigw.app.R.drawable.ic_provider_doubao),
    Relay("自定义供应商", "聚合接入", "custom", dev.aigw.app.R.drawable.ic_provider_relay),
    ;

    val supportsActivity: Boolean get() = this == WorkBuddy || this == Trae || this == Chatglm || this == Raccoon
    val supportsImport: Boolean get() =
        this == WorkBuddy || this == Trae || this == Mimo || this == Raccoon
}

internal fun detailKindByName(name: String): DetailKind =
    DetailKind.entries.find { it.name == name } ?: DetailKind.WorkBuddy

/** 供应商图标背景（设计稿浅色渐变近似）。 */
internal fun providerIconBg(kind: DetailKind): Color = when (kind) {
    DetailKind.WorkBuddy -> LoopColors.accentGreenSoft
    DetailKind.Trae -> LoopColors.accentCream
    DetailKind.DeepSeek -> LoopColors.accentBlueSoft
    DetailKind.Loomy -> LoopColors.accentGreenSoft
    DetailKind.Mimo -> LoopColors.accentOrangeSoft
    DetailKind.Raccoon -> LoopColors.accentBlueSoft
    DetailKind.Chatglm -> LoopColors.accentBlueSoft
    DetailKind.Doubao -> LoopColors.accentBlueSoft
    DetailKind.FreePool -> LoopColors.accentGreenSoft
    DetailKind.Relay -> LoopColors.brandSoft
    // 官方图标自带蓝紫底，再套一层底色会露出一圈边
}

/**
 * 供应商账号详情页的公共元数据与样式（页面主体在 ProviderDetailScreen.kt）。
 */

/** 详情页操作按钮：默认浅底，filled 为渐变实底。 */
@Composable
internal fun LoopActionButton(
    text: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (filled) androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(LoopColors.brand, LoopColors.brandDeep),
                ) else androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(LoopColors.brandSoft, LoopColors.brandSoft),
                ),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (filled) Color.White else LoopColors.brand,
        )
    }
}

internal fun balanceText(account: AccountUi): String {
    val balance = account.balance ?: return "未知"
    if (account.enterprise && balance >= 1099511627776.0) return "不限量"
    return "%,.0f".format(balance)
}

private val DETAIL_CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun detailFormatClock(ms: Long): String =
    DETAIL_CLOCK.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/** 状态徽章：正常/掉线/禁言/冷却中。 */
// ---------------------------------------------------------------------- 对话框

@Composable
internal fun WorkBuddyLoginDialog(login: WbLoginState, vm: AppViewModel) {
    if (!login.active && login.status.isEmpty() && login.error == null) return

    AlertDialog(
        onDismissRequest = { vm.cancelWorkbuddyLogin() },
        containerColor = LoopColors.surface,
        title = {
            Text(
                "登录 WorkBuddy（${if (login.region == "global") "国际版" else "国内版"}）",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (login.active) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = LoopColors.brand)
                        Spacer(Modifier.width(8.dp))
                        Text(login.status, fontSize = 13.sp, color = LoopColors.muted)
                    }
                } else {
                    Text(login.status, fontSize = 13.sp, color = LoopColors.muted)
                }
                login.error?.let {
                    Text(it, fontSize = 12.sp, color = LoopColors.danger)
                    if (it.contains("resolve host", ignoreCase = true)) {
                        Text(
                            "网络解析失败：请检查手机网络，或关闭 VPN/代理后重试。",
                            fontSize = 12.sp,
                            color = LoopColors.muted,
                        )
                    }
                }
                if (!login.active && login.error != null) {
                    Text(
                        "提示：国际版授权用海外站点（X/Google），若 VPN 代理不稳定，\n可用「浏览器打开」交给系统浏览器处理，登录后网关自动收凭证。",
                        fontSize = 12.sp,
                        color = LoopColors.muted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { vm.startWorkbuddyLogin(login.region) }) {
                            Text("重试", fontSize = 13.sp, color = LoopColors.brand)
                        }
                        if (login.url.isNotBlank()) {
                            TextButton(onClick = { vm.openWorkbuddyLoginUrl() }) {
                                Text("浏览器打开", fontSize = 13.sp, color = LoopColors.brand)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.cancelWorkbuddyLogin() }) {
                Text(if (login.active) "取消" else "关闭", color = LoopColors.brand)
            }
        },
    )
}

@Composable
internal fun ImportCredentialsDialog(
    providerTitle: String,
    /** 小浣熊这类走「浏览器授权 → 复制地址栏」的供应商，要的是链接而不是 JSON。 */
    callbackLink: Boolean = false,
    onDismiss: () -> Unit,
    onImport: (String, (String?) -> Unit) -> Unit,
    onImportBatch: (List<String>, (Int, Int, String?) -> Unit, (Int, Int, List<String>) -> Unit) -> Unit,
    onImportEnvelope: (String, (Int, Int, String?) -> Unit) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    // 回调登录（小浣熊这类）的正确姿势是「浏览器授权 → 复制地址栏 → 回来粘贴」，
    // 弹窗打开时如果剪贴板里已经是回调链接就直接填好，省一步。
    var raw by remember {
        mutableStateOf(
            clipboard.getText()?.text?.trim()?.let { text ->
                text.takeIf {
                    it.startsWith("http") && it.contains("authorization_code")
                }
            }.orEmpty(),
        )
    }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            }.orEmpty()
        }.onFailure { error = "读取文件失败：${it.message}" }.getOrDefault("")
        if (text.isNotBlank()) raw = text
    }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        containerColor = LoopColors.surface,
        title = { Text(if (callbackLink) "粘贴 $providerTitle 回调链接" else "粘贴 $providerTitle 凭证导入") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (callbackLink) {
                        "在浏览器里完成授权后，把地址栏整条链接复制过来（形如 " +
                            "http://127.0.0.1:51121/raccoon/callback?authorization_code=xxx）。" +
                            "弹窗打开时会自动从剪贴板填入。"
                    } else {
                        "粘贴凭证 JSON / token，或 JSON 数组批量导入："
                    },
                    fontSize = 12.sp,
                    color = LoopColors.muted,
                )
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text(if (callbackLink) "回调链接" else "凭证 JSON") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                    // 显式给文字色：Material 默认取 onSurface，深色主题下会和底色撞
                    textStyle = androidx.compose.ui.text.TextStyle(color = LoopColors.text),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !importing,
                        onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isBlank()) {
                                error = if (callbackLink) {
                                    "剪贴板是空的，先在浏览器里复制授权后的地址栏链接"
                                } else {
                                    "剪贴板是空的，先在别处复制账号文件"
                                }
                            } else {
                                raw = text
                                error = null
                            }
                        },
                    ) {
                        Text("从剪贴板粘贴", color = LoopColors.brand, fontSize = 13.sp)
                    }
                    if (!callbackLink) TextButton(
                        enabled = !importing,
                        onClick = { filePicker.launch("application/json") },
                    ) {
                        Text("选择 JSON 文件", color = LoopColors.brand, fontSize = 13.sp)
                    }
                }
                if (progressText.isNotEmpty()) {
                    Text(progressText, fontSize = 12.sp, color = LoopColors.brand)
                }
                error?.let {
                    Text(it, color = LoopColors.danger, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !importing,
                onClick = {
                    if (raw.isBlank()) {
                        error = "请粘贴凭证 JSON 或选择文件"
                        return@TextButton
                    }
                    // 账号迁移信封（含旧版单账号导出）优先：直接落池，不联网校验
                    if (looksLikeEnvelope(raw.trim())) {
                        importing = true
                        error = null
                        progressText = ""
                        onImportEnvelope(raw.trim()) { ok, failed, err ->
                            importing = false
                            progressText = ""
                            if (failed == 0 && err == null) {
                                onDismiss()
                            } else {
                                error = "导入完成：成功 $ok" + (err?.let { "；$it" } ?: "")
                            }
                        }
                        return@TextButton
                    }
                    val items = parseBatchInput(raw.trim())
                    if (items.size > 1) {
                        importing = true
                        error = null
                        progressText = ""
                        onImportBatch(
                            items,
                            { done, total, lastError ->
                                progressText = "$done/$total" + (lastError?.let { "（最近失败：$it）" } ?: "")
                            },
                            { ok, failed, errors ->
                                importing = false
                                if (failed == 0) {
                                    onDismiss()
                                } else {
                                    progressText = ""
                                    error = "导入完成：成功 $ok，失败 $failed\n" + errors.joinToString("\n").take(400)
                                }
                            },
                        )
                    } else {
                        importing = true
                        error = null
                        progressText = ""
                        onImport(items.firstOrNull() ?: raw.trim()) { err ->
                            importing = false
                            if (err == null) onDismiss() else error = err
                        }
                    }
                },
            ) { Text(if (importing) "导入中…" else "导入", color = LoopColors.brand) }
        },
        dismissButton = { TextButton(enabled = !importing, onClick = onDismiss) { Text("取消", color = LoopColors.muted) } },
    )
}

/**
 * 是否账号迁移信封：带 `format` 标记（新版）或旧版单账号导出（providerId+uid+secret 全套）。
 * 注意：WorkBuddy 裸凭证也有 uid 字段，但不会同时有 providerId；这里要求三个键齐全。
 */
private fun looksLikeEnvelope(raw: String): Boolean {
    val obj = runCatching { com.google.gson.JsonParser.parseString(raw).asJsonObject }.getOrNull()
        ?: return false
    if (obj.has("format") && obj.has("accounts")) return true
    return obj.has("providerId") && obj.has("uid") && obj.has("secret")
}

/**
 * 把粘贴内容拆成要导入的条目：JSON 数组逐项拆开；包装对象（含 accounts）也拆开；
 * 单条凭证/裸 token 作为一项。
 */
private fun parseBatchInput(raw: String): List<String> {
    val trimmed = raw.trim()
    val root = runCatching { com.google.gson.JsonParser.parseString(trimmed) }.getOrNull()
        ?: return listOf(trimmed)
    if (root.isJsonArray) {
        val out = mutableListOf<String>()
        for (element in root.asJsonArray) {
            val item = if (element.isJsonPrimitive) element.asString.trim() else element.toString().trim()
            if (item.isNotEmpty()) out.add(item)
        }
        if (out.isNotEmpty()) return out
    }
    if (root.isJsonObject) {
        val obj = root.asJsonObject
        val accounts = obj.get("accounts")
        if (accounts != null && accounts.isJsonArray) {
            val out = mutableListOf<String>()
            for (element in accounts.asJsonArray) {
                val item = if (element.isJsonPrimitive) element.asString.trim() else element.toString().trim()
                if (item.isNotEmpty()) out.add(item)
            }
            if (out.isNotEmpty()) return out
        }
    }
    return listOf(trimmed)
}

@Composable
internal fun AccountJsonDialog(
    account: AccountUi,
    minimalJson: String,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LoopColors.surface,
        title = { Text("账号 JSON 数据") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = minimalJson,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("${account.name} 的最小凭证") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = LoopColors.text,
                    ),
                )
                Text(
                    "最小凭证（access_token / refresh_token），本应用与 com.wb.api 等第三方工具都可直接导入",
                    fontSize = 11.sp,
                    color = LoopColors.faint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onCopy(minimalJson)
                onDismiss()
            }) { Text("复制", color = LoopColors.brand) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭", color = LoopColors.muted) } },
    )
}
