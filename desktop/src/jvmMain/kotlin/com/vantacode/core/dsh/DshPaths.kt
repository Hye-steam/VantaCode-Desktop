/*
 * VantaCode Desktop — DSH (DeepSeek Harness) managed runtime paths.
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import com.vantacode.core.platform.PlatformPaths
import java.io.File

/** 托管 DSH 运行时的目录布局（仿照 ffmpeg 的 ~/.vantacode/tools 方案）。 */
object DshPaths {
    val toolsDir: File get() = File(PlatformPaths.filesDir, "tools")

    /** 自带 Node 的安装目录（整个 node-vX-win-x64 解到这里）。 */
    val nodeDir: File get() = File(toolsDir, "node")

    /** DSH npm 安装根（node_modules 挂在这下面）。 */
    val dshRuntimeDir: File get() = File(toolsDir, "dsh/runtime")

    /** DSH_HOME：profile、session 数据。 */
    val dshHomeDir: File get() = File(toolsDir, "dsh/home")

    /** 插件市场索引缓存。 */
    val marketCacheFile: File get() = File(toolsDir, "dsh/market-index.json")

    /** VantaCode 使用的 DSH profile 名。 */
    const val PROFILE: String = "vantacode"

    const val MARKET_URL: String = "https://bruc3van.com/dsh-plugins/index.json"

    /** DSH npm 包名与 npm-cli 相对路径（随 node 官方发行版自带）。 */
    const val DSH_PACKAGE: String = "@deepseek-ai/dsh"

    fun ensureDirs() {
        listOf(toolsDir, nodeDir, dshRuntimeDir, dshHomeDir).forEach { it.mkdirs() }
    }
}
