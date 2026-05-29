package com.zugaldia.speedofsound.core.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import java.nio.file.Files
import java.nio.file.Path

class PropertiesStoreTest {
    @Test
    fun `setters do not rewrite unchanged values`() {
        val baseDir = Files.createTempDirectory("speedofsound-properties-store-test")
        val store = CountingPropertiesStore(baseDir)
        val savesAfterInit = store.saveCount

        store.setString("greeting", "hello")
        assertEquals(savesAfterInit + 1, store.saveCount)
        store.setString("greeting", "hello")
        assertEquals(savesAfterInit + 1, store.saveCount)

        store.setBoolean("enabled", true)
        assertEquals(savesAfterInit + 2, store.saveCount)
        store.setBoolean("enabled", true)
        assertEquals(savesAfterInit + 2, store.saveCount)

        store.setInt("limit", 7)
        assertEquals(savesAfterInit + 3, store.saveCount)
        store.setInt("limit", 7)
        assertEquals(savesAfterInit + 3, store.saveCount)

        store.setStringArray("tags", listOf("a", "b"))
        assertEquals(savesAfterInit + 4, store.saveCount)
        store.setStringArray("tags", listOf("a", "b"))
        assertEquals(savesAfterInit + 4, store.saveCount)
    }

    private class CountingPropertiesStore(
        baseDir: Path,
        filename: String = "settings.properties",
    ) : PropertiesStore(filename = filename, baseDir = baseDir) {
        var saveCount: Int = 0

        override fun save(): Boolean {
            saveCount += 1
            return super.save()
        }
    }
}
