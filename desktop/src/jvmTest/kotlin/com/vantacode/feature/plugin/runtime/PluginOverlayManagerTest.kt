package com.vantacode.feature.plugin.runtime

import com.vantacode.feature.plugin.domain.PluginOverlayDefinition
import com.vantacode.feature.plugin.domain.PluginOverlaySurface
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * 浮层状态机单测：创建/关闭/消息/更新/拖拽/吸附/限流全部走假窗口，
 * 不建 JFrame、不碰 JCEF（窗口实际显示与拖拽手感只能在 Windows 真机上验证）。
 */
class PluginOverlayManagerTest {

    private class FakeHandle(override val overlayId: String) : PluginOverlayWindowHandle {
        var disposed = false
        val messages = mutableListOf<JsonElement>()
        var geometry: Triple<Int, Int, Pair<Int, Int>>? = null
        var dragRegion: OverlayDragRegion? = null
        var visible: Boolean? = null

        override fun postMessage(payload: JsonElement) {
            messages += payload
        }

        override fun applyGeometry(x: Int, y: Int, width: Int, height: Int) {
            geometry = Triple(x, y, width to height)
        }

        override fun applyDragRegion(region: OverlayDragRegion?) {
            dragRegion = region
        }

        override fun applyVisibility(visible: Boolean) {
            this.visible = visible
        }

        override fun dispose() {
            disposed = true
        }
    }

    private class FakeFactory : PluginOverlayWindowFactory {
        val requests = mutableListOf<PluginOverlayWindowRequest>()
        val handles = mutableMapOf<String, FakeHandle>()

        override suspend fun create(request: PluginOverlayWindowRequest): PluginOverlayWindowHandle {
            requests += request
            val handle = FakeHandle(request.overlayId)
            handles[request.overlayId] = handle
            return handle
        }
    }

    private class FakeStore : PluginOverlayPositionStore {
        val map = mutableMapOf<String, Pair<Int, Int>>()
        override fun get(overlayId: String): Pair<Int, Int>? = map[overlayId]
        override fun save(overlayId: String, x: Int, y: Int) {
            map[overlayId] = x to y
        }

        override fun remove(overlayId: String) {
            map.remove(overlayId)
        }
    }

    private lateinit var factory: FakeFactory
    private lateinit var store: FakeStore
    private lateinit var manager: PluginOverlayManager
    private val definitions = mutableMapOf<String, PluginOverlayDefinition>()

    private fun surface(
        pluginId: String,
        localId: String,
        width: Int = 240,
        height: Int = 320,
        draggable: Boolean = true,
        snap: String = "none"
    ) = PluginOverlaySurface(
        pluginId = pluginId,
        localId = localId,
        pageFile = "pet.html",
        installDir = File("/tmp"),
        pluginVersion = "1.0.0",
        width = width,
        height = height,
        draggable = draggable,
        snap = snap
    )

    private fun define(pluginId: String, vararg surfaces: PluginOverlaySurface) {
        definitions[pluginId] = PluginOverlayDefinition(pluginId, "pet", surfaces.toList())
    }

    @Before
    fun setUp() {
        factory = FakeFactory()
        store = FakeStore()
        manager = PluginOverlayManager(
            source = PluginOverlaySource { definitions[it] },
            windowFactory = factory,
            positionStore = store,
            screenSize = { 1920 to 1080 }
        )
        define("jinghan.dafeiyu-pet", surface("jinghan.dafeiyu-pet", "pet"))
        define("jieapi.desktop-pet", surface("jieapi.desktop-pet", "pet", snap = "x"))
        define("vanta.niang", surface("vanta.niang", "pet", draggable = true))
    }

    private fun str(result: JsonElement, key: String): String? =
        (result as JsonObject)[key]?.let { (it as? JsonPrimitive)?.contentOrNull }

    private fun int(result: JsonElement, key: String): Int? =
        (result as JsonObject)[key]?.let { (it as? JsonPrimitive)?.intOrNull }

