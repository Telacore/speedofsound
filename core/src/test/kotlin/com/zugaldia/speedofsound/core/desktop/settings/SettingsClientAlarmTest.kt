package com.zugaldia.speedofsound.core.desktop.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDate
import java.time.LocalDateTime

class SettingsClientAlarmTest {

    @Test
    fun `setMaxAlarms clamps the value to the supported range`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setMaxAlarms(999)

        assertEquals(MAX_MAX_ALARMS, client.getMaxAlarms())
    }

    @Test
    fun `setMaxAlarms trims already persisted alarms`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", hour = 6, minute = 0),
                AlarmSetting(id = "alarm-2", hour = 7, minute = 0),
                AlarmSetting(id = "alarm-3", hour = 8, minute = 0),
            )
        )

        client.setMaxAlarms(2)

        assertEquals(2, client.loadAlarms().size)
        assertEquals(listOf("alarm-1", "alarm-2"), client.loadAlarms().map { it.id })
    }

    @Test
    fun `setMaxAlarms clears malformed alarm json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to "{not-json"
            )
        )
        val client = SettingsClient(store)

        client.setMaxAlarms(2)

        assertEquals(emptyList(), client.loadAlarms())
        assertEquals(DEFAULT_ALARMS, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `setAlarms trims entries to the configured maximum`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)
        client.setMaxAlarms(2)

        val alarms = listOf(
            AlarmSetting(id = "alarm-1", name = "Early", hour = 6, minute = 0),
            AlarmSetting(id = "alarm-2", name = "Mid", hour = 7, minute = 0),
            AlarmSetting(id = "alarm-3", name = "Late", hour = 8, minute = 0),
        )

        client.setAlarms(alarms)

        assertEquals(2, client.loadAlarms().size)
        assertEquals(listOf("alarm-1", "alarm-2"), client.loadAlarms().map { it.id })
        assertEquals(listOf("Early", "Mid"), client.loadAlarms().map { it.name })
    }

    @Test
    fun `setAlarms trims alarm names to the supported maximum`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        val longName = "A".repeat(MAX_ALARM_NAME_LENGTH + 25)
        client.setAlarms(
            listOf(
                AlarmSetting(id = "alarm-1", name = longName, hour = 6, minute = 0),
            )
        )

        assertEquals(1, client.loadAlarms().size)
        assertEquals(MAX_ALARM_NAME_LENGTH, client.loadAlarms().first().name.length)
        assertEquals(longName.take(MAX_ALARM_NAME_LENGTH), client.loadAlarms().first().name)
    }

    @Test
    fun `setAlarms filters invalid entries before persisting`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        val validAlarm = AlarmSetting(id = "alarm-1", name = "Morning", hour = 7, minute = 30, action = AlarmAction.ATTENTION)
        val invalidAlarm = AlarmSetting(id = "alarm-2", name = "Broken", hour = 25, minute = 99, action = AlarmAction.URGENT)

        client.setAlarms(listOf(validAlarm, invalidAlarm))

        assertEquals(listOf(validAlarm), client.loadAlarms())
        assertEquals(listOf("Morning"), client.loadAlarms().map { it.name })
    }

    @Test
    fun `setAlarms avoids rewriting an unchanged normalized list`() {
        val normalizedAlarms = listOf(
            AlarmSetting(id = "alarm-1", name = "Morning", hour = 7, minute = 30),
            AlarmSetting(id = "alarm-2", name = "Noon", hour = 12, minute = 0),
        )
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to Json.encodeToString(normalizedAlarms)
            )
        )
        val client = SettingsClient(store)

        assertEquals(true, client.setAlarms(normalizedAlarms))
        assertEquals(0, store.stringWriteCount)
    }

    @Test
    fun `setAlarms normalizes repeat days and falls back to daily when empty`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setAlarms(
            listOf(
                AlarmSetting(
                    id = "alarm-1",
                    name = "Gym",
                    hour = 18,
                    minute = 15,
                    repeatDays = listOf(
                        AlarmRepeatDay.FRIDAY,
                        AlarmRepeatDay.MONDAY,
                        AlarmRepeatDay.FRIDAY,
                    )
                ),
                AlarmSetting(
                    id = "alarm-2",
                    name = "Fallback",
                    hour = 7,
                    minute = 0,
                    repeatDays = emptyList(),
                )
            )
        )

        val gymAlarm = client.loadAlarms().first { it.id == "alarm-1" }
        val fallbackAlarm = client.loadAlarms().first { it.id == "alarm-2" }

        assertEquals(
            listOf(AlarmRepeatDay.MONDAY, AlarmRepeatDay.FRIDAY),
            gymAlarm.repeatDays
        )
        assertEquals(
            AlarmRepeatDay.values().toList(),
            fallbackAlarm.repeatDays
        )
    }

    @Test
    fun `alarm trigger dates round trip through the store`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setAlarmLastTriggeredDate("alarm-1", LocalDate.of(2026, 5, 29))

        assertEquals(
            mapOf("alarm-1" to LocalDate.of(2026, 5, 29)),
            client.loadAlarmLastTriggeredDates()
        )
    }

    @Test
    fun `peek alarm trigger dates does not migrate legacy keys`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(
                    mapOf(
                        "alarm-1" to "2026-05-29",
                    )
                ),
                KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30",
            )
        )
        val client = SettingsClient(store)

        assertEquals(
            mapOf(
                "alarm-1" to LocalDate.of(2026, 5, 29),
            ),
            client.peekAlarmLastTriggeredDates()
        )
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `alarm trigger dates batch round trip through the store`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setAlarmLastTriggeredDates(
            mapOf(
                "alarm-1" to LocalDate.of(2026, 5, 29),
                "alarm-2" to LocalDate.of(2026, 5, 30),
            )
        )

        assertEquals(
            mapOf(
                "alarm-1" to LocalDate.of(2026, 5, 29),
                "alarm-2" to LocalDate.of(2026, 5, 30),
            ),
            client.loadAlarmLastTriggeredDates()
        )
    }

    @Test
    fun `alarm scheduler state round trips through the store`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                    "alarm-2" to "2026-05-30",
                ),
            )
        )

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                    "alarm-2" to "2026-05-30",
                ),
            ),
            client.loadAlarmSchedulerState()
        )
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                    "alarm-2" to "2026-05-30",
                ),
            ),
            Json.decodeFromString<AlarmSchedulerState>(
                store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
            )
        )
        assertEquals(
            "2026-05-29T09:15:30",
            store.getString(KEY_ALARM_LAST_CHECK_AT, DEFAULT_ALARM_LAST_CHECK_AT)
        )
        assertEquals(
            mapOf(
                "alarm-1" to "2026-05-29",
                "alarm-2" to "2026-05-30",
            ),
            Json.decodeFromString<Map<String, String>>(
                store.getString(KEY_ALARM_LAST_TRIGGERED_DATES, DEFAULT_ALARM_LAST_TRIGGERED_DATES)
            )
        )
    }

    @Test
    fun `setAlarmSchedulerState avoids rewriting an unchanged normalized state`() {
        val normalizedState = AlarmSchedulerState(
            lastCheckAt = "2026-05-29T09:15:30",
            lastTriggeredDates = mapOf(
                "alarm-1" to "2026-05-29",
                "alarm-2" to "2026-05-30",
            ),
        )
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_SCHEDULER_STATE to Json.encodeToString(normalizedState),
                KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(normalizedState.lastTriggeredDates),
                KEY_ALARM_LAST_CHECK_AT to normalizedState.lastCheckAt!!,
            )
        )
        val client = SettingsClient(store)

        assertEquals(true, client.setAlarmSchedulerState(normalizedState))
        assertEquals(0, store.stringWriteCount)
    }

    @Test
    fun `setAlarmSchedulerState only rewrites missing legacy keys`() {
        val normalizedState = AlarmSchedulerState(
            lastCheckAt = "2026-05-29T09:15:30",
            lastTriggeredDates = mapOf(
                "alarm-1" to "2026-05-29",
                "alarm-2" to "2026-05-30",
            ),
        )
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(normalizedState.lastTriggeredDates),
                KEY_ALARM_LAST_CHECK_AT to normalizedState.lastCheckAt!!,
            )
        )
        val client = SettingsClient(store)

        assertEquals(true, client.setAlarmSchedulerState(normalizedState))
        assertEquals(1, store.stringWriteCount)
        assertEquals(
            normalizedState,
            Json.decodeFromString<AlarmSchedulerState>(
                store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
            )
        )
    }

    @Test
    fun `alarm scheduler state falls back to legacy keys`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(
                    mapOf(
                        "alarm-1" to "2026-05-29",
                    )
                ),
                KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30",
            )
        )
        val client = SettingsClient(store)

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                ),
            ),
            client.loadAlarmSchedulerState()
        )
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                ),
            ),
            Json.decodeFromString<AlarmSchedulerState>(
                store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
            )
        )
    }

    @Test
    fun `alarm scheduler state falls back from malformed combined json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_SCHEDULER_STATE to "{not-json",
                KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(
                    mapOf(
                        "alarm-1" to "2026-05-29",
                    )
                ),
                KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30",
            )
        )
        val client = SettingsClient(store)

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                ),
            ),
            client.loadAlarmSchedulerState()
        )
    }

    @Test
    fun `alarm scheduler state heals malformed combined json without legacy fallback`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_SCHEDULER_STATE to "{not-json",
            )
        )
        val client = SettingsClient(store)

        assertEquals(AlarmSchedulerState(), client.loadAlarmSchedulerState())
        assertEquals(AlarmSchedulerState(), Json.decodeFromString<AlarmSchedulerState>(store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)))
    }

    @Test
    fun `peek alarm scheduler state does not migrate legacy keys`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(
                    mapOf(
                        "alarm-1" to "2026-05-29",
                    )
                ),
                KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30",
            )
        )
        val client = SettingsClient(store)

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                ),
            ),
            client.peekAlarmSchedulerState()
        )
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `alarm trigger dates fall back to empty on malformed json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_TRIGGERED_DATES to "{not-json"
            )
        )
        val client = SettingsClient(store)

        assertEquals(emptyMap(), client.loadAlarmLastTriggeredDates())
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `alarm last check timestamp round trips through the store`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)
        val timestamp = LocalDateTime.of(2026, 5, 29, 9, 15, 30)

        client.setAlarmLastCheckAt(timestamp)

        assertEquals(timestamp, client.loadAlarmLastCheckAt())
    }

    @Test
    fun `peek alarm last check timestamp does not migrate legacy keys`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30"
            )
        )
        val client = SettingsClient(store)

        assertEquals(
            LocalDateTime.of(2026, 5, 29, 9, 15, 30),
            client.peekAlarmLastCheckAt()
        )
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `alarm last check timestamp falls back to null on malformed json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARM_LAST_CHECK_AT to "{not-json"
            )
        )
        val client = SettingsClient(store)

        assertEquals(null, client.loadAlarmLastCheckAt())
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `getAlarms filters invalid entries already stored`() {
        val validAlarm = AlarmSetting(id = "alarm-1", hour = 6, minute = 15, action = AlarmAction.NORMAL)
        val invalidAlarm = AlarmSetting(id = "", hour = 24, minute = -1, action = AlarmAction.SILENT)
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to Json.encodeToString(listOf(validAlarm, invalidAlarm))
            )
        )

        val client = SettingsClient(store)

        assertEquals(listOf(validAlarm), client.loadAlarms())
        assertEquals(Json.encodeToString(listOf(validAlarm)), store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `peekAlarms does not heal invalid entries already stored`() {
        val validAlarm = AlarmSetting(id = "alarm-1", hour = 6, minute = 15, action = AlarmAction.NORMAL)
        val invalidAlarm = AlarmSetting(id = "", hour = 24, minute = -1, action = AlarmAction.SILENT)
        val rawJson = Json.encodeToString(listOf(validAlarm, invalidAlarm))
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to rawJson
            )
        )

        val client = SettingsClient(store)

        assertEquals(listOf(validAlarm), client.peekAlarms())
        assertEquals(rawJson, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `getAlarms returns empty list on malformed json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to "{not-json"
            )
        )

        val client = SettingsClient(store)

        assertEquals(emptyList(), client.loadAlarms())
        assertEquals(DEFAULT_ALARMS, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
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
}
