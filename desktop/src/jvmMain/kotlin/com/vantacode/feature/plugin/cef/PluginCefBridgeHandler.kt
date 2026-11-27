package com.vantacode.feature.plugin.cef

import com.vantacode.core.platform.PlatformInfo
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.agent.domain.container.CommandEngine
import com.vantacode.feature.plugin.domain.PluginHostException
import com.vantacode.feature.plugin.domain.PluginHostPaths
import com.vantacode.feature.plugin.domain.PluginManager
import com.vantacode.feature.plugin.domain.PluginPage
import com.vantacode.feature.plugin.runtime.PluginChatService
import com.vantacode.feature.plugin.runtime.PluginModelService
import com.vantacode.feature.plugin.runtime.PluginRuntimeManager
import com.vantacode.feature.plugin.runtime.PluginSystemService
import com.vantacode.feature.plugin.runtime.PluginTipException
import com.vantacode.feature.plugin.runtime.PluginTipManager
import com.vantacode.feature.workspace.domain.FileAccessProvider
import com.vantacode.feature.workspace.domain.PathHomeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 页面桥：实现 aicode.js 契约。
 * 方法名与错误码和 Android 版保持一致；桌面无等价能力的返回稳定错误码而非崩溃。
 * Phase 1：补齐商店插件实际用到的 fs.*、clipboard、bash、ui.openUrl、chat.*、model.* 等方法。
 */
