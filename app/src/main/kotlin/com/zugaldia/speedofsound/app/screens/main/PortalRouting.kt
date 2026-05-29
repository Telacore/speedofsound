package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus
import com.zugaldia.speedofsound.app.plugins.textoutput.PortalTextOutput
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_CLIPBOARD
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_PORTAL

internal fun shouldForceClipboardFallback(
    textOutputMethod: String,
    isPortalAvailable: Boolean,
): Boolean = textOutputMethod == TEXT_OUTPUT_METHOD_PORTAL && !isPortalAvailable

internal fun shouldAttemptPortalReconnect(textOutputMethod: String): Boolean =
    textOutputMethod == TEXT_OUTPUT_METHOD_PORTAL

internal fun shouldAutoStartPortalSession(
    textOutputMethod: String,
    remoteDesktopStatus: RemoteDesktopStatus,
    isPortalAvailable: Boolean,
): Boolean = textOutputMethod == TEXT_OUTPUT_METHOD_PORTAL &&
    isPortalAvailable &&
    remoteDesktopStatus == RemoteDesktopStatus.NeedToken

internal fun resolveRemoteDesktopUiStatus(
    textOutputMethod: String,
    remoteDesktopStatus: RemoteDesktopStatus,
): RemoteDesktopStatus = if (textOutputMethod == TEXT_OUTPUT_METHOD_CLIPBOARD) {
    RemoteDesktopStatus.Ready
} else {
    remoteDesktopStatus
}

internal enum class ClipboardFallbackPolicy {
    PERSIST_PREFERENCE,
    RUNTIME_ONLY,
}

internal fun shouldPersistClipboardFallback(policy: ClipboardFallbackPolicy): Boolean =
    policy == ClipboardFallbackPolicy.PERSIST_PREFERENCE

internal fun shouldRestorePortalOutput(
    textOutputMethod: String,
    activeTextOutputId: String?,
    remoteDesktopStatus: RemoteDesktopStatus,
    isPortalAvailable: Boolean,
): Boolean = textOutputMethod == TEXT_OUTPUT_METHOD_PORTAL &&
    isPortalAvailable &&
    remoteDesktopStatus != RemoteDesktopStatus.NotSupported &&
    activeTextOutputId != PortalTextOutput.ID
