package com.zugaldia.speedofsound.core.desktop.portals

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class PortalsClientTest {

    @Test
    fun `isPortalAvailable retries connector after failed attempt`() {
        var attempts = 0
        val client = PortalsClient(
            portalConnector = {
                attempts += 1
                Result.failure<com.zugaldia.stargate.sdk.DesktopPortal>(
                    RuntimeException("Desktop portal is temporarily unavailable")
                )
            }
        )

        assertFalse(client.isPortalAvailable)
        assertFalse(client.isPortalAvailable)
        assertEquals(2, attempts)
    }
}
