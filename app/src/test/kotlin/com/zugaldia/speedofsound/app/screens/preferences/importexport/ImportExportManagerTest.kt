package com.zugaldia.speedofsound.app.screens.preferences.importexport

import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.app.alarms.AlarmSchedulerService
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_ALARM_SCHEDULER_STATE
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_SCHEDULER_STATE
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_PROCESSING_ENABLED
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.desktop.settings.SettingsExport
import com.zugaldia.speedofsound.core.desktop.settings.MAX_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.MAX_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.MAX_VOCABULARY_WORDS
import com.zugaldia.speedofsound.core.desktop.settings.MAX_VOICE_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.MAX_PROVIDER_CONFIG_NAME_LENGTH
import com.zugaldia.speedofsound.core.desktop.settings.MAX_CREDENTIAL_NAME_LENGTH
import com.zugaldia.speedofsound.core.desktop.settings.MAX_CREDENTIAL_VALUE_LENGTH
import com.zugaldia.speedofsound.core.getDataDir
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.io.File

class ImportExportManagerTest {
    private val exportFile = getDataDir().resolve(ImportExportManager.EXPORT_FILENAME).toFile()

    @AfterTest
    fun cleanup() {
        exportFile.delete()
    }

    @Test
    fun `export includes alarm scheduler state and version 6`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                ),
            )
        )
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(6, exported.version)
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            exported.alarmSchedulerState
        )
    }

    @Test
    fun `import applies alarm scheduler state when present`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    alarmSchedulerState = AlarmSchedulerState(
                        lastCheckAt = "2026-05-29T09:15:30",
                        lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
                    ),
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertTrue(result.alarmSchedulerStateImported)
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            settingsClient.loadAlarmSchedulerState()
        )
    }

    @Test
    fun `import applies imported max alarms before merging alarms`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_MAX_ALARMS to "1",
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    maxAlarms = 3,
                    alarms = listOf(
                        AlarmSetting(
                            id = "alarm-1",
                            name = "Morning",
                            hour = 6,
                            minute = 0,
                            enabled = false,
                        ),
                        AlarmSetting(
                            id = "alarm-2",
                            name = "Evening",
                            hour = 18,
                            minute = 0,
                            enabled = false,
                        ),
                    ),
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertEquals(2, result.alarmsAdded)
        assertEquals(3, settingsClient.loadMaxAlarms())
        assertEquals(2, settingsClient.loadAlarms().size)
    }

    @Test
    fun `import aborts when credential write fails`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_DEFAULT_LANGUAGE to "de",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_MAX_ALARMS to "7",
            ),
            failingStringKeys = setOf(com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS)
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    credentials = listOf(
                        CredentialSetting(
                            id = "cred-1",
                            type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                            name = "Primary",
                            value = "secret",
                        )
                    ),
                    voiceModelProviders = listOf(
                        VoiceModelProviderSetting(
                            id = "voice-1",
                            name = "Voice",
                            provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                            modelId = "model-1",
                            credentialId = "cred-1",
                        )
                    ),
                )
            )
        )

        val exception = assertFailsWith<IllegalStateException> {
            manager.importSettings().getOrThrow()
        }

        assertTrue(exception.message?.contains("Failed to save credentials during import") == true)
        assertEquals("de", store.rawValue(com.zugaldia.speedofsound.core.desktop.settings.KEY_DEFAULT_LANGUAGE))
        assertEquals(7, settingsClient.loadMaxAlarms())
        assertEquals(emptyList<CredentialSetting>(), settingsClient.peekCredentials())
        assertEquals(emptyList<VoiceModelProviderSetting>(), settingsClient.peekVoiceModelProviders(emptySet()).filter { it.id !in com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS.keys })
        assertEquals(null, store.rawValue(com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS))
        assertEquals(null, store.rawValue(com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS))
    }

    @Test
    fun `import restores exact whisper selection after rollback`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_SELECTED_VOICE_MODEL_PROVIDER_ID to DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID,
                com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-a",
                            name = "Alpha",
                            provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.OPENAI,
                            modelId = "model-a",
                        ),
                    )
                ),
            ),
            failingStringKeys = setOf(com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS),
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    credentials = listOf(
                        CredentialSetting(
                            id = "cred-1",
                            type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                            name = "Primary",
                            value = "secret",
                        )
                    ),
                )
            )
        )

        assertFailsWith<IllegalStateException> {
            manager.importSettings().getOrThrow()
        }

        assertEquals(DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID, settingsClient.peekSelectedVoiceModelProviderIdExact())
        assertEquals(DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID, settingsClient.loadSelectedVoiceModelProviderId())
        assertEquals(
            DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID,
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)
        )
    }

    @Test
    fun `import filters alarm scheduler history to imported alarms`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    alarms = listOf(
                        AlarmSetting(
                            id = "alarm-1",
                            name = "Morning",
                            hour = 6,
                            minute = 0,
                            enabled = false,
                        ),
                    ),
                    alarmSchedulerState = AlarmSchedulerState(
                        lastCheckAt = "2026-05-29T09:15:00",
                        lastTriggeredDates = mapOf(
                            "alarm-1" to "2026-05-29",
                            "alarm-2" to "2026-05-28",
                        ),
                    ),
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertTrue(result.alarmSchedulerStateImported)
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            settingsClient.peekAlarmSchedulerState()
        )
    }

    @Test
    fun `import preserves alarm scheduler state after alarm reloads`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        val schedulerService = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        schedulerService.connect()

        try {
            val viewModel = PreferencesViewModel(
                settingsClient = settingsClient,
                portalsClient = PortalsClient(portalConnector = {
                    Result.failure<DesktopPortal>(IllegalStateException("no portal"))
                }),
            )
            val manager = ImportExportManager(viewModel)

            exportFile.writeText(
                Json.encodeToString(
                    SettingsExport(
                        version = 6,
                        alarms = listOf(
                            AlarmSetting(
                                id = "alarm-1",
                                name = "Morning",
                                hour = 6,
                                minute = 0,
                                enabled = false,
                            ),
                        ),
                        alarmSchedulerState = AlarmSchedulerState(
                            lastCheckAt = "2026-05-29T09:15:00",
                            lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
                        ),
                    )
                )
            )

            val result = manager.importSettings().getOrThrow()

            assertTrue(result.filePath.isNotBlank())
            assertTrue(result.alarmSchedulerStateImported)
            assertEquals(
                AlarmSchedulerState(
                    lastCheckAt = "2026-05-29T09:15:00",
                    lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
                ),
                settingsClient.peekAlarmSchedulerState()
            )
        } finally {
            schedulerService.close()
        }
    }

    @Test
    fun `import still accepts older export versions without scheduler state`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(Json.encodeToString(SettingsExport(version = 5)))

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertEquals(false, result.alarmSchedulerStateImported)
        assertEquals(AlarmSchedulerState(), settingsClient.loadAlarmSchedulerState())
    }

    @Test
    fun `import leaves malformed existing collections untouched when merge adds nothing`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS to "{bad",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS to "{bad",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS to "{bad",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_CUSTOM_VOCABULARY to " alpha ||| |||beta|||alpha ",
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(Json.encodeToString(SettingsExport(version = 6)))

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertEquals(0, store.writeCount)
        assertEquals(emptyList<CredentialSetting>(), settingsClient.peekCredentials())
        assertEquals(emptyList<VoiceModelProviderSetting>(), settingsClient.peekVoiceModelProviders(emptySet()).filter { it.id !in com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS.keys })
        assertEquals(emptyList<TextModelProviderSetting>(), settingsClient.peekTextModelProviders(emptySet()))
        assertEquals(listOf("alpha", "beta"), settingsClient.peekCustomVocabulary())
    }

    @Test
    fun `export does not migrate legacy alarm scheduler state`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(
                    mapOf(
                        "alarm-1" to "2026-05-29",
                    )
                ),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30",
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            exported.alarmSchedulerState
        )
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `export does not heal invalid alarms`() {
        val validAlarm = AlarmSetting(id = "alarm-1", name = "Morning", hour = 6, minute = 0, action = AlarmAction.ATTENTION)
        val invalidAlarm = AlarmSetting(id = "alarm-2", name = "Broken", hour = 25, minute = 0, action = AlarmAction.URGENT)
        val rawJson = Json.encodeToString(listOf(validAlarm, invalidAlarm))
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to rawJson,
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(listOf(validAlarm), exported.alarms)
        assertEquals(rawJson, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `export stays side effect free for malformed shared settings`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_DEFAULT_LANGUAGE to "EN",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_SECONDARY_LANGUAGE to "zz",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_BACKGROUND_RECORDING to "maybe",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_HIDE_INSTEAD_OF_MINIMIZE to "maybe",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_STAY_HIDDEN_ON_ACTIVATION to "maybe",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_APPEND_SPACE to "maybe",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_MAX_ALARMS to "999",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_SANITIZE_SPECIAL_CHARS to "maybe",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_POST_HIDE_DELAY_MS to "250ms",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_TYPING_DELAY_MS to "003",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_CUSTOM_CONTEXT to "x".repeat(com.zugaldia.speedofsound.core.desktop.settings.MAX_CUSTOM_CONTEXT_CHARS + 13),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_CUSTOM_VOCABULARY to " alpha ||| |||beta|||alpha ",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS to Json.encodeToString(
                    listOf(
                        CredentialSetting(
                            id = " cred-1 ",
                            type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                            name = " Primary ",
                            value = " secret ",
                        )
                    )
                ),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = " voice-1 ",
                            name = " Whisper ",
                            provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                            modelId = " model-1 ",
                            credentialId = " cred-1 ",
                            baseUrl = " https://example.com ",
                        )
                    )
                ),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = " text-1 ",
                            name = " LLM ",
                            provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                            modelId = " model-2 ",
                            credentialId = " cred-1 ",
                            baseUrl = " https://example.com ",
                            disableThinking = true,
                        )
                    )
                ),
                KEY_ALARMS to "{bad",
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(0, store.writeCount)
        assertEquals("en", exported.defaultLanguage)
        assertEquals("es", exported.secondaryLanguage)
        assertEquals(false, exported.backgroundRecording)
        assertEquals(false, exported.hideInsteadOfMinimize)
        assertEquals(false, exported.stayHiddenOnActivation)
        assertEquals(false, exported.appendSpace)
        assertEquals(false, exported.sanitizeSpecialChars)
        assertEquals(100, exported.postHideDelayMs)
        assertEquals(3, exported.typingDelayMs)
        assertEquals(2, exported.customVocabulary.size)
        assertEquals(2000, exported.customContext.length)
        assertEquals(1, exported.credentials.size)
        assertEquals("cred-1", exported.credentials.first().id)
        assertEquals(1, exported.voiceModelProviders.size)
        assertEquals("voice-1", exported.voiceModelProviders.first().id)
        assertEquals(1, exported.textModelProviders.size)
        assertEquals("text-1", exported.textModelProviders.first().id)
        assertEquals(50, exported.maxAlarms)
        assertEquals(emptyList<AlarmSetting>(), exported.alarms)
        assertEquals(1, store.stringReadCount(com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS))
    }

    @Test
    fun `export clears dangling provider credential refs`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS to Json.encodeToString(
                    listOf(
                        CredentialSetting(
                            id = "cred-1",
                            type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                            name = "Primary",
                            value = "secret",
                        )
                    )
                ),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        VoiceModelProviderSetting(
                            id = "voice-1",
                            name = "Voice",
                            provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                            modelId = "model-1",
                            credentialId = "missing-cred",
                        )
                    )
                ),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                    listOf(
                        TextModelProviderSetting(
                            id = "text-1",
                            name = "Text",
                            provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                            modelId = "model-2",
                            credentialId = "missing-cred",
                        )
                    )
                ),
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(null, exported.voiceModelProviders.first().credentialId)
        assertEquals(null, exported.textModelProviders.first().credentialId)
        assertEquals(0, store.writeCount)
    }

    @Test
    fun `import reuses credential snapshot when capturing state`() {
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(Json.encodeToString(SettingsExport(version = 6)))

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertEquals(2, store.stringReadCount(com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS))
    }

    @Test
    fun `import heals malformed alarms during merge`() {
        val rawJson = "{not-json"
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to rawJson,
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(Json.encodeToString(SettingsExport(version = 6)))

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertEquals(DEFAULT_ALARMS, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `import counts only normalized additions`() {
        val settingsClient = SettingsClient(
            MapSettingsStore(
                initialValues = mutableMapOf(
                    com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS to Json.encodeToString(
                        listOf(
                            CredentialSetting(id = "existing-cred", type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY, name = "Existing", value = "secret")
                        )
                    ),
                    com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS to Json.encodeToString(
                        listOf(
                            VoiceModelProviderSetting(
                                id = "existing-voice",
                                name = "Existing Voice",
                                provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                                modelId = "model-existing",
                            )
                        )
                    ),
                    com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS to Json.encodeToString(
                        listOf(
                            TextModelProviderSetting(
                                id = "existing-text",
                                name = "Existing Text",
                                provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                                modelId = "model-existing",
                            )
                        )
                    ),
                    com.zugaldia.speedofsound.core.desktop.settings.KEY_CUSTOM_VOCABULARY to "existing-word",
                )
            )
        )
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    credentials = buildList {
                        add(
                            CredentialSetting(
                                id = "existing-cred",
                                type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                                name = "duplicate",
                                value = "ignored",
                            )
                        )
                        repeat(MAX_CREDENTIALS + 7) { index ->
                            add(
                                CredentialSetting(
                                    id = "import-cred-$index",
                                    type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                                    name = " ${"n".repeat(MAX_CREDENTIAL_NAME_LENGTH + 4)} ",
                                    value = " ${"v".repeat(MAX_CREDENTIAL_VALUE_LENGTH + 4)} ",
                                )
                            )
                        }
                    },
                    voiceModelProviders = buildList {
                        add(
                            VoiceModelProviderSetting(
                                id = "existing-voice",
                                name = "duplicate",
                                provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                                modelId = "ignored",
                            )
                        )
                        repeat(MAX_VOICE_MODEL_PROVIDERS + 7) { index ->
                            add(
                                VoiceModelProviderSetting(
                                    id = "import-voice-$index",
                                    name = " ${"w".repeat(MAX_PROVIDER_CONFIG_NAME_LENGTH + 4)} ",
                                    provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                                    modelId = "model-$index",
                                )
                            )
                        }
                    },
                    textModelProviders = buildList {
                        add(
                            TextModelProviderSetting(
                                id = "existing-text",
                                name = "duplicate",
                                provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                                modelId = "ignored",
                            )
                        )
                        repeat(MAX_TEXT_MODEL_PROVIDERS + 7) { index ->
                            add(
                                TextModelProviderSetting(
                                    id = "import-text-$index",
                                    name = " ${"t".repeat(MAX_PROVIDER_CONFIG_NAME_LENGTH + 4)} ",
                                    provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                                    modelId = "model-$index",
                                )
                            )
                        }
                    },
                    customVocabulary = buildList {
                        add(" existing-word ")
                        repeat(MAX_VOCABULARY_WORDS + 7) { index ->
                            add(" word-$index ")
                        }
                    },
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertEquals(MAX_CREDENTIALS - 1, result.credentialsAdded)
        assertEquals(MAX_VOICE_MODEL_PROVIDERS - 1, result.voiceProvidersAdded)
        assertEquals(MAX_TEXT_MODEL_PROVIDERS - 1, result.textProvidersAdded)
        assertEquals(MAX_VOCABULARY_WORDS - 1, result.vocabularyWordsAdded)
        assertEquals(2, store.stringReadCount(com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS))
        assertEquals(MAX_CREDENTIALS, settingsClient.loadCredentials().size)
        assertEquals(
            MAX_VOICE_MODEL_PROVIDERS,
            settingsClient.loadVoiceModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).filter { it.id !in com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS.keys }.size
        )
        assertEquals(MAX_TEXT_MODEL_PROVIDERS, settingsClient.loadTextModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).size)
        assertEquals(MAX_VOCABULARY_WORDS, settingsClient.loadCustomVocabulary().size)
    }

    @Test
    fun `import clears provider credential refs that lose their credential during merge`() {
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    credentials = buildList {
                        repeat(MAX_CREDENTIALS) { index ->
                            add(
                                CredentialSetting(
                                    id = "cred-$index",
                                    type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                                    name = "Credential $index",
                                    value = "secret-$index",
                                )
                            )
                        }
                        add(
                            CredentialSetting(
                                id = "dangling-cred",
                                type = com.zugaldia.speedofsound.core.desktop.settings.CredentialType.API_KEY,
                                name = "Dangling",
                                value = "secret-dangling",
                            )
                        )
                    },
                    voiceModelProviders = listOf(
                        VoiceModelProviderSetting(
                            id = "voice-1",
                            name = "Voice",
                            provider = com.zugaldia.speedofsound.core.plugins.asr.AsrProvider.SHERPA_WHISPER,
                            modelId = "model-1",
                            credentialId = "dangling-cred",
                        )
                    ),
                    textModelProviders = listOf(
                        TextModelProviderSetting(
                            id = "text-1",
                            name = "Text",
                            provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                            modelId = "model-2",
                            credentialId = "dangling-cred",
                        )
                    ),
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertEquals(MAX_CREDENTIALS, result.credentialsAdded)
        assertEquals(1, result.voiceProvidersAdded)
        assertEquals(1, result.textProvidersAdded)
        assertEquals(MAX_CREDENTIALS, settingsClient.loadCredentials().size)
        assertEquals("voice-1", settingsClient.loadVoiceModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).first().id)
        assertEquals(null, settingsClient.loadVoiceModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).first().credentialId)
        assertEquals("text-1", settingsClient.loadTextModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).first().id)
        assertEquals(null, settingsClient.loadTextModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).first().credentialId)
        assertTrue(
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, "").isNotBlank()
        )
        assertTrue(
            store.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, "") in settingsClient.loadVoiceModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).map { it.id }.toSet()
        )
        assertEquals(
            "text-1",
            store.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, "")
        )
    }

    @Test
    fun `import restores enabled text processing after provider refresh disables it`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_PROCESSING_ENABLED to "true",
                KEY_SELECTED_TEXT_MODEL_PROVIDER_ID to "missing-text",
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    textModelProviders = listOf(
                        TextModelProviderSetting(
                            id = "text-1",
                            name = "Text",
                            provider = com.zugaldia.speedofsound.core.plugins.llm.LlmProvider.OPENAI,
                            modelId = "model-2",
                        )
                    ),
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertEquals(1, result.textProvidersAdded)
        assertEquals("text-1", settingsClient.loadTextModelProviders(settingsClient.peekCredentials().map { it.id }.toSet()).first().id)
        assertEquals(
            "text-1",
            settingsClient.peekSelectedTextModelProviderId(
                settingsClient.peekTextModelProviders(settingsClient.peekCredentials().map { it.id }.toSet())
            )
        )
        assertEquals("true", store.getString(KEY_TEXT_PROCESSING_ENABLED, "false"))
    }

    @Test
    fun `import rejects directory export paths`() {
        exportFile.mkdirs()

        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val result = manager.importSettings()

        assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `import rejects malformed export json`() {
        exportFile.writeText("{bad")

        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val result = manager.importSettings()

        val exception = assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }

        assertTrue(exception.message?.contains("Malformed export file") == true)
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
        private val failingStringKeys: Set<String> = emptySet(),
    ) : SettingsStore {
        private val values = initialValues
        var writeCount: Int = 0
        private val stringReadCounts = mutableMapOf<String, Int>()

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String {
            stringReadCounts[key] = (stringReadCounts[key] ?: 0) + 1
            return values[key] ?: defaultValue
        }

        override fun setString(key: String, value: String): Boolean {
            if (key in failingStringKeys) {
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

        fun stringReadCount(key: String): Int = stringReadCounts[key] ?: 0

        fun rawValue(key: String): String? = values[key]
    }
}
