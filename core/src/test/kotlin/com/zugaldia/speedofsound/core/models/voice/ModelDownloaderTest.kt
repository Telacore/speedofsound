package com.zugaldia.speedofsound.core.models.voice

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import java.io.File

class ModelDownloaderTest {
    private val tempDirs = mutableListOf<File>()

    @AfterTest
    fun cleanup() {
        tempDirs.forEach { it.deleteRecursively() }
        tempDirs.clear()
    }

    @Test
    fun `downloadFile overwrites existing file with downloaded bytes`() = runBlocking {
        val tempDir = createTempDirectory("sos-downloader").toFile()
        tempDirs += tempDir
        val destination = tempDir.resolve("model.bin")
        destination.writeText("old")

        val downloader = createDownloader("fresh")
        downloader.use {
            val result = it.downloadFile("https://example.com/model.bin", destination)

            assertTrue(result.isSuccess)
        }

        assertEquals("fresh", destination.readText())
    }

    @Test
    fun `downloadFile fails when destination is a directory`() = runBlocking {
        val tempDir = createTempDirectory("sos-downloader-dir").toFile()
        tempDirs += tempDir
        val destination = tempDir.resolve("model.bin")
        destination.mkdirs()

        val downloader = createDownloader("fresh")
        downloader.use {
            val result = it.downloadFile("https://example.com/model.bin", destination)

            assertTrue(result.isFailure)
            assertTrue(destination.exists())
            assertTrue(destination.isDirectory)
        }
    }

    @Test
    fun `downloadFile replaces a zero length file`() = runBlocking {
        val tempDir = createTempDirectory("sos-downloader-empty").toFile()
        tempDirs += tempDir
        val destination = tempDir.resolve("model.bin")
        destination.writeText("")

        val downloader = createDownloader("fresh")
        downloader.use {
            val result = it.downloadFile("https://example.com/model.bin", destination)

            assertTrue(result.isSuccess)
        }

        assertFalse(destination.readText().isEmpty())
        assertEquals("fresh", destination.readText())
    }

    private fun createDownloader(content: String): ModelDownloader {
        val mockEngine = MockEngine { _ ->
            respond(content = content)
        }
        val client = HttpClient(mockEngine)

        return ModelDownloader(client)
    }
}
