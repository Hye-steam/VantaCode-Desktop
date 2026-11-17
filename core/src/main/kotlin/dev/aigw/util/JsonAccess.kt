package dev.aigw.util

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.Base64

fun JsonObject.objOrNull(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

fun JsonObject.arrayOrNull(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray

fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeIf { it.isJsonPrimitive }?.asString

fun JsonObject.longOrNull(name: String): Long? = get(name)?.takeIf { it.isJsonPrimitive }?.asLong

fun JsonObject.intOrNull(name: String): Int? = get(name)?.takeIf { it.isJsonPrimitive }?.asInt

fun JsonObject.boolOrNull(name: String): Boolean? = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean

fun JsonElement.asObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

fun JsonElement.asStringOrNull(): String? = takeIf { it.isJsonPrimitive }?.asString

/** 取字符串字段，缺字段/JsonNull/类型不符时回退 [fallback]。 */
fun JsonObject.str(key: String, fallback: String = ""): String {
    val v = get(key) ?: return fallback
    if (v.isJsonNull) return fallback
    return runCatching { v.asString }.getOrDefault(fallback)
}

/** 取整数字段，缺字段/JsonNull/类型不符时回退 [fallback]。 */
fun JsonObject.long(key: String, fallback: Long = 0L): Long {
    val v = get(key) ?: return fallback
    if (v.isJsonNull) return fallback
    return runCatching { v.asLong }.getOrDefault(fallback)
}

/** 取布尔字段，缺字段/JsonNull/类型不符时回退 [fallback]。 */
fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean {
    val v = get(key) ?: return fallback
    if (v.isJsonNull) return fallback
    return runCatching { v.asBoolean }.getOrDefault(fallback)
}

/** 依次取第一个字符串字段。 */
fun JsonObject.firstString(vararg keys: String): String {
    for (key in keys) {
        val value: JsonElement = get(key) ?: continue
        if (value.isJsonNull) continue
        if (value.isJsonPrimitive) return value.asString
    }
    return ""
}

/** 依次取第一个能解析为 Long 的字段。 */
fun JsonObject.firstLong(vararg keys: String): Long? {
    for (key in keys) {
        val value = get(key) ?: continue
        if (value.isJsonNull) continue
        if (value.isJsonPrimitive) {
            val prim = value.asJsonPrimitive
            if (prim.isNumber) return prim.asLong
            prim.asString.trim().toLongOrNull()?.let { return it }
        }
    }
    return null
}

/** 从 JWT（access_token）payload 里取声明；不合法/取不到返回空串。 */
fun jwtClaim(token: String, vararg names: String): String {
    val parts = token.split('.')
    if (parts.size < 2) return ""
    val payload = try {
        val decoded = Base64.getUrlDecoder().decode(parts[1].padEnd((parts[1].length + 3) / 4 * 4, '='))
        JsonParser.parseString(String(decoded, Charsets.UTF_8)).asJsonObject
    } catch (_: Exception) {
        return ""
    }
    for (name in names) {
        val value = payload.get(name) ?: continue
        if (value.isJsonNull) continue
        if (value.isJsonPrimitive) return value.asString
    }
    return ""
}
