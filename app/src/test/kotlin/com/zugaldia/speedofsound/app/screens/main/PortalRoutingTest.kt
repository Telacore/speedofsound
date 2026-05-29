package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_CLIPBOARD
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_PORTAL
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class PortalRoutingTest {

    @Test
    fun `shouldForceClipboardFallback returns true when portal is requested but unavailable`() {
        assertTrue(
            shouldForceClipboardFallback(
                textOutputMethod = TEXT_OUTPUT_METHOD_PORTAL,
                isPortalAvailable = false,
            )
        )
    }

    @Test
    fun `shouldForceClipboardFallback returns false when portal is available`() {
        assertFalse(
            shouldForceClipboardFallback(
                textOutputMethod = TEXT_OUTPUT_METHOD_PORTAL,
                isPortalAvailable = true,
            )
        )
    }

    @Test
    fun `shouldForceClipboardFallback returns false when clipboard is requested`() {
        assertFalse(
            shouldForceClipboardFallback(
                textOutputMethod = TEXT_OUTPUT_METHOD_CLIPBOARD,
                isPortalAvailable = false,
            )
        )
    }

    @Test
    fun `shouldAttemptPortalReconnect only returns true for portal output`() {
        assertTrue(shouldAttemptPortalReconnect(TEXT_OUTPUT_METHOD_PORTAL))
        assertFalse(shouldAttemptPortalReconnect(TEXT_OUTPUT_METHOD_CLIPBOARD))
    }

    @Test
    fun `shouldAutoStartPortalSession returns true when portal is selected and session is not ready`() {
        assertTrue(
            shouldAutoStartPortalSession(
                textOutputMethod = TEXT_OUTPUT_METHOD_PORTAL,
                remoteDesktopStatus = com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.NeedToken,
                isPortalAvailable = true,
            )
        )
    }

    @Test
    fun `shouldAutoStartPortalSession returns false when clipboard is selected`() {
        assertFalse(
            shouldAutoStartPortalSession(
                textOutputMethod = TEXT_OUTPUT_METHOD_CLIPBOARD,
                remoteDesktopStatus = com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.NotSupported,
                isPortalAvailable = true,
            )
        )
    }

    @Test
    fun `shouldAutoStartPortalSession returns false when session is already ready`() {
        assertFalse(
            shouldAutoStartPortalSession(
                textOutputMethod = TEXT_OUTPUT_METHOD_PORTAL,
                remoteDesktopStatus = com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.Ready,
                isPortalAvailable = true,
            )
        )
    }

    @Test
    fun `shouldPersistClipboardFallback returns true for persistent fallback`() {
        assertTrue(shouldPersistClipboardFallback(ClipboardFallbackPolicy.PERSIST_PREFERENCE))
    }

    @Test
    fun `shouldPersistClipboardFallback returns false for runtime fallback`() {
        assertFalse(shouldPersistClipboardFallback(ClipboardFallbackPolicy.RUNTIME_ONLY))
    }

    @Test
    fun `resolveRemoteDesktopUiStatus maps clipboard mode to ready`() {
        assertEquals(
            com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.Ready,
            resolveRemoteDesktopUiStatus(
                textOutputMethod = TEXT_OUTPUT_METHOD_CLIPBOARD,
                remoteDesktopStatus = com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.NeedToken,
            )
        )
    }

    @Test
    fun `resolveRemoteDesktopUiStatus preserves portal status in portal mode`() {
        assertEquals(
            com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.NotSupported,
            resolveRemoteDesktopUiStatus(
                textOutputMethod = TEXT_OUTPUT_METHOD_PORTAL,
                remoteDesktopStatus = com.zugaldia.speedofsound.app.portals.RemoteDesktopStatus.NotSupported,
            )
        )
    }
}
