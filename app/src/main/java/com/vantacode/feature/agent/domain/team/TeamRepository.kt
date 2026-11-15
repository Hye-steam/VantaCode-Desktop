package com.vantacode.feature.agent.domain.team

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.workspace.domain.FileAccessProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 团队定义仓库：全局目录 `~/.aicode/teams`，每个 `*.md` 一个团队。
 *
 * 团队跨项目复用（同一套角色分工在多个工作区都成立），因此只做全局一级，
 * 不做项目级覆盖——成员本身仍可来自 agents 的全局或项目级定义。
 */
@Singleton
class TeamRepository @Inject constructor(
    private val fileAccess: FileAccessProvider,
    private val teamChatDao: com.vantacode.feature.agent.data.local.dao.TeamChatDao
) {
    companion object {
        private const val TAG = "TeamRepository"
        private const val MAX_NAME_LENGTH = 40
        private val ILLEGAL_NAME_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

        /** 名称同时用作文件名，禁掉路径分隔符与保留字符。 */
        internal fun isValidName(name: String): Boolean {
            if (name.isBlank() || name.length > MAX_NAME_LENGTH) return false
            if (name == "." || name == "..") return false
            return name.none { it in ILLEGAL_NAME_CHARS || it.isISOControl() }
        }
    }

    val teamsRoot: String = "~/.aicode/teams"

    /** 定义内容版本：经本仓库增删改后递增，供系统提示词清单做缓存失效。 */
    @Volatile private var teamsVersionField: Long = 0
    val teamsVersion: Long get() = teamsVersionField

    private fun bumpVersion() {
        teamsVersionField++
    }

    /** 全部团队，按名称排序。目录不存在时返回空列表。 */
    fun listAll(): List<TeamDefinition> = runCatching {
        if (!fileAccess.isDirectory(teamsRoot)) return@runCatching emptyList()
        val base = teamsRoot.trimEnd('/')
        fileAccess.listFiles(teamsRoot)
            .filter { !it.isDirectory && it.name.endsWith(".md", ignoreCase = true) }
            .mapNotNull { entry ->
                runCatching { TeamDefinitionParser.parse(fileAccess, "$base/${entry.name}") }
                    .onFailure { FileLogger.w(TAG, "解析团队定义失败，已跳过: ${entry.name}", it) }
                    .getOrNull()
            }
            .sortedBy { it.name.lowercase() }
    }.getOrElse { e ->
        FileLogger.w(TAG, "扫描团队定义目录失败: $teamsRoot", e)
        emptyList()
    }

    /** 按名称查找团队（忽略大小写）。 */
    fun find(name: String): TeamDefinition? =
        listAll().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /**
     * 写入团队定义（新建或编辑）。[originalName] 为编辑前的名称，新建时传 null。
     * 返回 null 表示成功。
     */
    suspend fun save(form: TeamDefinition.Form, originalName: String? = null): TeamDefinition.SaveError? {
        val name = form.name.trim()
        if (!isValidName(name)) return TeamDefinition.SaveError.INVALID_NAME
        val cleaned = form.members
            .map { it.copy(name = it.name.trim(), agent = it.agent.trim()) }
            .filter { it.name.isNotBlank() && (it.usesAgent || it.model.isNotBlank()) }
            .distinctBy { it.name.lowercase() }
        // 超限要明确报错，不能静默截断——用户以为加上了，实际被丢了
        if (cleaned.size > TeamDefinition.MAX_MEMBERS) {
            return TeamDefinition.SaveError.MEMBER_LIMIT
        }
        val members = cleaned
        if (members.isEmpty()) return TeamDefinition.SaveError.NO_MEMBERS

        val overwritingSelf = originalName != null && originalName.equals(name, ignoreCase = true)
        if (!overwritingSelf) {
            val taken = listAll().any { it.name.equals(name, ignoreCase = true) }
            if (taken) return TeamDefinition.SaveError.NAME_CONFLICT
        }

        val existingFile = originalName?.let { old ->
            listAll().firstOrNull { it.name.equals(old, ignoreCase = true) }?.filePath
        }
        val text = TeamDefinitionParser.serialize(
            name = name,
            description = form.description,
            ownerName = form.ownerName.ifBlank { TeamDefinition.DEFAULT_OWNER_NAME },
            ownerAvatar = form.ownerAvatar,
            members = members,
            strategy = form.strategy
        )

        return try {
            val target = existingFile?.takeIf { overwritingSelf && fileAccess.isFile(it) }
                ?: "${teamsRoot.trimEnd('/')}/$name.md"
            fileAccess.writeFile(target, text, overwrite = true)
            if (!overwritingSelf && existingFile != null && existingFile != target && fileAccess.isFile(existingFile)) {
                runCatching { fileAccess.delete(existingFile) }.onFailure {
                    FileLogger.w(TAG, "重命名后删除旧团队定义失败: $existingFile")
                }
            }
            // 改名后把群聊记录迁到新名字下，否则界面上历史会凭空消失（数据还在库里，但查不到）
            if (!overwritingSelf && originalName != null && !originalName.equals(name, ignoreCase = true)) {
                runCatching { teamChatDao.renameTeam(originalName, name) }.onFailure {
                    FileLogger.w(TAG, "迁移群聊记录到新团队名失败: $originalName -> $name", it)
                }
            }
            bumpVersion()
            null
        } catch (e: Exception) {
            FileLogger.e(TAG, "保存团队定义失败: $name", e)
            TeamDefinition.SaveError.INVALID_NAME
        }
    }

    /** 删除团队定义文件，不可恢复。返回是否成功。 */
    fun delete(name: String): Boolean {
        val team = listAll().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return false
        val filePath = team.filePath ?: return false
        if (!fileAccess.isFile(filePath)) return false
        val deleted = runCatching { fileAccess.delete(filePath); true }.getOrDefault(false)
        if (deleted) bumpVersion()
        return deleted
    }
}
