package com.vantacode.feature.settings.data.repository

import android.content.Context
import com.vantacode.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 市场注册表里的单个技能条目。 */
@Serializable
data class SkillMarketEntry(
    val id: String,
    val name: String,
    val description: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val downloadUrl: String = "",
    val skillDir: String = "",
    val version: String = "1.0.0"
)

/** 市场注册表。 */
@Serializable
data class SkillMarketRegistry(
    val version: Int = 1,
    val skills: List<SkillMarketEntry> = emptyList()
)

/** 安装结果。 */
sealed class SkillInstallResult {
    data class Success(val skillNames: List<String>) : SkillInstallResult()
    data class Failure(val reason: String) : SkillInstallResult()
}

/**
 * 技能市场仓库：浏览远程注册表、从 URL 安装技能包、管理本地已安装技能。
 *
 * 技能包格式：ZIP 压缩包，内含一个或多个技能目录，每个目录有 SKILL.md。
 * 安装目标：`~/.aicode/skills/<name>/`（全局技能目录）。
 */
@Singleton
class SkillMarketRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 全局技能根目录（`~/.aicode/skills/` 映射到 filesDir）。 */
    private val skillsRoot: File
        get() = File(context.filesDir, "aicode/skills")

    // ─── 市场注册表 ───

    /** 从远程 URL 加载市场注册表。 */
    suspend fun loadRegistry(url: String): SkillMarketRegistry = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            val body = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                resp.body?.string() ?: throw IOException("Empty body")
            }
            json.decodeFromString<SkillMarketRegistry>(body)
        } catch (e: Exception) {
            FileLogger.w("SkillMarket", "Failed to load registry: ${e.message}")
            SkillMarketRegistry()
        }
    }

    /** 从本地 assets 加载内置注册表。 */
    fun loadBuiltinRegistry(): SkillMarketRegistry = try {
        val text = context.assets.open("skill-market/registry.json").bufferedReader().readText()
        json.decodeFromString<SkillMarketRegistry>(text)
    } catch (e: Exception) {
        SkillMarketRegistry()
    }

    // ─── 技能安装 ───

    /**
     * 从 URL 下载 ZIP 技能包并安装到全局技能目录。
     * 支持 GitHub archive URL、任意直链 ZIP。
     */
    suspend fun installFromUrl(url: String): SkillInstallResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            val bytes = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext SkillInstallResult.Failure("下载失败: HTTP ${resp.code}")
                resp.body?.bytes() ?: return@withContext SkillInstallResult.Failure("下载内容为空")
            }
            installFromZip(bytes)
        } catch (e: Exception) {
            FileLogger.w("SkillMarket", "Install from URL failed: ${e.message}")
            SkillInstallResult.Failure("下载失败: ${e.message}")
        }
    }

    /**
     * 从 ZIP 字节安装技能包。
     * 扫描压缩包里的 SKILL.md，把每个技能目录复制到全局技能目录。
     */
    suspend fun installFromZip(zipBytes: ByteArray): SkillInstallResult = withContext(Dispatchers.IO) {
        try {
            val installed = mutableListOf<String>()
            // 先把 ZIP 里的条目按「技能目录名 → (相对路径, 字节)」归组
            val skillDirs = mutableMapOf<String, MutableMap<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.contains("SKILL.md")) {
                        // 路径格式: <root>/<skillName>/SKILL.md 或 <skillName>/SKILL.md
                        val parts = entry.name.split("/").filter { it.isNotBlank() }
                        if (parts.size >= 2) {
                            val skillName = parts[parts.size - 2]
                            val relativePath = parts.last()
                            skillDirs.getOrPut(skillName) { mutableMapOf() }[relativePath] = zip.readBytes()
                        }
                    }
                    // 同时收集技能目录下的其他文件（scripts/ 等）
                    if (!entry.isDirectory && entry.name.contains("/")) {
                        val parts = entry.name.split("/").filter { it.isNotBlank() }
                        if (parts.size >= 2) {
                            val skillName = parts[parts.size - 2]
                            if (skillDirs.containsKey(skillName)) {
                                val relativePath = parts.drop(parts.size - 2).joinToString("/")
                                skillDirs[skillName]?.putIfAbsent(relativePath, zip.readBytes())
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            if (skillDirs.isEmpty()) {
                return@withContext SkillInstallResult.Failure("压缩包中没有找到 SKILL.md")
            }

            for ((name, files) in skillDirs) {
                val targetDir = File(skillsRoot, name)
                targetDir.mkdirs()
                for ((relPath, bytes) in files) {
                    val targetFile = File(targetDir, relPath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(bytes)
                }
                installed.add(name)
            }

            if (installed.isEmpty()) {
                SkillInstallResult.Failure("安装失败: 无法写入技能文件")
            } else {
                SkillInstallResult.Success(installed)
            }
        } catch (e: Exception) {
            FileLogger.w("SkillMarket", "Install from ZIP failed: ${e.message}")
            SkillInstallResult.Failure("安装失败: ${e.message}")
        }
    }

    // ─── 本地技能管理 ───

    /** 列出已安装的技能目录名。 */
    fun listInstalled(): List<String> {
        val dir = skillsRoot
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isDirectory && File(it, "SKILL.md").exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /** 删除指定技能目录。 */
    suspend fun deleteSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        val dir = File(skillsRoot, name)
        if (dir.isDirectory) {
            runCatching { dir.deleteRecursively() }.getOrDefault(false)
        } else {
            false
        }
    }

    /** 读取已安装技能的 SKILL.md 正文（用于预览）。 */
    fun readSkillContent(name: String): String? {
        val file = File(skillsRoot, "$name/SKILL.md")
        return if (file.exists()) runCatching { file.readText() }.getOrNull() else null
    }
}
