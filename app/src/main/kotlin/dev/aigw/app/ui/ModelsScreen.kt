package dev.aigw.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 模型页（设计稿：标题 + 自动刷新 + 搜索框 + 供应商筛选 + 模型网格大卡 + 详情弹层）。
 */
@Composable
fun ModelsScreen(vm: AppViewModel) {
    val state = vm.state
    var keyword by remember { mutableStateOf("") }
    var providerFilter by rememberSaveable { mutableStateOf("all") }
    var detailModel by remember { mutableStateOf<ModelUi?>(null) }

    // 首次进入与定时刷新：模型目录可能因账号变化而更新
    LaunchedEffect(Unit) {
        vm.refreshModels()
    }


    val providers = remember(state.models) { state.models.map { it.providerId }.distinct() }
    val filtered = state.models
        .filter { model ->
            (providerFilter == "all" || model.providerId == providerFilter) &&
                (keyword.isBlank() ||
                    model.model.contains(keyword, ignoreCase = true) ||
                    providerLabel(model.providerId).contains(keyword, ignoreCase = true) ||
                    model.id.contains(keyword, ignoreCase = true))
        }
        .sortedWith(compareBy({ it.providerId }, { it.model }))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(LoopColors.bg)
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 18.dp,
            bottom = 170.dp,
        ),
    ) {
        // 标题 + 副标题 + 自动刷新
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "模型",
                    fontSize = 28.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = LoopColors.text,
                )
                Spacer(Modifier.weight(1f))
                RefreshButton(
                    loading = state.loadingModels,
                    onClick = { vm.refreshModels() },
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.models.isEmpty()) {
                        "暂无模型"
                    } else {
                        "${filtered.size} 个模型 · 来自已启用的供应商"
                    },
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = LoopColors.muted,
                    modifier = Modifier.weight(1f),
                )
                val probing = state.probingModels
                when {
                    probing != null -> Text(
                        "检测中 ${probing.first}/${probing.second}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LoopColors.brandLight,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    )
                    state.models.isNotEmpty() -> Text(
                        "全部试调",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LoopColors.brandLight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { vm.probeModels() }
                            .padding(horizontal = 10.dp, vertical = 14.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        // 搜索框
        item {
            ModelSearchBar(
                keyword = keyword,
                onKeywordChange = { keyword = it },
                onSearch = {
                    if (keyword.isNotBlank()) vm.notify("已搜索「${keyword.trim()}」")
                },
            )
            Spacer(Modifier.height(12.dp))
        }
        // 供应商筛选
        item {
            ProviderChips(
                providers = providers,
                selected = providerFilter,
                onSelect = { providerFilter = it },
            )
            Spacer(Modifier.height(16.dp))
        }
        if (filtered.isEmpty() && state.loadingModels) {
            // 首次拉取：用骨架屏表示"正在加载"，而不是空状态插画
            items(7) { SkeletonRow() }
        } else if (filtered.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillParentMaxHeight(0.7f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.weight(0.9f))
                    EmptyMascot(size = 88.dp)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "暂无模型",
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LoopColors.subText,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "添加供应商后自动拉取。也可以用免费池：无需登录，直接问",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = LoopColors.muted,
                    )
                    Spacer(Modifier.weight(1.1f))
                }
            }
        } else {
            itemsIndexed(filtered) { _, model ->
                ModelRow(
                    model = model,
                    onClick = { detailModel = model },
                    onTest = { vm.probeSingle(model) },
                )
            }
        }
    }

    detailModel?.let { model ->
        ModelDetailSheet(
            model = model,
            onDismiss = { detailModel = null },
            onVisibilityChanged = { vm.setSheetOpen(it) },
        )
    }
}

// ------------------------------ 自动刷新按钮 ------------------------------

/** 模型类型徽章：图片 / 视频。 */
@Composable
private fun ModelTypeBadge(type: String) {
    val label = if (type == "image") "图片" else "视频"
    val tint = if (type == "image") LoopColors.purple else LoopColors.brand
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "refreshSpin")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "spin",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(50))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = "刷新模型",
            tint = if (loading) LoopColors.brandLight else LoopColors.muted,
            modifier = Modifier
                .size(18.dp)
                .graphicsLayer { if (loading) rotationZ = spin },
        )
        Text(
            if (loading) "刷新中" else "刷新",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (loading) LoopColors.brandLight else LoopColors.muted,
        )
    }
}

// ------------------------------ 搜索框 ------------------------------

@Composable
private fun ModelSearchBar(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x1428505A))
            .clip(RoundedCornerShape(22.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(22.dp))
            .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        BasicTextField(
            value = keyword,
            onValueChange = onKeywordChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = LoopColors.text,
            ),
            cursorBrush = SolidColor(LoopColors.brand),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            decorationBox = { inner ->
                Box {
                    if (keyword.isEmpty()) {
                        Text("模型和供应商", fontSize = 16.sp, color = LoopColors.faint)
                    }
                    inner()
                }
            },
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(LoopColors.surfaceAlt)
                .clickable(onClick = onSearch),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = "搜索",
                tint = LoopColors.subText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ------------------------------ 供应商筛选 chips ------------------------------

@Composable
private fun ProviderChips(
    providers: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip("全部", active = selected == "all") { onSelect("all") }
        providers.forEach { pid ->
            FilterChip(providerLabel(pid), active = selected == pid) { onSelect(pid) }
        }
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) LoopColors.brand else LoopColors.surfaceAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
            color = if (active) Color.White else LoopColors.subText,
        )
    }
}

