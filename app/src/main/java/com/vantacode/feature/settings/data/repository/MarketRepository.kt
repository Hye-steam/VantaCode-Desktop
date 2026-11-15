package com.vantacode.feature.settings.data.repository

import android.content.Context
import com.vantacode.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DSH 插件/技能市场仓库：从真实在线目录拉取、搜索、安装。
 *
 * 插件源：cdn.jsdelivr.net/npm/dsh-plugin-catalog/index.json（format 1，数千条真实 DSH 插件）
 * 技能源：本地 assets 注册表（可换远程 URL）
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

    companion object {
        /** DSH 插件目录（format 1）。 */
        const val DSH_CATALOG_URL = "https://cdn.jsdelivr.net/npm/dsh-plugin-catalog/index.json"
        /** 备用镜像。 */
        const val DSH_CATALOG_MIRROR = "https://awesome-dsh-plugin.com/index.json"
        /** DSH Go 插件市场。 */
        const val DSHGO_MARKET_URL = "https://cdn.jsdelivr.net/npm/dsh-plugin-catalog/index.json"
        /** DSH Go 插件格式文件名。 */
        const val DSHGO_MANIFEST = ".dshgo.json"
    }

    private val skillsRoot: File get() = File(context.filesDir, "aicode/skills")

    // ─── DSH 插件目录 ───

    /** 加载 DSH 插件目录。 */
    suspend fun loadDshCatalog(): List<DshCatalogEntry> = withContext(Dispatchers.IO) {
        for (url in listOf(DSH_CATALOG_URL, DSH_CATALOG_MIRROR)) {
            try {
                val body = fetchUrl(url)
                val catalog = json.decodeFromString<DshCatalog>(body)
                if (catalog.plugins.isNotEmpty()) return@withContext catalog.plugins
            } catch (e: Exception) {
                FileLogger.w("MarketRepo", "Catalog $url failed: ${e.message}")
            }
        }
        emptyList()
    }

    /** 按分类过滤。 */
    fun filterByCategory(entries: List<DshCatalogEntry>, category: String): List<DshCatalogEntry> {
        if (category.isBlank()) return entries
        return entries.filter { it.category.equals(category, ignoreCase = true) }
    }

    /** 搜索 DSH 插件。 */
    fun searchDsh(entries: List<DshCatalogEntry>, query: String): List<DshCatalogEntry> {
        if (query.isBlank()) return entries
        val q = query.lowercase()
        return entries.filter {
            it.name.lowercase().contains(q) ||
                it.title.lowercase().contains(q) ||
                it.descriptionZh.lowercase().contains(q) ||
                (it.description?.en ?: "").lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    // ─── 技能注册表 ───

    /** 加载技能注册表。 */
    suspend fun loadSkillRegistry(url: String): List<MarketEntry> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext loadLocalSkillRegistry()
        try {
            val body = fetchUrl(url)
            json.decodeFromString<MarketRegistry>(body).skills
        } catch (e: Exception) {
            FileLogger.w("MarketRepo", "Skill registry failed: ${e.message}")
            loadLocalSkillRegistry()
        }
    }

    private fun loadLocalSkillRegistry(): List<MarketEntry> {
        return try {
            val text = context.assets.open("market/registry.json").bufferedReader().readText()
            json.decodeFromString<MarketRegistry>(text).skills
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 搜索技能。 */
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
            val bytes = fetchUrlBytes(url)
            installFromZipBytes(bytes)
        } catch (e: Exception) {
            SkillInstallResult.Failure("下载失败: ${e.message}")
        }
    }

    /** 从 ZIP 安装（扫描 SKILL.md）。 */
    suspend fun installFromZipBytes(zipBytes: ByteArray): SkillInstallResult = withContext(Dispatchers.IO) {
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

            // 扫描技能目录（含 DSH 的 .dsh/skills/）
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
                for ((relPath, bytes) in files) {
                    val targetFile = File(targetDir, relPath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(bytes)
                }
                installed.add(name)
            }

            if (installed.isEmpty()) SkillInstallResult.Failure("安装失败")
            else SkillInstallResult.Success(installed)
        } catch (e: Exception) {
            SkillInstallResult.Failure("安装失败: ${e.message}")
        }
    }

    /**
     * 从 GitHub 安装 DSH 插件：下载 ZIP → 提取 SKILL.md / 工具定义 / MCP 配置。
     * 返回安装结果和提取到的内容类型。
     */
    suspend fun installDshPluginFromGithub(repoUrl: String): DshPluginInstallResult = withContext(Dispatchers.IO) {
        try {
            val ownerRepo = repoUrl.removePrefix("https://github.com/").trimEnd('/')
            val zipUrl = "https://github.com/$ownerRepo/archive/refs/heads/main.zip"
            val bytes = try {
                fetchUrlBytes(zipUrl)
            } catch (e: Exception) {
                // 尝试 master 分支
                fetchUrlBytes("https://github.com/$ownerRepo/archive/refs/heads/master.zip")
            }

            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val installed = mutableListOf<String>()
            val skipped = mutableListOf<String>()

            // 1) 提取技能（SKILL.md / CLAUDE.md）
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
            for ((name, files) in skillDirs) {
                val targetDir = File(skillsRoot, name)
                targetDir.mkdirs()
                for ((relPath, data) in files) {
                    val targetFile = File(targetDir, relPath)
                    targetFile.parentFile?.mkdirs()
                    targetFile.writeBytes(data)
                }
                installed.add("技能: $name")
            }

            // 2) 提取 MCP 配置（mcp.json / mcp/*.json）
            val mcpDir = File(context.filesDir, "aicode/mcp")
            for ((path, data) in entries) {
                if (path.endsWith("mcp.json") || path.contains("/mcp/") && path.endsWith(".json")) {
                    mcpDir.mkdirs()
                    val fileName = path.split("/").last()
                    File(mcpDir, fileName).writeBytes(data)
                    installed.add("MCP: $fileName")
                }
            }

            // 3) 提取 package.json 里的 dsh.profile.bundles / cordis.patch.yml
            val pkgEntry = entries.entries
                .firstOrNull { it.key.endsWith("package.json") && !it.key.contains("node_modules") }
            if (pkgEntry != null) {
                val pluginDir = File(context.filesDir, "aicode/dshgo/${ownerRepo.replace("/", "_")}")
                pluginDir.mkdirs()
                File(pluginDir, "package.json").writeBytes(pkgEntry.value)
                installed.add("配置: package.json")
            }

            // 4) cordis.patch.yml
            for ((path, data) in entries) {
                if (path.endsWith("cordis.patch.yml") || path.endsWith("dsh.json")) {
                    val pluginDir = File(context.filesDir, "aicode/dshgo/${ownerRepo.replace("/", "_")}")
                    pluginDir.mkdirs()
                    File(pluginDir, path.split("/").last()).writeBytes(data)
                    installed.add("配置: ${path.split("/").last()}")
                }
            }

            // 5) 保存原始 ZIP 供后续使用
            val pluginDir = File(context.filesDir, "aicode/dshgo/${ownerRepo.replace("/", "_")}")
            pluginDir.mkdirs()
            File(pluginDir, "source.zip").writeBytes(bytes)

            if (installed.isEmpty()) {
                // 没有可提取的内容，记录为已下载
                installed.add("已下载源码（无可直接安装的技能/配置）")
            }

            DshPluginInstallResult.Success(installed)
        } catch (e: Exception) {
            DshPluginInstallResult.Failure("安装失败: ${e.message}")
        }
    }

    /** 生成 DSH 插件安装命令。 */
    fun dshInstallCommand(entry: DshCatalogEntry): String {
        val repo = entry.repoUrl
        return if (repo.isNotBlank()) {
            "dsh plugin add github:${repo.removePrefix("https://github.com/")}"
        } else {
            "dsh plugin add ${entry.installTarget}"
        }
    }

    // ─── DSH Go 插件 ───

    /** 解析 .dshgo.json 插件。 */
    fun parseDshGoPlugin(jsonText: String): DshGoPlugin? {
        return try {
            json.decodeFromString<DshGoPlugin>(jsonText)
        } catch (e: Exception) {
            null
        }
    }

    /** 从 ZIP 安装 DSH Go 插件（.dshgo.json + 资源文件）。 */
    suspend fun installDshGoPlugin(zipBytes: ByteArray): SkillInstallResult = withContext(Dispatchers.IO) {
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

            // 找 .dshgo.json
            val manifestEntry = entries.entries
                .firstOrNull { it.key.endsWith(".dshgo.json") || it.key.endsWith("dshgo.json") }
                ?: return@withContext SkillInstallResult.Failure("缺少 .dshgo.json")

            val plugin = parseDshGoPlugin(String(manifestEntry.value, Charsets.UTF_8))
                ?: return@withContext SkillInstallResult.Failure(".dshgo.json 解析失败")

            // 安装到 DSH Go 插件目录
            val pluginDir = File(context.filesDir, "aicode/dshgo/${plugin.name.ifBlank { "unnamed" }}")
            pluginDir.mkdirs()
            for ((path, bytes) in entries) {
                val target = File(pluginDir, path)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }

            SkillInstallResult.Success(listOf(plugin.displayName.ifBlank { plugin.name }))
        } catch (e: Exception) {
            SkillInstallResult.Failure("安装失败: ${e.message}")
        }
    }

    /** 已安装 DSH Go 插件列表。 */
    fun listDshGoPlugins(): List<DshGoPlugin> {
        val dir = File(context.filesDir, "aicode/dshgo")
        return dir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { d ->
                val manifest = File(d, ".dshgo.json").takeIf { it.exists() }
                    ?: File(d, "dshgo.json").takeIf { it.exists() }
                    ?: return@mapNotNull null
                parseDshGoPlugin(manifest.readText())
            }
            ?: emptyList()
    }

    /** 删除 DSH Go 插件。 */
    suspend fun uninstallDshGoPlugin(name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching { File(context.filesDir, "aicode/dshgo/$name").deleteRecursively() }.getOrDefault(false)
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

/** DSH 插件安装结果。 */
sealed class DshPluginInstallResult {
    data class Success(val items: List<String>) : DshPluginInstallResult()
    data class Failure(val reason: String) : DshPluginInstallResult()
}
