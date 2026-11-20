package com.vantacode.feature.schedule.domain

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.domain.model.AgentContext
import com.vantacode.feature.agent.domain.model.AgentMode
import com.vantacode.feature.agent.domain.session.MessagePersistenceUseCase
import com.vantacode.feature.agent.domain.session.SessionUseCase
import com.vantacode.feature.agent.domain.tool.ToolRegistry
import com.vantacode.feature.agent.domain.workflow.AgentEvent
import com.vantacode.feature.agent.domain.workflow.AgentWorkflow
import com.vantacode.feature.agent.presentation.MessageRole
import com.vantacode.feature.schedule.data.dao.ScheduledTaskDao
import com.vantacode.feature.schedule.data.entity.ScheduledTaskEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.toList

/**
 * 定时任务执行器：把一个任务的提示词交给 Agent 完整跑一轮，并把结果写回数据库。
 *
 * 刻意不经过 AIAgentViewModel：ViewModel 绑定 viewModelScope，App 进后台或被系统回收时
 * 协程会被取消，定时任务就永远跑不完。这里直接调 [AgentWorkflow] 接口，由 Worker 的
 * 协程作用域托管。
 */
@Singleton
class ScheduledTaskRunner @Inject constructor(
    private val agentWorkflow: AgentWorkflow,
    private val toolRegistry: ToolRegistry,
    private val sessionUseCase: SessionUseCase,
    private val messagePersistenceUseCase: MessagePersistenceUseCase,
    private val dao: ScheduledTaskDao
) {
    /**
     * 执行单个任务并记录结果。
     *
     * 执行完成后无论成败都会推进 [ScheduledTaskEntity.nextRunAt]，
     * 否则失败的任务会在每次轮询时反复重试、把额度烧光。
     */
    suspend fun run(task: ScheduledTaskEntity): RunOutcome {
        val startedAt = System.currentTimeMillis()
        val projectRoot = task.projectRoot?.takeIf { it.isNotBlank() }
            ?: sessionUseCase.getMostRecentSessionOfWorkspace("")?.workspacePath
            ?: return finish(task, startedAt, RunOutcome.Failure("没有可用的项目目录"))

        val session = sessionUseCase.newSessionEntity(workspacePath = projectRoot)
            .copy(title = "定时 · ${task.title}".take(SessionUseCase.TITLE_MAX))
        sessionUseCase.upsertSession(session)
        val sessionId = session.id

        // 用户消息先落库，和手动发起的对话保持同一条链路，会话记录才完整
        messagePersistenceUseCase.persist(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = task.prompt,
            isContextExcluded = false
        )

        val context = AgentContext(
            currentFile = null,
            selectedCode = null,
            projectRoot = projectRoot,
            language = null,
            sessionId = sessionId,
            mode = AgentMode.AUTO
        )

        val outcome = runCatching {
            val events = agentWorkflow
                .executeEvents(task.prompt, context, toolRegistry.getAvailableTools())
                .toList()
            summarize(events)
        }.getOrElse { error ->
            FileLogger.e(TAG, "定时任务执行失败: ${task.title}", error)
            RunOutcome.Failure(error.message ?: error::class.simpleName.orEmpty())
        }

        return finish(task, startedAt, outcome, sessionId)
    }

    /** 从事件流里取最终结论：优先用错误，其次用最后一条可见的助手文本。 */
    private fun summarize(events: List<AgentEvent>): RunOutcome {
        val failure = events.filterIsInstance<AgentEvent.Failed>().lastOrNull()
        if (failure != null) {
            return RunOutcome.Failure(failure.error.ifBlank { "执行失败" })
        }
        val text = events
            .filterIsInstance<AgentEvent.AssistantDelta>()
            .lastOrNull()
            ?.accumulated
            ?.trim()
            .orEmpty()
        return RunOutcome.Success(text.take(SUMMARY_MAX_CHARS))
    }

    private suspend fun finish(
        task: ScheduledTaskEntity,
        startedAt: Long,
        outcome: RunOutcome,
        sessionId: String? = null
    ): RunOutcome {
        val nextRunAt = startedAt + task.intervalMinutes.coerceAtLeast(
            ScheduledTaskEntity.MIN_INTERVAL_MINUTES
        ) * 60_000L
        dao.recordRun(
            id = task.id,
            ranAt = startedAt,
            status = if (outcome is RunOutcome.Success) "success" else "error",
            message = when (outcome) {
                is RunOutcome.Success -> outcome.summary.ifBlank { "已完成" }
                is RunOutcome.Failure -> outcome.reason
            },
            sessionId = sessionId,
            nextRunAt = nextRunAt
        )
        return outcome
    }

    sealed interface RunOutcome {
        data class Success(val summary: String) : RunOutcome
        data class Failure(val reason: String) : RunOutcome
    }

    companion object {
        private const val TAG = "ScheduledTaskRunner"
        private const val SUMMARY_MAX_CHARS = 200
    }
}
