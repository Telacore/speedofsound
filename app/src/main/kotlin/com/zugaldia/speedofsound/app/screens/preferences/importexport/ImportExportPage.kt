package com.zugaldia.speedofsound.app.screens.preferences.importexport

import com.zugaldia.speedofsound.app.ICON_SEND
import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.getDataDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.gnome.adw.ActionRow
import org.gnome.adw.PreferencesGroup
import org.gnome.adw.PreferencesPage
import org.gnome.glib.GLib
import org.gnome.gtk.Align
import org.gnome.gtk.Button
import org.gnome.gtk.Label

class ImportExportPage(viewModel: PreferencesViewModel, private val onImportSuccess: () -> Unit) : PreferencesPage() {
    private val manager = ImportExportManager(viewModel)
    private val pageScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val statusLabel: Label
    private val exportButton: Button
    private val importButton: Button

    init {
        title = "Import / Export"
        iconName = ICON_SEND

        val exportFilePath = getDataDir().resolve(ImportExportManager.EXPORT_FILENAME)

        exportButton = Button.withLabel("Export").apply {
            valign = Align.CENTER
        }

        val exportRow = ActionRow().apply {
            title = "Export Preferences"
            subtitle = "File: $exportFilePath"
            addSuffix(exportButton)
        }

        val exportGroup = PreferencesGroup().apply {
            title = "Export"
            description = "Export your preferences to a file. Use it as a backup or to transfer your " +
                    "configuration and alarm scheduler state to a different machine."
            add(exportRow)
        }

        importButton = Button.withLabel("Import").apply {
            valign = Align.CENTER
        }

        val importRow = ActionRow().apply {
            title = "Import Preferences"
            subtitle = "File: $exportFilePath"
            addSuffix(importButton)
        }

        val importGroup = PreferencesGroup().apply {
            title = "Import"
            description = "Import preferences from a file. Items such as credentials, " +
                    "providers, vocabulary, alarms, and alarm scheduler state are added to your existing ones. " +
                    "Some items such as language and custom context will be replaced."
            add(importRow)
        }

        statusLabel = Label("").apply {
            selectable = true
            wrap = true
            halign = Align.CENTER
            valign = Align.CENTER
            hexpand = true
            vexpand = true
            visible = false
        }

        val statusGroup = PreferencesGroup().apply {
            add(statusLabel)
        }

        add(exportGroup)
        add(importGroup)
        add(statusGroup)

        exportButton.onClicked {
            exportButton.sensitive = false
            importButton.sensitive = false
            pageScope.launch {
                val result = manager.export()
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    exportButton.sensitive = true
                    importButton.sensitive = true
                    result.fold(
                        onSuccess = { filePath ->
                            statusLabel.label = "Exported to: $filePath"
                            statusLabel.visible = true
                        },
                        onFailure = { error ->
                            statusLabel.label = "Export failed: ${error.message}"
                            statusLabel.visible = true
                        }
                    )
                    false
                }
            }
        }
        importButton.onClicked {
            exportButton.sensitive = false
            importButton.sensitive = false
            pageScope.launch {
                val result = manager.importSettings()
                GLib.idleAdd(GLib.PRIORITY_DEFAULT) {
                    exportButton.sensitive = true
                    importButton.sensitive = true
                    result.fold(
                        onSuccess = { importResult ->
                            val parts = mutableListOf<String>()
                            if (importResult.alarmsAdded > 0) parts.add("${importResult.alarmsAdded} alarm(s)")
                            if (importResult.credentialsAdded > 0) parts.add("${importResult.credentialsAdded} credential(s)")
                            if (importResult.voiceProvidersAdded > 0) parts.add("${importResult.voiceProvidersAdded} voice provider(s)")
                            if (importResult.textProvidersAdded > 0) parts.add("${importResult.textProvidersAdded} text provider(s)")
                            if (importResult.vocabularyWordsAdded > 0) parts.add("${importResult.vocabularyWordsAdded} vocabulary word(s)")
                            if (importResult.alarmSchedulerStateImported) parts.add("alarm scheduler state")
                            val summary = if (parts.isEmpty()) {
                                "Import complete. No new items to add."
                            } else {
                                "Imported: ${parts.joinToString(", ")}."
                            }
                            statusLabel.label = summary
                            statusLabel.visible = true
                            onImportSuccess()
                        },
                        onFailure = { error ->
                            statusLabel.label = "Import failed: ${error.message}"
                            statusLabel.visible = true
                        }
                    )
                    false
                }
            }
        }
    }

    fun shutdown() {
        pageScope.cancel()
    }
}
