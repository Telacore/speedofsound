package com.zugaldia.speedofsound.app.screens.preferences.importexport

import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.APPLICATION_SHORT
import com.zugaldia.speedofsound.core.io.AtomicFileWriter
import com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.SettingsExport
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.shouldPreserveExactWhisperSelection
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
        val credentials = viewModel.peekCredentials()
        val credentialIds = credentials.map { it.id }.toSet()
        val voiceModelProviders = viewModel.peekVoiceModelProviders(credentialIds)
            .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
            .normalizedCredentialRefs(credentialIds)
        val textModelProviders = viewModel.peekTextModelProviders(credentialIds)
            .normalizedCredentialRefs(credentialIds)
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
            credentials = credentials,
            voiceModelProviders = voiceModelProviders,
            textModelProviders = textModelProviders,
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

        val snapshot = captureImportSnapshot()

        try {
            requireWrite(viewModel.setDefaultLanguage(exportData.defaultLanguage), "default language")
            requireWrite(viewModel.setSecondaryLanguage(exportData.secondaryLanguage), "secondary language")
            requireWrite(viewModel.setBackgroundRecording(exportData.backgroundRecording), "background recording")
            requireWrite(viewModel.setHideInsteadOfMinimize(exportData.hideInsteadOfMinimize), "hide instead of minimize")
            requireWrite(viewModel.setStayHiddenOnActivation(exportData.stayHiddenOnActivation), "stay hidden on activation")
            requireWrite(viewModel.setAppendSpace(exportData.appendSpace), "append space")
            requireWrite(viewModel.setMaxAlarms(exportData.maxAlarms), "max alarms")

            val existingAlarms = snapshot.alarms
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

            val existingCredentials = snapshot.credentials
            val existingCredentialIds = existingCredentials.map { it.id }.toSet()
            val newCredentials = exportData.credentials.filter { it.id !in existingCredentialIds }
            if (newCredentials.isNotEmpty()) {
                requireWrite(viewModel.setCredentials(existingCredentials + newCredentials), "credentials")
            }
            val importedCredentialIds = (existingCredentials + newCredentials).map { it.id }.toSet()
            val credentialsAdded = importedCredentialIds.count { it !in existingCredentialIds }

            val existingVoiceProviders = snapshot.voiceProviders
            val existingVoiceIds = existingVoiceProviders.map { it.id }.toSet()
            val existingCustomVoiceIds = existingVoiceProviders
                .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
                .map { it.id }
                .toSet()
            val newVoiceProviders = exportData.voiceModelProviders.filter { it.id !in existingVoiceIds }
            val normalizedVoiceProviders = (existingVoiceProviders + newVoiceProviders).normalizedCredentialRefs(importedCredentialIds)
            if (normalizedVoiceProviders != existingVoiceProviders) {
                requireWrite(
                    viewModel.setVoiceModelProviders(
                        normalizedVoiceProviders,
                        importedCredentialIds,
                        normalizedVoiceProviders,
                    ),
                    "voice model providers",
                )
            }
            val importedVoiceIds = normalizedVoiceProviders
                .filter { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
                .map { it.id }
                .toSet()
            val voiceProvidersAdded = importedVoiceIds.count { it !in existingCustomVoiceIds }
            requireWrite(
                persistExactVoiceProviderSelection(snapshot.selectedVoiceProviderIdExact, normalizedVoiceProviders),
                "selected voice model provider",
            )

            val existingTextProviders = snapshot.textProviders
            val existingTextIds = existingTextProviders.map { it.id }.toSet()
            val newTextProviders = exportData.textModelProviders.filter { it.id !in existingTextIds }
            val normalizedTextProviders = (existingTextProviders + newTextProviders).normalizedCredentialRefs(importedCredentialIds)
            if (normalizedTextProviders != existingTextProviders) {
                requireWrite(
                    viewModel.setTextModelProviders(
                        normalizedTextProviders,
                        importedCredentialIds,
                        normalizedTextProviders,
                    ),
                    "text model providers",
                )
            }
            val importedTextIds = normalizedTextProviders.map { it.id }.toSet()
            val textProvidersAdded = importedTextIds.count { it !in existingTextIds }
            requireWrite(
                viewModel.setSelectedTextModelProviderId(snapshot.selectedTextProviderId),
                "selected text model provider",
            )
            if (snapshot.textProcessingEnabled && normalizedTextProviders.isNotEmpty()) {
                requireWrite(viewModel.setTextProcessingEnabled(true), "text processing enabled")
            }

            val existingVocabSet = snapshot.customVocabulary.toSet()
            val newVocabWords = exportData.customVocabulary.filter { it !in existingVocabSet }
            if (newVocabWords.isNotEmpty()) {
                requireWrite(viewModel.setCustomVocabulary(snapshot.customVocabulary + newVocabWords), "custom vocabulary")
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
        } catch (error: Throwable) {
            logger.warn("Import failed, restoring previous settings snapshot", error)
            restoreImportSnapshot(snapshot)
            throw error
        }
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

    private fun persistExactVoiceProviderSelection(
        selectedProviderId: String,
        availableVoiceProviders: List<VoiceModelProviderSetting>,
    ): Boolean {
        val exactSelectedProviderId = selectedProviderId.trim()
        return if (shouldPreserveExactWhisperSelection(exactSelectedProviderId, availableVoiceProviders)) {
            viewModel.setSelectedVoiceModelProviderIdExact(exactSelectedProviderId)
        } else {
            viewModel.setSelectedVoiceModelProviderId(selectedProviderId, availableVoiceProviders)
        }
    }

    private fun requireWrite(wrote: Boolean, operation: String) {
        if (!wrote) {
            throw IllegalStateException("Failed to save $operation during import")
        }
    }

    private fun captureImportSnapshot(): ImportSnapshot =
        ImportSnapshot(
            defaultLanguage = viewModel.peekDefaultLanguage(),
            secondaryLanguage = viewModel.peekSecondaryLanguage(),
            backgroundRecording = viewModel.peekBackgroundRecording(),
            hideInsteadOfMinimize = viewModel.peekHideInsteadOfMinimize(),
            stayHiddenOnActivation = viewModel.peekStayHiddenOnActivation(),
            appendSpace = viewModel.peekAppendSpace(),
            maxAlarms = viewModel.peekMaxAlarms(),
            alarms = viewModel.peekAlarms(),
            sanitizeSpecialChars = viewModel.peekSanitizeSpecialChars(),
            postHideDelayMs = viewModel.peekPostHideDelayMs(),
            typingDelayMs = viewModel.peekTypingDelayMs(),
            customContext = viewModel.peekCustomContext(),
            credentials = viewModel.peekCredentials(),
            voiceProviders = viewModel.peekVoiceModelProviders(),
            selectedVoiceProviderIdExact = viewModel.peekSelectedVoiceModelProviderIdExact(),
            textProviders = viewModel.peekTextModelProviders(),
            selectedTextProviderId = viewModel.peekSelectedTextModelProviderId(),
            textProcessingEnabled = viewModel.peekTextProcessingEnabled(),
            customVocabulary = viewModel.peekCustomVocabulary(),
            alarmSchedulerState = viewModel.peekAlarmSchedulerState(),
        )

    private fun restoreImportSnapshot(snapshot: ImportSnapshot) {
        restoreWrite("default language") { viewModel.setDefaultLanguage(snapshot.defaultLanguage) }
        restoreWrite("secondary language") { viewModel.setSecondaryLanguage(snapshot.secondaryLanguage) }
        restoreWrite("background recording") { viewModel.setBackgroundRecording(snapshot.backgroundRecording) }
        restoreWrite("hide instead of minimize") { viewModel.setHideInsteadOfMinimize(snapshot.hideInsteadOfMinimize) }
        restoreWrite("stay hidden on activation") { viewModel.setStayHiddenOnActivation(snapshot.stayHiddenOnActivation) }
        restoreWrite("append space") { viewModel.setAppendSpace(snapshot.appendSpace) }
        restoreWrite("max alarms") { viewModel.setMaxAlarms(snapshot.maxAlarms) }
        restoreWrite("alarms") { viewModel.setAlarms(snapshot.alarms) }
        restoreWrite("alarm scheduler state") { viewModel.setAlarmSchedulerState(snapshot.alarmSchedulerState) }
        restoreWrite("sanitize special chars") { viewModel.setSanitizeSpecialChars(snapshot.sanitizeSpecialChars) }
        restoreWrite("post hide delay") { viewModel.setPostHideDelayMs(snapshot.postHideDelayMs) }
        restoreWrite("typing delay") { viewModel.setTypingDelayMs(snapshot.typingDelayMs) }
        restoreWrite("custom context") { viewModel.setCustomContext(snapshot.customContext) }
        restoreWrite("credentials") { viewModel.setCredentials(snapshot.credentials) }
        restoreWrite("voice model providers") {
            viewModel.setVoiceModelProviders(
                snapshot.voiceProviders,
                snapshot.credentials.map { it.id }.toSet(),
                snapshot.voiceProviders,
            )
        }
        restoreWrite("selected voice model provider") {
            persistExactVoiceProviderSelection(
                snapshot.selectedVoiceProviderIdExact,
                snapshot.voiceProviders,
            )
        }
        restoreWrite("text model providers") {
            viewModel.setTextModelProviders(
                snapshot.textProviders,
                snapshot.credentials.map { it.id }.toSet(),
                snapshot.textProviders,
            )
        }
        restoreWrite("selected text model provider") {
            viewModel.setSelectedTextModelProviderId(snapshot.selectedTextProviderId, snapshot.textProviders)
        }
        restoreWrite("text processing enabled") { viewModel.setTextProcessingEnabled(snapshot.textProcessingEnabled) }
        restoreWrite("custom vocabulary") { viewModel.setCustomVocabulary(snapshot.customVocabulary) }
    }

    private fun restoreWrite(label: String, write: () -> Boolean) {
        if (!write()) {
            logger.warn("Failed to restore {} after import failure", label)
        }
    }

    private data class ImportSnapshot(
        val defaultLanguage: String,
        val secondaryLanguage: String,
        val backgroundRecording: Boolean,
        val hideInsteadOfMinimize: Boolean,
        val stayHiddenOnActivation: Boolean,
        val appendSpace: Boolean,
        val maxAlarms: Int,
        val alarms: List<com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting>,
        val sanitizeSpecialChars: Boolean,
        val postHideDelayMs: Int,
        val typingDelayMs: Int,
        val customContext: String,
        val credentials: List<com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting>,
        val voiceProviders: List<VoiceModelProviderSetting>,
        val selectedVoiceProviderIdExact: String,
        val textProviders: List<TextModelProviderSetting>,
        val selectedTextProviderId: String,
        val textProcessingEnabled: Boolean,
        val customVocabulary: List<String>,
        val alarmSchedulerState: AlarmSchedulerState,
    )

    companion object {
        const val EXPORT_FILENAME = "$APPLICATION_SHORT-preferences.json"
    }
}
