package com.zugaldia.speedofsound.app.screens.preferences.voice

import com.zugaldia.speedofsound.app.DEFAULT_ADD_VOICE_PROVIDER_DIALOG_HEIGHT
import com.zugaldia.speedofsound.app.DEFAULT_ADD_PROVIDER_DIALOG_WIDTH
import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.DEFAULT_MARGIN
import com.zugaldia.speedofsound.app.STYLE_CLASS_ACCENT
import com.zugaldia.speedofsound.app.STYLE_CLASS_ERROR
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUCCESS
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.app.STYLE_CLASS_WARNING
import com.zugaldia.speedofsound.app.screens.preferences.shared.BaseUrlEntryRow
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.app.screens.preferences.shared.CustomServicePreset.Companion.VOICE_SERVICE_PRESETS
import com.zugaldia.speedofsound.app.screens.preferences.shared.ModelComboRow
import com.zugaldia.speedofsound.app.screens.preferences.shared.ProviderComboRow
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_VOICE_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.MAX_PROVIDER_CONFIG_NAME_LENGTH
import com.zugaldia.speedofsound.core.desktop.settings.MAX_VOICE_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting
import com.zugaldia.speedofsound.core.desktop.settings.VoiceModelProviderSetting
import com.zugaldia.speedofsound.core.generateUniqueId
import com.zugaldia.speedofsound.core.isValidUrl
import com.zugaldia.speedofsound.core.models.voice.VoiceModel
import com.zugaldia.speedofsound.core.plugins.asr.AsrProvider
import com.zugaldia.speedofsound.core.plugins.asr.DEFAULT_ASR_OPENAI_MODEL_ID
import com.zugaldia.speedofsound.core.plugins.asr.getModelsForProvider
import com.zugaldia.speedofsound.core.desktop.settings.SUPPORTED_LOCAL_ASR_MODELS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.gnome.adw.ComboRow
import org.gnome.adw.Dialog
import org.gnome.adw.EntryRow
import org.gnome.adw.PreferencesGroup
import org.gnome.glib.GLib
import org.gnome.gtk.Align
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Label
import org.gnome.gtk.Orientation
import org.gnome.gtk.StringList
import org.gnome.pango.WrapMode
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class AddVoiceModelProviderDialog(
    private val viewModel: PreferencesViewModel,
    private val onProviderAdded: (VoiceModelProviderSetting) -> Boolean
) : Dialog() {
    private val logger = LoggerFactory.getLogger(AddVoiceModelProviderDialog::class.java)

    private val nameEntry: EntryRow
    private val providerComboRow: ProviderComboRow<AsrProvider>
    private val modelComboRow: ModelComboRow<VoiceModel>
    private val credentialComboRow: ComboRow
    private val baseUrlEntry: BaseUrlEntryRow
    private val addButton: Button
    private val messageLabel: Label

    // Default to OpenAI (only custom/remote providers are shown in this dialog)
    private var selectedProvider: AsrProvider = AsrProvider.OPENAI
    private var selectedModelId: String = DEFAULT_ASR_OPENAI_MODEL_ID
    private var selectedCredentialId: String? = null
    private var currentCredentials: List<CredentialSetting> = emptyList()
    private var currentProviders: List<VoiceModelProviderSetting> = emptyList()
    private val dialogScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        title = "Add Voice Model Provider"
        contentWidth = DEFAULT_ADD_PROVIDER_DIALOG_WIDTH
        contentHeight = DEFAULT_ADD_VOICE_PROVIDER_DIALOG_HEIGHT

        nameEntry = EntryRow().apply {
            title = "Configuration Name"
        }

        providerComboRow = ProviderComboRow(
            rowTitle = "Provider",
            rowSubtitle = "Select the ASR provider",
            getCurrentProvider = { selectedProvider },
            onProviderSelected = { provider: AsrProvider ->
                selectedProvider = provider
                baseUrlEntry.clear()
                messageLabel.label = ""
                messageLabel.removeCssClass(STYLE_CLASS_ACCENT)
                messageLabel.removeCssClass(STYLE_CLASS_SUCCESS)
                messageLabel.removeCssClass(STYLE_CLASS_WARNING)
                messageLabel.removeCssClass(STYLE_CLASS_ERROR)
                messageLabel.addCssClass(STYLE_CLASS_ACCENT)
                modelComboRow.refreshComboRows()
                updateAddButtonState()
            },
            providers = AsrProvider.getCustomProviders()
        )

        modelComboRow = ModelComboRow(
            rowTitle = "Model",
            rowSubtitle = "Select the model to use",
            getModels = { getModelsForProvider(selectedProvider) },
            getCurrentModelId = { selectedModelId },
            onModelIdSelected = { modelId: String ->
                selectedModelId = modelId
                updateAddButtonState()
            }
        )

        credentialComboRow = ComboRow().apply {
            title = "Credentials"
            subtitle = "Select a credential (optional)"
            enableSearch = false
        }

        baseUrlEntry = BaseUrlEntryRow(
            servicePresets = VOICE_SERVICE_PRESETS,
            onTextChanged = { updateAddButtonState() }
        )

        val preferencesGroup = PreferencesGroup().apply {
            title = "Provider Configuration"
            description = "Configure a voice model provider for speech recognition"
            vexpand = false
            add(nameEntry)
            add(providerComboRow)
            add(credentialComboRow)
            add(modelComboRow.comboRow)
            add(modelComboRow.customEntryRow)
            add(baseUrlEntry)
        }

        val cancelButton = Button.withLabel("Cancel").apply {
            onClicked {
                dialogScope.cancel()
                close()
            }
        }

        addButton = Button.withLabel("Add").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            sensitive = false
            onClicked {
                val name = nameEntry.text.trim()
                val baseUrl = baseUrlEntry.getBaseUrl()
                val modelId = selectedModelId
                if (validateInput(name, baseUrl, modelId)) {
                    val config = VoiceModelProviderSetting(
                        id = generateUniqueId(),
                        name = name,
                        provider = selectedProvider,
                        credentialId = selectedCredentialId,
                        baseUrl = baseUrl,
                        modelId = modelId
                    )
                    if (onProviderAdded(config)) {
                        dialogScope.cancel()
                        close()
                    }
                }
            }
        }

        val buttonBox = Box(Orientation.HORIZONTAL, DEFAULT_BOX_SPACING).apply {
            halign = Align.END
            valign = Align.END
            append(cancelButton)
            append(addButton)
        }

        messageLabel = Label("").apply {
            vexpand = true
            halign = Align.CENTER
            valign = Align.CENTER
            wrap = true
            wrapMode = WrapMode.WORD_CHAR
            marginStart = DEFAULT_MARGIN
            marginEnd = DEFAULT_MARGIN
        }

        val contentBox = Box(Orientation.VERTICAL, DEFAULT_BOX_SPACING).apply {
            marginTop = DEFAULT_MARGIN
            marginBottom = DEFAULT_MARGIN
            marginStart = DEFAULT_MARGIN
            marginEnd = DEFAULT_MARGIN
            vexpand = true
            append(preferencesGroup)
            append(messageLabel)
            append(buttonBox)
        }

        child = contentBox
        onClosed { dialogScope.cancel() }

        // Initialize state
        refreshSnapshots()
        loadCredentialList()
        baseUrlEntry.clear()
        messageLabel.label = ""
        messageLabel.removeCssClass(STYLE_CLASS_ACCENT)
        messageLabel.removeCssClass(STYLE_CLASS_SUCCESS)
        messageLabel.removeCssClass(STYLE_CLASS_WARNING)
        messageLabel.removeCssClass(STYLE_CLASS_ERROR)
        messageLabel.addCssClass(STYLE_CLASS_ACCENT)
        modelComboRow.refreshComboRows()
        updateAddButtonState()

        // Set up notifications after all widgets are initialized
        providerComboRow.setupNotifications()
        modelComboRow.setupNotifications()
        baseUrlEntry.setupNotifications()
        nameEntry.onNotify("text") { updateAddButtonState() }
        credentialComboRow.onNotify("selected") {
            val selectedIndex = credentialComboRow.selected
            selectedCredentialId = if (selectedIndex > 0 && selectedIndex <= currentCredentials.size) {
                currentCredentials[selectedIndex - 1].id
            } else {
                null
            }
            updateAddButtonState()
        }
        dialogScope.launch {
            viewModel.settingsChanged
                .filter { it == KEY_CREDENTIALS || it == KEY_VOICE_MODEL_PROVIDERS }
                .collect {
                    GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                        refreshSnapshots()
                        loadCredentialList(selectedCredentialId)
                        updateAddButtonState()
                        false
                    }
                }
        }
    }

    private fun loadCredentialList(preservedCredentialId: String? = null) {
        val options = mutableListOf("None")
        options.addAll(currentCredentials.map { it.name })
        credentialComboRow.model = StringList(options.toTypedArray())
        credentialComboRow.selected = preservedCredentialId?.let { credentialId ->
            currentCredentials.indexOfFirst { it.id == credentialId }
                .takeIf { it >= 0 }
                ?.plus(1)
        } ?: 0
        selectedCredentialId = if (credentialComboRow.selected > 0 && credentialComboRow.selected <= currentCredentials.size) {
            currentCredentials[credentialComboRow.selected - 1].id
        } else {
            null // Index 0 is "None"
        }
    }

    private fun updateAddButtonState() {
        val name = nameEntry.text.trim()
        val baseUrl = baseUrlEntry.getBaseUrl()
        val modelId = selectedModelId
        addButton.sensitive = validateInput(name, baseUrl, modelId)
    }

    @Suppress("ReturnCount")
    private fun validateInput(name: String, baseUrl: String?, modelId: String?): Boolean {
        if (name.isEmpty()) { return false }
        if (name.length > MAX_PROVIDER_CONFIG_NAME_LENGTH) { return false }
        val customProviderCount = currentProviders.count { it.id !in SUPPORTED_LOCAL_ASR_MODELS.keys }
        if (customProviderCount >= MAX_VOICE_MODEL_PROVIDERS) { return false }
        if (currentProviders.any { it.name == name }) { return false }
        if (modelId == null) { return false }
        if (baseUrl != null && !isValidUrl(baseUrl)) { return false }
        return true
    }

    private fun refreshSnapshots() {
        currentCredentials = viewModel.peekCredentials()
        currentProviders = viewModel.peekVoiceModelProviders(currentCredentials.map { it.id }.toSet())
    }
}
