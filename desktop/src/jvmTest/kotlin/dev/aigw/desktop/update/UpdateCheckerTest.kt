package dev.aigw.desktop.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面版 UpdateChecker 的单测（JUnit 4，随 :desktop:jvmTest 在 CI 跑）。
 *
 * 覆盖计划里定死的四处改动：前缀 vantagate-desktop-v、取 .exe 资产、
 * per_page=100（体现在 API 常量上不直接测）、大小写不敏感剥离。
 */
class UpdateCheckerTest {

    @Test
    fun `compare handles multi-digit segments`() {
        assertTrue(UpdateChecker.compare("2.10", "2.9") > 0)
        assertTrue(UpdateChecker.compare("2.9", "2.10") < 0)
        assertEquals(0, UpdateChecker.compare("2.17.0", "2.17"))
        assertTrue(UpdateChecker.compare("2.17.1", "2.17.0") > 0)
    }

    @Test
    fun `parseLatest picks desktop exe release and ignores other products`() {
        val body = """
        [
          {"tag_name":"desktop-v1.4.0","draft":false,"prerelease":false,
           "html_url":"https://github.com/x/releases/1",
           "assets":[{"name":"VantaCode-1.4.0-win64.exe","browser_download_url":"https://example.com/vc.exe"}],
           "body":"desktop"},
          {"tag_name":"vantagate-v2.17","draft":false,"prerelease":false,
           "html_url":"https://github.com/x/releases/2",
           "assets":[{"name":"app.apk","browser_download_url":"https://example.com/app.apk"}],
           "body":"android"},
          {"tag_name":"vantagate-desktop-v2.18.0","draft":true,"prerelease":false,
           "html_url":"https://github.com/x/releases/3","assets":[],"body":"draft"},
          {"tag_name":"vantagate-desktop-v2.17.0","draft":false,"prerelease":false,
           "html_url":"https://github.com/x/releases/4",
           "assets":[{"name":"VantaGate-2.17.0-win64.exe","browser_download_url":"https://example.com/vg.exe"}],
           "body":"desktop 2.17.0"}
        ]
        """.trimIndent()
        val rel = UpdateChecker.parseLatest(body, "2.16.0")
        assertEquals("2.17.0", rel?.version)
        assertEquals("vantagate-desktop-v2.17.0", rel?.tag)
        assertEquals("https://example.com/vg.exe", rel?.exeUrl)
    }

    @Test
    fun `parseLatest returns null when current is newest`() {
        val body = """
        [{"tag_name":"vantagate-desktop-v2.17.0","draft":false,"prerelease":false,
          "html_url":"https://github.com/x/releases/4","assets":[],"body":""}]
        """.trimIndent()
        assertNull(UpdateChecker.parseLatest(body, "2.17.0"))
        assertNull(UpdateChecker.parseLatest(body, "2.18.0"))
    }

    @Test
    fun `parseLatest strips prefix case-insensitively`() {
        val body = """
        [{"tag_name":"VantaGate-Desktop-v2.19.0","draft":false,"prerelease":false,
          "html_url":"https://github.com/x/releases/5",
          "assets":[{"name":"VantaGate-2.19.0-win64.EXE","browser_download_url":"https://example.com/vg19.exe"}],
          "body":""}]
        """.trimIndent()
        val rel = UpdateChecker.parseLatest(body, "2.17.0")
        assertEquals("2.19.0", rel?.version)
        assertEquals("https://example.com/vg19.exe", rel?.exeUrl)
    }

    @Test
    fun `parseLatest skips prereleases and falls back to page url`() {
        val body = """
        [
          {"tag_name":"vantagate-desktop-v2.20.0","draft":false,"prerelease":true,
           "html_url":"https://github.com/x/releases/6","assets":[],"body":"rc"},
          {"tag_name":"vantagate-desktop-v2.17.0","draft":false,"prerelease":false,
           "html_url":"https://github.com/x/releases/4","assets":[],"body":""}
        ]
        """.trimIndent()
        val rel = UpdateChecker.parseLatest(body, "2.16.0")
        assertEquals("2.17.0", rel?.version)
        assertNull(rel?.exeUrl)
        assertEquals("https://github.com/x/releases/4", rel?.pageUrl)
    }
}
