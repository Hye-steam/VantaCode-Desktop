package dev.aigw.desktop.util

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File

/**
 * 桌面端 UI 偏好（替代 Android 的 SharedPreferences）：
 * 悬浮球位置等小标志。存在 ~/.aigw/ui-prefs.json。
 */
class UiPrefs(private val file: File) {

    private val gson = Gson()
    private val lock = Any()

    private var obj: JsonObject = load()

    private fun load(): JsonObject = runCatching {
        if (!file.isFile) return JsonObject()
        gson.fromJson(file.readText(), JsonObject::class.java) ?: JsonObject()
    }.getOrDefault(JsonObject())

    fun getInt(key: String, default: Int): Int = synchronized(lock) {
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asInt ?: default
    }

    fun putInt(key: String, value: Int) = synchronized(lock) {
        obj.addProperty(key, value)
        saveLocked()
    }

    fun getBoolean(key: String, default: Boolean): Boolean = synchronized(lock) {
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default
    }

    fun putBoolean(key: String, value: Boolean) = synchronized(lock) {
        obj.addProperty(key, value)
        saveLocked()
    }

    private fun saveLocked() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(gson.toJson(obj))
            if (file.exists()) file.delete()
            tmp.renameTo(file)
        }
    }
}
