/*
 * VantaCode Desktop — 定位本机的 DSH（DeepSeek Harness）可执行文件。
 *
 * 查找顺序：
 *  1. 环境变量 VANTACODE_DSH_BIN（可直接指到 dsh.cmd / dsh / 绝对路径）；
 *  2. PATH 里的 dsh（Windows 下 npm 全局包是 dsh.cmd）；
 *  3. 退回 npx（首次会联网拉包，慢，仅作兜底）。
 *
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import java.io.File

object DshLocator {
    private const val ENV_BIN = "VANTACODE_DSH_BIN"

    /** ACP profile 的启动参数。 */
    private val PROFILE_ARGS = listOf("--profile", "acp")

    private val isWindows: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /**
     * 解析出可直接交给 ProcessBuilder 的命令；找不到时返回 null。
     */
    fun resolve(): List<String>? {
        System.getenv(ENV_BIN)?.trim()?.takeIf { it.isNotEmpty() }?.let { explicit ->
            val exe = File(explicit)
            return if (exe.isFile) listOf(exe.absolutePath) + PROFILE_ARGS
            else listOf(explicit) + PROFILE_ARGS
        }

        findOnPath()?.let { return listOf(it.absolutePath) + PROFILE_ARGS }

        // 兜底：npx 拉起官方包（需要 node >= 24.9.0）
        return listOf("npx", "-y", "@deepseek-ai/dsh") + PROFILE_ARGS
    }

    /** PATH 中第一个可用的 dsh。 */
    fun findOnPath(): File? {
        val names = if (isWindows) listOf("dsh.cmd", "dsh.exe", "dsh.bat", "dsh") else listOf("dsh")
        val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator).filter { it.isNotBlank() }
        for (dir in pathDirs) {
            for (name in names) {
                val candidate = File(dir, name)
                if (candidate.isFile) return candidate
            }
        }
        return null
    }

    /** 是否装了 DSH（用于 UI 提示）。 */
    fun isInstalled(): Boolean =
        System.getenv(ENV_BIN)?.isNotBlank() == true || findOnPath() != null
}
