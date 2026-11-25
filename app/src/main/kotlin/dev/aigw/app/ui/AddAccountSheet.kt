package dev.aigw.app.ui

import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.outlined.CloudDownload
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 添加账号弹层（对应设计稿 sheetAddAccount）。
 *
 * 供应商信息头 + 启用开关 + 大操作按钮；按钮内容按供应商实际能力变化。
 */
@Composable
internal fun AddAccountSheet(
    kind: DetailKind,
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onVisibilityChanged: (Boolean) -> Unit = {},
    onImport: () -> Unit,
    onAddCustom: () -> Unit = {},
) {
    var showDoubaoForm by remember { mutableStateOf(false) }
    var showCallbackPaste by remember { mutableStateOf(false) }
    var showMuseCookie by remember { mutableStateOf(false) }
    val enabled = vm.state.providerEnabled[kind.providerId] ?: true
    if (showDoubaoForm) {
        DoubaoAccountDialog(
            vm = vm,
            onDismiss = { showDoubaoForm = false },
            onFinished = {
                showDoubaoForm = false
                onDismiss()
            },
        )
        // 关键：这里必须提前返回。LoopSheet 内部并不判断 visible，
        // 它永远 fillMaxSize + clickable，会把表单上所有点击吃掉。
        return
    }
    if (showCallbackPaste) {
        CallbackPasteDialog(
            onDismiss = { showCallbackPaste = false },
            onSubmit = { raw ->
                val ok = vm.pasteCallbackUrl(raw)
                if (ok) {
                    showCallbackPaste = false
                    onDismiss()
                }
            },
        )
        return
    }
    if (showMuseCookie) {
        MuseCookieDialog(
            onDismiss = { showMuseCookie = false },
            onSubmit = { raw ->
                val ok = vm.addMuseAccount(raw)
                if (ok) {
                    showMuseCookie = false
                    onDismiss()
                }
            },
        )
        return
    }
    var showLoomyForm by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    var exportedJson by remember { mutableStateOf<String?>(null) }

    // 打开豆包表单时把这一层收起来：两个 LoopSheet 同时 visible 会叠出两条拖动手柄，
    // 下面那层还会被挡住。
    LoopSheet(
        visible = !showDoubaoForm,
        onDismiss = onDismiss,
        onVisibilityChanged = onVisibilityChanged,
    ) {
        LoopSheetHeader(
            icon = Icons.Outlined.Login,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "添加账号",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            // 供应商信息头
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(providerIconBg(kind)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(kind.logoRes),
                        contentDescription = kind.title,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        kind.tagline,
                        fontSize = 13.sp,
                        color = LoopColors.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            // 启用开关行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LoopColors.fieldBg)
                    .border(1.dp, LoopColors.line, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (enabled) "已开启" else "已关闭",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoopColors.text,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("关闭，将无法调用该供应商账号", fontSize = 12.sp, color = LoopColors.muted)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { vm.setProviderEnabled(kind.providerId, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = LoopColors.success,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = LoopColors.handleBar,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
            Spacer(Modifier.height(20.dp))

            // 调用区域选择（仅国内/国际双区域的供应商）
            if (kind == DetailKind.WorkBuddy || kind == DetailKind.Trae) {
                RegionPrefSelector(vm = vm, kind = kind)
                Spacer(Modifier.height(20.dp))
            }

            // 按供应商给出实际操作
            when (kind) {
                DetailKind.WorkBuddy -> {
                    OpButton("国内授权", Icons.Outlined.Public, primary = true) {
                        vm.startWorkbuddyLogin("cn")
                        onDismiss()
                    }
                    OpButton("国际授权", Icons.Outlined.Language) {
                        vm.startWorkbuddyLogin("global")
                        onDismiss()
                    }
                    // 直接走 keycloak 的 broker 授权地址，WebView 里就是扫码界面
                    OpButton("微信扫码登录", Icons.Outlined.Chat) {
                        vm.startWorkbuddyLogin("cn", wechat = true)
                        onDismiss()
                    }
                    OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                    OpButton("JSON 导入登录", Icons.Outlined.Code) {
                        onImport()
                    }
                }
                DetailKind.Muse -> {
                    // muse.ai 的生成只对页面开放，账号就是一份登录 cookie。
                    // 优先用网页登录（自动取 cookie），粘 Cookie 留给已有凭证的场景。
                    OpButton("网页登录", Icons.Outlined.Public, primary = true) {
                        vm.startMuseLogin()
                        onDismiss()
                    }
                    OpButton("粘贴 Cookie 登录", Icons.Outlined.ContentPaste) {
                        showMuseCookie = true
                    }
                    OpButton("JSON 导入登录", Icons.Outlined.Code) {
                        onImport()
                    }
                }
                DetailKind.Trae -> {
                    OpButton("国内授权", Icons.Outlined.Public, primary = true) {
                        vm.startTraeLogin("cn")
                        onDismiss()
                    }
                    OpButton("国际授权", Icons.Outlined.Language) {
                        vm.startTraeLogin("global")
                        onDismiss()
                    }
                    OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                    OpButton("JSON 导入登录", Icons.Outlined.Code) {
                        onImport()
                    }
                }
                DetailKind.DeepSeek -> {
                    OpButton("网页登录", Icons.Outlined.Login, primary = true) {
                        vm.startDeepSeekLogin()
                        onDismiss()
                    }
                    OpButton("粘贴 Token 登录", Icons.Outlined.Code) {
                        onImport()
                    }
                    OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                }
                DetailKind.Loomy -> {
                    if (showLoomyForm) {
                        SmsLoginPanel(
                            sending = vm.state.loomyLogin.sending,
                            loggingIn = vm.state.loomyLogin.loggingIn,
                            error = vm.state.loomyLogin.error,
                            resetCounter = vm.state.loomyLogin.resetCounter,
                            onSend = { phone, ccode -> vm.sendLoomyCode(phone, ccode) },
                            onLogin = { phone, code, ccode -> vm.loginLoomy(phone, code, ccode) },
                            onDone = onDismiss,
                        )
                    } else {
                        OpButton("短信验证码登录", Icons.Outlined.Sms, primary = true) {
                            showLoomyForm = true
                        }
                        OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                    }
                }
                DetailKind.Chatglm -> {
                    OpButton("网页登录", Icons.Outlined.Login, primary = true) {
                        vm.startChatglmLogin()
                        onDismiss()
                    }
                    OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                }
                DetailKind.Mimo -> {
                    // 小米账号登录后 cookie 里就带着 serviceToken，直接开网页登录
                    OpButton("网页登录", Icons.Outlined.Login, primary = true) {
                        onDismiss()
                        vm.startMimoLogin()
                    }
                    // 已经在浏览器登录过的，也可以粘贴 Cookie 入池
                    OpButton("粘贴 Cookie 登录", Icons.Outlined.Code) {
                        onImport()
                    }
                    OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                }
                DetailKind.Raccoon -> {
                    OpButton("网页登录", Icons.Outlined.Login, primary = true) {
                        vm.startRaccoonLogin()
                        onDismiss()
                    }
                    OpButton("粘贴回调链接导入", Icons.Outlined.Code) {
                        onImport()
                    }
                    OpButton("刷新余额", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                }
                DetailKind.Relay -> {
                    // 与供应商列表页右下角 + 球的弹层一致：名称 / 接口 / 密钥
                    OpButton("添加自定义账号", Icons.Outlined.Link, primary = true) {
                        onAddCustom()
                    }
                }
                DetailKind.Doubao -> {
                    OpButton("添加豆包账号", Icons.Outlined.Link, primary = true) {
                        showDoubaoForm = true
                    }
                }
                DetailKind.Chatgpt -> {
                    // 走 OAuth 回调登录（PKCE），复用 Trae 那套 WebLoginActivity + 本地回调
                    OpButton("登录 ChatGPT", Icons.Outlined.Login, primary = true) {
                        vm.startProviderLogin("chatgpt")
                        onDismiss()
                    }
                    // 浏览器里已登录 ChatGPT 时，WebView 的 Cookie 是隔离的，用不了。
                    // 但把带 PKCE 的授权链接丢进那个浏览器，它会直接给出 code。
                    OpButton("复制授权链接", Icons.Outlined.ContentCopy) {
                        vm.copyChatgptAuthUrl()
                        onDismiss()
                    }
                    // 系统浏览器里完成授权后，跳转的 localhost 地址打不开，
                    // 但地址栏里的 code 是有效的：粘回来就能换 token
                    OpButton("粘贴回调地址", Icons.Outlined.ContentPaste) {
                        showCallbackPaste = true
                    }
                    OpButton("刷新用量", Icons.Outlined.Refresh) { vm.refreshCredits(kind.providerId) }
                }
                DetailKind.Jimeng -> {
                    // 即梦和豆包一样靠 Cookie，但它的签名算法是固定的，不需要页面里
                    // 补设备标识，所以登录成功后直接就能用，不用再弹二次表单。
                    val jctx = androidx.compose.ui.platform.LocalContext.current
                    OpButton("一键登录即梦", Icons.Outlined.Login, primary = true) {
                        val host = jctx.findActivity()
                        val intent = android.content.Intent(host ?: jctx, JimengLoginActivity::class.java)
                        if (host == null) {
                            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        val err = runCatching { (host ?: jctx).startActivity(intent) }.exceptionOrNull()
                        android.widget.Toast.makeText(
                            jctx,
                            if (err == null) "正在打开即梦登录页…" else "无法打开登录页：${err.message}",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                DetailKind.FreePool -> {
                    OpButton("一键启用免费池", Icons.Outlined.CloudDownload, primary = true) {
                        vm.addFreePoolAccount()
                        onDismiss()
                    }
                }
                else -> {}
            }


            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = LoopColors.line)
            Spacer(Modifier.height(14.dp))

            // 账号迁移：导出/导入这一家的全部账号（信封格式，含凭证与配置）
            Text(
                "账号迁移",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LoopColors.text,
            )
            Spacer(Modifier.height(8.dp))
            OpButton("一键导出本家账号", Icons.Outlined.FileDownload) {
                vm.exportAccountEnvelope(kind.providerId) { json, err ->
                    if (json != null) {
                        clipboard.setText(AnnotatedString(json))
                        exportedJson = json
                    } else {
                        vm.notify(err ?: "导出失败")
                    }
                }
            }
            OpButton("一键导入账号", Icons.Outlined.FileUpload) {
                onImport()
            }
        }
    }

    // 导出结果：直接展示信封内容，方便用户复制或另存
    exportedJson?.let { json ->
        AlertDialog(
            onDismissRequest = { exportedJson = null },
            containerColor = LoopColors.surface,
            title = { Text("已导出账号文件") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "内容已复制到剪贴板，可直接粘贴到另一台设备的「一键导入账号」里。",
                        fontSize = 12.sp,
                        color = LoopColors.muted,
                    )
                    OutlinedTextField(
                        value = json,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 6,
                        maxLines = 12,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = LoopColors.text,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { exportedJson = null }) {
                    Text("完成", color = LoopColors.brand)
                }
            },
        )
    }
}

/**
 * 调用区域选择（国内/国际双区域供应商）：三选一的分段按钮。
 * 只影响不带区域前缀的调用（`codebuddy/x`）；带 -cn/-global 前缀的调用始终精确。
 */
@Composable
private fun RegionPrefSelector(vm: AppViewModel, kind: DetailKind) {
    val current = vm.state.regionPrefs[kind.providerId]
    val options = listOf(
        RegionOption(null, "自由调用", "国内外账号都可路由"),
        RegionOption("cn", "仅国内", "只用国内账号"),
        RegionOption("global", "仅国际", "只用国际账号"),
    )
    Column {
        Text("调用区域", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
        Spacer(Modifier.height(4.dp))
        Text(
            "带 -cn/-global 前缀的模型始终精确路由，不受此影响",
            fontSize = 11.sp,
            color = LoopColors.muted,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(LoopColors.fieldBg)
                .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                val selected = current == option.value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) LoopColors.brandSoft else Color.Transparent)
                        .clickable { vm.setRegionPref(kind.providerId, option.value) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) LoopColors.brand else LoopColors.muted,
                    )
                }
            }
        }
    }
}

private data class RegionOption(val value: String?, val label: String, val desc: String)

/** 大操作按钮（op-btn）：整行、居中、图标 + 文案。 */
@Composable
private fun ColumnScope.OpButton(
    text: String,
    icon: ImageVector,
    primary: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) LoopColors.brandSoft else LoopColors.fieldBg)
            .border(
                1.dp,
                if (primary) Color.Transparent else LoopColors.line,
                RoundedCornerShape(18.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 17.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) LoopColors.brand else LoopColors.text,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (primary) LoopColors.brand else LoopColors.text,
        )
    }
}

