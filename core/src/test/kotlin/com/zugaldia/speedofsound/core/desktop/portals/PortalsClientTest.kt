package com.zugaldia.speedofsound.core.desktop.portals

import com.zugaldia.stargate.sdk.DesktopPortal
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
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
        assertFalse(client.isPortalAvailable)
        assertEquals(1, attempts)
    }

    @Test
    fun `close closes in-flight connection and prevents portal reuse`() {
        val connectStarted = CountDownLatch(1)
        val connectCanFinish = CountDownLatch(1)
        val closeCount = AtomicInteger(0)
        val dummyPortal = unsafeAllocateDesktopPortal()
        val client = PortalsClient(
            portalConnector = {
                connectStarted.countDown()
                connectCanFinish.await(1, TimeUnit.SECONDS)
                Result.success(dummyPortal)
            },
            portalCloser = { closeCount.incrementAndGet() }
        )

        var initialAvailability = false
        val connectThread = Thread {
            initialAvailability = client.isPortalAvailable
        }
        connectThread.start()

        assertTrue(connectStarted.await(1, TimeUnit.SECONDS))

        val closeThread = Thread { client.close() }
        closeThread.start()
        connectCanFinish.countDown()

        closeThread.join(1_000)
        connectThread.join(1_000)

        assertTrue(initialAvailability)
        assertFalse(client.isPortalAvailable)
        assertEquals(1, closeCount.get())
    }

    private fun unsafeAllocateDesktopPortal(): DesktopPortal {
        val unsafeField: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        return unsafe.allocateInstance(DesktopPortal::class.java) as DesktopPortal
    }
}
