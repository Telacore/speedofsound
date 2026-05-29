package com.zugaldia.speedofsound.app.settings

import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_PROCESSING_ENABLED
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.plugins.AppPlugin
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.EmptyOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class LlmProviderManagerTest {

    @Test
    fun `refreshProviderConfiguration disables text processing when selected provider is missing`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "custom-provider",
                KEY_TEXT_MODEL_PROVIDERS to "",
                KEY_TEXT_PROCESSING_ENABLED to "true",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = RecordingPlugin(id = "LLM_OPENAI")

        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.setActiveById(AppPluginCategory.LLM, activePlugin.id)

        LlmProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, settingsClient.loadSelectedTextModelProviderId())
        assertEquals(false, settingsClient.loadTextProcessingEnabled())
        assertEquals(null, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
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
