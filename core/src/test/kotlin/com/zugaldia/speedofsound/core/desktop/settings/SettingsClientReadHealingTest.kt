package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsClientReadHealingTest {
    @Test
    fun `malformed bool and int settings are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to "  CLIPBOARD  ",
                KEY_WELCOME_SCREEN_SHOWN to "maybe",
                KEY_SHORTCUT_CONFIGURED to "TRUE",
                KEY_BACKGROUND_RECORDING to "not-a-bool",
                KEY_POST_HIDE_DELAY_MS to "250ms",
                KEY_TYPING_DELAY_MS to "003",
                KEY_MAX_ALARMS to "999",
            )
        )
        val client = SettingsClient(store)

        assertEquals(TEXT_OUTPUT_METHOD_CLIPBOARD, client.getTextOutputMethod())
        assertEquals(DEFAULT_WELCOME_SCREEN_SHOWN, client.getWelcomeScreenShown())
        assertEquals(true, client.getShortcutConfigured())
        assertEquals(DEFAULT_BACKGROUND_RECORDING, client.getBackgroundRecording())
        assertEquals(DEFAULT_POST_HIDE_DELAY_MS, client.getPostHideDelayMs())
        assertEquals(3, client.getTypingDelayMs())
        assertEquals(MAX_MAX_ALARMS, client.getMaxAlarms())

        assertEquals(TEXT_OUTPUT_METHOD_CLIPBOARD, store.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD))
        assertEquals("false", store.getString(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN.toString()))
        assertEquals("true", store.getString(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED.toString()))
        assertEquals(DEFAULT_BACKGROUND_RECORDING.toString(), store.getString(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING.toString()))
        assertEquals(DEFAULT_POST_HIDE_DELAY_MS.toString(), store.getString(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS.toString()))
        assertEquals("3", store.getString(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS.toString()))
        assertEquals(MAX_MAX_ALARMS.toString(), store.getString(KEY_MAX_ALARMS, DEFAULT_MAX_ALARMS.toString()))
        assertEquals(7, store.writeCount)
    }

    @Test
    fun `malformed text output method is healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to "not-a-method"
            )
        )
        val client = SettingsClient(store)

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, client.getTextOutputMethod())
        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, store.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD))
        assertEquals(1, store.writeCount)
    }

    @Test
    fun `malformed language settings are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_DEFAULT_LANGUAGE to "EN",
                KEY_SECONDARY_LANGUAGE to "zz",
            )
        )
        val client = SettingsClient(store)

        assertEquals(DEFAULT_LANGUAGE.iso2, client.getDefaultLanguage())
        assertEquals(DEFAULT_SECONDARY_LANGUAGE.iso2, client.getSecondaryLanguage())
        assertEquals(DEFAULT_LANGUAGE.iso2, store.getString(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2))
        assertEquals(DEFAULT_SECONDARY_LANGUAGE.iso2, store.getString(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2))
        assertEquals(2, store.writeCount)
    }

    @Test
    fun `malformed selected provider ids are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "missing-voice-provider",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "missing-text-provider",
            )
        )
        val client = SettingsClient(store)

        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, client.getSelectedVoiceModelProviderId())
        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, client.getSelectedTextModelProviderId())
        assertEquals(
            DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)
        )
        assertEquals(
            DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            store.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)
        )
        assertEquals(2, store.writeCount)
    }

    @Test
    fun `valid but dirty credential and provider json is normalized on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CREDENTIALS to Json.encodeToString(
                    listOf(
                        CredentialSetting(
                            id = " cred-1 ",
                            type = CredentialType.API_KEY,
                            name = " Primary ",
                            value = " secret ",
                        ),
                        CredentialSetting(
                            id = "cred-1",
                            type = CredentialType.API_KEY,
                            name = "Duplicate",
                            value = "ignored",
                        ),
                    )
                ),
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "  $DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID  ",
                            name = " Whisper Tiny ",
                            provider = AsrProvider.SHERPA_WHISPER,
                            modelId = " $DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID ",
                            credentialId = " cred-1 ",
                            baseUrl = " https://example.com ",
                        ),
                        VoiceModelProviderSetting(
                            id = " voice-1 ",
                            name = " Whisper ",
                            provider = AsrProvider.SHERPA_WHISPER,
                            modelId = " model-1 ",
                            credentialId = " cred-1 ",
                            baseUrl = " https://example.com ",
                        ),
                    )
                ),
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = " text-1 ",
                            name = " LLM ",
                            provider = LlmProvider.OPENAI,
                            modelId = " model-2 ",
                            credentialId = " cred-1 ",
                            baseUrl = " https://example.com ",
                            disableThinking = true,
                        ),
                    )
                ),
            )
        )
        val client = SettingsClient(store)

        assertEquals(
            listOf(
                CredentialSetting(id = "cred-1", type = CredentialType.API_KEY, name = "Primary", value = "secret"),
            ),
            client.getCredentials()
        )
        assertEquals(
            listOf(
                VoiceModelProviderSetting(
                    id = "voice-1",
                    name = "Whisper",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-1",
                    credentialId = "cred-1",
                    baseUrl = "https://example.com",
                ),
            ),
            client.getVoiceModelProviders().filter { it.id == "voice-1" }
        )
        assertEquals(
            listOf(
                TextModelProviderSetting(
                    id = "text-1",
                    name = "LLM",
                    provider = LlmProvider.OPENAI,
                    modelId = "model-2",
                    credentialId = "cred-1",
                    baseUrl = "https://example.com",
                    disableThinking = true,
                ),
            ),
            client.getTextModelProviders()
        )

        assertEquals(
            Json.encodeToString(
                listOf(
                    CredentialSetting(id = "cred-1", type = CredentialType.API_KEY, name = "Primary", value = "secret"),
                )
            ),
            store.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS)
        )
        assertEquals(
            Json.encodeToString(
                listOf(
                    VoiceModelProviderSetting(
                        id = "voice-1",
                        name = "Whisper",
                        provider = AsrProvider.SHERPA_WHISPER,
                        modelId = "model-1",
                        credentialId = "cred-1",
                        baseUrl = "https://example.com",
                    ),
                )
            ),
            store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)
        )
        assertEquals(
            Json.encodeToString(
                listOf(
                    TextModelProviderSetting(
                        id = "text-1",
                        name = "LLM",
                        provider = LlmProvider.OPENAI,
                        modelId = "model-2",
                        credentialId = "cred-1",
                        baseUrl = "https://example.com",
                        disableThinking = true,
                    ),
                )
            ),
            store.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS)
        )
        assertEquals(3, store.writeCount)
    }

    @Test
    fun `malformed portals restore token is healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_PORTALS_RESTORE_TOKEN to "  smoke-token  "
            )
        )
        val client = SettingsClient(store)

        assertEquals("smoke-token", client.getPortalsRestoreToken())
        assertEquals("smoke-token", store.getString(KEY_PORTALS_RESTORE_TOKEN, DEFAULT_PORTALS_RESTORE_TOKEN))
        assertEquals(1, store.writeCount)
    }

    @Test
    fun `malformed custom vocabulary is healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CUSTOM_VOCABULARY to " alpha ||| |||beta|||alpha|||  gamma  "
            )
        )
        val client = SettingsClient(store)

        assertEquals(listOf("alpha", "beta", "gamma"), client.getCustomVocabulary())
        assertEquals("alpha|||beta|||gamma", store.getString(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY.joinToString("|||")))
        assertEquals(1, store.writeCount)
    }

    @Test
    fun `custom context over the limit is healed on read`() {
        val overlong = "x".repeat(MAX_CUSTOM_CONTEXT_CHARS + 17)
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CUSTOM_CONTEXT to overlong
            )
        )
        val client = SettingsClient(store)

        val expected = "x".repeat(MAX_CUSTOM_CONTEXT_CHARS)
        assertEquals(expected, client.getCustomContext())
        assertEquals(expected, store.getString(KEY_CUSTOM_CONTEXT, DEFAULT_CUSTOM_CONTEXT))
        assertEquals(1, store.writeCount)
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues
        var writeCount: Int = 0

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            values[key] = value
            writeCount += 1
            return true
        }

        override fun getStringArray(key: String, defaultValue: List<String>): List<String> =
            values[key]?.let { raw ->
                if (raw.isEmpty()) emptyList() else raw.split("|||")
            } ?: defaultValue

        override fun setStringArray(key: String, value: List<String>): Boolean {
            values[key] = value.joinToString("|||")
            writeCount += 1
            return true
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull() ?: defaultValue

        override fun setBoolean(key: String, value: Boolean): Boolean {
            values[key] = value.toString()
            writeCount += 1
            return true
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key]?.toIntOrNull() ?: defaultValue

        override fun setInt(key: String, value: Int): Boolean {
            values[key] = value.toString()
            writeCount += 1
            return true
        }
    }
}