// ------------------------------ 骨架屏 ------------------------------

/** 拉取中的占位行：形状对齐真实行，避免列表"跳出来"一下。 */
@Composable
private fun SkeletonRow() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "shimmer",
    )
    val bar = LoopColors.surfaceAlt.copy(alpha = shimmer)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(bar))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bar),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.24f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(bar),
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(56.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(bar),
        )
    }
    HorizontalDivider(color = LoopColors.line.copy(alpha = 0.4f), thickness = 0.8.dp)
}

// ------------------------------ 模型行 ------------------------------

@Composable
private fun ModelRow(
    model: ModelUi,
    onClick: () -> Unit,
    onTest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 探测状态：绿=可调用、红=不可用、灰=未检测
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    when (model.probeOk) {
                        true -> LoopColors.success
                        false -> LoopColors.danger
                        null -> LoopColors.faint
                    },
                ),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.displayName.ifBlank { model.model },
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoopColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (model.modelType != "text") {
                    Spacer(Modifier.width(8.dp))
                    ModelTypeBadge(model.modelType)
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(providerLabel(model.providerId))
                    if (model.contextWindow > 0) {
                        append("  ·  ")
                        append(formatContext(model.contextWindow))
                        append(" 上下文")
                    }
                },
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = LoopColors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (model.probing) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LoopColors.brand,
            )
        } else {
            Box(
                Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(LoopColors.surfaceAlt)
                    .clickable(onClick = onTest)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "试调",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoopColors.subText,
                )
            }
        }
    }
    HorizontalDivider(color = LoopColors.line.copy(alpha = 0.55f), thickness = 0.8.dp)
}

@Composable
private fun ModelTag(tag: String) {
    val normalized = tag.lowercase()
    val (bg, fg) = when (normalized) {
        "hot" -> LoopColors.accentOrangeSoft to LoopColors.warningDeep
        "free" -> LoopColors.accentGreenLight to Color(0xFF16A34A)
        else -> LoopColors.brandSoft to LoopColors.subText
    }
    Text(
        tagText(normalized),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

// ------------------------------ 模型详情弹层 ------------------------------

@Composable
private fun ModelDetailSheet(
    model: ModelUi,
    onDismiss: () -> Unit,
    onVisibilityChanged: (Boolean) -> Unit,
) {
    LoopSheet(visible = true, onDismiss = onDismiss, onVisibilityChanged = onVisibilityChanged) {
        LoopSheetHeader(
            icon = Icons.Outlined.Home,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = model.model,
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DetailInfoRow("所属供应商", providerLabel(model.providerId), valueColor = LoopColors.brand)
            DetailInfoRow(
                "完整调用名",
                model.id,
            )
            if (model.contextWindow > 0) {
                DetailInfoRow("上下文长度", formatContext(model.contextWindow))
            }
            if (model.maxOutputTokens > 0) {
                DetailInfoRow("最大输出", "${model.maxOutputTokens} tokens")
            }
            if (model.tags.isNotEmpty()) {
                DetailInfoRow("能力标签", model.tags.joinToString(" · ") { tagText(it.lowercase()) })
            }
            DetailInfoRow("调用方式", "OpenAI 兼容")
            DetailInfoRow(
                "状态",
                when (model.probeOk) {
                    true -> "● 可用"
                    false -> "● 不可用"
                    null -> "未检测"
                },
                valueColor = when (model.probeOk) {
                    true -> LoopColors.successDeep
                    false -> LoopColors.dangerDeep
                    null -> LoopColors.muted
                },
            )
            model.probeError.takeIf { model.probeOk == false && it.isNotBlank() }?.let { err ->
                Text(
                    err,
                    fontSize = 12.sp,
                    color = LoopColors.danger,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String, valueColor: Color = LoopColors.text) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.fieldBg)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LoopColors.muted)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ------------------------------ 工具 ------------------------------

private fun providerLabel(providerId: String): String = when (providerId) {
    "codebuddy" -> "WorkBuddy"
    "trae" -> "Trae"
    "deepseek" -> "DeepSeek"
    "loomy" -> "Loomy"
    "mimo" -> "MiMo"
    "raccoon" -> "小浣熊"
    "chatglm" -> "智谱清言"
    "custom" -> "自定义供应商"
    "doubao" -> "豆包"
    else -> providerId
}

private fun tagText(tag: String): String = when (tag.lowercase()) {
    "chat" -> "对话"
    "hot" -> "热门"
    "free" -> "免费"
    "vision" -> "视觉"
    "code" -> "代码"
    "reasoning" -> "推理"
    else -> tag
}

private fun formatContext(value: Long): String = when {
    value >= 1_000_000 -> "%.0fM".format(value / 1_000_000.0)
    value >= 1_000 -> "${value / 1_000}K"
    else -> "$value"
}
