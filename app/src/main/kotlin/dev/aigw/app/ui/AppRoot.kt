package dev.aigw.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

/** 底部导航页签（顺序与 Loop 设计稿一致，中心为仪表盘圆球）。 */
enum class Tab(val label: String, val icon: ImageVector) {
    Providers("供应商", Icons.Outlined.Cloud),
    Models("模型", Icons.Outlined.ViewInAr),
    Home("仪表盘", Icons.Outlined.Home),
    Logs("日志", Icons.Outlined.ListAlt),
    Settings("设置", Icons.Outlined.Settings),
}

@Composable
fun AppRoot(vm: AppViewModel) {
    val themeName = vm.state.themeName
    // 主题切换：先同步 LoopColors 色板，再整树重建，保证全界面即时换色
    androidx.compose.runtime.key(themeName) {
        remember(themeName) { LoopColors.apply(themeName) }
        // Material 色板要在主题重建范围内：LoopColors.apply 先跑，
        // MaterialTheme 再按新色板生成，深色主题下的输入框文字才不会隐形。
        androidx.compose.material3.MaterialTheme(
            colorScheme = dev.aigw.app.ui.theme.loopColorScheme(),
        ) {
            AppRootContent(vm)
        }
    }
}

@Composable
private fun AppRootContent(vm: AppViewModel) {
    // 首屏为仪表盘
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    var selectedProvider by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var toast by remember { mutableStateOf<String?>(null) }
    var toastTick by remember { mutableStateOf(0) }

    // 状态栏图标明暗随主题：暗夜蓝下用浅色图标
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        val dark = vm.state.themeName != "白昼"
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // 定期把后台下发的公告/版本同步到 UI：心跳线程在 Service 里跑，这里负责展示
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshRemoteMessages()
            delay(30_000)
        }
    }

    LaunchedEffect(vm.state.message) {
        vm.state.message?.let {
            toast = it
            toastTick++
            vm.consumeMessage()
        }
    }

    // toast 自动隐藏
    LaunchedEffect(toastTick) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }

    // 从仪表盘启动网关但没有任何账号时，跳到供应商页引导添加
    LaunchedEffect(vm.state.pendingTab) {
        val target = vm.state.pendingTab ?: return@LaunchedEffect
        tab = when (target) {
            "providers" -> Tab.Providers
            "models" -> Tab.Models
            "logs" -> Tab.Logs
            "settings" -> Tab.Settings
            else -> Tab.Home
        }
        vm.consumePendingTab()
    }

    // 进入供应商详情页后，手机返回键先退回供应商列表，而不是退出 App
    BackHandler(enabled = selectedProvider != null) {
        selectedProvider = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        Box(Modifier.fillMaxSize()) {
            when {
                selectedProvider != null -> ProviderAccountsScreen(
                    providerId = selectedProvider!!,
                    vm = vm,
                    onBack = { selectedProvider = null },
                )
                tab == Tab.Providers -> ProvidersScreen(vm) { selectedProvider = it }
                tab == Tab.Models -> ModelsScreen(vm)
                tab == Tab.Home -> LoopDashboard(vm)
                tab == Tab.Logs -> LogsScreen(vm)
                tab == Tab.Settings -> SettingsScreen(vm)
            }
        }

        // 底部悬浮胶囊导航（供应商详情页隐藏）
        // 弹层打开时隐藏导航，避免遮挡弹层内容（弹层关闭后恢复）
        if (selectedProvider == null && !vm.state.sheetOpen) {
            LoopNavBar(
                tab = tab,
                onSelect = { tab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }

        // 底部居中 Toast：胶囊形深色背景，从下方淡入上浮
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn(tween(300)) + slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(300),
            ),
            exit = fadeOut(tween(250)) + slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(250),
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 112.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFF1F2937).copy(alpha = 0.95f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                shadowElevation = 14.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 9.dp, end = 18.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        toast ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp,
                    )
                }
            }
        }
    }

    vm.state.announcement?.let { ann ->
        RemoteAnnouncementDialog(ann) { vm.consumeAnnouncement() }
    }
    vm.state.updateInfo?.let { upd ->
        RemoteUpdateDialog(
            update = upd,
            onDownload = { vm.openUpdateUrl(upd.url) },
            onDismiss = { vm.consumeUpdate() },
        )
    }
}

