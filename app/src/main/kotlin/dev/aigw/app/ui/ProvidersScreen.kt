package dev.aigw.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import dev.aigw.app.R
import kotlinx.coroutines.delay

private enum class ProviderKind(
    val title: String,
    val tagline: String,
    val providerId: String,
    val logoRes: Int,
) {
    FreePool(
        title = "免费池",
        tagline = "免登录公共通道 · 无需账号与密钥",
        providerId = "freepool",
        logoRes = R.drawable.ic_provider_freepool,
    ),
    WorkBuddy(
        title = "WorkBuddy",
        tagline = "腾讯 AI 编程助手 · 签到与积分查询",
        providerId = "codebuddy",
        logoRes = R.drawable.ic_provider_workbuddy,
    ),
    Trae(
        title = "Trae",
        tagline = "字节 AI IDE · 签到与额度查询",
        providerId = "trae",
        logoRes = R.drawable.ic_provider_trae,
    ),
    DeepSeek(
        title = "DeepSeek",
        tagline = "深度思考 · 联网搜索与长上下文",
        providerId = "deepseek",
        logoRes = R.drawable.ic_provider_deepseek,
    ),
    Loomy(
        title = "Loomy",
        tagline = "讯飞免费积分通道 · 短信登录",
        providerId = "loomy",
        logoRes = R.drawable.ic_provider_loomy,
    ),
    Mimo(
        title = "MiMo",
        tagline = "小米 AI Studio · Cookie 登录",
        providerId = "mimo",
        logoRes = R.drawable.ic_provider_mimo,
    ),
    Raccoon(
        title = "小浣熊",
        tagline = "商汤网页登录 · 签到与积分明细",
        providerId = "raccoon",
        logoRes = R.drawable.ic_provider_raccoon,
    ),
    Chatglm(
        title = "智谱清言",
        tagline = "GLM 系列 · 每日签到送积分",
        providerId = "chatglm",
        logoRes = R.drawable.ic_provider_chatglm,
    ),
    Doubao(
        title = "豆包",
        tagline = "Cookie 直连 · 工作模式 10 个模型",
        providerId = "doubao",
        logoRes = R.drawable.ic_provider_doubao,
    ),
    Jimeng(
        title = "即梦",
        tagline = "字节图片生成 · 每日 66 积分",
        providerId = "jimeng",
        logoRes = R.drawable.ic_provider_jimeng,
    ),
    Chatgpt(
        title = "ChatGPT",
        tagline = "Codex 后端 · 免费账号可用",
        providerId = "chatgpt",
        logoRes = R.drawable.ic_provider_chatgpt,
    ),
    Muse(
        title = "muse.ai",
        tagline = "生图与生视频 · 网页会话",
        providerId = "muse",
        logoRes = R.drawable.ic_provider_muse,
    ),
    Relay(
        title = "自定义供应商",
        tagline = "填 Base URL 与密钥即可添加",
        providerId = "custom",
        logoRes = R.drawable.ic_provider_relay,
    ),
    ;

    val supportsActivity: Boolean get() = this == WorkBuddy || this == Trae || this == Raccoon
    val supportsImport: Boolean get() =
        this == WorkBuddy || this == Trae || this == Mimo || this == Raccoon

    /** 是否有签到/积分体系（含智谱清言）。 */
    val supportsCheckin: Boolean get() = supportsActivity || this == Chatglm || this == Loomy

    /**
     * 是否在供应商列表里单独占一张卡。
     *
     * 自定义供应商不显示：右下角 + 号直接就是它的添加入口，
     * 列表里再放一张「自定义供应商」反而占位、且和 + 号重复。
     */
    val showInList: Boolean get() = this != Relay
}

/**
 * 供应商列表页（Loop 设计稿 viewProv）。
 *
 * 大标题 + live-dot 副标题 + 右上账号数徽章；卡片列表；FAB 打开添加弹层。
 */
