package com.zugaldia.speedofsound.core.models.voice

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModelPathResolverTest {
    @Test
    fun `resolveSafeChildPath returns a normalized child path`() {
        val baseDir = createTempDirectory("sos-model-path-resolver")
        try {
            val resolved = resolveSafeChildPath(baseDir, "model.onnx")

            assertEquals(baseDir.resolve("model.onnx").normalize(), resolved)
        } finally {
            baseDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `resolveSafeChildPath rejects invalid child names`() {
        val baseDir = createTempDirectory("sos-model-path-resolver")
        try {
            listOf("", ".", "..", "../outside", "/outside", "subdir/model.onnx").forEach { childName ->
                val exception = assertFailsWith<IllegalArgumentException> {
                    resolveSafeChildPath(baseDir, childName)
                }

                assertTrue(exception.message?.contains("escapes model directory") == true)
            }
        } finally {
            baseDir.toFile().deleteRecursively()
        }
    }
}
