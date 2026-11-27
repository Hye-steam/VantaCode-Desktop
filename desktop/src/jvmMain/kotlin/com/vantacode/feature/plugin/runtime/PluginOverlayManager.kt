package com.vantacode.feature.plugin.runtime

import com.vantacode.core.util.FileLogger
import com.vantacode.feature.plugin.domain.LocalizedText
import com.vantacode.feature.plugin.domain.PluginHostPaths
import com.vantacode.feature.plugin.domain.PluginOverlayDefinition
import com.vantacode.feature.plugin.domain.PluginOverlaySnap
import com.vantacode.feature.plugin.domain.PluginOverlaySurface
import com.vantacode.feature.plugin.domain.PluginPage
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** 插件浮层创建或操作失败；调用方转成 host API 错误码。 */
class PluginOverlayException(val code: String, message: String) : Exception(message)

/** 浮层窗口里的可拖拽区域，单位是页面 CSS 像素（桌面端 CSS 像素即物理像素）。 */
data class OverlayDragRegion(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    /** 是否覆盖整个窗口；覆盖时窗口改尺寸后拖拽区跟着撑满。 */
    fun coversFull(width: Int, height: Int): Boolean =
        left <= 0f && top <= 0f && right >= width && bottom >= height
}

/** 浮层窗口的宿主侧句柄：状态机只通过它操作窗口，窗口实现（JFrame + JCEF）在 cef 包。 */
interface PluginOverlayWindowHandle {
    val overlayId: String

    /** 把消息发给浮层页面里的 JS（`window.__aicodeOverlayMessage`）。 */
    fun postMessage(payload: JsonElement)

    fun applyGeometry(x: Int, y: Int, width: Int, height: Int)
    fun applyDragRegion(region: OverlayDragRegion?)
    fun applyVisibility(visible: Boolean)
    fun dispose()
}

/** 窗口实现回传给状态机的用户手势（拖拽/点击/长按）；状态机负责更新位置并通知页面。 */
interface PluginOverlayWindowListener {
    fun onDragStart(overlayId: String)
    fun onDragEnd(overlayId: String, x: Int, y: Int)
    fun onClick(overlayId: String)
    fun onLongPress(overlayId: String)
}

/**
 * 浮层页面桥（`aicode.overlay.*`）调回状态机的入口。
 * 桥只认自己绑定的 overlayId，不接受页面传来的其它 overlayId，因此这里不再重复校验归属之外的东西。
 */
interface PluginOverlayBridgeDelegate {
    fun selfInfo(pluginId: String, overlayId: String): JsonObject
    fun resize(
        pluginId: String,
        overlayId: String,
        width: Int,
        height: Int,
        animate: Boolean,
        x: Float?,
        y: Float?
    ): JsonObject

    fun moveBy(pluginId: String, overlayId: String, dx: Float, dy: Float)
    fun setDragRegion(pluginId: String, overlayId: String, region: OverlayDragRegion)
    fun setVisibilityPolicy(pluginId: String, overlayId: String, policy: String)
    fun close(pluginId: String, overlayId: String): JsonObject
    fun postMessageToPlugin(pluginId: String, overlayId: String, payload: JsonElement)
}

/** 建浮层窗口的请求：窗口实现据此建 JFrame + JCEF 浏览器并加载插件指定的 overlay 页面。 */
data class PluginOverlayWindowRequest(
    val page: PluginPage,
    val surface: PluginOverlaySurface,
    val overlayId: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val listener: PluginOverlayWindowListener,
    val bridgeDelegate: PluginOverlayBridgeDelegate,
    /** 浮层页面调 `aicode.overlay.create`：与进程侧 `host.overlay.create` 同款。 */
    val createOverlay: suspend (JsonObject) -> JsonElement,
    /** 浮层页面调 `aicode.overlay.update`：与进程侧 `host.overlay.update` 同款。 */
    val updateOverlay: suspend (JsonObject) -> JsonElement
)

