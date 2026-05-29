package com.zugaldia.speedofsound.app.screens.preferences.credentials

import com.zugaldia.speedofsound.app.DEFAULT_ADD_CREDENTIAL_DIALOG_HEIGHT
import com.zugaldia.speedofsound.app.DEFAULT_ADD_CREDENTIAL_DIALOG_WIDTH
import com.zugaldia.speedofsound.app.DEFAULT_BOX_SPACING
import com.zugaldia.speedofsound.app.DEFAULT_MARGIN
import com.zugaldia.speedofsound.app.MAX_CREDENTIAL_NAME_LENGTH
import com.zugaldia.speedofsound.app.MAX_CREDENTIALS
import com.zugaldia.speedofsound.app.MAX_CREDENTIAL_VALUE_LENGTH
import com.zugaldia.speedofsound.app.STYLE_CLASS_SUGGESTED_ACTION
import com.zugaldia.speedofsound.app.ADW_MAX_LENGTH_MIN_MAJOR_VERSION
import com.zugaldia.speedofsound.app.ADW_MAX_LENGTH_MIN_MINOR_VERSION
import com.zugaldia.speedofsound.app.isAdwVersionAtLeast
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.desktop.settings.KEY_CREDENTIALS
import com.zugaldia.speedofsound.core.desktop.settings.CredentialSetting
import com.zugaldia.speedofsound.core.desktop.settings.CredentialType
import com.zugaldia.speedofsound.core.generateUniqueId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.gnome.adw.Dialog
import org.gnome.adw.EntryRow
import org.gnome.adw.PasswordEntryRow
import org.gnome.adw.PreferencesGroup
import org.gnome.gtk.Align
import org.gnome.glib.GLib
import org.gnome.gtk.Box
import org.gnome.gtk.Button
import org.gnome.gtk.Orientation
import org.slf4j.LoggerFactory

