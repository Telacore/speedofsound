package com.zugaldia.speedofsound.core.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsClientWriteOptimizationTest {
    @Test
    fun `simple settings do not rewrite unchanged values`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to TEXT_OUTPUT_METHOD_CLIPBOARD,
                KEY_BACKGROUND_RECORDING to "true",
                KEY_POST_HIDE_DELAY_MS to "250",
                KEY_CUSTOM_VOCABULARY to "alpha|||beta",
            )
        )
        val client = SettingsClient(store)

        client.setTextOutputMethod(TEXT_OUTPUT_METHOD_CLIPBOARD)
        client.setBackgroundRecording(true)
        client.setPostHideDelayMs(250)
        client.setCustomVocabulary(listOf("alpha", "beta"))

        assertEquals(0, store.writeCount)
    }

    private class MapSettingsStore(
        initialValues: MutableMap<String, String> = mutableMapOf(),
    ) : SettingsStore {
        private val values = initialValues
        var writeCount: Int = 0

        override fun isAvailable(): Boolean = true

        override fun getString(key: String, defaultValue: String): String = values[key] ?: defaultValue

        override fun setString(key: String, value: String): Boolean {
            values[key] = value
            writeCount += 1
            return true
        }

        override fun getStringArray(key: String, defaultValue: List<String>): List<String> =
            values[key]?.let { raw ->
                if (raw.isEmpty()) emptyList() else raw.split("|||")
            } ?: defaultValue

        override fun setStringArray(key: String, value: List<String>): Boolean {
            values[key] = value.joinToString("|||")
            writeCount += 1
            return true
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            values[key]?.toBooleanStrictOrNull() ?: defaultValue

        override fun setBoolean(key: String, value: Boolean): Boolean {
            values[key] = value.toString()
            writeCount += 1
            return true
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            values[key]?.toIntOrNull() ?: defaultValue

        override fun setInt(key: String, value: Int): Boolean {
            values[key] = value.toString()
            writeCount += 1
            return true
        }
    }
}
