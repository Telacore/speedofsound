package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.languageFromIso2
import com.zugaldia.speedofsound.core.models.voice.ModelManager
import com.zugaldia.speedofsound.core.plugins.asr.AsrPluginOptions
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.asr.OpenAiAsrOptions
import com.zugaldia.speedofsound.core.plugins.asr.SherpaCanaryAsrOptions
import com.zugaldia.speedofsound.core.plugins.asr.SherpaParakeetAsrOptions
import com.zugaldia.speedofsound.core.plugins.asr.SherpaWhisperAsrOptions
import com.zugaldia.speedofsound.core.plugins.director.DirectorOptions
import com.zugaldia.speedofsound.core.plugins.llm.AnthropicLlmOptions
import com.zugaldia.speedofsound.core.plugins.llm.GoogleLlmOptions
import com.zugaldia.speedofsound.core.plugins.llm.LlmPluginOptions
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import com.zugaldia.speedofsound.core.plugins.llm.OpenAiLlmOptions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

@Suppress("TooManyFunctions")
class SettingsClient(val settingsStore: SettingsStore) {
    private val logger = LoggerFactory.getLogger(SettingsClient::class.java)

    private val _settingsChanged = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val settingsChanged: SharedFlow<String> = _settingsChanged.asSharedFlow()

    /**
     * Resolves a VoiceModelProviderSetting into the appropriate AsrPluginOptions
     */
    fun resolveVoiceProviderOptions(providerSetting: VoiceModelProviderSetting): AsrPluginOptions {
        return resolveVoiceProviderOptions(
            providerSetting = providerSetting,
            credentials = peekCredentials(),
            defaultLanguageIso2 = peekDefaultLanguage(),
        )
    }

    fun resolveVoiceProviderOptions(
        providerSetting: VoiceModelProviderSetting,
        credentials: List<CredentialSetting>,
        defaultLanguageIso2: String,
    ): AsrPluginOptions {
        val apiKey = providerSetting.credentialId?.let { credId -> credentials.find { it.id == credId }?.value }
        val language = languageFromIso2(defaultLanguageIso2) ?: DEFAULT_LANGUAGE
        return when (providerSetting.provider) {
            AsrProvider.OPENAI -> OpenAiAsrOptions(
                modelId = providerSetting.modelId,
                language = language,
                baseUrl = providerSetting.baseUrl,
                apiKey = apiKey,
            )

            AsrProvider.SHERPA_CANARY -> SherpaCanaryAsrOptions(
                modelId = providerSetting.modelId,
                language = language,
            )

            AsrProvider.SHERPA_PARAKEET -> SherpaParakeetAsrOptions(
                modelId = providerSetting.modelId,
                language = language,
            )

            AsrProvider.SHERPA_WHISPER -> SherpaWhisperAsrOptions(
                modelId = providerSetting.modelId,
                language = language,
            )
        }
    }

    /**
     * Resolves a TextModelProviderSetting into the appropriate LlmPluginOptions
     */
    fun resolveTextProviderOptions(providerSetting: TextModelProviderSetting): LlmPluginOptions {
        return resolveTextProviderOptions(
            providerSetting = providerSetting,
            credentials = peekCredentials(),
        )
    }

    fun resolveTextProviderOptions(
        providerSetting: TextModelProviderSetting,
        credentials: List<CredentialSetting>,
    ): LlmPluginOptions {
        val apiKey = providerSetting.credentialId?.let { credId -> credentials.find { it.id == credId }?.value }
        return when (providerSetting.provider) {
            LlmProvider.ANTHROPIC -> AnthropicLlmOptions(
                apiKey = apiKey,
                modelId = providerSetting.modelId,
                baseUrl = providerSetting.baseUrl,
                disableThinking = providerSetting.disableThinking,
            )

            LlmProvider.GOOGLE -> GoogleLlmOptions(
                apiKey = apiKey,
                modelId = providerSetting.modelId,
                baseUrl = providerSetting.baseUrl,
                disableThinking = providerSetting.disableThinking,
            )

            LlmProvider.OPENAI -> OpenAiLlmOptions(
                apiKey = apiKey,
                modelId = providerSetting.modelId,
                baseUrl = providerSetting.baseUrl,
                disableThinking = providerSetting.disableThinking,
            )
        }
    }

    fun peekDirectorOptions(): DirectorOptions = DirectorOptions(
        enableTextProcessing = peekTextProcessingEnabled(),
        language = languageFromIso2(peekDefaultLanguage()) ?: DEFAULT_LANGUAGE,
        customVocabulary = peekCustomVocabulary(),
        customContext = peekCustomContext()
    )

    /*
     * Not exposed to the UI
     */

    fun loadWelcomeScreenShown(): Boolean =
        readBooleanSetting(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN)

    fun peekWelcomeScreenShown(): Boolean =
        peekBooleanSetting(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN)

    fun setWelcomeScreenShown(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_WELCOME_SCREEN_SHOWN,
            settingsStore.getString(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN.toString()),
            value
        )

    fun loadPortalsRestoreToken(): String =
        readPortalsRestoreToken()

    fun peekPortalsRestoreToken(): String =
        validatedSetting(
            key = KEY_PORTALS_RESTORE_TOKEN,
            defaultValue = DEFAULT_PORTALS_RESTORE_TOKEN,
            heal = false,
            parse = ::normalizePortalsRestoreToken,
        )

    fun loadStartupState() {
        loadWelcomeScreenShown()
        loadPortalsRestoreToken()
        loadShortcutConfigured()
        loadDefaultLanguage()
        loadSecondaryLanguage()
        loadBackgroundRecording()
        loadAppendSpace()
        loadHideInsteadOfMinimize()
        loadStayHiddenOnActivation()
        loadTextOutputMethod()
        loadMaxAlarms()
        loadAlarms()
        loadAlarmSchedulerState()
        val credentials = loadCredentials()
        val validCredentialIds = credentials.map { it.id }.toSet()
        val availableVoiceProviders = loadVoiceModelProviders(validCredentialIds)
        loadSelectedVoiceModelProviderId(availableVoiceProviders)
        loadTextProcessingEnabled()
        val availableTextProviders = loadTextModelProviders(validCredentialIds)
        loadSelectedTextModelProviderId(availableTextProviders)
        loadCustomContext()
        loadCustomVocabulary()
        loadSanitizeSpecialChars()
        loadPostHideDelayMs()
        loadTypingDelayMs()
    }

    fun loadSelectedVoiceModelProviderId(availableProviders: List<SelectableProviderSetting>): String {
        val exactSelectedProviderId = peekSelectedVoiceModelProviderIdExact()
        if (shouldPreserveExactWhisperSelection(exactSelectedProviderId, availableProviders)) {
            return exactSelectedProviderId
        }
        return readSelectedProviderId(
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
            DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
            availableProviders
        )
    }

