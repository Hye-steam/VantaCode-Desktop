/*
 * VantaCode Desktop — Managed DeepSeek Harness (DSH) runtime.
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import com.vantacode.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
private data class DshPackageJson(val version: String = "")

@Serializable
private data class ProfilePackageJson(val dependencies: Map<String, String> = emptyMap())

data class DshStatus(
    val node: NodeInfo?,
    val dshInstalled: Boolean,
    val dshVersion: String?
)

private val jsonParser = Json { ignoreUnknownKeys = true; coerceInputValues = true }

data class DshPluginEntry(val name: String, val version: String)

data class DshTaskResult(
    val ok: Boolean,
    /** 最终答案正文（stdout），失败时为 stderr/空。 */
    val output: String,
    val error: String? = null
)

/**
 * 安装/管理托管式 DSH 运行时 + 调用其 CLI：
 * - 安装：自带 Node（找不到就下便携版）+ npm 装 @deepseek-ai/dsh 与 pnpm 到 tools/dsh/runtime
 * - 插件：走 `dsh plugin --profile vantacode add|remove <spec>`（官方 CLI，不重写依赖解析）
 * - 任务：`dsh headless` 子进程跑完取 stdout
 * - 市场：远端 format-1 索引（带磁盘缓存）
 */
object DshRuntime {
    private const val TAG = "DshRuntime"
    private const val NODE_INSTALL_TIMEOUT_SEC = 600L

    private val NPM_REGISTRIES = listOf("https://registry.npmjs.org/", "https://registry.npmmirror.com/")

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun status(): DshStatus {
        val node = NodeRuntime.find()
        val pkg = dshPackageJson()
        val installed = pkg.isFile
        val version = if (installed) runCatching {
            jsonParser.decodeFromString(DshPackageJson.serializer(), pkg.readText(Charsets.UTF_8))
                .version.ifBlank { null }
        }.getOrNull() else null
        return DshStatus(node, installed, version)
    }

    private fun runtimeBin(): File =
        File(DshPaths.dshRuntimeDir, "node_modules/@deepseek-ai/dsh/lib/bin.js")

    private fun dshPackageJson(): File =
        File(DshPaths.dshRuntimeDir, "node_modules/@deepseek-ai/dsh/package.json")

    private fun profileDir(): File = File(DshPaths.dshHomeDir, "profiles/${DshPaths.PROFILE}")

    private fun profilePackageJson(): File = File(profileDir(), "package.json")

    /** DSH_HOME 等环境变量 + PATH 前置（node 目录、pnpm 目录）。 */
    private fun dshEnvironment(node: NodeInfo): Map<String, String> {
        val nodeBinDir = node.nodeExe.parentFile.absolutePath
        val pnpmBinDir = File(DshPaths.dshRuntimeDir, "node_modules/.bin").absolutePath
        val path = buildString {
            append(nodeBinDir).append(File.pathSeparator)
            append(pnpmBinDir).append(File.pathSeparator)
            append(System.getenv("PATH").orEmpty())
        }
        return mapOf(
            "DSH_HOME" to DshPaths.dshHomeDir.absolutePath,
            "PATH" to path
        )
    }

    /** 自带 Node 的 npm-cli.js 路径（官方 tarball 内含 npm）。系统 Node 也有同结构 npm。 */
    private fun npmCli(node: NodeInfo): File? {
        val sibling = File(node.nodeExe.parentFile, "node_modules/npm/bin/npm-cli.js")
        return sibling.takeIf { it.isFile }
    }

    /**
     * 安装（或重装/更新）DSH 运行时。两次 npm 源兜底：先 npmjs，失败自动换 npmmirror。
     * @return 成功时返回实际用的 registry；失败抛异常。
     */
    suspend fun install(onProgress: (String) -> Unit): String = withContext(Dispatchers.IO) {
        DshPaths.ensureDirs()
        val node = NodeRuntime.ensure { onProgress(it) }
        val npm = npmCli(node)?.absolutePath
            ?: throw IllegalStateException("找不到 npm-cli.js（Node.js 安装不完整？）")

        var lastError: String? = null
        for (registry in NPM_REGISTRIES) {
            onProgress(registry.substringAfter("://").substringBefore('/'))
            FileLogger.i(TAG, "安装 DSH: registry=$registry node=${node.nodeExe}")
            val result = runCaptured(
                args = listOf(node.nodeExe.absolutePath, npm, "install",
                    "--prefix", DshPaths.dshRuntimeDir.absolutePath,
                    "--no-progress", "--no-audit", "--no-fund",
                    "--registry", registry,
                    DshPaths.DSH_PACKAGE, "pnpm"),
                environment = dshEnvironment(node),
                timeoutSec = NODE_INSTALL_TIMEOUT_SEC
            )
            if (result.ok && dshPackageJson().isFile) {
                FileLogger.i(TAG, "DSH 安装成功")
                return@withContext registry
            }
            lastError = result.output.takeLast(800)
            FileLogger.w(TAG, "DSH 安装失败 registry=$registry: $lastError")
        }
        throw IllegalStateException(lastError ?: "DSH 安装失败（两个源都不行）")
    }

    fun uninstallRuntime() {
        runCatching { DshPaths.dshRuntimeDir.deleteRecursively() }
    }