class PluginCefBridgeHandler(
    private val page: PluginPage,
    private val scope: CoroutineScope,
    private val pluginManager: PluginManager,
    private val runtimeManager: PluginRuntimeManager,
    private val authorized: () -> Boolean,
    private val onClose: () -> Unit,
    private val onToast: (String) -> Unit,
    // Phase 1：页面桥新增的后端服务（由 PluginPageHostDesktop 从 AppGraph 传入）。
    private val fileAccess: FileAccessProvider,
    private val homeResolver: PathHomeResolver,
    private val workspaceRoot: () -> String,
    private val commandEngine: CommandEngine,
    private val chatService: PluginChatService,
    private val modelService: PluginModelService,
    private val tipManager: PluginTipManager,
    private val systemService: PluginSystemService
) : CefMessageRouterHandlerAdapter() {

    private val closed = AtomicBoolean(false)

    /** fs.* 与 bash 的宿主路径解析（工作区挂载 + 沙箱），与 Android 版同款。 */
    private val hostPaths = PluginHostPaths(fileAccess, homeResolver, workspaceRoot, page.pluginId)

    override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: CefQueryCallback?
    ): Boolean {
        if (browser == null || frame == null || request == null) {
            callback?.failure(-1, "INVALID_REQUEST")
            return true
        }
        // 安全校验：只认虚拟 origin 的主 frame
        if (!frame.isMain || !frame.url.startsWith(PluginCefRequestHandler.ORIGIN)) {
            callback?.failure(-1, "UNTRUSTED_FRAME")
            return true
        }
        if (!authorized() || closed.get()) {
            callback?.failure(-1, "PLUGIN_INACTIVE")
            return true
        }

        val shortReq = request.take(300)
        FileLogger.d("PluginCefBridge", "dispatch start: $shortReq")
        scope.launch(Dispatchers.IO) {
            val reply = runCatching {
                kotlinx.coroutines.withTimeout(30_000L) { dispatch(request) }
            }.getOrElse { e ->
                FileLogger.e("PluginCefBridge", "dispatch failed: $shortReq", e)
                // 兜底错误也要带回请求 id，否则 aicode.js 找不到 pending 项、插件调用会一直挂起。
                val code = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "TIMEOUT"
                    is PluginHostException -> e.code
                    else -> "HOST_ERROR"
                }
                errorReply(extractId(request), code, e.message ?: "internal")
            }
            FileLogger.d("PluginCefBridge", "reply: ${reply.second.take(400)}")
            // CEF 回调切回 AWT 线程再写回 JS
            javax.swing.SwingUtilities.invokeLater {
                runCatching {
                    browser.executeJavaScript(
                        "window.__aicodeResolve(${jsString(reply.first)}, ${jsString(reply.second)})",
                        browser.url, 0
                    )
                    callback?.success("")
                }.onFailure { callback?.failure(-1, "DELIVER_FAILED") }
                Unit
            }
        }
        return true
    }

    fun markClosed() {
        closed.set(true)
    }

    /**
     * 返回 (id, payloadJsonString)。
     * 成功：`{ok:true, data:<结果>}`；失败：顶层 `{ok:false, error:{code,message}}`，
     * 与 Android 版同款、aicode.js 按此解析（错误码不再被包进 data 里丢掉）。
     */
    private suspend fun dispatch(request: String): Pair<String, String> {
        val root = runCatching { Json.parseToJsonElement(request) as JsonObject }
            .getOrElse { return errorReply("", "PARSE_ERROR", it.message ?: "parse failed") }
        val id = root["id"]?.jsonPrimitive?.content.orEmpty()
        val method = root["method"]?.jsonPrimitive?.content.orEmpty()
        val params = root["params"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            val result: JsonElement = when (method) {
                // 配置页的 aicode.invoke(handler,args) 走这里，转发给插件进程的 ui.invoke handler。
                "plugin.invoke" -> {
                    val handler = params["handler"]?.jsonPrimitive?.content.orEmpty()
                    val handlerArgs = (params["args"] as? JsonObject) ?: JsonObject(emptyMap())
                    val installed = pluginManager.plugins.value.firstOrNull { it.id == page.pluginId }
                    val runtime = installed?.content?.runtime
                    if (installed == null || runtime == null) {
                        throw PluginHostException("PLUGIN_NO_RUNTIME", "plugin runtime unavailable")
                    }
                    val version = installed.manifest?.version?.toString().orEmpty()
                    runtimeManager.invoke(
                        pluginId = page.pluginId,
                        version = version,
                        runtime = runtime,
                        handler = handler,
                        args = handlerArgs,
                        method = "ui.invoke"
                    )
                }
                "context" -> buildJsonObject {
                    put("pluginId", page.pluginId)
                    put("pageId", page.localId)
                    page.slot?.let { put("slot", it) }
                    put("language", if (com.vantacode.core.ui.Strings.current == "en") "en" else "zh-CN")
                    put("version", PlatformInfo.VERSION_NAME)
                    put("platform", "desktop")
                    // Android 版同款：home/workspace 供插件定位数据目录与工作区。
                    runCatching { homeResolver.home() }.getOrNull()?.let { put("home", it) }
                    runCatching { workspaceRoot() }.getOrNull()?.let { put("workspace", it) }
                }
                "toast" -> {
                    val message = params["message"]?.jsonPrimitive?.content.orEmpty()
                    javax.swing.SwingUtilities.invokeLater { onToast(message) }
                    buildJsonObject { put("ok", true) }
                }
                "ui.close" -> {
                    javax.swing.SwingUtilities.invokeLater { onClose() }
                    buildJsonObject { put("ok", true) }
                }
                "app.info" -> buildJsonObject {
                    put("name", "VantaCode")
                    put("version", PlatformInfo.VERSION_NAME)
                    put("platform", "desktop")
                    put("os", PlatformInfo.operatingSystem)
                }
                "log.write" -> {
                    val level = params["level"]?.jsonPrimitive?.content ?: "info"
                    val message = params["message"]?.jsonPrimitive?.content.orEmpty()
                    when (level.lowercase()) {
                        "warn" -> FileLogger.w("Plugin", message)
                        "error" -> FileLogger.e("Plugin", message)
                        else -> FileLogger.d("Plugin", message)
                    }
                    buildJsonObject { put("ok", true) }
                }
                "app.isForeground" -> buildJsonObject { put("value", true) }
                // ---- Phase 1：文件系统（与 Android 版同款，走 PluginHostPaths 沙箱） ----
                "fs.read" -> hostPaths.read(PluginHostPaths.requireString(params, "path"))
                "fs.write" -> hostPaths.write(
                    PluginHostPaths.requireString(params, "path"),
                    PluginHostPaths.requireString(params, "content")
                )
                "fs.list" -> hostPaths.list(PluginHostPaths.requireString(params, "path"))
                "fs.delete" -> hostPaths.delete(PluginHostPaths.requireString(params, "path"))
                "fs.mkdir" -> hostPaths.mkdir(PluginHostPaths.requireString(params, "path"))
                "fs.append" -> hostPaths.append(
                    PluginHostPaths.requireString(params, "path"),
                    PluginHostPaths.requireString(params, "content")
                )
                "fs.rename" -> hostPaths.rename(
                    PluginHostPaths.requireString(params, "from"),
                    PluginHostPaths.requireString(params, "to")
                )
                "fs.copy" -> hostPaths.copy(
                    PluginHostPaths.requireString(params, "from"),
                    PluginHostPaths.requireString(params, "to"),
                    (params["overwrite"] as? JsonPrimitive)?.booleanOrNull ?: false
                )
                "fs.stat" -> hostPaths.stat(PluginHostPaths.requireString(params, "path"))
                "fs.readBinary" -> hostPaths.readBinary(PluginHostPaths.requireString(params, "path"))
                "fs.writeBinary" -> hostPaths.writeBinary(
                    PluginHostPaths.requireString(params, "path"),
                    PluginHostPaths.requireString(params, "base64")
                )
                // ---- Phase 1：剪贴板 / 分享 / 打开链接 ----
                "clipboard.read" -> systemService.clipboardRead(page.pluginId)
                "clipboard.write" -> systemService.clipboardWrite(
                    page.pluginId,
                    PluginHostPaths.requireString(params, "text")
                )
                // 桌面端没有系统分享面板，PluginSystemService 会返回 SERVICE_UNAVAILABLE。
                "share.text" -> systemService.shareText(
                    page.pluginId,
                    PluginHostPaths.requireString(params, "text"),
                    (params["title"] as? JsonPrimitive)?.contentOrNull
                )
                "ui.openUrl" -> systemService.openUrl(
                    page.pluginId,
                    PluginHostPaths.requireString(params, "url")
                )
                // ---- Phase 1：命令与提示 ----
                "bash" -> bash(params)
                "tip" -> tip(params)
                // ---- Phase 1：会话 ----
                "chat.listSessions" -> chatService.listSessions(page.pluginId, params)
                "chat.createSession" -> chatService.createSession(page.pluginId, params)
                "chat.openSession" -> chatService.openSession(page.pluginId, params)
                "chat.listMessages" -> chatService.listMessages(page.pluginId, params)
                "chat.sendMessage" -> chatService.sendMessage(page.pluginId, params)
                "chat.setDraft" -> chatService.setDraft(page.pluginId, params)
                "chat.stop" -> chatService.stop(page.pluginId, params)
                // ---- Phase 1：模型 ----
                "model.listProviders" -> modelService.listProviders(page.pluginId)
                "model.listModels" -> modelService.listModels(page.pluginId, params)
                else -> throw PluginHostException("METHOD_UNKNOWN", "unsupported on desktop: $method")
            }
            id to buildJsonObject {
                put("ok", true)
                put("data", result)
            }.toString()
        } catch (e: PluginHostException) {
            errorReply(id, e.code, e.message)
        }
    }

    /**
     * Android 版同款 bash：命令长度与超时上限复用 PluginHostPaths 的常量。
     * 桌面版 CommandEngine.runCommandSyncWithExit 是 suspend，helper 也随之 suspend。
     */
    private suspend fun bash(params: JsonObject): JsonElement {
        val command = PluginHostPaths.requireString(params, "command")
        if (command.length > PluginHostPaths.MAX_COMMAND_CHARS) {
            throw PluginHostException("TOO_LARGE", "command too long")
        }
        val timeoutMs = (params["timeout"] as? JsonPrimitive)?.longOrNull
            ?.coerceIn(1L, PluginHostPaths.MAX_BASH_TIMEOUT_SECONDS * 1000L)
            ?: PluginHostPaths.DEFAULT_BASH_TIMEOUT_SECONDS * 1000L
        val result = commandEngine.runCommandSyncWithExit(command, workspaceRoot(), timeoutMs)
        return buildJsonObject {
            put("output", result.output)
            put("exitCode", result.exitCode)
            put("truncated", result.outputTruncated)
        }
    }

    /** Android 版同款 tip：把插件的提示异常翻译成宿主错误码。 */
    private fun tip(params: JsonObject): JsonElement {
        return try {
            tipManager.show(page.pluginId, params)
        } catch (e: PluginTipException) {
            throw PluginHostException(e.code, e.message.orEmpty())
        }
    }

    private fun errorReply(id: String, code: String, message: String): Pair<String, String> = id to buildJsonObject {
        put("ok", false)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }.toString()

    /** 从请求 JSON 里轻量提取 id，供 onQuery 的兜底错误回包使用（dispatch 内部已解析过，这里只做容错）。 */
    private fun extractId(request: String): String = runCatching {
        (Json.parseToJsonElement(request) as? JsonObject)?.get("id")?.jsonPrimitive?.content.orEmpty()
    }.getOrDefault("")

    /** 把字符串转成 JS 字面量，处理引号、反斜杠及 U+2028/U+2029。 */
    private fun jsString(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\u2028' -> append("\\u2028")
                '\u2029' -> append("\\u2029")
                else -> append(c)
            }
        }
        append('"')
    }
}
