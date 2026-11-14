/*
 * VantaCode Desktop — Node.js runtime detection & provisioning.
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import com.vantacode.core.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 解析出的 Node 运行时。 */
data class NodeInfo(
    /** node.exe 的路径。 */
    val nodeExe: File,
    /** 版本号（如 v24.19.0），未探测到时为 null。 */
    val version: String?,
    /** 由 VantaCode 自带安装（false = 系统 PATH 里找到的）。 */
    val bundled: Boolean
)

/**
 * 找 Node 或在线下载并安装一个便携版 Node：
 * 优先自带版（~/.vantacode/tools/node），其次系统 PATH；都没有则下载官方 win-x64 zip 解出。
 */
object NodeRuntime {
    private const val TAG = "NodeRuntime"
    private const val NODE_VERSION = "v24.19.0"
    private const val NODE_PACKAGE = "node-$NODE_VERSION-win-x64"

    /** 依次尝试的下载源：官方 → npmmirror 镜像（国内更稳）。 */
    private val DOWNLOAD_URLS = listOf(
        "https://nodejs.org/dist/$NODE_VERSION/$NODE_PACKAGE.zip",
        "https://registry.npmmirror.com/-/binary/node/$NODE_VERSION/$NODE_PACKAGE.zip"
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 已自带/已装到 tools 或系统 PATH 的 Node；版本低于 20 视为不可用。 */
    fun find(): NodeInfo? {
        findBundled()?.let { return it }
        return findSystem()
    }

    private fun findBundled(): NodeInfo? {
        val root = DshPaths.nodeDir
        if (!root.isDirectory) return null
        val exe = root.walkTopDown().firstOrNull { it.name.equals("node.exe", ignoreCase = true) }
            ?: return null
        val version = probeVersion(exe)
        if (version == null || !isSupported(version)) return null
        return NodeInfo(exe, version, bundled = true)
    }

    private fun findSystem(): NodeInfo? {
        val exe = whichOnPath("node.exe") ?: whichOnPath("node") ?: return null
        val version = probeVersion(exe)
        if (version == null || !isSupported(version)) return null
        return NodeInfo(exe, version, bundled = false)
    }

    private fun whichOnPath(name: String): File? {
        val path = System.getenv("PATH") ?: return null
        return path.split(File.pathSeparator)
            .filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile }
    }

    private fun probeVersion(exe: File): String? = runCatching {
        val process = ProcessBuilder(exe.absolutePath, "--version")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(10, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return@runCatching null
        }
        process.inputStream.bufferedReader().readText().trim().ifBlank { null }
    }.getOrNull()

    private fun isSupported(version: String): Boolean {
        val major = version.trim().removePrefix("v").substringBefore('.').toIntOrNull() ?: return false
        return major >= 20
    }

    /**
     * 找到可用 Node，找不到就下载便携版安装。
     * 下载/解压失败抛异常，由调用方展示。
     */
    suspend fun ensure(onProgress: (String) -> Unit): NodeInfo = withContext(Dispatchers.IO) {
        find()?.let { return@withContext it }

        DshPaths.ensureDirs()
        val tmpZip = File(PlatformPathsCacheDir(), "dsh-node.zip")
        tmpZip.delete()
        var lastError: String? = null
        for (url in DOWNLOAD_URLS) {
            runCatching {
                onProgress(url.substringAfter("://").substringBefore('/'))
                FileLogger.i(TAG, "下载 Node.js: $url")
                download(url, tmpZip)
                onProgress("extract")
                extractZip(tmpZip, DshPaths.nodeDir)
            }.recover {
                FileLogger.e(TAG, "Node.js 下载/解压失败: $url", it)
                lastError = it.message ?: it.javaClass.simpleName
                null
            }.getOrNull() ?: continue
            val exe = DshPaths.nodeDir.walkTopDown()
                .firstOrNull { it.name.equals("node.exe", ignoreCase = true) }
            if (exe != null) {
                return@withContext NodeInfo(exe, probeVersion(exe), bundled = true)
            }
            lastError = "解压后未找到 node.exe"
        }
        tmpZip.delete()
        throw IllegalStateException(lastError ?: "Node.js 安装失败")
    }

    private fun PlatformPathsCacheDir(): File {
        val cache = File(DshPaths.toolsDir, "dsh").apply { mkdirs() }
        return cache
    }

    private fun download(url: String, dest: File) {
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("空响应")
            dest.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
    }

    /** 用系统自带 tar.exe 解压 zip（bsdtar 兼容）。 */
    private fun extractZip(zip: File, intoDir: File) {
        val tarExe = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32/tar.exe")
        if (!tarExe.isFile) throw IllegalStateException("找不到 tar.exe")
        intoDir.mkdirs()
        val process = ProcessBuilder(tarExe.absolutePath, "-xf", zip.absolutePath, "-C", intoDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(90, TimeUnit.SECONDS)
        if (!finished || process.exitValue() != 0) {
            process.destroyForcibly()
            throw IllegalStateException("tar.exe 解压失败 exit=${process.exitValue()}")
        }
    }
}