/** 底部胶囊导航：白底圆角胶囊，5 项等分，中间是渐变圆球（仪表盘）。 */
@Composable
fun LoopNavBar(tab: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = LoopColors.surface,
        border = BorderStroke(1.dp, LoopColors.line),
        shadowElevation = 16.dp,
        // 通栏贴底：只保留顶部圆角，不再是浮在内容上的大胶囊
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Tab.entries.forEach { t ->
                if (t == Tab.Home) {
                    NavOrbItem(selected = tab == t, onClick = { onSelect(t) })
                } else {
                    NavItem(t, selected = tab == t) { onSelect(t) }
                }
            }
        }
    }
}

/** 普通导航项：图标 + 文字 + 激活时底部渐变下划线（scaleX 弹入）。 */
@Composable
private fun NavItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) LoopColors.brand else LoopColors.muted
    val underlineScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f),
        label = "underline",
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            t.icon,
            contentDescription = t.label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            t.label,
            fontSize = 10.5.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = color,
        )
        Spacer(Modifier.height(3.dp))
        // navIn：下划线 scaleX 弹入
        Box(
            Modifier
                .width(16.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .graphicsLayer { scaleX = underlineScale.coerceAtLeast(0.001f) }
                .background(Brush.horizontalGradient(listOf(LoopColors.brand, LoopColors.brandLight))),
        )
    }
}

/** 中心圆球项：46dp 渐变球 + 浮动动画 + 按压缩放。 */
@Composable
private fun NavOrbItem(selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 700f),
        label = "orbScale",
    )
    // float 3s 循环：0 -> -5px -> 0
    val floatTransition = rememberInfiniteTransition(label = "orbFloat")
    val floatOffset by floatTransition.animateFloat(
        initialValue = 0f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "floatY",
    )
    val orbScale = if (selected) 1f else 0.92f
    Box(
        modifier = Modifier
            .padding(vertical = 2.dp)
            .size(38.dp)
            .graphicsLayer {
                scaleX = scale * orbScale
                scaleY = scale * orbScale
                translationY = floatOffset
            }
            .shadow(8.dp, CircleShape, spotColor = LoopColors.brand.copy(alpha = 0.4f))
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(LoopColors.brand, LoopColors.brandLight)))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = "仪表盘",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 远程公告弹窗：展示后台下发的标题与正文，关闭后不再重复弹。 */
@Composable
private fun RemoteAnnouncementDialog(
    announcement: AnnouncementUi,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LoopColors.surface,
        title = {
            Text(
                announcement.title.ifBlank { "公告" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.text,
            )
        },
        text = {
            Text(
                announcement.content,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = LoopColors.muted,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("知道了", color = LoopColors.brand, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

/** 远程更新弹窗：强制更新时无法关闭（只能点下载）。 */
@Composable
private fun RemoteUpdateDialog(
    update: UpdateUi,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!update.force) onDismiss() },
        containerColor = LoopColors.surface,
        title = {
            Text(
                if (update.force) "必须更新到 ${update.version}" else "发现新版本 ${update.version}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.text,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (update.notes.isNotBlank()) {
                    Text(update.notes, fontSize = 13.sp, lineHeight = 20.sp, color = LoopColors.muted)
                }
                if (update.force) {
                    Text(
                        "当前版本已不受支持，请更新后继续使用。",
                        fontSize = 12.sp,
                        color = LoopColors.danger,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text("立即下载", color = LoopColors.brand, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = if (update.force) null else {
            {
                TextButton(onClick = onDismiss) {
                    Text("稍后", color = LoopColors.muted)
                }
            }
        },
    )
}
