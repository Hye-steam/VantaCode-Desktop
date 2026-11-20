package com.vantacode.feature.schedule.domain

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.schedule.data.dao.ScheduledTaskDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * 定时任务轮询 Worker：每次醒来挑出到期任务逐个执行。
 *
 * 用周期 Worker 而不是为每个任务单独排一次：任务可随时增删改，
 * 单一轮询源不用在增删时反复 cancel/enqueue，也不会因为进程重启丢调度。
 *
 * 注意 WorkManager 的周期下限是 15 分钟且会被 Doze 延后，
 * 因此这里只保证「到期后尽快跑」，不保证准点。
 */
@HiltWorker
class ScheduleWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParameters: WorkerParameters,
    private val dao: ScheduledTaskDao,
    private val runner: ScheduledTaskRunner
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        val due = runCatching { dao.findDue(System.currentTimeMillis()) }
            .onFailure { FileLogger.e(TAG, "读取到期任务失败", it) }
            .getOrDefault(emptyList())

        if (due.isEmpty()) return Result.success()

        FileLogger.i(TAG, "执行 ${due.size} 个到期任务")
        for (task in due) {
            // 单个任务失败不打断其余任务：一条坏任务不该拖累整个批次
            runCatching { runner.run(task) }
                .onFailure { FileLogger.e(TAG, "任务执行异常: ${task.title}", it) }
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "ScheduleWorker"
        private const val UNIQUE_NAME = "scheduled-agent-tasks"

        /** 轮询周期：WorkManager 允许的最小值。 */
        private const val PERIOD_MINUTES = 15L

        /** 调度轮询任务（幂等）。启用任一定时任务时调用。 */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduleWorker>(
                PERIOD_MINUTES, TimeUnit.MINUTES
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        /** 取消轮询。所有定时任务都关闭时调用，避免无意义唤醒。 */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