    private suspend fun assertOverlayError(code: String, block: suspend () -> Unit) {
        try {
            block()
            fail("expected PluginOverlayException($code)")
        } catch (e: PluginOverlayException) {
            assertEquals(code, e.code)
        }
    }

    @Test
    fun create_returnsDescribeWithDefaultsAtBottomRight() = runTest {
        val result = manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        assertEquals("jinghan.dafeiyu-pet:pet", str(result, "overlayId"))
        assertEquals(240, int(result, "width"))
        assertEquals(320, int(result, "height"))
        // 默认右下角：1920-240-24，1080-320-96（与 Android 同款）。
        assertEquals(1656, int(result, "x"))
        assertEquals(664, int(result, "y"))
        assertEquals(1, factory.requests.size)
        assertEquals(true, factory.handles["jinghan.dafeiyu-pet:pet"]?.visible)
    }

    @Test
    fun create_honorsExplicitGeometryAndSavedPosition() = runTest {
        store.save("jinghan.dafeiyu-pet:pet", 100, 200)
        val saved = manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        assertEquals(100, int(saved, "x"))
        assertEquals(200, int(saved, "y"))
        manager.close("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet")

        val explicit = manager.create(
            "jinghan.dafeiyu-pet", "pet",
            buildJsonObject {
                put("width", 300)
                put("height", 400)
                put("x", 50)
                put("y", 60)
            }
        )
        assertEquals(300, int(explicit, "width"))
        assertEquals(400, int(explicit, "height"))
        assertEquals(50, int(explicit, "x"))
        assertEquals(60, int(explicit, "y"))
    }

    @Test
    fun create_twiceIsIdempotent() = runTest {
        val first = manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val second = manager.create("jinghan.dafeiyu-pet", "pet", JsonObject(emptyMap()))
        assertEquals(first, second)
        assertEquals(1, factory.requests.size)
    }

    @Test
    fun create_unknownSurfaceOrNoDeclarationFails() = runTest {
        assertOverlayError("SURFACE_NOT_FOUND") {
            manager.create("jinghan.dafeiyu-pet", "nope", JsonObject(emptyMap()))
        }
        assertOverlayError("OVERLAY_NOT_DECLARED") {
            manager.create("no.such-plugin", null, JsonObject(emptyMap()))
        }
    }

    @Test
    fun create_rejectsBadSizeAndPolicy() = runTest {
        assertOverlayError("PARAM_INVALID") {
            manager.create(
                "jinghan.dafeiyu-pet", null,
                buildJsonObject { put("width", 10) }
            )
        }
        assertOverlayError("PARAM_INVALID") {
            manager.create(
                "jinghan.dafeiyu-pet", null,
                buildJsonObject { put("width", 721) }
            )
        }
        assertOverlayError("PARAM_INVALID") {
            manager.create(
                "jinghan.dafeiyu-pet", null,
                buildJsonObject { put("visibilityPolicy", "sometimes") }
            )
        }
    }

