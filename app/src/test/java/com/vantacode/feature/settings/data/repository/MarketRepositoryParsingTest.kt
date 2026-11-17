package com.vantacode.feature.settings.data.repository

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 市场清单解析的回归测试。
 *
 * 这两块是最容易静默出错的地方：字段名对不上时不会抛异常，只会得到一个空列表，
 * 表现成「市场打开了但一个条目都没有」。
 */
class MarketRepositoryParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `github item maps to market entry`() {
        val body = """
            {
              "items": [
                {
                  "full_name": "owner/repo",
                  "description": "a skill",
                  "default_branch": "develop",
                  "stargazers_count": 42,
                  "language": "TypeScript"
                }
              ]
            }
        """.trimIndent()

        val entries = parseGithubReposJson(json, body)

        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("owner/repo", entry.name)
        assertEquals("repo", entry.displayName)
        assertEquals("owner", entry.author)
        assertEquals("a skill", entry.description)
        assertEquals(42, entry.stars)
        assertEquals("skill", entry.category)
        assertEquals(
            "https://github.com/owner/repo/archive/refs/heads/develop.zip",
            entry.downloadUrl
        )
        assertEquals(listOf("TypeScript"), entry.tags)
    }

    @Test
    fun `github item without optional fields still yields an entry`() {
        val body = """{"items":[{"full_name":"a/b"}]}"""

        val entries = parseGithubReposJson(json, body)

        assertEquals(1, entries.size)
        assertEquals("a/b", entries.first().name)
        assertEquals("", entries.first().description)
        assertEquals(0, entries.first().stars)
        assertTrue(entries.first().downloadUrl.endsWith("/main.zip"))
    }

    @Test
    fun `github payload without items yields empty list instead of throwing`() {
        assertTrue(parseGithubReposJson(json, """{"total_count":0}""").isEmpty())
        assertTrue(parseGithubReposJson(json, "not json at all").isEmpty())
    }

    @Test
    fun `registry accepts plugins key as alias for skills`() {
        val body = """
            {"version":1,"plugins":[{"name":"legacy","downloadUrl":"https://x/y.zip"}]}
        """.trimIndent()

        val registry = json.decodeFromString<MarketRegistry>(body)

        assertEquals(1, registry.entries.size)
        assertEquals("legacy", registry.entries.first().name)
    }

    @Test
    fun `registry prefers skills over plugins when both present`() {
        val body = """
            {
              "skills":[{"name":"new"}],
              "plugins":[{"name":"old"}]
            }
        """.trimIndent()

        val registry = json.decodeFromString<MarketRegistry>(body)

        assertEquals(listOf("new"), registry.entries.map { it.name })
    }
}
