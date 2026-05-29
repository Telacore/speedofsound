package com.zugaldia.speedofsound.core.models.voice

import com.zugaldia.speedofsound.core.FatalStartupException
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ModelFileManagerTest {

    // Test fixture: real LFS pointer generated from this repo's tiny-encoder.int8.onnx using:
    //   git lfs pointer --file=core/src/main/resources/models/asr/tiny-encoder.int8.onnx > \
    //     core/src/test/resources/models/asr/lfs-pointer.bin
    // Stored as .bin (not .onnx) so it is not subject to Git LFS tracking.
    private val lfsPointerPath by lazy {
        val resource = javaClass.getResource("/models/asr/lfs-pointer.bin")
            ?: error("Test resource /models/asr/lfs-pointer.bin not found")
        Path.of(resource.toURI())
    }

    private val testModel = VoiceModel(
        id = "test-model",
        name = "Test Model",
        provider = AsrProvider.SHERPA_WHISPER,
        components = listOf(VoiceModelFile(name = "model.onnx"))
    )

    private fun createFileManager(tempDir: Path): ModelFileManager {
        val pathProvider = object : PathProvider {
            override fun getDataDir(): Path = tempDir
            override fun getCacheDir(): Path = tempDir.resolve("cache")
        }
        return ModelFileManager(pathProvider, DefaultFileSystemOperations())
    }

    @Test
    fun `isModelDownloaded returns false when component is an LFS pointer file`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val modelPath = manager.getModelPath("test-model")

            // Copy LFS pointer into the model directory as if it were a real model file
            lfsPointerPath.toFile().copyTo(modelPath.resolve("model.onnx").toFile())
            assertFalse(manager.isModelDownloaded("test-model", testModel))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `isModelDownloaded returns true for a real (non-LFS) model file`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val modelPath = manager.getModelPath("test-model")

            // Write a minimal fake ONNX binary (not an LFS pointer)
            modelPath.resolve("model.onnx").toFile().writeBytes(ByteArray(1024) { it.toByte() })
            assertTrue(manager.isModelDownloaded("test-model", testModel))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extractDefaultModelFromResources throws helpful error when resource is an LFS pointer`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val outputFile = manager.getModelPath("test-model").resolve("model.onnx").toFile()
            val lfsPointerResourceLoader = object : ResourceLoader {
                override fun loadResource(path: String): InputStream =
                    lfsPointerPath.toFile().inputStream()
            }

            val exception = assertFailsWith<FatalStartupException> {
                manager.extractDefaultModelFromResources("test-model", testModel, lfsPointerResourceLoader)
                    .getOrThrow()
            }

            assertNotNull(exception.message)
            assertTrue(exception.message?.contains("Git LFS") == true)
            assertTrue(exception.message?.contains("CONTRIBUTING.md") == true)
            assertFalse(outputFile.exists())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extractDefaultModelFromResources writes complete file atomically`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val outputFile = manager.getModelPath("test-model").resolve("model.onnx").toFile()
            val modelBytes = ByteArray(1024) { it.toByte() }
            val resourceLoader = object : ResourceLoader {
                override fun loadResource(path: String): InputStream = modelBytes.inputStream()
            }

            val result = manager.extractDefaultModelFromResources("test-model", testModel, resourceLoader)
            assertTrue(result.isSuccess)
            assertTrue(outputFile.exists())
            assertEquals(modelBytes.size.toLong(), outputFile.length())
            assertTrue(outputFile.readBytes().contentEquals(modelBytes))
            assertTrue(manager.isModelDownloaded("test-model", testModel))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extractDefaultModelFromResources rejects file-backed model directories`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val modelPath = manager.getModelPath("test-model").toFile()
            modelPath.deleteRecursively()
            modelPath.writeText("not-a-directory")
            val resourceLoader = object : ResourceLoader {
                override fun loadResource(path: String): InputStream = ByteArray(16) { it.toByte() }.inputStream()
            }

            val exception = assertFailsWith<IllegalStateException> {
                manager.extractDefaultModelFromResources("test-model", testModel, resourceLoader).getOrThrow()
            }

            assertNotNull(exception.message)
            assertTrue(exception.message?.contains("not a directory") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `copyModelFiles rejects traversal component names`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val archiveTempDir = tempDir.resolve("archive").toFile()
            val modelDir = archiveTempDir.resolve("test-model")
            modelDir.mkdirs()
            val maliciousModel = testModel.copy(
                components = listOf(VoiceModelFile(name = "../outside.onnx"))
            )

            val exception = assertFailsWith<IllegalArgumentException> {
                manager.copyModelFiles(archiveTempDir, "test-model", maliciousModel).getOrThrow()
            }

            assertNotNull(exception.message)
            assertTrue(exception.message?.contains("escapes model directory") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `extractDefaultModelFromResources rejects traversal component names`() {
        val tempDir = createTempDirectory("sos-test")
        try {
            val manager = createFileManager(tempDir)
            val maliciousModel = testModel.copy(
                components = listOf(VoiceModelFile(name = "../outside.onnx"))
            )
            val resourceLoader = object : ResourceLoader {
                override fun loadResource(path: String): InputStream = ByteArray(16) { it.toByte() }.inputStream()
            }

            val exception = assertFailsWith<IllegalArgumentException> {
                manager.extractDefaultModelFromResources("test-model", maliciousModel, resourceLoader).getOrThrow()
            }

            assertNotNull(exception.message)
            assertTrue(exception.message?.contains("escapes model directory") == true)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