    fun setPortalsRestoreToken(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_PORTALS_RESTORE_TOKEN,
            settingsStore.getString(KEY_PORTALS_RESTORE_TOKEN, DEFAULT_PORTALS_RESTORE_TOKEN),
            normalizePortalsRestoreToken(value),
            KEY_PORTALS_RESTORE_TOKEN
        )

    fun loadShortcutConfigured(): Boolean =
        readBooleanSetting(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED)

    fun peekShortcutConfigured(): Boolean =
        peekBooleanSetting(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED)

    fun setShortcutConfigured(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_SHORTCUT_CONFIGURED,
            settingsStore.getString(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED.toString()),
            value
        )

    /*
     * General page
     */

    fun loadDefaultLanguage(): String =
        readLanguageSetting(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2)

    fun peekDefaultLanguage(): String =
        peekLanguageSetting(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2)

    fun setDefaultLanguage(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_DEFAULT_LANGUAGE,
            settingsStore.getString(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2),
            languageFromIso2(value.trim().lowercase())?.iso2 ?: DEFAULT_LANGUAGE.iso2,
            KEY_DEFAULT_LANGUAGE
        )

    fun loadSecondaryLanguage(): String =
        readLanguageSetting(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2)

    fun peekSecondaryLanguage(): String =
        peekLanguageSetting(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2)

    fun setSecondaryLanguage(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_SECONDARY_LANGUAGE,
            settingsStore.getString(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2),
            languageFromIso2(value.trim().lowercase())?.iso2 ?: DEFAULT_SECONDARY_LANGUAGE.iso2,
            KEY_SECONDARY_LANGUAGE
        )

    fun loadBackgroundRecording(): Boolean =
        readBooleanSetting(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING)

    fun peekBackgroundRecording(): Boolean =
        peekBooleanSetting(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING)

    fun setBackgroundRecording(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_BACKGROUND_RECORDING,
            settingsStore.getString(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING.toString()),
            value,
            KEY_BACKGROUND_RECORDING
        )

    fun loadAppendSpace(): Boolean =
        readBooleanSetting(KEY_APPEND_SPACE, DEFAULT_APPEND_SPACE)

    fun peekAppendSpace(): Boolean =
        peekBooleanSetting(KEY_APPEND_SPACE, DEFAULT_APPEND_SPACE)

    fun setAppendSpace(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_APPEND_SPACE,
            settingsStore.getString(KEY_APPEND_SPACE, DEFAULT_APPEND_SPACE.toString()),
            value,
            KEY_APPEND_SPACE
        )

    fun loadHideInsteadOfMinimize(): Boolean =
        readBooleanSetting(KEY_HIDE_INSTEAD_OF_MINIMIZE, DEFAULT_HIDE_INSTEAD_OF_MINIMIZE)

    fun peekHideInsteadOfMinimize(): Boolean =
        peekBooleanSetting(KEY_HIDE_INSTEAD_OF_MINIMIZE, DEFAULT_HIDE_INSTEAD_OF_MINIMIZE)

    fun setHideInsteadOfMinimize(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_HIDE_INSTEAD_OF_MINIMIZE,
            settingsStore.getString(KEY_HIDE_INSTEAD_OF_MINIMIZE, DEFAULT_HIDE_INSTEAD_OF_MINIMIZE.toString()),
            value,
            KEY_HIDE_INSTEAD_OF_MINIMIZE
        )

    fun loadStayHiddenOnActivation(): Boolean =
        readBooleanSetting(KEY_STAY_HIDDEN_ON_ACTIVATION, DEFAULT_STAY_HIDDEN_ON_ACTIVATION)

    fun peekStayHiddenOnActivation(): Boolean =
        peekBooleanSetting(KEY_STAY_HIDDEN_ON_ACTIVATION, DEFAULT_STAY_HIDDEN_ON_ACTIVATION)

    fun setStayHiddenOnActivation(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_STAY_HIDDEN_ON_ACTIVATION,
            settingsStore.getString(KEY_STAY_HIDDEN_ON_ACTIVATION, DEFAULT_STAY_HIDDEN_ON_ACTIVATION.toString()),
            value,
            KEY_STAY_HIDDEN_ON_ACTIVATION
        )

    fun loadTextOutputMethod(): String =
        readTextOutputMethod()

    fun peekTextOutputMethod(): String =
        validatedSetting(
            key = KEY_TEXT_OUTPUT_METHOD,
            defaultValue = DEFAULT_TEXT_OUTPUT_METHOD,
            heal = false,
            parse = { raw ->
                val normalized = raw.trim().lowercase()
                if (normalized == TEXT_OUTPUT_METHOD_PORTAL || normalized == TEXT_OUTPUT_METHOD_CLIPBOARD) {
                    normalized
                } else {
                    null
                }
            },
        )

