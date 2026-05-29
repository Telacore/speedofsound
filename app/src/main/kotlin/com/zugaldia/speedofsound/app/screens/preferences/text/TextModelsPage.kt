package com.zugaldia.speedofsound.app.screens.preferences.text

import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.ICON_PASSWORD
import com.zugaldia.speedofsound.app.ICON_SERVER
import com.zugaldia.speedofsound.app.ICON_TEXT_EDITOR
import com.zugaldia.speedofsound.app.ICON_TRASH
import com.zugaldia.speedofsound.app.STYLE_CLASS_BOXED_LIST
import com.zugaldia.speedofsound.app.STYLE_CLASS_DIM_LABEL
import com.zugaldia.speedofsound.app.STYLE_CLASS_FLAT
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.app.screens.preferences.shared.ActiveProviderComboRow
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_SELECTED_TEXT_MODEL_PROVIDER_ID
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_PROCESSING_ENABLED
import com.zugaldia.speedofsound.core.desktop.settings.MAX_TEXT_MODEL_PROVIDERS
import com.zugaldia.speedofsound.core.desktop.settings.TextModelProviderSetting
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.gnome.adw.ActionRow
import org.gnome.adw.PreferencesGroup
import org.gnome.adw.PreferencesPage
import org.gnome.adw.SwitchRow
import org.gnome.gtk.Align
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Label
import org.gnome.gtk.ListBox
import org.gnome.gtk.Orientation
import org.gnome.gtk.SelectionMode
import org.gnome.glib.GLib
import org.slf4j.LoggerFactory

class TextModelsPage(private val viewModel: PreferencesViewModel) : PreferencesPage() {
    private val logger = LoggerFactory.getLogger(TextModelsPage::class.java)
    private val scope = viewModel.viewModelScope

    private val enableSwitch: SwitchRow
    private val activeProviderComboRow: ActiveProviderComboRow<TextModelProviderSetting>
    private val providersListBox: ListBox
    private val placeholderBox: Box
    private val addProviderButton: Button
    private var suppressEnableSwitchNotify = false

    init {
        title = "Text Models"
        iconName = ICON_TEXT_EDITOR

        enableSwitch = SwitchRow().apply {
            title = "Enable text processing"
            subtitle = "Process transcriptions with an LLM for improved results"
            active = viewModel.peekTextProcessingEnabled()
        }

        activeProviderComboRow = ActiveProviderComboRow(
            getSelectedProviderId = { viewModel.peekSelectedTextModelProviderId() },
            setSelectedProviderId = { viewModel.setSelectedTextModelProviderId(it) },
            rowSubtitle = "Select which provider to use for text processing"
        )

        val textProcessingGroup = PreferencesGroup().apply {
            title = "Text Processing"
            description = "(Optional) Process transcriptions using a Large Language Model (LLM) to improve " +
                        "accuracy, grammar, and formatting."
            add(enableSwitch)
            add(activeProviderComboRow)
        }

        addProviderButton = Button.withLabel("Add Provider").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            onClicked { showAddProviderDialog() }
        }

        providersListBox = ListBox().apply {
            addCssClass(STYLE_CLASS_BOXED_LIST)
            marginTop = DEFAULT_BOX_SPACING
            selectionMode = SelectionMode.NONE
        }

        val placeholderLabel = Label("No providers configured").apply {
            addCssClass(STYLE_CLASS_DIM_LABEL)
            halign = Align.CENTER
        }

        placeholderBox = Box(Orientation.VERTICAL, 0).apply {
            vexpand = true
            halign = Align.FILL
            valign = Align.FILL

            // Add expanding spacers above and below to center the label vertically
            append(Box(Orientation.VERTICAL, 0).apply { vexpand = true })
            append(placeholderLabel)
            append(Box(Orientation.VERTICAL, 0).apply { vexpand = true })
        }

        val providersGroup = PreferencesGroup().apply {
            title = "Provider Configurations"
            description = "Configure LLM providers for text processing."
            add(addProviderButton)
            add(providersListBox)
            add(placeholderBox)
        }

