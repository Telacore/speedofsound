package com.zugaldia.speedofsound.core.desktop.portals

import com.zugaldia.stargate.sdk.DesktopPortal
import java.lang.reflect.Field
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `close clears cached portal and forces a fresh reconnect`() {
        var attempts = 0
        val dummyPortal = unsafeAllocateDesktopPortal()
        val client = PortalsClient(
            portalConnector = {
                attempts += 1
                Result.success(dummyPortal)
            },
            portalCloser = { }
        )

        assertTrue(client.isPortalAvailable)
        client.close()
        assertTrue(client.isPortalAvailable)
        assertEquals(2, attempts)
    }

    private fun unsafeAllocateDesktopPortal(): DesktopPortal {
        val unsafeField: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        return unsafe.allocateInstance(DesktopPortal::class.java) as DesktopPortal
    }
}
