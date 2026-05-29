package com.zugaldia.speedofsound.app.screens.preferences.credentials

import com.zugaldia.speedofsound.app.CREDENTIAL_MASK_PREFIX_LENGTH
import com.zugaldia.speedofsound.app.CREDENTIAL_MASK_SUFFIX_LENGTH
import com.zugaldia.speedofsound.app.ICON_SERVER
import com.zugaldia.speedofsound.app.ICON_TRASH
import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.MIN_CREDENTIAL_LENGTH_FOR_MASKING
import com.zugaldia.speedofsound.app.STYLE_CLASS_BOXED_LIST
import com.zugaldia.speedofsound.app.STYLE_CLASS_DIM_LABEL
import com.zugaldia.speedofsound.app.STYLE_CLASS_FLAT
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.MAX_CREDENTIALS
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.gnome.adw.ActionRow
import org.gnome.adw.PreferencesGroup
import org.gnome.adw.PreferencesPage
import org.gnome.gtk.Align
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Label
import org.gnome.gtk.ListBox
import org.gnome.gtk.Orientation
import org.gnome.gtk.SelectionMode
import org.gnome.glib.GLib
import org.slf4j.LoggerFactory

class CloudCredentialsPage(private val viewModel: PreferencesViewModel) : PreferencesPage() {
    private val logger = LoggerFactory.getLogger(CloudCredentialsPage::class.java)
    private val scope = viewModel.viewModelScope

    private val credentialsListBox: ListBox
    private val placeholderBox: Box
    private val addButton: Button

    init {
        title = "Cloud Credentials"
        iconName = ICON_SERVER

        addButton = Button.withLabel("Add API Key").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            onClicked { showAddCredentialDialog() }
        }

        credentialsListBox = ListBox().apply {
            addCssClass(STYLE_CLASS_BOXED_LIST)
            marginTop = DEFAULT_BOX_SPACING
            selectionMode = SelectionMode.NONE
        }

        val placeholderLabel = Label("No cloud credentials configured").apply {
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

        val credentialsGroup = PreferencesGroup().apply {
            title = "Cloud Credentials"
            description = "(Optional) Add credentials for cloud services like Anthropic, Google, or OpenAI. " +
                    "These credentials can be referenced when adding Voice and Text Models."
            add(addButton)
            add(credentialsListBox)
            add(placeholderBox)
        }

        add(credentialsGroup)
        loadCredentials()
        setupNotifications()
    }

    private fun loadCredentials() {
        val credentials = viewModel.peekCredentials()
        credentials.sortedBy { it.name.lowercase() }.forEach { credential -> addCredentialToUI(credential) }
        updatePlaceholderVisibility()
    }

    private fun setupNotifications() {
        scope.launch {
            viewModel.settingsChanged
                .filter { it == KEY_CREDENTIALS }
                .collect {
                    GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                        refresh()
                        false
                    }
                }
        }
    }

    fun refresh() {
        logger.info("Refreshing cloud credentials")
        credentialsListBox.removeAll()
        loadCredentials()
    }

    private fun showAddCredentialDialog() {
        val dialog = AddCredentialDialog(viewModel) { credential -> onCredentialAdded(credential) }
        dialog.present(this)
    }

    private fun onCredentialAdded(credential: CredentialSetting) {
        val currentCredentials = viewModel.peekCredentials()
        if (currentCredentials.size >= MAX_CREDENTIALS) {
            logger.warn("Cannot add credential: limit of $MAX_CREDENTIALS reached")
            return
        }

        val exists = currentCredentials.any { it.name == credential.name }
        if (exists) {
            logger.warn("Credential with name '${credential.name}' already exists")
            return
        }

        val updatedCredentials = currentCredentials + credential
        logger.info("Adding credential, total is now ${updatedCredentials.size} entries.")
        viewModel.setCredentials(updatedCredentials)
        addCredentialToUI(credential)
        updatePlaceholderVisibility()
    }

    private fun addCredentialToUI(credential: CredentialSetting) {
        val maskedValue = maskCredentialValue(credential.value)
        val row = ActionRow().apply {
            title = credential.name
            subtitle = maskedValue
        }

        val deleteButton = Button.fromIconName(ICON_TRASH).apply {
            addCssClass(STYLE_CLASS_FLAT)
            valign = Align.CENTER
        }

        row.addSuffix(deleteButton)
        credentialsListBox.append(row)
        deleteButton.onClicked {
            if (onCredentialDeleted(credential.id, credential.name)) {
                credentialsListBox.remove(row)
            }
        }
    }

    private fun maskCredentialValue(value: String): String =
        if (value.length < MIN_CREDENTIAL_LENGTH_FOR_MASKING) {
            "..."
        } else {
            "${value.take(CREDENTIAL_MASK_PREFIX_LENGTH)}...${value.takeLast(CREDENTIAL_MASK_SUFFIX_LENGTH)}"
        }

    private fun onCredentialDeleted(credentialId: String, credentialName: String): Boolean {
        val currentCredentials = viewModel.peekCredentials()
        val credentialToDelete = currentCredentials.find { it.id == credentialId }
        if (credentialToDelete != null) {
            val textProviders = viewModel.peekTextModelProviders()
            val voiceProviders = viewModel.peekVoiceModelProviders()
            val referencingProviders = buildList {
                addAll(textProviders.filter { it.credentialId == credentialToDelete.id }.map {
                    "Text: ${it.name}"
                })
                addAll(voiceProviders.filter { it.credentialId == credentialToDelete.id }.map {
                    "Voice: ${it.name}"
                })
            }
            if (referencingProviders.isNotEmpty()) {
                val providerNames = referencingProviders.joinToString(", ")
                logger.warn("Cannot delete credential '$credentialName': used by providers: $providerNames")
                return false
            }
        }

        // Proceed with deletion
        val updatedCredentials = currentCredentials.filter { it.id != credentialId }
        logger.info("Removing credential '$credentialName', total is now ${updatedCredentials.size} entries.")
        viewModel.setCredentials(updatedCredentials)
        updatePlaceholderVisibility()
        return true
    }

    private fun updatePlaceholderVisibility() {
        val credentials = viewModel.peekCredentials()
        val hasCredentials = credentials.isNotEmpty()
        val atLimit = credentials.size >= MAX_CREDENTIALS
        credentialsListBox.visible = hasCredentials
        placeholderBox.visible = !hasCredentials
        addButton.sensitive = !atLimit
        if (atLimit) {
            logger.info("Credential limit of $MAX_CREDENTIALS reached")
        }
    }
}
