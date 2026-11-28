package com.vantacode.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 浮层位置文件存储：正常往返、损坏文件容错、删除。 */
class PluginOverlayPositionStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun roundTripAndRemove() {
        val file = temp.newFile("positions.json")
        val store = FilePluginOverlayPositionStore(file)
        assertNull(store.get("jinghan.dafeiyu-pet:pet"))

        store.save("jinghan.dafeiyu-pet:pet", 100, 200)
        store.save("jieapi.desktop-pet:pet", 300, 400)
        assertEquals(100 to 200, store.get("jinghan.dafeiyu-pet:pet"))
        assertEquals(300 to 400, store.get("jieapi.desktop-pet:pet"))

        // 新实例从同一文件读回。
        val reloaded = FilePluginOverlayPositionStore(file)
        assertEquals(100 to 200, reloaded.get("jinghan.dafeiyu-pet:pet"))

        reloaded.remove("jinghan.dafeiyu-pet:pet")
        assertNull(reloaded.get("jinghan.dafeiyu-pet:pet"))
        assertEquals(300 to 400, reloaded.get("jieapi.desktop-pet:pet"))
    }

    @Test
    fun corruptFile_returnsEmptyAndRecovers() {
        val file = temp.newFile("positions.json")
        file.writeText("{not json")
        val store = FilePluginOverlayPositionStore(file)
        assertNull(store.get("x:pet"))
        store.save("x:pet", 1, 2)
        assertEquals(1 to 2, store.get("x:pet"))
        assertEquals(1 to 2, FilePluginOverlayPositionStore(file).get("x:pet"))
    }
}
