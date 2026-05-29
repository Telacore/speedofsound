package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.app.settings.AsrProviderManager
import com.zugaldia.speedofsound.app.settings.LlmProviderManager
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_PROCESSING_ENABLED
import com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.speedofsound.core.plugins.AppPlugin
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.EmptyOptions
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MainViewModelCredentialsRefreshTest {
    @Test
    fun `refreshCredentials updates labels after llm refresh failure`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = AsrProvider.OPENAI,
                            modelId = "whisper-1",
                            baseUrl = "http://localhost:1234/v1",
                        ),
                    )
                ),
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Bravo",
                            provider = LlmProvider.OPENAI,
                            modelId = "gpt-5.4-mini",
                            baseUrl = "http://localhost:1234/v1",
                        ),
                    )
                ),
                KEY_TEXT_PROCESSING_ENABLED to "true",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val viewModel = MainViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )

        viewModel.state.updateAsrModel("stale-asr")
        viewModel.state.updateLlmModel("stale-llm")

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val asrProviderManager = getPrivateField<AsrProviderManager>(viewModel, "asrProviderManager")
        val llmProviderManager = getPrivateField<LlmProviderManager>(viewModel, "llmProviderManager")
        asrProviderManager.registerAsrPlugins()
        llmProviderManager.registerLlmPlugins()

        val failingPlugin = ThrowingPlugin(id = "LLM_FAIL")
        registry.register(AppPluginCategory.LLM, failingPlugin)
        registry.setActiveById(AppPluginCategory.LLM, failingPlugin.id)

        invokePrivateUnit(viewModel, "refreshCredentials")

        assertEquals("Alpha", viewModel.state.currentAsrModel())
        assertEquals("Bravo", viewModel.state.currentLlmModel())
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
        assertSame(failingPlugin, registry.getActive(AppPluginCategory.LLM))
    }

    @Test
    fun `refreshCredentials stops after fatal asr failure`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = AsrProvider.OPENAI,
                            modelId = "whisper-1",
                            baseUrl = "http://localhost:1234/v1",
                        ),
                    )
                ),
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "stale-text",
                KEY_TEXT_MODEL_PROVIDERS to "",
                KEY_TEXT_PROCESSING_ENABLED to "true",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val viewModel = MainViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val asrProviderManager = getPrivateField<AsrProviderManager>(viewModel, "asrProviderManager")
        asrProviderManager.registerAsrPlugins()

        val failingPlugin = ThrowingPlugin(id = "ASR_FAIL")
        registry.register(AppPluginCategory.ASR, failingPlugin)
        registry.setActiveById(AppPluginCategory.ASR, failingPlugin.id)

        invokePrivateUnit(viewModel, "refreshCredentials")

        assertEquals("stale-text", settingsClient.loadSelectedTextModelProviderId())
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
    }

    @Test
    fun `refreshAsrSetting updates labels after asr refresh failure`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = AsrProvider.OPENAI,
                            modelId = "whisper-1",
                            baseUrl = "http://localhost:1234/v1",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val viewModel = MainViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )

        viewModel.state.updateAsrModel("stale-asr")
        viewModel.state.updateLlmModel("stale-llm")

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val asrProviderManager = getPrivateField<AsrProviderManager>(viewModel, "asrProviderManager")
        asrProviderManager.registerAsrPlugins()

        val failingPlugin = ThrowingPlugin(id = "ASR_FAIL")
        registry.register(AppPluginCategory.ASR, failingPlugin)
        registry.setActiveById(AppPluginCategory.ASR, failingPlugin.id)

        invokePrivateString(viewModel, "refreshAsrSetting", KEY_SELECTED_VOICE_MODEL_PROVIDER_ID)

        assertEquals("Alpha", viewModel.state.currentAsrModel())
        assertEquals("", viewModel.state.currentLlmModel())
        assertSame(failingPlugin, registry.getActive(AppPluginCategory.ASR))
    }

    @Test
    fun `refreshLlmSetting updates labels after llm refresh failure`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = AsrProvider.OPENAI,
                            modelId = "whisper-1",
                            baseUrl = "http://localhost:1234/v1",
                        ),
                    )
                ),
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Bravo",
                            provider = LlmProvider.OPENAI,
                            modelId = "gpt-5.4-mini",
                            baseUrl = "http://localhost:1234/v1",
                        ),
                    )
                ),
                KEY_TEXT_PROCESSING_ENABLED to "true",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val viewModel = MainViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )

        viewModel.state.updateAsrModel("stale-asr")
        viewModel.state.updateLlmModel("stale-llm")

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val llmProviderManager = getPrivateField<LlmProviderManager>(viewModel, "llmProviderManager")
        llmProviderManager.registerLlmPlugins()

        val failingPlugin = ThrowingPlugin(id = "LLM_FAIL")
        registry.register(AppPluginCategory.LLM, failingPlugin)
        registry.setActiveById(AppPluginCategory.LLM, failingPlugin.id)

        invokePrivateString(viewModel, "refreshLlmSetting", KEY_SELECTED_TEXT_MODEL_PROVIDER_ID)

        assertEquals("Alpha", viewModel.state.currentAsrModel())
        assertEquals("Bravo", viewModel.state.currentLlmModel())
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
        assertSame(failingPlugin, registry.getActive(AppPluginCategory.LLM))
    }

    private fun invokePrivateUnit(instance: Any, methodName: String) {
        val method = instance.javaClass.getDeclaredMethod(methodName)
        method.isAccessible = true
        method.invoke(instance)
    }

    private fun invokePrivateString(instance: Any, methodName: String, arg: String) {
        val method = instance.javaClass.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        method.invoke(instance, arg)
    }

    private inline fun <reified T> getPrivateField(instance: Any, fieldName: String): T {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(instance) as T
    }

    private class ThrowingPlugin(
        override val id: String,
    ) : AppPlugin<EmptyOptions>(EmptyOptions) {
        override fun enable() {
        }

        override fun disable() {
            throw IllegalStateException("disable failed")
        }
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            values[key] = value
            return true
        }

        override fun getStringArray(key: String, defaultValue: List<String>): List<String> =
            values[key]?.let { raw ->
                if (raw.isEmpty()) emptyList() else raw.split("|||")
            } ?: defaultValue

        override fun setStringArray(key: String, value: List<String>): Boolean {
            values[key] = value.joinToString("|||")
            return true
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull() ?: defaultValue

        override fun setBoolean(key: String, value: Boolean): Boolean {
            values[key] = value.toString()
            return true
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key]?.toIntOrNull() ?: defaultValue

        override fun setInt(key: String, value: Int): Boolean {
            values[key] = value.toString()
            return true
        }
    }
}
