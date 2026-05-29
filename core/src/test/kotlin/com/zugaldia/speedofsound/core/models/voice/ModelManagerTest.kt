package com.zugaldia.speedofsound.core.models.voice

import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ModelManagerTest {
    @Test
    fun `downloadModel rejects file-backed cache directories`() = runBlocking {
        val tempDir = createTempDirectory("sos-model-manager")
        try {
            val dataDir = tempDir.resolve("data")
            val cacheDir = tempDir.resolve("cache")
            dataDir.toFile().mkdirs()
            cacheDir.toFile().writeText("not-a-directory")

            val model = VoiceModel(
                id = "test-model",
                name = "Test Model",
                provider = AsrProvider.SHERPA_WHISPER,
                archiveFile = VoiceModelFile(
                    name = "model.tar.bz2",
                    url = "https://example.com/model.tar.bz2",
                    sha256sum = "deadbeef",
                ),
                components = listOf(VoiceModelFile(name = "model.onnx")),
            )

            val manager = ModelManager(
                pathProvider = object : PathProvider {
                    override fun getDataDir() = dataDir
                    override fun getCacheDir() = cacheDir
                },
                voiceModelCatalog = object : VoiceModelCatalog {
                    override fun getModel(modelId: String): VoiceModel? = model.takeIf { it.id == modelId }
                    override fun getDefaultModelId(): String = model.id
                },
                modelDownloaderFactory = {
                    error("Model downloader must not be created when cache dir is invalid")
                }
            )

            val result = manager.downloadModel(model.id)

            assertTrue(result.isFailure)
            assertEquals(true, result.exceptionOrNull()?.message?.contains("Could not create temporary directory"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
