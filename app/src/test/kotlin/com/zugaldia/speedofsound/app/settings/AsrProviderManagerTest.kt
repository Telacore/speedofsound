package com.zugaldia.speedofsound.app.settings

import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_DEFAULT_LANGUAGE
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
import com.zugaldia.speedofsound.core.plugins.asr.DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID
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
    fun `refreshProviderConfiguration persists exact whisper fallback when selected provider is missing and custom providers exist`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "custom-provider",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
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
        val activePlugin = RecordingPlugin(id = "ASR_OPENAI")
        val fallbackPlugin = RecordingPlugin(id = SherpaWhisperAsr.ID)

        registry.register(AppPluginCategory.ASR, activePlugin)
        registry.register(AppPluginCategory.ASR, fallbackPlugin)
        registry.setActiveById(AppPluginCategory.ASR, activePlugin.id)

        AsrProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals(
            DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID,
            settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID),
        )
        assertSame(fallbackPlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
        assertEquals(1, fallbackPlugin.enableCount)
        assertEquals(0, fallbackPlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration shows whisper fallback name when persisting selection fails`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "custom-provider",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = AsrProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            ),
            failOnSetStringKeys = setOf(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID),
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = RecordingPlugin(id = "ASR_OPENAI")
        val fallbackPlugin = RecordingPlugin(id = SherpaWhisperAsr.ID)
        val manager = AsrProviderManager(registry, settingsClient)

        registry.register(AppPluginCategory.ASR, activePlugin)
        registry.register(AppPluginCategory.ASR, fallbackPlugin)
        registry.setActiveById(AppPluginCategory.ASR, activePlugin.id)

        manager.refreshProviderConfiguration()

        assertEquals("custom-provider", settingsClient.loadSelectedVoiceModelProviderId())
        assertEquals("Whisper (Local)", manager.peekCurrentProviderName())
        assertSame(fallbackPlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
        assertEquals(1, fallbackPlugin.enableCount)
        assertEquals(0, fallbackPlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration keeps whisper active without reactivating it when selection is missing`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "custom-provider",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
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
        val activePlugin = RecordingPlugin(id = SherpaWhisperAsr.ID)

        registry.register(AppPluginCategory.ASR, activePlugin)
        registry.setActiveById(AppPluginCategory.ASR, activePlugin.id)

        AsrProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals(
            DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID,
            settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID),
        )
        assertSame(activePlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(0, activePlugin.enableCount)
        assertEquals(0, activePlugin.disableCount)
    }

    @Test
    fun `activateSelectedProvider uses the first visible provider when selection is stale`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-provider",
                KEY_DEFAULT_LANGUAGE to "de",
                KEY_CREDENTIALS to Json.encodeToString(
                    listOf(
                        com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting(
                            id = "cred-1",
                            type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                            name = "Primary",
                            value = "secret",
                        )
                    )
                ),
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

        assertEquals("voice-a", settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID))
        assertSame(activePlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, settingsStore.stringReadCount(KEY_CREDENTIALS))
        assertEquals(1, settingsStore.stringReadCount(KEY_DEFAULT_LANGUAGE))
        assertEquals(1, inactivePlugin.enableCount)
        assertEquals(1, inactivePlugin.disableCount)
        assertEquals(1, activePlugin.enableCount)
        assertEquals(0, activePlugin.disableCount)
    }

    @Test
    fun `activateSelectedProvider activates whisper when no custom provider matches the default selection`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
                KEY_VOICE_MODEL_PROVIDERS to "",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val inactivePlugin = RecordingPlugin(id = "ASR_INACTIVE")
        val fallbackPlugin = RecordingPlugin(id = SherpaWhisperAsr.ID)

        registry.register(AppPluginCategory.ASR, inactivePlugin)
        registry.register(AppPluginCategory.ASR, fallbackPlugin)
        registry.setActiveById(AppPluginCategory.ASR, inactivePlugin.id)

        AsrProviderManager(registry, settingsClient).activateSelectedProvider()

        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, settingsClient.loadSelectedVoiceModelProviderId())
        assertSame(fallbackPlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, inactivePlugin.enableCount)
        assertEquals(1, inactivePlugin.disableCount)
        assertEquals(1, fallbackPlugin.enableCount)
        assertEquals(0, fallbackPlugin.disableCount)
    }

    @Test
    fun `activateSelectedProvider falls back to whisper when selected provider activation leaves no active plugin`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
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
        val failingPlugin = ThrowingEnablePlugin(id = OpenAiAsr.ID)
        val fallbackPlugin = RecordingPlugin(id = SherpaWhisperAsr.ID)

        registry.register(AppPluginCategory.ASR, failingPlugin)
        registry.register(AppPluginCategory.ASR, fallbackPlugin)

        AsrProviderManager(registry, settingsClient).activateSelectedProvider()

        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, settingsClient.loadSelectedVoiceModelProviderId())
        assertSame(fallbackPlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, failingPlugin.enableCount)
        assertEquals(1, failingPlugin.disableCount)
        assertEquals(1, fallbackPlugin.enableCount)
        assertEquals(0, fallbackPlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration reactivates the selected provider when active plugin is stale`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
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
        val selectedPlugin = RecordingPlugin(id = OpenAiAsr.ID)

        registry.register(AppPluginCategory.ASR, inactivePlugin)
        registry.register(AppPluginCategory.ASR, selectedPlugin)
        registry.setActiveById(AppPluginCategory.ASR, inactivePlugin.id)

        AsrProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertSame(selectedPlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, inactivePlugin.enableCount)
        assertEquals(1, inactivePlugin.disableCount)
        assertEquals(1, selectedPlugin.enableCount)
        assertEquals(0, selectedPlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration tolerates failing asr disable when selected provider changes`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-a",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
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
        val activePlugin = ThrowingPlugin(id = "ASR_ACTIVE")
        val selectedPlugin = RecordingPlugin(id = OpenAiAsr.ID)

        registry.register(AppPluginCategory.ASR, activePlugin)
        registry.register(AppPluginCategory.ASR, selectedPlugin)
        registry.setActiveById(AppPluginCategory.ASR, activePlugin.id)

        AsrProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals("voice-a", settingsClient.loadSelectedVoiceModelProviderId())
        assertSame(activePlugin, registry.getActive(AppPluginCategory.ASR))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
        assertEquals(0, selectedPlugin.enableCount)
        assertEquals(0, selectedPlugin.disableCount)
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

    private class ThrowingPlugin(
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
            throw IllegalStateException("disable failed")
        }
    }

    private class ThrowingEnablePlugin(
        override val id: String,
    ) : AppPlugin<EmptyOptions>(EmptyOptions) {
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set

        override fun enable() {
            enableCount += 1
            throw IllegalStateException("enable failed")
        }

        override fun disable() {
            disableCount += 1
        }
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues
        private val stringReadCounts = mutableMapOf<String, Int>()

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String {
            stringReadCounts[key] = (stringReadCounts[key] ?: 0) + 1
            return values[key] ?: defaultValue
        }

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

        fun stringReadCount(key: String): Int = stringReadCounts[key] ?: 0
    }
}
