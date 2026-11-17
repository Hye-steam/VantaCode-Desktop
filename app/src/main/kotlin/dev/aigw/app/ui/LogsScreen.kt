package dev.aigw.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 日志页（设计稿：调用记录 / 请求记录两个页签）。
 *
 * - 调用记录：API 调用的结构化卡片（状态/模型/账号/反馈/供应商），可筛选与搜索；
 * - 请求记录：App 系统反馈的纯文本时间线；
 * - 右上角搜索按钮打开搜索弹层。
 */
@Composable
fun LogsScreen(vm: AppViewModel) {
    val state = vm.state
    var tab by rememberSaveable { mutableStateOf("call") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var keyword by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshAll()
            delay(3000)
        }
    }

    // 过滤逻辑提到这里：空态判断与列表渲染共用
    val kw = keyword.trim()
    val filteredCalls = state.calls
        .asReversed()
        .filter { call ->
            (filter == "all" || callStatusOf(call) == filter) &&
                (kw.isEmpty() ||
                    call.model.contains(kw, ignoreCase = true) ||
                    call.account.contains(kw, ignoreCase = true) ||
                    callProviderName(call.providerId).contains(kw, ignoreCase = true) ||
                    call.detail.contains(kw, ignoreCase = true))
        }
    val filteredEvents = state.events
        .take(500)
        .filter { e ->
            kw.isEmpty() ||
                e.message.contains(kw, ignoreCase = true) ||
                e.tag.contains(kw, ignoreCase = true)
        }

    Column(
        Modifier
            .fillMaxSize()
            .background(LoopColors.bg)
            .statusBarsPadding(),
    ) {
        // 顶部：标题 + 搜索按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "日志",
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold,
                color = LoopColors.text,
                modifier = Modifier.padding(start = 4.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .shadow(6.dp, CircleShape, ambientColor = Color(0x1428505A))
                    .clip(CircleShape)
                    .background(LoopColors.surface)
                    .border(1.dp, LoopColors.line, CircleShape)
                    .clickable { showSearch = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "搜索",
                    tint = LoopColors.text,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
        ) {
            LogTabCard(tab = tab, onSelect = { tab = it })
            Spacer(Modifier.height(12.dp))

            if (tab == "call") {
                LogFilterChips(filter = filter, onChange = { filter = it })
                Spacer(Modifier.height(16.dp))
            }

            val isEmpty = if (tab == "call") filteredCalls.isEmpty() else filteredEvents.isEmpty()
            if (isEmpty) {
                // 空状态卡延伸到页面底部
                LogEmptyState(
                    text = if (tab == "call") "暂无相关调用记录" else "暂无请求日志",
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (tab == "call") {
                        CallLogList(calls = filteredCalls, onCopy = { vm.notify("已复制到剪贴板") })
                    } else {
                        RequestLogList(events = filteredEvents, onCopy = { vm.notify("已复制到剪贴板") })
                    }
                    Spacer(Modifier.height(132.dp))
                }
            }
        }
    }

    if (showSearch) {
        LogSearchSheet(
            scopeLabel = if (tab == "call") "调用记录" else "请求记录",
            initialKeyword = keyword,
            onDismiss = { showSearch = false },
            onVisibilityChanged = { vm.setSheetOpen(it) },
            onSubmit = { kw ->
                keyword = kw
                showSearch = false
                if (kw.isNotBlank()) vm.notify("已搜索「$kw」")
            },
        )
    }
}

// ------------------------------ Tab 切换卡 ------------------------------

@Composable
private fun LogTabCard(tab: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LogTabItem("调用记录", selected = tab == "call") { onSelect("call") }
        LogTabItem("请求记录", selected = tab == "request") { onSelect("request") }
    }
}

@Composable
private fun LogTabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(top = 6.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) LoopColors.text else LoopColors.muted,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .width(if (selected) 32.dp else 0.dp)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) LoopColors.brand else Color.Transparent),
        )
    }
}

// ------------------------------ 状态筛选 chips ------------------------------

private enum class LogFilter(val key: String, val label: String) {
    All("all", "全部"),
    Success("success", "成功"),
    Fail("fail", "失败"),
    Abort("abort", "中断"),
}

@Composable
private fun LogFilterChips(filter: String, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LogFilter.entries.forEach { f ->
            val active = filter == f.key
            val tint = when (f) {
                LogFilter.All -> LoopColors.brand
                LogFilter.Success -> LoopColors.success
                LogFilter.Fail -> LoopColors.danger
                LogFilter.Abort -> LoopColors.warning
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) tint.copy(alpha = 0.16f) else LoopColors.surfaceAlt)
                    .border(
                        1.dp,
                        if (active) tint.copy(alpha = 0.45f) else LoopColors.line,
                        RoundedCornerShape(50),
                    )
                    .clickable { onChange(f.key) }
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    f.label,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) tint else LoopColors.subText,
                )
            }
        }
    }
}

// ------------------------------ 调用记录卡片 ------------------------------

/** 判断调用状态：成功 / 中断（客户端取消、流断开）/ 失败。 */
private fun callStatusOf(call: CallUi): String {
    if (call.ok) return "success"
    val d = call.detail.lowercase()
    return if (
        d.contains("中断") || d.contains("取消") || d.contains("断开") ||
        d.contains("cancel") || d.contains("abort") || d.contains("disconnect")
    ) "abort" else "fail"
}

