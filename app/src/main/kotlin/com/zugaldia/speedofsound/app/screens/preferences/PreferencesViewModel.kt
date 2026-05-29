package com.zugaldia.speedofsound.app.screens.preferences

import com.zugaldia.speedofsound.core.APPLICATION_URL_KEYBOARD_SHORTCUT
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.stargate.sdk.globalshortcuts.BoundShortcut
import com.zugaldia.stargate.sdk.session.CreateSessionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions") // ViewModel delegates to SettingsClient for all preference properties
class PreferencesViewModel(
    private val settingsClient: SettingsClient,
    private val portalsClient: PortalsClient,
) {
    private val logger = LoggerFactory.getLogger(PreferencesViewModel::class.java)
    val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsChanged = settingsClient.settingsChanged

    init {
        logger.info("Initializing.")
    }

    fun shutdown() {
        logger.info("Shutting down.")
        viewModelScope.cancel()
    }

    /*
     * General page
     */

    fun peekShortcutConfigured(): Boolean = settingsClient.peekShortcutConfigured()
    fun setShortcutConfigured(value: Boolean): Boolean = settingsClient.setShortcutConfigured(value)

    suspend fun createGlobalShortcutsSession(): Result<CreateSessionResponse> =
        portalsClient.createGlobalShortcutsSession()
    suspend fun listGlobalShortcuts(): Result<List<BoundShortcut>> = portalsClient.listGlobalShortcuts()
    suspend fun bindGlobalShortcuts(): Result<List<BoundShortcut>> = portalsClient.bindGlobalShortcuts()
    val globalShortcutsVersion: Int get() = portalsClient.globalShortcutsVersion
    fun configureGlobalShortcuts(): Result<Unit> = portalsClient.configureGlobalShortcuts()
    suspend fun openDocumentationUri() = portalsClient.openUri(APPLICATION_URL_KEYBOARD_SHORTCUT)

    fun peekBackgroundRecording(): Boolean = settingsClient.peekBackgroundRecording()
    fun setBackgroundRecording(value: Boolean): Boolean = settingsClient.setBackgroundRecording(value)

    fun peekHideInsteadOfMinimize(): Boolean = settingsClient.peekHideInsteadOfMinimize()
    fun setHideInsteadOfMinimize(value: Boolean): Boolean = settingsClient.setHideInsteadOfMinimize(value)

    fun peekStayHiddenOnActivation(): Boolean = settingsClient.peekStayHiddenOnActivation()
    fun setStayHiddenOnActivation(value: Boolean): Boolean = settingsClient.setStayHiddenOnActivation(value)

    fun peekDefaultLanguage(): String = settingsClient.peekDefaultLanguage()
    fun setDefaultLanguage(value: String): Boolean = settingsClient.setDefaultLanguage(value)

    fun peekSecondaryLanguage(): String = settingsClient.peekSecondaryLanguage()
    fun setSecondaryLanguage(value: String): Boolean = settingsClient.setSecondaryLanguage(value)

    fun peekAppendSpace(): Boolean = settingsClient.peekAppendSpace()
    fun setAppendSpace(value: Boolean): Boolean = settingsClient.setAppendSpace(value)

    fun peekTextOutputMethod(): String = settingsClient.peekTextOutputMethod()
    fun setTextOutputMethod(value: String): Boolean = settingsClient.setTextOutputMethod(value)

    /*
     * Alarms page
     */

    fun peekAlarms(): List<AlarmSetting> = settingsClient.peekAlarms()
    fun setAlarms(value: List<AlarmSetting>): Boolean = settingsClient.setAlarms(value)
    fun peekMaxAlarms(): Int = settingsClient.peekMaxAlarms()
    fun setMaxAlarms(value: Int): Boolean = settingsClient.setMaxAlarms(value)
    fun setAlarmSchedulerState(value: AlarmSchedulerState): Boolean = settingsClient.setAlarmSchedulerState(value)
    fun peekAlarmSchedulerState(): AlarmSchedulerState = settingsClient.peekAlarmSchedulerState()

    /*
     * Cloud Credentials page
     */

    fun peekCredentials(): List<CredentialSetting> = settingsClient.peekCredentials()
    fun setCredentials(value: List<CredentialSetting>): Boolean =
        settingsClient.setCredentials(value)

    /*
     * Voice Models page
     */

    fun peekVoiceModelProviders(): List<VoiceModelProviderSetting> = settingsClient.peekVoiceModelProviders()
    fun setVoiceModelProviders(value: List<VoiceModelProviderSetting>): Boolean =
        settingsClient.setVoiceModelProviders(value)
    fun peekSelectedVoiceModelProviderIdExact(): String =
        settingsClient.peekSelectedVoiceModelProviderIdExact()
    fun setSelectedVoiceModelProviderId(value: String): Boolean =
        settingsClient.setSelectedVoiceModelProviderId(value)

    /*
     * Text Models page
     */

    fun peekTextProcessingEnabled(): Boolean = settingsClient.peekTextProcessingEnabled()
    fun setTextProcessingEnabled(value: Boolean): Boolean = settingsClient.setTextProcessingEnabled(value)

    fun peekTextModelProviders(): List<TextModelProviderSetting> = settingsClient.peekTextModelProviders()
    fun setTextModelProviders(value: List<TextModelProviderSetting>): Boolean =
        settingsClient.setTextModelProviders(value)

    fun peekSelectedTextModelProviderId(): String =
        settingsClient.peekSelectedTextModelProviderId()
    fun setSelectedTextModelProviderId(value: String): Boolean =
        settingsClient.setSelectedTextModelProviderId(value)

    /*
     * Personalization page
     */

    fun peekCustomContext(): String = settingsClient.peekCustomContext()
    fun setCustomContext(value: String): Boolean = settingsClient.setCustomContext(value)

    fun peekCustomVocabulary(): List<String> = settingsClient.peekCustomVocabulary()
    fun setCustomVocabulary(value: List<String>): Boolean = settingsClient.setCustomVocabulary(value)

    /*
     * Advanced page
     */

    fun peekSanitizeSpecialChars(): Boolean = settingsClient.peekSanitizeSpecialChars()
    fun setSanitizeSpecialChars(value: Boolean): Boolean = settingsClient.setSanitizeSpecialChars(value)

    fun peekPostHideDelayMs(): Int = settingsClient.peekPostHideDelayMs()
    fun setPostHideDelayMs(value: Int): Boolean = settingsClient.setPostHideDelayMs(value)

    fun peekTypingDelayMs(): Int = settingsClient.peekTypingDelayMs()
    fun setTypingDelayMs(value: Int): Boolean = settingsClient.setTypingDelayMs(value)
}
