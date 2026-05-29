package com.zugaldia.speedofsound.app.screens.preferences.shared

import com.zugaldia.speedofsound.core.desktop.settings.SelectableProviderSetting
import org.gnome.adw.ComboRow
import org.gnome.gtk.StringList
import org.slf4j.LoggerFactory

/**
 * Generic ComboRow for selecting the active model provider from the list of configured providers.
 *
 * @param T The provider setting type that implements SelectableProviderSetting
 */
class ActiveProviderComboRow<T>(
    private val getSelectedProviderId: () -> String,
    private val setSelectedProviderId: (String) -> Boolean,
    private val shouldPersistFallbackSelection: (String, List<T>) -> Boolean = { _, _ -> true },
    private val rowSubtitle: String
) : ComboRow() where T : SelectableProviderSetting {
    private val logger = LoggerFactory.getLogger(ActiveProviderComboRow::class.java)

    private var providers: List<T> = emptyList()

    // Prevents onNotify("selected") from firing while rebuilding the model, which would
    // overwrite the saved selection before we can restore it
    private var isUpdating = false

    init {
        title = "Active Provider"
        subtitle = rowSubtitle
        useSubtitle = true
        enableSearch = false
    }

    /**
     * Update the list of available providers and refresh the UI.
     */
    fun updateProviders(newProviders: List<T>) {
        isUpdating = true
        try {
            providers = newProviders.sortedBy { it.name.lowercase() }
            if (providers.isEmpty()) {
                model = StringList(arrayOf())
                visible = false
                return
            }

            visible = true
            val providerNames = providers.map { it.name }.toTypedArray()
            model = StringList(providerNames)

            val savedProviderId = getSelectedProviderId()
            val selectedIndex = resolveSelectedIndex(savedProviderId)
            if (selectedIndex >= 0) {
                selected = selectedIndex
            } else if (providers.isNotEmpty()) {
                selected = 0 // Select the first provider
                if (shouldPersistFallbackSelection(savedProviderId, providers) && !setSelectedProviderId(providers[0].id)) {
                    logger.warn("Failed to persist fallback provider selection to ${providers[0].id}")
                }
            }
        } finally {
            isUpdating = false
        }
    }

    /**
     * Set up notification callbacks. Call this after all widgets are initialized.
     */
    fun setupNotifications() {
        onNotify("selected") {
            if (isUpdating || providers.isEmpty()) return@onNotify
            val selectedIndex = selected
            if (selectedIndex in providers.indices) {
                val selectedProvider = providers[selectedIndex]
                logger.info("Selected model provider changed to ${selectedProvider.name}")
                if (!setSelectedProviderId(selectedProvider.id)) {
                    logger.warn("Failed to persist provider selection to ${selectedProvider.id}, restoring previous selection")
                    isUpdating = true
                    try {
                        selected = restoreSelectedIndex()
                    } finally {
                        isUpdating = false
                    }
                }
            }
        }
    }

    private fun resolveSelectedIndex(savedProviderId: String): Int =
        providers.indexOfFirst { it.id == savedProviderId }

    private fun restoreSelectedIndex(): Int =
        resolveSelectedIndex(getSelectedProviderId()).takeIf { it >= 0 } ?: 0
}
