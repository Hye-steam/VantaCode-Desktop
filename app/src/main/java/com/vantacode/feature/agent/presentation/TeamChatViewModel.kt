package com.vantacode.feature.agent.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.data.local.dao.TeamChatDao
import com.vantacode.feature.agent.data.local.entity.TeamChatMessageEntity
import com.vantacode.feature.agent.domain.team.TeamChatRunner
import com.vantacode.feature.agent.domain.team.TeamExecutor
import com.vantacode.feature.agent.domain.subagent.SubAgentEventBus
import com.vantacode.feature.agent.domain.subagent.SubAgentEventType
import com.vantacode.feature.agent.domain.subagent.SubAgentEvent
import com.vantacode.feature.agent.domain.team.TeamExecutionRegistry
import com.vantacode.feature.agent.domain.team.TeamDefinition
import com.vantacode.feature.agent.domain.team.TeamRepository
import com.vantacode.feature.settings.domain.repository.AIProviderRepository
import android.content.Context
import android.net.Uri
import android.util.Base64
import com.vantacode.feature.agent.domain.model.AgentImage
import java.io.File
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 团队群聊窗口的状态：一条按时间排的消息时间线 + 一次「你说一句，成员挨个接话」的往返。
 *
 * 群聊记录按团队名存在独立库里，切走再回来历史还在；成员的头像与名字在发送时定格，
 * 之后改群成员配置不会重写历史消息。
 */
