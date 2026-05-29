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
            client.loadCredentials()
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
            client.loadVoiceModelProviders().filter { it.id == "voice-1" }
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
            client.loadTextModelProviders()
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
        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, client.loadSelectedVoiceModelProviderId())
        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, client.loadSelectedTextModelProviderId())

        assertEquals(
            Json.encodeToString(
                listOf(
                    CredentialSetting(id = "cred-1", type = CredentialType.API_KEY, name = "Primary", value = "secret"),
                )
            ),
            store.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS)
        )
    }

    @Test
    fun `setting providers heals stale selected provider ids on save`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-voice",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "stale-text",
            )
        )
        val client = SettingsClient(store)

        client.setVoiceModelProviders(
            listOf(
                VoiceModelProviderSetting(
                    id = "voice-z",
                    name = "Zulu",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-z",
                ),
                VoiceModelProviderSetting(
                    id = "voice-a",
                    name = "Alpha",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-a",
                ),
            )
        )
        client.setTextModelProviders(
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
        )

        val expectedVoiceProviderId = client.peekVoiceModelProviders()
            .sortedBy { it.name.lowercase() }
            .firstOrNull()
            ?.id
            ?: DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID
        val expectedTextProviderId = client.peekTextModelProviders()
            .sortedBy { it.name.lowercase() }
            .firstOrNull()
            ?.id
            ?: DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID

        assertEquals(
            expectedVoiceProviderId,
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)
        )
        assertEquals(
            expectedTextProviderId,
            store.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)
        )
        assertEquals(expectedVoiceProviderId, client.loadSelectedVoiceModelProviderId())
        assertEquals(expectedTextProviderId, client.loadSelectedTextModelProviderId())
        assertEquals(4, store.writeCount)
    }

    @Test
    fun `setting providers reports failure when selected provider write fails`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-voice",
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(emptyList<VoiceModelProviderSetting>()),
            ),
            failingSetStringKeys = setOf(KEY_VOICE_MODEL_PROVIDERS),
        )
        val client = SettingsClient(store)

        val provider = VoiceModelProviderSetting(
            id = "voice-a",
            name = "Alpha",
            provider = AsrProvider.SHERPA_WHISPER,
            modelId = "model-a",
        )

        val saved = client.setVoiceModelProviders(listOf(provider))

        assertEquals(false, saved)
        assertEquals(Json.encodeToString(emptyList<VoiceModelProviderSetting>()), store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS))
        assertEquals("stale-voice", store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID))
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `setting credentials stops when credential write fails`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CREDENTIALS to Json.encodeToString(
                    listOf(
                        CredentialSetting(id = "cred-keep", type = CredentialType.API_KEY, name = "Keep", value = "keep"),
                        CredentialSetting(id = "cred-drop", type = CredentialType.API_KEY, name = "Drop", value = "drop"),
                    )
                ),
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-1",
                            name = "Whisper",
                            provider = AsrProvider.SHERPA_WHISPER,
                            modelId = "model-1",
                            credentialId = "cred-drop",
                        ),
                    )
                ),
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "voice-1",
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-1",
                            name = "LLM",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-2",
                            credentialId = "cred-drop",
                        ),
                    )
                ),
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "text-1",
            ),
            failingSetStringKeys = setOf(KEY_CREDENTIALS),
        )
        val client = SettingsClient(store)

        val saved = client.setCredentials(
            listOf(
                CredentialSetting(id = "cred-keep", type = CredentialType.API_KEY, name = "Keep", value = "keep"),
            )
        )

        assertEquals(false, saved)
        assertEquals(
            listOf(
                VoiceModelProviderSetting(
                    id = "voice-1",
                    name = "Whisper",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-1",
                    credentialId = "cred-drop",
                ),
            ),
            Json.decodeFromString<List<VoiceModelProviderSetting>>(
                store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)
            )
        )
        assertEquals(
            listOf(
                TextModelProviderSetting(
                    id = "text-1",
                    name = "LLM",
                    provider = LlmProvider.OPENAI,
                    modelId = "model-2",
                    credentialId = null,
                ),
            ),
            Json.decodeFromString<List<TextModelProviderSetting>>(
                store.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS)
            )
        )
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `selected provider setters do not heal malformed provider json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_VOICE_MODEL_PROVIDERS to "{bad",
                KEY_TEXT_MODEL_PROVIDERS to "{bad",
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-voice",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "stale-text",
            )
        )
        val client = SettingsClient(store)

        client.setSelectedVoiceModelProviderId(" missing-voice-provider ")
        client.setSelectedTextModelProviderId(" missing-text-provider ")

        assertEquals("{bad", store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS))
        assertEquals("{bad", store.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS))
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
    fun `local voice providers are filtered from save`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setVoiceModelProviders(
            listOf(
                VoiceModelProviderSetting(
                    id = DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
                    name = "Whisper Tiny",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
                ),
                VoiceModelProviderSetting(
                    id = "voice-1",
                    name = "Whisper",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-1",
                ),
            )
        )

        assertEquals(
            Json.encodeToString(
                listOf(
                    VoiceModelProviderSetting(
                        id = "voice-1",
                        name = "Whisper",
                        provider = AsrProvider.SHERPA_WHISPER,
                        modelId = "model-1",
                    ),
                )
            ),
            store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)
        )
    }

    @Test
    fun `selected provider ids fall back to the first visible provider`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "stale-voice",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "stale-text",
            )
        )
        val client = SettingsClient(store)

        client.setVoiceModelProviders(
            listOf(
                VoiceModelProviderSetting(
                    id = "voice-z",
                    name = "Zulu",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-z",
                ),
                VoiceModelProviderSetting(
                    id = "voice-a",
                    name = "Alpha",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-a",
                ),
            )
        )
        client.setTextModelProviders(
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
        )

        val expectedVoiceProviderId = client.peekVoiceModelProviders().sortedBy { it.name.lowercase() }.first().id
        val expectedTextProviderId = client.peekTextModelProviders().sortedBy { it.name.lowercase() }.first().id

        assertEquals(expectedVoiceProviderId, client.peekSelectedVoiceModelProviderId())
        assertEquals(expectedTextProviderId, client.peekSelectedTextModelProviderId())
        assertEquals(expectedVoiceProviderId, client.loadSelectedVoiceModelProviderId())
        assertEquals(expectedTextProviderId, client.loadSelectedTextModelProviderId())
        assertEquals(
            expectedVoiceProviderId,
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)
        )
        assertEquals(
            expectedTextProviderId,
            store.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)
        )
        assertEquals(4, store.writeCount)
    }

    @Test
    fun `local voice providers do not consume custom limit slots`() {
        val localProviderId = SUPPORTED_LOCAL_ASR_MODELS.keys.first()
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setVoiceModelProviders(
            buildList {
                add(
                    VoiceModelProviderSetting(
                        id = localProviderId,
                        name = "Local",
                        provider = AsrProvider.SHERPA_WHISPER,
                        modelId = localProviderId,
                    )
                )
                repeat(MAX_VOICE_MODEL_PROVIDERS + 3) { index ->
                    add(
                        VoiceModelProviderSetting(
                            id = "voice-$index",
                            name = "Voice $index",
                            provider = AsrProvider.SHERPA_WHISPER,
                            modelId = "model-$index",
                        )
                    )
                }
            }
        )

        val storedVoiceProviders = Json.decodeFromString<List<VoiceModelProviderSetting>>(
            store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)
        )

        assertEquals(MAX_VOICE_MODEL_PROVIDERS, storedVoiceProviders.size)
        assertEquals("voice-0", storedVoiceProviders.first().id)
        assertEquals(false, storedVoiceProviders.any { it.id == localProviderId })
    }

    @Test
    fun `credential provider and vocabulary limits are enforced on save`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setCredentials(
            buildList {
                add(
                    CredentialSetting(
                        id = " cred-0 ",
                        type = CredentialType.API_KEY,
                        name = " ${"n".repeat(MAX_CREDENTIAL_NAME_LENGTH + 9)} ",
                        value = " ${"v".repeat(MAX_CREDENTIAL_VALUE_LENGTH + 9)} ",
                    )
                )
                repeat(MAX_CREDENTIALS + 5) { index ->
                    add(
                        CredentialSetting(
                            id = "cred-$index",
                            type = CredentialType.API_KEY,
                            name = "name-$index",
                            value = "value-$index",
                        )
                    )
                }
            }
        )
        client.setVoiceModelProviders(
            buildList {
                repeat(MAX_VOICE_MODEL_PROVIDERS + 5) { index ->
                    add(
                        VoiceModelProviderSetting(
                            id = "voice-$index",
                            name = " ${"w".repeat(MAX_PROVIDER_CONFIG_NAME_LENGTH + 7)} ",
                            provider = AsrProvider.SHERPA_WHISPER,
                            modelId = "model-$index",
                        )
                    )
                }
            }
        )
        client.setTextModelProviders(
            buildList {
                repeat(MAX_TEXT_MODEL_PROVIDERS + 5) { index ->
                    add(
                        TextModelProviderSetting(
                            id = "text-$index",
                            name = " ${"t".repeat(MAX_PROVIDER_CONFIG_NAME_LENGTH + 7)} ",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-$index",
                            disableThinking = index % 2 == 0,
                        )
                    )
                }
            }
        )
        client.setCustomVocabulary((0 until MAX_VOCABULARY_WORDS + 7).map { " word-$it " })

        val storedCredentials = Json.decodeFromString<List<CredentialSetting>>(
            store.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS)
        )
        val storedVoiceProviders = Json.decodeFromString<List<VoiceModelProviderSetting>>(
            store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)
        )
        val storedTextProviders = Json.decodeFromString<List<TextModelProviderSetting>>(
            store.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS)
        )

        assertEquals(MAX_CREDENTIALS, storedCredentials.size)
        assertEquals(MAX_CREDENTIAL_NAME_LENGTH, storedCredentials.first().name.length)
        assertEquals(MAX_CREDENTIAL_VALUE_LENGTH, storedCredentials.first().value.length)
        assertEquals(MAX_VOICE_MODEL_PROVIDERS, storedVoiceProviders.size)
        assertEquals(MAX_PROVIDER_CONFIG_NAME_LENGTH, storedVoiceProviders.first().name.length)
        assertEquals(MAX_TEXT_MODEL_PROVIDERS, storedTextProviders.size)
        assertEquals(MAX_PROVIDER_CONFIG_NAME_LENGTH, storedTextProviders.first().name.length)
        assertEquals(MAX_VOCABULARY_WORDS, store.getStringArray(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY).size)
        assertEquals(4, store.writeCount)
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
        private val failingSetStringKeys: Set<String> = emptySet(),
    ) : SettingsStore {
        private val values = initialValues
        private val failingKeys = failingSetStringKeys
        var writeCount: Int = 0

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            if (key in failingKeys) {
                return false
            }
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
