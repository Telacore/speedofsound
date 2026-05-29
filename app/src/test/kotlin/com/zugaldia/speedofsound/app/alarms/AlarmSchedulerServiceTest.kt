package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_SCHEDULER_STATE
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AlarmSchedulerServiceTest {
    @Test
    fun `scheduler state reloads when settings change`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 9, minute = 30),
            )
        )
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-28"),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-28"),
            ),
            service.snapshotSchedulerState()
        )

        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:45:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            )
        )

        service.onSettingsChanged(KEY_ALARM_SCHEDULER_STATE)

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:45:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            service.snapshotSchedulerState()
        )
    }

    @Test
    fun `reloading alarms drops history for removed alarms only`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 9, minute = 30),
                AlarmSetting(id = "alarm-2", hour = 10, minute = 0),
            )
        )
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-28",
                    "alarm-2" to "2026-05-28",
                ),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()

        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-2", hour = 10, minute = 0),
            )
        )

        service.reloadAlarms()

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf("alarm-2" to "2026-05-28"),
            ),
            service.snapshotSchedulerState()
        )
    }

    @Test
    fun `reloading unchanged alarms does not write scheduler state`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 9, minute = 30),
            )
        )
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-28"),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()

        val writesBefore = store.stringWriteCount

        service.reloadAlarms()

        assertEquals(writesBefore, store.stringWriteCount)
    }

    @Test
    fun `reloading unchanged scheduler state does not write back to store`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 9, minute = 30),
            )
        )
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-28"),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()

        val writesBefore = store.stringWriteCount

        service.onSettingsChanged(KEY_ALARM_SCHEDULER_STATE)

        assertEquals(writesBefore, store.stringWriteCount)
    }

    @Test
    fun `reloading malformed alarm data does not heal the store from scheduler`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to "{bad",
                com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_SCHEDULER_STATE to "{bad",
            )
        )
        val settingsClient = SettingsClient(store)
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()

        assertEquals(0, store.stringWriteCount)
        assertEquals(emptyList<AlarmSetting>(), settingsClient.peekAlarms())
        assertEquals(
            AlarmSchedulerState(),
            settingsClient.peekAlarmSchedulerState()
        )
    }

    @Test
    fun `scheduler persistence writes once per minute when state is otherwise unchanged`() {
        val clock = MutableClock(
            Instant.parse("2026-05-29T09:00:05Z"),
            ZoneOffset.UTC,
        )
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 23, minute = 0),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()

        service.checkAlarms()
        val writesAfterFirstTick = store.stringWriteCount

        clock.advanceSeconds(30)
        service.checkAlarms()
        val writesAfterSecondTick = store.stringWriteCount

        clock.advanceSeconds(35)
        service.checkAlarms()

        assertEquals(writesAfterFirstTick, writesAfterSecondTick)
        assertEquals(writesAfterFirstTick + 3, store.stringWriteCount)
    }

    @Test
    fun `scheduler loop starts and stops with active alarms`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_SCHEDULER_STATE to Json.encodeToString(
                    AlarmSchedulerState(
                        lastCheckAt = "2026-05-29T08:00:00",
                        lastTriggeredDates = mapOf("alarm-1" to "2026-05-28"),
                    )
                )
            )
        )
        val settingsClient = SettingsClient(store)
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.connect()

        assertFalse(service.isSchedulerRunning())
        assertEquals(
            AlarmSchedulerState(),
            service.snapshotSchedulerState()
        )

        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 9, minute = 30),
            )
        )
        service.onSettingsChanged(KEY_ALARMS)
        assertTrue(service.isSchedulerRunning())

        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 9, minute = 30, enabled = false),
            )
        )
        service.onSettingsChanged(KEY_ALARMS)
        assertFalse(service.isSchedulerRunning())
        assertEquals(
            AlarmSchedulerState(),
            service.snapshotSchedulerState()
        )

        service.close()
    }

    @Test
    fun `scheduler state changes are normalized back to the store`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:00:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-2", hour = 10, minute = 0),
            )
        )
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:00:00",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-28",
                    "alarm-2" to "2026-05-28",
                ),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()

        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:45:00",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                    "alarm-2" to "2026-05-29",
                ),
            )
        )

        service.onSettingsChanged(KEY_ALARM_SCHEDULER_STATE)

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:45:00",
                lastTriggeredDates = mapOf("alarm-2" to "2026-05-29"),
            ),
            service.snapshotSchedulerState()
        )
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T08:45:00",
                lastTriggeredDates = mapOf("alarm-2" to "2026-05-29"),
            ),
            Json.decodeFromString<AlarmSchedulerState>(
                store.getString(KEY_ALARM_SCHEDULER_STATE, "")
            )
        )
    }

    @Test
    fun `startup catch up uses the grace window when no last check exists`() {
        val clock = Clock.fixed(Instant.parse("2026-05-29T09:05:00Z"), ZoneOffset.UTC)
        val store = MapSettingsStore()
        val settingsClient = SettingsClient(store)
        settingsClient.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 8, minute = 59),
            )
        )
        val service = AlarmSchedulerService(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
            clock = clock,
        )

        service.reloadAlarms()
        service.reloadSchedulerState()
        service.checkAlarms()

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:05:00",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            service.snapshotSchedulerState()
        )
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues
        var stringWriteCount: Int = 0

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            values[key] = value
            stringWriteCount += 1
            return true
        }

        override fun getStringArray(key: String, defaultValue: List<String>): List<String> =
            values[key]?.let { raw ->
                if (raw.isEmpty()) emptyList() else raw.split("|||")
            } ?: defaultValue

        override fun setStringArray(key: String, value: List<String>): Boolean {
            values[key] = value.joinToString("|||")
            return true
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull() ?: defaultValue

        override fun setBoolean(key: String, value: Boolean): Boolean {
            values[key] = value.toString()
            return true
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key]?.toIntOrNull() ?: defaultValue

        override fun setInt(key: String, value: Int): Boolean {
            values[key] = value.toString()
            return true
        }
    }

    private class MutableClock(
        private var currentInstant: Instant,
        private val zone: ZoneId,
    ) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MutableClock(currentInstant, zone)

        override fun instant(): Instant = currentInstant

        fun advanceSeconds(seconds: Long) {
            currentInstant = currentInstant.plusSeconds(seconds)
        }
    }
}
