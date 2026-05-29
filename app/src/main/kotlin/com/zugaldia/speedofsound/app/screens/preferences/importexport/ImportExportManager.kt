package com.zugaldia.speedofsound.app.screens.preferences.importexport

import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.APPLICATION_SHORT
import com.zugaldia.speedofsound.core.io.AtomicFileWriter
import com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.SettingsExport
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.speedofsound.core.getDataDir
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

data class ImportResult(
    val filePath: String,
    val alarmsAdded: Int = 0,
    val credentialsAdded: Int = 0,
    val voiceProvidersAdded: Int = 0,
    val textProvidersAdded: Int = 0,
    val vocabularyWordsAdded: Int = 0,
    val alarmSchedulerStateImported: Boolean = false
)

class ImportExportManager(private val viewModel: PreferencesViewModel) {
    private val logger = LoggerFactory.getLogger(ImportExportManager::class.java)

    private val prettyJson = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun export(): Result<String> = runCatching {
        // We do not export anything that is instance-specific (e.g., portal token). That
        // also includes built-in voice models, which we filter out below.
        val schedulerState = viewModel.peekAlarmSchedulerState()
        val exportData = SettingsExport(
            defaultLanguage = viewModel.peekDefaultLanguage(),
            secondaryLanguage = viewModel.peekSecondaryLanguage(),
            backgroundRecording = viewModel.peekBackgroundRecording(),
            hideInsteadOfMinimize = viewModel.peekHideInsteadOfMinimize(),
            stayHiddenOnActivation = viewModel.peekStayHiddenOnActivation(),
            appendSpace = viewModel.peekAppendSpace(),
            alarms = viewModel.peekAlarms(),
            maxAlarms = viewModel.peekMaxAlarms(),
            alarmSchedulerState = AlarmSchedulerState(
                lastCheckAt = schedulerState.lastCheckAt,
                lastTriggeredDates = schedulerState.lastTriggeredDates,
            ),
            credentials = viewModel.peekCredentials(),
            voiceModelProviders = viewModel.peekVoiceModelProviders()
                .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
                .normalizedCredentialRefs(viewModel.peekCredentials().map { it.id }.toSet()),
            textModelProviders = viewModel.peekTextModelProviders()
                .normalizedCredentialRefs(viewModel.peekCredentials().map { it.id }.toSet()),
            sanitizeSpecialChars = viewModel.peekSanitizeSpecialChars(),
            postHideDelayMs = viewModel.peekPostHideDelayMs(),
            typingDelayMs = viewModel.peekTypingDelayMs(),
            customContext = viewModel.peekCustomContext(),
            customVocabulary = viewModel.peekCustomVocabulary()
        )

        val outputFile = getDataDir().resolve(EXPORT_FILENAME).toFile()
        AtomicFileWriter.write(outputFile) { tempFile ->
            tempFile.writeText(prettyJson.encodeToString(exportData))
        }.getOrThrow()
        logger.info("Exported settings to: ${outputFile.absolutePath}")
        outputFile.absolutePath
    }

