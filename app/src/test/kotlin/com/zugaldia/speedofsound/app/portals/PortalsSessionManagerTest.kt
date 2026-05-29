package com.zugaldia.speedofsound.app.portals

import com.zugaldia.speedofsound.core.desktop.portals.PortalsSessionClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.KEY_PORTALS_RESTORE_TOKEN
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.stargate.sdk.globalshortcuts.BoundShortcut
import com.zugaldia.stargate.sdk.globalshortcuts.ShortcutActivation
import com.zugaldia.stargate.sdk.remotedesktop.DeviceType
import com.zugaldia.stargate.sdk.remotedesktop.StartResponse
import com.zugaldia.stargate.sdk.session.CreateSessionResponse
import com.zugaldia.stargate.sdk.session.SessionClosedEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `startSession transient failure keeps restore token and stays reconnectable`() = runBlocking {
        val portalsClient = FakePortalsSessionClient(
            createGlobalShortcutsSessionResult = Result.failure<CreateSessionResponse>(
                RuntimeException("No such interface 'org.freedesktop.portal.GlobalShortcuts' on object /org/freedesktop/portal/desktop")
            ),
            startRemoteDesktopSessionResult = Result.failure<StartResponse>(RuntimeException("Temporary D-Bus timeout")),
        )
        val settingsClient = SettingsClient(FakeSettingsStore()).apply {
            setPortalsRestoreToken("smoke-restore-token")
        }
        val manager = PortalsSessionManager(portalsClient, settingsClient)

        manager.initialize(this)
        awaitPortalsCalls(portalsClient, 1)

        assertEquals(RemoteDesktopStatus.NeedToken, manager.remoteDesktopStatus.value)
        assertEquals("smoke-restore-token", settingsClient.loadPortalsRestoreToken())
        assertEquals(listOf<String?>("smoke-restore-token"), portalsClient.requestedRestoreTokens)

        manager.attemptReconnect(this)
        awaitPortalsCalls(portalsClient, 2)

        assertEquals(2, portalsClient.startRemoteDesktopSessionCalls)
        assertEquals(
            listOf<String?>("smoke-restore-token", "smoke-restore-token"),
            portalsClient.requestedRestoreTokens,
        )
    }

    @Test
    fun `startSession missing remote desktop interface clears restore token and disables reconnects`() = runBlocking {
        val portalsClient = FakePortalsSessionClient(
            createGlobalShortcutsSessionResult = Result.failure<CreateSessionResponse>(
                RuntimeException("No such interface 'org.freedesktop.portal.GlobalShortcuts' on object /org/freedesktop/portal/desktop")
            ),
            startRemoteDesktopSessionResult = Result.failure<StartResponse>(
                RuntimeException("No such interface 'org.freedesktop.portal.RemoteDesktop' on object /org/freedesktop/portal/desktop")
            ),
        )
        val settingsClient = SettingsClient(FakeSettingsStore()).apply {
            setPortalsRestoreToken("smoke-restore-token")
        }
        val manager = PortalsSessionManager(portalsClient, settingsClient)

        manager.initialize(this)
        awaitPortalsCalls(portalsClient, 1)

        assertEquals(RemoteDesktopStatus.NotSupported, manager.remoteDesktopStatus.value)
        assertEquals("", settingsClient.loadPortalsRestoreToken())
        assertEquals(listOf<String?>("smoke-restore-token"), portalsClient.requestedRestoreTokens)

        val callsAfterFailure = portalsClient.startRemoteDesktopSessionCalls
        manager.attemptReconnect(this)
        repeat(20) { yield() }

        assertEquals(callsAfterFailure, portalsClient.startRemoteDesktopSessionCalls)
    }

    @Test
    fun `startSession missing remote desktop interface keeps restore token when clearing fails`() = runBlocking {
        val portalsClient = FakePortalsSessionClient(
            createGlobalShortcutsSessionResult = Result.failure<CreateSessionResponse>(
                RuntimeException("No such interface 'org.freedesktop.portal.GlobalShortcuts' on object /org/freedesktop/portal/desktop")
            ),
            startRemoteDesktopSessionResult = Result.failure<StartResponse>(
                RuntimeException("No such interface 'org.freedesktop.portal.RemoteDesktop' on object /org/freedesktop/portal/desktop")
            ),
        )
        val settingsClient = SettingsClient(
            FakeSettingsStore(failOnSetStringKeys = setOf(KEY_PORTALS_RESTORE_TOKEN))
        ).apply {
            setPortalsRestoreToken("smoke-restore-token")
        }
        val manager = PortalsSessionManager(portalsClient, settingsClient)

        manager.initialize(this)
        awaitPortalsCalls(portalsClient, 1)

        assertEquals(RemoteDesktopStatus.NotSupported, manager.remoteDesktopStatus.value)
        assertEquals("smoke-restore-token", settingsClient.loadPortalsRestoreToken())
    }

    @Test
    fun `startSession fresh restore token is preserved in runtime when persistence fails`() = runBlocking {
        val portalsClient = FakePortalsSessionClient(
            createGlobalShortcutsSessionResult = Result.success(CreateSessionResponse(org.freedesktop.dbus.DBusPath("/org/freedesktop/portal/desktop/session/1"))),
            startRemoteDesktopSessionResult = Result.success(
                StartResponse(
                    emptySet<DeviceType>(),
                    true,
                    "fresh-restore-token",
                )
            ),
        )
        val settingsClient = SettingsClient(
            FakeSettingsStore(
                failOnSetStringKeys = setOf(KEY_PORTALS_RESTORE_TOKEN),
            )
        )
        val manager = PortalsSessionManager(portalsClient, settingsClient)

        manager.startSession(this)
        awaitPortalsCalls(portalsClient, 1)
        setPrivateStateFlowValue(manager, "_isSessionDisconnected", true)
        setPrivateStateFlowValue(manager, "_remoteDesktopStatus", RemoteDesktopStatus.Ready)
        manager.attemptReconnect(this)
        awaitPortalsCalls(portalsClient, 2)

        assertEquals(RemoteDesktopStatus.Ready, manager.remoteDesktopStatus.value)
        assertEquals("", settingsClient.loadPortalsRestoreToken())
        assertEquals(
            listOf<String?>(null, "fresh-restore-token"),
            portalsClient.requestedRestoreTokens,
        )
    }
}

