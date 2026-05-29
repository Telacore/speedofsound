package com.zugaldia.speedofsound.app.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class GioStoreTest {
    @Test
    fun `setters do not rewrite unchanged values`() {
        val backend = FakeGioSettingsBackend(
            initialValues = mutableMapOf(
                "text" to "hello",
                "flags" to "true",
                "count" to "7",
                "items" to listOf("a", "b"),
            )
        )
        val store = GioStore(schemaId = "io.speedofsound.SpeedOfSound", backend = backend)

        assertEquals(true, store.setString("text", "hello"))
        assertEquals(true, store.setBoolean("flags", true))
        assertEquals(true, store.setInt("count", 7))
        assertEquals(true, store.setStringArray("items", listOf("a", "b")))

        assertEquals(0, backend.writeCount)
    }

    @Test
    fun `setters write when values change`() {
        val backend = FakeGioSettingsBackend(
            initialValues = mutableMapOf(
                "text" to "hello",
                "flags" to "true",
                "count" to "7",
                "items" to listOf("a", "b"),
            )
        )
        val store = GioStore(schemaId = "io.speedofsound.SpeedOfSound", backend = backend)

        assertEquals(true, store.setString("text", "goodbye"))
        assertEquals(true, store.setBoolean("flags", false))
        assertEquals(true, store.setInt("count", 8))
        assertEquals(true, store.setStringArray("items", listOf("a", "c")))

        assertEquals(4, backend.writeCount)
    }

    private class FakeGioSettingsBackend(
        initialValues: MutableMap<String, Any>,
    ) : GioSettingsBackend {
        private val values = initialValues
        var writeCount: Int = 0

        override fun hasKey(key: String): Boolean = values.containsKey(key)

        override fun getString(key: String): String = values[key] as String

        override fun setString(key: String, value: String): Boolean {
            values[key] = value
            writeCount += 1
            return true
        }

        override fun getStringArray(key: String): List<String> = values[key] as List<String>

        override fun setStringArray(key: String, value: List<String>): Boolean {
            values[key] = value
            writeCount += 1
            return true
        }

        override fun getBoolean(key: String): Boolean = (values[key] as String).toBooleanStrict()

        override fun setBoolean(key: String, value: Boolean): Boolean {
            values[key] = value.toString()
            writeCount += 1
            return true
        }

        override fun getInt(key: String): Int = (values[key] as String).toInt()

        override fun setInt(key: String, value: Int): Boolean {
            values[key] = value.toString()
            writeCount += 1
            return true
        }
    }
}
