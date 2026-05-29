package com.zugaldia.speedofsound.core.desktop.settings

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsClientAlarmTest {

    @Test
    fun `setMaxAlarms clamps the value to the supported range`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        client.setMaxAlarms(999)

        assertEquals(MAX_MAX_ALARMS, client.getMaxAlarms())
    }

    @Test
    fun `setAlarms trims entries to the configured maximum`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)
        client.setMaxAlarms(2)

        val alarms = listOf(
            AlarmSetting(id = "alarm-1", hour = 6, minute = 0),
            AlarmSetting(id = "alarm-2", hour = 7, minute = 0),
            AlarmSetting(id = "alarm-3", hour = 8, minute = 0),
        )

        client.setAlarms(alarms)

        assertEquals(2, client.getAlarms().size)
        assertEquals(listOf("alarm-1", "alarm-2"), client.getAlarms().map { it.id })
    }

    @Test
    fun `setAlarms filters invalid entries before persisting`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        val validAlarm = AlarmSetting(id = "alarm-1", hour = 7, minute = 30, action = AlarmAction.ATTENTION)
        val invalidAlarm = AlarmSetting(id = "alarm-2", hour = 25, minute = 99, action = AlarmAction.URGENT)

        client.setAlarms(listOf(validAlarm, invalidAlarm))

        assertEquals(listOf(validAlarm), client.getAlarms())
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

        assertEquals(listOf(validAlarm), client.getAlarms())
    }

    @Test
    fun `getAlarms returns empty list on malformed json`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to "{not-json"
            )
        )

        val client = SettingsClient(store)

        assertEquals(emptyList(), client.getAlarms())
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
