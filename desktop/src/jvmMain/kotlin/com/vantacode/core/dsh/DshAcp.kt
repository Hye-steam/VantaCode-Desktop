/*
 * VantaCode Desktop — DeepSeek Harness (DSH) 的 ACP 客户端。
 *
 * DSH（https://github.com/deepseek-ai/deepseek-harness，MIT）以 `dsh --profile acp`
 * 暴露一个「自动化专用」的 Agent Client Protocol 服务端：JSON-RPC 2.0 over stdio
 * （NDJSON，一行一条消息）。VantaCode 作为 ACP client 驱动它：
 *
 *   initialize → session/new → session/prompt →（收 session/update 通知）→ session/close
 *
 * DSH 自己不碰文件系统——它反过来向 client 请求 fs/read_text_file、fs/write_text_file、
 * session/request_permission。于是「DSH 当大脑、VantaCode 当手」成立：
 * 工具调用落回 VantaCode 的工作区与权限模型。
 *
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** ACP v1 方法名（见 @agentclientprotocol/sdk 的 methods 常量表）。 */
object AcpMethods {
    const val INITIALIZE = "initialize"
    const val SESSION_NEW = "session/new"
    const val SESSION_PROMPT = "session/prompt"
    const val SESSION_CLOSE = "session/close"
    const val SESSION_CANCEL = "session/cancel"

    /** agent → client */
    const val SESSION_UPDATE = "session/update"
    const val REQUEST_PERMISSION = "session/request_permission"
    const val FS_READ_TEXT_FILE = "fs/read_text_file"
    const val FS_WRITE_TEXT_FILE = "fs/write_text_file"
}

/** ACP 协议版本。 */
const val ACP_PROTOCOL_VERSION = 1

/** DSH 的一次任务结果。 */
data class DshAcpResult(
    val sessionId: String,
    val text: String,
    val stopReason: String?,
    val toolCalls: List<String>,
    val thinking: String
)

/**
 * 极简 JSON-RPC 2.0 编解码。
 *
 * 不引第三方 JSON-RPC 库：协议面很窄（请求/响应/通知三态），自己拼更可控，
 * 也避免 kotlinx-serialization 的 sealed class 判别器与 ACP 的 `_meta` 扩展字段打架。
 */
internal object AcpJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun request(id: Long, method: String, params: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("method", method)
        put("params", params)
    }

    fun notification(method: String, params: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", method)
        put("params", params)
    }

    fun response(id: Long, result: JsonObject): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
    }

    fun errorResponse(id: Long, code: Int, message: String): JsonObject = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }

    /** 从 session/update 的 update 对象里取纯文本（agent_message_chunk / agent_thought_chunk）。 */
    fun textOf(update: JsonObject): String {
        val content = update["content"] ?: return ""
        val text = when {
            content is JsonObject -> content["text"]?.jsonPrimitive?.contentOrNull ?: ""
            else -> ""
        }
        return text
    }

    /** 更新类型，例如 agent_message_chunk / tool_call / plan。 */
    fun updateType(update: JsonObject): String =
        update["sessionUpdate"]?.jsonPrimitive?.contentOrNull ?: ""

    /** 工具调用标题（用于回给模型的执行轨迹）。 */
    fun toolTitle(update: JsonObject): String {
        val title = update["title"]?.jsonPrimitive?.contentOrNull
        if (!title.isNullOrBlank()) return title
        val name = update["name"]?.jsonPrimitive?.contentOrNull
        return name ?: ""
    }

    fun asString(el: JsonElement?): String = el?.jsonPrimitive?.contentOrNull ?: ""

    fun asInt(el: JsonElement?): Int? = el?.jsonPrimitive?.runCatching { int }?.getOrNull()

    fun asLong(el: JsonElement?): Long? = el?.jsonPrimitive?.runCatching { long }?.getOrNull()

    fun asObject(el: JsonElement?): JsonObject = (el as? JsonObject) ?: JsonObject(emptyMap())
}