/** 短信登录面板（Loomy / 智谱清言共用）：国家码 + 手机号 + 验证码。 */
@Composable
private fun SmsLoginPanel(
    sending: Boolean,
    loggingIn: Boolean,
    error: String?,
    resetCounter: Int,
    onSend: (String, String) -> Unit,
    onLogin: (String, String, String) -> Unit,
    onDone: () -> Unit,
) {
    var ccode by remember { mutableStateOf("86") }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(countdown) {
        if (countdown > 0) {
            kotlinx.coroutines.delay(1_000)
            countdown -= 1
        }
    }
    androidx.compose.runtime.LaunchedEffect(resetCounter) {
        if (resetCounter > 0) {
            phone = ""
            code = ""
            countdown = 0
            onDone()
        }
    }

    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LoopFormInput(
                value = ccode,
                onValueChange = { v -> ccode = v.filter(Char::isDigit).take(4) },
                placeholder = "国家码",
                modifier = Modifier.width(96.dp),
            )
            LoopFormInput(
                value = phone,
                onValueChange = { v -> phone = v.filter(Char::isDigit).take(15) },
                placeholder = "手机号",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
        LoopFormInput(value = code, onValueChange = { code = it }, placeholder = "验证码")
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LoopSaveButton(
                text = if (countdown > 0) "${countdown} 秒后重发" else "发送验证码",
                enabled = countdown == 0,
                icon = Icons.Outlined.Sms,
                modifier = Modifier.weight(1f),
            ) {
                countdown = 60
                onSend(phone, ccode)
            }
            LoopSaveButton(
                text = "登录",
                icon = Icons.Outlined.CheckCircle,
                modifier = Modifier.weight(1f),
            ) { onLogin(phone, code, ccode) }
        }
        if (sending || loggingIn) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (sending) "发送中…" else "登录中…",
                fontSize = 12.sp,
                color = LoopColors.muted,
            )
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 12.sp, color = LoopColors.danger)
        }
    }
}

