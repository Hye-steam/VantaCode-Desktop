package com.vantacode.feature.plugin.cef

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.plugin.domain.PluginHostException
import com.vantacode.feature.plugin.domain.PluginHostPaths
import com.vantacode.feature.plugin.domain.PluginPage
import com.vantacode.feature.plugin.runtime.OverlayDragRegion
import com.vantacode.feature.plugin.runtime.PluginOverlayBridgeDelegate
import com.vantacode.feature.plugin.runtime.PluginOverlayException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 浮层页面的桥：实现 `aicode.overlay.*` 契约（对照 Android `PluginOverlayBridge`）。
 *
 * 只提供 overlay.* 与 context，不暴露 fs、bash、chat、model 等能力——桥的能力边界
 * 比配置页更窄，页面脚本由插件提供。
 * 与 Android 的差别（超集，均有意为之）：
 * - 多了 `overlay.create` / `overlay.update`：jieapi.desktop-pet 的浮层页面会调它们，
 *   在 Android 上会 METHOD_UNKNOWN；桌面直接路由到同一插件的 OverlayManager。
 * - `context` 多一个 `platform: "desktop"` 字段，与配置页桥一致。
 */
class PluginOverlayCefBridgeHandler(
    private val pluginId: String,
    private val overlayId: String,
    private val page: PluginPage,
    private val scope: CoroutineScope,
    private val delegate: PluginOverlayBridgeDelegate,
    private val createOverlay: suspend (JsonObject) -> JsonElement,
    private val updateOverlay: suspend (JsonObject) -> JsonElement,
    private val authorized: () -> Boolean
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

        val shortReq = request.take(300)
        FileLogger.d("PluginOverlayBridge", "dispatch start: $shortReq")
        scope.launch(Dispatchers.IO) {
            val reply = runCatching {
                kotlinx.coroutines.withTimeout(30_000L) { dispatch(request) }
            }.getOrElse { e ->
                FileLogger.e("PluginOverlayBridge", "dispatch failed: $shortReq", e)
                val code = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "TIMEOUT"
                    is PluginHostException -> e.code
                    else -> "HOST_ERROR"
                }
                errorReply(extractId(request), code, e.message ?: "internal")
            }
            FileLogger.d("PluginOverlayBridge", "reply: ${reply.second.take(400)}")
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
     * 返回 (id, payloadJsonString)，与配置页桥同款：
     * 成功 `{ok:true, data:<结果>}`；失败顶层 `{ok:false, error:{code,message}}`。
     */
    private suspend fun dispatch(request: String): Pair<String, String> {
        val root = runCatching { Json.parseToJsonElement(request) as JsonObject }
            .getOrElse { return errorReply("", "PARSE_ERROR", it.message ?: "parse failed") }
        val id = root["id"]?.jsonPrimitive?.content.orEmpty()
        val method = root["method"]?.jsonPrimitive?.content.orEmpty()
        val params = root["params"] as? JsonObject ?: JsonObject(emptyMap())
        return try {
            val result: JsonElement = when (method) {
                "context" -> buildJsonObject {
                    put("pluginId", pluginId)
                    put("overlayId", overlayId)
                    put("pageId", page.localId)
                    put("version", page.pluginVersion)
                    put("density", 1.0f)
                    put("platform", "desktop")
                }
                "overlay.self" -> delegate.selfInfo(pluginId, overlayId)
                "app.isForeground" -> JsonPrimitive(true)
                "overlay.setVisibilityPolicy" -> {
                    val policy = PluginHostPaths.requireString(params, "policy")
                    delegate.setVisibilityPolicy(pluginId, overlayId, policy)
                    JsonNull
                }
                "overlay.close" -> delegate.close(pluginId, overlayId)
                "overlay.setDragRegion" -> {
                    val left = requireFloat(params, "left")
                    val top = requireFloat(params, "top")
                    val width = requireFloat(params, "width")
                    val height = requireFloat(params, "height")
                    if (width <= 0f || height <= 0f) {
                        throw PluginHostException("PARAM_INVALID", "drag region must be positive")
                    }
                    delegate.setDragRegion(pluginId, overlayId, OverlayDragRegion(left, top, width, height))
                    JsonNull
                }
                "overlay.moveBy" -> {
                    val dx = (params["dx"] as? JsonPrimitive)?.floatOrNull ?: 0f
                    val dy = (params["dy"] as? JsonPrimitive)?.floatOrNull ?: 0f
                    delegate.moveBy(pluginId, overlayId, dx, dy)
                    JsonNull
                }
                "overlay.resize" -> {
                    val width = requireInt(params, "width")
                    val height = requireInt(params, "height")
                    val animate = (params["animate"] as? JsonPrimitive)?.booleanOrNull ?: false
                    val x = (params["x"] as? JsonPrimitive)?.floatOrNull
                    val y = (params["y"] as? JsonPrimitive)?.floatOrNull
                    if (x?.isFinite() == false || y?.isFinite() == false) {
                        throw PluginHostException("PARAM_INVALID", "overlay position must be finite")
                    }
                    delegate.resize(pluginId, overlayId, width, height, animate, x, y)
                }
                // 浮层页面只负责渲染；需要状态或副作用的消息转给插件进程。
                "overlay.postMessage" -> {
                    val payload = params["payload"] ?: JsonNull
                    delegate.postMessageToPlugin(pluginId, overlayId, payload)
                    JsonNull
                }
                "overlay.create" -> createOverlay(params)
                "overlay.update" -> updateOverlay(params)
                else -> throw PluginHostException("METHOD_UNKNOWN", "unsupported on desktop: $method")
            }
            id to buildJsonObject {
                put("ok", true)
                put("data", result)
            }.toString()
        } catch (e: PluginHostException) {
            errorReply(id, e.code, e.message)
        } catch (e: PluginOverlayException) {
            errorReply(id, e.code, e.message.orEmpty())
        }
    }

    private fun requireFloat(params: JsonObject, key: String): Float =
        (params[key] as? JsonPrimitive)?.floatOrNull
            ?: throw PluginHostException("PARAM_INVALID", "$key required")

    private fun requireInt(params: JsonObject, key: String): Int =
        (params[key] as? JsonPrimitive)?.intOrNull
            ?: throw PluginHostException("PARAM_INVALID", "$key required")

    private fun errorReply(id: String, code: String, message: String): Pair<String, String> = id to buildJsonObject {
        put("ok", false)
        put("error", buildJsonObject {
            put("code", code)
            put("message", message)
        })
    }.toString()

    /** 从请求 JSON 里轻量提取 id，供 onQuery 的兜底错误回包使用。 */
    private fun extractId(request: String): String = runCatching {
        (Json.parseToJsonElement(request) as? JsonObject)?.get("id")?.jsonPrimitive?.content.orEmpty()
    }.getOrDefault("")

    /** 把字符串转成 JS 字面量，处理引号、反斜杠及 U+2028/U+2029（与配置页桥同款）。 */
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
