/*
 * VantaCode Desktop — DSH plugin market index (format 1), cached on disk.
 * 索引里的 title/description 既可能是 {zh, en} 对象，也可能是纯字符串，这里统一归一化。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.core.dsh

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class MarketIndex(
    val plugins: List<MarketPlugin> = emptyList()
)

data class MarketPlugin(
    /** npm 包名或仓库名，安装参数。 */
    val name: String,
    val title: Map<String, String> = emptyMap(),
    val description: Map<String, String> = emptyMap(),
    val author: String? = null,
    val repositoryUrl: String? = null,
    val category: String? = null,
    /** 来自 metadata.sortKeys.stars，没有则 0。 */
    val stars: Long = 0L
) {
    fun localizedTitle(current: String): String =
        title[current] ?: title["zh"] ?: title["en"]?.takeIf { it.isNotBlank() } ?: name

    fun localizedDescription(current: String): String =
        description[current] ?: description["zh"] ?: description["en"].orEmpty()
}

/**
 * 手工解析 format-1 索引：字段缺失、类型漂移（字符串↔对象）都跳过该条目而不是整体失败。
 * 索引里有些条目 title 是纯字符串（838/1000），旧版严格 schema 会直接抛错、市场整体不可用。
 */
object DshMarketParser {
    fun parse(text: String): MarketIndex {
        val root = Json.parseToJsonElement(text).jsonObject
        val plugins = root["plugins"]?.jsonArray?.mapNotNull { element -> parsePlugin(element as? JsonObject) } ?: emptyList()
        return MarketIndex(plugins)
    }

    private fun parsePlugin(o: JsonObject?): MarketPlugin? {
        o ?: return null
        val name = o.stringOrNull("name") ?: return null
        val meta = o["metadata"] as? JsonObject
        val sortKeys = meta?.get("sortKeys") as? JsonObject
        val stars = sortKeys?.get("stars")?.jsonPrimitive?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0L
        val repository = o["repository"] as? JsonObject
        return MarketPlugin(
            name = name,
            title = readL10n(o["title"]),
            description = readL10n(o["description"]),
            author = o.stringOrNull("author"),
            repositoryUrl = repository?.stringOrNull("url"),
            category = meta?.stringOrNull("category"),
            stars = stars
        )
    }

    /** 字符串 → 双语映射；对象 → 取字符串值；其余 → 空。 */
    private fun readL10n(element: kotlinx.serialization.json.JsonElement?): Map<String, String> {
        return when (element) {
            is JsonPrimitive -> {
                if (element.isString) mapOf("zh" to element.content, "en" to element.content)
                else emptyMap()
            }
            is JsonObject -> element.entries
                .mapNotNull { (key, value) ->
                    (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { key to it }
                }
                .toMap()
            else -> emptyMap()
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
