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
        settingsStore.setBoolean(KEY_WELCOME_SCREEN_SHOWN, value)

    fun getPortalsRestoreToken(): String =
        settingsStore.getString(KEY_PORTALS_RESTORE_TOKEN, DEFAULT_PORTALS_RESTORE_TOKEN)

    fun setPortalsRestoreToken(value: String): Boolean =
        settingsStore.setString(KEY_PORTALS_RESTORE_TOKEN, value)

    fun getShortcutConfigured(): Boolean =
        settingsStore.getBoolean(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED)

    fun setShortcutConfigured(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_SHORTCUT_CONFIGURED, value)

    /*
     * General page
     */

    fun getDefaultLanguage(): String =
        settingsStore.getString(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2)

    fun setDefaultLanguage(value: String): Boolean =
        settingsStore.setString(KEY_DEFAULT_LANGUAGE, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_DEFAULT_LANGUAGE)
        }

    fun getSecondaryLanguage(): String =
        settingsStore.getString(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2)

    fun setSecondaryLanguage(value: String): Boolean =
        settingsStore.setString(KEY_SECONDARY_LANGUAGE, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_SECONDARY_LANGUAGE)
        }

    fun getBackgroundRecording(): Boolean =
        settingsStore.getBoolean(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING)

    fun setBackgroundRecording(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_BACKGROUND_RECORDING, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_BACKGROUND_RECORDING)
        }

    fun getAppendSpace(): Boolean =
        settingsStore.getBoolean(KEY_APPEND_SPACE, DEFAULT_APPEND_SPACE)

    fun setAppendSpace(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_APPEND_SPACE, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_APPEND_SPACE)
        }

    fun getHideInsteadOfMinimize(): Boolean =
        settingsStore.getBoolean(KEY_HIDE_INSTEAD_OF_MINIMIZE, DEFAULT_HIDE_INSTEAD_OF_MINIMIZE)

    fun setHideInsteadOfMinimize(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_HIDE_INSTEAD_OF_MINIMIZE, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_HIDE_INSTEAD_OF_MINIMIZE)
        }

    fun getStayHiddenOnActivation(): Boolean =
        settingsStore.getBoolean(KEY_STAY_HIDDEN_ON_ACTIVATION, DEFAULT_STAY_HIDDEN_ON_ACTIVATION)

    fun setStayHiddenOnActivation(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_STAY_HIDDEN_ON_ACTIVATION, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_STAY_HIDDEN_ON_ACTIVATION)
        }

    fun getTextOutputMethod(): String =
        settingsStore.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD)

    fun setTextOutputMethod(value: String): Boolean =
        settingsStore.setString(KEY_TEXT_OUTPUT_METHOD, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_TEXT_OUTPUT_METHOD)
        }

    /*
     * Alarms page
     */

    fun getAlarms(): List<AlarmSetting> {
        val json = settingsStore.getString(KEY_ALARMS, DEFAULT_ALARMS)
        return if (json.isEmpty() || json == DEFAULT_ALARMS) {
            emptyList()
        } else {
            val decoded = runCatching {
                Json.decodeFromString<List<AlarmSetting>>(json)
            }.getOrElse { error ->
                logger.error("Failed to decode alarms from JSON", error)
                emptyList()
            }
            val normalized = normalizeAlarms(decoded)
            val dropped = decoded.size - normalized.size
            if (dropped > 0) {
                logger.warn("Adjusted {} alarm(s) while loading settings", dropped)
            }
            normalized
        }
    }

    fun setAlarms(value: List<AlarmSetting>): Boolean {
        val normalizedAlarms = normalizeAlarms(value)
        val dropped = value.size - normalizedAlarms.size
        if (dropped > 0) {
            logger.warn("Adjusted {} alarm(s) while saving settings", dropped)
        }
        val json = Json.encodeToString(normalizedAlarms)
        return settingsStore.setString(KEY_ALARMS, json).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_ALARMS)
        }
    }

    fun getAlarmSchedulerState(): AlarmSchedulerState {
        readAlarmSchedulerState()?.let { return it }
        return loadLegacyAlarmSchedulerState()
    }

    fun setAlarmSchedulerState(value: AlarmSchedulerState, emitChange: Boolean = true): Boolean {
        val normalized = value.copy(
            lastCheckAt = value.lastCheckAt?.takeIf { it.isNotBlank() },
            lastTriggeredDates = value.lastTriggeredDates
                .mapKeys { (alarmId, _) -> alarmId.trim() }
                .mapValues { (_, dateValue) -> dateValue.trim() }
                .filterKeys { it.isNotBlank() }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
        )
        val json = Json.encodeToString(normalized)
        return settingsStore.setString(KEY_ALARM_SCHEDULER_STATE, json).also { success ->
            if (success) {
                if (emitChange) {
                    _settingsChanged.tryEmit(KEY_ALARM_SCHEDULER_STATE)
                }
                persistLegacyAlarmSchedulerState(normalized)
            }
        }
    }

    fun getAlarmLastTriggeredDates(): Map<String, LocalDate> =
        getAlarmSchedulerState().lastTriggeredDates.mapNotNull { (alarmId, dateValue) ->
            runCatching { LocalDate.parse(dateValue) }
                .getOrNull()
                ?.let { parsedDate -> alarmId to parsedDate }
        }.toMap()

    fun getAlarmLastCheckAt(): LocalDateTime? {
        val value = getAlarmSchedulerState().lastCheckAt ?: return null
        return runCatching { LocalDateTime.parse(value) }
            .getOrElse { error ->
                logger.error("Failed to decode alarm last check timestamp", error)
                null
            }
    }

    fun setAlarmLastTriggeredDates(value: Map<String, LocalDate>): Boolean =
        setAlarmSchedulerState(
            getAlarmSchedulerState().copy(
                lastTriggeredDates = value.mapValues { (_, date) -> date.toString() }
            )
        )

    fun setAlarmLastTriggeredDate(alarmId: String, date: LocalDate): Boolean =
        getAlarmSchedulerState().let { currentState ->
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
            getAlarmSchedulerState().copy(lastCheckAt = value.toString())
        )

    fun getMaxAlarms(): Int =
        settingsStore.getInt(KEY_MAX_ALARMS, DEFAULT_MAX_ALARMS)
            .coerceIn(MIN_MAX_ALARMS, MAX_MAX_ALARMS)

    fun setMaxAlarms(value: Int): Boolean =
        settingsStore.setInt(KEY_MAX_ALARMS, value.coerceIn(MIN_MAX_ALARMS, MAX_MAX_ALARMS)).also { success ->
            if (!success) return@also

            _settingsChanged.tryEmit(KEY_MAX_ALARMS)
            normalizeStoredAlarmsToCurrentLimit()
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

    private fun loadLegacyAlarmSchedulerState(): AlarmSchedulerState {
        val legacyTriggeredDates = readLegacyAlarmLastTriggeredDates().mapValues { (_, date) -> date.toString() }
        val legacyCheckAt = readLegacyAlarmLastCheckAt()?.toString()
        return AlarmSchedulerState(
            lastCheckAt = legacyCheckAt,
            lastTriggeredDates = legacyTriggeredDates,
        )
    }

    private fun readLegacyAlarmLastTriggeredDates(): Map<String, LocalDate> {
        val json = settingsStore.getString(KEY_ALARM_LAST_TRIGGERED_DATES, DEFAULT_ALARM_LAST_TRIGGERED_DATES)
        return if (json.isBlank() || json == DEFAULT_ALARM_LAST_TRIGGERED_DATES) {
            emptyMap()
        } else {
            runCatching {
                Json.decodeFromString<Map<String, String>>(json).mapNotNull { (alarmId, dateValue) ->
                    runCatching { LocalDate.parse(dateValue) }
                        .getOrNull()
                        ?.let { parsedDate -> alarmId to parsedDate }
                }.toMap()
            }.getOrElse { error ->
                logger.error("Failed to decode alarm trigger dates from JSON", error)
                emptyMap()
            }
        }
    }

    private fun readLegacyAlarmLastCheckAt(): LocalDateTime? {
        val value = settingsStore.getString(KEY_ALARM_LAST_CHECK_AT, DEFAULT_ALARM_LAST_CHECK_AT)
        return if (value.isBlank()) {
            null
        } else {
            runCatching { LocalDateTime.parse(value) }
                .getOrElse { error ->
                    logger.error("Failed to decode alarm last check timestamp", error)
                    null
                }
        }
    }

    private fun persistLegacyAlarmSchedulerState(state: AlarmSchedulerState) {
        val triggerDatesJson = Json.encodeToString(state.lastTriggeredDates)
        val triggeredDatesSaved = settingsStore.setString(KEY_ALARM_LAST_TRIGGERED_DATES, triggerDatesJson)
        if (!triggeredDatesSaved) {
            logger.warn("Failed to persist legacy alarm trigger dates.")
        }

        val checkAtSaved = settingsStore.setString(KEY_ALARM_LAST_CHECK_AT, state.lastCheckAt ?: DEFAULT_ALARM_LAST_CHECK_AT)
        if (!checkAtSaved) {
            logger.warn("Failed to persist legacy alarm last check timestamp.")
        }
    }

    /*
     * Cloud Credentials page
     */

    fun getCredentials(): List<CredentialSetting> {
        val json = settingsStore.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS)
        return if (json.isEmpty() || json == DEFAULT_CREDENTIALS) {
            emptyList()
        } else {
            runCatching {
                Json.decodeFromString<List<CredentialSetting>>(json)
            }.getOrElse { error ->
                logger.error("Failed to decode credentials from JSON", error)
                emptyList()
            }
        }
    }

    fun setCredentials(value: List<CredentialSetting>): Boolean {
        val json = Json.encodeToString(value)
        return settingsStore.setString(KEY_CREDENTIALS, json).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_CREDENTIALS)
        }
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
        val json = settingsStore.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS)
        val customProviders = if (json.isEmpty() || json == DEFAULT_VOICE_MODEL_PROVIDERS) {
            emptyList()
        } else {
            runCatching {
                Json.decodeFromString<List<VoiceModelProviderSetting>>(json)
            }.getOrElse { error ->
                logger.error("Failed to decode voice model providers from JSON", error)
                emptyList()
            }
        }

        // Include the local provider first
        return getLocalVoiceModelProviders() + customProviders
    }

    fun setVoiceModelProviders(value: List<VoiceModelProviderSetting>): Boolean {
        // Filter out the local providers before saving
        val customProviders = value.filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
        val json = Json.encodeToString(customProviders)
        return settingsStore.setString(KEY_VOICE_MODEL_PROVIDERS, json).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_VOICE_MODEL_PROVIDERS)
        }
    }

    fun getSelectedVoiceModelProviderId(): String =
        settingsStore.getString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, DEFAULT_SELECTED_VOICE_MODEL_PROVIDER_ID)

    fun setSelectedVoiceModelProviderId(value: String): Boolean =
        settingsStore.setString(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_SELECTED_VOICE_MODEL_PROVIDER_ID)
        }

    /*
     * Text Models page
     */

    fun getTextProcessingEnabled(): Boolean =
        settingsStore.getBoolean(KEY_TEXT_PROCESSING_ENABLED, DEFAULT_TEXT_PROCESSING_ENABLED)

    fun setTextProcessingEnabled(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_TEXT_PROCESSING_ENABLED, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_TEXT_PROCESSING_ENABLED)
        }

    fun getTextModelProviders(): List<TextModelProviderSetting> {
        val json = settingsStore.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS)
        return if (json.isEmpty() || json == DEFAULT_TEXT_MODEL_PROVIDERS) {
            emptyList()
        } else {
            runCatching {
                Json.decodeFromString<List<TextModelProviderSetting>>(json)
            }.getOrElse { error ->
                logger.error("Failed to decode text model providers from JSON", error)
                emptyList()
            }
        }
    }

    fun setTextModelProviders(value: List<TextModelProviderSetting>): Boolean {
        val json = Json.encodeToString(value)
        return settingsStore.setString(KEY_TEXT_MODEL_PROVIDERS, json).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_TEXT_MODEL_PROVIDERS)
        }
    }

    fun getSelectedTextModelProviderId(): String =
        settingsStore.getString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, DEFAULT_SELECTED_TEXT_MODEL_PROVIDER_ID)

    fun setSelectedTextModelProviderId(value: String): Boolean =
        settingsStore.setString(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_SELECTED_TEXT_MODEL_PROVIDER_ID)
        }

    /*
     * Personalization page
     */

    fun getCustomContext(): String =
        settingsStore.getString(KEY_CUSTOM_CONTEXT, DEFAULT_CUSTOM_CONTEXT)

    fun setCustomContext(value: String): Boolean =
        settingsStore.setString(KEY_CUSTOM_CONTEXT, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_CUSTOM_CONTEXT)
        }

    fun getCustomVocabulary(): List<String> =
        settingsStore.getStringArray(KEY_CUSTOM_VOCABULARY, DEFAULT_CUSTOM_VOCABULARY)

    fun setCustomVocabulary(value: List<String>): Boolean =
        settingsStore.setStringArray(KEY_CUSTOM_VOCABULARY, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_CUSTOM_VOCABULARY)
        }

    /*
     * Advanced page
     */

    fun getSanitizeSpecialChars(): Boolean =
        settingsStore.getBoolean(KEY_SANITIZE_SPECIAL_CHARS, DEFAULT_SANITIZE_SPECIAL_CHARS)

    fun setSanitizeSpecialChars(value: Boolean): Boolean =
        settingsStore.setBoolean(KEY_SANITIZE_SPECIAL_CHARS, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_SANITIZE_SPECIAL_CHARS)
        }

    fun getPostHideDelayMs(): Int =
        settingsStore.getInt(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS)

    fun setPostHideDelayMs(value: Int): Boolean =
        settingsStore.setInt(KEY_POST_HIDE_DELAY_MS, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_POST_HIDE_DELAY_MS)
        }

    fun getTypingDelayMs(): Int =
        settingsStore.getInt(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS)

    fun setTypingDelayMs(value: Int): Boolean =
        settingsStore.setInt(KEY_TYPING_DELAY_MS, value).also { success ->
            if (success) _settingsChanged.tryEmit(KEY_TYPING_DELAY_MS)
        }
}
