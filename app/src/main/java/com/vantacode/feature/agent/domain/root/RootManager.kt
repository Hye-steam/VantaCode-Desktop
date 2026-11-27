package com.vantacode.feature.agent.domain.root

import android.content.Context
import com.vantacode.core.util.FileLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root 提权：检测与命令执行。
 *
 * 和 [com.vantacode.feature.agent.domain.shizuku.ShizukuManager] 的区别：
 * Shizuku 走 ADB shell（uid 2000，除非 Shizuku 本身以 root 启动），受 adb 权限边界约束；
 * 这里直接调宿主 `su -c`，拿到的是真正的 root（uid 0）。
 * 两条链路互不替代——设备没 root 时 Shizuku 仍可用，反之亦然。
 *
 * 所有调用都要求用户先在设置里显式开启（默认关闭），未开启时 [execute] 直接报错，
 * 不会静默降级成普通 shell。
 */
@Singleton
class RootManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: RootSettingsRepository,
) {
    private companion object {
        const val TAG = "RootManager"
        /** 探测 su 是否可用的超时：某些设备的 su 会挂起等授权弹窗，不能无限等。 */
        const val PROBE_TIMEOUT_MS = 5_000L
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val MAX_TIMEOUT_MS = 10 * 60 * 1000L
    }

    sealed interface Availability {
        /** 有 su 且已获授权，可以直接执行 root 命令。 */
        data class Granted(val suPath: String, val version: String?) : Availability

        /** 设备上没有 su，或 su 存在但用户拒绝授权。 */
        data class Unavailable(val reason: String) : Availability
    }

    /** 探测 root 可用性。结果不缓存：用户可能刚在 Magisk 里放行或撤销。 */
    suspend fun probe(): Availability = withContext(Dispatchers.IO) {
        val su = findSu() ?: return@withContext Availability.Unavailable(
            "设备上没有 su，可能未 root"
        )
        val out = runCatching { execRaw(su, "id", PROBE_TIMEOUT_MS) }.getOrNull()
            ?: return@withContext Availability.Unavailable("执行 su 失败")
        if (out.exitCode != 0) {
            return@withContext Availability.Unavailable(
                "su 执行被拒绝或超时（退出码 ${out.exitCode}）"
            )
        }
        // `su -c id` 正常应输出 uid=0(root)。有些 root 方案返回 uid=0 但组不同，只认 uid。
        if (!out.output.contains("uid=0")) {
            return@withContext Availability.Unavailable("su 未返回 root 身份：${out.output.take(80)}")
        }
        val ver = runCatching {
            execRaw(su, "getprop ro.build.version.release", PROBE_TIMEOUT_MS).output.trim()
        }.getOrNull()
        Availability.Granted(su, ver?.ifBlank { null })
    }

    /**
     * 以 root 身份执行命令。
     *
     * @throws RootDisabledException 用户没在设置里开
     * @throws RootUnavailableException 设备没 root 或授权被拒
     */
    suspend fun execute(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Result =
        withContext(Dispatchers.IO) {
            if (!settings.isEnabled()) {
                throw RootDisabledException()
            }
            val su = findSu() ?: throw RootUnavailableException("设备上没有 su，可能未 root")
            val timeout = timeoutMs.coerceIn(1_000L, MAX_TIMEOUT_MS)
            val start = System.currentTimeMillis()
            val r = execRaw(su, command, timeout)
            if (r.exitCode != 0 && r.output.contains("permission denied", ignoreCase = true)) {
                throw RootUnavailableException("su 授权被拒绝，请在 root 管理器中放行本应用")
            }
            Result(r.output, r.exitCode, System.currentTimeMillis() - start)
        }

    /** 一条命令的执行结果。 */
    data class Result(val output: String, val exitCode: Int, val elapsedMs: Long)

    private data class RawResult(val output: String, val exitCode: Int)

    /**
     * 跑 `su -c <cmd>` 并把 stderr 合进 stdout。
     *
     * 合并是刻意的：默认把 stderr 读干净，很多工具的报错才是最有用的信息。
     */
    private fun execRaw(suPath: String, command: String, timeoutMs: Long): RawResult {
        val start = System.currentTimeMillis()
        val proc = ProcessBuilder(suPath, "-c", command)
            .redirectErrorStream(true)
            .start()
        val out = StringBuilder()
        // 读线程必须先起来：命令输出多时管道会写满，先 waitFor 会死锁。
        val reader = Thread {
            runCatching {
                proc.inputStream.bufferedReader().use { r ->
                    val buf = CharArray(4096)
                    while (true) {
                        val n = r.read(buf)
                        if (n <= 0) break
                        synchronized(out) { out.append(buf, 0, n) }
                    }
                }
            }.onFailure { FileLogger.w(TAG, "读取 root 命令输出失败", it) }
        }.apply { isDaemon = true; start() }

        val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            proc.destroyForcibly()
            runCatching { reader.join(500) }
            throw RootUnavailableException("命令超时（${timeoutMs}ms）：${command.take(80)}")
        }
        reader.join(1_000)
        val text = synchronized(out) { out.toString() }
        return RawResult(text, proc.exitValue()).also {
            FileLogger.d(TAG, "root exec exit=${it.exitCode} ${System.currentTimeMillis() - start}ms")
        }
    }

    /**
     * 找 su 的位置。
     *
     * 不同 root 方案路径不同：Magisk 常见 /system/bin/su（实为 /product/bin/su 的链接）、
     * KernelSU/APatch 可能在 /system/bin 或 /debug_ramdisk。这里按常见顺序试。
     */
    private fun findSu(): String? = RootSupport.findSu()
}

/** 用户在设置里没开启 root 能力。 */
class RootDisabledException : Exception("未开启 root 权限，请在 设置 → 权限 → Root 权限 中开启")

/** 设备没 root，或 su 授权被拒。 */
class RootUnavailableException(message: String) : Exception(message)
