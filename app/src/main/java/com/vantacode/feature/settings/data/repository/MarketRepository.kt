package com.vantacode.feature.settings.data.repository

import android.content.Context
import com.vantacode.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 技能市场仓库：在线拉取技能清单、搜索、安装。
 *
 * 条目来源三级兜底：自托管注册表 → GitHub 话题聚合 → 内置 assets 清单。
 * 安装动作统一为「下载 ZIP → 扫描其中所有 SKILL.md → 落到 aicode/skills/<名字>/」。
 */
@Singleton
class MarketRepository @Inject constructor(
    @param:ApplicationContext internal val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val skillsRoot: File get() = File(context.filesDir, "aicode/skills")

    companion object {
        /** 自托管注册表：优先读它，便于随时增删条目而不用发版。 */
        const val SKILL_REGISTRY_URL =
            "https://raw.githubusercontent.com/Hye-steam/VantaCode-Desktop/vantacode-android/skills-registry.json"

        /** 注册表不可用时的在线兜底：按 GitHub 话题聚合社区技能仓库。 */
        private const val GITHUB_SKILL_SEARCH =
            "https://api.github.com/search/repositories?q=topic:claude-skills&sort=stars&order=desc&per_page=60"
    }

    // ─── 技能注册表 ───

    /**
     * 加载技能注册表：自托管注册表 → GitHub 话题聚合 → 内置清单，逐级兜底。
     */
    suspend fun loadSkillRegistry(url: String = SKILL_REGISTRY_URL): List<MarketEntry> =
        withContext(Dispatchers.IO) {
            if (url.isNotBlank()) {
                runCatching {
                    json.decodeFromString<MarketRegistry>(fetchUrl(url)).entries
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
            }
            runCatching { fetchGithubSkills() }.getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { return@withContext it }
            loadLocalSkillRegistry()
        }

    /**
     * 在线搜索技能：直接查 GitHub 仓库搜索接口，支持关键词与分页。
     * 空关键词等价于按 claude-skills 话题按星数浏览。
     */
    suspend fun searchSkillsOnline(
        query: String,
        page: Int = 1,
        perPage: Int = 50
    ): List<MarketEntry> = withContext(Dispatchers.IO) {
        val q = if (query.isBlank()) "topic:claude-skills" else "$query topic:claude-skills"
        val encoded = URLEncoder.encode(q, "UTF-8")
        val url = "https://api.github.com/search/repositories" +
            "?q=$encoded&sort=stars&order=desc&per_page=$perPage&page=$page"
        runCatching { parseGithubRepos(fetchUrl(url)) }.getOrDefault(emptyList())
    }

    /** 按 GitHub 的 claude-skills 话题聚合社区仓库，作为注册表不可用时的在线来源。 */
    private fun fetchGithubSkills(): List<MarketEntry> =
        runCatching { parseGithubRepos(fetchUrl(GITHUB_SKILL_SEARCH)) }.getOrDefault(emptyList())

    private fun parseGithubRepos(body: String): List<MarketEntry> =
        parseGithubReposJson(json, body)

    private fun loadLocalSkillRegistry(): List<MarketEntry> {
        return try {
            val text = context.assets.open("market/registry.json").bufferedReader().readText()
            json.decodeFromString<MarketRegistry>(text).entries
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 搜索技能（本地过滤）。 */
    fun searchSkills(entries: List<MarketEntry>, query: String): List<MarketEntry> {
        if (query.isBlank()) return entries
        val q = query.lowercase()
        return entries.filter {
            it.name.lowercase().contains(q) ||
                it.displayName.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.tags.any { tag -> tag.lowercase().contains(q) }
        }
    }

    // ─── 安装 ───

    /** 从 URL 下载 ZIP 并安装技能。 */
    suspend fun installFromUrl(url: String): SkillInstallResult = withContext(Dispatchers.IO) {
        try {
            installFromZipBytes(fetchUrlBytes(url))
        } catch (e: Exception) {
            SkillInstallResult.Failure("下载失败: ${e.message}")
        }
    }

    /** 从 ZIP 安装（扫描 SKILL.md）。 */
    suspend fun installFromZipBytes(zipBytes: ByteArray): SkillInstallResult =
        withContext(Dispatchers.IO) {
            try {
                val entries = mutableMapOf<String, ByteArray>()
                ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                val skillDirs = mutableMapOf<String, MutableMap<String, ByteArray>>()
                for ((path, bytes) in entries) {
                    val parts = path.split("/").filter { it.isNotBlank() }
                    if (parts.size >= 2 && (parts.last() == "SKILL.md" || parts.last() == "CLAUDE.md")) {
                        val skillName = parts[parts.size - 2]
                        skillDirs.getOrPut(skillName) { mutableMapOf() }["SKILL.md"] = bytes
                    }
                }
                for ((path, bytes) in entries) {
                    val parts = path.split("/").filter { it.isNotBlank() }
                    if (parts.size >= 3) {
                        val skillName = parts[parts.size - 3]
                        if (skillDirs.containsKey(skillName)) {
                            val relPath = parts.drop(parts.size - 2).joinToString("/")
                            skillDirs[skillName]?.putIfAbsent(relPath, bytes)
                        }
                    }
                }

                if (skillDirs.isEmpty()) {
                    return@withContext SkillInstallResult.Failure("压缩包中没有找到 SKILL.md")
                }

                val installed = mutableListOf<String>()
                for ((name, files) in skillDirs) {
                    val targetDir = File(skillsRoot, name)
                    targetDir.mkdirs()
                    for ((relPath, data) in files) {
                        val targetFile = File(targetDir, relPath)
                        targetFile.parentFile?.mkdirs()
                        targetFile.writeBytes(data)
                    }
                    installed.add(name)
                }

                if (installed.isEmpty()) SkillInstallResult.Failure("安装失败")
                else SkillInstallResult.Success(installed)
            } catch (e: Exception) {
                SkillInstallResult.Failure("安装失败: ${e.message}")
            }
        }

    /** 已安装技能列表。 */
    fun listInstalledSkills(): List<String> {
        return skillsRoot.listFiles()
            ?.filter { it.isDirectory && File(it, "SKILL.md").exists() }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    /** 删除技能。 */
    suspend fun uninstallSkill(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(skillsRoot, name).deleteRecursively() }.getOrDefault(false)
    }

    // ─── 内部 ───

    private fun fetchUrl(url: String): String {
        val req = Request.Builder().url(url).build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw java.io.IOException("Empty body")
        }
    }

    fun fetchUrlBytes(url: String): ByteArray {
        val req = Request.Builder().url(url).build()
        return httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            resp.body?.bytes() ?: throw java.io.IOException("Empty body")
        }
    }
}

/**
 * GitHub 搜索接口 items → 市场条目。
 *
 * 纯函数、不依赖网络与 Context，字段映射的回归可以直接用单测覆盖。
 */
internal fun parseGithubReposJson(json: Json, body: String): List<MarketEntry> {
    val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        ?: return emptyList()
    val items = root["items"]?.jsonArray ?: return emptyList()
    return items.mapNotNull { element ->
        runCatching {
            val obj = element.jsonObject
            val full = obj["full_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val branch = obj["default_branch"]?.jsonPrimitive?.contentOrNull ?: "main"
            MarketEntry(
                name = full,
                displayName = full.substringAfterLast("/"),
                description = obj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                author = full.substringBefore("/"),
                stars = obj["stargazers_count"]?.jsonPrimitive?.intOrNull ?: 0,
                downloadUrl = "https://github.com/$full/archive/refs/heads/$branch.zip",
                category = "skill",
                tags = listOfNotNull(obj["language"]?.jsonPrimitive?.contentOrNull)
            )
        }.getOrNull()
    }
}
