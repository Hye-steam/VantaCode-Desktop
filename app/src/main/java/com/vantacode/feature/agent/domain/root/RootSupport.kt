package com.vantacode.feature.agent.domain.root

import java.io.File

/**
 * 跨模块共用的 su 定位。
 *
 * 单独抽出来是因为两条链路都要找它：RootManager（直接执行命令）和
 * LinuxContainerEngine（用 su 包住 proot 启动）。
 */
internal object RootSupport {

    /**
     * su 的常见路径，按可能性排序。
     *
     * Magisk 一般是 /system/bin/su（指向 /product/bin/su）；
     * KernelSU 在 /data/adb/ksu/bin/su；APatch 在 /data/adb/ap/bin/su；
     * 少数老方案放 /sbin/su 或 /su/bin/su。
     */
    private val CANDIDATES = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/product/bin/su",
        "/sbin/su",
        "/su/bin/su",
        "/debug_ramdisk/su",
        "/data/adb/ksu/bin/su",
        "/data/adb/ap/bin/su",
    )

    /** 返回第一个可执行的 su；都没有则返回 null。 */
    fun findSu(): String? = CANDIDATES.firstOrNull { path ->
        runCatching { File(path).canExecute() }.getOrDefault(false)
    }
}
