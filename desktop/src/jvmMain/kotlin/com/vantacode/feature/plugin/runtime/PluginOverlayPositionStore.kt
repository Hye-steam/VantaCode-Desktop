package com.vantacode.feature.plugin.runtime

import com.vantacode.core.util.FileLogger
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** 浮层窗口位置（按 surface 键 `pluginId:surfaceId` 存取），桌面端替代 Android 的 DataStore。 */
interface PluginOverlayPositionStore {
    fun get(overlayId: String): Pair<Int, Int>?
    fun save(overlayId: String, x: Int, y: Int)
    fun remove(overlayId: String)
}

/**
 * 位置存到 `~/.vantacode/plugin-overlay-positions.json`（`{"plugin:surface": {"x":..,"y":..}}`）。
 * 文件损坏或写不进去时只记日志、退回内存态，不阻塞浮层创建。
 */
class FilePluginOverlayPositionStore(
    private val file: File
) : PluginOverlayPositionStore {

    private val lock = Any()
    private var cache: MutableMap<String, Pair<Int, Int>>? = null

    override fun get(overlayId: String): Pair<Int, Int>? = synchronized(lock) {
        loadLocked()[overlayId]
    }

    override fun save(overlayId: String, x: Int, y: Int) = synchronized(lock) {
        loadLocked()[overlayId] = x to y
        persistLocked()
    }

    override fun remove(overlayId: String) {
        synchronized(lock) {
            if (loadLocked().remove(overlayId) != null) persistLocked()
        }
    }

    private fun loadLocked(): MutableMap<String, Pair<Int, Int>> {
        cache?.let { return it }
        val loaded = mutableMapOf<String, Pair<Int, Int>>()
        runCatching {
            if (file.isFile) {
                val root = Json.parseToJsonElement(file.readText()) as? JsonObject
                root?.forEach { (key, value) ->
                    val obj = (value as? JsonObject) ?: return@forEach
                    val x = (obj["x"] as? JsonPrimitive)?.intOrNull ?: return@forEach
                    val y = (obj["y"] as? JsonPrimitive)?.intOrNull ?: return@forEach
                    loaded[key] = x to y
                }
            }
        }.onFailure { FileLogger.w(TAG, "读取浮层位置失败，已忽略历史位置: ${file.path}", it) }
        cache = loaded
        return loaded
    }

    private fun persistLocked() {
        val snapshot = cache ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val json = buildJsonObject {
                snapshot.forEach { (key, value) ->
                    put(key, buildJsonObject {
                        put("x", value.first)
                        put("y", value.second)
                    })
                }
            }
            file.writeText(json.toString())
        }.onFailure { FileLogger.w(TAG, "保存浮层位置失败: ${file.path}", it) }
    }

    private companion object {
        const val TAG = "PluginOverlay"
    }
}
