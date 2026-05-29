package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.asr.DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID
import com.zugaldia.speedofsound.core.plugins.llm.LlmProvider
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The type of credential. Currently, the only supported credential type is an API key. Prepared for future expansion
 * (e.g., Vertex AI credentials).
 */
@Serializable
enum class CredentialType {
    API_KEY
}

/**
 * A credential used to authenticate with a Voice or Text provider.
 */
@Serializable
data class CredentialSetting(
    val id: String,
    val type: CredentialType,
    val name: String,
    val value: String
)

/**
 * Configuration for a voice model provider.
 *
 * Users can configure which voice provider to use for voice transcription, along with
 * authentication credentials and provider-specific settings.
 */
@Serializable
data class VoiceModelProviderSetting(
    override val id: String,
    override val name: String,
    val provider: AsrProvider,
    val modelId: String,
    val credentialId: String? = null,
    val baseUrl: String? = null
) : SelectableProviderSetting

/**
 * Configuration for a text model provider.
 *
 * Users can configure which LLM provider to use for text processing, along with
 * authentication credentials and provider-specific settings.
 */
@Serializable
data class TextModelProviderSetting(
    override val id: String,
    override val name: String,
    val provider: LlmProvider,
    val modelId: String,
    val credentialId: String? = null,
    val baseUrl: String? = null,
    val disableThinking: Boolean = false
) : SelectableProviderSetting

/**
 * How an alarm should notify the user.
 *
 * Desktop Linux does not expose a reliable cross-desktop vibration API, so these actions map
 * to notification urgency levels as a best-effort approximation.
 */
@Serializable
enum class AlarmAction {
    SILENT,
    NORMAL,
    ATTENTION,
    URGENT
}

/**
 * Days on which an alarm can repeat.
 */
@Serializable
enum class AlarmRepeatDay {
    MONDAY,
    TUESDAY,
    WEDNESDAY,
    THURSDAY,
    FRIDAY,
    SATURDAY,
    SUNDAY
}

/**
 * A repeating alarm.
 */
@Serializable
data class AlarmSetting(
    val id: String,
    val name: String = "",
    val hour: Int,
    val minute: Int,
    val action: AlarmAction = AlarmAction.NORMAL,
    val enabled: Boolean = true,
    val repeatDays: List<AlarmRepeatDay> = allAlarmRepeatDays()
)

fun AlarmSetting.isValid(): Boolean =
    id.isNotBlank() && hour in 0..23 && minute in 0..59

fun AlarmSetting.normalized(): AlarmSetting =
    copy(
        name = name.trim().take(MAX_ALARM_NAME_LENGTH),
        repeatDays = repeatDays.normalizedRepeatDays(),
    )

fun AlarmSetting.isScheduledOn(date: LocalDate): Boolean =
    repeatDays.normalizedRepeatDays().any { it.matches(date.dayOfWeek) }

fun hasActiveAlarms(alarms: List<AlarmSetting>): Boolean =
    alarms.any { it.enabled }

fun shouldPreserveExactWhisperSelection(
    selectedProviderId: String,
    availableProviders: List<SelectableProviderSetting>,
): Boolean =
    selectedProviderId.trim() == DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID &&
        availableProviders.none { it.id == DEFAULT_ASR_SHERPA_WHISPER_MODEL_ID }

fun AlarmRepeatDay.matches(dayOfWeek: DayOfWeek): Boolean = when (dayOfWeek) {
    DayOfWeek.MONDAY -> this == AlarmRepeatDay.MONDAY
    DayOfWeek.TUESDAY -> this == AlarmRepeatDay.TUESDAY
    DayOfWeek.WEDNESDAY -> this == AlarmRepeatDay.WEDNESDAY
    DayOfWeek.THURSDAY -> this == AlarmRepeatDay.THURSDAY
    DayOfWeek.FRIDAY -> this == AlarmRepeatDay.FRIDAY
    DayOfWeek.SATURDAY -> this == AlarmRepeatDay.SATURDAY
    DayOfWeek.SUNDAY -> this == AlarmRepeatDay.SUNDAY
}

fun DayOfWeek.toAlarmRepeatDay(): AlarmRepeatDay = when (this) {
    DayOfWeek.MONDAY -> AlarmRepeatDay.MONDAY
    DayOfWeek.TUESDAY -> AlarmRepeatDay.TUESDAY
    DayOfWeek.WEDNESDAY -> AlarmRepeatDay.WEDNESDAY
    DayOfWeek.THURSDAY -> AlarmRepeatDay.THURSDAY
    DayOfWeek.FRIDAY -> AlarmRepeatDay.FRIDAY
    DayOfWeek.SATURDAY -> AlarmRepeatDay.SATURDAY
    DayOfWeek.SUNDAY -> AlarmRepeatDay.SUNDAY
}

