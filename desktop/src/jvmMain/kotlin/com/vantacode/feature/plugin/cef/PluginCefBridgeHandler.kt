package com.vantacode.feature.plugin.cef

import com.vantacode.core.platform.DesktopActions
import com.vantacode.core.platform.PlatformInfo
import com.vantacode.core.util.FileLogger
import com.vantacode.feature.plugin.domain.PluginPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 页面桥：实现 aicode.js 契约的最小可用子集。
 * 方法名与错误码和 Android 版保持一致；桌面无等价能力的返回稳定错误码而非崩溃。
 */
class PluginCefBridgeHandler(
    private val page: PluginPage,
    private val scope: CoroutineScope,
    private val authorized: () -> Boolean,
    private val onClose: () -> Unit,
    private val onToast: (String) -> Unit
) : CefMessageRouterHandlerAdapter() {

    private val closed = AtomicBoolean(false)

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

        scope.launch(Dispatchers.IO) {
            val reply = runCatching { dispatch(request) }
                .getOrElse { e -> errorReply("HOST_ERROR", e.message ?: "internal") }
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

    /** 返回 (id, payloadJsonString)。 */
    private fun dispatch(request: String): Pair<String, String> {
        val root = runCatching { Json.parseToJsonElement(request) as JsonObject }
            .getOrElse { return errorReply("PARSE_ERROR", it.message ?: "parse failed") }
        val id = root["id"]?.jsonPrimitive?.content.orEmpty()
        val method = root["method"]?.jsonPrimitive?.content.orEmpty()
        val params = root["params"] as? JsonObject ?: JsonObject(emptyMap())
        val result = when (method) {
            "context" -> buildJsonObject {
                put("pluginId", page.pluginId)
                put("pageId", page.localId)
                page.slot?.let { put("slot", it) }
                put("language", if (com.vantacode.core.ui.Strings.current == "en") "en" else "zh-CN")
                put("version", PlatformInfo.VERSION_NAME)
                put("platform", "desktop")
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
            else -> buildJsonObject {
                put("ok", false)
                put("error", buildJsonObject {
                    put("code", "METHOD_UNKNOWN")
                    put("message", "unsupported on desktop: $method")
                })
            }
        }
        return id to buildJsonObject {
            put("ok", result["ok"]?.jsonPrimitive?.content?.let { it == "true" } ?: true)
            put("data", result)
        }.toString()
    }

    private fun errorReply(code: String, message: String): Pair<String, String> = "" to buildJsonObject {
        put("ok", false)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }.toString()

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
