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
import com.zugaldia.speedofsound.core.plugins.recorder.RecorderOptions
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

    /*
     * Utility functions to build options objects
     */

    fun getRecorderOptions(): RecorderOptions =
        RecorderOptions(computeVolumeLevel = true)

    /**
     * Resolves a VoiceModelProviderSetting into the appropriate AsrPluginOptions
     */
    fun resolveVoiceProviderOptions(providerSetting: VoiceModelProviderSetting): AsrPluginOptions {
        val apiKey = providerSetting.credentialId?.let { credId -> getCredentials().find { it.id == credId }?.value }
        val language = languageFromIso2(getDefaultLanguage()) ?: DEFAULT_LANGUAGE
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
        val apiKey = providerSetting.credentialId?.let { credId -> getCredentials().find { it.id == credId }?.value }
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

    fun getDirectorOptions(): DirectorOptions = DirectorOptions(
        enableTextProcessing = getTextProcessingEnabled(),
        language = languageFromIso2(getDefaultLanguage()) ?: DEFAULT_LANGUAGE,
        customVocabulary = getCustomVocabulary(),
        customContext = getCustomContext()
    )

    /*
     * Not exposed to the UI
     */

    fun getWelcomeScreenShown(): Boolean =
        settingsStore.getBoolean(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN)

    fun setWelcomeScreenShown(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_WELCOME_SCREEN_SHOWN,
            settingsStore.getString(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN.toString()),
            value
        )

    fun getPortalsRestoreToken(): String =
        settingsStore.getString(KEY_PORTALS_RESTORE_TOKEN, DEFAULT_PORTALS_RESTORE_TOKEN)

    fun setPortalsRestoreToken(value: String): Boolean =
        setStringSettingIfChanged(KEY_PORTALS_RESTORE_TOKEN, getPortalsRestoreToken(), value)

    fun getShortcutConfigured(): Boolean =
        settingsStore.getBoolean(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED)

    fun setShortcutConfigured(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_SHORTCUT_CONFIGURED,
            settingsStore.getString(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED.toString()),
            value
        )

    /*
     * General page
     */

    fun getDefaultLanguage(): String =
        settingsStore.getString(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2)

    fun setDefaultLanguage(value: String): Boolean =
        setStringSettingIfChanged(KEY_DEFAULT_LANGUAGE, getDefaultLanguage(), value, KEY_DEFAULT_LANGUAGE)

    fun getSecondaryLanguage(): String =
        settingsStore.getString(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2)

    fun setSecondaryLanguage(value: String): Boolean =
        setStringSettingIfChanged(KEY_SECONDARY_LANGUAGE, getSecondaryLanguage(), value, KEY_SECONDARY_LANGUAGE)

    fun getBackgroundRecording(): Boolean =
        settingsStore.getBoolean(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING)

    fun setBackgroundRecording(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_BACKGROUND_RECORDING,
            settingsStore.getString(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING.toString()),
            value,
            KEY_BACKGROUND_RECORDING
        )

    fun getAppendSpace(): Boolean =
        settingsStore.getBoolean(KEY_APPEND_SPACE, DEFAULT_APPEND_SPACE)

    fun setAppendSpace(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_APPEND_SPACE,
            settingsStore.getString(KEY_APPEND_SPACE, DEFAULT_APPEND_SPACE.toString()),
            value,
            KEY_APPEND_SPACE
        )

    fun getHideInsteadOfMinimize(): Boolean =
        settingsStore.getBoolean(KEY_HIDE_INSTEAD_OF_MINIMIZE, DEFAULT_HIDE_INSTEAD_OF_MINIMIZE)

    fun setHideInsteadOfMinimize(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_HIDE_INSTEAD_OF_MINIMIZE,
            settingsStore.getString(KEY_HIDE_INSTEAD_OF_MINIMIZE, DEFAULT_HIDE_INSTEAD_OF_MINIMIZE.toString()),
            value,
            KEY_HIDE_INSTEAD_OF_MINIMIZE
        )

    fun getStayHiddenOnActivation(): Boolean =
        settingsStore.getBoolean(KEY_STAY_HIDDEN_ON_ACTIVATION, DEFAULT_STAY_HIDDEN_ON_ACTIVATION)

    fun setStayHiddenOnActivation(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_STAY_HIDDEN_ON_ACTIVATION,
            settingsStore.getString(KEY_STAY_HIDDEN_ON_ACTIVATION, DEFAULT_STAY_HIDDEN_ON_ACTIVATION.toString()),
            value,
            KEY_STAY_HIDDEN_ON_ACTIVATION
        )

    fun getTextOutputMethod(): String =
        settingsStore.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD)

    fun setTextOutputMethod(value: String): Boolean =
        setStringSettingIfChanged(KEY_TEXT_OUTPUT_METHOD, getTextOutputMethod(), value, KEY_TEXT_OUTPUT_METHOD)

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
        val currentLegacy = loadLegacyAlarmSchedulerState()
        val rawJson = settingsStore.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        val alreadyStored = when {
            currentCombined != null -> currentCombined == normalized && currentLegacy.state == normalized
            rawJson.isBlank() || rawJson == DEFAULT_ALARM_SCHEDULER_STATE ->
                normalized == AlarmSchedulerState() && currentLegacy.state == AlarmSchedulerState()
            else -> false
        }
        if (alreadyStored && !forceLegacyWrite) {
            return true
        }
        val json = Json.encodeToString(normalized)
        val combinedChanged = currentCombined != normalized
        val legacyChanged = forceLegacyWrite || currentLegacy.state != normalized
        val combinedSaved = if (combinedChanged) {
            settingsStore.setString(KEY_ALARM_SCHEDULER_STATE, json).also { success ->
                if (success && emitChange) {
                    _settingsChanged.tryEmit(KEY_ALARM_SCHEDULER_STATE)
                }
            }
        } else {
            true
        }
        val legacySaved = if (legacyChanged) {
            persistLegacyAlarmSchedulerState(
                state = normalized,
                currentState = currentLegacy.state,
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
            loadAlarmSchedulerState().copy(
                lastTriggeredDates = value.mapValues { (_, date) -> date.toString() }
            )
        )

    fun setAlarmLastTriggeredDate(alarmId: String, date: LocalDate): Boolean =
        loadAlarmSchedulerState().let { currentState ->
            setAlarmSchedulerState(
                currentState.copy(
                    lastTriggeredDates = currentState.lastTriggeredDates.toMutableMap().apply {
                        this[alarmId] = date.toString()
                    }
                )
            )
        }

    fun setAlarmLastCheckAt(value: LocalDateTime): Boolean =
        setAlarmSchedulerState(
            loadAlarmSchedulerState().copy(lastCheckAt = value.toString())
        )

    fun getMaxAlarms(): Int =
        settingsStore.getInt(KEY_MAX_ALARMS, DEFAULT_MAX_ALARMS)
            .coerceIn(MIN_MAX_ALARMS, MAX_MAX_ALARMS)

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

        return sortedUniqueAlarms.take(getMaxAlarms())
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

    fun getCredentials(): List<CredentialSetting> {
        return readJsonListSetting(
            key = KEY_CREDENTIALS,
            defaultValue = DEFAULT_CREDENTIALS,
            label = "credentials",
            heal = true,
        )
    }

    fun setCredentials(value: List<CredentialSetting>): Boolean {
        val json = Json.encodeToString(value)
        return setStringSettingIfChanged(KEY_CREDENTIALS, settingsStore.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS), json, KEY_CREDENTIALS)
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

    fun getVoiceModelProviders(): List<VoiceModelProviderSetting> {
        val customProviders = readJsonListSetting(
            key = KEY_VOICE_MODEL_PROVIDERS,
            defaultValue = DEFAULT_VOICE_MODEL_PROVIDERS,
            label = "voice model providers",
            heal = true,
        )

        // Include the local provider first
        return getLocalVoiceModelProviders() + customProviders
    }

    fun setVoiceModelProviders(value: List<VoiceModelProviderSetting>): Boolean {
        // Filter out the local providers before saving
        val customProviders = value.filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
        val json = Json.encodeToString(customProviders)
        return setStringSettingIfChanged(
            KEY_VOICE_MODEL_PROVIDERS,
            settingsStore.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS),
            json,
            KEY_VOICE_MODEL_PROVIDERS
        )
    }

    fun getSelectedVoiceModelProviderId(): String =
        settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)

    fun setSelectedVoiceModelProviderId(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID,
            getSelectedVoiceModelProviderId(),
            value,
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID
        )

    /*
     * Text Models page
     */

    fun getTextProcessingEnabled(): Boolean =
        settingsStore.getBoolean(KEY_TEXT_PROCESSING_ENABLED, DEFAULT_TEXT_PROCESSING_ENABLED)

    fun setTextProcessingEnabled(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_TEXT_PROCESSING_ENABLED,
            settingsStore.getString(KEY_TEXT_PROCESSING_ENABLED, DEFAULT_TEXT_PROCESSING_ENABLED.toString()),
            value,
            KEY_TEXT_PROCESSING_ENABLED
        )

    fun getTextModelProviders(): List<TextModelProviderSetting> {
        return readJsonListSetting(
            key = KEY_TEXT_MODEL_PROVIDERS,
            defaultValue = DEFAULT_TEXT_MODEL_PROVIDERS,
            label = "text model providers",
            heal = true,
        )
    }

    fun setTextModelProviders(value: List<TextModelProviderSetting>): Boolean {
        val json = Json.encodeToString(value)
        return setStringSettingIfChanged(
            KEY_TEXT_MODEL_PROVIDERS,
            settingsStore.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS),
            json,
            KEY_TEXT_MODEL_PROVIDERS
        )
    }

    fun getSelectedTextModelProviderId(): String =
        settingsStore.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)

    fun setSelectedTextModelProviderId(value: String): Boolean =
        setStringSettingIfChanged(
            KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
            getSelectedTextModelProviderId(),
            value,
            KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
        )

    /*
     * Personalization page
     */

    fun getCustomContext(): String =
        settingsStore.getString(KEY_CUSTOM_CONTEXT, DEFAULT_CUSTOM_CONTEXT)

    fun setCustomContext(value: String): Boolean =
        setStringSettingIfChanged(KEY_CUSTOM_CONTEXT, getCustomContext(), value, KEY_CUSTOM_CONTEXT)

    fun getCustomVocabulary(): List<String> =
        settingsStore.getStringArray(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY)

    fun setCustomVocabulary(value: List<String>): Boolean =
        setStringArraySettingIfChanged(
            KEY_CUSTOM_VOCABULARY,
            settingsStore.getString(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY.joinToString("|||")),
            value,
            KEY_CUSTOM_VOCABULARY
        )

    /*
     * Advanced page
     */

    fun getSanitizeSpecialChars(): Boolean =
        settingsStore.getBoolean(KEY_SANITIZE_SPECIAL_CHARS, DEFAULT_SANITIZE_SPECIAL_CHARS)

    fun setSanitizeSpecialChars(value: Boolean): Boolean =
        setBooleanSettingIfChanged(
            KEY_SANITIZE_SPECIAL_CHARS,
            settingsStore.getString(KEY_SANITIZE_SPECIAL_CHARS, DEFAULT_SANITIZE_SPECIAL_CHARS.toString()),
            value,
            KEY_SANITIZE_SPECIAL_CHARS
        )

    fun getPostHideDelayMs(): Int =
        settingsStore.getInt(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS)

    fun setPostHideDelayMs(value: Int): Boolean =
        setIntSettingIfChanged(
            KEY_POST_HIDE_DELAY_MS,
            settingsStore.getString(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS.toString()),
            value,
            KEY_POST_HIDE_DELAY_MS
        )

    fun getTypingDelayMs(): Int =
        settingsStore.getInt(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS)

    fun setTypingDelayMs(value: Int): Boolean =
        setIntSettingIfChanged(
            KEY_TYPING_DELAY_MS,
            settingsStore.getString(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS.toString()),
            value,
            KEY_TYPING_DELAY_MS
        )

    private fun setStringSettingIfChanged(
        key: String,
        currentValue: String,
        value: String,
        emitChangeKey: String? = null,
    ): Boolean {
        if (currentValue == value) {
            return true
        }
        return settingsStore.setString(key, value).also { success ->
            if (success && emitChangeKey != null) {
                _settingsChanged.tryEmit(emitChangeKey)
            }
        }
    }

    private fun setBooleanSettingIfChanged(
        key: String,
        currentRawValue: String,
        value: Boolean,
        emitChangeKey: String? = null,
    ): Boolean {
        val nextRawValue = value.toString()
        if (currentRawValue == nextRawValue) {
            return true
        }
        return settingsStore.setBoolean(key, value).also { success ->
            if (success && emitChangeKey != null) {
                _settingsChanged.tryEmit(emitChangeKey)
            }
        }
    }

    private fun setIntSettingIfChanged(
        key: String,
        currentRawValue: String,
        value: Int,
        emitChangeKey: String? = null,
    ): Boolean {
        val nextRawValue = value.toString()
        if (currentRawValue == nextRawValue) {
            return true
        }
        return settingsStore.setInt(key, value).also { success ->
            if (success && emitChangeKey != null) {
                _settingsChanged.tryEmit(emitChangeKey)
            }
        }
    }

    private fun setStringArraySettingIfChanged(
        key: String,
        currentRawValue: String,
        value: List<String>,
        emitChangeKey: String? = null,
    ): Boolean {
        val nextRawValue = value.joinToString("|||")
        if (currentRawValue == nextRawValue) {
            return true
        }
        return settingsStore.setStringArray(key, value).also { success ->
            if (success && emitChangeKey != null) {
                _settingsChanged.tryEmit(emitChangeKey)
            }
        }
    }

    private inline fun <reified T> readJsonListSetting(
        key: String,
        defaultValue: String,
        label: String,
        heal: Boolean,
    ): List<T> {
        val json = settingsStore.getString(key, defaultValue)
        if (json.isEmpty() || json == defaultValue) {
            return emptyList()
        }

        return runCatching {
            Json.decodeFromString<List<T>>(json)
        }.getOrElse { error ->
            logger.error("Failed to decode {} from JSON", label, error)
            if (heal) {
                settingsStore.setString(key, Json.encodeToString(emptyList<T>()))
            }
            emptyList()
        }
    }

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
