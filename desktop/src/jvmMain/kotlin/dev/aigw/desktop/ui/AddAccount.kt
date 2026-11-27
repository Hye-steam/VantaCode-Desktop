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
import dev.aigw.desktop.ProviderMeta

/**
 * 添加账号（Android AddAccountSheet 的桌面版）。
 *
 * 按供应商给登录入口：设备授权 / 微信扫码 / 网页登录（JCEF）/ 短信验证码 /
 * 粘贴凭证 / 自定义 / 免费池一键启用。网页登录统一走 LoginWindow。
 */
@Composable
fun AddAccountScreen(
    vm: GateViewModel,
    theme: ThemePalette,
    initialProviderId: String,
    onBack: () -> Unit,
) {
    val s = vm.state
    var providerId by remember { mutableStateOf(initialProviderId) }
    val providerIds = s.providerEnabled.keys.sorted()

    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row {
                GhostButton(theme, "← 返回") { onBack() }
            }
        }
        item {
            LoopCard(theme) {
                SectionTitle(theme, "选择供应商")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    providerIds.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { pid ->
                                GhostButton(
                                    theme,
                                    if (providerId == pid) "${ProviderMeta.displayName(pid)} ✓" else ProviderMeta.displayName(pid),
                                ) { providerId = pid }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "登录方式：${ProviderMeta.loginOptions(providerId).joinToString(" / ")}",
                    color = theme.ink2,
                    fontSize = 13.sp,
                )
            }
        }
        item {
            when (providerId) {
                "codebuddy" -> CodebuddyLogin(vm, theme)
                "trae" -> TraeLogin(vm, theme)
                "chatgpt" -> ChatgptLogin(vm, theme)
                "deepseek" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "DeepSeek")
                        RowButtons(
                            { PrimaryButton(theme, "网页登录") { vm.startDeepSeekLogin() } },
                        )
                        Spacer(Modifier.height(8.dp))
                        PasteCredential(vm, theme, "deepseek", "粘贴 Token 登录")
                    }
                }
                "loomy" -> SmsLogin(vm, theme, "loomy")
                "mimo" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "MiMo")
                        RowButtons(
                            { PrimaryButton(theme, "网页登录") { vm.startMimoLogin() } },
                        )
                        Spacer(Modifier.height(8.dp))
                        PasteCredential(vm, theme, "mimo", "粘贴 Cookie 登录")
                    }
                }
                "chatglm" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "智谱清言")
                        RowButtons(
                            { PrimaryButton(theme, "网页登录") { vm.startChatglmLogin() } },
                        )
                        Spacer(Modifier.height(8.dp))
                        PasteCredential(vm, theme, "chatglm", "粘贴 Cookie 登录")
                    }
                    SmsLogin(vm, theme, "chatglm")
                }
                "jimeng" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "即梦")
                        PrimaryButton(theme, "网页登录") { vm.startJimengLogin() }
                        Spacer(Modifier.height(8.dp))
                        Text("登录后自动抓取 sessionid Cookie 保存账号。", color = theme.ink2, fontSize = 13.sp)
                    }
                }
                "doubao" -> DoubaoLogin(vm, theme)
                "muse" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "muse.ai")
                        RowButtons(
                            { PrimaryButton(theme, "网页登录") { vm.startMuseLogin() } },
                        )
                        Spacer(Modifier.height(8.dp))
                        PasteCredential(vm, theme, "muse", "粘贴 Cookie（需含 hatch_sess）", onImport = { vm.addMuseAccount(it) })
                    }
                }
                "raccoon" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "小浣熊")
                        PrimaryButton(theme, "网页登录") { vm.startRaccoonLogin() }
                        Spacer(Modifier.height(8.dp))
                        PasteCallback(vm, theme)
                    }
                }
                "custom" -> CustomLogin(vm, theme)
                "freepool" -> {
                    LoopCard(theme) {
                        SectionTitle(theme, "免费池")
                        Text("无需登录，一键启用即可使用免费渠道。", color = theme.ink2, fontSize = 13.sp)
                        Spacer(Modifier.height(8.dp))
                        PrimaryButton(theme, "一键启用") { vm.addFreePoolAccount() }
                    }
                }
                else -> {
                    LoopCard(theme) {
                        SectionTitle(theme, ProviderMeta.displayName(providerId))
                        PasteCredential(vm, theme, providerId, "JSON 导入")
                    }
                }
            }
        }
    }
}

