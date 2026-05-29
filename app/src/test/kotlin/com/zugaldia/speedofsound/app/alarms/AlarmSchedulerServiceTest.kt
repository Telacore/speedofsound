package com.zugaldia.speedofsound.app.alarms

import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_SCHEDULER_STATE
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.Clock
import java.time.Instant
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
            checkIntervalSeconds = 60,
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

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            values[key] = value
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
}
