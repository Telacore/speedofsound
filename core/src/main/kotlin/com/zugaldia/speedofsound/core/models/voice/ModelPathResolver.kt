package com.zugaldia.speedofsound.core.models.voice

import java.nio.file.Path

fun resolveSafeChildPath(basePath: Path, childName: String): Path {
    require(childName.isNotBlank() && childName != "." && childName != "..") {
        "Path escapes model directory: $childName"
    }
    val normalizedBase = basePath.normalize()
    val resolved = normalizedBase.resolve(childName).normalize()
    if (!resolved.startsWith(normalizedBase)) {
        throw IllegalArgumentException("Path escapes model directory: $childName")
    }
    return resolved
}
