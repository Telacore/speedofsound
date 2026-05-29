package com.zugaldia.speedofsound.core.io

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class AtomicFileWriterTest {
    private val tempDir: File = Files.createTempDirectory("atomic-file-writer-test").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `write replaces destination atomically on success`() {
        val destination = tempDir.resolve("settings.json")
        destination.writeText("old")

        val result = AtomicFileWriter.write(destination) { tempFile ->
            tempFile.writeText("new")
        }

        assertTrue(result.isSuccess)
        assertEquals("new", destination.readText())
        assertEquals(listOf(destination.name), tempDir.listFiles()?.map { it.name }?.sorted())
    }

    @Test
    fun `write keeps destination untouched on failure`() {
        val destination = tempDir.resolve("settings.json")
        destination.writeText("old")

        val result = AtomicFileWriter.write(destination) { tempFile ->
            tempFile.writeText("partial")
            throw IllegalStateException("boom")
        }

        assertTrue(result.isFailure)
        assertEquals("old", destination.readText())
        assertEquals(listOf(destination.name), tempDir.listFiles()?.map { it.name }?.sorted())
    }

    @Test
    fun `write fails when parent path is not a directory`() {
        val parentPath = tempDir.resolve("parent-file")
        parentPath.writeText("not-a-directory")
        val destination = parentPath.resolve("settings.json")

        val result = AtomicFileWriter.write(destination) { tempFile ->
            tempFile.writeText("new")
        }

        assertTrue(result.isFailure)
        assertTrue(parentPath.exists())
        assertTrue(!destination.exists())
    }

    @Test
    fun `write fails when destination is a directory`() {
        val destination = tempDir.resolve("settings.json")
        destination.mkdirs()

        val result = AtomicFileWriter.write(destination) { tempFile ->
            tempFile.writeText("new")
        }

        assertTrue(result.isFailure)
        assertTrue(destination.exists())
        assertTrue(destination.isDirectory)
    }
}
