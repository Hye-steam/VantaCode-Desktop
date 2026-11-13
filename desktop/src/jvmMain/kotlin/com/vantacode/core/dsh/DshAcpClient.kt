/*
 * VantaCode Desktop — DSH（DeepSeek Harness）ACP 客户端。
 *
 * 一次 [runTask] 的完整链路：
 *   initialize → session/new(cwd=工作区) → session/prompt(任务) → session/close
 *
 * DSH 是「自动化专用」的 ACP 服务端：它不自己碰文件系统，而是反向请求 client 做
 * 文件读写与授权。本实现把这两类请求接回 VantaCode：
 *   · fs/read_text_file  → 从工作区内读（越界拒绝）
 *   · fs/write_text_file → 写回工作区（越界拒绝；只读模式下直接报错）
 *   · session/request_permission → 按 allowWrite 决定放行或拒绝
 *
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import com.vantacode.core.util.FileLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class DshAcpClient(
    private val workspaceRoot: File,
    private val scope: CoroutineScope,
    private val allowWrite: Boolean,
    private val commandOverride: List<String>? = null
) {
    private companion object {
        const val TAG = "DshAcpClient"
        const val TASK_TIMEOUT_MS = 15 * 60 * 1000L
    }

    /**
     * 跑一个任务，返回 DSH 的最终答复。
     *
     * @param task 交给 DSH 的任务描述（自然语言）
     * @param timeoutMs 整体超时
     */
    suspend fun runTask(task: String, timeoutMs: Long = TASK_TIMEOUT_MS): DshAcpResult {
        val command = commandOverride ?: DshLocator.resolve()
            ?: throw DshUnavailableException("未找到 DSH 可执行文件，请先 npm i -g @deepseek-ai/dsh")

        val root = canonical(workspaceRoot)
        val builder = StringBuilder()
        val toolCalls = mutableListOf<String>()
        val thinking = StringBuilder()

        val process = DshAcpProcess(
            command = command,
            workingDir = root,
            scope = scope,
            onRequest = { method, params -> handleRequest(method, params, root, builder) },
            onNotification = { method, params ->
                if (method == AcpMethods.SESSION_UPDATE) {
                    val update = AcpJson.asObject(params["update"])
                    when (AcpJson.updateType(update)) {
                        "agent_message_chunk" -> builder.append(AcpJson.textOf(update))
                        "agent_thought_chunk" -> thinking.append(AcpJson.textOf(update))
                        "tool_call", "tool_call_update" -> {
                            val title = AcpJson.toolTitle(update)
                            if (title.isNotBlank()) toolCalls += title
                        }
                    }
                }
            }
        )

        process.start()
        try {
            val init = process.request(
                AcpMethods.INITIALIZE,
                buildJsonObject {
                    put("protocolVersion", ACP_PROTOCOL_VERSION)
                    put("clientCapabilities", buildJsonObject {
                        put("fs", buildJsonObject {
                            put("readTextFile", true)
                            put("writeTextFile", allowWrite)
                        })
                        put("terminal", false)
                    })
                    put("clientInfo", buildJsonObject {
                        put("name", "VantaCode")
                        put("version", "1.5.0")
                    })
                }
            )
            FileLogger.i(TAG, "DSH 初始化完成：${AcpJson.asString(init["protocolVersion"])}")

            val session = process.request(
                AcpMethods.SESSION_NEW,
                buildJsonObject {
                    put("cwd", root.absolutePath)
                    put("mcpServers", kotlinx.serialization.json.JsonArray(emptyList()))
                }
            )
            val sessionId = AcpJson.asString(session["sessionId"])
            if (sessionId.isBlank()) throw DshAcpException("DSH 未返回 sessionId")

            val prompt = process.request(
                AcpMethods.SESSION_PROMPT,
                buildJsonObject {
                    put("sessionId", sessionId)
                    put("prompt", kotlinx.serialization.json.buildJsonArray {
                        add(buildJsonObject {
                            put("type", "text")
                            put("text", task)
                        })
                    })
                },
                timeoutMs = timeoutMs
            )

            val stopReason = AcpJson.asString(prompt["stopReason"])

            runCatching {
                process.notify(AcpMethods.SESSION_CLOSE, buildJsonObject { put("sessionId", sessionId) })
            }

            return DshAcpResult(
                sessionId = sessionId,
                text = builder.toString().trim(),
                stopReason = stopReason.ifBlank { null },
                toolCalls = toolCalls.toList(),
                thinking = thinking.toString().trim()
            )
        } finally {
            process.close()
        }
    }

    /** 处理 agent → client 的反向请求。 */
    private suspend fun handleRequest(
        method: String,
        params: JsonObject,
        root: File,
        builder: StringBuilder
    ): JsonObject = when (method) {
        AcpMethods.FS_READ_TEXT_FILE -> {
            val path = AcpJson.asString(params["path"])
            val resolved = resolveInRoot(root, path)
            if (resolved == null) {
                AcpJson.errorResponse(0, -32001, "路径越界：$path")
            } else {
                runCatching {
                    val content = resolved.readText()
                    buildJsonObject { put("content", content) }
                }.getOrElse { AcpJson.errorResponse(0, -32002, "读取失败：${it.message}") }
            }
        }

        AcpMethods.FS_WRITE_TEXT_FILE -> {
            if (!allowWrite) {
                AcpJson.errorResponse(0, -32003, "当前任务为只读模式，不允许写文件")
            } else {
                val path = AcpJson.asString(params["path"])
                val content = AcpJson.asString(params["content"])
                val resolved = resolveInRoot(root, path)
                if (resolved == null) {
                    AcpJson.errorResponse(0, -32001, "路径越界：$path")
                } else {
                    runCatching {
                        resolved.parentFile?.mkdirs()
                        resolved.writeText(content)
                        buildJsonObject { }
                    }.getOrElse { AcpJson.errorResponse(0, -32004, "写入失败：${it.message}") }
                }
            }
        }

        AcpMethods.REQUEST_PERMISSION -> {
            // DSH 给的选项里优先挑一个「允许」；没有就拒绝。
            val options = (params["options"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            val allow = options.firstOrNull { option ->
                val kind = AcpJson.asString((option as? JsonObject)?.get("kind"))
                kind.startsWith("allow")
            } as? JsonObject
            if (allow == null) {
                buildJsonObject {
                    put("outcome", buildJsonObject { put("outcome", "cancelled") })
                }
            } else {
                buildJsonObject {
                    put("outcome", buildJsonObject {
                        put("outcome", "selected")
                        put("optionId", AcpJson.asString(allow["optionId"]))
                    })
                }
            }
        }

        else -> AcpJson.errorResponse(0, -32601, "VantaCode 不支持该 ACP 方法：$method")
    }

    /** 把路径限制在工作区内；越界返回 null。 */
    private fun resolveInRoot(root: File, rawPath: String): File? {
        if (rawPath.isBlank()) return null
        val candidate = File(rawPath)
        val resolved = canonical(if (candidate.isAbsolute) candidate else File(root, rawPath))
        val rootPath = root.path
        return if (resolved.path == rootPath || resolved.path.startsWith(rootPath + File.separator)) {
            resolved
        } else {
            null
        }
    }

    private fun canonical(file: File): File = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
}
