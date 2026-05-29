package com.zugaldia.speedofsound.core.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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

    @Test
    fun `values survive a new store instance on the same directory`() {
        val baseDir = Files.createTempDirectory("speedofsound-properties-store-roundtrip")
        val store = PropertiesStore(filename = "settings.properties", baseDir = baseDir)

        store.setString("greeting", "hello")
        store.setBoolean("enabled", true)
        store.setInt("limit", 7)
        store.setStringArray("tags", listOf("a", "b"))

        val reloaded = PropertiesStore(filename = "settings.properties", baseDir = baseDir)

        assertEquals("hello", reloaded.getString("greeting", ""))
        assertEquals(true, reloaded.getBoolean("enabled", false))
        assertEquals(7, reloaded.getInt("limit", 0))
        assertEquals(listOf("a", "b"), reloaded.getStringArray("tags", emptyList()))
    }

    @Test
    fun `constructor rejects traversal filenames`() {
        val baseDir = Files.createTempDirectory("speedofsound-properties-store-path")
        try {
            val exception = assertFailsWith<IllegalArgumentException> {
                PropertiesStore(filename = "../outside.properties", baseDir = baseDir)
            }

            assertTrue(exception.message?.contains("escapes model directory") == true)
        } finally {
            baseDir.toFile().deleteRecursively()
        }
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
