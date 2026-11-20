package com.vantacode.feature.schedule.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vantacode.feature.schedule.data.dao.ScheduledTaskDao
import com.vantacode.feature.schedule.data.entity.ScheduledTaskEntity
import com.vantacode.feature.schedule.domain.ScheduleWorker
import com.vantacode.feature.schedule.domain.ScheduledTaskRunner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: ScheduledTaskDao,
    private val runner: ScheduledTaskRunner
) : ViewModel() {

    val tasks: StateFlow<List<ScheduledTaskEntity>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 新建或更新任务。
     *
     * 新任务与刚改过间隔的任务都把 nextRunAt 重排到「现在 + 间隔」，
     * 否则用户新建后要干等一个完整周期才会第一次执行。
     */
    fun save(
        id: Long?,
        title: String,
        prompt: String,
        intervalMinutes: Int,
        projectRoot: String?
    ) {
        viewModelScope.launch {
            val interval = intervalMinutes.coerceAtLeast(ScheduledTaskEntity.MIN_INTERVAL_MINUTES)
            val existing = id?.let { dao.findById(it) }
            val nextRunAt = if (existing != null && existing.intervalMinutes == interval) {
                existing.nextRunAt
            } else {
                System.currentTimeMillis() + interval * 60_000L
            }
            if (existing == null) {
                dao.insert(
                    ScheduledTaskEntity(
                        title = title.trim(),
                        prompt = prompt.trim(),
                        projectRoot = projectRoot,
                        intervalMinutes = interval,
                        nextRunAt = nextRunAt
                    )
                )
            } else {
                dao.update(
                    existing.copy(
                        title = title.trim(),
                        prompt = prompt.trim(),
                        projectRoot = projectRoot,
                        intervalMinutes = interval,
                        nextRunAt = nextRunAt
                    )
                )
            }
            syncWorker()
        }
    }

    fun setEnabled(task: ScheduledTaskEntity, enabled: Boolean) {
        viewModelScope.launch {
            // 重新启用时立刻排一次，避免沿用早已过期的 nextRunAt
            val nextRunAt = if (enabled) {
                System.currentTimeMillis() + task.intervalMinutes * 60_000L
            } else {
                task.nextRunAt
            }
            dao.update(task.copy(enabled = enabled, nextRunAt = nextRunAt))
            syncWorker()
        }
    }

    fun delete(task: ScheduledTaskEntity) {
        viewModelScope.launch {
            dao.delete(task)
            syncWorker()
        }
    }

    /** 立即执行一次，便于验证配置是否正确而不必等周期。 */
    fun runNow(task: ScheduledTaskEntity) {
        viewModelScope.launch {
            runner.run(task)
        }
    }

    /**
     * 有启用任务才保留轮询，全关时取消，避免无意义的后台唤醒。
     * 每次增删改都要调用：Worker 的存在与否跟任务状态必须一致。
     */
    private suspend fun syncWorker() {
        val enabled = tasks.value.count { it.enabled }
        if (enabled > 0) ScheduleWorker.schedule(context) else ScheduleWorker.cancel(context)
    }
}