    fun importSettings(): Result<ImportResult> = runCatching {
        val inputFile = getDataDir().resolve(EXPORT_FILENAME).toFile()
        check(inputFile.exists()) { "Export file not found: ${inputFile.absolutePath}" }
        check(inputFile.isFile) { "Export file is not a regular file: ${inputFile.absolutePath}" }

        val exportData = decodeExportData(inputFile)
        if (exportData.version !in 1..6) {
            throw IllegalStateException("Unsupported export version: ${exportData.version}")
        }

        requireWrite(viewModel.setDefaultLanguage(exportData.defaultLanguage), "default language")
        requireWrite(viewModel.setSecondaryLanguage(exportData.secondaryLanguage), "secondary language")
        requireWrite(viewModel.setBackgroundRecording(exportData.backgroundRecording), "background recording")
        requireWrite(viewModel.setHideInsteadOfMinimize(exportData.hideInsteadOfMinimize), "hide instead of minimize")
        requireWrite(viewModel.setStayHiddenOnActivation(exportData.stayHiddenOnActivation), "stay hidden on activation")
        requireWrite(viewModel.setAppendSpace(exportData.appendSpace), "append space")
        requireWrite(viewModel.setMaxAlarms(exportData.maxAlarms), "max alarms")

        val existingAlarms = viewModel.peekAlarms()
        val existingAlarmIds = existingAlarms.map { it.id }.toSet()
        val newAlarms = exportData.alarms.filter { it.id !in existingAlarmIds }
        requireWrite(viewModel.setAlarms(existingAlarms + newAlarms), "alarms")
        val importedAlarmIds = viewModel.peekAlarms().map { it.id }.toSet()
        val alarmsAdded = importedAlarmIds.count { it !in existingAlarmIds }

        exportData.alarmSchedulerState?.let { schedulerState ->
            val filteredSchedulerState = schedulerState.copy(
                lastTriggeredDates = schedulerState.lastTriggeredDates.filterKeys { it in importedAlarmIds }
            )
            requireWrite(
                viewModel.setAlarmSchedulerState(filteredSchedulerState),
                "alarm scheduler state",
            )
        }

        requireWrite(viewModel.setSanitizeSpecialChars(exportData.sanitizeSpecialChars), "sanitize special chars")
        requireWrite(viewModel.setPostHideDelayMs(exportData.postHideDelayMs), "post hide delay")
        requireWrite(viewModel.setTypingDelayMs(exportData.typingDelayMs), "typing delay")
        requireWrite(viewModel.setCustomContext(exportData.customContext), "custom context")
        val wasTextProcessingEnabled = viewModel.peekTextProcessingEnabled()

        val existingCredentials = viewModel.peekCredentials()
        val existingCredentialIds = existingCredentials.map { it.id }.toSet()
        val newCredentials = exportData.credentials.filter { it.id !in existingCredentialIds }
        if (newCredentials.isNotEmpty()) {
            requireWrite(viewModel.setCredentials(existingCredentials + newCredentials), "credentials")
        }
        val importedCredentialIds = viewModel.peekCredentials().map { it.id }.toSet()
        val credentialsAdded = importedCredentialIds.count { it !in existingCredentialIds }

        val existingVoiceProviders = viewModel.peekVoiceModelProviders()
        val existingVoiceIds = existingVoiceProviders.map { it.id }.toSet()
        val existingCustomVoiceIds = existingVoiceProviders
            .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
            .map { it.id }
            .toSet()
        val newVoiceProviders = exportData.voiceModelProviders.filter { it.id !in existingVoiceIds }
        val normalizedVoiceProviders = (existingVoiceProviders + newVoiceProviders).normalizedCredentialRefs(importedCredentialIds)
        if (normalizedVoiceProviders != existingVoiceProviders) {
            requireWrite(viewModel.setVoiceModelProviders(normalizedVoiceProviders), "voice model providers")
        }
        val importedVoiceIds = viewModel.peekVoiceModelProviders()
            .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
            .map { it.id }
            .toSet()
        val voiceProvidersAdded = importedVoiceIds.count { it !in existingCustomVoiceIds }
        requireWrite(
            viewModel.setSelectedVoiceModelProviderId(viewModel.peekSelectedVoiceModelProviderId()),
            "selected voice model provider",
        )

        val existingTextProviders = viewModel.peekTextModelProviders()
        val existingTextIds = existingTextProviders.map { it.id }.toSet()
        val newTextProviders = exportData.textModelProviders.filter { it.id !in existingTextIds }
        val normalizedTextProviders = (existingTextProviders + newTextProviders).normalizedCredentialRefs(importedCredentialIds)
        if (normalizedTextProviders != existingTextProviders) {
            requireWrite(viewModel.setTextModelProviders(normalizedTextProviders), "text model providers")
        }
        val importedTextIds = viewModel.peekTextModelProviders().map { it.id }.toSet()
        val textProvidersAdded = importedTextIds.count { it !in existingTextIds }
        requireWrite(
            viewModel.setSelectedTextModelProviderId(viewModel.peekSelectedTextModelProviderId()),
            "selected text model provider",
        )
        if (wasTextProcessingEnabled && viewModel.peekTextModelProviders().isNotEmpty()) {
            requireWrite(viewModel.setTextProcessingEnabled(true), "text processing enabled")
        }

        val existingVocabulary = viewModel.peekCustomVocabulary()
        val existingVocabSet = existingVocabulary.toSet()
        val newVocabWords = exportData.customVocabulary.filter { it !in existingVocabSet }
        if (newVocabWords.isNotEmpty()) {
            requireWrite(viewModel.setCustomVocabulary(existingVocabulary + newVocabWords), "custom vocabulary")
        }
        val importedVocabulary = viewModel.peekCustomVocabulary()
        val vocabularyWordsAdded = importedVocabulary.count { it !in existingVocabSet }

        logger.info("Imported settings from: ${inputFile.absolutePath}")
        ImportResult(
            filePath = inputFile.absolutePath,
            alarmsAdded = alarmsAdded,
            credentialsAdded = credentialsAdded,
            voiceProvidersAdded = voiceProvidersAdded,
            textProvidersAdded = textProvidersAdded,
            vocabularyWordsAdded = vocabularyWordsAdded,
            alarmSchedulerStateImported = exportData.alarmSchedulerState != null
        )
    }

    private fun decodeExportData(inputFile: java.io.File): SettingsExport {
        val rawJson = try {
            inputFile.readText()
        } catch (e: Exception) {
            throw IllegalStateException("Could not read export file: ${inputFile.absolutePath}", e)
        }

        return try {
            prettyJson.decodeFromString<SettingsExport>(rawJson)
        } catch (e: Exception) {
            throw IllegalStateException("Malformed export file: ${inputFile.absolutePath}", e)
        }
    }

    private fun List<VoiceModelProviderSetting>.normalizedCredentialRefs(
        validCredentialIds: Set<String>
    ): List<VoiceModelProviderSetting> =
        map { provider ->
            if (provider.credentialId != null && provider.credentialId !in validCredentialIds) {
                provider.copy(credentialId = null)
            } else {
                provider
            }
        }

    private fun List<TextModelProviderSetting>.normalizedCredentialRefs(
        validCredentialIds: Set<String>
    ): List<TextModelProviderSetting> =
        map { provider ->
            if (provider.credentialId != null && provider.credentialId !in validCredentialIds) {
                provider.copy(credentialId = null)
            } else {
                provider
            }
        }

    private fun requireWrite(wrote: Boolean, operation: String) {
        if (!wrote) {
            throw IllegalStateException("Failed to save $operation during import")
        }
    }

    companion object {
        const val EXPORT_FILENAME = "$APPLICATION_SHORT-preferences.json"
    }
}
