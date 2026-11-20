package com.vantacode.feature.schedule.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vantacode.feature.schedule.data.entity.ScheduledTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledTaskDao {

    @Query("SELECT * FROM scheduled_tasks ORDER BY enabled DESC, nextRunAt ASC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun findById(id: Long): ScheduledTaskEntity?

    /** 到期且启用的任务。Worker 每次轮询时调用。 */
    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1 AND nextRunAt <= :now ORDER BY nextRunAt ASC")
    suspend fun findDue(now: Long): List<ScheduledTaskEntity>

    @Query("SELECT COUNT(*) FROM scheduled_tasks WHERE enabled = 1")
    fun observeEnabledCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduledTaskEntity): Long

    @Update
    suspend fun update(task: ScheduledTaskEntity)

    @Delete
    suspend fun delete(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 只更新执行结果，避免把列表里可能已过期的其它字段覆盖回去。 */
    @Query(
        """
        UPDATE scheduled_tasks
        SET lastRunAt = :ranAt, lastStatus = :status, lastMessage = :message,
            lastSessionId = :sessionId, nextRunAt = :nextRunAt
        WHERE id = :id
        """
    )
    suspend fun recordRun(
        id: Long,
        ranAt: Long,
        status: String,
        message: String?,
        sessionId: String?,
        nextRunAt: Long
    )

    @Query("UPDATE scheduled_tasks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
