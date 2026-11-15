package com.vantacode.feature.agent.domain.team

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.data.local.dao.LlmCallRecordDao
import com.vantacode.feature.agent.data.remote.anthropic.AnthropicApi
import com.vantacode.feature.agent.data.remote.gemini.GeminiApi
import com.vantacode.feature.agent.data.remote.openai.OpenAIApi
import com.vantacode.feature.agent.domain.model.AgentMessage
import com.vantacode.feature.agent.domain.provider.AIProvider
import com.vantacode.feature.agent.domain.provider.AnthropicAdapter
import com.vantacode.feature.agent.domain.provider.GeminiAdapter
import com.vantacode.feature.agent.domain.provider.OpenAIAdapter
import com.vantacode.feature.agent.domain.provider.toReasoningCapability
import kotlinx.coroutines.flow.first
import com.vantacode.feature.agent.domain.subagent.AgentDefinitionRepository
import com.vantacode.feature.settings.data.remote.ModelMetadataService
import com.vantacode.feature.settings.domain.repository.AIProviderRepository
import com.vantacode.feature.settings.data.repository.GeneralSettingsRepository
import com.vantacode.feature.settings.data.repository.ProviderKeyRotator
import com.vantacode.feature.settings.domain.model.AIProviderConfig
import com.vantacode.feature.settings.domain.model.ProviderType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 团队群聊运行器：让团队成员共享同一份发言记录、轮流发言，彼此看得见对方说过什么。
 *
 * 与子代理派发（`task(create)`）的区别：子代理各自独立上下文、互不可见，只向主会话汇报；
 * 这里是同一个「群」，第 N 个发言者读到的是前面所有发言，可以回应、质疑、纠正。
 *
 * 每轮按成员顺序各发言一次，默认两轮（表态 + 互相回应）。成员的能力（模型、提示词）
 * 来自各自的子代理定义；群聊阶段不挂工具——成员只说，不动手。
 */