private suspend fun awaitPortalsCalls(
    portalsClient: FakePortalsSessionClient,
    expectedCalls: Int,
) {
    repeat(100) {
        if (portalsClient.startRemoteDesktopSessionCalls >= expectedCalls) return
        yield()
    }
    error("Timed out waiting for $expectedCalls remote desktop session attempts")
}

private fun <T> setPrivateStateFlowValue(instance: Any, fieldName: String, value: T) {
    val field = instance.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val stateFlow = field.get(instance) as MutableStateFlow<T>
    stateFlow.value = value
}

private class FakeSettingsStore : SettingsStore {
    private val strings: MutableMap<String, String>
    private val stringArrays = mutableMapOf<String, List<String>>()
    private val booleans = mutableMapOf<String, Boolean>()
    private val ints = mutableMapOf<String, Int>()

    private val failOnSetStringKeys: Set<String>

    constructor(
        initialValues: MutableMap<String, String> = mutableMapOf(),
        failOnSetStringKeys: Set<String> = emptySet(),
    ) {
        this.strings = initialValues
        this.failOnSetStringKeys = failOnSetStringKeys
    }

    override fun isAvailable(): Boolean = true

    override fun getString(key: String, defaultValue: String): String =
        strings[key] ?: defaultValue

    override fun setString(key: String, value: String): Boolean {
        if (key in failOnSetStringKeys) {
            return false
        }
        strings[key] = value
        return true
    }

    override fun getStringArray(key: String, defaultValue: List<String>): List<String> =
        stringArrays[key] ?: defaultValue

    override fun setStringArray(key: String, value: List<String>): Boolean {
        stringArrays[key] = value
        return true
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        booleans[key] ?: defaultValue

    override fun setBoolean(key: String, value: Boolean): Boolean {
        booleans[key] = value
        return true
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        ints[key] ?: defaultValue

    override fun setInt(key: String, value: Int): Boolean {
        ints[key] = value
        return true
    }
}

private class FakePortalsSessionClient(
    private val createGlobalShortcutsSessionResult: Result<CreateSessionResponse>,
    private val startRemoteDesktopSessionResult: Result<StartResponse>,
) : PortalsSessionClient {
    var registerApplicationCalls = 0
    var createGlobalShortcutsSessionCalls = 0
    var bindGlobalShortcutsCalls = 0
    var startRemoteDesktopSessionCalls = 0
    val requestedRestoreTokens = mutableListOf<String?>()

    override val isPortalAvailable: Boolean = true
    override val sessionClosedEvents: Flow<SessionClosedEvent> = emptyFlow()

    override suspend fun registerApplication(): Result<Unit> {
        registerApplicationCalls += 1
        return Result.success(Unit)
    }

    override suspend fun startRemoteDesktopSession(restoreToken: String?): Result<StartResponse> {
        startRemoteDesktopSessionCalls += 1
        requestedRestoreTokens += restoreToken
        return startRemoteDesktopSessionResult
    }

    override suspend fun createGlobalShortcutsSession(): Result<CreateSessionResponse> {
        createGlobalShortcutsSessionCalls += 1
        return createGlobalShortcutsSessionResult
    }

    override suspend fun bindGlobalShortcuts(): Result<List<BoundShortcut>> {
        bindGlobalShortcutsCalls += 1
        return Result.success(emptyList())
    }

    override fun observeShortcutActivated(): Flow<ShortcutActivation> = emptyFlow()
}
