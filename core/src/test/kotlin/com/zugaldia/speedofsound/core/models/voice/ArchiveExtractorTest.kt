package com.zugaldia.speedofsound.core.models.voice

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.io.File
import java.io.FileOutputStream

class ArchiveExtractorTest {
    @Test
    fun `extractTarBz2 writes files atomically and preserves content`() {
        val tempDir = createTempDirectory("sos-archive-extract")
        try {
            val destinationDir = tempDir.resolve("dest").toFile()
            val archiveFile = tempDir.resolve("archive.tar.bz2").toFile()
            val fileBytes = ByteArray(1024) { it.toByte() }
            createTarBz2Archive(
                archiveFile,
                mapOf("model/model.onnx" to fileBytes)
            )

            val extractor = ArchiveExtractor()
            val result = extractor.extractTarBz2(archiveFile, destinationDir)

            assertTrue(result.isSuccess)
            val extractedFile = destinationDir.toPath().resolve("model/model.onnx").toFile()
            assertTrue(extractedFile.exists())
            assertEquals(fileBytes.size.toLong(), extractedFile.length())
            assertTrue(extractedFile.readBytes().contentEquals(fileBytes))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extractTarBz2 rejects traversal entries`() {
        val tempDir = createTempDirectory("sos-archive-traversal")
        try {
            val destinationDir = tempDir.resolve("dest").toFile()
            val archiveFile = tempDir.resolve("archive.tar.bz2").toFile()
            val outsideFile = tempDir.resolve("outside.txt").toFile()
            createTarBz2Archive(
                archiveFile,
                mapOf("../outside.txt" to byteArrayOf(1, 2, 3))
            )

            val extractor = ArchiveExtractor()
            val result = extractor.extractTarBz2(archiveFile, destinationDir)

            assertTrue(result.isFailure)
            assertFalse(outsideFile.exists())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extractTarBz2 rejects symlink entries`() {
        val tempDir = createTempDirectory("sos-archive-symlink")
        try {
            val destinationDir = tempDir.resolve("dest").toFile()
            val archiveFile = tempDir.resolve("archive.tar.bz2").toFile()
            createTarBz2ArchiveWithSymlink(
                archiveFile,
                linkName = "model-link",
                targetName = "model.onnx"
            )

            val extractor = ArchiveExtractor()
            val result = extractor.extractTarBz2(archiveFile, destinationDir)

            assertTrue(result.isFailure)
            assertFalse(destinationDir.toPath().resolve("model-link").toFile().exists())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun createTarBz2Archive(archiveFile: File, entries: Map<String, ByteArray>) {
        archiveFile.parentFile?.mkdirs()
        FileOutputStream(archiveFile).use { fileOut ->
            BZip2CompressorOutputStream(fileOut).use { bzOut ->
                TarArchiveOutputStream(bzOut).use { tarOut ->
                    tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    entries.forEach { (name, bytes) ->
                        val entry = TarArchiveEntry(name)
                        entry.size = bytes.size.toLong()
                        tarOut.putArchiveEntry(entry)
                        tarOut.write(bytes)
                        tarOut.closeArchiveEntry()
                    }
                    tarOut.finish()
                }
            }
        }
    }

    private fun createTarBz2ArchiveWithSymlink(
        archiveFile: File,
        linkName: String,
        targetName: String,
    ) {
        archiveFile.parentFile?.mkdirs()
        FileOutputStream(archiveFile).use { fileOut ->
            BZip2CompressorOutputStream(fileOut).use { bzOut ->
                TarArchiveOutputStream(bzOut).use { tarOut ->
                    tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    val entry = TarArchiveEntry(linkName, TarConstants.LF_SYMLINK)
                    entry.setLinkName(targetName)
                    entry.setSize(0L)
                    tarOut.putArchiveEntry(entry)
                    tarOut.closeArchiveEntry()
                    tarOut.finish()
                }
            }
        }
    }
}