@Singleton
class TeamChatRunner @Inject constructor(
    private val aiProviderRepository: AIProviderRepository,
    private val anthropicApi: AnthropicApi,
    private val openAIApi: OpenAIApi,
    private val geminiApi: GeminiApi,
    private val keyRotator: ProviderKeyRotator,
    private val modelMetadataService: ModelMetadataService,
    private val generalSettingsRepository: GeneralSettingsRepository,
    private val agentDefinitionRepository: AgentDefinitionRepository,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val llmCallRecordDao: LlmCallRecordDao
) {
    companion object {
        private const val TAG = "TeamChatRunner"
        const val DEFAULT_ROUNDS = 2
        const val MAX_ROUNDS = 4
        private const val MIN_TURNS_TO_ACCEPT = 1
    }

    data class Turn(
        val round: Int,
        val speaker: String,
        val avatar: String,
        val content: String
    )

    /** 一个已经解析好、可以发言的成员。 */
    private data class Speaker(
        val displayName: String,
        val prompt: String,
        val avatar: String,
        val providerId: String?,
        val model: String?
    )

    data class Result(
        val turns: List<Turn>,
        val warnings: List<String>,
        /** 讨论是否已经收敛（自动多轮模式下由主持人判定）。 */
        val converged: Boolean = false,
        /** 收敛时主持人给出的方案（谁做什么、验收标准）；未收敛为 null。 */
        val plan: String? = null
    )

    /**
     * 跑一场群聊。
     *
     * @param fallbackProviderId 成员定义未指定 provider 时继承的主会话 provider
     * @param fallbackModel 成员定义未指定 model 时继承的主会话模型
     */
    suspend fun run(
        team: TeamDefinition,
        task: String,
        rounds: Int,
        fallbackProviderId: String?,
        fallbackModel: String?,
        sessionId: String?,
        history: List<Turn> = emptyList(),
        onDelta: (speaker: String, delta: String) -> Unit = { _, _ -> },
        /** 每产生一条新发言就回调一次：调用方据此逐条落库，成员说完即可见，不必等整轮结束。 */
        onTurn: suspend (Turn) -> Unit = {},
        reasoningEffort: String? = null,
        images: List<com.vantacode.feature.agent.domain.model.AgentImage> = emptyList(),
        /** 自动多轮：每轮结束让主持人判断是否已形成可执行方案，收敛就提前停，不必用户催。 */
        autoContinue: Boolean = false
    ): kotlin.Result<Result> {
        val warnings = mutableListOf<String>()
        val speakers = team.members.mapNotNull { member -> resolveSpeaker(member, warnings) }
        if (speakers.isEmpty()) {
            return kotlin.Result.failure(
                IllegalStateException(
                    "团队「${team.name}」没有可发言的成员" + warnings.firstOrNull()?.let { "：$it" }.orEmpty()
                )
            )
        }

        val totalRounds = rounds.coerceIn(1, MAX_ROUNDS)
        val historySize = history.size
        val turns = history.toMutableList()
        val firstRound = (history.maxOfOrNull { it.round } ?: 0) + 1
        // 记录每个成员解析失败的 provider，避免每轮重复报错刷屏。
        val broken = mutableSetOf<String>()

        for (offset in 0 until totalRounds) {
            val round = firstRound + offset
            for (speaker in speakers) {
                if (speaker.displayName in broken) continue
                val reply = runCatching {
                    speak(
                        team = team,
                        speaker = speaker,
                        task = task,
                        turns = turns,
                        round = round,
                        fallbackProviderId = fallbackProviderId,
                        fallbackModel = fallbackModel,
                        sessionId = sessionId,
                        onDelta = onDelta,
                        reasoningEffort = reasoningEffort,
                        images = images
                    )
                }.getOrElse { e ->
                    FileLogger.w(TAG, "成员发言失败: ${speaker.displayName}", e)
                    broken += speaker.displayName
                    warnings += "${speaker.displayName} 发言失败，已退出本场群聊：${e.message ?: e.javaClass.simpleName}"
                    null
                }
                if (reply != null && reply.isNotBlank()) {
                    val turn = Turn(
                        round = round,
                        speaker = speaker.displayName,
                        // 头像定稿：没设头像的模型成员用自家上游 logo，历史消息也能显示
                        avatar = speaker.avatar.ifBlank {
                            speaker.model?.takeIf { it.isNotBlank() }?.let { modelName -> "logo:" + modelName }.orEmpty()
                        },
                        content = reply.trim()
                    )
                    turns += turn
                    runCatching { onTurn(turn) }
                }
            }
            if (turns.isEmpty() && round >= 2) break
            if (broken.size == speakers.size) break
            // 自动多轮：问一句主持人「定了没有」，定了就不再消耗下一轮
            if (autoContinue && offset < totalRounds - 1) {
                val plan = runCatching {
                    judgeConverged(team, turns, fallbackProviderId, fallbackModel)
                }.getOrNull()
                if (plan != null) {
                    return kotlin.Result.success(
                        Result(
                            turns = turns.drop(historySize),
                            warnings = warnings,
                            converged = true,
                            plan = plan
                        )
                    )
                }
            }
        }

        if (turns.size < MIN_TURNS_TO_ACCEPT) {
            return kotlin.Result.failure(
                IllegalStateException(
                    "团队「${team.name}」没有产生任何发言" + warnings.firstOrNull()?.let { "：$it" } .orEmpty()
                )
            )
        }
        // 只回传本次新增的发言：turns 里前面是传进来的 history，原样返回会让调用方
        // 把历史重新插一遍，消息量按轮次指数膨胀。
        return kotlin.Result.success(Result(turns = turns.drop(historySize), warnings = warnings))
    }

    /**
     * 把团队成员解析成可发言的 [Speaker]：引用子代理的取定义里的模型与提示词，
     * 直接拉模型的用成员自己的 provider/model/prompt。
     */
    private fun resolveSpeaker(member: TeamMember, warnings: MutableList<String>): Speaker? {
        if (member.usesAgent) {
            val definition = agentDefinitionRepository.findIncludingDisabled(member.agent)
            if (definition == null) {
                warnings += "${member.name} 引用的子代理「${member.agent}」不存在，已跳过"
                return null
            }
            return Speaker(
                displayName = member.name,
                prompt = definition.prompt,
                avatar = member.avatar,
                providerId = definition.providerId,
                model = definition.model
            )
        }
        if (member.model.isBlank()) {
            warnings += "${member.name} 没有指定模型，已跳过"
            return null
        }
        return Speaker(
            displayName = member.name,
            prompt = member.prompt.ifBlank { "你是「${member.name}」，就讨论的问题给出你自己的判断。" },
            avatar = member.avatar,
            providerId = member.providerId.ifBlank { null },
            model = member.model
        )
    }

    /** 让单个成员基于当前群聊记录发一次言。 */
    private suspend fun speak(
        team: TeamDefinition,
        speaker: Speaker,
        task: String,
        turns: List<Turn>,
        round: Int,
        fallbackProviderId: String?,
        fallbackModel: String?,
        sessionId: String?,
        onDelta: (speaker: String, delta: String) -> Unit,
        reasoningEffort: String?,
        images: List<com.vantacode.feature.agent.domain.model.AgentImage>
    ): String {
        val speakerName = speaker.displayName
        val provider = buildProvider(speaker.providerId ?: fallbackProviderId, speaker.model ?: fallbackModel)
        val systemPrompt = buildSpeakerPrompt(team, speakerName, speaker.prompt)
        val message = AgentMessage.UserMessage(
            content = buildRoundMessage(team, task, turns, round, speakerName),
            images = images
        )
        val started = System.currentTimeMillis()
        // 流式输出：成员说话像正常对话一样逐字出现，而不是等整段生成完再一次性弹出。
        val buffer = StringBuilder()
        var response: com.vantacode.feature.agent.domain.provider.AIResponse? = null
        provider.completeStream(
            systemPrompt = systemPrompt,
            messages = listOf(message),
            tools = emptyList(),
            reasoningEffort = reasoningEffort
        ).collect { chunk ->
            when (chunk) {
                is com.vantacode.feature.agent.domain.provider.AIStreamChunk.TextDelta -> {
                    if (chunk.text.isNotEmpty()) {
                        buffer.append(chunk.text)
                        onDelta(speakerName, chunk.text)
                    }
                }
                is com.vantacode.feature.agent.domain.provider.AIStreamChunk.Final -> {
                    response = chunk.response
                }
                else -> Unit
            }
        }
        val finalResponse = response ?: throw IllegalStateException("模型没有返回最终结果")
        recordCall(sessionId, provider, finalResponse, started)
        if (finalResponse.isAborted) {
            throw IllegalStateException("模型中止了本次回复（${finalResponse.stopReason ?: "unknown"}）")
        }
        return buffer.toString().ifBlank { finalResponse.content }
    }

    /**
     * 让一个中立的判断者看一遍记录，决定是否已经能开工。
     *
     * 判据只有一个：是否已经明确到「谁做什么、交付什么」。宁可多聊一轮，也不要半成品就开工。
     */
    private suspend fun judgeConverged(
        team: TeamDefinition,
        turns: List<Turn>,
        fallbackProviderId: String?,
        fallbackModel: String?
    ): String? {
        val provider = buildProvider(fallbackProviderId, fallbackModel)
        val transcript = turns.joinToString("\n") { turn -> turn.speaker + "：" + turn.content }
        val instruction = "「" + team.name + "」的讨论记录：\n" + transcript +
            "\n\n判断标准：是否已经明确到「谁负责做什么、交付什么」，且没有未决的关键分歧。\n" +
            "如果已收敛：第一行输出 CONVERGED，随后用 3-6 行写出最终方案（谁负责什么、验收标准）。\n" +
            "如果没收敛：只输出一行，以 CONTINUE 开头，跟一句还差什么。"
        val response = provider.complete(
            systemPrompt = "你是会议主持。只判断讨论是否已经收敛到可以立刻开工的具体方案，不做别的。",
            messages = listOf(AgentMessage.UserMessage(content = instruction)),
            tools = emptyList()
        )
        val text = response.content.trim()
        if (!text.startsWith("CONVERGED", ignoreCase = true)) return null
        return text.lineSequence()
            .drop(1)
            .joinToString("\n")
            .trim()
            .ifBlank { "（主持人未给出细节，按上面这轮讨论的结论执行）" }
    }

    /**
     * 读全局人格片段：优先 `~/.aicode/prompts.custom/00-identity.md`（用户自定义），
     * 没有就用包内 `assets/prompts/00-identity.md`。直接读文件而不经依赖注入，
     * 避免团队模块把整个提示词装配链拖进来。
     */
    private fun readIdentity(): String? {
        val custom = java.io.File(java.io.File(context.filesDir, "aicode/prompts.custom"), "00-identity.md")
        if (custom.isFile) return custom.readText()
        return runCatching {
            context.assets.open("prompts/00-identity.md").bufferedReader().use { it.readText() }
        }.getOrNull()
    }

    /**
     * 成员的系统提示词：全局人格 + 自身职责 + 团队协作说明 + 群聊行为约束。
     *
     * 人格片段取自 `prompts.custom/00-identity.md`（没有自定义时回落到内置），
     * 保证群里的成员和主会话是同一套价值观与说话方式，而不是一群陌生人。
     */
    private fun buildSpeakerPrompt(
        team: TeamDefinition,
        speakerName: String,
        ownPrompt: String
    ): String = buildString {
        runCatching { readIdentity() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { identity ->
                appendLine(identity)
                appendLine()
            }
        appendLine("你是「${team.name}」团队的成员「$speakerName」，正与其他成员围在同一张桌上讨论。")
        if (team.description.isNotBlank()) appendLine("团队职责：${team.description}")
        appendLine()
        appendLine("你的职责：")
        appendLine(ownPrompt.trim())
        if (team.strategy.isNotBlank()) {
            appendLine()
            appendLine("团队协作约定：")
            appendLine(team.strategy.trim())
        }
        appendLine()
        appendLine(
            """
            群聊规则：
            - 你看得到本场讨论里所有人的发言；轮到你时直接说你要说的，不要复述已达成一致的内容。
            - 可以反对、补充、指出别人漏掉的前提；有分歧就摆出依据，不要客气，也不要和稀泥。
            - 已经能下结论时给出明确结论，别再重复讨论过程。
            - 只发言，不动手：本轮不需要、也不能调用工具或执行命令。
            - 长度控制在 200 字以内；需要贴代码或清单时按必要长度写。
            """.trimIndent()
        )
    }

    /** 组装给当前发言者的群聊记录。 */
    private fun buildRoundMessage(
        team: TeamDefinition,
        task: String,
        turns: List<Turn>,
        round: Int,
        speakerName: String
    ): String = buildString {
        appendLine("【讨论主题】")
        appendLine(task.trim())
        appendLine()
        if (turns.isEmpty()) {
            appendLine("【群聊记录】暂无，你是第一个发言的人。")
        } else {
            appendLine("【群聊记录】")
            var lastRound = -1
            turns.forEach { turn ->
                // round <= 0 表示群聊窗口里的既有历史，不按轮次分组，直接按时间顺序读。
                if (turn.round > 0 && turn.round != lastRound) {
                    appendLine("— 第 ${turn.round} 轮 —")
                    lastRound = turn.round
                }
                appendLine("${turn.speaker}：${turn.content}")
                appendLine()
            }
        }
        appendLine()
        append("现在轮到第 $round 轮的「$speakerName」发言。")
    }

    /** 按成员定义或主会话兜底解析出可用的 provider 实例。 */
    private suspend fun buildProvider(providerIdRaw: String?, model: String?): AIProvider {
        val all = runCatching { aiProviderRepository.getAllProviders().first() }.getOrDefault(emptyList())
        val config = when {
            providerIdRaw.isNullOrBlank() -> null
            else -> all.firstOrNull { it.id == providerIdRaw }
                ?: all.firstOrNull { it.name.equals(providerIdRaw, ignoreCase = true) }
        } ?: throw IllegalStateException("未找到可用的 AI 供应商（$providerIdRaw）")
        if (config.isEnabled.not()) throw IllegalStateException("「${config.name}」已被停用")
        return createProvider(config.copy(selectedModel = model ?: config.effectiveModel))
    }

    private suspend fun createProvider(config: AIProviderConfig): AIProvider {
        val provider: AIProvider = when (config.type) {
            ProviderType.ANTHROPIC -> AnthropicAdapter(anthropicApi).also {
                it.cacheBreakpointsEnabled = config.anthropicCacheBreakpoints
            }
            ProviderType.GEMINI -> GeminiAdapter(geminiApi)
            else -> OpenAIAdapter(openAIApi).also {
                it.chatCacheKeyEnabled = config.openaiChatCacheKey
            }
        }
        provider.apiKey = keyRotator.activeKey(config, null)
            ?: throw IllegalStateException("「${config.name}」的 Key 均在冷却中，请稍后重试")
        provider.baseUrl = config.baseUrl
        provider.model = config.effectiveModel
        provider.useFullUrl = config.useFullUrl
        provider.useResponseApi = config.useResponseApi
        provider.providerId = config.id
        provider.customHeaders = config.customHeaders
        val metadata = modelMetadataService.resolve(config.id, config.type, config.effectiveModel)
        provider.maxOutputTokens = metadata.outputTokens
        provider.reasoningCapability = metadata.toReasoningCapability()
        provider.firstByteTimeoutMs = generalSettingsRepository.firstByteTimeoutMs()
        provider.streamIdleTimeoutMs = generalSettingsRepository.streamIdleTimeoutMs()
        provider.maxNetworkRetries = generalSettingsRepository.maxNetworkRetries()
        return provider
    }

    private suspend fun recordCall(
        sessionId: String?,
        provider: AIProvider,
        response: com.vantacode.feature.agent.domain.provider.AIResponse,
        startedAt: Long
    ) {
        runCatching {
            llmCallRecordDao.insert(
                com.vantacode.feature.agent.data.local.entity.LlmCallRecordEntity(
                    sessionId = sessionId,
                    providerId = provider.providerId.ifBlank { null },
                    model = provider.model,
                    kind = "team_chat",
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    cachedInputTokens = response.cachedInputTokens,
                    cacheCreationTokens = response.cacheCreationTokens,
                    ttfbMillis = null,
                    durationMillis = (System.currentTimeMillis() - startedAt).toInt(),
                    status = "success",
                    errorMessage = null,
                    stopReason = response.stopReason,
                    createdAt = startedAt
                )
            )
        }.onFailure { FileLogger.w(TAG, "记录群聊调用统计失败", it) }
    }
}
