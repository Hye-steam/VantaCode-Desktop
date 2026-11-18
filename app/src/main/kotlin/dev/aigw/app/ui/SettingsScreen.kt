package dev.aigw.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radar

import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aigw.gateway.ProxyType
import dev.aigw.gateway.RemoteAdminClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import dev.aigw.app.update.UpdateChecker

/**
 * 设置页（设计稿：主页 + 子页导航）。
 *
 * 主页：外观配置（主题色环）+ 调度中心（网络/并发/代理）+ 其他（桌面宠物/问题反馈）。
 */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var showBall by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }

    BackHandler(enabled = sub != null) { sub = null }

    when (sub) {
        null -> SettingsMain(vm, onOpen = { sub = it }, onBall = { showBall = true }, onPrompt = { showPrompt = true })
        "net" -> NetSubPage(vm, onBack = { sub = null })
        "conc" -> ConcSubPage(vm, onBack = { sub = null })
        "proxy" -> ProxySubPage(vm, onBack = { sub = null })
        "feedback" -> FeedbackSubPage(vm, onBack = { sub = null })
    }

    if (showBall) {
        BallSheet(vm, onDismiss = { showBall = false })
    }
    if (showPrompt) {
        SystemPromptSheet(vm, onDismiss = { showPrompt = false })
    }
}

// ------------------------------ 通用 ------------------------------

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = LoopColors.subText,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
    }
}

@Composable
private fun LoopSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = LoopColors.brand,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = LoopColors.handleBar,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** 设置行：标题 + 描述 + 右侧控件。 */
@Composable
private fun SetRow(
    title: String,
    desc: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
            Spacer(Modifier.height(3.dp))
            Text(desc, fontSize = 12.5.sp, color = LoopColors.muted, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** 分组容器：把同一组设置收进一张卡，靠分隔线分项，而不是一卡一项。 */
@Composable
private fun SetGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(18.dp)),
        content = content,
    )
}

/** 组内分隔线：左侧留白对齐文字起点。 */
@Composable
private fun GroupDivider() {
    HorizontalDivider(
        color = LoopColors.line,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 20.dp),
    )
}

/** 组内设置行：无卡片外框，由 [SetGroup] 统一承载。 */
@Composable
private fun CompactSetRow(
    title: String,
    desc: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = LoopColors.text)
            Spacer(Modifier.height(3.dp))
            Text(desc, fontSize = 12.5.sp, color = LoopColors.muted, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** 步进器：− 数值 +。 */
@Composable
private fun Stepper(
    value: Int,
    display: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    var pop by remember(value) { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (pop) {
            pop = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pop) 1.25f else 1f,
        animationSpec = tween(220),
        label = "stPop",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LoopColors.fieldBg)
            .border(1.dp, LoopColors.line, RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LoopColors.surface)
                .border(1.dp, LoopColors.line, CircleShape)
                .clickable { onMinus(); pop = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("−", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = LoopColors.brand)
        }
        Box(
            Modifier.width(76.dp).height(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                display,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LoopColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayerCompat(scale),
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LoopColors.surface)
                .border(1.dp, LoopColors.line, CircleShape)
                .clickable { onPlus(); pop = true },
            contentAlignment = Alignment.Center,
        ) {
            Text("+", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = LoopColors.brand)
        }
    }
}

private fun Modifier.graphicsLayerCompat(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

/** 循环选择行：点击在选项间循环。 */
@Composable
private fun SelectorRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LoopColors.muted)
            Spacer(Modifier.height(5.dp))
            Text(value, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = LoopColors.text)
        }
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = LoopColors.faint,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ------------------------------ 主页 ------------------------------

