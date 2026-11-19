package dev.aigw.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.PlayCircleOutline
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 供应商账号详情页（全屏独立页，无底部导航）。
 *
 * 按设计稿复刻：顶栏（返回 + 标题 + 账号数徽章）+ 账号列表标题卡（添加胶囊）+
 * 空状态吉祥物 + 渐变账号卡（左滑露出删除）。
 */
@Composable
fun ProviderAccountsScreen(providerId: String, vm: AppViewModel, onBack: () -> Unit) {
    val kind = detailKindByName(providerId)
    val state = vm.state
    val accounts = state.accounts.filter { it.providerId == kind.providerId }
    // 额度存在池内各账号上，进页面自动拉一次，省得列表一直「未知」
    LaunchedEffect(kind.providerId) {
        accounts.forEach { vm.refreshAccountBalance(kind.providerId, it.uid) }
    }
    // 显示名可能随上游变化（用户改了昵称），进页面刷新一次
    LaunchedEffect(kind.providerId) {
        vm.refreshDisplayNames(kind.providerId)
    }
    val clipboard = LocalClipboardManager.current
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var exportAccount by remember { mutableStateOf<AccountUi?>(null) }
    var showAddCustom by remember { mutableStateOf(false) }
    // 当前滑开删除区的账号；点空白/其它卡片时置空复位
    var openSwipeUid by remember { mutableStateOf<String?>(null) }

    // 登录完成后账号会落盘，这里定时刷新列表，让新账号无需手动操作就出现
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshAll()
            delay(2000)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg)
            // 点空白处（未被卡片手势消费的点击）收起已滑开的删除区
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
            ) { openSwipeUid = null },
    ) {
        // 顶栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = LoopColors.subText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                kind.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            CountBadge(count = "${accounts.size}")
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // 简洁标题行：不套卡片，减少视觉噪音
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 2.dp, top = 2.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "账号",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoopColors.muted,
                )
                Spacer(Modifier.weight(1f))
                AddPill { showAdd = true }
            }

            if (accounts.isEmpty()) {
                // 空状态卡延伸到页面底部
                EmptyAccounts(kind, Modifier.weight(1f))
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    accounts.forEach { account ->
                        SwipeableAccountCard(
                            kind = kind,
                            account = account,
                            vm = vm,
                            isOpen = openSwipeUid == account.uid,
                            onOpenChanged = { open ->
                                openSwipeUid = if (open) account.uid else null
                            },
                            onDelete = { vm.removeAccount(account.providerId, account.uid) },
                            onExport = { exportAccount = account },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    Spacer(Modifier.height(120.dp))
                }
            }
        }
    }

    if (showAdd) {
        AddAccountSheet(
            kind = kind,
            vm = vm,
            onDismiss = { showAdd = false },
            onVisibilityChanged = { vm.setSheetOpen(it) },
            onImport = {
                showAdd = false
                showImport = true
            },
            onAddCustom = {
                showAdd = false
                showAddCustom = true
            },
        )
    }

    if (showAddCustom) {
        AddProviderSheet(
            onDismiss = { showAddCustom = false },
            onVisibilityChanged = { vm.setSheetOpen(it) },
            defaultUrl = if (kind.providerId == "doubao") "http://127.0.0.1:8765/v1" else "",
            defaultName = if (kind.providerId == "doubao") "豆包" else "",
            onSave = { name, baseUrl, key ->
                val ok = vm.addAccount(name, baseUrl, key, kind.providerId)
                if (ok) showAddCustom = false
                ok
            },
        )
    }

    if (showImport) {
        ImportCredentialsDialog(
            providerTitle = kind.title,
            // 小浣熊走「浏览器授权 → 复制地址栏」，粘的是回调链接不是 JSON
            callbackLink = kind == DetailKind.Raccoon,
            onDismiss = { showImport = false },
            onImport = { json, onDone -> vm.importCredentials(kind.providerId, json, onDone) },
            onImportBatch = { items, onProgress, onDone ->
                vm.importCredentialsBatch(kind.providerId, items, onProgress, onDone)
            },
            onImportEnvelope = { json, onDone -> vm.importAccountEnvelope(json, onDone) },
        )
    }

    exportAccount?.let { account ->
        AccountJsonDialog(
            account = account,
            minimalJson = account.minimalJson,
            onDismiss = { exportAccount = null },
            onCopy = { text ->
                clipboard.setText(AnnotatedString(text))
                vm.notify("账号 JSON 已复制")
            },
        )
    }
}

/** 添加胶囊按钮（add-pill）：渐变实底 + 投影。 */
@Composable
private fun AddPill(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .shadow(8.dp, RoundedCornerShape(50), spotColor = LoopColors.brand.copy(alpha = 0.35f))
            .clip(RoundedCornerShape(50))
            .background(Brush.linearGradient(listOf(LoopColors.brand, LoopColors.brandDeep)))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 11.dp),
    ) {
        androidx.compose.material3.Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp),
        )
        Text("添加", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** 空状态：卡片延伸到页面底部，吉祥物与文案下移居中偏上。 */
@Composable
private fun EmptyAccounts(kind: DetailKind, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1428505A))
            .clip(RoundedCornerShape(22.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(22.dp)),
    ) {
        Spacer(Modifier.weight(0.9f))
        EmptyMascot(size = 110.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        Text(
            "添加点什么吧……",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = LoopColors.faint,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1.3f))
    }
}
