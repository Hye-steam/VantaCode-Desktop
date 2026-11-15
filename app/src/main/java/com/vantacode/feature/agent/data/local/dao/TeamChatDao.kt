package com.vantacode.feature.agent.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vantacode.feature.agent.data.local.entity.TeamChatMessageEntity
import kotlinx.coroutines.flow.Flow

/** 团队群聊消息读写。按团队名分区，一个团队一条时间线。 */
@Dao
interface TeamChatDao {

    @Query("SELECT * FROM team_chat_messages WHERE teamName = :teamName ORDER BY id ASC")
    fun observeMessages(teamName: String): Flow<List<TeamChatMessageEntity>>

    @Query("SELECT * FROM team_chat_messages WHERE teamName = :teamName ORDER BY id ASC")
    suspend fun getMessagesOnce(teamName: String): List<TeamChatMessageEntity>

    @Insert
    suspend fun insert(message: TeamChatMessageEntity): Long

    @Query("DELETE FROM team_chat_messages WHERE teamName = :teamName")
    suspend fun clear(teamName: String)

    /** 团队改名时把历史消息一起搬过去，否则旧名字的记录会变成查不到的孤儿。 */
    @Query("UPDATE team_chat_messages SET teamName = :newName WHERE teamName = :oldName")
    suspend fun renameTeam(oldName: String, newName: String)

    /** 只保留最近 [keep] 条，防止单群记录无限增长。 */
    @Query(
        "DELETE FROM team_chat_messages WHERE teamName = :teamName AND id NOT IN " +
            "(SELECT id FROM team_chat_messages WHERE teamName = :teamName ORDER BY id DESC LIMIT :keep)"
    )
    suspend fun trim(teamName: String, keep: Int)

    @Query("SELECT COUNT(*) FROM team_chat_messages WHERE teamName = :teamName")
    suspend fun count(teamName: String): Int

    /**
     * 最近 [since] 之后是否已经有同一条内容。
     * 执行完成事件在个别路径上会重复到达，落库前用它去重，避免群里出现一模一样的孪生消息。
     */
    @Query(
        "SELECT COUNT(*) FROM team_chat_messages " +
            "WHERE teamName = :teamName AND speaker = :speaker AND content = :content AND createdAt >= :since"
    )
    suspend fun countRecent(teamName: String, speaker: String, content: String, since: Long): Int
}
