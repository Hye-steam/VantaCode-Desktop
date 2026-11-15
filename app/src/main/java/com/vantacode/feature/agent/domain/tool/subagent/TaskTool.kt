package com.vantacode.feature.agent.domain.tool.subagent

import android.content.Context
import com.vantacode.R
import com.vantacode.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import com.vantacode.feature.agent.data.local.dao.AgentMessageDao
import com.vantacode.feature.agent.data.local.dao.ChatSessionDao
import com.vantacode.feature.agent.domain.model.AgentContext
import com.vantacode.feature.agent.domain.model.AgentMode
import com.vantacode.feature.agent.domain.model.ReasoningEffort
import com.vantacode.feature.agent.domain.session.SessionUseCase
import com.vantacode.feature.agent.domain.subagent.AgentDefinition
import com.vantacode.feature.agent.domain.subagent.InjectPart
import com.vantacode.feature.agent.domain.subagent.AgentDefinitionRepository
import com.vantacode.feature.agent.domain.subagent.SubAgentEvent
import com.vantacode.feature.agent.domain.subagent.SubAgentEventBus
import com.vantacode.feature.agent.domain.subagent.SubAgentEventType
import com.vantacode.feature.agent.domain.subagent.ResolvedSubagentModel
import com.vantacode.feature.agent.domain.subagent.SubagentModelFallback
import com.vantacode.feature.agent.domain.subagent.SubagentModelResolver
import com.vantacode.feature.agent.domain.subagent.SubagentModelTier
import com.vantacode.feature.agent.domain.subagent.SubagentTierSelection
import com.vantacode.feature.agent.domain.team.TeamChatRunner
import com.vantacode.feature.agent.domain.team.TeamDefinition
import com.vantacode.feature.agent.domain.team.TeamExecutionRegistry
import com.vantacode.feature.agent.domain.team.TeamExecutor
import com.vantacode.feature.agent.domain.team.TeamMember
import com.vantacode.feature.agent.domain.team.TeamRepository
import com.vantacode.feature.agent.domain.tool.AbstractContextualTool
import com.vantacode.feature.agent.domain.tool.ParameterType
import com.vantacode.feature.agent.domain.tool.ToolCapability
import com.vantacode.feature.agent.domain.tool.ToolParameter
import com.vantacode.feature.agent.domain.tool.ToolPermissionPolicy
import com.vantacode.feature.agent.domain.tool.ToolResult
import com.vantacode.feature.agent.presentation.MessageRole
import com.vantacode.feature.settings.domain.repository.AIProviderRepository
import com.vantacode.feature.settings.data.repository.SubagentTierSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * 子代理工具 `task`：统一管理子代理的生命周期。
 *
 * 通过 `action` 参数区分操作类型：
 * - `create`（默认）：创建一个子代理会话并让 AI 替用户向其发消息，子代理自动开始回复。
 * - `send`：向指定子代理发送一条消息（可反复发送）。运行中的子代理会在下一批工具结果里搭车收到，
 *   已完成的子代理会被重新唤醒；消息按发送顺序送达。
 * - `read`：读取指定子代理的最后输出（最后一条助手回复）。
 * - `stop`：停止指定子代理的执行（取消其 AI 任务）。
 * - `del`：删除指定子代理会话及其全部消息。
 * - `list`：列出当前会话的全部子代理及其状态。
 *
 * 最多同时允许 5 个运行中的子代理（create 时检查上限）。
 * 子代理不能嵌套创建子代理（其工具集中不含 `task`）。
 *
 * `create` 可用 `agent` 参数指定自定义子代理定义（`agents/<name>.md`），
 * 由定义决定该子会话的模型、工具集与系统提示词；省略则用继承父会话模型的默认通用子代理。
 */
