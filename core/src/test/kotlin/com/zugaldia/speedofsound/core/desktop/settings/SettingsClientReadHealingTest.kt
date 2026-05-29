package com.zugaldia.speedofsound.core.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsClientReadHealingTest {
    @Test
    fun `malformed bool and int settings are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to "  CLIPBOARD  ",
                KEY_WELCOME_SCREEN_SHOWN to "maybe",
                KEY_SHORTCUT_CONFIGURED to "TRUE",
                KEY_BACKGROUND_RECORDING to "not-a-bool",
                KEY_POST_HIDE_DELAY_MS to "250ms",
                KEY_TYPING_DELAY_MS to "003",
                KEY_MAX_ALARMS to "999",
            )
        )
        val client = SettingsClient(store)

        assertEquals(TEXT_OUTPUT_METHOD_CLIPBOARD, client.getTextOutputMethod())
        assertEquals(DEFAULT_WELCOME_SCREEN_SHOWN, client.getWelcomeScreenShown())
        assertEquals(true, client.getShortcutConfigured())
        assertEquals(DEFAULT_BACKGROUND_RECORDING, client.getBackgroundRecording())
        assertEquals(DEFAULT_POST_HIDE_DELAY_MS, client.getPostHideDelayMs())
        assertEquals(3, client.getTypingDelayMs())
        assertEquals(MAX_MAX_ALARMS, client.getMaxAlarms())

        assertEquals(TEXT_OUTPUT_METHOD_CLIPBOARD, store.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD))
        assertEquals("false", store.getString(KEY_WELCOME_SCREEN_SHOWN, DEFAULT_WELCOME_SCREEN_SHOWN.toString()))
        assertEquals("true", store.getString(KEY_SHORTCUT_CONFIGURED, DEFAULT_SHORTCUT_CONFIGURED.toString()))
        assertEquals(DEFAULT_BACKGROUND_RECORDING.toString(), store.getString(KEY_BACKGROUND_RECORDING, DEFAULT_BACKGROUND_RECORDING.toString()))
        assertEquals(DEFAULT_POST_HIDE_DELAY_MS.toString(), store.getString(KEY_POST_HIDE_DELAY_MS, DEFAULT_POST_HIDE_DELAY_MS.toString()))
        assertEquals("3", store.getString(KEY_TYPING_DELAY_MS, DEFAULT_TYPING_DELAY_MS.toString()))
        assertEquals(MAX_MAX_ALARMS.toString(), store.getString(KEY_MAX_ALARMS, DEFAULT_MAX_ALARMS.toString()))
        assertEquals(7, store.writeCount)
    }

    @Test
    fun `malformed text output method is healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to "not-a-method"
            )
        )
        val client = SettingsClient(store)

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, client.getTextOutputMethod())
        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, store.getString(KEY_TEXT_OUTPUT_METHOD, DEFAULT_TEXT_OUTPUT_METHOD))
        assertEquals(1, store.writeCount)
    }

    @Test
    fun `malformed language settings are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_DEFAULT_LANGUAGE to "EN",
                KEY_SECONDARY_LANGUAGE to "zz",
            )
        )
        val client = SettingsClient(store)

        assertEquals(DEFAULT_LANGUAGE.iso2, client.getDefaultLanguage())
        assertEquals(DEFAULT_SECONDARY_LANGUAGE.iso2, client.getSecondaryLanguage())
        assertEquals(DEFAULT_LANGUAGE.iso2, store.getString(KEY_DEFAULT_LANGUAGE, DEFAULT_LANGUAGE.iso2))
        assertEquals(DEFAULT_SECONDARY_LANGUAGE.iso2, store.getString(KEY_SECONDARY_LANGUAGE, DEFAULT_SECONDARY_LANGUAGE.iso2))
        assertEquals(2, store.writeCount)
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