@Composable
fun ProvidersScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    val state = vm.state

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshAll()
            delay(5000)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 0.dp,
                bottom = 170.dp,
            ),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(18.dp))
                    ProvidersHeader(count = state.accountTotal)
                    Spacer(Modifier.height(18.dp))
                }
            }
            val visibleKinds = ProviderKind.entries.filter { it.showInList }
            items(visibleKinds.size) { index ->
                val kind = visibleKinds[index]
                RiseIn(100 + index * 100) {
                    val mine = state.accounts.filter { it.providerId == kind.providerId }
                    // 免费池没有真实账号：徽章显示已接入的渠道数，显示账号数会误导
                    val isFreePool = kind.providerId == "freepool"
                    ProviderCard(
                        kind = kind,
                        accountCount = if (isFreePool) {
                            if (mine.isEmpty()) 0 else 3
                        } else mine.size,
                        enabledCount = if (isFreePool) 3 else mine.count { it.enabled },
                    ) { onOpen(kind.name) }
                }
                if (index != visibleKinds.lastIndex) Spacer(Modifier.height(10.dp))
            }
        }

        // FAB：仅在供应商页显示（设计稿仅 viewProv 有 fab）
        LoopFab(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 18.dp, bottom = 88.dp),
            visible = !showAdd,
            onClick = { showAdd = true },
        )
    }

    if (showAdd) {
        AddProviderSheet(
            onDismiss = { showAdd = false },
            onVisibilityChanged = { vm.setSheetOpen(it) },
            onSave = { name, baseUrl, key ->
                val ok = vm.addAccount(name, baseUrl, key)
                if (ok) showAdd = false
                ok
            },
        )
    }

    WorkBuddyLoginDialog(vm.state.wbLogin, vm)
}

/** 供应商页头部：大标题 + live 副标题 + 数字徽章。 */
@Composable
private fun ProvidersHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "供应商",
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
            color = LoopColors.text,
        )
        Spacer(Modifier.weight(1f))
        if (count > 0) {
            Text(
                "$count 个账号",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = LoopColors.muted,
            )
        }
    }
}

/** provider-card：56dp 图标 + 17sp 标题 + 13sp 描述 + deco 箭头。 */
@Composable
private fun ProviderCard(
    kind: ProviderKind,
    accountCount: Int,
    enabledCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 裸 logo：各家标识本身就有识别度，不再套底色框，视觉更干净
        Box(
            Modifier.size(34.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(kind.logoRes),
                contentDescription = kind.title,
                modifier = Modifier.size(34.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                kind.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = LoopColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                kind.tagline,
                fontSize = 12.sp,
                color = LoopColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        // 徽章槽位固定宽度：有没有账号都占同一格，右侧箭头才能对齐
        Box(Modifier.width(28.dp), contentAlignment = Alignment.CenterEnd) {
            if (accountCount > 0) {
                val allOn = enabledCount == accountCount
                Text(
                    "$enabledCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (allOn) LoopColors.text else LoopColors.warning,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (allOn) LoopColors.surfaceAlt else LoopColors.warningSoft)
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = LoopColors.faint,
            modifier = Modifier.size(18.dp),
        )
    }
    HorizontalDivider(color = LoopColors.line.copy(alpha = 0.55f), thickness = 0.8.dp)
}

/** 供应商图标背景（设计稿的浅色渐变）。 */
private fun providerIconBg(kind: ProviderKind): Color = when (kind) {
    ProviderKind.WorkBuddy -> LoopColors.accentGreenSoft
    ProviderKind.Trae -> LoopColors.accentCream
    ProviderKind.DeepSeek -> LoopColors.accentBlueSoft
    ProviderKind.Loomy -> LoopColors.accentGreenSoft
    ProviderKind.Raccoon -> LoopColors.accentBlueSoft
    ProviderKind.Mimo -> LoopColors.accentOrangeSoft
    ProviderKind.Chatglm -> LoopColors.accentBlueSoft
    ProviderKind.Doubao -> LoopColors.accentBlueSoft
    ProviderKind.Jimeng -> LoopColors.brandSoft
    ProviderKind.Chatgpt -> LoopColors.accentGreenSoft
    ProviderKind.Muse -> LoopColors.brandSoft
    ProviderKind.FreePool -> LoopColors.accentGreenSoft
    ProviderKind.Relay -> LoopColors.brandSoft
}

// ---------------------------------------------------------------------- FAB

/** FAB：62dp 渐变圆，呼吸阴影，打开后图标旋转 135 度（设计稿 open 态）。 */
@Composable
fun LoopFab(modifier: Modifier = Modifier, visible: Boolean, onClick: () -> Unit) {
    val interaction = androidx.compose.foundation.interaction.MutableInteractionSource()
    val pressed by interaction.collectIsPressedAsState()
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 135f else 0f,
        animationSpec = androidx.compose.animation.core.tween(350, easing = LoopEase),
        label = "fabRotate",
    )
    val appear by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300, easing = LoopEase),
        label = "fabAppear",
    )
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "fabBreathe")
    val glow by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1300, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "glow",
    )
    if (appear > 0.01f) {
        Box(
            modifier = modifier
                .graphicsLayer {
                    scaleX = appear
                    scaleY = appear
                    alpha = appear
                }
                .shadow(
                    8.dp,
                    CircleShape,
                    spotColor = LoopColors.brand.copy(alpha = glow),
                )
                .size(54.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(LoopColors.brand, LoopColors.brandDeep)))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.Add,
                contentDescription = "添加供应商",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
    }
}