@Composable
private fun SettingsMain(
    vm: AppViewModel,
    onOpen: (String) -> Unit,
    onBall: () -> Unit,
    onPrompt: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "设置",
            fontSize = 25.sp,
            fontWeight = FontWeight.SemiBold,
            color = LoopColors.text,
            modifier = Modifier.padding(start = 4.dp),
        )
        Spacer(Modifier.height(24.dp))

        // ---- 外观配置 ----
        Text(
            "外观配置",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = LoopColors.muted,
        )
        Spacer(Modifier.height(12.dp))
        ThemeCard(vm)
        Spacer(Modifier.height(28.dp))

        // ---- 调度中心 ----
        Text(
            "调度中心",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = LoopColors.muted,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1428505A))
                .clip(RoundedCornerShape(22.dp))
                .background(LoopColors.surface)
                .border(1.dp, LoopColors.line, RoundedCornerShape(22.dp)),
        ) {
            MenuRow(Icons.Outlined.Public, "网络端口") { onOpen("net") }
            MenuRow(Icons.Outlined.Bolt, "并发与容错") { onOpen("conc") }
            MenuRow(Icons.Outlined.Link, "代理") { onOpen("proxy") }
        }
        Spacer(Modifier.height(28.dp))

        // ---- 其他 ----
        Text(
            "其他",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = LoopColors.muted,
        )
        Spacer(Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1428505A))
                .clip(RoundedCornerShape(22.dp))
                .background(LoopColors.surface)
                .border(1.dp, LoopColors.line, RoundedCornerShape(22.dp)),
        ) {
            MenuRow(Icons.Outlined.Pets, "桌面宠物", onClick = onBall)
            MenuRow(Icons.Outlined.EditNote, "系统提示词", onClick = onPrompt)
            MenuRow(Icons.Outlined.Mail, "QQ群 1094669183") { onOpen("feedback") }
        }
        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun MenuRow(icon: ImageVector, name: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(LoopColors.brandSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = LoopColors.brand, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            name,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = LoopColors.text,
            modifier = Modifier.weight(1f),
        )
        ArrowBadge()
    }
}

// ------------------------------ 外观配置 ------------------------------


/** 旧主题名映射到新的深色主题，避免老设置下选择器没有高亮项。 */
private fun normalizeTheme(name: String): String = when (name) {
    "暗夜蓝" -> "深海"
    "薄荷紫" -> "靛夜"
    "日落橙" -> "炭焰"
    "樱花粉" -> "炭焰"
    "默认" -> "石墨"
    else -> name
}

private data class ThemeSwatch(
    val key: String,
    val label: String,
    val color: Color,
    val circleBg: Color = LoopColors.bg,
)

private val THEME_SWATCHES = listOf(
    ThemeSwatch("石墨", "石墨", Color(0xFF63636B), circleBg = Color(0xFF09090B)),
    ThemeSwatch("深海", "深海", Color(0xFF2563EB), circleBg = Color(0xFF05080F)),
    ThemeSwatch("靛夜", "靛夜", Color(0xFF4F46E5), circleBg = Color(0xFF08070F)),
    ThemeSwatch("墨林", "墨林", Color(0xFF047857), circleBg = Color(0xFF05100B)),
    ThemeSwatch("炭焰", "炭焰", Color(0xFFB45309), circleBg = Color(0xFF0C0A08)),
    ThemeSwatch("白昼", "白昼", Color(0xFF2563EB), circleBg = Color(0xFFF6F6F8)),
)

@Composable
private fun ThemeCard(vm: AppViewModel) {
    val current = vm.state.themeName
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1428505A))
            .clip(RoundedCornerShape(22.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoopColors.brandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = LoopColors.brand,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "外观",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LoopColors.text,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "换一种心情，让界面更有色彩",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = LoopColors.muted,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            THEME_SWATCHES.forEach { swatch ->
                ThemeSwatchItem(
                    swatch = swatch,
                    active = normalizeTheme(current) == swatch.key,
                    onClick = {
                        vm.setTheme(swatch.key)
                        vm.notify("已切换「${swatch.label}」主题")
                    },
                )
            }
        }
    }
}

@Composable
private fun ThemeSwatchItem(swatch: ThemeSwatch, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(swatch.circleBg)
                .border(
                    width = if (active) 2.5.dp else 0.dp,
                    color = if (active) swatch.color else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(swatch.color),
            )
            if (active) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(swatch.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            swatch.label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) swatch.color else LoopColors.muted,
        )
    }
}