fun DayOfWeek.shortLabel(): String = toAlarmRepeatDay().shortLabel()

fun AlarmRepeatDay.shortLabel(): String = when (this) {
    AlarmRepeatDay.MONDAY -> "Mon"
    AlarmRepeatDay.TUESDAY -> "Tue"
    AlarmRepeatDay.WEDNESDAY -> "Wed"
    AlarmRepeatDay.THURSDAY -> "Thu"
    AlarmRepeatDay.FRIDAY -> "Fri"
    AlarmRepeatDay.SATURDAY -> "Sat"
    AlarmRepeatDay.SUNDAY -> "Sun"
}

fun AlarmRepeatDay.longLabel(): String = when (this) {
    AlarmRepeatDay.MONDAY -> "Monday"
    AlarmRepeatDay.TUESDAY -> "Tuesday"
    AlarmRepeatDay.WEDNESDAY -> "Wednesday"
    AlarmRepeatDay.THURSDAY -> "Thursday"
    AlarmRepeatDay.FRIDAY -> "Friday"
    AlarmRepeatDay.SATURDAY -> "Saturday"
    AlarmRepeatDay.SUNDAY -> "Sunday"
}

fun allAlarmRepeatDays(): List<AlarmRepeatDay> = AlarmRepeatDay.values().toList()

fun weekdayAlarmRepeatDays(): List<AlarmRepeatDay> = listOf(
    AlarmRepeatDay.MONDAY,
    AlarmRepeatDay.TUESDAY,
    AlarmRepeatDay.WEDNESDAY,
    AlarmRepeatDay.THURSDAY,
    AlarmRepeatDay.FRIDAY,
)

fun weekendAlarmRepeatDays(): List<AlarmRepeatDay> = listOf(
    AlarmRepeatDay.SATURDAY,
    AlarmRepeatDay.SUNDAY,
)

fun List<AlarmRepeatDay>.normalizedRepeatDays(): List<AlarmRepeatDay> =
    if (isEmpty()) {
        allAlarmRepeatDays()
    } else {
        distinct().sortedBy { it.ordinal }
    }

/**
 * Persisted runtime state for the alarm scheduler.
 */
@Serializable
data class AlarmSchedulerState(
    val lastCheckAt: String? = null,
    val lastTriggeredDates: Map<String, String> = emptyMap(),
)

/**
 * A serializable snapshot of all exportable user preferences.
 * Instance-specific settings (portal token, selected provider IDs, text processing toggle) are excluded.
 *
 * IMPORTANT: When adding a new setting here, you must also update [ImportExportManager] in the app
 * module to include it in both the export() and importSettings() functions.
 */
@Serializable
data class SettingsExport(
    val version: Int = 6,
    val defaultLanguage: String = DEFAULT_LANGUAGE.iso2,
    val secondaryLanguage: String = DEFAULT_SECONDARY_LANGUAGE.iso2,
    val backgroundRecording: Boolean = DEFAULT_BACKGROUND_RECORDING,
    val hideInsteadOfMinimize: Boolean = DEFAULT_HIDE_INSTEAD_OF_MINIMIZE,
    val stayHiddenOnActivation: Boolean = DEFAULT_STAY_HIDDEN_ON_ACTIVATION,
    val appendSpace: Boolean = DEFAULT_APPEND_SPACE,
    val alarms: List<AlarmSetting> = emptyList(),
    val maxAlarms: Int = DEFAULT_MAX_ALARMS,
    val alarmSchedulerState: AlarmSchedulerState? = null,
    val credentials: List<CredentialSetting> = emptyList(),
    val voiceModelProviders: List<VoiceModelProviderSetting> = emptyList(),
    val textModelProviders: List<TextModelProviderSetting> = emptyList(),
    val sanitizeSpecialChars: Boolean = DEFAULT_SANITIZE_SPECIAL_CHARS,
    val postHideDelayMs: Int = DEFAULT_POST_HIDE_DELAY_MS,
    val typingDelayMs: Int = DEFAULT_TYPING_DELAY_MS,
    val customContext: String = DEFAULT_CUSTOM_CONTEXT,
    val customVocabulary: List<String> = emptyList()
)
