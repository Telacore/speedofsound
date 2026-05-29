package com.zugaldia.speedofsound.core.io

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object AtomicFileWriter {
    fun write(destination: File, writeAction: (File) -> Unit): Result<Unit> = runCatching {
        destination.parentFile?.mkdirs()
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
        val rawPrefix = destination.name.ifBlank { "atomic" }
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
