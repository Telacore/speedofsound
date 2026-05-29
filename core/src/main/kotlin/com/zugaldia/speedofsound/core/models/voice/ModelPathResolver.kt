package com.zugaldia.speedofsound.core.models.voice

import java.nio.file.Path
import java.nio.file.Paths

fun resolveSafeChildPath(basePath: Path, childName: String): Path {
    require(childName.isNotBlank() && childName != "." && childName != "..") {
        "Path escapes model directory: $childName"
    }
    val childPath = Paths.get(childName)
    require(!childPath.isAbsolute && childPath.nameCount == 1) {
        "Path escapes model directory: $childName"
    }
    val normalizedBase = basePath.normalize()
    val resolved = normalizedBase.resolve(childName).normalize()
    if (!resolved.startsWith(normalizedBase)) {
        throw IllegalArgumentException("Path escapes model directory: $childName")
    }
    return resolved
}
