package com.zugaldia.speedofsound.app.portals

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortalsSessionManagerTest {

    @Test
    fun `isMissingRemoteDesktopPortalInterface detects german dbus error message`() {
        val error = RuntimeException(
            "Keine derartige Schnittstelle »org.freedesktop.portal.RemoteDesktop« " +
                "des Objekts im Pfad /org/freedesktop/portal/desktop"
        )

        assertTrue(isMissingRemoteDesktopPortalInterface(error))
    }

    @Test
    fun `isMissingRemoteDesktopPortalInterface detects english dbus error message`() {
        val error = RuntimeException(
            "No such interface 'org.freedesktop.portal.remotedesktop' on object /org/freedesktop/portal/desktop"
        )

        assertTrue(isMissingRemoteDesktopPortalInterface(error))
    }

    @Test
    fun `isMissingRemoteDesktopPortalInterface detects german missing object message`() {
        val error = RuntimeException(
            "Kein zugehöriges Objekt für 'org.freedesktop.portal.RemoteDesktop' " +
                "unter /org/freedesktop/portal/desktop"
        )

        assertTrue(isMissingRemoteDesktopPortalInterface(error))
    }

    @Test
    fun `isMissingRemoteDesktopPortalInterface detects mixed case english dbus error message`() {
        val error = RuntimeException(
            "No Such Interface 'org.freedesktop.portal.RemoteDesktop' on object /org/freedesktop/portal/desktop"
        )

        assertTrue(isMissingRemoteDesktopPortalInterface(error))
    }

    @Test
    fun `isMissingRemoteDesktopPortalInterface ignores unrelated messages`() {
        val error = RuntimeException("Unable to open microphone permissions due to transient dbus timeout")

        assertFalse(isMissingRemoteDesktopPortalInterface(error))
    }
}
