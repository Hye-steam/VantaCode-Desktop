/*
 * VantaCode Desktop — DSH 进程与 ACP（JSON-RPC over stdio）传输层。
 *
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import com.vantacode.core.util.FileLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * DSH 子进程 + ACP 传输。
 *
 * - stdout 只承载协议流量（NDJSON，一行一条 JSON），stderr 单独排空并写日志；
 *   DSH 的 ACP profile 明确要求 stdout 干净，任何多余输出都会破坏协议。
 * - 请求按 id 关联到 [CompletableDeferred]；agent 反向发起的请求（fs/权限）
 *   走 [onRequest] 回调，结果再写回同一连接。
 */
class DshAcpProcess(
    private val command: List<String>,
    private val workingDir: File,
    private val scope: CoroutineScope,
    private val onRequest: suspend (method: String, params: JsonObject) -> JsonObject,
    private val onNotification: (method: String, params: JsonObject) -> Unit
) {
    private companion object {
        const val TAG = "DshAcpProcess"
        const val DEFAULT_TIMEOUT_MS = 120_000L
    }

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null
    private var stderrJob: Job? = null

    private val pending = ConcurrentHashMap<Long, CompletableDeferred<JsonObject>>()
    private val idSeq = AtomicLong(0L)
    private val writeLock = Any()

    val isAlive: Boolean get() = process?.isAlive == true

    /** 启动进程；命令不存在或立刻退出时抛 [DshUnavailableException]。 */
    fun start() {
        if (isAlive) return
        val builder = ProcessBuilder(command).apply {
            directory(workingDir)
            redirectErrorStream(false)
        }
        val proc = try {
            builder.start()
        } catch (e: Exception) {
            throw DshUnavailableException("无法启动 DSH（${command.joinToString(" ")}）：${e.message}", e)
        }
        process = proc
        writer = proc.outputStream.bufferedWriter(Charsets.UTF_8)

        readerJob = scope.launch(Dispatchers.IO) {
            try {
                proc.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isBlank()) continue
                        handleLine(line)
                    }
                }
            } catch (e: Exception) {
                FileLogger.w(TAG, "DSH stdout 读取结束", e)
            } finally {
                failAllPending("DSH 进程已退出")
            }
        }

        stderrJob = scope.launch(Dispatchers.IO) {
            try {
                proc.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) FileLogger.i(TAG, "dsh stderr: $line")
                    }
                }
            } catch (e: Exception) {
                FileLogger.w(TAG, "DSH stderr 读取结束", e)
            }
        }
    }

    private fun handleLine(line: String) {
        val obj = runCatching { AcpJson.json.parseToJsonElement(line).let { it as? JsonObject } }.getOrNull()
        if (obj == null) {
            FileLogger.w(TAG, "DSH 输出了非 JSON 行：${line.take(200)}")
            return
        }
        val id = AcpJson.asLong(obj["id"])
        val method = AcpJson.asString(obj["method"])
        when {
            id != null && method.isEmpty() -> {
                // 响应：result / error
                pending.remove(id)?.complete(obj)
            }
            id != null && method.isNotEmpty() -> {
                // agent → client 请求
                scope.launch {
                    val result = runCatching { onRequest(method, AcpJson.asObject(obj["params"])) }
                        .getOrElse {
                            AcpJson.errorResponse(id, -32603, it.message ?: "内部错误")
                        }
                    if (result.containsKey("error")) {
                        send(result)
                    } else {
                        send(AcpJson.response(id, result))
                    }
                }
            }
            method.isNotEmpty() -> {
                onNotification(method, AcpJson.asObject(obj["params"]))
            }
        }
    }

    /** 发请求并等待响应。 */
    suspend fun request(method: String, params: JsonObject, timeoutMs: Long = DEFAULT_TIMEOUT_MS): JsonObject {
        val id = idSeq.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            send(AcpJson.request(id, method, params))
            val raw = withTimeout(timeoutMs) { deferred.await() }
            raw["error"]?.let { err ->
                val message = AcpJson.asString((err as? JsonObject)?.get("message"))
                throw DshAcpException("DSH 返回错误：$message")
            }
            return AcpJson.asObject(raw["result"])
        } catch (e: TimeoutCancellationException) {
            throw DshAcpException("DSH 请求超时（$method，${timeoutMs}ms）")
        } finally {
            pending.remove(id)
        }
    }

    /** 发通知（无响应）。 */
    fun notify(method: String, params: JsonObject) {
        runCatching { send(AcpJson.notification(method, params)) }
            .onFailure { FileLogger.w(TAG, "DSH 通知发送失败：$method", it) }
    }

    private fun send(message: JsonObject) {
        val out = writer ?: throw DshUnavailableException("DSH 进程未启动")
        val text = AcpJson.json.encodeToString(JsonObject.serializer(), message)
        synchronized(writeLock) {
            out.write(text)
            out.write("\n")
            out.flush()
        }
    }

    private fun failAllPending(reason: String) {
        pending.keys.toList().forEach { key ->
            pending.remove(key)?.completeExceptionally(DshAcpException(reason))
        }
    }

    fun close() {
        readerJob?.cancel()
        stderrJob?.cancel()
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        process = null
        writer = null
    }

    /** 读取命令行输出（调试用）。 */
    fun describe(): String = buildString {
        append("cmd=").append(command.joinToString(" "))
        append(" cwd=").append(workingDir.absolutePath)
        append(" alive=").append(isAlive)
    }
}

/** DSH 不可用（没装 / 启动失败）。 */
class DshUnavailableException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** ACP 协议层错误。 */
class DshAcpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 便捷构造：空对象。 */
internal fun emptyJsonObject(): JsonObject = buildJsonObject { }

/** 便捷构造：字符串字段。 */
internal fun jsonObjectOf(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
    pairs.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
}
