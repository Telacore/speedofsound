package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.asr.DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID
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

        assertEquals(TEXT_OUTPUT_METHOD_CLIPBOARD, client.loadTextOutputMethod())
        assertEquals(DEFAULT_WELCOME_SCREEN_SHOWN, client.loadWelcomeScreenShown())
        assertEquals(true, client.loadShortcutConfigured())
        assertEquals(DEFAULT_BACKGROUND_RECORDING, client.loadBackgroundRecording())
        assertEquals(DEFAULT_POST_HIDE_DELAY_MS, client.loadPostHideDelayMs())
        assertEquals(3, client.loadTypingDelayMs())
        assertEquals(MAX_MAX_ALARMS, client.loadMaxAlarms())

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

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, client.loadTextOutputMethod())
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

        assertEquals(DEFAULT_LANGUAGE.iso2, client.loadDefaultLanguage())
        assertEquals(DEFAULT_SECONDARY_LANGUAGE.iso2, client.loadSecondaryLanguage())
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
        assertEquals(2, store.writeCount)
    }

    @Test
    fun `peek selected provider snapshots are normalized without writing`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "missing-voice-provider",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "missing-text-provider",
            )
        )
        val client = SettingsClient(store)

        val expectedVoiceProvider = client.peekVoiceModelProviders()
            .sortedBy { it.name.lowercase() }
            .firstOrNull()
        val expectedTextProvider = client.peekTextModelProviders()
            .sortedBy { it.name.lowercase() }
            .firstOrNull()

        assertEquals(expectedVoiceProvider, client.peekSelectedVoiceModelProvider())
        assertEquals(expectedTextProvider, client.peekSelectedTextModelProvider())
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `exact voice provider id is preserved when provider is not visible`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID,
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
        val client = SettingsClient(store)

        assertEquals(DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID, client.peekSelectedVoiceModelProviderIdExact())
        assertEquals("voice-a", client.peekSelectedVoiceModelProviderId())
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `malformed provider credential refs are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CREDENTIALS to Json.encodeToString(
                    listOf(
                        CredentialSetting(id = "cred-keep", type = CredentialType.API_KEY, name = "Keep", value = "keep"),
                    )
                ),
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-1",
                            name = "Whisper",
                            provider = AsrProvider.SHERPA_WHISPER,
                            modelId = "model-1",
                            credentialId = "missing-credential",
                        ),
                    )
                ),
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-1",
                            name = "LLM",
                            provider = LlmProvider.OPENAI,
                            modelId = "model-2",
                            credentialId = "missing-credential",
                        ),
                    )
                ),
            )
        )
        val client = SettingsClient(store)

        assertEquals(null, client.loadVoiceModelProviders().first { it.id == "voice-1" }.credentialId)
        assertEquals(null, client.loadTextModelProviders().first { it.id == "text-1" }.credentialId)
        assertEquals(
            listOf(
                VoiceModelProviderSetting(
                    id = "voice-1",
                    name = "Whisper",
                    provider = AsrProvider.SHERPA_WHISPER,
                    modelId = "model-1",
                    credentialId = null,
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
        assertEquals(2, store.writeCount)
    }

    @Test
    fun `peek director and portal reads do not heal malformed startup settings`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_DEFAULT_LANGUAGE to "EN",
                KEY_SECONDARY_LANGUAGE to "zz",
                KEY_TEXT_PROCESSING_ENABLED to "maybe",
                KEY_CUSTOM_CONTEXT to "x".repeat(MAX_CUSTOM_CONTEXT_CHARS + 11),
                KEY_CUSTOM_VOCABULARY to " alpha ||| |||beta|||alpha ",
                KEY_PORTALS_RESTORE_TOKEN to " token ",
            )
        )
        val client = SettingsClient(store)

        val directorOptions = client.peekDirectorOptions()
        assertEquals(com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_TEXT_PROCESSING_ENABLED, directorOptions.enableTextProcessing)
        assertEquals(com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_LANGUAGE, directorOptions.language)
        assertEquals(listOf("alpha", "beta"), directorOptions.customVocabulary)
        assertEquals(MAX_CUSTOM_CONTEXT_CHARS, directorOptions.customContext.length)
        assertEquals("token", client.peekPortalsRestoreToken())
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `load startup state heals portal token alarms and scheduler state`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_PORTALS_RESTORE_TOKEN to " token ",
                KEY_ALARMS to "{bad",
                KEY_ALARM_SCHEDULER_STATE to "{bad",
            )
        )
        val client = SettingsClient(store)

        client.loadStartupState()

        assertEquals("token", store.getString(KEY_PORTALS_RESTORE_TOKEN, DEFAULT_PORTALS_RESTORE_TOKEN))
        assertEquals(DEFAULT_ALARMS, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
        assertEquals(
            AlarmSchedulerState(),
            client.peekAlarmSchedulerState()
        )
        assertEquals(3, store.writeCount)
    }

    @Test
    fun `load startup state reads provider collections once while healing selected ids`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_VOICE_MODEL_PROVIDERS to "{bad",
                KEY_TEXT_MODEL_PROVIDERS to "{bad",
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "missing-voice-provider",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "missing-text-provider",
            )
        )
        val client = SettingsClient(store)

        client.loadStartupState()

        assertEquals(1, store.stringReadCount(KEY_VOICE_MODEL_PROVIDERS))
        assertEquals(1, store.stringReadCount(KEY_TEXT_MODEL_PROVIDERS))
        assertEquals("[]", store.rawString(KEY_VOICE_MODEL_PROVIDERS))
        assertEquals("[]", store.rawString(KEY_TEXT_MODEL_PROVIDERS))
        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, store.rawString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID))
        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, store.rawString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID))
        assertEquals(4, store.writeCount)
    }

    @Test
    fun `provider resolution does not heal malformed credential and language settings`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_DEFAULT_LANGUAGE to "EN",
                KEY_CREDENTIALS to "{bad",
            )
        )
        val client = SettingsClient(store)

        val asrOptions = client.resolveVoiceProviderOptions(
            VoiceModelProviderSetting(
                id = "voice-1",
                name = "Whisper",
                provider = AsrProvider.SHERPA_WHISPER,
                modelId = "model-1",
                credentialId = "cred-1",
            )
        )
        val llmOptions = client.resolveTextProviderOptions(
            TextModelProviderSetting(
                id = "text-1",
                name = "LLM",
                provider = LlmProvider.OPENAI,
                modelId = "model-2",
                credentialId = "cred-1",
            )
        )

        assertEquals(com.zugaldia.speedofsound.core.plugins.asr.SherpaWhisperAsrOptions::class, asrOptions::class)
        assertEquals(com.zugaldia.speedofsound.core.plugins.llm.OpenAiLlmOptions::class, llmOptions::class)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `peek reads do not heal malformed shared settings`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to "not-a-method",
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to "missing-voice-provider",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "missing-text-provider",
                KEY_TEXT_PROCESSING_ENABLED to "maybe",
                KEY_CUSTOM_CONTEXT to "x".repeat(MAX_CUSTOM_CONTEXT_CHARS + 17),
                KEY_CUSTOM_VOCABULARY to " alpha ||| |||beta|||alpha ",
                KEY_CREDENTIALS to "{bad",
            )
        )
        val client = SettingsClient(store)

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, client.peekTextOutputMethod())
        assertEquals(DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID, client.peekSelectedVoiceModelProviderId())
        assertEquals(DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID, client.peekSelectedTextModelProviderId())
        assertEquals(DEFAULT_TEXT_PROCESSING_ENABLED, client.peekTextProcessingEnabled())
        assertEquals(MAX_CUSTOM_CONTEXT_CHARS, client.peekCustomContext().length)
        assertEquals(listOf("alpha", "beta"), client.peekCustomVocabulary())
        assertEquals(emptyList<CredentialSetting>(), client.peekCredentials())
        assertEquals(0, store.writeCount)
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
    fun `oversized credential provider and vocabulary json is capped on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CREDENTIALS to Json.encodeToString(
                    buildList {
                        add(
                            CredentialSetting(
                                id = "cred-0",
                                type = CredentialType.API_KEY,
                                name = " ${"n".repeat(MAX_CREDENTIAL_NAME_LENGTH + 5)} ",
                                value = " ${"v".repeat(MAX_CREDENTIAL_VALUE_LENGTH + 5)} ",
                            )
                        )
                        repeat(MAX_CREDENTIALS + 4) { index ->
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
                ),
                KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    buildList {
                        repeat(MAX_VOICE_MODEL_PROVIDERS + 4) { index ->
                            add(
                                VoiceModelProviderSetting(
                                    id = "voice-$index",
                                    name = " ${"w".repeat(MAX_PROVIDER_CONFIG_NAME_LENGTH + 4)} ",
                                    provider = AsrProvider.SHERPA_WHISPER,
                                    modelId = "model-$index",
                                )
                            )
                        }
                    }
                ),
                KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    buildList {
                        repeat(MAX_TEXT_MODEL_PROVIDERS + 4) { index ->
                            add(
                                TextModelProviderSetting(
                                    id = "text-$index",
                                    name = " ${"t".repeat(MAX_PROVIDER_CONFIG_NAME_LENGTH + 4)} ",
                                    provider = LlmProvider.OPENAI,
                                    modelId = "model-$index",
                                    disableThinking = index % 2 == 0,
                                )
                            )
                        }
                    }
                ),
                KEY_CUSTOM_VOCABULARY to (0 until MAX_VOCABULARY_WORDS + 7).joinToString("|||") { " word-$it " },
            )
        )
        val client = SettingsClient(store)

        val credentials = client.loadCredentials()
        val voiceProviders = client.loadVoiceModelProviders().filter { it.id.startsWith("voice-") }
        val textProviders = client.loadTextModelProviders()
        val vocabulary = client.loadCustomVocabulary()

        assertEquals(MAX_CREDENTIALS, credentials.size)
        assertEquals(MAX_CREDENTIAL_NAME_LENGTH, credentials.first().name.length)
        assertEquals(MAX_CREDENTIAL_VALUE_LENGTH, credentials.first().value.length)
        assertEquals(MAX_VOICE_MODEL_PROVIDERS, voiceProviders.size)
        assertEquals(MAX_PROVIDER_CONFIG_NAME_LENGTH, voiceProviders.first().name.length)
        assertEquals(MAX_TEXT_MODEL_PROVIDERS, textProviders.size)
        assertEquals(MAX_PROVIDER_CONFIG_NAME_LENGTH, textProviders.first().name.length)
        assertEquals(MAX_VOCABULARY_WORDS, vocabulary.size)
        assertEquals(MAX_CREDENTIALS, Json.decodeFromString<List<CredentialSetting>>(store.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS)).size)
        assertEquals(MAX_VOICE_MODEL_PROVIDERS, Json.decodeFromString<List<VoiceModelProviderSetting>>(store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)).size)
        assertEquals(MAX_TEXT_MODEL_PROVIDERS, Json.decodeFromString<List<TextModelProviderSetting>>(store.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS)).size)
        assertEquals(MAX_VOCABULARY_WORDS, store.getStringArray(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY).size)
        assertEquals(4, store.writeCount)
    }

    @Test
    fun `malformed portals restore token is healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_PORTALS_RESTORE_TOKEN to "  smoke-token  "
            )
        )
        val client = SettingsClient(store)

        assertEquals("smoke-token", client.loadPortalsRestoreToken())
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

        assertEquals(listOf("alpha", "beta", "gamma"), client.loadCustomVocabulary())
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
        assertEquals(expected, client.loadCustomContext())
        assertEquals(expected, store.getString(KEY_CUSTOM_CONTEXT, DEFAULT_CUSTOM_CONTEXT))
        assertEquals(1, store.writeCount)
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues
        var writeCount: Int = 0
        private val stringReadCounts = mutableMapOf<String, Int>()

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String {
            stringReadCounts[key] = (stringReadCounts[key] ?: 0) + 1
            return values[key] ?: defaultValue
        }

        fun rawString(key: String): String? = values[key]

        fun stringReadCount(key: String): Int = stringReadCounts[key] ?: 0

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
