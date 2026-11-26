package com.vantacode.feature.settings.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 桌面版更新检查的 Tag 过滤（见 UpdateCheckService.kt 的 isDesktopReleaseTag）。
 * 只认 `desktop-v*` 前缀和 v1.2.0/v1.3.0/v1.4.0 三个历史 Tag；同仓库的安卓 /
 * VantaGate Release 必须被排除，否则桌面端 1.4.0 会把安卓 v5.9.4 当成更新。
 */
class UpdateTagFilterTest {

    @Test
    fun desktopTag_prefixedAccepted() {
        assertTrue(isDesktopReleaseTag("desktop-v1.5.0"))
        assertTrue(isDesktopReleaseTag("desktop-v1.5.0-rc1"))
        assertTrue(isDesktopReleaseTag("desktop-v2.0.0"))
    }

    @Test
    fun desktopTag_legacyAccepted() {
        assertTrue(isDesktopReleaseTag("v1.2.0"))
        assertTrue(isDesktopReleaseTag("v1.3.0"))
        assertTrue(isDesktopReleaseTag("v1.4.0"))
    }

    @Test
    fun desktopTag_otherProductsRejected() {
        assertFalse(isDesktopReleaseTag("v5.9.4"))
        assertFalse(isDesktopReleaseTag("v4.1"))
        assertFalse(isDesktopReleaseTag("vantagate-v2.17"))
        assertFalse(isDesktopReleaseTag("vantagate-v2.2"))
        assertFalse(isDesktopReleaseTag("vantagate-desktop-v2.0.2"))
        assertFalse(isDesktopReleaseTag("android-v5.9.5"))
        assertFalse(isDesktopReleaseTag(""))
    }

    @Test
    fun parseVersionTag_stripsDesktopPrefix() {
        assertEquals("1.5.0", parseVersionTag("desktop-v1.5.0"))
        assertEquals("1.4.0", parseVersionTag("v1.4.0"))
        assertEquals("1.5.0", parseVersionTag("desktop-v1.5.0-rc1"))
    }

    @Test
    fun compareVersions_desktopLineOnly() {
        // 过滤后只剩桌面版：1.5.0 > 1.4.0，1.4.0 不大于自身
        assertTrue(compareVersions("1.5.0", "1.4.0") > 0)
        assertEquals(0, compareVersions("1.4.0", "1.4.0"))
        assertTrue(compareVersions("1.3.0", "1.4.0") < 0)
    }
}