    fun setTextOutputMethod(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_TEXT_OUTPUT_METHOD,
            settingsStore.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD),
            value.trim().lowercase().takeIf {
                it == TEXT_OUTPUT_METHOD_PORTAL || it == TEXT_OUTPUT_METHOD_CLIPBOARD
            } ?: DEFAULT_TEXT_OUTPUT_METHOD,
            KEY_TEXT_OUTPUT_METHOD
        )

    /*
     * Alarms page
     */

    fun loadAlarms(): List<AlarmSetting> = readAlarms(heal = true)

    fun peekAlarms(): List<AlarmSetting> = readAlarms(heal = false)

    fun setAlarms(value: List<AlarmSetting>): Boolean {
        val normalizedAlarms = normalizeAlarms(value)
        val dropped = value.size - normalizedAlarms.size
        if (dropped > 0) {
            logger.warn("Adjusted {} alarm(s) while saving settings", dropped)
        }
        val json = Json.encodeToString(normalizedAlarms)
        return setStringSettingIfChanged(KEY_ALARMS, settingsStore.getString(KEY_ALARMS, DEFAULT_ALARMS), json, KEY_ALARMS)
    }

    fun loadAlarmSchedulerState(): AlarmSchedulerState {
        val rawJson = settingsStore.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        readAlarmSchedulerState()?.let { return it }
        val legacyLoad = loadLegacyAlarmSchedulerState()
        if (legacyLoad.state.lastCheckAt != null || legacyLoad.state.lastTriggeredDates.isNotEmpty()) {
            setAlarmSchedulerState(
                legacyLoad.state,
                emitChange = false,
                forceLegacyWrite = legacyLoad.dirty,
            )
        } else if (legacyLoad.dirty) {
            setAlarmSchedulerState(
                AlarmSchedulerState(),
                emitChange = false,
                forceLegacyWrite = true,
            )
        } else if (rawJson.isNotBlank() && rawJson != DEFAULT_ALARM_SCHEDULER_STATE) {
            setAlarmSchedulerState(AlarmSchedulerState(), emitChange = false)
        }
        return legacyLoad.state
    }

    fun peekAlarmSchedulerState(): AlarmSchedulerState =
        readAlarmSchedulerState() ?: loadLegacyAlarmSchedulerState().state

    fun setAlarmSchedulerState(
        value: AlarmSchedulerState,
        emitChange: Boolean = true,
        forceLegacyWrite: Boolean = false,
        persistLegacyState: Boolean = true,
    ): Boolean {
        val normalized = value.copy(
            lastCheckAt = value.lastCheckAt?.takeIf { it.isNotBlank() },
            lastTriggeredDates = value.lastTriggeredDates
                .mapKeys { (alarmId, _) -> alarmId.trim() }
                .mapValues { (_, dateValue) -> dateValue.trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
        )
        val currentCombined = readAlarmSchedulerState()
        val rawJson = settingsStore.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        val currentLegacy = if (persistLegacyState) loadLegacyAlarmSchedulerState() else null
        val alreadyStored = when {
            persistLegacyState && currentCombined != null ->
                currentCombined == normalized && currentLegacy?.state == normalized
            persistLegacyState && (rawJson.isBlank() || rawJson == DEFAULT_ALARM_SCHEDULER_STATE) ->
                normalized == AlarmSchedulerState() && currentLegacy?.state == AlarmSchedulerState()
            !persistLegacyState && currentCombined != null ->
                currentCombined == normalized
            !persistLegacyState && (rawJson.isBlank() || rawJson == DEFAULT_ALARM_SCHEDULER_STATE) ->
                normalized == AlarmSchedulerState()
            else -> false
        }
        if (alreadyStored && !(persistLegacyState && forceLegacyWrite)) {
            return true
        }
        val json = Json.encodeToString(normalized)
        val combinedChanged = currentCombined != normalized
        val combinedSaved = if (combinedChanged) {
            settingsStore.setString(KEY_ALARM_SCHEDULER_STATE, json).also { success ->
                if (success && emitChange) {
                    _settingsChanged.tryEmit(KEY_ALARM_SCHEDULER_STATE)
                }
            }
        } else {
            true
        }
        val legacySaved = if (persistLegacyState && (forceLegacyWrite || currentLegacy?.state != normalized)) {
            persistLegacyAlarmSchedulerState(
                state = normalized,
                currentState = currentLegacy?.state ?: AlarmSchedulerState(),
                forceWrite = forceLegacyWrite,
            )
        } else {
            true
        }
        return combinedSaved && legacySaved
    }

    fun loadAlarmLastTriggeredDates(): Map<String, LocalDate> =
        loadAlarmSchedulerState().lastTriggeredDates.mapNotNull { (alarmId, dateValue) ->
            runCatching { LocalDate.parse(dateValue) }
                .getOrNull()
                ?.let { parsedDate -> alarmId to parsedDate }
        }.toMap()

    fun peekAlarmLastTriggeredDates(): Map<String, LocalDate> =
        peekAlarmSchedulerState().lastTriggeredDates.mapNotNull { (alarmId, dateValue) ->
            runCatching { LocalDate.parse(dateValue) }
                .getOrNull()
                ?.let { parsedDate -> alarmId to parsedDate }
        }.toMap()

    fun loadAlarmLastCheckAt(): LocalDateTime? {
        val value = loadAlarmSchedulerState().lastCheckAt ?: return null
        return runCatching { LocalDateTime.parse(value) }
            .getOrElse { error ->
                logger.error("Failed to decode alarm last check timestamp", error)
                null
            }
    }

    fun peekAlarmLastCheckAt(): LocalDateTime? {
        val value = peekAlarmSchedulerState().lastCheckAt ?: return null
        return runCatching { LocalDateTime.parse(value) }
            .getOrElse { error ->
                logger.error("Failed to decode alarm last check timestamp", error)
                null
            }
    }

    fun setAlarmLastTriggeredDates(value: Map<String, LocalDate>): Boolean =
        setAlarmSchedulerState(
            peekAlarmSchedulerState().copy(
                lastTriggeredDates = value.mapValues { (_, date) -> date.toString() }
            ),
            persistLegacyState = false,
        )

    fun setAlarmLastTriggeredDate(alarmId: String, date: LocalDate): Boolean =
        peekAlarmSchedulerState().let { currentState ->
            setAlarmSchedulerState(
                currentState.copy(
                    lastTriggeredDates = currentState.lastTriggeredDates.toMutableMap().apply {
                        this[alarmId] = date.toString()
                    }
                ),
                persistLegacyState = false,
            )
        }

    fun setAlarmLastCheckAt(value: LocalDateTime): Boolean =
        setAlarmSchedulerState(
            peekAlarmSchedulerState().copy(lastCheckAt = value.toString()),
            persistLegacyState = false,
        )

    fun loadMaxAlarms(): Int =
        readIntSetting(
            key = KEY_MAX_ALARMS,
            defaultValue = DEFAULT_MAX_ALARMS,
        ) { it.coerceIn(MIN_MAX_ALARMS, MAX_MAX_ALARMS) }

    fun peekMaxAlarms(): Int =
        peekIntSetting(
            key = KEY_MAX_ALARMS,
            defaultValue = DEFAULT_MAX_ALARMS,
        ) { it.coerceIn(MIN_MAX_ALARMS, MAX_MAX_ALARMS) }

    fun setMaxAlarms(value: Int): Boolean {
        val normalized = value.coerceIn(MIN_MAX_ALARMS, MAX_MAX_ALARMS)
        val currentRaw = settingsStore.getString(KEY_MAX_ALARMS, DEFAULT_MAX_ALARMS.toString())
        if (currentRaw == normalized.toString()) {
            normalizeStoredAlarmsToCurrentLimit()
            return true
        }

        return settingsStore.setInt(KEY_MAX_ALARMS, normalized).also { success ->
            if (!success) return@also
            _settingsChanged.tryEmit(KEY_MAX_ALARMS)
            normalizeStoredAlarmsToCurrentLimit()
        }
    }

    private fun normalizeAlarms(value: List<AlarmSetting>): List<AlarmSetting> {
        val sortedUniqueAlarms = value.asSequence()
            .map { it.normalized() }
            .filter { it.isValid() }
            .sortedWith(
                compareBy<AlarmSetting> { it.hour }
                    .thenBy { it.minute }
                    .thenBy { it.action.ordinal }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            .distinctBy { it.id }
            .toList()

        return sortedUniqueAlarms.take(loadMaxAlarms())
    }

    private fun readAlarms(heal: Boolean): List<AlarmSetting> {
        val json = settingsStore.getString(KEY_ALARMS, DEFAULT_ALARMS)
        if (json.isEmpty() || json == DEFAULT_ALARMS) {
            return emptyList()
        }

        val decoded = runCatching {
            Json.decodeFromString<List<AlarmSetting>>(json)
        }.getOrElse { error ->
            logger.error("Failed to decode alarms from JSON", error)
            if (heal) {
                persistAlarms(emptyList(), emitChange = false)
            }
            return emptyList()
        }

        val normalized = normalizeAlarms(decoded)
        val dropped = decoded.size - normalized.size
        if (dropped > 0) {
            logger.warn("Adjusted {} alarm(s) while loading settings", dropped)
        }

        if (heal && decoded != normalized) {
            persistAlarms(normalized, emitChange = false)
        }

        return normalized
    }

    private fun persistAlarms(value: List<AlarmSetting>, emitChange: Boolean): Boolean {
        val json = Json.encodeToString(value)
        return settingsStore.setString(KEY_ALARMS, json).also { success ->
            if (success && emitChange) _settingsChanged.tryEmit(KEY_ALARMS)
        }
    }

    private fun normalizeStoredAlarmsToCurrentLimit() {
        val rawJson = settingsStore.getString(KEY_ALARMS, DEFAULT_ALARMS)
        if (rawJson.isEmpty() || rawJson == DEFAULT_ALARMS) {
            return
        }

        val decodeAttempt = runCatching {
            Json.decodeFromString<List<AlarmSetting>>(rawJson)
        }
        val decoded = decodeAttempt.getOrNull() ?: run {
            val error = decodeAttempt.exceptionOrNull() ?: IllegalStateException("Malformed alarm JSON")
            logger.error("Failed to decode alarms while applying a new limit", error)
            val clearedJson = Json.encodeToString(emptyList<AlarmSetting>())
            settingsStore.setString(KEY_ALARMS, clearedJson).also { success ->
                if (success) _settingsChanged.tryEmit(KEY_ALARMS)
            }
            return
        }
        val normalized = normalizeAlarms(decoded)
        if (decoded == normalized) {
            return
        }

        val json = Json.encodeToString(normalized)
        settingsStore.setString(KEY_ALARMS, json).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_ALARMS)
        }
    }

    private fun readAlarmSchedulerState(): AlarmSchedulerState? {
        val json = settingsStore.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        if (json.isBlank() || json == DEFAULT_ALARM_SCHEDULER_STATE) {
            return null
        }

        return runCatching {
            Json.decodeFromString<AlarmSchedulerState>(json)
        }.getOrElse { error ->
            logger.error("Failed to decode alarm scheduler state from JSON", error)
            null
        }
    }

    private fun loadLegacyAlarmSchedulerState(): LegacyAlarmSchedulerStateLoad {
        val legacyTriggeredDates = readLegacyAlarmLastTriggeredDates()
        val legacyCheckAt = readLegacyAlarmLastCheckAt()
        return LegacyAlarmSchedulerStateLoad(
            state = AlarmSchedulerState(
                lastCheckAt = legacyCheckAt.value?.toString(),
                lastTriggeredDates = legacyTriggeredDates.value.mapValues { (_, date) -> date.toString() },
            ),
            dirty = legacyTriggeredDates.dirty || legacyCheckAt.dirty,
        )
    }

    private fun readLegacyAlarmLastTriggeredDates(): LegacyAlarmTriggeredDatesLoad {
        val json = settingsStore.getString(KEY_ALARM_LAST_TRIGGERED_DATES, DEFAULT_ALARM_LAST_TRIGGERED_DATES)
        return if (json.isBlank() || json == DEFAULT_ALARM_LAST_TRIGGERED_DATES) {
            LegacyAlarmTriggeredDatesLoad(emptyMap(), false)
        } else {
            runCatching {
                val decoded = Json.decodeFromString<Map<String, String>>(json)
                val parsed = decoded.mapNotNull { (alarmId, dateValue) ->
                    runCatching { LocalDate.parse(dateValue) }
                        .getOrNull()
                        ?.let { parsedDate -> alarmId to parsedDate }
                }.toMap()
                LegacyAlarmTriggeredDatesLoad(parsed, parsed.size != decoded.size)
            }.getOrElse { error ->
                logger.error("Failed to decode alarm trigger dates from JSON", error)
                LegacyAlarmTriggeredDatesLoad(emptyMap(), true)
            }
        }
    }

    private fun readLegacyAlarmLastCheckAt(): LegacyAlarmCheckAtLoad {
        val value = settingsStore.getString(KEY_ALARM_LAST_CHECK_AT, DEFAULT_ALARM_LAST_CHECK_AT)
        return if (value.isBlank()) {
            LegacyAlarmCheckAtLoad(null, false)
        } else {
            runCatching { LocalDateTime.parse(value) }
                .map { LegacyAlarmCheckAtLoad(it, false) }
                .getOrElse { error ->
                    logger.error("Failed to decode alarm last check timestamp", error)
                    LegacyAlarmCheckAtLoad(null, true)
                }
        }
    }

    private fun persistLegacyAlarmSchedulerState(
        state: AlarmSchedulerState,
        currentState: AlarmSchedulerState,
        forceWrite: Boolean = false,
    ): Boolean {
        var saved = true

        if (forceWrite || currentState.lastTriggeredDates != state.lastTriggeredDates) {
            val triggerDatesJson = Json.encodeToString(state.lastTriggeredDates)
            val triggeredDatesSaved = settingsStore.setString(KEY_ALARM_LAST_TRIGGERED_DATES, triggerDatesJson)
            if (!triggeredDatesSaved) {
                logger.warn("Failed to persist legacy alarm trigger dates.")
                saved = false
            }
        }

        if (forceWrite || currentState.lastCheckAt != state.lastCheckAt) {
            val checkAtSaved = settingsStore.setString(KEY_ALARM_LAST_CHECK_AT, state.lastCheckAt ?: DEFAULT_ALARM_LAST_CHECK_AT)
            if (!checkAtSaved) {
                logger.warn("Failed to persist legacy alarm last check timestamp.")
                saved = false
            }
        }

        return saved
    }

    /*
     * Cloud Credentials page
     */

    fun loadCredentials(): List<CredentialSetting> {
        return readNormalizedJsonListSetting<CredentialSetting>(
            key = KEY_CREDENTIALS,
            defaultValue = DEFAULT_CREDENTIALS,
            label = "credentials",
            normalize = { it.normalizedCredentials() },
        )
    }

    fun peekCredentials(): List<CredentialSetting> =
        peekNormalizedJsonListSetting(
            key = KEY_CREDENTIALS,
            defaultValue = DEFAULT_CREDENTIALS,
            label = "credentials",
            normalize = { it.normalizedCredentials() },
        )

    fun setCredentials(value: List<CredentialSetting>): Boolean {
        val normalized = value.normalizedCredentials()
        val validCredentialIds = normalized.map { it.id }.toSet()
        return setCredentialsInternal(
            normalized = normalized,
            validCredentialIds = validCredentialIds,
            availableVoiceProviders = peekVoiceModelProviders(validCredentialIds),
            availableTextProviders = peekTextModelProviders(validCredentialIds),
        )
    }

    fun setCredentials(
        value: List<CredentialSetting>,
        availableVoiceProviders: List<VoiceModelProviderSetting>,
        availableTextProviders: List<TextModelProviderSetting>,
    ): Boolean {
        val normalized = value.normalizedCredentials()
        val validCredentialIds = normalized.map { it.id }.toSet()
        return setCredentialsInternal(
            normalized = normalized,
            validCredentialIds = validCredentialIds,
            availableVoiceProviders = availableVoiceProviders,
            availableTextProviders = availableTextProviders,
        )
    }

    /*
     * Voice Models page
     */

    private fun getLocalVoiceModelProviders(): List<VoiceModelProviderSetting> {
        val modelManager = ModelManager()
        return SUPPORTED_LOCAL_ASR_MODELS.values
            .filter { modelManager.isModelDownloaded(it.id) }
            .sortedBy { it.dataSizeMegabytes }
            .map { voiceModel ->
                VoiceModelProviderSetting(
                    id = voiceModel.id,
                    name = voiceModel.name,
                    provider = voiceModel.provider,
                    modelId = voiceModel.id,
                )
            }
    }

    fun loadVoiceModelProviders(validCredentialIds: Set<String>): List<VoiceModelProviderSetting> {
        val customProviders = readNormalizedJsonListSetting<VoiceModelProviderSetting>(
            key = KEY_VOICE_MODEL_PROVIDERS,
            defaultValue = DEFAULT_VOICE_MODEL_PROVIDERS,
            label = "voice model providers",
            normalize = { it.normalizedCustomVoiceModelProviders(validCredentialIds) },
        )

        // Include the local provider first
        return getLocalVoiceModelProviders() + customProviders
    }

    fun peekVoiceModelProviders(validCredentialIds: Set<String>): List<VoiceModelProviderSetting> {
        val customProviders = peekNormalizedJsonListSetting<VoiceModelProviderSetting>(
            key = KEY_VOICE_MODEL_PROVIDERS,
            defaultValue = DEFAULT_VOICE_MODEL_PROVIDERS,
            label = "voice model providers",
            normalize = { it.normalizedCustomVoiceModelProviders(validCredentialIds) },
        )

        return getLocalVoiceModelProviders() + customProviders
    }

    /**
     * Reads the stored ASR selection exactly as persisted, without normalizing it against the
     * visible provider list.
     *
     * Used by the Whisper fallback path while the local ASR model may still be hidden.
     */
    fun peekSelectedVoiceModelProviderIdExact(): String =
        settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID).trim()

    fun setSelectedVoiceModelProviderId(
        value: String,
        availableProviders: List<VoiceModelProviderSetting>,
    ): Boolean =
        setSelectedProviderIdInternal(
            key = KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
            value = value,
            availableProviders = availableProviders,
        )

    /**
     * Stores the selected ASR provider exactly as provided.
     *
     * Used for the Whisper fallback path, which may need to persist a provider ID before the local
     * Whisper model becomes visible in the provider list.
     */
    fun setSelectedVoiceModelProviderIdExact(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
            settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID),
            value.trim(),
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID
        )

    private fun readSelectedProviderId(
        key: String,
        defaultValue: String,
        availableProviders: List<SelectableProviderSetting>,
    ): String {
        val raw = settingsStore.getString(key, defaultValue)
        val normalized = normalizeSelectedProviderId(raw, defaultValue, availableProviders)
        return if (raw != normalized) {
            settingsStore.setString(key, normalized)
            normalized
        } else {
            raw
        }
    }

    private fun peekSelectedProviderId(
        key: String,
        defaultValue: String,
        availableProviders: List<SelectableProviderSetting>,
    ): String {
        val raw = settingsStore.getString(key, defaultValue)
        return normalizeSelectedProviderId(raw, defaultValue, availableProviders)
    }

    private fun normalizeSelectedProviderId(
        value: String,
        defaultValue: String,
        availableProviders: List<SelectableProviderSetting>,
    ): String {
        val trimmed = value.trim()
        val sortedAvailableProviders = availableProviders.sortedBy { it.name.lowercase() }
        val availableProviderIds = sortedAvailableProviders.map { it.id }
        return when {
            trimmed.isBlank() -> sortedAvailableProviders.firstOrNull()?.id ?: defaultValue
            trimmed in availableProviderIds -> trimmed
            else -> sortedAvailableProviders.firstOrNull()?.id ?: defaultValue
        }
    }

    fun peekSelectedVoiceModelProviderId(availableProviders: List<VoiceModelProviderSetting>): String =
        peekSelectedProviderId(
            key = KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
            availableProviders = availableProviders,
        )

    fun peekSelectedTextModelProviderId(availableProviders: List<TextModelProviderSetting>): String =
        peekSelectedProviderId(
            key = KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            availableProviders = availableProviders,
        )

    fun loadSelectedTextModelProviderId(availableProviders: List<SelectableProviderSetting>): String =
        readSelectedProviderId(
            key = KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            availableProviders = availableProviders,
        )

    fun setSelectedTextModelProviderId(
        value: String,
        availableProviders: List<TextModelProviderSetting>,
    ): Boolean =
        setSelectedProviderIdInternal(
            key = KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            value = value,
            availableProviders = availableProviders,
        )

    private fun setSelectedProviderIdInternal(
        key: String,
        defaultValue: String,
        value: String,
        availableProviders: List<SelectableProviderSetting>,
    ): Boolean =
        setStringSettingIfChanged(
            key,
            settingsStore.getString(key, defaultValue),
            normalizeSelectedProviderId(
                value = value,
                defaultValue = defaultValue,
                availableProviders = availableProviders,
            ),
            key
        )

    /*
     * Text Models page
     */

    fun loadTextProcessingEnabled(): Boolean =
        readBooleanSetting(KEY_TEXT_PROCESSING_ENABLED, DEFAULT_TEXT_PROCESSING_ENABLED)

    fun peekTextProcessingEnabled(): Boolean =
        peekBooleanSetting(KEY_TEXT_PROCESSING_ENABLED, DEFAULT_TEXT_PROCESSING_ENABLED)

    fun setTextProcessingEnabled(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_TEXT_PROCESSING_ENABLED,
            settingsStore.getString(KEY_TEXT_PROCESSING_ENABLED, DEFAULT_TEXT_PROCESSING_ENABLED.toString()),
            value,
            KEY_TEXT_PROCESSING_ENABLED
        )

    fun loadTextModelProviders(validCredentialIds: Set<String>): List<TextModelProviderSetting> {
        return readNormalizedJsonListSetting<TextModelProviderSetting>(
            key = KEY_TEXT_MODEL_PROVIDERS,
            defaultValue = DEFAULT_TEXT_MODEL_PROVIDERS,
            label = "text model providers",
            normalize = { it.normalizedTextModelProviders(validCredentialIds) },
        )
    }

    fun peekTextModelProviders(validCredentialIds: Set<String>): List<TextModelProviderSetting> {
        return peekNormalizedJsonListSetting(
            key = KEY_TEXT_MODEL_PROVIDERS,
            defaultValue = DEFAULT_TEXT_MODEL_PROVIDERS,
            label = "text model providers",
            normalize = { it.normalizedTextModelProviders(validCredentialIds) },
        )
    }

    /*
     * Personalization page
     */

    fun loadCustomContext(): String =
        readCustomContext()

    fun peekCustomContext(): String =
        validatedSetting(
            key = KEY_CUSTOM_CONTEXT,
            defaultValue = DEFAULT_CUSTOM_CONTEXT,
            heal = false,
            parse = ::normalizeCustomContext,
        )

    fun setCustomContext(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_CUSTOM_CONTEXT,
            settingsStore.getString(KEY_CUSTOM_CONTEXT, DEFAULT_CUSTOM_CONTEXT),
            normalizeCustomContext(value),
            KEY_CUSTOM_CONTEXT
        )

    fun loadCustomVocabulary(): List<String> =
        readCustomVocabulary()

    fun peekCustomVocabulary(): List<String> =
        peekNormalizedJsonListSetting(
            key = KEY_CUSTOM_VOCABULARY,
            defaultValue = DEFAULT_CUSTOM_VOCABULARY,
            label = "custom vocabulary",
            normalize = List<String>::normalizedCustomVocabulary,
        )

    fun setCustomVocabulary(value: List<String>): Boolean =
        setStringArraySettingIfChanged(
            KEY_CUSTOM_VOCABULARY,
            settingsStore.getString(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY.joinToString("|||")),
            value.normalizedCustomVocabulary(),
            KEY_CUSTOM_VOCABULARY
        )

    /*
     * Advanced page
     */

    fun loadSanitizeSpecialChars(): Boolean =
        readBooleanSetting(KEY_SANITIZE_SPECIAL_CHARS, DEFAULT_SANITIZE_SPECIAL_CHARS)

    fun peekSanitizeSpecialChars(): Boolean =
        peekBooleanSetting(KEY_SANITIZE_SPECIAL_CHARS, DEFAULT_SANITIZE_SPECIAL_CHARS)

    fun setSanitizeSpecialChars(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_SANITIZE_SPECIAL_CHARS,
            settingsStore.getString(KEY_SANITIZE_SPECIAL_CHARS, DEFAULT_SANITIZE_SPECIAL_CHARS.toString()),
            value,
            KEY_SANITIZE_SPECIAL_CHARS
        )

    fun loadPostHideDelayMs(): Int =
        readIntSetting(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS)

    fun peekPostHideDelayMs(): Int =
        peekIntSetting(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS)

    fun setPostHideDelayMs(value: Int): Boolean =
        setSettingIfChanged(
            KEY_POST_HIDE_DELAY_MS,
            settingsStore.getString(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS.toString()),
            value.toString(),
            KEY_POST_HIDE_DELAY_MS,
        ) {
            settingsStore.setInt(KEY_POST_HIDE_DELAY_MS, value)
        )

    fun loadTypingDelayMs(): Int =
        readIntSetting(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS)

    fun peekTypingDelayMs(): Int =
        peekIntSetting(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS)

    fun setTypingDelayMs(value: Int): Boolean =
        setSettingIfChanged(
            KEY_TYPING_DELAY_MS,
            settingsStore.getString(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS.toString()),
            value.toString(),
            KEY_TYPING_DELAY_MS,
        ) {
            settingsStore.setInt(KEY_TYPING_DELAY_MS, value)
        )

    private inline fun setSettingIfChanged(
        key: String,
        currentValue: String,
        nextRawValue: String,
        emitChangeKey: String? = null,
        write: () -> Boolean,
    ): Boolean {
        if (currentValue == nextRawValue) {
            return true
        }
        return write().also { success ->
            if (success && emitChangeKey != null) {
                _settingsChanged.tryEmit(emitChangeKey)
            }
        }
    }

    private inline fun <T> validatedSetting(
        key: String,
        defaultValue: T,
        parse: (String) -> T?,
        normalize: (T) -> T = { it },
        heal: Boolean,
        write: ((T) -> Boolean)? = null,
    ): T {
        val raw = settingsStore.getString(key, defaultValue.toString())
        val parsed = parse(raw)
        return if (parsed != null) {
            val normalized = normalize(parsed)
            if (heal && raw != normalized.toString()) {
                write?.invoke(normalized)
            }
            normalized
        } else {
            if (heal) {
                write?.invoke(defaultValue)
            }
            defaultValue
        }
    }

    private fun setStringSettingIfChanged(
        key: String,
        currentRawValue: String,
        value: String,
        emitChangeKey: String? = null,
    ): Boolean =
        setSettingIfChanged(
            key = key,
            currentValue = currentRawValue,
            nextRawValue = value,
            emitChangeKey = emitChangeKey,
        ) {
            settingsStore.setString(key, value)
        }

    private fun setBooleanSettingIfChanged(
        key: String,
        currentRawValue: String,
        value: Boolean,
        emitChangeKey: String? = null,
    ): Boolean =
        setSettingIfChanged(
            key = key,
            currentValue = currentRawValue,
            nextRawValue = value.toString(),
            emitChangeKey = emitChangeKey,
        ) {
            settingsStore.setBoolean(key, value)
        }

    private fun setIntSettingIfChanged(
        key: String,
        currentRawValue: String,
        value: Int,
        emitChangeKey: String? = null,
    ): Boolean =
        setSettingIfChanged(
            key = key,
            currentValue = currentRawValue,
            nextRawValue = value.toString(),
            emitChangeKey = emitChangeKey,
        ) {
            settingsStore.setInt(key, value)
        }

    private fun setStringArraySettingIfChanged(
        key: String,
        currentRawValue: String,
        value: List<String>,
        emitChangeKey: String? = null,
    ): Boolean =
        setSettingIfChanged(
            key = key,
            currentValue = currentRawValue,
            nextRawValue = value.joinToString("|||"),
            emitChangeKey = emitChangeKey,
        ) {
            settingsStore.setStringArray(key, value)
        }

    private inline fun <reified T> readNormalizedJsonListSetting(
        key: String,
        defaultValue: String,
        label: String,
        normalize: (List<T>) -> List<T>,
    ): List<T> =
        normalizedJsonListSetting(
            key = key,
            defaultValue = defaultValue,
            label = label,
            normalize = normalize,
            heal = true,
        )

    private inline fun <reified T> peekNormalizedJsonListSetting(
        key: String,
        defaultValue: String,
        label: String,
        normalize: (List<T>) -> List<T>,
    ): List<T> =
        normalizedJsonListSetting(
            key = key,
            defaultValue = defaultValue,
            label = label,
            normalize = normalize,
            heal = false,
        )

    private inline fun <reified T> normalizedJsonListSetting(
        key: String,
        defaultValue: String,
        label: String,
        normalize: (List<T>) -> List<T>,
        heal: Boolean,
    ): List<T> {
        val json = settingsStore.getString(key, defaultValue)
        if (json.isEmpty() || json == defaultValue) {
            return emptyList()
        }

        return runCatching {
            Json.decodeFromString<List<T>>(json)
        }.map { parsed ->
            val normalized = normalize(parsed)
            if (heal && parsed != normalized) {
                settingsStore.setString(key, Json.encodeToString(normalized))
            }
            normalized
        }.getOrElse { error ->
            logger.error("Failed to decode {} from JSON", label, error)
            if (heal) {
                settingsStore.setString(key, Json.encodeToString(emptyList<T>()))
            }
            emptyList()
        }
    }

    private fun readBooleanSetting(key: String, defaultValue: Boolean): Boolean {
        return validatedSetting(
            key = key,
            defaultValue = defaultValue,
            heal = true,
            parse = { raw ->
                when {
                    raw.equals("true", ignoreCase = true) -> true
                    raw.equals("false", ignoreCase = true) -> false
                    else -> null
                }
            },
            write = { value -> settingsStore.setBoolean(key, value) },
        )
    }

    private fun peekBooleanSetting(key: String, defaultValue: Boolean): Boolean {
        return validatedSetting(
            key = key,
            defaultValue = defaultValue,
            heal = false,
            parse = { raw ->
                when {
                    raw.equals("true", ignoreCase = true) -> true
                    raw.equals("false", ignoreCase = true) -> false
                    else -> null
                }
            },
        )
    }

    private fun readTextOutputMethod(): String {
        return validatedSetting(
            key = KEY_TEXT_OUTPUT_METHOD,
            defaultValue = DEFAULT_TEXT_OUTPUT_METHOD,
            heal = true,
            parse = { raw ->
                val normalized = raw.trim().lowercase()
                if (normalized == TEXT_OUTPUT_METHOD_PORTAL || normalized == TEXT_OUTPUT_METHOD_CLIPBOARD) {
                    normalized
                } else {
                    null
                }
            },
            write = { value -> settingsStore.setString(KEY_TEXT_OUTPUT_METHOD, value) },
        )
    }

    private fun readPortalsRestoreToken(): String {
        return validatedSetting(
            key = KEY_PORTALS_RESTORE_TOKEN,
            defaultValue = DEFAULT_PORTALS_RESTORE_TOKEN,
            heal = true,
            parse = ::normalizePortalsRestoreToken,
            write = { value -> settingsStore.setString(KEY_PORTALS_RESTORE_TOKEN, value) },
        )
    }

    private fun normalizePortalsRestoreToken(value: String): String = value.trim()

    private fun readCustomContext(): String {
        return validatedSetting(
            key = KEY_CUSTOM_CONTEXT,
            defaultValue = DEFAULT_CUSTOM_CONTEXT,
            heal = true,
            parse = ::normalizeCustomContext,
            write = { value -> settingsStore.setString(KEY_CUSTOM_CONTEXT, value) },
        )
    }

    private fun normalizeCustomContext(value: String): String =
        if (value.length <= MAX_CUSTOM_CONTEXT_CHARS) value else value.take(MAX_CUSTOM_CONTEXT_CHARS)

    private fun List<CredentialSetting>.normalizedCredentials(): List<CredentialSetting> =
        map { credential ->
            credential.copy(
                id = credential.id.trim(),
                name = credential.name.trim().take(MAX_CREDENTIAL_NAME_LENGTH),
                value = credential.value.trim().take(MAX_CREDENTIAL_VALUE_LENGTH),
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.value.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_CREDENTIALS)

    fun setVoiceModelProviders(
        value: List<VoiceModelProviderSetting>,
        validCredentialIds: Set<String>,
    ): Boolean =
        setVoiceModelProviders(value, validCredentialIds, peekVoiceModelProviders(validCredentialIds))

    fun setVoiceModelProviders(
        value: List<VoiceModelProviderSetting>,
        validCredentialIds: Set<String>,
        availableProviders: List<VoiceModelProviderSetting>,
    ): Boolean =
        setVoiceModelProvidersInternal(value, validCredentialIds, availableProviders)

    private fun setVoiceModelProvidersInternal(
        value: List<VoiceModelProviderSetting>,
        validCredentialIds: Set<String>,
        availableProviders: List<VoiceModelProviderSetting>,
    ): Boolean {
        val json = Json.encodeToString(value.normalizedCustomVoiceModelProviders(validCredentialIds))
        return persistJsonSettingAndRunPostWrite(
            key = KEY_VOICE_MODEL_PROVIDERS,
            defaultValue = DEFAULT_VOICE_MODEL_PROVIDERS,
            value = json,
        ) {
            normalizeSelectedVoiceModelProviderIdForCurrentProviders(availableProviders)
        }
    }

    fun setTextModelProviders(
        value: List<TextModelProviderSetting>,
        validCredentialIds: Set<String>,
    ): Boolean =
        setTextModelProviders(value, validCredentialIds, peekTextModelProviders(validCredentialIds))

    fun setTextModelProviders(
        value: List<TextModelProviderSetting>,
        validCredentialIds: Set<String>,
        availableProviders: List<TextModelProviderSetting>,
    ): Boolean =
        setTextModelProvidersInternal(value, validCredentialIds, availableProviders)

    private fun setTextModelProvidersInternal(
        value: List<TextModelProviderSetting>,
        validCredentialIds: Set<String>,
        availableProviders: List<TextModelProviderSetting>,
    ): Boolean {
        val json = Json.encodeToString(value.normalizedTextModelProviders(validCredentialIds))
        return persistJsonSettingAndRunPostWrite(
            key = KEY_TEXT_MODEL_PROVIDERS,
            defaultValue = DEFAULT_TEXT_MODEL_PROVIDERS,
            value = json,
        ) {
            normalizeSelectedTextModelProviderIdForCurrentProviders(availableProviders)
        }
    }

    private inline fun persistJsonSettingAndRunPostWrite(
        key: String,
        defaultValue: String,
        value: String,
        postWrite: () -> Boolean,
    ): Boolean {
        val providersSaved = setStringSettingIfChanged(
            key,
            settingsStore.getString(key, defaultValue),
            value,
            key
        )
        if (!providersSaved) {
            return false
        }
        return postWrite()
    }

    private fun setCredentialsInternal(
        normalized: List<CredentialSetting>,
        validCredentialIds: Set<String>,
        availableVoiceProviders: List<VoiceModelProviderSetting>,
        availableTextProviders: List<TextModelProviderSetting>,
    ): Boolean {
        val json = Json.encodeToString(normalized)
        return persistJsonSettingAndRunPostWrite(
            KEY_CREDENTIALS,
            DEFAULT_CREDENTIALS,
            json,
        ) {
            normalizeStoredProviderCredentialRefs(
                validCredentialIds = validCredentialIds,
                availableVoiceProviders = availableVoiceProviders,
                availableTextProviders = availableTextProviders,
            )
        }
    }

    private fun normalizeStoredProviderCredentialRefs(
        validCredentialIds: Set<String>,
        availableVoiceProviders: List<VoiceModelProviderSetting>,
        availableTextProviders: List<TextModelProviderSetting>,
    ): Boolean {
        val voiceSaved = setVoiceModelProvidersInternal(availableVoiceProviders, validCredentialIds, availableVoiceProviders)
        val textSaved = setTextModelProvidersInternal(availableTextProviders, validCredentialIds, availableTextProviders)
        return voiceSaved && textSaved
    }

    private fun normalizeSelectedVoiceModelProviderIdForCurrentProviders(
        availableProviders: List<SelectableProviderSetting>,
    ): Boolean {
        val exactSelectedProviderId = peekSelectedVoiceModelProviderIdExact()
        return if (shouldPreserveExactWhisperSelection(exactSelectedProviderId, availableProviders)) {
            setSelectedVoiceModelProviderIdExact(exactSelectedProviderId)
        } else {
            val normalizedSelectedProviderId = peekSelectedProviderId(
                key = KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
                defaultValue = DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
                availableProviders = availableProviders,
            )
            setSelectedProviderIdInternal(
                key = KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
                defaultValue = DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID,
                value = normalizedSelectedProviderId,
                availableProviders = availableProviders,
            )
        }
    }

    private fun normalizeSelectedTextModelProviderIdForCurrentProviders(
        availableProviders: List<SelectableProviderSetting>,
    ): Boolean {
        val normalizedSelectedProviderId = peekSelectedProviderId(
            key = KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            availableProviders = availableProviders,
        )
        return setSelectedProviderIdInternal(
            key = KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
            defaultValue = DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID,
            value = normalizedSelectedProviderId,
            availableProviders = availableProviders,
        )
    }

    private fun List<VoiceModelProviderSetting>.normalizedCustomVoiceModelProviders(
        validCredentialIds: Set<String>,
    ): List<VoiceModelProviderSetting> =
        map { provider ->
            provider.copy(
                id = provider.id.trim(),
                name = provider.name.trim().take(MAX_PROVIDER_CONFIG_NAME_LENGTH),
                modelId = provider.modelId.trim(),
                credentialId = provider.credentialId?.trim()?.takeIf { it.isNotBlank() && it in validCredentialIds },
                baseUrl = provider.baseUrl?.trim()?.takeIf { it.isNotBlank() },
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.modelId.isNotBlank() && it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
            .distinctBy { it.id }
            .take(MAX_VOICE_MODEL_PROVIDERS)

    private fun List<TextModelProviderSetting>.normalizedTextModelProviders(
        validCredentialIds: Set<String>,
    ): List<TextModelProviderSetting> =
        map { provider ->
            provider.copy(
                id = provider.id.trim(),
                name = provider.name.trim().take(MAX_PROVIDER_CONFIG_NAME_LENGTH),
                modelId = provider.modelId.trim(),
                credentialId = provider.credentialId?.trim()?.takeIf { it.isNotBlank() && it in validCredentialIds },
                baseUrl = provider.baseUrl?.trim()?.takeIf { it.isNotBlank() },
            )
        }.filter { it.id.isNotBlank() && it.name.isNotBlank() && it.modelId.isNotBlank() }
            .distinctBy { it.id }
            .take(MAX_TEXT_MODEL_PROVIDERS)

    private fun readCustomVocabulary(): List<String> {
        val raw = settingsStore.getStringArray(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY)
        val normalized = raw.normalizedCustomVocabulary()
        return if (raw != normalized) {
            settingsStore.setStringArray(KEY_CUSTOM_VOCABULARY, normalized)
            normalized
        } else {
            raw
        }
    }

    private fun readLanguageSetting(key: String, defaultValue: String): String {
        return validatedSetting(
            key = key,
            defaultValue = defaultValue,
            heal = true,
            parse = { raw -> languageFromIso2(raw.trim().lowercase())?.iso2 },
            write = { value -> settingsStore.setString(key, value) },
        )
    }

    private fun peekLanguageSetting(key: String, defaultValue: String): String {
        return validatedSetting(
            key = key,
            defaultValue = defaultValue,
            heal = false,
            parse = { raw -> languageFromIso2(raw.trim().lowercase())?.iso2 },
        )
    }

    private fun readIntSetting(
        key: String,
        defaultValue: Int,
        normalize: (Int) -> Int = { it },
    ): Int {
        return validatedSetting(
            key = key,
            defaultValue = defaultValue,
            heal = true,
            parse = String::toIntOrNull,
            normalize = normalize,
            write = { value -> settingsStore.setInt(key, value) },
        )
    }

    private fun peekIntSetting(
        key: String,
        defaultValue: Int,
        normalize: (Int) -> Int = { it },
    ): Int {
        return validatedSetting(
            key = key,
            defaultValue = defaultValue,
            heal = false,
            parse = String::toIntOrNull,
            normalize = normalize,
        )
    }

    private fun List<String>.normalizedCustomVocabulary(): List<String> =
        map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_VOCABULARY_WORDS)

    private data class LegacyAlarmSchedulerStateLoad(
        val state: AlarmSchedulerState,
        val dirty: Boolean,
    )

    private data class LegacyAlarmTriggeredDatesLoad(
        val value: Map<String, LocalDate>,
        val dirty: Boolean,
    )

    private data class LegacyAlarmCheckAtLoad(
        val value: LocalDateTime?,
        val dirty: Boolean,
    )
}