/** 浮层窗口工厂：生产实现建真窗口，单元测试用假窗口。 */
interface PluginOverlayWindowFactory {
    suspend fun create(request: PluginOverlayWindowRequest): PluginOverlayWindowHandle
}

/**
 * 插件声明的 overlay 查找：生产实现读 [com.vantacode.feature.plugin.domain.PluginManager]，
 * 只认已启用且状态正常的插件；测试用假实现。
 */
fun interface PluginOverlaySource {
    fun activeOverlay(pluginId: String): PluginOverlayDefinition?
}

/**
 * 桌面端插件浮层的唯一宿主（对照 Android `PluginOverlayManager`）。
 *
 * 只在插件调用 `host.overlay.create`（或配置页调 `aicode.overlay.create`）时建窗：
 * 桌宠是插件行为，不是内置功能——没有插件调用就不会出现任何悬浮窗，启动时也不会弹。
 * 窗口、位置与可见性状态都由这里持有；插件只拿到 overlayId 与消息通道。
 *
 * 与 Android 的对照与取舍：
 * - 尺寸单位：Android 用 dp，桌面端 CSS 像素即物理像素（density = 1），`create`/`resize`
 *   的宽高在桌面直接按像素解释；上限沿用 Android 的 48..720 x 48..1200。
 * - 权限：Android 要系统悬浮窗权限 + 插件单独授权；桌面窗口是应用自己的窗口，
 *   不需要系统授权，因此 `create` 不做授权检查——装了插件、插件调了 create 就能建。
 * - 前后台：桌面没有 Android 式的前后台状态，`isAppForeground()` 恒为 true；
 *   `visibilityPolicy` 为 `background` 时浮层隐藏（桌面永远不在“后台”），
 *   `always`/`foreground` 时按 `requestedVisible` 显示。
 * - 吸附：沿用 Android 的 snap（x/y/both、40px 触发），松手后直接贴边，不做动画。
 * - resize 动画：Android 有 280ms 动画，桌面直接改尺寸（`animate` 参数接受但忽略）。
 * - 位置持久化：Android 用 DataStore，桌面用 `~/.vantacode/plugin-overlay-positions.json`。
 * - host 侧比 Android 多了 `resize/moveBy/self/setDragRegion/setVisibilityPolicy`：
 *   Android 的 host 桥只有 create/close/postMessage/update，这几个方法在 Android 上是
 *   METHOD_UNKNOWN；但 vanta.niang 的进程侧调了 `host.overlay.resize`，桌面一并补齐，
 *   语义与页面侧 `aicode.overlay.*` 相同。
 */
