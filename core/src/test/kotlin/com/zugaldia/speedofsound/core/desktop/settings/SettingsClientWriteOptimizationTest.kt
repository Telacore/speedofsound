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
                KEY_PORTALS_RESTORE_TOKEN to "smoke-token",
                KEY_CUSTOM_CONTEXT to "x".repeat(MAX_CUSTOM_CONTEXT_CHARS),
                KEY_CUSTOM_VOCABULARY to "alpha|||beta",
            )
        )
        val client = SettingsClient(store)

        client.setTextOutputMethod(TEXT_OUTPUT_METHOD_CLIPBOARD)
        client.setBackgroundRecording(true)
        client.setPostHideDelayMs(250)
        client.setPortalsRestoreToken("  smoke-token  ")
        client.setCustomContext("x".repeat(MAX_CUSTOM_CONTEXT_CHARS))
        client.setCustomVocabulary(listOf(" alpha ", "beta", "", "alpha"))

        assertEquals(0, store.writeCount)
    }

    @Test
    fun `custom context is truncated on save`() {
        val store = MapSettingsStore()
        val client = SettingsClient(store)

        val overlong = "y".repeat(MAX_CUSTOM_CONTEXT_CHARS + 9)
        client.setCustomContext(overlong)

        val expected = "y".repeat(MAX_CUSTOM_CONTEXT_CHARS)
        assertEquals(expected, store.getString(KEY_CUSTOM_CONTEXT, DEFAULT_CUSTOM_CONTEXT))
        assertEquals(1, store.writeCount)
    }

    @Test
    fun `malformed boolean and int settings are healed when defaults match`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_BACKGROUND_RECORDING to "maybe",
                KEY_POST_HIDE_DELAY_MS to "250ms",
            )
        )
        val client = SettingsClient(store)

        client.setBackgroundRecording(false)
        client.setPostHideDelayMs(250)

        assertEquals(2, store.writeCount)
        assertEquals("false", store.getString(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING.toString()))
        assertEquals("250", store.getString(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS.toString()))
    }

    @Test
    fun `language and text output setters heal malformed values when saving defaults`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_DEFAULT_LANGUAGE to "EN",
                KEY_SECONDARY_LANGUAGE to "zz",
                KEY_TEXT_OUTPUT_METHOD to "not-a-method",
            )
        )
        val client = SettingsClient(store)

        client.setDefaultLanguage(DEFAULT_LANGUAGE.iso2)
        client.setSecondaryLanguage(DEFAULT_SECONDARY_LANGUAGE.iso2)
        client.setTextOutputMethod(TEXT_OUTPUT_METHOD_PORTAL)

        assertEquals(3, store.writeCount)
        assertEquals(DEFAULT_LANGUAGE.iso2, store.getString(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2))
        assertEquals(DEFAULT_SECONDARY_LANGUAGE.iso2, store.getString(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2))
        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, store.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD))
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
