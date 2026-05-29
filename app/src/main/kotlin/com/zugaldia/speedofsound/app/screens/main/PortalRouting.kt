package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_CLIPBOARD
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_PORTAL

internal fun shouldForceClipboardFallback(
    textOutputMethod: String,
    isPortalAvailable: Boolean,
): Boolean = textOutputMethod == TEXT_OUTPUT_METHOD_PORTAL && !isPortalAvailable

internal fun shouldAttemptPortalReconnect(textOutputMethod: String): Boolean =
    textOutputMethod == TEXT_OUTPUT_METHOD_PORTAL