    /** 当前 profile 已 npm 安装的插件（dependencies 里的项）。 */
    fun listPlugins(): List<DshPluginEntry> {
        val pkg = profilePackageJson()
        if (!pkg.isFile) return emptyList()
        return runCatching {
            jsonParser.decodeFromString(ProfilePackageJson.serializer(), pkg.readText(Charsets.UTF_8))
                .dependencies
                .map { (name, version) -> DshPluginEntry(name, version) }
                .sortedBy { it.name }
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 安装一个插件来源：npm 包名 / git URL / link: 本地路径。 */
    suspend fun installPlugin(spec: String): Result<String> = pluginAction(true, spec)

    /** 卸载一个插件（npm 名称）。 */
    suspend fun removePlugin(name: String): Result<String> = pluginAction(false, name)

    private suspend fun pluginAction(add: Boolean, spec: String): Result<String> = withContext(Dispatchers.IO) {
        val status = status()
        if (!status.dshInstalled) {
            return@withContext Result.failure(IllegalStateException("DSH 运行时未安装"))
        }
        val node = status.node ?: return@withContext Result.failure(IllegalStateException("Node.js 未就绪"))
        val bin = runtimeBin()
        if (!bin.isFile) {
            return@withContext Result.failure(IllegalStateException("DSH 可执行入口丢失：$bin"))
        }
        val result = runCaptured(
            args = listOf(node.nodeExe.absolutePath, bin.absolutePath,
                "plugin", "--profile", DshPaths.PROFILE, if (add) "add" else "remove", spec),
            environment = dshEnvironment(node),
            timeoutSec = 600L
        )
        if (result.ok) Result.success(result.output.takeLast(400))
        else Result.failure(IllegalStateException(result.output.takeLast(600).ifBlank { "退出码 ${result.exitCode}" }))
    }

    /**
     * 跑一条 headless 任务：`dsh --profile vantacode headless "<任务>"`。
     * stdin 保持打开但不写 → DSH 若要权限批准会立即得到 EOF 而非死等。
     * 上限 10 分钟，超时强杀。
     */
    suspend fun headless(task: String): DshTaskResult = withContext(Dispatchers.IO) {
        val trimmed = task.trim()
        if (trimmed.isEmpty()) {
            return@withContext DshTaskResult(false, "", "空任务")
        }
        val status = status()
        if (!status.dshInstalled) {
            return@withContext DshTaskResult(false, "", "DSH 运行时未安装（请到设置 → DSH 插件安装运行时）")
        }
        val node = status.node
            ?: return@withContext DshTaskResult(false, "", "Node.js 未就绪（请到设置 → DSH 插件安装 Node）")
        val bin = runtimeBin()
        val result = runCaptured(
            args = listOf(node.nodeExe.absolutePath, bin.absolutePath,
                "--profile", DshPaths.PROFILE, "headless", trimmed),
            environment = dshEnvironment(node),
            timeoutSec = 600L
        )
        if (result.ok) DshTaskResult(true, result.output.trim())
        else DshTaskResult(false, result.output.trim(), "DSH 执行失败（exit=${result.exitCode}）：${result.output.takeLast(400)}")
    }

    /** 脱离式启动 DSH Web（供 UI 调用）。 */
    fun spawnWebDetached(): Result<Unit> = runCatching {
        val status = status()
        check(status.dshInstalled) { "DSH 运行时未安装" }
        val node = status.node ?: error("Node.js 未就绪")
        val bin = runtimeBin()
        val pb = ProcessBuilder(node.nodeExe.absolutePath, bin.absolutePath, "--profile", DshPaths.PROFILE, "web")
        pb.environment().putAll(dshEnvironment(node))
        pb.redirectErrorStream(true)
        pb.redirectOutput(File(DshPaths.dshHomeDir, "web.log"))
        pb.start()
        FileLogger.i(TAG, "DSH Web 已启动（127.0.0.1:3080）")
    }

    /** 拉取市场索引；网络失败时回落本地缓存。 */
    suspend fun fetchMarket(maxCacheAgeMs: Long = 24 * 3600 * 1000L): Result<MarketIndex> = withContext(Dispatchers.IO) {
        val cache = DshPaths.marketCacheFile
        val now = System.currentTimeMillis()
        val cached = if (cache.isFile) runCatching { cache.readText(Charsets.UTF_8) }.getOrNull() else null
        if (cached != null && now - cache.lastModified() < maxCacheAgeMs) {
            return@withContext runCatching { DshMarketParser.parse(cached) }
        }
        try {
            val request = Request.Builder().url(DshPaths.MARKET_URL).get().build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                DshMarketParser.parse(body).also {
                    runCatching { cache.writeText(body, Charsets.UTF_8) }
                }
            }.let { Result.success(it) }
        } catch (e: Exception) {
            FileLogger.w(TAG, "市场拉取失败: ${e.message}")
            if (cached != null) {
                runCatching { DshMarketParser.parse(cached) }
                    .fold({ Result.success(it) }, { Result.failure(it) })
            } else {
                Result.failure(e)
            }
        }
    }

    private data class Captured(val ok: Boolean, val exitCode: Int, val output: String)

    private fun runCaptured(
        args: List<String>,
        environment: Map<String, String>,
        timeoutSec: Long
    ): Captured {
        val process = ProcessBuilder(args)
            .redirectErrorStream(true)
            .apply { environment().putAll(environment) }
            .start()
        val output = java.io.ByteArrayOutputStream()
        val outThread = Thread {
            runCatching { process.inputStream.copyTo(output) }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutSec, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            outThread.join(2000)
            return Captured(false, -1, "执行超时（${timeoutSec}s）已强制结束\n" + output.toString(Charsets.UTF_8))
        }
        outThread.join(2000)
        val text = output.toString(Charsets.UTF_8)
        return Captured(process.exitValue() == 0, process.exitValue(), text)
    }
}
