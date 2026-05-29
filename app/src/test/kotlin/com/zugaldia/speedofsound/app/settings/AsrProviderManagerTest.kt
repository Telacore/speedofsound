package com.zugaldia.speedofsound.app.settings

import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.speedofsound.core.plugins.AppPlugin
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.EmptyOptions
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.asr.OpenAiAsr
import com.zugaldia.speedofsound.core.plugins.asr.SherpaWhisperAsr
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class AsrProviderManagerTest {

    @Test
    fun `refreshProviderConfiguration activates default whisper when selected provider is missing`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "custom-provider",
                KEY_VOICE_MODEL_PROVIDERS to "",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = RecordingPlugin(id = "ASR_OPENAI")
        val fallbackPlugin = RecordingPlugin(id = SherpaWhisperAsr.ID)

        registry.register(AppPluginCategory.ASR, activePlugin)
        registry.register(AppPluginCategory.ASR, fallbackPlugin)
        registry.setActiveById(AppPluginCategory.ASR, activePlugin.id)

        AsrProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, settingsClient.loadSelectedVoiceModelProviderId())
        assertSame(fallbackPlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
        assertEquals(1, fallbackPlugin.enableCount)
        assertEquals(0, fallbackPlugin.disableCount)
    }

    @Test
    fun `activateSelectedProvider uses the first visible provider when selection is stale`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-provider",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-z",
                            name = "Zulu",
                            provider = AsrProvider.OPENAI,
                            modelId = "model-z",
                        ),
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = AsrProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val inactivePlugin = RecordingPlugin(id = "ASR_INACTIVE")
        val activePlugin = RecordingPlugin(id = OpenAiAsr.ID)

        registry.register(AppPluginCategory.ASR, inactivePlugin)
        registry.register(AppPluginCategory.ASR, activePlugin)
        registry.setActiveById(AppPluginCategory.ASR, inactivePlugin.id)

        AsrProviderManager(registry, settingsClient).activateSelectedProvider()

        assertEquals("voice-a", settingsClient.loadSelectedVoiceModelProviderId())
        assertSame(activePlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, inactivePlugin.enableCount)
        assertEquals(1, inactivePlugin.disableCount)
        assertEquals(1, activePlugin.enableCount)
        assertEquals(0, activePlugin.disableCount)
    }

    private class RecordingPlugin(
        override val id: String,
    ) : AppPlugin<EmptyOptions>(EmptyOptions) {
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set

        override fun enable() {
            enableCount += 1
        }

        override fun disable() {
            disableCount += 1
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
