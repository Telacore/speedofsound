package com.zugaldia.speedofsound.core.desktop.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsClientJsonHealingTest {
    @Test
    fun `malformed json list settings are healed on read`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_CREDENTIALS to "{bad",
                KEY_VOICE_MODEL_PROVIDERS to "{bad",
                KEY_TEXT_MODEL_PROVIDERS to "{bad",
            )
        )
        val client = SettingsClient(store)

        assertEquals(emptyList<CredentialSetting>(), client.getCredentials())
        client.getVoiceModelProviders()
        assertEquals(emptyList<TextModelProviderSetting>(), client.getTextModelProviders())

        assertEquals(3, store.writeCount)
        assertEquals("[]", store.getString(KEY_CREDENTIALS, DEFAULT_CREDENTIALS))
        assertEquals("[]", store.getString(KEY_VOICE_MODEL_PROVIDERS, DEFAULT_VOICE_MODEL_PROVIDERS))
        assertEquals("[]", store.getString(KEY_TEXT_MODEL_PROVIDERS, DEFAULT_TEXT_MODEL_PROVIDERS))
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
