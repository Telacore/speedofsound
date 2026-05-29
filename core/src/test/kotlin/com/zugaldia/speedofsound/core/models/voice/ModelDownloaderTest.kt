package com.zugaldia.speedofsound.core.models.voice

import com.zugaldia.speedofsound.core.io.AtomicFileWriter
import kotlin.io.path.createTempDirectory
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ModelDownloaderTest {
    @Test
    fun `writeFileAtomically writes complete file and preserves bytes`() {
        val tempDir = createTempDirectory("sos-downloader")
        try {
            val destination = tempDir.resolve("model.tar.bz2").toFile()
            val bytes = ByteArray(1024) { it.toByte() }

            val result = AtomicFileWriter.write(destination) { tempFile ->
                tempFile.writeBytes(bytes)
            }

            assertTrue(result.isSuccess)
            assertTrue(destination.exists())
            assertEquals(bytes.size.toLong(), destination.length())
            assertTrue(destination.readBytes().contentEquals(bytes))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `writeFileAtomically does not leave destination behind on failure`() {
        val tempDir = createTempDirectory("sos-downloader-failure")
        try {
            val destination = tempDir.resolve("model.tar.bz2").toFile()

            val result = AtomicFileWriter.write(destination) { tempFile ->
                tempFile.writeBytes(byteArrayOf(1, 2, 3))
                throw IllegalStateException("boom")
            }

            assertTrue(result.isFailure)
            assertFalse(destination.exists())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `shouldSkipExistingDownload only skips completed files`() {
        val tempDir = createTempDirectory("sos-downloader-skip")
        try {
            val destination = tempDir.resolve("model.tar.bz2").toFile()

            assertFalse(ModelDownloader.shouldSkipExistingDownload(destination))

            destination.writeText("")
            assertFalse(ModelDownloader.shouldSkipExistingDownload(destination))

            destination.writeText("payload")
            assertTrue(ModelDownloader.shouldSkipExistingDownload(destination))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `shouldSkipExistingDownload rejects directory destinations`() {
        val tempDir = createTempDirectory("sos-downloader-dir")
        try {
            val destination = tempDir.resolve("model.tar.bz2").toFile()
            destination.mkdirs()

            assertFailsWith<IOException> {
                ModelDownloader.shouldSkipExistingDownload(destination)
            }
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