@HiltViewModel
class TeamChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val teamRepository: TeamRepository,
    private val teamChatDao: TeamChatDao,
    private val teamChatRunner: TeamChatRunner,
    private val teamExecutor: TeamExecutor,
    private val subAgentEventBus: SubAgentEventBus,
    private val teamExecutionRegistry: TeamExecutionRegistry,
    private val aiProviderRepository: AIProviderRepository
) : ViewModel() {

    private val _teamName = MutableStateFlow("")
    val teamName: StateFlow<String> = _teamName.asStateFlow()

    private val _team = MutableStateFlow<TeamDefinition?>(null)
    val team: StateFlow<TeamDefinition?> = _team.asStateFlow()

    private val _messages = MutableStateFlow<List<TeamChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<TeamChatMessageEntity>> = _messages.asStateFlow()

    private val _streaming = MutableStateFlow<StreamingTurn?>(null)

    /** 正在逐字输出的那条发言（成员名 + 已输出文本），为 null 表示没有在生成。 */
    val streaming: StateFlow<StreamingTurn?> = _streaming.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)

    /** 中性提示（非错误），例如「讨论已收敛，可以开工」。 */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun dismissNotice() {
        _notice.value = null
    }

    private val _converged = MutableStateFlow(false)

    /** 讨论是否已收敛出可执行方案（收敛后 UI 给一个开工入口）。 */
    val converged: StateFlow<Boolean> = _converged.asStateFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 群聊兜底模型：成员自己没指定模型时用它。空表示跟随第一个可用供应商。 */
    private val _fallbackProviderId = MutableStateFlow<String?>(null)
    val fallbackProviderId: StateFlow<String?> = _fallbackProviderId.asStateFlow()

    private val _fallbackModel = MutableStateFlow<String?>(null)
    val fallbackModel: StateFlow<String?> = _fallbackModel.asStateFlow()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** 讨论收敛后是否自动交给执行者。关掉就只讨论，想开工再点。 */
    private val _autoStart = MutableStateFlow(true)
    val autoStart: StateFlow<Boolean> = _autoStart.asStateFlow()

    init {
        _autoStart.value = prefs.getBoolean(KEY_AUTO_START, true)
        _fallbackProviderId.value = prefs.getString(KEY_PROVIDER, null)
        _fallbackModel.value = prefs.getString(KEY_MODEL, null)
    }

    fun setFallbackModel(providerId: String, model: String) {
        _fallbackProviderId.value = providerId
        _fallbackModel.value = model
        prefs.edit().putString(KEY_PROVIDER, providerId).putString(KEY_MODEL, model).apply()
    }

    private var loadedTeam: String? = null

    init {
        // 终态事件到达就把这个执行移出运行中。状态在单例里，多个 ViewModel 实例共享同一份。
        viewModelScope.launch {
            subAgentEventBus.events.collect { event ->
                when (event.type) {
                    SubAgentEventType.COMPLETED,
                    SubAgentEventType.FAILED,
                    SubAgentEventType.STOPPED -> teamExecutionRegistry.markFinished(event.subSessionId)
                    else -> Unit
                }
            }
        }
    }

    fun setAutoStart(enabled: Boolean) {
        _autoStart.value = enabled
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
    private var currentJob: kotlinx.coroutines.Job? = null

    /** 正在执行的任务（群里显示进度用）。状态来自进程级登记簿，退出群聊再进来不会丢。 */
    val executing: StateFlow<List<TeamExecutionRegistry.ActiveExecution>> =
        kotlinx.coroutines.flow.combine(
            _teamName,
            teamExecutionRegistry.active
        ) { name: String, active: Map<String, TeamExecutionRegistry.ActiveExecution> ->
            if (name.isBlank()) {
                emptyList()
            } else {
                active.values.filter { it.teamName == name }
            }
        }.stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            emptyList<TeamExecutionRegistry.ActiveExecution>()
        )

    /** 上次派发的任务与时间：短时间内相同任务不重复派。 */
    private var lastDispatchedTask: String? = null
    private var lastDispatchedAt: Long = 0L

    /** 承载执行的父会话 id（从群聊来源会话带过来）。 */
    private var parentSessionId: String? = null

    /** 打开某个团队的群聊；重复调用同一团队不重复订阅。 */
    fun open(name: String, parentSessionId: String? = null) {
        this.parentSessionId = parentSessionId
        if (loadedTeam == name) {
            _teamName.value = name
            return
        }
        loadedTeam = name
        _teamName.value = name
        _team.value = teamRepository.find(name)
        viewModelScope.launch {
            runCatching { teamChatDao.trim(name, MAX_MESSAGES) }
        }
        viewModelScope.launch {
            teamChatDao.observeMessages(name).collect { list ->
                _messages.value = list
                refreshExecutingFromHistory(list)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    /** 用户说一句话：先落到时间线，再让成员依次回应。 */
    fun send(text: String, imageUris: List<Uri> = emptyList(), fileUris: List<Uri> = emptyList()) {
        val content = text.trim()
        val name = _teamName.value
        if ((content.isEmpty() && imageUris.isEmpty() && fileUris.isEmpty()) || name.isEmpty() || _sending.value) return
        val definition = _team.value ?: teamRepository.find(name)
        if (definition == null) {
            _error.value = "团队定义已不存在：$name"
            return
        }

        currentJob = viewModelScope.launch {
            _sending.value = true
            _converged.value = false
            _error.value = null
            try {
                // 文件按文本读进来拼在消息前面：成员看到的是文件内容，不是一串二进制。
                val fileBlocks = fileUris.mapNotNull { uri -> runCatching { readFileText(uri) }.getOrNull() }
                val composed = buildString {
                    fileBlocks.forEach { (fileName, body) ->
                        appendLine("[附件：$fileName]")
                        appendLine(body)
                        appendLine()
                    }
                    append(content)
                }
                // 图片先落到私有目录再发：content:// 的临时授权活不过这次会话。
                val storedPaths = imageUris.mapNotNull { uri -> runCatching { importImage(uri) }.getOrNull() }
                val agentImages = storedPaths.mapNotNull { path -> toAgentImage(path) }
                teamChatDao.insert(
                    TeamChatMessageEntity(
                        teamName = name,
                        speaker = definition.ownerName,
                        avatar = definition.ownerAvatar,
                        role = ROLE_OWNER,
                        content = composed,
                        imagePaths = encodePaths(storedPaths)
                    )
                )

                // 已经有执行在跑：把这句话直接转给它，让它当场吸收中途的修改，
                // 而不是等这一轮讨论结束、再派下一轮才看到。
                executing.value.firstOrNull()?.let { active ->
                    subAgentEventBus.emit(
                        SubAgentEvent(
                            subSessionId = active.subSessionId,
                            parentSessionId = parentSessionId.orEmpty(),
                            type = SubAgentEventType.MESSAGE_FROM_PARENT,
                            detail = content
                        )
                    )
                }

                // 既有时间线作为群聊上下文交给成员；round = 0 表示历史，不按轮分组。
                val history = _messages.value
                    .map { message ->
                        TeamChatRunner.Turn(
                            round = 0,
                            speaker = message.speaker,
                            avatar = message.avatar,
                            content = message.content
                        )
                    }

                val allProviders = runCatching { aiProviderRepository.getAllProviders().first() }
                    .getOrDefault(emptyList())
                val picked = allProviders.firstOrNull { it.id == _fallbackProviderId.value }
                    ?.takeIf { it.isEnabled }
                val fallback = picked ?: allProviders.firstOrNull { it.isEnabled }
                val fallbackModelValue = if (picked != null) {
                    _fallbackModel.value ?: picked.selectedModel?.ifBlank { null } ?: picked.models.firstOrNull()
                } else {
                    fallback?.selectedModel?.ifBlank { null } ?: fallback?.models?.firstOrNull()
                }
                val result = teamChatRunner.run(
                    team = definition,
                    task = content,
                    rounds = 3,
                    autoContinue = true,
                    fallbackProviderId = fallback?.id,
                    fallbackModel = fallbackModelValue,
                    sessionId = null,
                    history = history,
                    reasoningEffort = null,
                    images = agentImages,
                    onTurn = { turn ->
                        teamChatDao.insert(
                            TeamChatMessageEntity(
                                teamName = name,
                                speaker = turn.speaker,
                                avatar = turn.avatar,
                                role = ROLE_MEMBER,
                                content = turn.content
                            )
                        )
                        _streaming.value = null
                    },
                    onDelta = { speaker, delta ->
                        val avatar = definition.members.firstOrNull { it.name == speaker }?.avatar.orEmpty()
                        val current = _streaming.value
                        _streaming.value = if (current != null && current.speaker == speaker) {
                            current.copy(text = current.text + delta)
                        } else {
                            StreamingTurn(speaker = speaker, avatar = avatar, text = delta)
                        }
                    }
                )
                _streaming.value = null
                result.onSuccess { payload ->
                    val warnings = payload.warnings
                    _converged.value = payload.converged
                    // 收敛时把主持人给的方案单独落一条，群里能直接看到「定的是什么」
                    payload.plan?.takeIf { it.isNotBlank() }?.let { plan ->
                        runCatching {
                            teamChatDao.insert(
                                TeamChatMessageEntity(
                                    teamName = name,
                                    speaker = "主持人",
                                    avatar = "",
                                    role = ROLE_MEMBER,
                                    content = "[方案]\n$plan"
                                )
                            )
                        }
                    }
                    _notice.value = null
                    if (warnings.isNotEmpty()) _error.value = warnings.joinToString("\n")
                    // 收敛即开工：不让用户再喊一遍「开工」
                    if (payload.converged) {
                        if (_autoStart.value) {
                            startWork(content)
                        } else {
                            _notice.value = "讨论已收敛，点下方「开工」交给执行者"
                        }
                    }
                }.onFailure { e ->
                    FileLogger.w(TAG, "群聊失败", e)
                    _error.value = e.message ?: "群聊失败"
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户按了停止：把已经流出的半句留成一条消息，别让说过的话凭空消失。
                val partial = _streaming.value
                if (partial != null && partial.text.isNotBlank()) {
                    runCatching {
                        teamChatDao.insert(
                            TeamChatMessageEntity(
                                teamName = name,
                                speaker = partial.speaker,
                                avatar = partial.avatar,
                                role = ROLE_MEMBER,
                                content = partial.text
                            )
                        )
                    }
                }
                throw e
            } finally {
                _streaming.value = null
                _sending.value = false
            }
        }
    }

    /** 流式输出的临时气泡。 */
    data class StreamingTurn(val speaker: String, val avatar: String, val text: String)

    /**
     * 收敛后把活交给主执行者。
     *
     * 没有父会话（比如刚装完还没进过会话）时只提示，不硬派——派发要有一条会话承载执行。
     */
    fun startWork(task: String) {
        // 同一句话在短时间内重复收敛时只派一次，否则会同时跑起多个一模一样的执行
        val normalized = task.trim().ifBlank { "按群里讨论的结论执行" }
        val now = System.currentTimeMillis()
        if (normalized == lastDispatchedTask && now - lastDispatchedAt < 30_000) return
        lastDispatchedTask = normalized
        lastDispatchedAt = now
        val name = _teamName.value
        val definition = _team.value ?: teamRepository.find(name) ?: return
        val parentId = parentSessionId
        if (parentId.isNullOrBlank()) {
            _error.value = "没有可承载执行的会话：先在聊天页开一个会话，再回来开工"
            return
        }
        viewModelScope.launch {
            _sending.value = true
            // 把群聊最近的结论一起交给执行者：只传用户最初那句话，执行者会拿旧上下文干活，
            // 群里后来纠正的方向它根本看不到。
            val recentConclusion = _messages.value.takeLast(12)
                .joinToString("\n") { message -> message.speaker + "：" + message.content.take(300) }
            val enrichedTask = buildString {
                appendLine(normalized)
                if (recentConclusion.isNotBlank()) {
                    appendLine()
                    appendLine("【群聊最新讨论】")
                    appendLine(recentConclusion)
                }
            }
            teamExecutor.execute(definition, enrichedTask, parentId)
                .onFailure { e ->
                    FileLogger.w(TAG, "团队成员执行派发失败", e)
                    _error.value = e.message ?: "执行派发失败"
                }
            _sending.value = false
        }
    }

    /** 停止本轮生成：已流出的内容保留成一条消息，后面的成员不再开口。 */
    fun stop() {
        currentJob?.cancel()
        currentJob = null
    }

    /** 把附件文件读成文本（超长截断）：群聊里成员只能理解文本，不能执行文件。 */
    private fun readFileText(uri: Uri): Pair<String, String>? {
        val resolver = context.contentResolver
        val fileName = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment ?: "附件"
        val text = resolver.openInputStream(uri)?.use { input ->
            input.bufferedReader().use { reader -> reader.readText().take(MAX_FILE_CHARS) }
        } ?: return null
        if (text.isBlank() || text.any { it.code == 0 }) return null
        return fileName to text
    }

    /** 把选中的图片复制进 `~/.aicode/team_chat_images/`，返回容器路径。 */
    private fun importImage(uri: Uri): String? {
        val dir = File(File(context.filesDir, "aicode/team_chat_images").apply { mkdirs() }, "${java.util.UUID.randomUUID()}.img")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dir.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return "~/.aicode/team_chat_images/${dir.name}"
    }

    /** 读本地图片转成模型可用的 [AgentImage]（base64 + mime）。 */
    private fun toAgentImage(path: String): AgentImage? = runCatching {
        val file = resolveLocalFile(path) ?: return@runCatching null
        val bytes = file.readBytes()
        AgentImage(
            mimeType = guessMime(bytes),
            base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
            path = path
        )
    }.getOrNull()

    private fun resolveLocalFile(path: String): File? = when {
        path.startsWith("~/.aicode/") -> File(File(context.filesDir, "aicode"), path.removePrefix("~/.aicode/"))
        path.startsWith("/") -> File(path)
        else -> null
    }?.takeIf { it.isFile }

    private fun guessMime(bytes: ByteArray): String = when {
        bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "image/png"
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes.size > 12 && String(bytes, 0, 4) == "RIFF" -> "image/webp"
        else -> "image/jpeg"
    }

    private fun encodePaths(paths: List<String>): String =
        if (paths.isEmpty()) "" else paths.joinToString("\n")

    /**
     * 从历史推导「正在执行」状态。
     *
     * 退出群聊再进来时 ViewModel 是新建的，内存里的执行状态全丢；靠消息本身能还原：
     * 最后一条带 refSessionId 的「[执行]」若还没等到同 id 的「[执行结果]/[执行失败]」，
     * 就说明它还在跑。这样重进后进度条和停止按钮自动回来。
     */
    private fun refreshExecutingFromHistory(messages: List<TeamChatMessageEntity>) {
        val teamName = _teamName.value
        if (teamName.isBlank()) return
        // 进程重启后登记簿是空的：把「有 [执行] 但没等到结果」的补回运行中状态
        messages.forEach { message ->
            if (!message.content.startsWith("[执行] ") || message.refSessionId.isBlank()) return@forEach
            val finished = messages.any { later ->
                later.id > message.id && later.refSessionId == message.refSessionId &&
                    (later.content.startsWith("[执行结果]") ||
                        later.content.startsWith("[执行失败]") ||
                        later.content.startsWith("[执行已停止]"))
            }
            if (finished) {
                teamExecutionRegistry.markFinished(message.refSessionId)
            } else {
                teamExecutionRegistry.restoreActive(
                    subSessionId = message.refSessionId,
                    teamName = teamName,
                    memberName = message.speaker,
                    avatar = message.avatar
                )
            }
        }
    }

    /** 收敛后手动开工：任务取最近一条群主发言。 */
    fun startFromConverged() {
        val task = _messages.value.lastOrNull { it.role == ROLE_OWNER }?.content.orEmpty()
        _notice.value = null
        _converged.value = false
        startWork(task)
    }

    /** 停止当前执行（群里点停止时调用）。 */
    fun stopExecution() {
        val current = executing.value.firstOrNull() ?: return
        val parentId = parentSessionId ?: return
        subAgentEventBus.emit(
            SubAgentEvent(
                subSessionId = current.subSessionId,
                parentSessionId = parentId,
                type = SubAgentEventType.STOPPED
            )
        )
    }

    fun clearHistory() {
        val name = _teamName.value
        if (name.isEmpty()) return
        viewModelScope.launch { teamChatDao.clear(name) }
    }

    private companion object {
        const val TAG = "TeamChatViewModel"
        const val PREFS_NAME = "team_chat_prefs"
        const val KEY_PROVIDER = "fallback_provider"
        const val KEY_MODEL = "fallback_model"
        const val KEY_AUTO_START = "auto_start"
        /** 单个群保留的最大消息数，超出后裁掉最旧的。 */
        const val MAX_MESSAGES = 500
        const val MAX_FILE_CHARS = 20_000
        const val ROLE_OWNER = "owner"
        const val ROLE_MEMBER = "member"
    }
}
