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

        viewModel.setDefaultLanguage(exportData.defaultLanguage)
        viewModel.setSecondaryLanguage(exportData.secondaryLanguage)
        viewModel.setBackgroundRecording(exportData.backgroundRecording)
        viewModel.setHideInsteadOfMinimize(exportData.hideInsteadOfMinimize)
        viewModel.setStayHiddenOnActivation(exportData.stayHiddenOnActivation)
        viewModel.setAppendSpace(exportData.appendSpace)
        viewModel.setMaxAlarms(exportData.maxAlarms)

        val existingAlarms = viewModel.peekAlarms()
        val existingAlarmIds = existingAlarms.map { it.id }.toSet()
        val newAlarms = exportData.alarms.filter { it.id !in existingAlarmIds }
        viewModel.setAlarms(existingAlarms + newAlarms)
        val importedAlarmIds = viewModel.peekAlarms().map { it.id }.toSet()
        val alarmsAdded = importedAlarmIds.count { it !in existingAlarmIds }

        exportData.alarmSchedulerState?.let { schedulerState ->
            val filteredSchedulerState = schedulerState.copy(
                lastTriggeredDates = schedulerState.lastTriggeredDates.filterKeys { it in importedAlarmIds }
            )
            viewModel.setAlarmSchedulerState(filteredSchedulerState)
        }

        viewModel.setSanitizeSpecialChars(exportData.sanitizeSpecialChars)
        viewModel.setPostHideDelayMs(exportData.postHideDelayMs)
        viewModel.setTypingDelayMs(exportData.typingDelayMs)
        viewModel.setCustomContext(exportData.customContext)
        val wasTextProcessingEnabled = viewModel.peekTextProcessingEnabled()

        val existingCredentials = viewModel.peekCredentials()
        val existingCredentialIds = existingCredentials.map { it.id }.toSet()
        val newCredentials = exportData.credentials.filter { it.id !in existingCredentialIds }
        if (newCredentials.isNotEmpty()) {
            viewModel.setCredentials(existingCredentials + newCredentials)
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
            viewModel.setVoiceModelProviders(normalizedVoiceProviders)
        }
        val importedVoiceIds = viewModel.peekVoiceModelProviders()
            .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
            .map { it.id }
            .toSet()
        val voiceProvidersAdded = importedVoiceIds.count { it !in existingCustomVoiceIds }
        viewModel.setSelectedVoiceModelProviderId(viewModel.peekSelectedVoiceModelProviderId())

        val existingTextProviders = viewModel.peekTextModelProviders()
        val existingTextIds = existingTextProviders.map { it.id }.toSet()
        val newTextProviders = exportData.textModelProviders.filter { it.id !in existingTextIds }
        val normalizedTextProviders = (existingTextProviders + newTextProviders).normalizedCredentialRefs(importedCredentialIds)
        if (normalizedTextProviders != existingTextProviders) {
            viewModel.setTextModelProviders(normalizedTextProviders)
        }
        val importedTextIds = viewModel.peekTextModelProviders().map { it.id }.toSet()
        val textProvidersAdded = importedTextIds.count { it !in existingTextIds }
        viewModel.setSelectedTextModelProviderId(viewModel.peekSelectedTextModelProviderId())
        if (wasTextProcessingEnabled && viewModel.peekTextModelProviders().isNotEmpty()) {
            viewModel.setTextProcessingEnabled(true)
        }

        val existingVocabulary = viewModel.peekCustomVocabulary()
        val existingVocabSet = existingVocabulary.toSet()
        val newVocabWords = exportData.customVocabulary.filter { it !in existingVocabSet }
        if (newVocabWords.isNotEmpty()) {
            viewModel.setCustomVocabulary(existingVocabulary + newVocabWords)
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

    companion object {
        const val EXPORT_FILENAME = "$APPLICATION_SHORT-preferences.json"
    }
}
