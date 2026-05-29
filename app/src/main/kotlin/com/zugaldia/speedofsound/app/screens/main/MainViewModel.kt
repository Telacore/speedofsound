package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.app.APPEND_SPACE_TEXT
import com.zugaldia.speedofsound.app.isGStreamerDisabled
import com.zugaldia.speedofsound.app.plugins.recorder.GStreamerRecorder
import com.zugaldia.speedofsound.app.plugins.textoutput.ClipboardTextOutput
import com.zugaldia.speedofsound.app.plugins.textoutput.PortalTextOutput
import com.zugaldia.speedofsound.app.plugins.textoutput.PortalTextOutputOptions
import com.zugaldia.speedofsound.app.portals.PortalsSessionManager
import com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus
import com.zugaldia.speedofsound.app.settings.AsrProviderManager
import com.zugaldia.speedofsound.app.settings.LlmProviderManager
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_LANGUAGE
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_SECONDARY_LANGUAGE
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CUSTOM_CONTEXT
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CUSTOM_VOCABULARY
import com.zugaldia.speedofsound.core.desktop.settings.KEY_DEFAULT_LANGUAGE
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SECONDARY_LANGUAGE
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_VOICE_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SANITIZE_SPECIAL_CHARS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_OUTPUT_METHOD
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_PROCESSING_ENABLED
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TYPING_DELAY_MS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_CLIPBOARD
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_PORTAL
import com.zugaldia.speedofsound.core.languageFromIso2
import com.zugaldia.speedofsound.core.FatalStartupException
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.director.DefaultDirector
import com.zugaldia.speedofsound.core.plugins.director.DirectorEvent
import com.zugaldia.speedofsound.core.plugins.director.PipelineStage
import com.zugaldia.speedofsound.core.plugins.recorder.JvmRecorder
import com.zugaldia.speedofsound.core.plugins.recorder.RecorderEvent
import com.zugaldia.speedofsound.core.plugins.textoutput.TextOutputPlugin
import com.zugaldia.speedofsound.core.plugins.textoutput.TextOutputRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import org.gnome.glib.GLib
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.milliseconds