// ------------------------------ 豆包账号表单 ------------------------------

/**
 * 豆包走 PC 客户端协议，需要 Cookie 和与之同源的 device_id / web_id。
 * 三个值缺一个都会被上游判成异常设备，所以分开填、逐个校验。
 */
@Composable
internal fun DoubaoAccountDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onFinished: () -> Unit = onDismiss,
) {
    var name by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var webId by remember { mutableStateOf("") }
    val ready = cookie.isNotBlank() && deviceId.isNotBlank() && webId.isNotBlank()

    LoopSheet(
        visible = true,
        onDismiss = onDismiss,
        onVisibilityChanged = { vm.setSheetOpen(it) },
    ) {
        LoopSheetHeader(
            icon = Icons.Outlined.Link,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "添加豆包账号",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            // 主路径：一键登录。手动填 Cookie 在手机上基本没法用（要开开发者工具），
            // 只作为兜底保留。
            val ctx = androidx.compose.ui.platform.LocalContext.current
            OpButton("一键登录豆包", Icons.Outlined.Login, primary = true) {
                // LocalContext 在弹层里拿到的可能是 Application context，
                // 用它启动 Activity 必须带 NEW_TASK，否则直接抛异常（且容易被静默吞掉）
                val host = ctx.findActivity()
                val intent = android.content.Intent(host ?: ctx, DoubaoLoginActivity::class.java)
                if (host == null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val err = runCatching { (host ?: ctx).startActivity(intent) }.exceptionOrNull()
                if (err != null) {
                    android.util.Log.w("DoubaoLogin", "启动登录页失败：${err.message}")
                    android.widget.Toast.makeText(
                        ctx,
                        "无法打开登录页：${err.message}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                } else {
                    // 不主动关闭弹层：关弹层会触发重组，把刚启动的登录页顶掉。
                    // 用户从登录页返回后弹层还在，关掉即可（账号已经保存好了）。
                    android.widget.Toast.makeText(
                        ctx, "正在打开豆包登录页…", android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "登录成功后会自动保存账号，不需要手动复制任何东西。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = LoopColors.line, thickness = 1.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                "也可以手动填写（需要从开发者工具取，必须来自同一会话）",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(12.dp))
            DoubaoField("备注名（可选）", name, 1) { name = it }
            Spacer(Modifier.height(10.dp))
            DoubaoField("Cookie", cookie, 4) { cookie = it }
            Spacer(Modifier.height(10.dp))
            DoubaoField("device_id", deviceId, 1) { deviceId = it }
            Spacer(Modifier.height(10.dp))
            DoubaoField("web_id", webId, 1) { webId = it }
            Spacer(Modifier.height(18.dp))
            LoopSaveButton(text = "保存", icon = Icons.Outlined.Check, enabled = ready) {
                if (vm.addDoubaoAccount(name.ifBlank { "豆包账号" }, cookie, deviceId, webId)) {
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun MuseCookieDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    LoopSheet(visible = true, onDismiss = onDismiss) {
        LoopSheetHeader(
            icon = Icons.Outlined.ContentPaste,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "muse.ai 登录",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                "在浏览器登录 muse.ai 后，把整串 Cookie 粘进来（必须含 hatch_sess）。" +
                    "开发者工具 → Network → 任意请求 → Request Headers → Cookie。",
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(14.dp))
            DoubaoField("Cookie", text, lines = 4) { text = it }
            Spacer(Modifier.height(16.dp))
            OpButton("确认", Icons.Outlined.Check, primary = true) {
                onSubmit(text.trim())
            }
        }
    }
}

@Composable
private fun CallbackPasteDialog(
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    LoopSheet(visible = true, onDismiss = onDismiss) {
        LoopSheetHeader(
            icon = Icons.Outlined.ContentPaste,
            iconBg = LoopColors.accentGreenSoft,
            iconTint = LoopColors.brand,
            title = "粘贴回调地址",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                "在浏览器里完成授权后，地址栏会跳到 http://localhost:1455/... 并显示打不开。" +
                    "把那个完整地址（或只把 code= 后面那串）复制到这里。",
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(14.dp))
            DoubaoField("回调地址或 code", text, lines = 3) { text = it }
            Spacer(Modifier.height(16.dp))
            OpButton("确认", Icons.Outlined.Check, primary = true) {
                onSubmit(text.trim())
            }
        }
    }
}

@Composable
private fun DoubaoField(
    label: String,
    value: String,
    lines: Int,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = LoopColors.muted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (lines > 1) 84.dp else 44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LoopColors.fieldBg)
                .border(1.dp, LoopColors.line, RoundedCornerShape(14.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    color = LoopColors.text,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(LoopColors.brand),
                maxLines = if (lines > 1) 8 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 从 Compose 的 Context 里取出宿主 Activity；取不到返回 null。 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
