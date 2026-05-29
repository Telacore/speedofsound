package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsClientNormalizationTest {
    @Test
    fun `credentials and providers are normalized on save`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-voice",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "stale-text",
            )
        )
        val client = SettingsClient(store)

        client.setCredentials(
            listOf(
                CredentialSetting(id = " cred-1 ", type = CredentialType.API_KEY, name = " Primary ", value = " secret "),
                CredentialSetting(id = "cred-1", type = CredentialType.API_KEY, name = "Duplicate", value = "ignored"),
                CredentialSetting(id = " ", type = CredentialType.API_KEY, name = "Broken", value = "x"),
            )
        )
        client.setVoiceModelProviders(
            listOf(
                VoiceModelProviderSetting(
                    id = " voice-1 ",
                    name = " Whisper ",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = " model-1 ",
                    credentialId = " cred-1 ",
                    baseUrl = " https://example.com ",
                ),
                VoiceModelProviderSetting(
                    id = "voice-1",
                    name = "Duplicate",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "ignored",
                ),
            )
        )
        client.setTextModelProviders(
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
        )

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
        client.setSelectedVoiceModelProviderId(" missing-voice-provider ")
        client.setSelectedTextModelProviderId(" missing-text-provider ")

        assertEquals(
            DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)
        )
        assertEquals(
            DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            store.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)
        )
        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, client.getSelectedVoiceModelProviderId())
        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, client.getSelectedTextModelProviderId())

        assertEquals(
            Json.encodeToString(
                listOf(
                    CredentialSetting(id = "cred-1", type = CredentialType.API_KEY, name = "Primary", value = "secret"),
                )
            ),
            store.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS)
        )
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