@Singleton
class PluginOverlayManager @Inject constructor(
    private val source: PluginOverlaySource,
    private val windowFactory: PluginOverlayWindowFactory,
    private val positionStore: PluginOverlayPositionStore,
    private val screenSize: () -> Pair<Int, Int> = { defaultScreenSize() }
) : PluginOverlayWindowListener, PluginOverlayBridgeDelegate {

    private class OverlayRecord(
        val pluginId: String,
        val surface: PluginOverlaySurface,
        val page: PluginPage,
        val handle: PluginOverlayWindowHandle,
        var x: Int,
        var y: Int,
        var width: Int,
        var height: Int,
        var visibilityPolicy: String,
        var requestedVisible: Boolean,
        var dragRegion: OverlayDragRegion,
        var snappedEdge: String?
    ) {
        val overlayId: String get() = surface.key
    }

    private val windows = ConcurrentHashMap<String, OverlayRecord>()
    private val createMutex = Mutex()
    private val lock = Any()

    /** 浮层页面发给插件进程的消息；由 PluginRuntimeManager 接入，转调 `overlay.message` handler。 */
    @Volatile
    private var eventSink: ((pluginId: String, overlayId: String, payload: JsonElement) -> Unit)? = null

    fun setEventSink(sink: ((pluginId: String, overlayId: String, payload: JsonElement) -> Unit)?) {
        eventSink = sink
    }

    fun hasOverlays(): Boolean = windows.isNotEmpty()

    /** 桌面没有 Android 式的前后台状态：应用在运行即前台。 */
    fun isAppForeground(): Boolean = true

    suspend fun create(
        pluginId: String,
        surfaceId: String?,
        params: JsonObject
    ): JsonObject = createMutex.withLock {
        val declaration = source.activeOverlay(pluginId)
            ?: throw PluginOverlayException("OVERLAY_NOT_DECLARED", "plugin did not declare overlay: $pluginId")
        val surface = when {
            surfaceId.isNullOrBlank() -> declaration.surfaces.firstOrNull()
            else -> declaration.surfaces.firstOrNull { it.localId == surfaceId }
        } ?: throw PluginOverlayException("SURFACE_NOT_FOUND", "overlay surface not found: $surfaceId")
        val width = readSize(params, "width", surface.width, MAX_WIDTH_PX)
        val height = readSize(params, "height", surface.height, MAX_HEIGHT_PX)
        val visibilityPolicy = readVisibilityPolicy(params, "visibilityPolicy", "always")
        synchronized(lock) {
            windows[surface.key]?.let { existing ->
                if (existing.visibilityPolicy != visibilityPolicy) {
                    existing.visibilityPolicy = visibilityPolicy
                    applyVisibility(existing)
                }
                return@withLock existing.describe()
            }
        }
        synchronized(lock) {
            val perPlugin = windows.values.count { it.pluginId == pluginId }
            if (perPlugin >= MAX_SURFACES_PER_PLUGIN) {
                throw PluginOverlayException("OVERLAY_LIMIT", "too many overlays for this plugin")
            }
            if (windows.size >= MAX_SURFACES_TOTAL) {
                throw PluginOverlayException("OVERLAY_LIMIT", "too many overlays")
            }
        }
        val (screenWidth, screenHeight) = screenSize()
        val savedPosition = positionStore.get(surface.key)
        val x = ((params["x"] as? JsonPrimitive)?.intOrNull
            ?: savedPosition?.first
            ?: (screenWidth - width - 24))
            .coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val y = ((params["y"] as? JsonPrimitive)?.intOrNull
            ?: savedPosition?.second
            ?: (screenHeight - height - 96))
            .coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        val page = PluginPage(
            pluginId = surface.pluginId,
            localId = surface.localId,
            title = LocalizedText.single(surface.localId),
            slot = null,
            pageFile = surface.pageFile,
            icon = "circle",
            installDir = surface.installDir,
            pluginVersion = surface.pluginVersion
        )
        val request = PluginOverlayWindowRequest(
            page = page,
            surface = surface,
            overlayId = surface.key,
            x = x,
            y = y,
            width = width,
            height = height,
            listener = this,
            bridgeDelegate = this,
            createOverlay = { p ->
                create(
                    pluginId,
                    (p["surface"] as? JsonPrimitive)?.contentOrNull,
                    p
                )
            },
            updateOverlay = { p ->
                update(pluginId, PluginHostPaths.requireString(p, "overlayId"), p)
            }
        )
        val handle = windowFactory.create(request)
        val record = OverlayRecord(
            pluginId = pluginId,
            surface = surface,
            page = page,
            handle = handle,
            x = x,
            y = y,
            width = width,
            height = height,
            visibilityPolicy = visibilityPolicy,
            requestedVisible = true,
            dragRegion = OverlayDragRegion(0f, 0f, width.toFloat(), height.toFloat()),
            snappedEdge = null
        )
        synchronized(lock) {
            // 建窗期间可能已有并发 create 建好同一 surface，复用它并丢弃刚建好的窗口。
            windows[surface.key]?.let { existing ->
                runCatching { handle.dispose() }
                if (existing.visibilityPolicy != visibilityPolicy) {
                    existing.visibilityPolicy = visibilityPolicy
                    applyVisibility(existing)
                }
                return@withLock existing.describe()
            }
            windows[surface.key] = record
        }
        applyVisibility(record)
        record.describe()
    }

    override fun close(pluginId: String, overlayId: String): JsonObject {
        val record = synchronized(lock) { windows[overlayId] }
            ?: throw PluginOverlayException("OVERLAY_NOT_FOUND", "overlay not found: $overlayId")
        if (record.pluginId != pluginId) {
            throw PluginOverlayException("OVERLAY_DENIED", "overlay does not belong to this plugin")
        }
        if (windows.remove(overlayId, record)) disposeRecord(record)
        return ok()
    }

    fun closeBySurface(pluginId: String, surfaceId: String): JsonObject {
        val key = "$pluginId:$surfaceId"
        val record = synchronized(lock) { windows[key] }
            ?: throw PluginOverlayException("OVERLAY_NOT_FOUND", "overlay not found: $surfaceId")
        if (record.pluginId != pluginId) {
            throw PluginOverlayException("OVERLAY_DENIED", "overlay does not belong to this plugin")
        }
        if (windows.remove(key, record)) disposeRecord(record)
        return ok()
    }

    /** 插件停用、卸载或版本替换时关掉它建的全部浮层。 */
    fun closePlugin(pluginId: String) {
        val mine = synchronized(lock) {
            val found = windows.values.filter { it.pluginId == pluginId }
            found.forEach { windows.remove(it.overlayId, it) }
            found
        }
        mine.forEach { disposeRecord(it) }
    }

    /** 宿主退出时关掉全部浮层。 */
    fun closeAll() {
        val all = synchronized(lock) {
            val found = windows.values.toList()
            windows.clear()
            found
        }
        all.forEach { disposeRecord(it) }
    }

    fun postMessage(pluginId: String, overlayId: String, payload: JsonElement): JsonObject {
        val record = requireOwned(pluginId, overlayId)
        postToPage(record, payload)
        return ok()
    }

    /** 不依赖 overlayId，按 surface 查找当前窗口；适合常驻进程主动推状态。 */
    fun postMessageBySurface(pluginId: String, surfaceId: String, payload: JsonElement): JsonObject {
        val record = synchronized(lock) { windows["$pluginId:$surfaceId"] }
            ?: throw PluginOverlayException("OVERLAY_NOT_FOUND", "overlay not found: $surfaceId")
        if (record.pluginId != pluginId) {
            throw PluginOverlayException("OVERLAY_DENIED", "overlay does not belong to this plugin")
        }
        postToPage(record, payload)
        return ok()
    }

    fun update(pluginId: String, overlayId: String, params: JsonObject): JsonObject {
        val record = requireOwned(pluginId, overlayId)
        val dx = (params["dx"] as? JsonPrimitive)?.floatOrNull
        val dy = (params["dy"] as? JsonPrimitive)?.floatOrNull
        if (dx != null || dy != null) moveByInternal(record, dx ?: 0f, dy ?: 0f)
        val width = (params["width"] as? JsonPrimitive)?.intOrNull
        val height = (params["height"] as? JsonPrimitive)?.intOrNull
        if (width != null || height != null) {
            val (currentWidth, currentHeight) = synchronized(lock) { record.width to record.height }
            resizeInternal(record, width ?: currentWidth, height ?: currentHeight, animate = false, x = null, y = null)
        }
        val visible = (params["visible"] as? JsonPrimitive)?.booleanOrNull
        if (visible != null) {
            synchronized(lock) { record.requestedVisible = visible }
            applyVisibility(record)
        }
        val visibilityPolicy = (params["visibilityPolicy"] as? JsonPrimitive)?.contentOrNull
        if (visibilityPolicy != null) {
            val policy = validateVisibilityPolicy(visibilityPolicy)
            synchronized(lock) { record.visibilityPolicy = policy }
            applyVisibility(record)
        }
        return ok()
    }

    // ---- PluginOverlayBridgeDelegate（浮层页面调 aicode.overlay.*） ----

    override fun selfInfo(pluginId: String, overlayId: String): JsonObject {
        val record = requireOwned(pluginId, overlayId)
        return synchronized(lock) { record.selfInfo() }
    }

    override fun resize(
        pluginId: String,
        overlayId: String,
        width: Int,
        height: Int,
        animate: Boolean,
        x: Float?,
        y: Float?
    ): JsonObject {
        val record = requireOwned(pluginId, overlayId)
        resizeInternal(record, width, height, animate, x, y)
        return buildJsonObject {
            put("width", width)
            put("height", height)
        }
    }

    override fun moveBy(pluginId: String, overlayId: String, dx: Float, dy: Float) {
        val record = requireOwned(pluginId, overlayId)
        moveByInternal(record, dx, dy)
    }

    override fun setDragRegion(pluginId: String, overlayId: String, region: OverlayDragRegion) {
        val record = requireOwned(pluginId, overlayId)
        if (!record.surface.draggable) return
        synchronized(lock) { record.dragRegion = region }
        runCatching { record.handle.applyDragRegion(region) }
    }

    override fun setVisibilityPolicy(pluginId: String, overlayId: String, policy: String) {
        val record = requireOwned(pluginId, overlayId)
        val validated = validateVisibilityPolicy(policy)
        synchronized(lock) { record.visibilityPolicy = validated }
        applyVisibility(record)
    }

    override fun postMessageToPlugin(pluginId: String, overlayId: String, payload: JsonElement) {
        requireOwned(pluginId, overlayId)
        eventSink?.invoke(pluginId, overlayId, payload)
    }

    // ---- PluginOverlayWindowListener（窗口手势回传） ----

    override fun onDragStart(overlayId: String) {
        val record = synchronized(lock) { windows[overlayId] } ?: return
        postToPage(record, buildJsonObject { put("type", "dragStart") })
    }

    override fun onDragEnd(overlayId: String, x: Int, y: Int) {
        val record = synchronized(lock) { windows[overlayId] } ?: return
        var snap: Triple<String, Int, Int>? = null
        val (nx, ny) = synchronized(lock) {
            val (screenWidth, screenHeight) = screenSize()
            var nextX = x.coerceIn(0, (screenWidth - record.width).coerceAtLeast(0))
            var nextY = y.coerceIn(0, (screenHeight - record.height).coerceAtLeast(0))
            snap = snapTarget(
                record.surface.snap, nextX, nextY,
                record.width, record.height, screenWidth, screenHeight
            )
            if (snap != null) {
                record.snappedEdge = snap!!.first
                nextX = snap!!.second
                nextY = snap!!.third
                record.handle.applyGeometry(nextX, nextY, record.width, record.height)
            } else {
                record.snappedEdge = null
            }
            record.x = nextX
            record.y = nextY
            positionStore.save(record.overlayId, nextX, nextY)
            nextX to nextY
        }
        postToPage(record, buildJsonObject {
            put("type", "dragEnd")
            put("x", nx)
            put("y", ny)
        })
        snap?.let { (edge, sx, sy) ->
            postToPage(record, buildJsonObject {
                put("type", "snap")
                put("edge", edge)
                put("x", sx)
                put("y", sy)
            })
        }
    }

    override fun onClick(overlayId: String) {
        val record = synchronized(lock) { windows[overlayId] } ?: return
        postToPage(record, buildJsonObject { put("type", "click") })
    }

    override fun onLongPress(overlayId: String) {
        val record = synchronized(lock) { windows[overlayId] } ?: return
        postToPage(record, buildJsonObject { put("type", "longPress") })
    }

    // ---- 内部 ----

    private fun requireOwned(pluginId: String, overlayId: String): OverlayRecord {
        val record = synchronized(lock) { windows[overlayId] }
            ?: throw PluginOverlayException("OVERLAY_NOT_FOUND", "overlay not found: $overlayId")
        if (record.pluginId != pluginId) {
            throw PluginOverlayException("OVERLAY_DENIED", "overlay does not belong to this plugin")
        }
        return record
    }

    private fun moveByInternal(record: OverlayRecord, dx: Float, dy: Float) {
        val (screenWidth, screenHeight) = screenSize()
        synchronized(lock) {
            record.snappedEdge = null
            record.x = (record.x + dx.toInt()).coerceIn(0, (screenWidth - record.width).coerceAtLeast(0))
            record.y = (record.y + dy.toInt()).coerceIn(0, (screenHeight - record.height).coerceAtLeast(0))
            positionStore.save(record.overlayId, record.x, record.y)
            record.handle.applyGeometry(record.x, record.y, record.width, record.height)
        }
    }

    private fun resizeInternal(
        record: OverlayRecord,
        width: Int,
        height: Int,
        animate: Boolean,
        x: Float?,
        y: Float?
    ) {
        // animate 接受但忽略：桌面直接改尺寸，不做 Android 那种 280ms 动画。
        if (width !in MIN_PX..MAX_WIDTH_PX || height !in MIN_PX..MAX_HEIGHT_PX) {
            throw PluginOverlayException("PARAM_INVALID", "overlay size out of range")
        }
        if (x?.isFinite() == false || y?.isFinite() == false) {
            throw PluginOverlayException("PARAM_INVALID", "overlay position must be finite")
        }
        val (screenWidth, screenHeight) = screenSize()
        val (toX, toY) = synchronized(lock) {
            val maxX = (screenWidth - width).coerceAtLeast(0)
            val maxY = (screenHeight - height).coerceAtLeast(0)
            val wasFull = record.dragRegion.coversFull(record.width, record.height)
            val nextX = when {
                x != null -> x.toInt()
                record.snappedEdge == "left" -> 0
                record.snappedEdge == "right" -> maxX
                else -> record.x
            }.coerceIn(0, maxX)
            val nextY = when {
                y != null -> y.toInt()
                record.snappedEdge == "top" -> 0
                record.snappedEdge == "bottom" -> maxY
                else -> record.y
            }.coerceIn(0, maxY)
            if (wasFull) record.dragRegion = OverlayDragRegion(0f, 0f, width.toFloat(), height.toFloat())
            record.width = width
            record.height = height
            record.x = nextX
            record.y = nextY
            positionStore.save(record.overlayId, nextX, nextY)
            record.handle.applyGeometry(nextX, nextY, width, height)
            if (wasFull) record.handle.applyDragRegion(record.dragRegion)
            nextX to nextY
        }
        postToPage(record, buildJsonObject {
            put("type", "resize")
            put("width", width)
            put("height", height)
            put("x", toX)
            put("y", toY)
        })
    }

    private fun applyVisibility(record: OverlayRecord) {
        val visible = synchronized(lock) {
            record.requestedVisible && record.visibilityPolicy != "background"
        }
        runCatching { record.handle.applyVisibility(visible) }
    }

    private fun postToPage(record: OverlayRecord, payload: JsonElement) {
        runCatching { record.handle.postMessage(payload) }
            .onFailure { FileLogger.w(TAG, "overlay postMessage failed: ${record.overlayId}", it) }
    }

    private fun disposeRecord(record: OverlayRecord) {
        runCatching { record.handle.dispose() }
            .onFailure { FileLogger.w(TAG, "overlay dispose failed: ${record.overlayId}", it) }
    }

    private fun OverlayRecord.describe(): JsonObject = synchronized(lock) {
        buildJsonObject {
            put("overlayId", overlayId)
            put("pluginId", pluginId)
            put("width", width)
            put("height", height)
            put("x", x)
            put("y", y)
            put("draggable", surface.draggable)
            put("snap", surface.snap)
            put("visibilityPolicy", visibilityPolicy)
        }
    }

    private fun OverlayRecord.selfInfo(): JsonObject = synchronized(lock) {
        buildJsonObject {
            put("overlayId", overlayId)
            put("pluginId", pluginId)
            put("width", width)
            put("height", height)
            put("x", x)
            put("y", y)
            put("density", 1.0f)
            put("draggable", surface.draggable)
            put("snap", surface.snap)
            put("visibilityPolicy", visibilityPolicy)
            put("foreground", true)
        }
    }

    private fun ok(): JsonObject = buildJsonObject { put("ok", true) }

    private fun readSize(params: JsonObject, key: String, default: Int, max: Int): Int {
        val raw = params[key] ?: return default
        val value = (raw as? JsonPrimitive)?.intOrNull
            ?: throw PluginOverlayException("PARAM_INVALID", "$key must be an integer")
        if (value !in MIN_PX..max) {
            throw PluginOverlayException("PARAM_INVALID", "$key out of range")
        }
        return value
    }

    private fun readVisibilityPolicy(params: JsonObject, key: String, default: String): String {
        val raw = params[key] ?: return default
        val value = (raw as? JsonPrimitive)?.contentOrNull
            ?: throw PluginOverlayException("PARAM_INVALID", "$key must be a string")
        return validateVisibilityPolicy(value)
    }

    private fun validateVisibilityPolicy(value: String): String {
        if (value !in VISIBILITY_POLICIES) {
            throw PluginOverlayException("PARAM_INVALID", "unsupported visibilityPolicy: $value")
        }
        return value
    }

    companion object {
        const val TAG = "PluginOverlay"
        const val MAX_SURFACES_PER_PLUGIN = 3
        const val MAX_SURFACES_TOTAL = 8

        /** 桌面端 CSS 像素即物理像素（density = 1），尺寸上下限沿用 Android 的 dp 数值。 */
        const val MIN_PX = 48
        const val MAX_WIDTH_PX = 720
        const val MAX_HEIGHT_PX = 1200

        /** 吸附触发距离（像素），与 Android 的 40dp 同值。 */
        const val SNAP_TRIGGER_PX = 40

        val VISIBILITY_POLICIES = setOf("always", "foreground", "background")

        /**
         * 松手后贴边目标；只有边缘距屏幕边缘小于 [SNAP_TRIGGER_PX] 时才吸附，
         * 多个候选取最近的。无候选返回 null（停在松手位置）。
         */
        fun snapTarget(
            snap: String,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
            screenWidth: Int,
            screenHeight: Int
        ): Triple<String, Int, Int>? {
            val allowX = snap == PluginOverlaySnap.X || snap == PluginOverlaySnap.BOTH
            val allowY = snap == PluginOverlaySnap.Y || snap == PluginOverlaySnap.BOTH
            if (!allowX && !allowY) return null
            val maxX = (screenWidth - width).coerceAtLeast(0)
            val maxY = (screenHeight - height).coerceAtLeast(0)
            val candidates = mutableListOf<Pair<Int, Triple<String, Int, Int>>>()
            if (allowX) {
                if (x <= SNAP_TRIGGER_PX) candidates += x to Triple("left", 0, y)
                if (maxX - x <= SNAP_TRIGGER_PX) candidates += (maxX - x) to Triple("right", maxX, y)
            }
            if (allowY) {
                if (y <= SNAP_TRIGGER_PX) candidates += y to Triple("top", x, 0)
                if (maxY - y <= SNAP_TRIGGER_PX) candidates += (maxY - y) to Triple("bottom", x, maxY)
            }
            return candidates.minByOrNull { it.first }?.second
        }

        /** 主屏尺寸；无图形环境（CI 单测走假实现，不会调到这里）时退回 1920x1080。 */
        fun defaultScreenSize(): Pair<Int, Int> {
            if (java.awt.GraphicsEnvironment.isHeadless()) return 1920 to 1080
            return runCatching {
                val size = java.awt.Toolkit.getDefaultToolkit().screenSize
                size.width to size.height
            }.getOrDefault(1920 to 1080)
        }
    }
}
