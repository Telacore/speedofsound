package com.zugaldia.speedofsound.app.settings

import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_PROCESSING_ENABLED
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import com.zugaldia.speedofsound.core.plugins.AppPlugin
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.EmptyOptions
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import com.zugaldia.speedofsound.core.plugins.llm.OpenAiLlm
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

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

    @Test
    fun `refreshProviderConfiguration keeps stale llm selection when disabling text processing fails`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "custom-provider",
                KEY_TEXT_MODEL_PROVIDERS to "",
                KEY_TEXT_PROCESSING_ENABLED to "true",
            ),
            failOnSetStringKeys = setOf(KEY_TEXT_PROCESSING_ENABLED),
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = RecordingPlugin(id = "LLM_OPENAI")

        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.setActiveById(AppPluginCategory.LLM, activePlugin.id)

        LlmProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals("custom-provider", settingsClient.loadSelectedTextModelProviderId())
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
        assertEquals(null, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration shows active llm name when clearing missing provider fails`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "custom-provider",
                KEY_TEXT_MODEL_PROVIDERS to "",
                KEY_TEXT_PROCESSING_ENABLED to "true",
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = ThrowingPlugin(id = OpenAiLlm.ID)
        val manager = LlmProviderManager(registry, settingsClient)

        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.setActiveById(AppPluginCategory.LLM, activePlugin.id)

        manager.refreshProviderConfiguration()

        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, settingsClient.loadSelectedTextModelProviderId())
        assertEquals(false, settingsClient.loadTextProcessingEnabled())
        assertSame(activePlugin, registry.getActive(AppPluginCategory.LLM))
        assertEquals("OpenAI", manager.peekCurrentProviderName())
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
    }

    @Test
    fun `activateSelectedProvider uses the first visible provider when selection is stale`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "stale-provider",
                KEY_TEXT_PROCESSING_ENABLED to "true",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-z",
                            name = "Zulu",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-z",
                        ),
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Alpha",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val inactivePlugin = RecordingPlugin(id = "LLM_INACTIVE")
        val activePlugin = RecordingPlugin(id = OpenAiLlm.ID)

        registry.register(AppPluginCategory.LLM, inactivePlugin)
        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.setActiveById(AppPluginCategory.LLM, inactivePlugin.id)

        LlmProviderManager(registry, settingsClient).activateSelectedProvider()

        assertEquals("text-a", settingsClient.loadSelectedTextModelProviderId())
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
        assertSame(activePlugin, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, inactivePlugin.enableCount)
        assertEquals(1, inactivePlugin.disableCount)
        assertEquals(1, activePlugin.enableCount)
        assertEquals(0, activePlugin.disableCount)
    }

    @Test
    fun `activateSelectedProvider disables text processing when activation leaves no active provider`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_PROCESSING_ENABLED to "true",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Alpha",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val failingPlugin = ThrowingEnablePlugin(id = OpenAiLlm.ID)

        registry.register(AppPluginCategory.LLM, failingPlugin)

        LlmProviderManager(registry, settingsClient).activateSelectedProvider()

        assertEquals(false, settingsClient.loadTextProcessingEnabled())
        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, settingsClient.loadSelectedTextModelProviderId())
        assertEquals(null, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, failingPlugin.enableCount)
        assertEquals(1, failingPlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration reactivates the selected provider when active plugin is stale`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_PROCESSING_ENABLED to "true",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Alpha",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val inactivePlugin = RecordingPlugin(id = "LLM_INACTIVE")
        val selectedPlugin = RecordingPlugin(id = OpenAiLlm.ID)

        registry.register(AppPluginCategory.LLM, inactivePlugin)
        registry.register(AppPluginCategory.LLM, selectedPlugin)
        registry.setActiveById(AppPluginCategory.LLM, inactivePlugin.id)

        LlmProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertSame(selectedPlugin, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, inactivePlugin.enableCount)
        assertEquals(1, inactivePlugin.disableCount)
        assertEquals(1, selectedPlugin.enableCount)
        assertEquals(0, selectedPlugin.disableCount)
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
    }

    @Test
    fun `refreshProviderConfiguration tolerates failing llm disable when selected provider changes`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_PROCESSING_ENABLED to "true",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Alpha",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = ThrowingPlugin(id = "LLM_ACTIVE")
        val selectedPlugin = RecordingPlugin(id = OpenAiLlm.ID)

        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.register(AppPluginCategory.LLM, selectedPlugin)
        registry.setActiveById(AppPluginCategory.LLM, activePlugin.id)

        LlmProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals("text-a", settingsClient.loadSelectedTextModelProviderId())
        assertEquals(true, settingsClient.loadTextProcessingEnabled())
        assertSame(activePlugin, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
        assertEquals(0, selectedPlugin.enableCount)
        assertEquals(0, selectedPlugin.disableCount)
    }

    @Test
    fun `refreshProviderConfiguration clears active llm when text processing is disabled`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_PROCESSING_ENABLED to "false",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Alpha",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = RecordingPlugin(id = "LLM_ACTIVE")
        val selectedPlugin = RecordingPlugin(id = OpenAiLlm.ID)

        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.register(AppPluginCategory.LLM, selectedPlugin)
        registry.setActiveById(AppPluginCategory.LLM, activePlugin.id)

        LlmProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals(null, registry.getActive(AppPluginCategory.LLM))
        assertEquals(1, activePlugin.enableCount)
        assertEquals(1, activePlugin.disableCount)
        assertEquals(0, selectedPlugin.enableCount)
        assertEquals(0, selectedPlugin.disableCount)
        assertEquals(false, settingsClient.loadTextProcessingEnabled())
    }

    @Test
    fun `refreshProviderConfiguration tolerates failing llm disable when text processing is disabled`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-a",
                KEY_TEXT_PROCESSING_ENABLED to "false",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-a",
                            name = "Alpha",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val registry = AppPluginRegistry()
        val activePlugin = ThrowingPlugin(id = "LLM_ACTIVE")

        registry.register(AppPluginCategory.LLM, activePlugin)
        registry.setActiveById(AppPluginCategory.LLM, activePlugin.id)

        LlmProviderManager(registry, settingsClient).refreshProviderConfiguration()

        assertEquals(false, settingsClient.loadTextProcessingEnabled())
        assertEquals(activePlugin, registry.getActive(AppPluginCategory.LLM))
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
        private val failOnSetStringKeys: Set<String> = emptySet(),
    ) : SettingsStore {
        private val values = initialValues

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            if (key in failOnSetStringKeys) {
                return false
            }
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