        add(textProcessingGroup)
        add(providersGroup)
        loadProviders()
        setupNotifications()
    }

    fun refreshProviders() {
        logger.info("Refreshing text model providers")
        providersListBox.removeAll()
        loadProviders()
    }

    private fun syncEnableSwitch() {
        val enabled = viewModel.peekTextProcessingEnabled()
        if (enableSwitch.active == enabled) return
        suppressEnableSwitchNotify = true
        try {
            enableSwitch.active = enabled
        } finally {
            suppressEnableSwitchNotify = false
        }
    }

    private fun loadProviders() {
        val providers = viewModel.peekTextModelProviders()
        providers.sortedBy { it.name.lowercase() }.forEach { provider -> addProviderToUI(provider) }
        activeProviderComboRow.updateProviders(providers)
        updatePlaceholderVisibility()
        updateActiveProviderSensitivity()
    }

    private fun setupNotifications() {
        activeProviderComboRow.setupNotifications()
        enableSwitch.onNotify("active") {
            if (suppressEnableSwitchNotify) return@onNotify
            val enabled = enableSwitch.active
            logger.info("Text processing enabled: $enabled")
            viewModel.setTextProcessingEnabled(enabled)
            updateActiveProviderSensitivity()
        }
        scope.launch {
            viewModel.settingsChanged
                .filter {
                    it == KEY_CREDENTIALS ||
                        it == KEY_TEXT_PROCESSING_ENABLED ||
                        it == KEY_SELECTED_TEXT_MODEL_PROVIDER_ID ||
                        it == KEY_TEXT_MODEL_PROVIDERS
                }
                .collect {
                    GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                        when (it) {
                            KEY_CREDENTIALS -> {
                                refreshProviders()
                            }
                            KEY_TEXT_PROCESSING_ENABLED -> {
                                syncEnableSwitch()
                                updateActiveProviderSensitivity()
                            }
                            KEY_SELECTED_TEXT_MODEL_PROVIDER_ID,
                            KEY_TEXT_MODEL_PROVIDERS -> {
                                refreshProviders()
                                syncEnableSwitch()
                            }
                        }
                        false
                    }
                }
        }
    }

    private fun addProviderToUI(providerSetting: TextModelProviderSetting) {
        val providerLabel = providerSetting.provider.displayName
        val modelLabel = providerSetting.modelId
        val subtitle = "$providerLabel • $modelLabel"

        val row = ActionRow().apply {
            title = providerSetting.name
            this.subtitle = subtitle
        }

        // Credential indicator
        if (providerSetting.credentialId != null) {
            row.addSuffix(Button.fromIconName(ICON_PASSWORD).apply {
                addCssClass(STYLE_CLASS_FLAT)
                valign = Align.CENTER
                sensitive = false
                tooltipText = "Custom Credentials Set"
            })
        }

        // Base URL indicator
        if (providerSetting.baseUrl != null) {
            row.addSuffix(Button.fromIconName(ICON_SERVER).apply {
                addCssClass(STYLE_CLASS_FLAT)
                valign = Align.CENTER
                sensitive = false
                tooltipText = "Custom URL: ${providerSetting.baseUrl}"
            })
        }

        // Delete button
        val deleteButton = Button.fromIconName(ICON_TRASH).apply {
            addCssClass(STYLE_CLASS_FLAT)
            valign = Align.CENTER
            onClicked {
                providersListBox.remove(row)
                onProviderDeleted(providerSetting.id)
            }
        }

        row.addSuffix(deleteButton)
        providersListBox.append(row)
    }

    private fun onProviderDeleted(providerId: String) {
        val currentProviders = viewModel.peekTextModelProviders()
        val updatedProviders = currentProviders.filter { it.id != providerId }
        logger.info("Removing provider, total is now ${updatedProviders.size} entries.")
        viewModel.setTextModelProviders(updatedProviders)
        activeProviderComboRow.updateProviders(updatedProviders)
        updatePlaceholderVisibility()
        updateActiveProviderSensitivity()
    }

    private fun updatePlaceholderVisibility() {
        val providers = viewModel.peekTextModelProviders()
        val hasProviders = providers.isNotEmpty()
        val atLimit = providers.size >= MAX_TEXT_MODEL_PROVIDERS
        providersListBox.visible = hasProviders
        placeholderBox.visible = !hasProviders
        addProviderButton.sensitive = !atLimit
    }

    private fun updateActiveProviderSensitivity() {
        val textProcessingEnabled = viewModel.peekTextProcessingEnabled()
        val hasProviders = viewModel.peekTextModelProviders().isNotEmpty()
        activeProviderComboRow.sensitive = textProcessingEnabled && hasProviders
    }

    /*
     * Dialog logic
     */

    private fun showAddProviderDialog() {
        val existingNames = viewModel.peekTextModelProviders().map { it.name }.toSet()
        val dialog = AddTextModelProviderDialog(existingNames, viewModel) { provider ->
            onProviderAdded(provider)
        }

        dialog.present(this)
    }

    private fun onProviderAdded(provider: TextModelProviderSetting) {
        val currentProviders = viewModel.peekTextModelProviders()
        val updatedProviders = currentProviders + provider
        logger.info("Adding provider, total is now ${updatedProviders.size} entries.")
        viewModel.setTextModelProviders(updatedProviders)
        addProviderToUI(provider)
        activeProviderComboRow.updateProviders(updatedProviders)
        updatePlaceholderVisibility()
        updateActiveProviderSensitivity()
    }
}