// ------------------------------ 网络子页 ------------------------------

private val REFRESH_OPTIONS = listOf(60L, 300L, 900L, 1800L)

@Composable
private fun NetSubPage(vm: AppViewModel, onBack: () -> Unit) {
    val state = vm.state
    var port by rememberSaveable { mutableStateOf(state.port.toString()) }
    var refreshIndex by rememberSaveable {
        mutableIntStateOf(
            REFRESH_OPTIONS.indexOf(state.balanceRefreshSeconds.toLong())
                .let { if (it >= 0) it else 1 },
        )
    }

    // 端口输入停止 600ms 后自动保存（设计稿行为）
    LaunchedEffect(port) {
        if (port.toIntOrNull() == state.port) return@LaunchedEffect
        delay(600)
        saveAll(vm, port = port.toIntOrNull() ?: return@LaunchedEffect)
    }

    val portValue = port.toIntOrNull()
    val portValid = portValue == null || portValue in 1024..65535

    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        SubHeader(title = "网络", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SetRow(
                title = "监听端口",
                desc = "网关服务的本地监听端口",
                trailing = {
                    Text(
                        if (state.running) "运行中" else "未运行",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.running) LoopColors.success else LoopColors.faint,
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = port,
                onValueChange = { v -> port = v.filter(Char::isDigit).take(5) },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoopColors.text,
                ),
                cursorBrush = SolidColor(LoopColors.brand),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(LoopColors.fieldBg)
                    .border(
                        1.5.dp,
                        if (portValid) LoopColors.line else LoopColors.danger,
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner -> inner() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (portValid) "1024 - 65535" else "端口需在 1024 - 65535 之间",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (portValid) LoopColors.muted else LoopColors.danger,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(20.dp))
            SetRow(
                title = "监听局域网",
                desc = "同一 Wi-Fi 下的其它设备可访问",
                trailing = {
                    LoopSwitch(checked = state.bindLan) {
                        vm.setBindLan(it)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            SelectorRow(
                label = "余额自动刷新",
                value = "${REFRESH_OPTIONS[refreshIndex] / 60} 分钟",
                onClick = {
                    refreshIndex = (refreshIndex + 1) % REFRESH_OPTIONS.size
                    saveAll(vm, refreshSeconds = REFRESH_OPTIONS[refreshIndex].toInt())
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "首页可见时按此间隔刷新各账号余额",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = LoopColors.faint,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ------------------------------ 并发与容错子页 ------------------------------

@Composable
private fun ConcSubPage(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"
    }
    var checking by remember { mutableStateOf(false) }
    var latest by remember { mutableStateOf<UpdateChecker.Release?>(null) }
    var checkError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(checking) {
        if (!checking) return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { UpdateChecker.check(versionName) }
        checking = false
        r.onSuccess { rel ->
            if (rel == null) vm.notify("已是最新版本") else latest = rel
        }.onFailure { e ->
            checkError = e.message ?: "检查失败"
            vm.notify("检查更新失败：${e.message ?: ""}")
        }
    }
    val state = vm.state
    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        SubHeader(title = "并发与容错", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetGroup {
                CompactSetRow(
                title = "单账号并发上限",
                desc = "同一账号同时处理的最大请求数",
                trailing = {
                    Stepper(
                        value = state.maxConcurrentPerAccount,
                        display = "${state.maxConcurrentPerAccount} 个",
                        onMinus = {
                            if (state.maxConcurrentPerAccount > 1) {
                                saveAll(vm, maxConcurrent = state.maxConcurrentPerAccount - 1)
                            }
                        },
                        onPlus = {
                            if (state.maxConcurrentPerAccount < 50) {
                                saveAll(vm, maxConcurrent = state.maxConcurrentPerAccount + 1)
                            }
                        },
                    )
                },
                )
                GroupDivider()
                CompactSetRow(
                title = "全局并发上限",
                desc = "所有账号同时在途的请求总数，0 表示不限",
                trailing = {
                    Stepper(
                        value = state.maxConcurrentGlobal,
                        display = if (state.maxConcurrentGlobal == 0) "不限" else "${state.maxConcurrentGlobal} 个",
                        onMinus = {
                            saveAll(vm, maxConcurrentGlobal = (state.maxConcurrentGlobal - 5).coerceAtLeast(0))
                        },
                        onPlus = {
                            val next = if (state.maxConcurrentGlobal == 0) 5
                            else (state.maxConcurrentGlobal + 5).coerceAtMost(200)
                            saveAll(vm, maxConcurrentGlobal = next)
                        },
                    )
                },
                )
                GroupDivider()
                CompactSetRow(
                title = "连续熔断失败",
                desc = "连续失败达到该次数后自动熔断",
                trailing = {
                    Stepper(
                        value = state.failThreshold,
                        display = "${state.failThreshold} 次",
                        onMinus = {
                            if (state.failThreshold > 1) saveAll(vm, failThreshold = state.failThreshold - 1)
                        },
                        onPlus = {
                            if (state.failThreshold < 50) saveAll(vm, failThreshold = state.failThreshold + 1)
                        },
                    )
                },
                )
                GroupDivider()
                CompactSetRow(
                title = "冷却时间",
                desc = "熔断后账号等待恢复的时长",
                trailing = {
                    Stepper(
                        value = state.cooldownSeconds / 60,
                        display = "${(state.cooldownSeconds / 60).coerceAtLeast(1)} 分钟",
                        onMinus = {
                            if (state.cooldownSeconds > 60) {
                                saveAll(vm, cooldownSeconds = state.cooldownSeconds - 60)
                            }
                        },
                        onPlus = {
                            if (state.cooldownSeconds < 86400) {
                                saveAll(vm, cooldownSeconds = state.cooldownSeconds + 60)
                            }
                        },
                    )
                },
                )
                GroupDivider()
                CompactSetRow(
                title = "会话粘性",
                desc = "同一会话优先路由到同一账号，保持上下文",
                trailing = {
                    LoopSwitch(checked = state.sessionSticky) {
                        vm.notify(if (it) "会话粘性已开启" else "会话粘性已关闭")
                        saveAll(vm, sessionSticky = it)
                    }
                },
            )
            }
            GroupDivider()
            CompactSetRow(
                title = "检查更新",
                desc = "当前版本 ${versionName}",
                trailing = {
                    TextButton(
                        onClick = { checking = true },
                        enabled = !checking,
                    ) {
                        Text(
                            if (checking) "检查中…" else "检查",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (checking) LoopColors.muted else LoopColors.brand,
                        )
                    }
                },
            )
            Spacer(Modifier.height(40.dp))

            latest?.let { rel ->
                AlertDialog(
                    onDismissRequest = { latest = null },
                    title = { Text("发现新版本 ${rel.version}", fontWeight = FontWeight.SemiBold) },
                    text = {
                        Text(
                            rel.notes.take(400).ifBlank { "更新内容请见发布页。" },
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            latest = null
                            val target = rel.apkUrl ?: rel.pageUrl
                            runCatching {
                                val i = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(target),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                vm.notify("正在打开下载页")
                                ctx.startActivity(i)
                            }.onFailure { vm.notify("打不开下载页：${it.message}") }
                        }) { Text("去下载") }
                    },
                    dismissButton = {
                        TextButton(onClick = { latest = null }) { Text("稍后") }
                    },
                )
            }
        }
    }
}

// ------------------------------ 代理子页 ------------------------------

@Composable
private fun ProxySubPage(vm: AppViewModel, onBack: () -> Unit) {
    val proxy = vm.state.proxy
    var enabled by rememberSaveable { mutableStateOf(proxy.enabled) }
    var type by rememberSaveable { mutableStateOf(proxy.type.name) }
    var host by rememberSaveable { mutableStateOf(proxy.host) }
    var port by rememberSaveable { mutableStateOf(proxy.port) }
    var user by rememberSaveable { mutableStateOf(proxy.username) }
    var pass by rememberSaveable { mutableStateOf(proxy.password) }

    val proxyTypes = listOf("HTTP", "SOCKS5")
    val portValue = port.toIntOrNull()
    val portValid = portValue == null || portValue in 1..65535

    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        SubHeader(title = "代理", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SetRow(
                title = "启用代理",
                desc = "国际上游走代理；国内域名自动直连",
                trailing = {
                    LoopSwitch(checked = enabled) {
                        enabled = it
                        vm.notify(if (it) "代理已启用" else "代理已关闭，配置已锁定")
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            // 代理配置区：关闭时变灰锁定
            Column(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (enabled) 1f else 0.4f),
            ) {
                SelectorRow(
                    label = "代理类型",
                    value = type,
                    onClick = {
                        if (!enabled) return@SelectorRow
                        type = proxyTypes[(proxyTypes.indexOf(type) + 1) % proxyTypes.size]
                    },
                )
                Spacer(Modifier.height(12.dp))
                IconInput(
                    icon = Icons.Outlined.Public,
                    value = host,
                    onValueChange = { host = it },
                    placeholder = "代理主机",
                    enabled = enabled,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "例如 127.0.0.1",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoopColors.faint,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                BasicTextField(
                    value = port,
                    onValueChange = { v -> port = v.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = LoopColors.text,
                    ),
                    cursorBrush = SolidColor(LoopColors.brand),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(LoopColors.fieldBg)
                        .border(
                            1.5.dp,
                            if (portValid) LoopColors.line else LoopColors.danger,
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (portValid) "1 - 65535" else "⚠ 端口需在 1 - 65535 之间",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (portValid) LoopColors.muted else LoopColors.danger,
                    modifier = Modifier.padding(start = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                IconInput(
                    icon = Icons.Outlined.Person,
                    value = user,
                    onValueChange = { user = it },
                    placeholder = "用户名（可选）",
                    enabled = enabled,
                )
                Spacer(Modifier.height(12.dp))
                IconInput(
                    icon = Icons.Outlined.Lock,
                    value = pass,
                    onValueChange = { pass = it },
                    placeholder = "密码（可选）",
                    password = true,
                    enabled = enabled,
                )
                Spacer(Modifier.height(18.dp))
                LoopSaveButton(
                    text = "保存代理设置",
                    icon = Icons.Outlined.Check,
                    enabled = enabled,
                ) {
                    val err = vm.saveProxy(
                        enabled = enabled,
                        type = if (type == "SOCKS5") ProxyType.SOCKS else ProxyType.HTTP,
                        host = host,
                        port = port,
                        user = user,
                        pass = pass,
                    )
                    if (err != null) vm.notify(err)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun IconInput(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.fieldBg)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = LoopColors.muted,
            modifier = Modifier.size(18.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            textStyle = TextStyle(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = LoopColors.text,
            ),
            cursorBrush = SolidColor(LoopColors.brand),
            modifier = Modifier
                .weight(1f)
                .padding(start = 11.dp, top = 15.dp, bottom = 15.dp, end = 4.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, fontSize = 15.sp, color = LoopColors.muted)
                    }
                    inner()
                }
            },
        )
    }
}

// ------------------------------ 问题反馈子页 ------------------------------

private val CONTACT_OPTIONS = listOf("qq" to "QQ", "wechat" to "微信", "email" to "邮箱")

@Composable
private fun FeedbackSubPage(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var content by remember { mutableStateOf("") }
    var contactIndex by remember { mutableIntStateOf(0) }
    var contact by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var submitting by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        SubHeader(title = "QQ群 · 1094669183", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "加入QQ群 1094669183，问题直接在群里反馈",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = LoopColors.muted,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            BasicTextField(
                value = content,
                onValueChange = { content = it.take(2000) },
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoopColors.text,
                ),
                cursorBrush = SolidColor(LoopColors.brand),
                minLines = 5,
                maxLines = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LoopColors.fieldBg)
                    .border(1.5.dp, LoopColors.line, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                decorationBox = { inner ->
                    Box {
                        if (content.isEmpty()) {
                            Text("问题描述", fontSize = 15.sp, color = LoopColors.faint)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            AttachmentPicker(
                images = images,
                onAdd = { uri -> addImage(context, uri, images) { images = it } },
                onRemove = { index -> images = images.filterIndexed { i, _ -> i != index } },
                onPreview = { preview = it },
            )
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 联系方式选择器：与输入框等高同宽风格
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(LoopColors.fieldBg)
                        .border(1.5.dp, LoopColors.line, RoundedCornerShape(18.dp))
                        .clickable { contactIndex = (contactIndex + 1) % CONTACT_OPTIONS.size },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 14.dp, end = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "联系方式",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = LoopColors.muted,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                CONTACT_OPTIONS[contactIndex].second,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LoopColors.text,
                            )
                        }
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = LoopColors.muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                BasicTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LoopColors.text,
                    ),
                    cursorBrush = SolidColor(LoopColors.brand),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(LoopColors.fieldBg)
                        .border(1.5.dp, LoopColors.line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    decorationBox = { inner ->
                        Box {
                            if (contact.isEmpty()) {
                                Text("账号 / 手机号 / 邮箱", fontSize = 15.sp, color = LoopColors.faint)
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(18.dp))
            LoopSaveButton(
                text = if (submitting) "提交中…" else "提交工单",
                icon = Icons.Outlined.Check,
                enabled = content.isNotBlank() && !submitting,
            ) {
                submitting = true
                vm.submitTicket(
                    content = content.trim(),
                    contactType = CONTACT_OPTIONS[contactIndex].first,
                    contact = contact.trim(),
                    images = images,
                ) { err ->
                    submitting = false
                    if (err == null) {
                        content = ""
                        contact = ""
                        images = emptyList()
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    preview?.let { base64 ->
        ImagePreviewDialog(base64 = base64, onDismiss = { preview = null })
    }
}

// ------------------------------ 检测球弹层 ------------------------------

@Composable
private fun BallSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    val state = vm.state
    // 从系统授权页返回后刷新权限状态
    LaunchedEffect(Unit) { vm.recheckOverlayPermission() }
    LoopSheet(
        visible = true,
        onDismiss = onDismiss,
        onVisibilityChanged = { vm.setSheetOpen(it) },
    ) {
        LoopSheetHeader(
            icon = Icons.Outlined.Pets,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "桌面宠物",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                "桌面上会出现一只会走会跳的宠物：点它触发回应动作，按住可以拖到任意位置。",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(16.dp))
            SetRow(
                title = "显示桌宠",
                desc = "关闭后宠物将从桌面隐藏",
                trailing = {
                    // 不在这里拦权限：MIUI 的 canDrawOverlays 会误报，
                    // 让服务真正去挂载，挂不上再提示授权。
                    LoopSwitch(checked = state.petBubble) {
                        vm.setPetBubble(it)
                    }
                },
            )
            if (!state.canDrawOverlays) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LoopColors.danger.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "未授予悬浮窗权限，桌宠无法显示",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LoopColors.danger,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "去授权",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = LoopColors.brand,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoopColors.brandSoft)
                            .clickable { vm.openOverlayPermissionSettings() }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "桌宠只在网关运行时显示。",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = LoopColors.muted,
            )
        }
    }
}

private fun ballLabel(state: UiState): String = when {
    !state.running -> "未运行"
    state.accountTotal == 0 || state.accountEnabled == 0 -> "无账号"
    else -> "待命"
}

// ------------------------------ 保存辅助 ------------------------------

/** 用当前状态补齐其余字段后保存；失败原因返回给调用方。 */
private fun saveAll(
    vm: AppViewModel,
    port: Int = vm.state.port,
    bindLan: Boolean = vm.state.bindLan,
    refreshSeconds: Int = vm.state.balanceRefreshSeconds,
    maxConcurrent: Int = vm.state.maxConcurrentPerAccount,
    maxConcurrentGlobal: Int = vm.state.maxConcurrentGlobal,
    failThreshold: Int = vm.state.failThreshold,
    cooldownSeconds: Int = vm.state.cooldownSeconds,
    sessionSticky: Boolean = vm.state.sessionSticky,
) {
    val err = vm.saveSettings(
        port = port,
        bindLan = bindLan,
        balanceRefreshSeconds = refreshSeconds,
        maxConcurrentPerAccount = maxConcurrent,
        maxConcurrentGlobal = maxConcurrentGlobal,
        failThreshold = failThreshold,
        cooldownSeconds = cooldownSeconds,
        sessionSticky = sessionSticky,
    )
    if (err != null) vm.notify(err)
}

// ------------------------------ 截图附件 ------------------------------

@Composable
private fun AttachmentPicker(
    images: List<String>,
    onAdd: (android.net.Uri) -> Unit,
    onRemove: (Int) -> Unit,
    onPreview: (String) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onAdd(uri) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 标题行：标题 + 计数 + 添加按钮
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("截图（可选）", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LoopColors.text)
            Spacer(Modifier.weight(1f))
            Text(
                "${images.size}/${RemoteAdminClient.MAX_IMAGES}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.faint,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(LoopColors.brandSoft)
                    .border(1.5.dp, LoopColors.line, RoundedCornerShape(14.dp))
                    .clickable { picker.launch("image/*") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "添加截图",
                    tint = LoopColors.brand,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // 缩略图行
        if (images.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                images.take(RemoteAdminClient.MAX_IMAGES).forEachIndexed { index, base64 ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(LoopColors.fieldBg)
                            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
                            .clickable { onPreview(base64) },
                    ) {
                        Thumbnail(base64 = base64, modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0x8C1F2937))
                                .clickable { onRemove(index) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "移除",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 全屏预览截图：点击任意位置关闭。 */
@Composable
private fun ImagePreviewDialog(base64: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, base64) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val payload = base64.substringAfter("base64,", base64)
                    val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val current = bitmap
            if (current != null) {
                Image(
                    bitmap = current.asImageBitmap(),
                    contentDescription = "预览截图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(base64: String, modifier: Modifier = Modifier) {
    val bitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(initialValue = null, base64) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val payload = base64.substringAfter("base64,", base64)
                val bytes = android.util.Base64.decode(payload, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }
    val current = bitmap
    if (current != null) {
        Image(
            bitmap = current.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

private fun addImage(
    context: android.content.Context,
    uri: android.net.Uri,
    current: List<String>,
    onResult: (List<String>) -> Unit,
) {
    if (current.size >= RemoteAdminClient.MAX_IMAGES) return
    Thread {
        val encoded = runCatching { encodeImage(context, uri) }.getOrNull()
        if (encoded != null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                onResult(current + encoded)
            }
        }
    }.apply { isDaemon = true }.start()
}

private fun encodeImage(context: android.content.Context, uri: android.net.Uri): String? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val original = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val maxSide = 1600
    val scale = minOf(1f, maxSide.toFloat() / maxOf(original.width, original.height))
    val scaled = if (scale < 1f) {
        android.graphics.Bitmap.createScaledBitmap(
            original,
            (original.width * scale).toInt().coerceAtLeast(1),
            (original.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        original
    }
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
    return "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
}

// ------------------------------ 系统提示词弹层 ------------------------------

@Composable
private fun SystemPromptSheet(vm: AppViewModel, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(vm.state.systemPrompt) }
    LoopSheet(
        visible = true,
        onDismiss = onDismiss,
        onVisibilityChanged = { vm.setSheetOpen(it) },
    ) {
        LoopSheetHeader(
            icon = Icons.Outlined.EditNote,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "系统提示词",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                "网关会给每个请求插入这段提示词，并移除客户端自带的 system 消息。留空则不注入。",
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 132.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(LoopColors.fieldBg)
                    .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                if (text.isEmpty()) {
                    Text(
                        "例如：你是一个回答简洁的中文助手，不说客套话。",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = LoopColors.muted,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = LoopColors.text,
                    ),
                    cursorBrush = SolidColor(LoopColors.brand),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "已输入 ${text.length} 字",
                fontSize = 12.sp,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(16.dp))
            LoopSaveButton(text = "保存", icon = Icons.Outlined.Check) {
                vm.setSystemPrompt(text)
                onDismiss()
            }
        }
    }
}