    @Test
    fun close_removesAndDisposes() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val closed = manager.close("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet")
        assertEquals(true, (closed["ok"] as? JsonPrimitive)?.contentOrNull?.toBoolean())
        assertTrue(factory.handles["jinghan.dafeiyu-pet:pet"]?.disposed == true)
        assertFalse(manager.hasOverlays())
        assertOverlayError("OVERLAY_NOT_FOUND") {
            manager.close("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet")
        }
    }

    @Test
    fun close_wrongPluginDeniedAndCloseBySurface() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        assertOverlayError("OVERLAY_DENIED") {
            manager.close("jieapi.desktop-pet", "jinghan.dafeiyu-pet:pet")
        }
        manager.closeBySurface("jinghan.dafeiyu-pet", "pet")
        assertFalse(manager.hasOverlays())
    }

    @Test
    fun postMessage_routesAndChecksOwnership() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val payload = buildJsonObject { put("type", "hello") }
        manager.postMessage("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet", payload)
        val handle = factory.handles["jinghan.dafeiyu-pet:pet"]!!
        assertEquals(listOf(payload), handle.messages)
        assertOverlayError("OVERLAY_NOT_FOUND") {
            manager.postMessage("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:nope", payload)
        }
        assertOverlayError("OVERLAY_DENIED") {
            manager.postMessage("jieapi.desktop-pet", "jinghan.dafeiyu-pet:pet", payload)
        }
    }

    @Test
    fun postMessageBySurface_worksWithoutOverlayId() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val payload = buildJsonObject { put("type", "ping") }
        manager.postMessageBySurface("jinghan.dafeiyu-pet", "pet", payload)
        assertEquals(listOf(payload), factory.handles["jinghan.dafeiyu-pet:pet"]!!.messages)
    }

    @Test
    fun update_movesResizesAndHides() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val handle = factory.handles["jinghan.dafeiyu-pet:pet"]!!
        manager.update(
            "jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet",
            buildJsonObject {
                put("dx", 10)
                put("dy", -20)
            }
        )
        assertEquals(Triple(1666, 644, 240 to 320), handle.geometry)
        assertEquals(1666 to 644, store.map["jinghan.dafeiyu-pet:pet"])

        manager.update(
            "jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet",
            buildJsonObject {
                put("width", 300)
                put("height", 400)
            }
        )
        assertEquals(Triple(1666, 644, 300 to 400), handle.geometry)
        // resize 会通知页面。
        assertTrue(handle.messages.any { str(it, "type") == "resize" })

        manager.update(
            "jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet",
            buildJsonObject { put("visible", false) }
        )
        assertEquals(false, handle.visible)

        manager.update(
            "jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet",
            buildJsonObject {
                put("visible", true)
                put("visibilityPolicy", "background")
            }
        )
        // 桌面永远不在“后台”，background 策略即隐藏。
        assertEquals(false, handle.visible)

        assertOverlayError("PARAM_INVALID") {
            manager.update(
                "jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet",
                buildJsonObject { put("width", 10) }
            )
        }
    }

    @Test
    fun resizeAndMoveByAndSelf() = runTest {
        manager.create("vanta.niang", null, JsonObject(emptyMap()))
        val handle = factory.handles["vanta.niang:pet"]!!
        val resized = manager.resize("vanta.niang", "vanta.niang:pet", 300, 360, false, null, null)
        assertEquals(300, int(resized, "width"))
        assertEquals(360, int(resized, "height"))
        assertEquals(Triple(1656, 664, 300 to 360), handle.geometry)

        manager.moveBy("vanta.niang", "vanta.niang:pet", 5f, 5f)
        assertEquals(Triple(1661, 669, 300 to 360), handle.geometry)

        val self = manager.selfInfo("vanta.niang", "vanta.niang:pet")
        assertEquals(300, int(self, "width"))
        assertEquals(true, (self["foreground"] as? JsonPrimitive)?.contentOrNull?.toBoolean())
        assertEquals(1.0f, (self["density"] as? JsonPrimitive)?.contentOrNull?.toFloat())

        assertOverlayError("PARAM_INVALID") {
            manager.resize("vanta.niang", "vanta.niang:pet", 800, 360, false, null, null)
        }
    }

    @Test
    fun setDragRegion_appliesAndSkipsNonDraggable() = runTest {
        define("fixed.plugin", surface("fixed.plugin", "pet", draggable = false))
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        manager.create("fixed.plugin", null, JsonObject(emptyMap()))

        val region = OverlayDragRegion(0f, 0f, 100f, 80f)
        manager.setDragRegion("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet", region)
        assertEquals(region, factory.handles["jinghan.dafeiyu-pet:pet"]!!.dragRegion)

        manager.setDragRegion("fixed.plugin", "fixed.plugin:pet", region)
        assertNull(factory.handles["fixed.plugin:pet"]!!.dragRegion)

        assertOverlayError("PARAM_INVALID") {
            manager.setVisibilityPolicy("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet", "nope")
        }
    }

    @Test
    fun closePlugin_closesOnlyThatPlugin() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        manager.create("jieapi.desktop-pet", null, JsonObject(emptyMap()))
        manager.closePlugin("jinghan.dafeiyu-pet")
        assertTrue(factory.handles["jinghan.dafeiyu-pet:pet"]?.disposed == true)
        assertFalse(factory.handles["jieapi.desktop-pet:pet"]?.disposed == true)
        assertTrue(manager.hasOverlays())
        manager.closeAll()
        assertFalse(manager.hasOverlays())
        assertTrue(factory.handles["jieapi.desktop-pet:pet"]?.disposed == true)
    }

    @Test
    fun limits_perPluginAndTotal() = runTest {
        define(
            "many.plugin",
            surface("many.plugin", "a"), surface("many.plugin", "b"),
            surface("many.plugin", "c"), surface("many.plugin", "d")
        )
        manager.create("many.plugin", "a", JsonObject(emptyMap()))
        manager.create("many.plugin", "b", JsonObject(emptyMap()))
        manager.create("many.plugin", "c", JsonObject(emptyMap()))
        assertOverlayError("OVERLAY_LIMIT") {
            manager.create("many.plugin", "d", JsonObject(emptyMap()))
        }
    }

    @Test
    fun dragEnd_snapsToEdgeAndPersists() = runTest {
        manager.create("jieapi.desktop-pet", null, JsonObject(emptyMap()))
        val handle = factory.handles["jieapi.desktop-pet:pet"]!!
        val listener = factory.requests.single().listener
        // snap=x，松手离左边缘 10px → 贴左边缘。
        listener.onDragEnd("jieapi.desktop-pet:pet", 10, 500)
        assertEquals(Triple(0, 500, 240 to 320), handle.geometry)
        assertEquals(0 to 500, store.map["jieapi.desktop-pet:pet"])
        assertEquals("dragEnd", str(handle.messages[0], "type"))
        assertEquals("snap", str(handle.messages[1], "type"))
        assertEquals("left", str(handle.messages[1], "edge"))
    }

    @Test
    fun dragEnd_farFromEdgeStaysPut() = runTest {
        manager.create("jieapi.desktop-pet", null, JsonObject(emptyMap()))
        val handle = factory.handles["jieapi.desktop-pet:pet"]!!
        val listener = factory.requests.single().listener
        listener.onDragEnd("jieapi.desktop-pet:pet", 500, 500)
        assertEquals(500 to 500, store.map["jieapi.desktop-pet:pet"])
        assertTrue(handle.messages.none { str(it, "type") == "snap" })
    }

    @Test
    fun clickAndLongPress_forwardToPage() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val handle = factory.handles["jinghan.dafeiyu-pet:pet"]!!
        val listener = factory.requests.single().listener
        listener.onDragStart("jinghan.dafeiyu-pet:pet")
        listener.onClick("jinghan.dafeiyu-pet:pet")
        listener.onLongPress("jinghan.dafeiyu-pet:pet")
        assertEquals(listOf("dragStart", "click", "longPress"), handle.messages.map { str(it, "type") })
    }

    @Test
    fun pageMessage_forwardsToEventSink() = runTest {
        manager.create("jinghan.dafeiyu-pet", null, JsonObject(emptyMap()))
        val received = mutableListOf<Triple<String, String, JsonElement>>()
        manager.setEventSink { pluginId, overlayId, payload ->
            received += Triple(pluginId, overlayId, payload)
        }
        val payload = buildJsonObject { put("type", "petClicked") }
        manager.postMessageToPlugin("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet", payload)
        assertEquals(listOf(Triple("jinghan.dafeiyu-pet", "jinghan.dafeiyu-pet:pet", payload)), received)
    }

    @Test
    fun snapTarget_unitChecks() {
        assertEquals(
            Triple("left", 0, 500),
            PluginOverlayManager.snapTarget("x", 10, 500, 240, 320, 1920, 1080)
        )
        assertEquals(
            Triple("right", 1680, 500),
            PluginOverlayManager.snapTarget("x", 1670, 500, 240, 320, 1920, 1080)
        )
        assertNull(PluginOverlayManager.snapTarget("none", 10, 500, 240, 320, 1920, 1080))
        assertNull(PluginOverlayManager.snapTarget("x", 500, 500, 240, 320, 1920, 1080))
    }
}