@Suppress("TooManyFunctions")
class MainViewModel(
    private val settingsClient: SettingsClient,
    private val portalsClient: PortalsClient,
    private val onShortcutTriggered: (() -> Unit)? = null,
) {
    private val logger = LoggerFactory.getLogger(MainViewModel::class.java)

    var state: MainState = MainState()
        private set

    private val registry = AppPluginRegistry()

    private val recorder = if (isGStreamerDisabled()) {
        JvmRecorder(settingsClient.getRecorderOptions())
    } else {
        GStreamerRecorder(settingsClient.getRecorderOptions())
    }

    private val director = DefaultDirector(registry, settingsClient.getDirectorOptions())

    private val portalTextOutput = PortalTextOutput(
        portalsClient = portalsClient,
        options = PortalTextOutputOptions(
            typingDelayMs = settingsClient.getTypingDelayMs().toLong(),
            sanitizeSpecialChars = settingsClient.getSanitizeSpecialChars(),
        ),
    )

    private val clipboardTextOutput = ClipboardTextOutput(portalsClient = portalsClient)

    private val portalsSessionManager = PortalsSessionManager(
        portalsClient = portalsClient,
        settingsClient = settingsClient,
        initialSessionDisconnected = true,
        initialRemoteDesktopStatus = if (settingsClient.getPortalsRestoreToken().isBlank()) {
            RemoteDesktopStatus.NeedToken
        } else {
            RemoteDesktopStatus.Ready
        },
    )

    private val asrProviderManager = AsrProviderManager(registry, settingsClient)
    private val llmProviderManager = LlmProviderManager(registry, settingsClient)
    private var startupErrorMessage: String = ""
    private var hasFatalStartupError = false
    private var activeRemoteDesktopStatus: RemoteDesktopStatus = if (
        settingsClient.getPortalsRestoreToken().isBlank()
    ) {
        RemoteDesktopStatus.NeedToken
    } else {
        RemoteDesktopStatus.Ready
    }
    private val viewModelJob = SupervisorJob()
    private val viewModelScope = CoroutineScope(Dispatchers.Default + viewModelJob)
    private var currentPipelineJob: Job? = null
    private var lastToggleTime = 0L

    fun start() {
        // Phase 1 (sync, main thread): Register plugins and set up event collection.
        // This is lightweight so the window can render immediately with "Loading..." state.
        registry.register(AppPluginCategory.RECORDER, recorder)
        asrProviderManager.registerAsrPlugins()
        llmProviderManager.registerLlmPlugins()
        registry.register(AppPluginCategory.TEXT_OUTPUT, portalTextOutput)
        registry.register(AppPluginCategory.TEXT_OUTPUT, clipboardTextOutput)
        registry.register(AppPluginCategory.DIRECTOR, director)

        collectDirectorEvents()
        collectRecorderEvents()
        collectSettingsChanges()
        collectPortalsSessionState()
        collectShortcutActivations()

        // Initialize status UI labels
        onPrimaryLanguageSelected(forceUpdate = true)
        updateModelLabels()

        // Phase 2 (async, IO thread): Enable plugins (heavy: model extraction + ONNX load).
        state.updateStage(AppStage.LOADING)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                registry.setActiveById(AppPluginCategory.RECORDER, recorder.id)
                asrProviderManager.activateSelectedProvider()
                llmProviderManager.activateSelectedProvider()
                activateSelectedTextOutput()
                registry.setActiveById(AppPluginCategory.DIRECTOR, DefaultDirector.ID)
                if (shouldForceClipboardFallback(settingsClient.getTextOutputMethod(), portalsClient.isPortalAvailable)) {
                    switchToClipboardFallback("Desktop portal is not available for this session.")
                    updateRemoteDesktopStatusUi(activeRemoteDesktopStatus)
                }
                portalsSessionManager.initialize(viewModelScope)
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    state.updateStage(AppStage.IDLE)
                    false
                }
            } catch (e: FatalStartupException) {
                handleFatalStartupError(e)
            } catch (e: IllegalStateException) {
                handleFatalStartupError(
                    FatalStartupException("Unexpected startup error: ${e.message}")
                )
            }
        }
    }

    fun onTriggerAction() {
        if (hasFatalStartupError) {
            logger.warn("Ignoring trigger action because startup failed: {}", startupErrorMessage)
            return
        }

        // Safeguard: only proceed if we're in IDLE (to start) or LISTENING (to stop) state
        val currentStage = state.currentStage()
        if (currentStage != AppStage.IDLE && currentStage != AppStage.LISTENING) {
            logger.info("Ignoring trigger action during processing stage: $currentStage")
            return
        }

        // Check if the portal session needs reconnection. This typically happens when the user locks the screen and
        // comes back. The remote desktop session is closed in those circumstances for security reasons.
        if (shouldAttemptPortalReconnect(settingsClient.getTextOutputMethod())) {
            portalsSessionManager.attemptReconnect(viewModelScope)
        }
        logger.info("Trigger action invoked.")
        toggleListening()
    }

    private fun collectDirectorEvents() {
        viewModelScope.launch {
            director.events.filterIsInstance<DirectorEvent>().collect { event ->
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    when (event) {
                        is DirectorEvent.RecordingStarted -> onRecordingStarted()
                        is DirectorEvent.TranscriptionStarted -> state.updateStage(AppStage.TRANSCRIBING)
                        is DirectorEvent.PolishingStarted -> state.updateStage(AppStage.POLISHING)
                        is DirectorEvent.PipelineCompleted -> onPipelineCompleted(event)
                        is DirectorEvent.PipelineCancelled -> onPipelineCancelled()
                        is DirectorEvent.PipelineError -> onPipelineError(event)
                    }
                    false // Return false for one-shot execution
                }
            }
        }
    }

    private fun collectRecorderEvents() {
        viewModelScope.launch {
            recorder.events.filterIsInstance<RecorderEvent>().collect { event ->
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    when (event) {
                        is RecorderEvent.RecordingLevel -> state.updateRecordingLevel(event.level)
                    }
                    false // Return false for one-shot execution
                }
            }
        }
    }

    private fun collectSettingsChanges() {
        viewModelScope.launch {
            settingsClient.settingsChanged.collect { key ->
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    refreshSettings(key)
                    false // Return false for one-shot execution
                }
            }
        }
    }

    private fun collectShortcutActivations() {
        viewModelScope.launch {
            portalsSessionManager.shortcutActivated.collect {
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    logger.info("Global shortcut activated.")
                    onShortcutTriggered?.invoke()
                    false // Return false for one-shot execution
                }
            }
        }
    }

    private fun collectPortalsSessionState() {
        viewModelScope.launch {
            portalsSessionManager.isSessionDisconnected.collect { isDisconnected ->
                // setPortalsSessionDisconnected does not emit a GObject signal, so no need for idleAdd
                state.setPortalsSessionDisconnected(isDisconnected)
            }
        }
        viewModelScope.launch {
            portalsSessionManager.remoteDesktopStatus.collect { status ->
                if (status == RemoteDesktopStatus.NotSupported) {
                    switchToClipboardFallback("Remote desktop portal is not supported on this system.")
                }
                activeRemoteDesktopStatus = status
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    updateRemoteDesktopStatusUi(status)
                    false // Return false for one-shot execution
                }
            }
        }
    }

    private fun onRecordingStarted() {
        state.updateStage(AppStage.LISTENING)
    }

    private fun refreshSettings(key: String) {
        if (hasFatalStartupError && key != KEY_TEXT_OUTPUT_METHOD) {
            logger.warn("Ignoring settings change '{}' while startup is in error state", key)
            return
        }

        when (key) {
            KEY_DEFAULT_LANGUAGE -> onPrimaryLanguageSelected()
            KEY_SECONDARY_LANGUAGE -> onSecondaryLanguageUpdated()
            KEY_TEXT_OUTPUT_METHOD -> refreshTextOutputMethodSetting()
            KEY_TYPING_DELAY_MS -> refreshTypingDelaySetting()
            KEY_SANITIZE_SPECIAL_CHARS -> refreshSanitizeSpecialCharsSetting()
            KEY_TEXT_PROCESSING_ENABLED -> refreshTextProcessingSetting()
            KEY_CUSTOM_CONTEXT -> refreshCustomContextSetting()
            KEY_CUSTOM_VOCABULARY -> refreshCustomVocabularySetting()
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID, KEY_VOICE_MODEL_PROVIDERS -> refreshAsrSetting(key)
            KEY_SELECTED_TEXT_MODEL_PROVIDER_ID, KEY_TEXT_MODEL_PROVIDERS -> refreshLlmSetting(key)
            KEY_CREDENTIALS -> refreshCredentials()
        }
    }

    private fun refreshTypingDelaySetting() {
        portalTextOutput.updateOptions(
            portalTextOutput.getOptions().copy(
                typingDelayMs = settingsClient.getTypingDelayMs().toLong()
            )
        )
    }

    private fun refreshSanitizeSpecialCharsSetting() {
        portalTextOutput.updateOptions(
            portalTextOutput.getOptions().copy(
                sanitizeSpecialChars = settingsClient.getSanitizeSpecialChars()
            )
        )
    }

    private fun refreshTextProcessingSetting() {
        director.updateOptions(
            director.getOptions().copy(enableTextProcessing = settingsClient.getTextProcessingEnabled())
        )
        updateModelLabels()
    }

    private fun refreshCustomContextSetting() {
        director.updateOptions(
            director.getOptions().copy(customContext = settingsClient.getCustomContext())
        )
    }

    private fun refreshCustomVocabularySetting() {
        director.updateOptions(
            director.getOptions().copy(customVocabulary = settingsClient.getCustomVocabulary())
        )
    }

    private fun refreshAsrSetting(key: String) {
        val asrResult = when (key) {
            KEY_SELECTED_VOICE_MODEL_PROVIDER_ID -> runCatching {
                asrProviderManager.activateSelectedProvider()
            }

            else -> runCatching {
                asrProviderManager.refreshProviderConfiguration()
            }
        }

        asrResult.onSuccess { updateModelLabels() }
            .onFailure { error ->
                logger.error("Failed to apply ASR settings after {} change: {}", key, error.message)
                if (error is FatalStartupException) {
                    handleFatalStartupError(error)
                }
            }
    }

    private fun refreshTextOutputMethodSetting() {
        runCatching { activateSelectedTextOutput() }
            .onSuccess {
                if (shouldForceClipboardFallback(settingsClient.getTextOutputMethod(), portalsClient.isPortalAvailable)) {
                    switchToClipboardFallback("Remote desktop portal is not supported on this system.")
                    return@onSuccess
                }
                if (shouldAutoStartPortalSession(
                        settingsClient.getTextOutputMethod(),
                        activeRemoteDesktopStatus,
                        portalsClient.isPortalAvailable,
                    )
                ) {
                    startPortalsSession(settingsClient.getPortalsRestoreToken().ifBlank { null })
                    return@onSuccess
                }
                updateRemoteDesktopStatusUi(activeRemoteDesktopStatus)
            }
            .onFailure { error ->
                logger.error("Failed to switch text output method: {}", error.message)
                portalsClient.showNotification(
                    "Failed to switch text output method: ${error.message ?: "Unknown error"}"
                )
            }
    }

    private fun refreshLlmSetting(key: String) {
        val llmResult = when (key) {
            KEY_SELECTED_TEXT_MODEL_PROVIDER_ID -> runCatching {
                llmProviderManager.activateSelectedProvider()
            }

            else -> runCatching {
                llmProviderManager.refreshProviderConfiguration()
            }
        }

        llmResult.onSuccess { updateModelLabels() }
            .onFailure { error ->
                logger.error("Failed to apply LLM settings after {} change: {}", key, error.message)
                portalsClient.showNotification(
                    "Could not apply LLM setting '$key': ${error.message ?: "Unknown error"}"
                )
            }
    }

    private fun refreshCredentials() {
        runCatching { asrProviderManager.refreshProviderConfiguration() }
            .onFailure { error ->
                logger.error("Failed to refresh ASR provider configuration: {}", error.message)
                if (error is FatalStartupException) {
                    handleFatalStartupError(error)
                }
            }
        runCatching { llmProviderManager.refreshProviderConfiguration() }
            .onFailure { error ->
                logger.error("Failed to refresh LLM provider configuration: {}", error.message)
                portalsClient.showNotification(
                    "Could not refresh LLM provider configuration: " +
                        "${error.message ?: "Unknown error"}"
                )
            }
    }

    private fun updateModelLabels() {
        if (hasFatalStartupError) {
            state.updateAsrModel("ASR unavailable")
            state.updateLlmModel(llmProviderManager.getCurrentProviderName())
            return
        }
        val asrModelName = asrProviderManager.getCurrentProviderName()
        val llmModelName = llmProviderManager.getCurrentProviderName()
        state.updateAsrModel(asrModelName)
        state.updateLlmModel(llmModelName)
    }

    private fun activateSelectedTextOutput() {
        val pluginId = if (settingsClient.getTextOutputMethod() == TEXT_OUTPUT_METHOD_CLIPBOARD) {
            ClipboardTextOutput.ID
        } else {
            PortalTextOutput.ID
        }
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, pluginId)
    }

    private fun switchToClipboardFallback(reason: String) {
        if (settingsClient.getTextOutputMethod() == TEXT_OUTPUT_METHOD_CLIPBOARD) return

        logger.warn("Falling back to Clipboard output: {}", reason)
        runCatching {
            settingsClient.setTextOutputMethod(TEXT_OUTPUT_METHOD_CLIPBOARD)
                .takeIf { !it }
                ?.let {
                    logger.warn(
                        "Could not persist text output method preference; continuing with runtime fallback"
                    )
                }
            activateSelectedTextOutput()
        }
            .onFailure { error ->
                logger.error("Failed to switch to clipboard fallback: {}", error.message)
                portalsClient.showNotification(
                    "Could not switch to clipboard output: ${error.message ?: "Unknown error"}"
                )
            }
            .onSuccess {
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    updateRemoteDesktopStatusUi(activeRemoteDesktopStatus)
                    false
                }
            }
    }

    private fun updateRemoteDesktopStatusUi(status: RemoteDesktopStatus) {
        val uiStatus = if (settingsClient.getTextOutputMethod() == TEXT_OUTPUT_METHOD_CLIPBOARD) {
            RemoteDesktopStatus.Ready
        } else {
            status
        }
        state.updateRemoteDesktopStatus(uiStatus)
    }

    fun startPortalsSession(token: String? = null) {
        portalsSessionManager.startSession(viewModelScope, token)
    }

    fun openUri(uri: String) {
        viewModelScope.launch { portalsClient.openUri(uri) }
    }

    fun onPrimaryLanguageSelected(forceUpdate: Boolean = false) {
        val language = languageFromIso2(settingsClient.getDefaultLanguage()) ?: DEFAULT_LANGUAGE
        if (!forceUpdate && language == state.currentLanguage()) return // Force update on initialization
        state.updateLanguage(language)
        asrProviderManager.updateLanguage(language)
        director.updateOptions(director.getOptions().copy(language = language))
    }

    fun onSecondaryLanguageSelected() {
        val language = languageFromIso2(settingsClient.getSecondaryLanguage()) ?: DEFAULT_SECONDARY_LANGUAGE
        if (language == state.currentLanguage()) return
        state.updateLanguage(language)
        asrProviderManager.updateLanguage(language)
        director.updateOptions(director.getOptions().copy(language = language))
    }

    fun onLanguageToggled() {
        val primaryLanguage = languageFromIso2(settingsClient.getDefaultLanguage()) ?: DEFAULT_LANGUAGE
        if (state.currentLanguage() == primaryLanguage) onSecondaryLanguageSelected() else onPrimaryLanguageSelected()
    }

    private fun onSecondaryLanguageUpdated() {
        val primaryLanguage = languageFromIso2(settingsClient.getDefaultLanguage()) ?: DEFAULT_LANGUAGE
        if (state.currentLanguage() == primaryLanguage) return
        onSecondaryLanguageSelected()
    }

    fun toggleListening() {
        // Debounce: ignore calls within TOGGLE_DEBOUNCE_MS of the last one.
        // Protects against (1) a global shortcut and the in-app shortcut both firing for the
        // same keypress, and (2) the user accidentally tapping the shortcut twice in quick succession.
        val now = System.currentTimeMillis()
        if (now - lastToggleTime < TOGGLE_DEBOUNCE_MS) return
        lastToggleTime = now

        if (state.currentStage() == AppStage.IDLE) {
            currentPipelineJob = viewModelScope.launch { director.start() }
        } else if (state.currentStage() == AppStage.LISTENING) {
            viewModelScope.launch { director.stop() }
        }
    }

    fun cancelListening() {
        if (state.currentStage() in listOf(AppStage.LISTENING, AppStage.TRANSCRIBING, AppStage.POLISHING)) {
            currentPipelineJob?.cancel()
            viewModelScope.launch { director.cancel() }
        }
    }

    private fun onPipelineCompleted(event: DirectorEvent.PipelineCompleted) {
        logger.info("Pipeline completed.")
        if (event.finalResult.isBlank()) {
            hideAndReset()
            return
        }

        val suffix = if (settingsClient.getAppendSpace()) APPEND_SPACE_TEXT else ""
        val finalText = event.finalResult.trim() + suffix
        val textOutput = registry.getActive(AppPluginCategory.TEXT_OUTPUT) as? TextOutputPlugin<*>
        if (textOutput == null) {
            logger.error("No text output plugin active")
            hideAndReset()
            return
        }

        val request = TextOutputRequest(finalText)
        val isPortalOutputActive = textOutput is PortalTextOutput
        var isClipboardPreparedAhead = false
        viewModelScope.launch {
            // Clipboard (and similar focus-dependent operations) must be set while this window
            // still owns focus, before hiding, otherwise Wayland rejects the clipboard write.
            if (isPortalOutputActive) {
                isClipboardPreparedAhead = runCatching {
                    clipboardTextOutput.prepareText(request).getOrThrow()
                }.onFailure { error ->
                    logger.warn("Clipboard warmup preparation failed: {}", error.message)
                }.isSuccess
            }

            val preparedTextOutput = textOutput.prepareText(request)
            val postHideDelayMs = settingsClient.getPostHideDelayMs()
            if (preparedTextOutput.isFailure) {
                val error = preparedTextOutput.exceptionOrNull()
                if (textOutput is PortalTextOutput) {
                    logger.warn("Portal prepare failed, attempting clipboard fallback: {}", error?.message)
                    GLib.idleAdd(GLib.PRIORITY_DEFAULT) { hideAndReset(); false }
                    if (postHideDelayMs > 0) delay(postHideDelayMs.toLong().milliseconds)
                    outputTextWithFallback(request, isClipboardPreparedAhead)
                        .onFailure { fallbackError ->
                            logger.error("Error outputting text after portal prepare failure: ${fallbackError.message}")
                            portalsClient.showNotification(
                                body = "Failed to output text: ${fallbackError.message ?: "Unknown error"}"
                            )
                        }
                } else {
                    logger.error("Failed to prepare text: ${error?.message}")
                    portalsClient.showNotification(
                        body = "Failed to prepare text: ${error?.message ?: "Unknown error"}"
                    )
                    GLib.idleAdd(GLib.PRIORITY_DEFAULT) { hideAndReset(); false }
                }
                return@launch
            }

            GLib.idleAdd(GLib.PRIORITY_DEFAULT) { hideAndReset(); false }
            if (postHideDelayMs > 0) delay(postHideDelayMs.toLong().milliseconds)

            outputTextWithFallback(request, isClipboardPreparedAhead)
                .onFailure { error ->
                    logger.error("Error outputting text: ${error.message}")
                    portalsClient.showNotification(body = "Failed to output text: ${error.message ?: "Unknown error"}")
                }
        }
    }

    private fun onPipelineCancelled() {
        hideAndReset()
    }

    private fun onPipelineError(event: DirectorEvent.PipelineError) {
        logger.error("Pipeline error at ${event.stage}: ${event.error.message}")
        val body = when (event.stage) {
            PipelineStage.RECORDING -> "Recording failed: ${event.error.message ?: "Unknown error"}"
            PipelineStage.TRANSCRIPTION -> "Transcription failed: ${event.error.message ?: "Unknown error"}"
            PipelineStage.POLISHING -> "Text processing failed: ${event.error.message ?: "Unknown error"}"
        }
        portalsClient.showNotification(body = body)
        hideAndReset()
    }

    private fun hideAndReset() {
        state.emitPipelineCompleted() // Signals the main window to hide
        state.updateStage(AppStage.IDLE)
    }

    private suspend fun outputTextWithFallback(
        request: TextOutputRequest,
        isClipboardPreparedAhead: Boolean = false,
    ): Result<Unit> {
        val primaryTextOutput = registry.getActive(AppPluginCategory.TEXT_OUTPUT) as? TextOutputPlugin<*>
            ?: return Result.failure(IllegalStateException("No text output plugin active"))

        return primaryTextOutput.outputText(request).recoverCatching { error ->
            if (primaryTextOutput is PortalTextOutput) {
                logger.warn("Portal output failed, falling back to clipboard: {}", error.message)
                runCatching {
                    switchToClipboardFallback("Remote desktop portal output failed; using clipboard fallback.")
                    val clipboardTextOutput = registry.getActive(AppPluginCategory.TEXT_OUTPUT) as? ClipboardTextOutput
                        ?: throw IllegalStateException("Clipboard text output plugin unavailable")
                    if (!isClipboardPreparedAhead) {
                        clipboardTextOutput.prepareText(request).getOrThrow()
                    }
                    clipboardTextOutput.outputText(request).getOrThrow()
                }.getOrThrow()
            }
            throw error
        }
    }

    private fun handleFatalStartupError(error: FatalStartupException) {
        if (hasFatalStartupError) return
        hasFatalStartupError = true
        startupErrorMessage = error.message ?: "Unknown fatal startup error"
        logger.error("A fatal error was encountered during startup: {}", error.message)
        runCatching { registry.shutdownAll() }
            .onFailure { shutdownError ->
                logger.error(
                    "Failed to clean up plugins after startup failure: {}",
                    shutdownError.message
                )
            }
        runCatching { portalsSessionManager.shutdown() }
            .onFailure { shutdownError ->
                logger.error(
                    "Failed to clean up portal session manager: {}",
                    shutdownError.message
                )
            }
        GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
            state.updateAsrModel("ASR unavailable")
            state.updateLlmModel(llmProviderManager.getCurrentProviderName())
            state.updateStage(AppStage.IDLE)
            false
        }
        portalsClient.showNotification("Speed of Sound could not start. ${error.message}")
    }

    fun shutdown() {
        logger.info("Shutting down.")
        viewModelScope.cancel()
        portalsSessionManager.shutdown()
        registry.shutdownAll()
    }

    companion object {
        private const val TOGGLE_DEBOUNCE_MS = 500L
    }
}
