package com.zugaldia.speedofsound.app.screens.preferences.shared

import com.zugaldia.speedofsound.core.desktop.settings.SelectableProviderSetting

internal fun <T : SelectableProviderSetting> resolveSelectedProviderIndex(
    savedProviderId: String,
    providers: List<T>,
): Int =
    providers.indexOfFirst { it.id == savedProviderId }

internal fun <T : SelectableProviderSetting> restoreSelectedProviderIndex(
    savedProviderId: String,
    providers: List<T>,
): Int =
    resolveSelectedProviderIndex(savedProviderId, providers).takeIf { it >= 0 } ?: 0
