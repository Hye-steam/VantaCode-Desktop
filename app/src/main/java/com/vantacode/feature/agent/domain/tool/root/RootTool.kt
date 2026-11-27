package com.vantacode.feature.agent.domain.tool.root

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.domain.container.BoundedOutput
import com.vantacode.feature.agent.domain.root.RootDisabledException
import com.vantacode.feature.agent.domain.root.RootManager
import com.vantacode.feature.agent.domain.root.RootSettingsRepository
import com.vantacode.feature.agent.domain.root.RootUnavailableException
import com.vantacode.feature.agent.domain.tool.AgentTool
import com.vantacode.feature.agent.domain.tool.ParameterType
import com.vantacode.feature.agent.domain.tool.ToolCapability
import com.vantacode.feature.agent.domain.tool.ToolParameter
import com.vantacode.feature.agent.domain.tool.ToolPermissionPolicy
import com.vantacode.feature.agent.domain.tool.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject

/**
 * 以真正的 root（uid 0）身份在宿主执行命令。
 *
 * 与 [com.vantacode.feature.agent.domain.tool.shizuku.ShizukuTool] 的区别：
 * Shizuku 走 ADB shell，而这里直接 `su -c`，能做 adb 做不到的事——读写任意应用私有目录、
 * 挂载目录、改系统设置、操作 Magisk 模块等。
 *
 * 三个开关共同决定行为（见 [RootSettingsRepository]）：
 *   - 总开关关闭 → 工具不注册，AI 看不到它（不需要在读代码时判断）
 *   - 跳过确认关闭 → 每次调用都弹确认框
 *   - 私有数据开关关闭 → 含 `/data/data`、`/data/system` 等路径的命令直接拒绝
 */
class RootTool @Inject constructor(
    private val rootManager: RootManager,
    private val settings: RootSettingsRepository,
) : AgentTool() {
    private companion object {
        const val TAG = "RootTool"
        const val DEFAULT_TIMEOUT_SECONDS = 120L
        const val MAX_TIMEOUT_SECONDS = 600L

        /**
         * 命中即视为「访问其他应用私有数据」的路径。
         *
         * 只挡真正的私有目录：`/storage/emulated/0`（公共存储）、`/data/local/tmp`（adb 可写）、
         * `/data/media` 都不在这里，那些本来就该自由访问。
         */
        val PRIVATE_DATA_PATTERNS = listOf(
            "/data/data/",
            "/data/user/",
            "/data/user_de/",
            "/data/system/",
            "/data/misc/",
            "/data/adb/",
            "/data/app/",
        )
    }

    override val name = "Root"

    override val description =
        "以 root 身份在 Android 宿主上执行命令，可读写任意目录（包括其他应用的私有数据）、" +
            "挂载目录、修改系统设置、操作 Magisk 模块。需设备已 root 且用户在设置中开启 root 权限。" +
            "普通容器内操作请用 Bash，ADB 权限级别的操作请用 Shizuku。"

    override val permissionPolicy = ToolPermissionPolicy.ASK
    override val capabilities = setOf(ToolCapability.EXECUTE_COMMANDS)

    override val parameters: Map<String, ToolParameter> = mapOf(
        "command" to ToolParameter(
            name = "command",
            type = ParameterType.STRING,
            description = "要以 root 执行的 Shell 命令",
            required = true
        ),
        "timeout" to ToolParameter(
            name = "timeout",
            type = ParameterType.INTEGER,
            description = "命令最长执行时间（秒），默认 $DEFAULT_TIMEOUT_SECONDS 秒，上限 $MAX_TIMEOUT_SECONDS 秒。",
            required = false
        )
    )

    private fun resolveTimeoutMs(args: Map<String, JsonElement>): Long {
        val seconds = args["timeout"]?.jsonPrimitive?.longOrNull ?: DEFAULT_TIMEOUT_SECONDS
        return seconds.coerceIn(1L, MAX_TIMEOUT_SECONDS) * 1000L
    }

    override suspend fun execute(args: Map<String, JsonElement>): ToolResult {
        val command = args["command"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.Error("缺少必需参数：command")

        if (command.isBlank()) {
            return ToolResult.Error("command 不能为空")
        }

        if (!settings.isPrivateDataAllowed()) {
            val hit = PRIVATE_DATA_PATTERNS.firstOrNull { command.contains(it) }
            if (hit != null) {
                return ToolResult.Error(
                    "命令触碰其他应用私有数据（$hit），需在 设置 → 权限 → Root 权限 中开启" +
                        "「允许访问其他应用数据」后才能执行。",
                    code = "ROOT_PRIVATE_DATA_BLOCKED"
                )
            }
        }

        return try {
            val timeoutMs = resolveTimeoutMs(args)
            FileLogger.d(TAG, "root exec (timeout=${timeoutMs}ms): $command")
            val result = rootManager.execute(command, timeoutMs)
            val output = BoundedOutput().apply { append(result.output) }.build()
            FileLogger.v(TAG, "root exec 完成，退出码 ${result.exitCode}，${result.elapsedMs}ms")
            ToolResult.Success(JsonPrimitive(output))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RootDisabledException) {
            ToolResult.Error(e.message ?: "root 未开启", code = "ROOT_DISABLED")
        } catch (e: RootUnavailableException) {
            ToolResult.Error(e.message ?: "root 不可用", code = "ROOT_UNAVAILABLE")
        } catch (e: Exception) {
            FileLogger.e(TAG, "root exec 失败: $command", e)
            ToolResult.Error("执行 root 命令失败：${e.message}")
        }
    }
}