class AddCredentialDialog(
    private val viewModel: PreferencesViewModel,
    private val onCredentialAdded: (CredentialSetting) -> Boolean
) : Dialog() {
    private val logger = LoggerFactory.getLogger(AddCredentialDialog::class.java)
    private val dialogScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val nameEntry: EntryRow
    private val apiKeyEntry: PasswordEntryRow
    private val addButton: Button
    private var currentCredentials: List<CredentialSetting> = emptyList()

    init {
        title = "Add Credential"
        contentWidth = DEFAULT_ADD_CREDENTIAL_DIALOG_WIDTH
        contentHeight = DEFAULT_ADD_CREDENTIAL_DIALOG_HEIGHT

        val supportsMaxLength = isAdwVersionAtLeast(ADW_MAX_LENGTH_MIN_MAJOR_VERSION, ADW_MAX_LENGTH_MIN_MINOR_VERSION)

        nameEntry = EntryRow().apply {
            title = "Name"
            if (supportsMaxLength) maxLength = MAX_CREDENTIAL_NAME_LENGTH
        }

        apiKeyEntry = PasswordEntryRow().apply {
            title = "API Key"
            if (supportsMaxLength) maxLength = MAX_CREDENTIAL_VALUE_LENGTH
        }

        val preferencesGroup = PreferencesGroup().apply {
            title = "Add Credential"
            description = "Use descriptive names like \"Anthropic (Work)\" or \"Gemini (Personal)\""
            vexpand = true
            add(nameEntry)
            add(apiKeyEntry)
        }

        val cancelButton = Button.withLabel("Cancel").apply {
            onClicked { close() }
        }

        addButton = Button.withLabel("Add").apply {
            addCssClass(STYLE_CLASS_SUGGESTED_ACTION)
            sensitive = false
            onClicked {
                val name = nameEntry.text.trim()
                val apiKey = apiKeyEntry.text.trim()
                val valid = when {
                    name.isEmpty() || apiKey.isEmpty() -> false
                    name.length > MAX_CREDENTIAL_NAME_LENGTH -> {
                        logger.warn("Credential name too long: ${name.length} > $MAX_CREDENTIAL_NAME_LENGTH")
                        false
                    }
                    apiKey.length > MAX_CREDENTIAL_VALUE_LENGTH -> {
                        logger.warn("Credential value too long: ${apiKey.length} > $MAX_CREDENTIAL_VALUE_LENGTH")
                        false
                    }
                    currentCredentials.size >= MAX_CREDENTIALS -> {
                        logger.warn("Credential limit reached: $MAX_CREDENTIALS")
                        false
                    }
                    currentCredentials.any { it.name == name } -> {
                        logger.warn("Credential name already exists: $name")
                        false
                    }
                    else -> true
                }
                if (valid) {
                    val credential = CredentialSetting(
                        id = generateUniqueId(),
                        type = CredentialType.API_KEY,
                        name = name,
                        value = apiKey
                    )
                    if (onCredentialAdded(credential)) {
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

        val contentBox = Box(Orientation.VERTICAL, DEFAULT_BOX_SPACING).apply {
            marginTop = DEFAULT_MARGIN
            marginBottom = DEFAULT_MARGIN
            marginStart = DEFAULT_MARGIN
            marginEnd = DEFAULT_MARGIN
            vexpand = true
            append(preferencesGroup)
            append(buttonBox)
        }

        child = contentBox
        nameEntry.onNotify("text") {
            val name = nameEntry.text.trim()
            val apiKey = apiKeyEntry.text.trim()
            addButton.sensitive = when {
                name.isEmpty() || apiKey.isEmpty() -> false
                name.length > MAX_CREDENTIAL_NAME_LENGTH -> {
                    logger.warn("Credential name too long: ${name.length} > $MAX_CREDENTIAL_NAME_LENGTH")
                    false
                }
                apiKey.length > MAX_CREDENTIAL_VALUE_LENGTH -> {
                    logger.warn("Credential value too long: ${apiKey.length} > $MAX_CREDENTIAL_VALUE_LENGTH")
                    false
                }
                currentCredentials.size >= MAX_CREDENTIALS -> {
                    logger.warn("Credential limit reached: $MAX_CREDENTIALS")
                    false
                }
                currentCredentials.any { it.name == name } -> {
                    logger.warn("Credential name already exists: $name")
                    false
                }
                else -> true
            }
        }
        apiKeyEntry.onNotify("text") {
            val name = nameEntry.text.trim()
            val apiKey = apiKeyEntry.text.trim()
            addButton.sensitive = when {
                name.isEmpty() || apiKey.isEmpty() -> false
                name.length > MAX_CREDENTIAL_NAME_LENGTH -> {
                    logger.warn("Credential name too long: ${name.length} > $MAX_CREDENTIAL_NAME_LENGTH")
                    false
                }
                apiKey.length > MAX_CREDENTIAL_VALUE_LENGTH -> {
                    logger.warn("Credential value too long: ${apiKey.length} > $MAX_CREDENTIAL_VALUE_LENGTH")
                    false
                }
                currentCredentials.size >= MAX_CREDENTIALS -> {
                    logger.warn("Credential limit reached: $MAX_CREDENTIALS")
                    false
                }
                currentCredentials.any { it.name == name } -> {
                    logger.warn("Credential name already exists: $name")
                    false
                }
                else -> true
            }
        }
        currentCredentials = viewModel.peekCredentials()
        dialogScope.launch {
            viewModel.settingsChanged
                .filter { it == KEY_CREDENTIALS }
                .collect {
                    GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                        currentCredentials = viewModel.peekCredentials()
                        val name = nameEntry.text.trim()
                        val apiKey = apiKeyEntry.text.trim()
                        addButton.sensitive = when {
                            name.isEmpty() || apiKey.isEmpty() -> false
                            name.length > MAX_CREDENTIAL_NAME_LENGTH -> {
                                logger.warn("Credential name too long: ${name.length} > $MAX_CREDENTIAL_NAME_LENGTH")
                                false
                            }
                            apiKey.length > MAX_CREDENTIAL_VALUE_LENGTH -> {
                                logger.warn("Credential value too long: ${apiKey.length} > $MAX_CREDENTIAL_VALUE_LENGTH")
                                false
                            }
                            currentCredentials.size >= MAX_CREDENTIALS -> {
                                logger.warn("Credential limit reached: $MAX_CREDENTIALS")
                                false
                            }
                            currentCredentials.any { it.name == name } -> {
                                logger.warn("Credential name already exists: $name")
                                false
                            }
                            else -> true
                        }
                        false
                    }
                }
        }
        onClosed { dialogScope.cancel() }
    }

}
