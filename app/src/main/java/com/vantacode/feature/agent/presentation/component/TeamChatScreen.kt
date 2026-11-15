package com.vantacode.feature.agent.presentation.component

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vantacode.R
import com.vantacode.core.theme.Radius
import com.vantacode.core.theme.Spacing
import com.vantacode.core.theme.semanticColors
import com.vantacode.core.ui.AppTextField
import com.vantacode.core.ui.MemberAvatar
import com.vantacode.feature.settings.presentation.component.settingsPageBackground
import com.vantacode.feature.agent.data.local.entity.TeamChatMessageEntity
import com.vantacode.feature.agent.domain.team.TeamDefinition
import compose.icons.FeatherIcons
import compose.icons.feathericons.ArrowLeft
import compose.icons.feathericons.ArrowUp
import compose.icons.feathericons.FileText
import compose.icons.feathericons.Plus
import compose.icons.feathericons.Zap
import compose.icons.feathericons.Cpu
import compose.icons.feathericons.MoreHorizontal
import compose.icons.feathericons.Square
import compose.icons.feathericons.Users

/**
 * 团队群聊窗口：一个能翻历史、能自己说话的群。
 *
 * 群主（用户）的消息靠右，成员的消息靠左带头像；发送后成员依次接话，接完自动落到时间线。
 * 记录按团队名存在独立库，退出再进来还在。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TeamChatScreen(
    team: TeamDefinition?,
    messages: List<TeamChatMessageEntity>,
    sending: Boolean,
    streaming: com.vantacode.feature.agent.presentation.TeamChatViewModel.StreamingTurn?,
    error: String?,
    onSend: (String, List<android.net.Uri>, List<android.net.Uri>) -> Unit,
    onStop: () -> Unit,
    /** 正在执行的成员名列表；非空时显示执行进度条。 */
    executingMembers: List<String>,
    onStopExecution: () -> Unit,
    /** 点执行类消息时跳到对应的执行子会话。 */
    onOpenExecution: (String) -> Unit,
    /** 跳到团队编辑页（加/减成员、改群规）。 */
    onEditTeam: () -> Unit,
    /** 讨论收敛后是否自动交给执行者。 */
    autoStart: Boolean,
    onToggleAutoStart: (Boolean) -> Unit,
    /** 中性提示（例如「讨论已收敛，点开工」），与红色错误区分。 */
    notice: String?,
    /** 讨论已收敛但没自动开工：给出一个手动开工按钮。 */
    canStartWork: Boolean,
    onStartWork: () -> Unit,
    onDismissError: () -> Unit,
    onClearHistory: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var pendingImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var pendingFiles by remember { mutableStateOf<List<Pair<android.net.Uri, String>>>(emptyList()) }
    var showAttachSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(9)
    ) { uris -> if (uris.isNotEmpty()) pendingImages = pendingImages + uris }
    // 文件：与普通对话一致，可传任意文件；群聊里按文本读给成员看。
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "文件"
            pendingFiles = pendingFiles + (uri to name)
        }
    }
    // 拍照：预览图落到缓存目录，转成 uri 走图片通道。
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val file = java.io.File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
            runCatching {
                java.io.FileOutputStream(file).use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out) }
            }
            pendingImages = pendingImages + android.net.Uri.fromFile(file)
        }
    }
    var showMembers by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    BackHandler { onNavigateBack() }

    if (showMembers) MembersDialog(team = team, onDismiss = { showMembers = false })
    if (showAttachSheet) {
        var showAppPicker by remember { mutableStateOf(false) }
        AttachmentSheet(
            canUploadFiles = true,
            canUploadImages = true,
            onUploadFile = { showAttachSheet = false; filePicker.launch(arrayOf("*/*")) },
            onUploadImage = {
                showAttachSheet = false
                imagePicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onTakePhoto = { showAttachSheet = false; cameraLauncher.launch(null) },
            onUploadApp = { showAttachSheet = false; showAppPicker = true },
            onDismiss = { showAttachSheet = false }
        )
        if (showAppPicker) {
            AppPickerSheet(
                onPick = { apkPath, appName, packageName ->
                    showAppPicker = false
                    try {
                        val src = java.io.File(apkPath)
                        if (src.exists()) {
                            val safeName = appName.replace(Regex("[^\\w\\u4e00-\\u9fff-]"), "_")
                            val destDir = java.io.File(context.cacheDir, "app_uploads")
                            destDir.mkdirs()
                            val dest = java.io.File(destDir, "$safeName.apk")
                            src.copyTo(dest, overwrite = true)
                            pendingFiles = pendingFiles + (android.net.Uri.fromFile(dest) to "$safeName.apk [应用: $appName ($packageName)]")
                        }
                    } catch (_: Exception) {}
                },
                onDismiss = { showAppPicker = false }
            )
        }
    }

    // 新消息到达后贴到底部：群聊的阅读方向永远是「最新的在下面」。
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val members = remember(team) { team?.members.orEmpty() }

    Scaffold(
        containerColor = settingsPageBackground(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = team?.name ?: stringResource(R.string.team_chat_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (members.isNotEmpty()) {
                            Text(
                                text = members.joinToString("、") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageBackground(),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(FeatherIcons.ArrowLeft, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                FeatherIcons.MoreHorizontal,
                                contentDescription = stringResource(R.string.team_chat_more),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.team_chat_members)) },
                                onClick = { showMenu = false; showMembers = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.team_chat_edit)) },
                                onClick = { showMenu = false; onEditTeam() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.team_chat_auto_start)) },
                                onClick = { onToggleAutoStart(!autoStart) },
                                trailingIcon = {
                                    androidx.compose.material3.Switch(
                                        checked = autoStart,
                                        onCheckedChange = { onToggleAutoStart(it) }
                                    )
                                }
                            )
                            if (messages.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.team_chat_clear)) },
                                    onClick = { showMenu = false; onClearHistory() }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    EmptyGroupHint(ownerName = team?.ownerName.orEmpty())
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = Spacing.lg,
                            vertical = Spacing.md
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            MessageRow(
                                message = message,
                                ownerName = team?.ownerName.orEmpty(),
                                team = team,
                                onOpenExecution = onOpenExecution
                            )
                        }
                        // 流式气泡：成员正在说的话实时长出来，读完再落库。
                        streaming?.let { current ->
                            item(key = "streaming") {
                                BubbleRow(
                                    speaker = current.speaker,
                                    avatar = current.avatar,
                                    content = current.text,
                                    isOwner = false,
                                    streaming = true,
                                    images = emptyList()
                                )
                            }
                        }
                    }
                }
            }

            if (canStartWork) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notice.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onStartWork) {
                        Text(stringResource(R.string.team_chat_start_work))
                    }
                }
            }
            if (!canStartWork) notice?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                )
            }

            error?.let { text ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.common_cancel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = Spacing.sm)
                            .clickable { onDismissError() }
                    )
                }
            }

            // 执行进度：群里能看到谁在干活，也能就地停掉
            if (executingMembers.isNotEmpty()) {
                val lead = executingMembers.first()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = if (executingMembers.size > 1) {
                            "「$lead」等 ${executingMembers.size} 个执行中，结果会回到群里"
                        } else {
                            "「$lead」正在执行，结果会回到群里"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onStopExecution) {
                        Text(stringResource(R.string.team_chat_stop))
                    }
                }
            }

            if (sending) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = stringResource(R.string.team_chat_thinking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 与普通对话同款输入容器：上方输入、底下一排工具，发送/停止在右下角。
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                shape = RoundedCornerShape(Radius.xl),
                color = MaterialTheme.semanticColors.cardSurface
            ) {
                Column(modifier = Modifier.padding(Spacing.sm)) {
                    if (pendingFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            pendingFiles.forEachIndexed { index, (_, name) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Radius.sm))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { pendingFiles = pendingFiles.filterIndexed { i, _ -> i != index } }
                                        .padding(horizontal = Spacing.sm, vertical = 4.dp)
                                ) {
                                    Icon(
                                        FeatherIcons.FileText,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (pendingImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = Spacing.xs, bottom = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                        ) {
                            pendingImages.forEachIndexed { index, uri ->
                                Box {
                                    LocalUriThumb(uri = uri, size = 52.dp)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(18.dp)
                                            .background(
                                                MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                                                androidx.compose.foundation.shape.CircleShape
                                            )
                                            .clickable {
                                                pendingImages = pendingImages.filterIndexed { i, _ -> i != index }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("×", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                                    }
                                }
                            }
                        }
                    }
                    AppTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = stringResource(R.string.team_chat_input_hint),
                        singleLine = false,
                        minLines = 1,
                        maxLines = 4
                    )
                    // 工具行与普通对话对齐：左侧模型胶囊，右侧成员数与发送/停止圆钮。
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.xs, end = Spacing.xs, top = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.team_chat_member_count, members.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        // 附件：发图给成员
                        IconButton(
                            enabled = pendingImages.size < 9,
                            onClick = { showAttachSheet = true }
                        ) {
                            Icon(
                                FeatherIcons.Plus,
                                contentDescription = stringResource(R.string.team_chat_add_image),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val canSend = (input.isNotBlank() || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) && !sending
                        if (sending) {
                            FilledIconButton(
                                onClick = onStop,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    FeatherIcons.Square,
                                    contentDescription = stringResource(R.string.team_chat_stop),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            FilledIconButton(
                                enabled = canSend,
                                onClick = {
                                    val text = input.trim()
                                    if (text.isNotEmpty() || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) {
                                        onSend(text, pendingImages, pendingFiles.map { it.first })
                                        input = ""
                                        pendingImages = emptyList()
                                        pendingFiles = emptyList()
                                    }
                                }
                            ) {
                                Icon(
                                    FeatherIcons.ArrowUp,
                                    contentDescription = stringResource(R.string.team_chat_send),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 一条消息：群主靠右，成员靠左带头像。 */
@Composable
private fun MessageRow(
    message: TeamChatMessageEntity,
    ownerName: String,
    team: TeamDefinition?,
    onOpenExecution: (String) -> Unit
) {
    val isOwner = message.role == ROLE_OWNER
    // 成员改过名时，历史消息里存的是旧名：按当前成员表反查一次，让同一个人始终显示同一个称呼。
    // 成员已被删除时查不到，回落到消息里存的旧名。
    val currentName = remember(message.speaker, team) {
        team?.members?.firstOrNull { member ->
            member.name.equals(message.speaker, ignoreCase = true) ||
                member.agent.equals(message.speaker, ignoreCase = true) ||
                member.model.equals(message.speaker, ignoreCase = true)
        }?.name
    }
    val name = currentName ?: message.speaker.ifBlank { if (isOwner) ownerName else "成员" }
    Column {
        BubbleRow(
            speaker = name,
            avatar = message.avatar,
            content = message.content,
            isOwner = isOwner,
            streaming = false,
            images = message.imagePaths.split('\n').mapNotNull { it.trim().ifBlank { null } },
            timeLabel = remember(message.createdAt) {
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(message.createdAt))
            }
        )
        // 执行类消息：给一条进执行现场的入口，能看到它每一步读了什么改了什么
        if (message.refSessionId.isNotBlank()) {
            Text(
                text = stringResource(R.string.team_chat_open_execution),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = if (isOwner) 0.dp else 38.dp, top = 2.dp, end = if (isOwner) 38.dp else 0.dp)
                    .clickable { onOpenExecution(message.refSessionId) }
            )
        }
    }
}

/** 气泡本体：群聊消息与流式输出共用，保证两种状态视觉一致。 */
@Composable
private fun BubbleRow(
    speaker: String,
    avatar: String,
    content: String,
    isOwner: Boolean,
    streaming: Boolean,
    images: List<String>,
    timeLabel: String = ""
) {
    val messageImagesFromRow = images
    val name = speaker
    val tint = if (isOwner) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwner) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isOwner) {
            MemberAvatar(avatar = avatar, name = name, tint = tint, size = 30.dp)
            Spacer(Modifier.width(Spacing.sm))
        }
        Column(
            horizontalAlignment = if (isOwner) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (timeLabel.isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            Spacer(Modifier.size(2.dp))
            Surface(
                shape = RoundedCornerShape(
                    topStart = if (isOwner) Radius.lg else Radius.sm,
                    topEnd = if (isOwner) Radius.sm else Radius.lg,
                    bottomStart = Radius.lg,
                    bottomEnd = Radius.lg
                ),
                color = if (isOwner) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.semanticColors.cardSurface
                }
            ) {
                // 正文走 Markdown 渲染，与普通对话一致（代码块、列表、加粗都正常显示）。
                Column {
                    MessageImages(paths = messageImagesFromRow)
                    MarkdownContent(
                        text = content,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                        compact = true
                    )
                    if (streaming) {
                        Text(
                            text = "▍",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = Spacing.md, bottom = Spacing.xs)
                        )
                    }
                }
            }
        }
        if (isOwner) {
            Spacer(Modifier.width(Spacing.sm))
            MemberAvatar(avatar = avatar, name = name, tint = tint, size = 30.dp)
        }
    }
}

@Composable
private fun MembersDialog(team: TeamDefinition?, onDismiss: () -> Unit) {
    val members = team?.members.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.team_chat_members)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                members.forEachIndexed { index, member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MemberAvatar(
                            avatar = member.avatar,
                            name = member.name,
                            tint = MaterialTheme.colorScheme.tertiary,
                            size = 30.dp
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column {
                            Text(member.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                member.sourceLabel(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_confirm)) }
        }
    )
}

@Composable
private fun EmptyGroupHint(ownerName: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.team_chat_empty),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = stringResource(R.string.team_chat_empty_hint, ownerName.ifBlank { "你" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 已选待发送的图片缩略图（直接读 content uri）。 */
@Composable
private fun LocalUriThumb(uri: android.net.Uri, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
        bitmap = loaded
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bitmap?.let { bmp ->
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** 消息气泡里的图片（本地路径，发送时已落盘）。 */
@Composable
private fun MessageImages(paths: List<String>) {
    if (paths.isEmpty()) return
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        paths.forEach { path ->
            val file = remember(path) { com.vantacode.core.ui.resolveAvatarFile(context, path) }
            var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(path) {
                val loaded = withContext(Dispatchers.IO) {
                    runCatching { file?.let { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() } }.getOrNull()
                }
                bitmap = loaded
            }
            bitmap?.let { bmp ->
                androidx.compose.foundation.Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                )
            }
        }
    }
}

private const val ROLE_OWNER = "owner"

