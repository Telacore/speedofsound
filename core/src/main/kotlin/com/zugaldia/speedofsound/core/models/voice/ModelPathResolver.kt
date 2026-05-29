package com.zugaldia.speedofsound.core.models.voice

import java.nio.file.Path

internal fun resolveSafeChildPath(basePath: Path, childName: String): Path {
    val normalizedBase = basePath.normalize()
    val resolved = normalizedBase.resolve(childName).normalize()
    if (!resolved.startsWith(normalizedBase)) {
        throw IllegalArgumentException("Path escapes model directory: $childName")
    }
    return resolved
}
