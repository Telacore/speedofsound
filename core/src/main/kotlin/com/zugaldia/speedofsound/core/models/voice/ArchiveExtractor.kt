package com.zugaldia.speedofsound.core.models.voice

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Handles extraction of tar.bz2 archives.
 */
class ArchiveExtractor {
    private val log: Logger = LoggerFactory.getLogger(ArchiveExtractor::class.java)

    /**
     * Extract a tar.bz2 archive to a destination directory.
     */
    @Suppress("NestedBlockDepth")
    fun extractTarBz2(sourceFile: File, destinationDir: File): Result<Unit> = runCatching {
        destinationDir.mkdirs()
        val basePath = destinationDir.toPath().toAbsolutePath().normalize()
        sourceFile.inputStream().use { fileInput ->
            BufferedInputStream(fileInput).use { bufferedInput ->
                BZip2CompressorInputStream(bufferedInput).use { bzipInput ->
                    TarArchiveInputStream(bzipInput).use { tarInput ->
                        while (true) {
                            val entry = tarInput.nextEntry ?: break
                            val outputPath = resolveSafeOutputPath(basePath, entry.name)
                            val outputFile = outputPath.toFile()
                            if (entry.isDirectory) {
                                log.info("Creating directory: ${outputFile.absolutePath}")
                                outputFile.mkdirs()
                            } else {
                                log.info("Extracting file: ${outputFile.absolutePath}")
                                outputFile.parentFile?.mkdirs()
                                writeAtomically(outputFile) { tempFile ->
                                    FileOutputStream(tempFile).use { output ->
                                        tarInput.copyTo(output)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun resolveSafeOutputPath(basePath: Path, entryName: String): Path {
        val resolved = basePath.resolve(entryName).normalize()
        if (!resolved.startsWith(basePath)) {
            throw IllegalArgumentException("Archive entry escapes destination directory: $entryName")
        }
        return resolved
    }

    private inline fun writeAtomically(destination: File, writeAction: (File) -> Unit) {
        val tempFile = createTempSiblingFile(destination)
        try {
            writeAction(tempFile)
            moveTempFile(tempFile, destination)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun createTempSiblingFile(destination: File): File {
        val parentPath = destination.parentFile?.toPath() ?: Path.of(".")
        val rawPrefix = destination.name.ifBlank { "archive" }
        val prefix = if (rawPrefix.length < 3) rawPrefix.padEnd(3, '_') else rawPrefix
        return Files.createTempFile(parentPath, prefix, ".tmp").toFile()
    }

    private fun moveTempFile(tempFile: File, destination: File) {
        try {
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tempFile.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