private fun callProviderName(providerId: String): String = when (providerId) {
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

/** 复制用：把一条调用记录拼成可读文本。 */
private fun logCopyText(call: CallUi): String = buildString {
    append(formatLogDate(call.time)).append(" · ")
    append(if (call.ok) "成功" else "失败").append("\n")
    append("模型：").append(call.model).append("\n")
    append("账号：").append(call.account)
    if (call.detail.isNotBlank()) append("\n").append(call.detail)
    append("\n供应商：").append(callProviderName(call.providerId))
}

@Composable
private fun CallLogList(calls: List<CallUi>, onCopy: (String) -> Unit) {
    calls.forEach { call ->
        CallLogCard(call, onCopy)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CallLogCard(call: CallUi, onCopy: (String) -> Unit) {
    var expanded by remember(call.time, call.via) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val status = callStatusOf(call)
    val dotColor = when (status) {
        "success" -> LoopColors.success
        "fail" -> LoopColors.danger
        else -> LoopColors.warning
    }
    val statusText = when (status) {
        "success" -> "成功"
        "fail" -> "失败"
        else -> "中断"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LoopColors.surface)
            .border(1.dp, LoopColors.line, RoundedCornerShape(16.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(8.dp))
            Text(
                statusText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = dotColor,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                call.model,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
                color = LoopColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatLogClock(call.time),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = LoopColors.muted,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            buildString {
                append(providerLabel(call.providerId))
                if (call.detail.isNotBlank()) {
                    append("  ·  ")
                    append(call.detail)
                }
            },
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = LoopColors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
        if (expanded) {
            Spacer(Modifier.height(11.dp))
            HorizontalDivider(color = LoopColors.line, thickness = 1.dp)
            Spacer(Modifier.height(11.dp))
            DetailLine("账号", call.account)
            if (call.inputTokens > 0 || call.outputTokens > 0) {
                DetailLine("Token", "${call.inputTokens} / ${call.outputTokens}")
            }
            if (call.detail.isNotBlank()) {
                DetailLine("详情", call.detail)
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        clipboard.setText(AnnotatedString(logCopyText(call)))
                        onCopy(logCopyText(call))
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Text(
                    "复制这条记录",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LoopColors.brandLight,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 17.sp, color = LoopColors.muted, modifier = Modifier.width(52.dp))
        Text(value, fontSize = 12.sp, lineHeight = 17.sp, color = LoopColors.subText, modifier = Modifier.weight(1f))
    }
}

// ------------------------------ 请求记录时间线 ------------------------------

@Composable
private fun RequestLogList(events: List<EventUi>, onCopy: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        events.forEachIndexed { index, e ->
            if (index > 0) {
                HorizontalDivider(color = LoopColors.line.copy(alpha = 0.5f), thickness = 0.8.dp)
            }
            RequestLine(e, onCopy)
        }
    }
}

@Composable
private fun RequestLine(event: EventUi, onCopy: (String) -> Unit) {
    val clipboard = LocalClipboardManager.current
    val color = when (event.level) {
        "WARN" -> LoopColors.warning
        "ERROR" -> LoopColors.danger
        else -> LoopColors.subText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable {
                clipboard.setText(AnnotatedString(event.message))
                onCopy(event.message)
            }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            formatRequestTime(event.time),
            fontSize = 11.5.sp,
            lineHeight = 19.sp,
            color = LoopColors.muted,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            event.message,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = color,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

// ------------------------------ 空状态 ------------------------------

@Composable
private fun LogEmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.9f))
        EmptyMascot(size = 88.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(18.dp))
        Text(
            text,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = LoopColors.subText,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "发起调用后会显示在这里",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = LoopColors.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.weight(1.1f))
    }
}

// ------------------------------ 搜索弹层 ------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogSearchSheet(
    scopeLabel: String,
    initialKeyword: String,
    onDismiss: () -> Unit,
    onVisibilityChanged: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
) {
    var query by remember { mutableStateOf(initialKeyword) }
    var focused by remember { mutableStateOf(false) }
    val hotKeywords = listOf("DeepSeek", "gpt-4o", "WorkBuddy", "失败", "超时")

    LoopSheet(visible = true, onDismiss = onDismiss, onVisibilityChanged = onVisibilityChanged) {
        LoopSheetHeader(
            icon = Icons.Outlined.Search,
            iconBg = LoopColors.brandSoft,
            iconTint = LoopColors.brand,
            title = "搜索$scopeLabel",
            onDismiss = onDismiss,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            // 搜索框
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (focused) LoopColors.surface else LoopColors.fieldBg)
                    .border(
                        1.dp,
                        if (focused) LoopColors.brand.copy(alpha = 0.4f) else LoopColors.line,
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 18.dp, vertical = 15.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 16.sp, color = LoopColors.text),
                    cursorBrush = SolidColor(LoopColors.brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(query.trim()) }),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("找点什么吧", fontSize = 16.sp, color = LoopColors.faint)
                            }
                            inner()
                        }
                    },
                )
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = "搜索",
                    tint = LoopColors.faint,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            // 热门搜索
            Text(
                "热门搜索",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = LoopColors.muted,
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                hotKeywords.forEach { hot ->
                    Text(
                        hot,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LoopColors.subText,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoopColors.fieldBg)
                            .border(1.dp, LoopColors.line, RoundedCornerShape(50))
                            .clickable { onSubmit(hot) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

// ------------------------------ 格式化 ------------------------------

private val LOG_DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MM.dd HH:mm")

private fun formatLogDate(ms: Long): String =
    LOG_DATE_FORMATTER.format(
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()),
    )

private val REQ_TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")

private val CLOCK_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

private fun formatLogClock(ms: Long): String =
    CLOCK_FORMATTER.format(
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()),
    )

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

private fun formatRequestTime(ms: Long): String =
    REQ_TIME_FORMATTER.format(
        java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()),
    )
