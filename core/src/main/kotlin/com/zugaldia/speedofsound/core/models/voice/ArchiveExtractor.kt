package com.zugaldia.speedofsound.core.models.voice

import com.zugaldia.speedofsound.core.io.AtomicFileWriter
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.nio.file.Path

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
        ensureDestinationDirectory(destinationDir)
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
                                ensureOutputDirectory(outputFile)
                            } else if (entry.isFile) {
                                log.info("Extracting file: ${outputFile.absolutePath}")
                                outputFile.parentFile?.mkdirs()
                                AtomicFileWriter.write(outputFile) { tempFile ->
                                    tempFile.outputStream().use { output ->
                                        tarInput.copyTo(output)
                                    }
                                }.getOrThrow()
                            } else {
                                throw IllegalArgumentException("Unsupported archive entry type: ${entry.name}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun ensureOutputDirectory(outputFile: File) {
        when {
            outputFile.exists() && !outputFile.isDirectory -> {
                throw IllegalArgumentException("Archive directory entry collides with a file: ${outputFile.absolutePath}")
            }
            !outputFile.exists() && !outputFile.mkdirs() -> {
                throw IllegalStateException("Could not create archive directory: ${outputFile.absolutePath}")
            }
        }
    }

    private fun ensureDestinationDirectory(destinationDir: File) {
        when {
            destinationDir.exists() && !destinationDir.isDirectory -> {
                throw IllegalArgumentException("Destination path is not a directory: ${destinationDir.absolutePath}")
            }
            !destinationDir.exists() && !destinationDir.mkdirs() -> {
                throw IllegalStateException("Could not create destination directory: ${destinationDir.absolutePath}")
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
}
