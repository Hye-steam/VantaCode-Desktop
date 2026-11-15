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
 * 插件仓库：安装、卸载、启用、禁用 DSH 风格插件。
 *
 * 插件格式：ZIP 包，内含：
 * - `manifest.json` — 插件元数据
 * - `skills/<name>/SKILL.md` — 技能文件
 * - `mcp/<name>.json` — MCP 服务器配置（可选）
 *
 * 安装位置：`~/.aicode/plugins/<name>/`
 * 技能安装后自动链接到 `~/.aicode/skills/<name>/`
 */
@Singleton
class PluginRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** 插件根目录。 */
    private val pluginsRoot: File
        get() = File(context.filesDir, "aicode/plugins")

    /** 技能根目录（插件的技能会被链接到这里）。 */
    private val skillsRoot: File
        get() = File(context.filesDir, "aicode/skills")

    /** 已安装插件清单文件。 */
    private val registryFile: File
        get() = File(pluginsRoot, "installed.json")

    // ─── 安装 ───

    /** 从 URL 下载 ZIP 插件包并安装。 */
    suspend fun installFromUrl(url: String): PluginInstallResult = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).build()
            val bytes = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext PluginInstallResult.Failure("下载失败: HTTP ${resp.code}")
                resp.body?.bytes() ?: return@withContext PluginInstallResult.Failure("下载内容为空")
            }
            installFromZip(bytes)
        } catch (e: Exception) {
            FileLogger.w("PluginRepo", "Install from URL failed: ${e.message}")
            PluginInstallResult.Failure("下载失败: ${e.message}")
        }
    }

    /** 从 ZIP 字节安装插件。 */
    suspend fun installFromZip(zipBytes: ByteArray): PluginInstallResult = withContext(Dispatchers.IO) {
        try {
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            // 解析清单：优先 manifest.json，其次 DSH 的 package.json
            val manifestBytes = entries.entries
                .firstOrNull { it.key.endsWith("manifest.json") }
                ?.value
            val packageBytes = entries.entries
                .firstOrNull { it.key.endsWith("package.json") && !it.key.contains("node_modules") }
                ?.value

            val manifest = when {
                manifestBytes != null -> json.decodeFromString<PluginManifest>(
                    String(manifestBytes, Charsets.UTF_8)
                )
                packageBytes != null -> parseDshPackageJson(String(packageBytes, Charsets.UTF_8))
                else -> return@withContext PluginInstallResult.Failure("缺少 manifest.json 或 package.json")
            }

            // 安装到插件目录
            val pluginDir = File(pluginsRoot, manifest.name)
            pluginDir.mkdirs()

            // 写入所有文件（去掉外层目录前缀）
            for ((path, bytes) in entries) {
                val relativePath = path.substringAfter("/", path)
                val target = File(pluginDir, relativePath)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }

            // 技能目录链接到全局技能目录（支持 DSH 的 .dsh/skills/ 和标准 skills/）
            val dshSkillsDir = File(pluginDir, ".dsh/skills")
            val stdSkillsDir = File(pluginDir, "skills")
            val skillsDir = if (dshSkillsDir.isDirectory) dshSkillsDir else stdSkillsDir
            if (skillsDir.isDirectory) {
                skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                    val target = File(skillsRoot, skillDir.name)
                    if (!target.exists()) {
                        target.mkdirs()
                        skillDir.listFiles()?.forEach { file ->
                            File(target, file.name).writeBytes(file.readBytes())
                        }
                    }
                }
            }

            // MCP 配置：复制到全局 MCP 配置目录（可选）
            val mcpDir = File(pluginDir, "mcp")
            if (mcpDir.isDirectory) {
                val mcpTarget = File(context.filesDir, "aicode/mcp")
                mcpTarget.mkdirs()
                mcpDir.listFiles()?.forEach { file ->
                    File(mcpTarget, file.name).writeBytes(file.readBytes())
                }
            }

            // 注册到已安装列表
            val installed = InstalledPlugin(
                manifest = manifest,
                installPath = pluginDir.absolutePath,
                enabled = true,
                installedAt = System.currentTimeMillis()
            )
            addToRegistry(installed)

            PluginInstallResult.Success(installed)
        } catch (e: Exception) {
            FileLogger.w("PluginRepo", "Install from ZIP failed: ${e.message}")
            PluginInstallResult.Failure("安装失败: ${e.message}")
        }
    }

    // ─── 管理 ───

    /** 列出所有已安装插件。 */
    fun listInstalled(): List<InstalledPlugin> {
        return try {
            if (!registryFile.exists()) return emptyList()
            json.decodeFromString<List<InstalledPlugin>>(registryFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 启用/禁用插件。 */
    suspend fun setEnabled(name: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val plugins = listInstalled().toMutableList()
        val idx = plugins.indexOfFirst { it.manifest.name == name }
        if (idx >= 0) {
            plugins[idx] = plugins[idx].copy(enabled = enabled)
            saveRegistry(plugins)
        }
    }

    /** 卸载插件。 */
    suspend fun uninstall(name: String): Boolean = withContext(Dispatchers.IO) {
        val plugins = listInstalled().toMutableList()
        val plugin = plugins.firstOrNull { it.manifest.name == name } ?: return@withContext false

        // 删除插件目录
        runCatching { File(plugin.installPath).deleteRecursively() }

        // 删除链接的技能目录
        val skillsDir = File(plugin.installPath, "skills")
        skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
            runCatching { File(skillsRoot, skillDir.name).deleteRecursively() }
        }

        plugins.removeAll { it.manifest.name == name }
        saveRegistry(plugins)
        true
    }

    // ─── 内部 ───

    private fun addToRegistry(plugin: InstalledPlugin) {
        val plugins = listInstalled().toMutableList()
        plugins.removeAll { it.manifest.name == plugin.manifest.name }
        plugins.add(plugin)
        saveRegistry(plugins)
    }

    /** 解析 DSH 插件的 package.json，提取元数据。 */
    private fun parseDshPackageJson(text: String): PluginManifest {
        val obj = json.parseToJsonElement(text) as? kotlinx.serialization.json.JsonObject
            ?: return PluginManifest(name = "unknown", displayName = "Unknown Plugin")
        fun str(key: String): String = (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
        return PluginManifest(
            name = str("name").ifBlank { "dsh-plugin" },
            displayName = str("description").ifBlank { str("name") },
            version = str("version").ifBlank { "1.0.0" },
            description = str("description"),
            author = str("author"),
            tags = listOf("DSH")
        )
    }

    private fun saveRegistry(plugins: List<InstalledPlugin>) {
        registryFile.parentFile?.mkdirs()
        registryFile.writeText(json.encodeToString(plugins))
    }

    /** 从本地 assets 扫描内置插件（plugin-market/builtin/<name>/manifest.json）。 */
    fun loadBuiltinPlugins(): List<PluginManifest> {
        return try {
            val dirs = context.assets.list("plugin-market/builtin") ?: return emptyList()
            dirs.mapNotNull { dir ->
                try {
                    val text = context.assets.open("plugin-market/builtin/$dir/manifest.json")
                        .bufferedReader().readText()
                    json.decodeFromString<PluginManifest>(text)
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 从 assets 安装内置插件。 */
    suspend fun installBuiltin(name: String): PluginInstallResult = withContext(Dispatchers.IO) {
        try {
            val entries = mutableMapOf<String, ByteArray>()
            val base = "plugin-market/builtin/$name"
            val manifestText = context.assets.open("$base/manifest.json").bufferedReader().readText()
            val manifest = json.decodeFromString<PluginManifest>(manifestText)

            // 递归读取 assets 里的所有文件
            fun readAssets(path: String) {
                val children = context.assets.list(path) ?: return
                if (children.isEmpty()) {
                    // 是文件
                    entries[path.substringAfter("$base/")] = context.assets.open(path).readBytes()
                } else {
                    children.forEach { readAssets("$path/$it") }
                }
            }
            readAssets(base)

            // 安装到插件目录
            val pluginDir = File(pluginsRoot, manifest.name)
            pluginDir.mkdirs()
            for ((relPath, bytes) in entries) {
                val target = File(pluginDir, relPath)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }

            // 技能链接
            val skillsDir = File(pluginDir, "skills")
            if (skillsDir.isDirectory) {
                skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                    val target = File(skillsRoot, skillDir.name)
                    if (!target.exists()) {
                        target.mkdirs()
                        skillDir.listFiles()?.forEach { file ->
                            File(target, file.name).writeBytes(file.readBytes())
                        }
                    }
                }
            }

            val installed = InstalledPlugin(
                manifest = manifest,
                installPath = pluginDir.absolutePath,
                enabled = true,
                installedAt = System.currentTimeMillis()
            )
            addToRegistry(installed)
            PluginInstallResult.Success(installed)
        } catch (e: Exception) {
            PluginInstallResult.Failure("安装失败: ${e.message}")
        }
    }
}