class TaskTool @Inject constructor(
    private val sessionUseCase: SessionUseCase,
    private val chatSessionDao: ChatSessionDao,
    private val agentMessageDao: AgentMessageDao,
    private val eventBus: SubAgentEventBus,
    private val agentDefinitionRepository: AgentDefinitionRepository,
    private val aiProviderRepository: AIProviderRepository,
    private val subagentTierSettings: SubagentTierSettingsRepository,
    private val teamRepository: TeamRepository,
    private val teamChatRunner: TeamChatRunner,
    private val teamExecutionRegistry: TeamExecutionRegistry,
    private val teamExecutor: TeamExecutor,
    private val teamChatDao: com.vantacode.feature.agent.data.local.dao.TeamChatDao,
    @param:ApplicationContext private val appContext: Context
) : AbstractContextualTool() {

    private companion object {
        const val TAG = "TaskTool"
        const val TASK_DESCRIPTION_MAX = 30
        /** 未指定 agent 时子会话记录的类型标识。 */
        const val DEFAULT_SUBAGENT_TYPE = "subagent"
    }

    override val name = "task"
    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.MODIFY_SESSION_STATE)

    override fun effectiveCapabilities(args: Map<String, JsonElement>): Set<ToolCapability> {
        val action = (args["action"] as? JsonPrimitive)?.content?.trim()?.lowercase() ?: "create"
        return when (action) {
            "read", "list", "send", "team_chat" -> emptySet()
            else -> setOf(ToolCapability.MODIFY_SESSION_STATE)
        }
    }

    override val description = "管理子代理：创建、发消息、读取结果、停止、删除、列出、团队群聊。子代理拥有独立上下文，按所选代理的工具集工作，不能嵌套创建子代理；全局最多同时运行 5 个（所有会话共享名额）。任务完成后会通知主会话，无需轮询；运行中也可能主动发消息。用 send 可追加指令或继续追问已完成的子代理。创建时可用 tier 按任务难度选择模型档位（light 只读探索与机械活 / standard 单文件修改与常规修复 / strong 跨模块重构与疑难排查）；档位是否可用取决于用户在设置里的配置，未配置时按继承处理。team_chat 用 team 指定团队跑一场群聊：成员共享同一份发言记录、轮流发言、互相看得见，适合多视角交锋与拿主意；create + team 则按成员名单一人一岗批量派发，适合已分好工的任务。"

    /** tasks 数组中单个任务项的 JSON Schema */
    private val taskItemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "prompt" to mapOf(
                "type" to "string",
                "description" to "给子代理的完整指令（必填）。写明目标、已知信息、约束与预期产出"
            ),
            "description" to mapOf(
                "type" to "string",
                "description" to "子代理任务描述（可选，作为子会话标题，如「修复登录 bug」）"
            ),
            "agent" to mapOf(
                "type" to "string",
                "description" to "自定义子代理名（可选）：取系统提示词「可用子代理」清单中的名称"
            ),
            "tier" to mapOf(
                "type" to "string",
                "enum" to SubagentModelTier.entries.map { it.apiValue },
                "description" to "模型档位（可选）：light / standard / strong"
            )
        ),
        "required" to listOf("prompt")
    )

    override val parameters: Map<String, ToolParameter> = mapOf(
        "action" to ToolParameter(
            name = "action",
            type = ParameterType.STRING,
            description = "操作类型：create（默认，创建子代理并执行任务）/ send（向子代理发一条消息，可反复发送）/ read（读取子代理的最后输出）/ stop（停止子代理的执行）/ del（删除子代理会话及其消息）/ list（列出当前会话的全部子代理）",
            required = false
        ),
        "id" to ToolParameter(
            name = "id",
            type = ParameterType.STRING,
            description = "子会话 id（read/send/stop/del 必填，取自 task 返回的 id）",
            required = false
        ),
        "description" to ToolParameter(
            name = "description",
            type = ParameterType.STRING,
            description = "子代理任务描述（create 用，作为子会话标题，如「修复登录 bug」）",
            required = false
        ),
        "prompt" to ToolParameter(
            name = "prompt",
            type = ParameterType.STRING,
            description = "给子代理的完整指令（create 必填）。子代理看不到主会话上下文，需写明目标、已知信息、约束与预期产出",
            required = false
        ),
        "agent" to ToolParameter(
            name = "agent",
            type = ParameterType.STRING,
            description = "自定义子代理名（create 可选）：取系统提示词「可用子代理」清单中的名称，按其专属提示词、模型与工具集运行；省略则用继承本会话模型的默认通用子代理",
            required = false
        ),
        "team" to ToolParameter(
            name = "team",
            type = ParameterType.STRING,
            description = "团队名（create / team_chat 必填）：取系统提示词「可用团队」清单中的名称。create 时按成员名单一次性批量派发（各自独立上下文）；team_chat 时让成员围坐群聊、互相看得见发言",
            required = false
        ),
        "rounds" to ToolParameter(
            name = "rounds",
            type = ParameterType.INTEGER,
            description = "群聊轮数（team_chat 可选，默认 2，最多 4）：每轮按成员顺序各发言一次，第 2 轮起成员能看到前面所有人的发言并回应",
            required = false
        ),
        "tier" to ToolParameter(
            name = "tier",
            type = ParameterType.STRING,
            description = "模型档位（create 可选）：light（只读探索、批量定位、机械改名）/ standard（单文件修改、常规修复）/ strong（跨模块重构、疑难排查、方案设计）。按本次任务的难度选，不必固定用同一个档位。档位对应的具体模型由用户在设置里配置；未配置或对应提供商不可用时会自动降级并在结果里说明。子代理定义里写死了 model 时该参数被忽略。",
            required = false,
            enum = SubagentModelTier.entries.map { it.apiValue }
        ),
        "tasks" to ToolParameter(
            name = "tasks",
            type = ParameterType.ARRAY,
            description = "批量创建任务列表（create 可选，与单个 prompt 二选一）：每个元素为一个包含 prompt（必填）、description（可选）、agent（可选）、tier（可选）的对象。一次性批量派发多个子代理。",
            required = false,
            itemsSchema = taskItemSchema
        ),
        "message" to ToolParameter(
            name = "message",
            type = ParameterType.STRING,
            description = "发给子代理的消息正文（send 必填）。可反复调用；运行中的子代理会尽快收到，已完成的会被重新唤醒",
            required = false
        )
    )

    override suspend fun executeWithContext(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val action = (args["action"] as? JsonPrimitive)?.content?.trim()?.lowercase() ?: "create"
        return when (action) {
            "create" -> createSubagent(args, context)
            "team_chat" -> runTeamChat(args, context)
            "team_execute" -> executeTeamLead(args, context)
            "send" -> sendToSubagent(args, context)
            "read" -> readSubagent(args, context)
            "stop" -> stopSubagent(args, context)
            "del" -> deleteSubagent(args, context)
            "list" -> listSubagents(context)
            else -> ToolResult.Error("未知的 action：$action，支持：create / send / read / stop / del / list", "INVALID_ARGS")
        }
    }

    private data class SubagentTaskSpec(
        val prompt: String,
        val description: String,
        val agent: String?,
        val tier: String?,
        val rawArgs: Map<String, JsonElement>,
        /** 团队派发时带上，用于把执行结果写回对应群聊。 */
        val teamName: String? = null,
        val teamMember: TeamMember? = null
    )

    private data class CreatedSubagentInfo(
        val id: String,
        val agent: String?,
        val description: String,
        val tier: String,
        val model: String,
        val warning: String?
    )

    /**
     * 团队执行：把讨论收敛后的任务交给团队的主执行者，由它以完整 agent 身份去干活。
     *
     * 与 `create + team` 的区别：这里只有一个执行者，且它的定义是运行时合成的——
     * 注入主代理同级的规则基线（mainRules/skills/memory/projectRules），工具全量，
     * 行为与普通会话的 agent 一致，而不是精简版子代理。
     */
    /**
     * 团队执行：把讨论收敛后的任务交给团队的主执行者。
     *
     * 实际派发逻辑统一走 [TeamExecutor]——以前这里有一份自己的实现，和群聊自动开工那份不一致，
     * 结果「[执行]」消息一边有 refSessionId、一边没有，进度条与跳转入口时灵时不灵。
     */
    private suspend fun executeTeamLead(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val parentSessionId = context.sessionId ?: return ToolResult.Error("缺少会话上下文", "NO_SESSION")
        val teamName = (args["team"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("参数无效：team_execute 需要 team 参数", "INVALID_ARGS")
        val team = teamRepository.find(teamName)
            ?: return ToolResult.Error("团队定义不存在：$teamName", "TEAM_NOT_FOUND")
        val task = (args["prompt"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (task.isNullOrBlank()) {
            return ToolResult.Error("参数无效：team_execute 需要 prompt，写明要交付什么", "INVALID_ARGS")
        }

        val outcome = teamExecutor.execute(team, task, parentSessionId).getOrElse { e ->
            return ToolResult.Error(e.message ?: "执行派发失败", "TEAM_EXECUTE_FAILED")
        }
        return ToolResult.Success(
            buildJsonObject {
                put("id", outcome.subSessionId)
                put("team", team.name)
                put("agent", outcome.leadName)
                put("state", "running")
                put(
                    "message",
                    "已交给「${outcome.leadName}」执行，进度与结果会回到群里。可用 task(action=\"read\", id=...) 查看细节。"
                )
            }
        )
    }

    /**
     * 团队群聊：成员共享同一份发言记录、轮流发言，彼此看得见对方说过什么。
     * 与 create 的区别是社群式讨论而非分散并行；群聊只发言不挂工具。
     */
    private suspend fun runTeamChat(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val teamName = (args["team"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.Error("参数无效：team_chat 需要 team 参数", "INVALID_ARGS")
        val team = teamRepository.find(teamName) ?: return ToolResult.Error(
            "团队定义不存在：$teamName（可用：${teamRepository.listAll().map { it.name }.joinToString(", ").ifBlank { "无" }}）",
            "TEAM_NOT_FOUND"
        )
        val prompt = (args["prompt"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (prompt.isNullOrBlank()) {
            return ToolResult.Error("参数无效：team_chat 需要 prompt，写明本场群聊的议题", "INVALID_ARGS")
        }
        val rounds = (args["rounds"] as? JsonPrimitive)?.contentOrNull?.trim()?.toIntOrNull()
            ?: TeamChatRunner.DEFAULT_ROUNDS

        val sessionId = context.sessionId
        val parentSession = sessionId?.let { sessionUseCase.getSessionById(it) }
        val result = teamChatRunner.run(
            team = team,
            task = prompt,
            rounds = rounds,
            fallbackProviderId = parentSession?.providerId,
            fallbackModel = parentSession?.model,
            sessionId = sessionId
        )
        val payload = result.getOrElse { e ->
            return ToolResult.Error(e.message ?: "团队群聊失败", "TEAM_CHAT_FAILED")
        }

        return ToolResult.Success(
            buildJsonObject {
                put("team", team.name)
                put("rounds", payload.turns.maxOfOrNull { it.round } ?: 0)
                put("count", payload.turns.size)
                put("transcript", renderTranscript(payload.turns))
                if (payload.warnings.isNotEmpty()) {
                    put("warnings", buildJsonArray { payload.warnings.forEach { add(it) } })
                }
                put(
                    "turns",
                    buildJsonArray {
                        payload.turns.forEach { turn ->
                            addJsonObject {
                                put("round", turn.round)
                                put("speaker", turn.speaker)
                                if (turn.avatar.isNotBlank()) put("avatar", turn.avatar)
                                put("content", turn.content)
                            }
                        }
                    }
                )
            }
        )
    }

    /** 群聊记录的纯文本形式，供主代理直接阅读引用。 */
    private fun renderTranscript(turns: List<TeamChatRunner.Turn>): String = buildString {
        var lastRound = -1
        turns.forEach { turn ->
            if (turn.round != lastRound) {
                if (lastRound != -1) appendLine()
                appendLine("— 第 ${turn.round} 轮 —")
                lastRound = turn.round
            }
            appendLine("${turn.speaker}：${turn.content}")
        }
    }

    /** 创建子代理并启动执行（支持单任务与批量任务）。 */
    private suspend fun createSubagent(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val parentSessionId = context.sessionId ?: return ToolResult.Error("缺少会话上下文", "NO_SESSION")
        val parentSession = sessionUseCase.getSessionById(parentSessionId)
            ?: return ToolResult.Error("当前会话不存在", "SESSION_NOT_FOUND")

        val tasksArray = args["tasks"] as? JsonArray
        val enabledDefinitions = agentDefinitionRepository.listEnabled()
        val teamWarnings = mutableListOf<String>()
        val teamName = (args["team"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
        val teamDef = if (teamName != null) {
            teamRepository.find(teamName) ?: return ToolResult.Error(
                "团队定义不存在：$teamName（可用：${teamRepository.listAll().map { it.name }.joinToString(", ").ifBlank { "无" }}）",
                "TEAM_NOT_FOUND"
            )
        } else {
            null
        }

        var specs: List<SubagentTaskSpec> = if (teamDef != null) {
            if (tasksArray != null && tasksArray.isNotEmpty()) {
                return ToolResult.Error("参数无效：team 与 tasks 不能同时使用", "INVALID_ARGS")
            }
            val basePrompt = (args["prompt"] as? JsonPrimitive)?.contentOrNull?.trim()
            if (basePrompt.isNullOrBlank()) {
                return ToolResult.Error("参数无效：使用 team 时 prompt 必填，写明本次团队任务的目标与产出", "INVALID_ARGS")
            }
            // create + team 是「一人一岗」语义，必须有对应的子代理定义；直接用模型的成员只参与群聊。
            val agentMembers = teamDef.members.filter { it.usesAgent }
            val missing = agentMembers.filter { member ->
                enabledDefinitions.none { it.definition.name.equals(member.agent, ignoreCase = true) }
            }
            if (missing.isNotEmpty()) {
                teamWarnings += "团队成员缺少可派发的子代理定义，已跳过：${missing.joinToString(", ") { it.name }}"
            }
            val present = agentMembers.filter { member ->
                missing.none { it.name.equals(member.name, ignoreCase = true) }
            }
            if (present.isEmpty()) {
                return ToolResult.Error(
                    "团队「${teamDef.name}」没有可派发的子代理成员（直接用模型的成员只参与群聊，请用 team_chat）",
                    "AGENT_NOT_FOUND"
                )
            }
            val tier = (args["tier"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            present.map { member ->
                SubagentTaskSpec(
                    prompt = buildTeamMemberPrompt(teamDef, member.name, basePrompt),
                    description = buildTeamTaskDescription(teamDef.name, member.name),
                    agent = member.agent,
                    tier = tier,
                    rawArgs = args,
                    teamName = teamDef.name,
                    teamMember = member
                )
            }
        } else if (tasksArray != null && tasksArray.isNotEmpty()) {
            val list = mutableListOf<SubagentTaskSpec>()
            for (element in tasksArray) {
                val obj = element as? JsonObject ?: continue
                val p = (obj["prompt"] as? JsonPrimitive)?.contentOrNull?.trim()
                if (p.isNullOrBlank()) {
                    return ToolResult.Error("参数无效：tasks 中的 prompt 不能为空", "INVALID_ARGS")
                }
                val desc = (obj["description"] as? JsonPrimitive)?.contentOrNull?.trim()
                    ?.replace(Regex("\\s+"), " ")
                    ?.take(TASK_DESCRIPTION_MAX)
                    ?: p.lineSequence().firstOrNull()?.trim()?.take(TASK_DESCRIPTION_MAX)
                    ?: "子代理任务"
                val agent = (obj["agent"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                val tier = (obj["tier"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
                list.add(
                    SubagentTaskSpec(
                        prompt = p,
                        description = desc,
                        agent = agent,
                        tier = tier,
                        rawArgs = obj
                    )
                )
            }
            list
        } else {
            val prompt = (args["prompt"] as? JsonPrimitive)?.contentOrNull?.trim()
            if (prompt.isNullOrBlank()) {
                return ToolResult.Error("参数无效：prompt 或 tasks 不能为空", "INVALID_ARGS")
            }
            val description = (args["description"] as? JsonPrimitive)?.contentOrNull?.trim()
                ?.replace(Regex("\\s+"), " ")
                ?.take(TASK_DESCRIPTION_MAX)
                ?: prompt.lineSequence().firstOrNull()?.trim()?.take(TASK_DESCRIPTION_MAX)
                ?: "子代理任务"
            val agent = (args["agent"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            val tier = (args["tier"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            listOf(
                SubagentTaskSpec(
                    prompt = prompt,
                    description = description,
                    agent = agent,
                    tier = tier,
                    rawArgs = args
                )
            )
        }

        if (specs.isEmpty()) {
            return ToolResult.Error("参数无效：未提供任何有效的子代理任务", "INVALID_ARGS")
        }

        // 团队一次上场的人数可能超过全局并发名额：按当前可用名额截断并告知，
        // 剩下的成员等本批完成后再派，避免整队因超额直接失败。
        if (teamDef != null) {
            val available = (SubAgentEventBus.MAX_RUNNING - eventBus.activeCount).coerceAtLeast(0)
            if (available in 1 until specs.size) {
                teamWarnings += "并发名额不足，本次只派发前 $available 个成员（共 ${specs.size} 个），其余成员等本批完成后再派"
                specs = specs.take(available)
            }
        }

        // 预检查指定的 agent 是否都存在（一次扫描本地匹配，避免逐任务重复读盘）
        val definitions = mutableMapOf<SubagentTaskSpec, AgentDefinition?>()
        for (spec in specs) {
            if (spec.agent == null) {
                definitions[spec] = null
                continue
            }
            val def = enabledDefinitions.firstOrNull {
                it.definition.name.equals(spec.agent, ignoreCase = true)
            }?.definition
            if (def == null) {
                val available = enabledDefinitions.map { it.definition.name }
                val hint = if (available.isEmpty()) "当前未定义任何自定义子代理" else "可用：${available.joinToString(", ")}"
                return ToolResult.Error("子代理定义不存在：${spec.agent}（$hint）", "AGENT_NOT_FOUND")
            }
            definitions[spec] = def
        }

        // 并发上限：检查与占位在同一锁内原子完成。同一批工具是并行执行的，
        // 「先读计数再创建」的写法会让并行的 task(create) 双双通过检查后超额创建。
        if (!eventBus.tryReserveSlots(specs.size)) {
            val currentRunning = eventBus.activeCount
            return ToolResult.Error(
                "子代理已达上限（全局最多 ${SubAgentEventBus.MAX_RUNNING} 个同时运行，当前已有 $currentRunning 个在运行），无法再同时派发 ${specs.size} 个，请先等待部分完成或用 stop 停止后再创建",
                "MAX_SUBAGENTS_REACHED"
            )
        }

        // 批量创建共用的快照：提供商与三档配置只冷读一次，不随任务数放大
        val allProviders = runCatching { aiProviderRepository.getAllProviders().first() }.getOrDefault(emptyList())
        val enabledProviderIds = allProviders.filter { it.isEnabled }.map { it.id }.toSet()
        val tierSelections = runCatching { subagentTierSettings.snapshot() }.getOrDefault(emptyMap())
        val matchProviderId: (String?) -> String? = { raw ->
            if (raw.isNullOrBlank()) null
            else allProviders.firstOrNull { it.id == raw }?.id
                ?: allProviders.firstOrNull { it.name.equals(raw, ignoreCase = true) }?.id
        }

        // 批量创建会话并启动
        val createdList = mutableListOf<CreatedSubagentInfo>()
        var pendingReserve = specs.size
        try {
            for (spec in specs) {
                val definition = definitions[spec]
                val definitionProviderId = matchProviderId(definition?.providerId)
                val modelBinding = resolveModel(
                    args = spec.rawArgs,
                    definition = definition,
                    definitionProviderId = definitionProviderId,
                    enabledProviderIds = enabledProviderIds,
                    tierSelections = tierSelections
                )
                val subSession = sessionUseCase.newSubSessionEntity(
                    title = spec.description,
                    parentId = parentSessionId,
                    parent = parentSession,
                    subagentType = definition?.name ?: DEFAULT_SUBAGENT_TYPE,
                    providerId = modelBinding.providerId,
                    model = modelBinding.model,
                    reasoningEffort = definition?.reasoningEffort?.let { effort ->
                        ReasoningEffort.entries.firstOrNull { it.apiValue == effort }?.name
                    },
                    mode = definition?.mode
                )
                sessionUseCase.upsertSession(subSession)
                val subSessionId = subSession.id
                // 团队派发：登记归属，完成时把结果写回群聊
                spec.teamName?.let { teamName ->
                    spec.teamMember?.let { member ->
                        teamExecutionRegistry.register(
                            subSessionId = subSessionId,
                            teamName = teamName,
                            memberName = member.name,
                            avatar = member.avatar
                        )
                    }
                }

                eventBus.emit(
                    SubAgentEvent(
                        subSessionId = subSessionId,
                        parentSessionId = parentSessionId,
                        type = SubAgentEventType.SPAWNED,
                        detail = spec.prompt
                    )
                )
                pendingReserve--
                FileLogger.i(TAG, "子代理已创建: session=$subSessionId parent=$parentSessionId agent=${definition?.name ?: "-"}")

                // 与 capabilitySummary 同口径：定义钉死 mode: plan 时即便父会话是 BUILD，
                // 子代理也在只读边界运行，派发结果里同样要给出 PLAN 只读警告。
                val effectiveMode = definition?.effectiveMode(context.mode) ?: context.mode
                val writeCapped = effectiveMode == AgentMode.PLAN && (definition?.canModifyOrExecute() ?: true)
                val warnings = buildList {
                    // 定义写了 provider 却解析不到（打错名/已删除）：实际会拼成「父会话提供商 + 定义模型」，
                    // 跨提供商的组合大概率无法调用，不说明的话主代理只会在派发失败上一头雾水
                    if (definition?.providerId != null && definitionProviderId == null) {
                        add(
                            appContext.getString(
                                R.string.tool_subagent_provider_unresolved,
                                definition.providerId,
                                modelBinding.model ?: "inherit"
                            )
                        )
                    }
                    if (writeCapped) add(appContext.getString(R.string.tool_subagent_plan_readonly_warning))
                    describeModelFallback(modelBinding)?.let { add(it) }
                }

                createdList.add(
                    CreatedSubagentInfo(
                        id = subSessionId,
                        agent = definition?.name,
                        description = spec.description,
                        tier = modelBinding.tier?.apiValue ?: "inherit",
                        model = modelBinding.model ?: "inherit",
                        warning = warnings.joinToString("\n").takeIf { it.isNotBlank() }
                    )
                )
            }
        } finally {
            // 创建中途失败（DB 异常等）时归还没用掉的预留名额，避免名额泄漏到进程重启
            eventBus.releaseSlots(pendingReserve)
        }

        val first = createdList.first()
        return ToolResult.Success(
            buildJsonObject {
                teamDef?.let { put("team", it.name) }
                put("count", createdList.size)
                put("subagents", buildJsonArray {
                    createdList.forEach { item ->
                        addJsonObject {
                            put("id", item.id)
                            item.agent?.let { put("agent", it) }
                            put("description", item.description)
                            put("tier", item.tier)
                            put("model", item.model)
                            item.warning?.let { put("warning", it) }
                            put("state", "running")
                        }
                    }
                })
                // 保留首项平铺字段以保持对既有单会话消费逻辑与单元测试的 100% 兼容
                put("id", first.id)
                put("state", "running")
                first.agent?.let { put("agent", it) }
                put("tier", first.tier)
                put("model", first.model)
                first.warning?.let { put("warning", it) }
                put("mode", context.mode.name)
                put(
                    "message",
                    buildString {
                        if (createdList.size > 1) {
                            append("已批量创建 ${createdList.size} 个子代理并开始执行，各任务完成后会收到通知。")
                        } else {
                            append(
                                "子代理已创建并开始执行，任务完成后会收到通知。可用 task(action=\"read\", id=...) 读取输出，" +
                                    "task(action=\"stop\", id=...) 主动关闭。"
                            )
                        }
                        if (teamWarnings.isNotEmpty()) {
                            append("\n")
                            append(teamWarnings.joinToString("\n"))
                        }
                    }
                )
            }
        )
    }

    /** 团队成员的任务标题：团队名·成员名，按子会话标题上限截断。 */
    private fun buildTeamTaskDescription(teamName: String, member: String): String =
        "$teamName·$member".replace(Regex("\\s+"), " ").take(TASK_DESCRIPTION_MAX)

    /**
     * 团队成员的初始指令：团队协作说明 + 本成员角色 + 本次任务。
     *
     * 成员的模型/工具集/自身提示词仍由 `agents/<name>.md` 提供，这里只补团队上下文，
     * 不覆盖成员自己的职责定义。
     */
    private fun buildTeamMemberPrompt(
        team: TeamDefinition,
        member: String,
        basePrompt: String
    ): String = buildString {
        appendLine("[团队任务] 团队：${team.name}")
        if (team.description.isNotBlank()) {
            appendLine("团队职责：${team.description}")
        }
        if (team.strategy.isNotBlank()) {
            appendLine()
            appendLine("团队协作说明：")
            appendLine(team.strategy.trim())
        }
        appendLine()
        appendLine("你在本团队中的角色：$member")
        appendLine()
        appendLine("本次任务：")
        append(basePrompt.trim())
    }

    /**
     * 解析本次派发实际使用的模型。提供商可用性与三档快照由调用方批量取一次传入，
     * 这里只做纯解析（[SubagentModelResolver] 是纯函数）。
     */
    private fun resolveModel(
        args: Map<String, JsonElement>,
        definition: AgentDefinition?,
        definitionProviderId: String?,
        enabledProviderIds: Set<String>,
        tierSelections: Map<SubagentModelTier, SubagentTierSelection>
    ): ResolvedSubagentModel {
        val requestedTier = SubagentModelTier.fromToken(
            (args["tier"] as? JsonPrimitive)?.contentOrNull
        )
        return SubagentModelResolver.resolve(
            requestedTier = requestedTier,
            definitionModel = definition?.model,
            definitionProviderId = definitionProviderId,
            selections = tierSelections,
            isProviderAvailable = { id -> id in enabledProviderIds }
        )
    }

    /**
     * 档位没按请求生效时给主代理的解释。Claude Code 同款做法：不隐藏，讲清请求与实际，
     * 免得主代理以为换了模型还在等它的效果。
     */
    private fun describeModelFallback(binding: ResolvedSubagentModel): String? {
        val requested = binding.requestedTier ?: return null
        val requestedLabel = tierLabel(requested)
        return when (binding.fallback) {
            SubagentModelFallback.DEFINITION_PINNED -> appContext.getString(
                R.string.tool_subagent_tier_pinned,
                binding.model ?: "inherit",
                requestedLabel
            )
            SubagentModelFallback.TIER_UNAVAILABLE -> binding.tier?.let { applied ->
                appContext.getString(
                    R.string.tool_subagent_tier_fallback,
                    requestedLabel,
                    tierLabel(applied),
                    binding.model ?: "inherit"
                )
            } ?: appContext.getString(R.string.tool_subagent_tier_unavailable, requestedLabel)
            null -> null
        }
    }

    private fun tierLabel(tier: SubagentModelTier): String = appContext.getString(
        when (tier) {
            SubagentModelTier.LIGHT -> R.string.settings_subagent_tier_light
            SubagentModelTier.STANDARD -> R.string.settings_subagent_tier_standard
            SubagentModelTier.STRONG -> R.string.settings_subagent_tier_strong
        }
    )

    /**
     * 向指定子代理发送一条消息（可反复发送）。
     *
     * 事件交由 ViewModel 按收件人状态分发：运行中的子代理把消息入通知队列、在下一批工具结果里搭车送达；
     * 已完成的子代理则被重新唤醒并起新一轮。不在工具层直接投递，以复用同一套忙碌/空闲分发逻辑。
     */
    private suspend fun sendToSubagent(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val parentSessionId = context.sessionId ?: return ToolResult.Error("缺少会话上下文", "NO_SESSION")
        val subSessionId = (args["id"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (subSessionId.isNullOrBlank()) {
            return ToolResult.Error("参数无效：id 不能为空", "INVALID_ARGS")
        }
        val message = (args["message"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (message.isNullOrBlank()) {
            return ToolResult.Error("参数无效：message 不能为空", "INVALID_ARGS")
        }
        val sub = sessionUseCase.getSessionById(subSessionId)
            ?: return ToolResult.Error("子会话不存在：$subSessionId", "SESSION_NOT_FOUND")
        if (sub.parentId != parentSessionId) {
            return ToolResult.Error("只能向当前会话派生的子代理发消息", "NOT_YOUR_SUBAGENT")
        }

        eventBus.emit(
            SubAgentEvent(
                subSessionId = subSessionId,
                parentSessionId = parentSessionId,
                type = SubAgentEventType.MESSAGE_FROM_PARENT,
                detail = message
            )
        )
        FileLogger.i(TAG, "向子代理发送消息: session=$subSessionId parent=$parentSessionId")
        return ToolResult.Success(
            buildJsonObject {
                put("id", subSessionId)
                put("state", "delivered")
                put("message", appContext.getString(R.string.tool_subagent_message_delivered))
            }
        )
    }

    /** 读取指定子代理的最后输出。 */
    private suspend fun readSubagent(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val subSessionId = (args["id"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (subSessionId.isNullOrBlank()) {
            return ToolResult.Error("参数无效：id 不能为空", "INVALID_ARGS")
        }
        val sub = sessionUseCase.getSessionById(subSessionId)
            ?: return ToolResult.Error("子会话不存在：$subSessionId", "SESSION_NOT_FOUND")
        if (sub.parentId != context.sessionId) {
            return ToolResult.Error("只能读取当前会话派生的子代理", "NOT_YOUR_SUBAGENT")
        }

        val messages = agentMessageDao.getMessagesBySessionOnce(subSessionId)
        // 取最后一条有内容的助手回复（跳过 reasoning-only 的中间消息）
        val lastAssistant = messages.lastOrNull {
            it.role == MessageRole.ASSISTANT.name && it.content.isNotBlank()
        }
        val fallback = messages.lastOrNull { it.role == MessageRole.USER.name }
        val content = when {
            lastAssistant != null -> lastAssistant.content
            fallback != null -> "（子代理尚未回复）请求内容：${fallback.content.take(500)}"
            else -> "（子代理会话为空）"
        }
        val last = runCatching { messages.lastOrNull()?.timestamp ?: 0L }.getOrDefault(0L)
        return ToolResult.Success(
            buildJsonObject {
                put("id", subSessionId)
                put("title", sub.title)
                put("updatedAt", last)
                put("lastOutput", content)
            }
        )
    }

    /** 停止指定子代理的执行。 */
    private suspend fun stopSubagent(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val subSessionId = (args["id"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (subSessionId.isNullOrBlank()) {
            return ToolResult.Error("参数无效：id 不能为空", "INVALID_ARGS")
        }
        val sub = sessionUseCase.getSessionById(subSessionId)
            ?: return ToolResult.Error("子会话不存在：$subSessionId", "SESSION_NOT_FOUND")
        if (sub.parentId != context.sessionId) {
            return ToolResult.Error("只能关闭当前会话派生的子代理", "NOT_YOUR_SUBAGENT")
        }

        eventBus.emit(
            SubAgentEvent(
                subSessionId = subSessionId,
                parentSessionId = context.sessionId!!,
                type = SubAgentEventType.STOPPED
            )
        )
        return ToolResult.Success(
            buildJsonObject {
                put("id", subSessionId)
                put("state", "stopping")
                put("message", "已请求停止子代理，正在取消其 AI 任务。")
            }
        )
    }

    /** 删除指定子代理会话（含其消息）。若仍在运行先请求停止。 */
    private suspend fun deleteSubagent(args: Map<String, JsonElement>, context: AgentContext): ToolResult {
        val subSessionId = (args["id"] as? JsonPrimitive)?.contentOrNull?.trim()
        if (subSessionId.isNullOrBlank()) {
            return ToolResult.Error("参数无效：id 不能为空", "INVALID_ARGS")
        }
        val sub = sessionUseCase.getSessionById(subSessionId)
            ?: return ToolResult.Error("子会话不存在：$subSessionId", "SESSION_NOT_FOUND")
        if (sub.parentId != context.sessionId) {
            return ToolResult.Error("只能删除当前会话派生的子代理", "NOT_YOUR_SUBAGENT")
        }
        if (eventBus.activeSubSessionIds.value.contains(subSessionId)) {
            // 用 DELETED 而非 STOPPED：会话马上被删库，收集方须清运行时状态而不是走停止收尾，
            // 否则取消中的 job 会对着已删除的会话补送通知（白烧一轮 LLM、落孤儿消息）。
            eventBus.emit(
                SubAgentEvent(
                    subSessionId = subSessionId,
                    parentSessionId = context.sessionId!!,
                    type = SubAgentEventType.DELETED
                )
            )
        }
        sessionUseCase.deleteSession(subSessionId)
        FileLogger.i(TAG, "子代理已删除: session=$subSessionId parent=${context.sessionId}")
        return ToolResult.Success(
            buildJsonObject {
                put("id", subSessionId)
                put("state", "deleted")
                put("message", "子代理已删除（含其全部消息）。")
            }
        )
    }

    /** 列出当前会话的全部子代理。 */
    private suspend fun listSubagents(context: AgentContext): ToolResult {
        val parentSessionId = context.sessionId ?: return ToolResult.Error("缺少会话上下文", "NO_SESSION")
        val subs = chatSessionDao.getSubSessionsByParentOnce(parentSessionId)
        val activeIds = eventBus.activeSubSessionIds.value

        val jsonArray = buildJsonArray {
            subs.forEach { entity ->
                addJsonObject {
                    put("id", entity.id)
                    put("title", entity.title)
                    put("state", if (entity.id in activeIds) "running" else "completed")
                    put("createdAt", entity.createdAt)
                    put("updatedAt", entity.updatedAt)
                }
            }
        }

        return ToolResult.Success(
            buildJsonObject {
                put("subagents", jsonArray)
                put("count", subs.size)
                put("runningCount", activeIds.size)
                put("maxRunning", SubAgentEventBus.MAX_RUNNING)
            }
        )
    }
}