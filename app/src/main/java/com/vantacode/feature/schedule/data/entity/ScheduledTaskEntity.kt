package com.vantacode.feature.schedule.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 一条定时任务：按固定间隔把一个提示词交给 Agent 自主执行。
 *
 * 用「间隔分钟数」而不是 cron 表达式：WorkManager 本身只有 15 分钟粒度、
 * 且会被系统省电策略延后，支持 cron 会让用户误以为能精确到点执行。
 */
@Entity(
    tableName = "scheduled_tasks",
    indices = [Index(value = ["enabled", "nextRunAt"])]
)
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** 交给 Agent 的完整指令文本。 */
    val prompt: String,
    /** 执行时使用的项目根目录；为空则用默认工作区。 */
    val projectRoot: String? = null,
    /** 间隔分钟数，最小 [MIN_INTERVAL_MINUTES]。 */
    val intervalMinutes: Int = 60,
    val enabled: Boolean = true,
    /** 下次应执行的时刻（epoch 毫秒）。Worker 只挑 <= now 的任务。 */
    val nextRunAt: Long = 0,
    val lastRunAt: Long? = null,
    /** 上次执行结果：success / error / skipped。 */
    val lastStatus: String? = null,
    /** 上次执行的简要结果或错误信息，列表页直接展示。 */
    val lastMessage: String? = null,
    /** 上次执行对应的会话 id，便于点进去看完整过程。 */
    val lastSessionId: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** WorkManager 的最小周期，低于此值系统仍按 15 分钟调度。 */
        const val MIN_INTERVAL_MINUTES = 15
    }
}
