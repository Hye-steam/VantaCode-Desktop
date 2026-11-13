package com.vantacode.feature.agent.domain.tool.dsh

import com.vantacode.core.dsh.DshAcpClient
import com.vantacode.core.dsh.DshAcpException
import com.vantacode.core.dsh.DshUnavailableException
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.domain.tool.AgentTool
import com.vantacode.feature.agent.domain.tool.ParameterType
import com.vantacode.feature.agent.domain.tool.ToolCapability
import com.vantacode.feature.agent.domain.tool.ToolParameter
import com.vantacode.feature.agent.domain.tool.ToolPermissionPolicy
import com.vantacode.feature.agent.domain.tool.ToolResult
import com.vantacode.feature.workspace.data.repository.WorkspaceRepository
import com.vantacode.feature.workspace.domain.WorkspacePathMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

/**
 * 把一个复杂子任务交给 DeepSeek Harness（DSH）执行。
 *
 * DSH 是独立的开源 Agent 框架（MIT，Node >= 24.9.0），以 ACP（Agent Client Protocol，
 * JSON-RPC over stdio）暴露「自动化专用」的 agent：它不自己碰文件系统，而是把文件读写
 * 与授权请求反向打回 client。于是这里 VantaCode 当 client：
 *
 *   · 工作区路径直接是宿主机目录（桌面端没有 PRoot 容器这层）；
 *   · DSH 的文件请求被限制在工作区内（[DshAcpClient] 做边界校验）；
 *   · DSH 的工具调用轨迹与最终答复一并回给主模型。
 *
 * 适用场景：主模型遇到需要独立上下文的长任务（跨文件重构、批量分析、长链任务），
 * 交给 DSH 独立跑一轮，拿结论回来，而不是把全部上下文塞进当前会话。
 */
class DshTaskTool @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val workspacePathMapper: WorkspacePathMapper
) : AgentTool() {

    private companion object {
        const val TAG = "DshTaskTool"
    }

    override val name = "dsh_task"
    override val description =
        "把一个独立的复杂子任务交给 DeepSeek Harness（DSH，独立的开源 Agent 框架）执行，" +
            "DSH 在同一工作区里自己读写文件、跑命令，最后回传结论与工具调用轨迹。" +
            "适合跨文件重构、批量分析、需要独立上下文的长任务；简单编辑直接用 writeFile/editFile 更快。"

    override val permissionPolicy = ToolPermissionPolicy.ASK

    override val capabilities = setOf(
        ToolCapability.READ_WORKSPACE,
        ToolCapability.WRITE_WORKSPACE,
        ToolCapability.EXECUTE_COMMANDS
    )

    override val parameters: Map<String, ToolParameter> = mapOf(
        "task" to ToolParameter(
            name = "task",
            type = ParameterType.STRING,
            description = "交给 DSH 的完整任务描述：要做什么、在哪些文件上做、期望产出什么。" +
                "描述要自足——DSH 看不到当前会话的上下文。",
            required = true
        ),
        "allow_write" to ToolParameter(
            name = "allow_write",
            type = ParameterType.BOOLEAN,
            description = "是否允许 DSH 修改工作区文件。默认 true；纯分析/只读任务传 false。",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, kotlinx.serialization.json.JsonElement>): ToolResult {
        val task = args["task"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (task.isEmpty()) {
            return ToolResult.Error("缺少必需参数：task", "MISSING_ARGS")
        }
        val allowWrite = args["allow_write"]?.jsonPrimitive?.booleanOrNull ?: true

        val workspace = runCatching {
            workspacePathMapper.toHostFile(workspaceRepository.currentPath())
        }.getOrElse {
            return ToolResult.Error("无法解析当前工作区：${it.message}", "WORKSPACE_UNRESOLVED")
        }
        if (!workspace.exists()) {
            return ToolResult.Error("工作区不存在：${workspace.absolutePath}", "WORKSPACE_MISSING")
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        return try {
            val client = DshAcpClient(
                workspaceRoot = workspace,
                scope = scope,
                allowWrite = allowWrite
            )
            val result = client.runTask(task)
            if (result.text.isBlank()) {
                ToolResult.Error(
                    "DSH 未返回文本内容（stopReason=${result.stopReason ?: "未知"}）",
                    "DSH_EMPTY_RESULT"
                )
            } else {
                ToolResult.Success(
                    JsonObject(
                        mapOf(
                            "content" to JsonPrimitive(result.text),
                            "tool_calls" to JsonPrimitive(result.toolCalls.joinToString("\n")),
                            "stop_reason" to JsonPrimitive(result.stopReason ?: ""),
                            "session_id" to JsonPrimitive(result.sessionId)
                        )
                    )
                )
            }
        } catch (e: DshUnavailableException) {
            FileLogger.w(TAG, "DSH 不可用", e)
            ToolResult.Error(e.message ?: "DSH 不可用", "DSH_UNAVAILABLE")
        } catch (e: DshAcpException) {
            FileLogger.w(TAG, "DSH 调用失败", e)
            ToolResult.Error(e.message ?: "DSH 调用失败", "DSH_ERROR")
        } catch (e: Exception) {
            FileLogger.e(TAG, "DSH 任务异常", e)
            ToolResult.Error(e.message ?: "DSH 任务异常", "DSH_EXCEPTION")
        } finally {
            scope.cancel()
        }
    }
}