@Composable
private fun CodebuddyLogin(vm: GateViewModel, theme: ThemePalette) {
    val s = vm.state
    LoopCard(theme) {
        SectionTitle(theme, "WorkBuddy")
        RowButtons(
            { PrimaryButton(theme, "国内授权") { vm.startWorkbuddyLogin("cn") } },
            { PrimaryButton(theme, "国际授权") { vm.startWorkbuddyLogin("global") } },
            { PrimaryButton(theme, "微信扫码登录") { vm.startWorkbuddyLogin("cn", wechat = true) } },
        )
        if (s.wbLogin.url.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text("授权链接已生成：${s.wbLogin.url}", color = theme.ink2, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            RowButtons(
                { GhostButton(theme, "在系统浏览器中打开") { vm.openWorkbuddyLoginUrl() } },
                { GhostButton(theme, "复制链接") { vm.copyText(s.wbLogin.url, "授权链接") } },
                { GhostButton(theme, "取消") { vm.cancelWorkbuddyLogin() } },
            )
        }
        if (s.wbLogin.status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(s.wbLogin.status, color = theme.ink2, fontSize = 13.sp)
        }
        if (s.wbLogin.error != null) {
            Text(s.wbLogin.error!!, color = theme.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        PasteCredential(vm, theme, "codebuddy", "JSON 导入")
    }
}

@Composable
private fun TraeLogin(vm: GateViewModel, theme: ThemePalette) {
    LoopCard(theme) {
        SectionTitle(theme, "Trae")
        RowButtons(
            { PrimaryButton(theme, "国内授权") { vm.startTraeLogin("cn") } },
            { PrimaryButton(theme, "国际授权") { vm.startTraeLogin("global") } },
        )
        Spacer(Modifier.height(8.dp))
        Text("登录完成后上游会回跳本机回调地址，自动换出账号。", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        PasteCredential(vm, theme, "trae", "JSON 导入")
    }
}

@Composable
private fun ChatgptLogin(vm: GateViewModel, theme: ThemePalette) {
    LoopCard(theme) {
        SectionTitle(theme, "ChatGPT")
        RowButtons(
            { PrimaryButton(theme, "网页登录") { vm.startProviderLogin("chatgpt") } },
            { GhostButton(theme, "复制授权链接到外部浏览器") { vm.copyChatgptAuthUrl() } },
        )
        Spacer(Modifier.height(8.dp))
        Text("已在某个浏览器里登录 ChatGPT 时，用「复制授权链接」在那个浏览器里打开，会直接返回 code。", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        PasteCallback(vm, theme)
    }
}

@Composable
private fun PasteCallback(vm: GateViewModel, theme: ThemePalette) {
    var text by remember { mutableStateOf("") }
    LoopTextField(theme, text, { text = it }, "粘贴回调链接", singleLine = false, minLines = 2)
    Spacer(Modifier.height(8.dp))
    PrimaryButton(theme, "粘贴回调链接导入") { vm.pasteCallbackUrl(text) }
}

@Composable
private fun PasteCredential(
    vm: GateViewModel,
    theme: ThemePalette,
    providerId: String,
    label: String,
    onImport: ((String) -> Boolean)? = null,
) {
    var text by remember { mutableStateOf("") }
    LoopTextField(theme, text, { text = it }, label, singleLine = false, minLines = 3)
    Spacer(Modifier.height(8.dp))
    PrimaryButton(theme, "导入") {
        if (onImport != null) {
            onImport(text)
        } else {
            vm.importCredentials(providerId, text)
        }
    }
}

@Composable
private fun SmsLogin(vm: GateViewModel, theme: ThemePalette, providerId: String) {
    val s = vm.state
    val loginState = if (providerId == "loomy") s.loomyLogin else s.chatglmLogin
    var phone by remember { mutableStateOf(loginState.phone) }
    var ccode by remember { mutableStateOf(loginState.ccode) }
    var code by remember { mutableStateOf("") }
    LoopCard(theme) {
        SectionTitle(theme, if (providerId == "loomy") "Loomy 短信登录" else "智谱清言短信登录")
        LoopTextField(theme, phone, { phone = it }, "手机号")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, ccode, { ccode = it }, "国家码（中国大陆 86）")
        Spacer(Modifier.height(8.dp))
        RowButtons(
            {
                PrimaryButton(theme, "发送验证码") {
                    if (providerId == "loomy") vm.sendLoomyCode(phone, ccode) else vm.sendChatglmCode(phone, ccode)
                }
            },
        )
        if (loginState.status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(loginState.status, color = theme.ink2, fontSize = 13.sp)
        }
        if (loginState.error != null) {
            Text(loginState.error!!, color = theme.danger, fontSize = 13.sp)
        }
        if (loginState.active) {
            Spacer(Modifier.height(8.dp))
            LoopTextField(theme, code, { code = it }, "短信验证码")
            Spacer(Modifier.height(8.dp))
            PrimaryButton(theme, "登录") {
                if (providerId == "loomy") vm.loginLoomy(phone, code, ccode) else vm.loginChatglm(phone, code, ccode)
            }
        }
    }
}

@Composable
private fun DoubaoLogin(vm: GateViewModel, theme: ThemePalette) {
    var name by remember { mutableStateOf("") }
    var cookie by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var webId by remember { mutableStateOf("") }
    LoopCard(theme) {
        SectionTitle(theme, "豆包")
        PrimaryButton(theme, "网页登录（自动抓取）") { vm.startDoubaoLogin() }
        Spacer(Modifier.height(8.dp))
        Text("或手动粘贴（Cookie、deviceId、webId 必须同源）：", color = theme.ink2, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, name, { name = it }, "显示名（可选）")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, cookie, { cookie = it }, "Cookie", singleLine = false, minLines = 2)
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, deviceId, { deviceId = it }, "deviceId")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, webId, { webId = it }, "webId")
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "添加豆包账号") { vm.addDoubaoAccount(name, cookie, deviceId, webId) }
    }
}

@Composable
private fun CustomLogin(vm: GateViewModel, theme: ThemePalette) {
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    LoopCard(theme) {
        SectionTitle(theme, "添加自定义账号")
        LoopTextField(theme, name, { name = it }, "名称")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, baseUrl, { baseUrl = it }, "Base URL（如 https://api.openai.com/v1）")
        Spacer(Modifier.height(8.dp))
        LoopTextField(theme, key, { key = it }, "API Key")
        Spacer(Modifier.height(8.dp))
        PrimaryButton(theme, "添加") { vm.addCustomAccount(name, baseUrl, key) }
    }
}
