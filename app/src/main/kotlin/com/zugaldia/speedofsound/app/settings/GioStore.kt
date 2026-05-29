package com.zugaldia.speedofsound.app.settings

/*
 * Store backed by Gio.
 *
 * It can be managed from the CLI. Examples:
 *
 * gsettings list-keys io.speedofsound.SpeedOfSound
 * gsettings get io.speedofsound.SpeedOfSound language
 * gsettings set io.speedofsound.SpeedOfSound language en
 *
 */

import com.zugaldia.speedofsound.core.APPLICATION_ID
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import org.gnome.gio.Settings
import org.gnome.gio.SettingsSchema
import org.gnome.gio.SettingsSchemaSource
import org.slf4j.LoggerFactory

internal interface GioSettingsBackend {
    fun hasKey(key: String): Boolean
    fun getString(key: String): String
    fun setString(key: String, value: String): Boolean
    fun getStringArray(key: String): List<String>
    fun setStringArray(key: String, value: List<String>): Boolean
    fun getBoolean(key: String): Boolean
    fun setBoolean(key: String, value: Boolean): Boolean
    fun getInt(key: String): Int
    fun setInt(key: String, value: Int): Boolean
}

class GioStore(
    val schemaId: String = APPLICATION_ID,
    internal val backend: GioSettingsBackend? = createBackend(schemaId),
): SettingsStore {
    private val logger = LoggerFactory.getLogger(GioStore::class.java)

    private val isAvailable: Boolean = backend != null

    override fun isAvailable(): Boolean = isAvailable

    private fun ensureKeyExists(key: String) {
        if (backend?.hasKey(key) != true) {
            // We throw the exception ourselves because otherwise the GLib-GIO-ERROR
            // is not caught and crashes the app. Example:
            // GLib-GIO-ERROR **: Settings schema 'io.speedofsound.SpeedOfSound' does not contain a key named 'x'
            throw IllegalArgumentException("Schema ($schemaId) or key ($key) not found")
        }
    }

    override fun getString(key: String, defaultValue: String): String = try {
        ensureKeyExists(key)
        backend?.getString(key) ?: defaultValue
    } catch (e: IllegalArgumentException) {
        logger.error("Error getting setting ($key), using default ($defaultValue): ${e.message}")
        defaultValue
    }

    override fun setString(key: String, value: String): Boolean = try {
        ensureKeyExists(key)
        val currentValue = backend?.getString(key)
        if (currentValue == value) {
            true
        } else {
            backend?.setString(key, value) ?: false
        }
    } catch (e: IllegalArgumentException) {
        logger.error("Error setting value ($key -> $value): ${e.message}")
        false
    }

    override fun getStringArray(key: String, defaultValue: List<String>): List<String> = try {
        ensureKeyExists(key)
        backend?.getStringArray(key) ?: defaultValue
    } catch (e: IllegalArgumentException) {
        logger.error("Error getting array setting ($key), using default: ${e.message}")
        defaultValue
    }

    override fun setStringArray(key: String, value: List<String>): Boolean = try {
        ensureKeyExists(key)
        val currentValue = backend?.getStringArray(key)
        if (currentValue == value) {
            true
        } else {
            backend?.setStringArray(key, value) ?: false
        }
    } catch (e: IllegalArgumentException) {
        logger.error("Error setting array value ($key): ${e.message}")
        false
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = try {
        ensureKeyExists(key)
        backend?.getBoolean(key) ?: defaultValue
    } catch (e: IllegalArgumentException) {
        logger.error("Error getting boolean setting ($key), using default ($defaultValue): ${e.message}")
        defaultValue
    }

    override fun setBoolean(key: String, value: Boolean): Boolean = try {
        ensureKeyExists(key)
        val currentValue = backend?.getBoolean(key)
        if (currentValue == value) {
            true
        } else {
            backend?.setBoolean(key, value) ?: false
        }
    } catch (e: IllegalArgumentException) {
        logger.error("Error setting boolean value ($key -> $value): ${e.message}")
        false
    }

    override fun getInt(key: String, defaultValue: Int): Int = try {
        ensureKeyExists(key)
        backend?.getInt(key) ?: defaultValue
    } catch (e: IllegalArgumentException) {
        logger.error("Error getting int setting ($key), using default ($defaultValue): ${e.message}")
        defaultValue
    }

    override fun setInt(key: String, value: Int): Boolean = try {
        ensureKeyExists(key)
        val currentValue = backend?.getInt(key)
        if (currentValue == value) {
            true
        } else {
            backend?.setInt(key, value) ?: false
        }
    } catch (e: IllegalArgumentException) {
        logger.error("Error setting int value ($key -> $value): ${e.message}")
        false
    }

    internal companion object {
        fun createBackend(schemaId: String): GioSettingsBackend? {
            val source = SettingsSchemaSource.getDefault()
            val settingsSchema = source?.lookup(schemaId, true) ?: return null
            val settings = Settings(schemaId)
            return RealGioSettingsBackend(settingsSchema, settings)
        }
    }
}

private class RealGioSettingsBackend(
    private val settingsSchema: SettingsSchema,
    private val settings: Settings,
) : GioSettingsBackend {
    override fun hasKey(key: String): Boolean = settingsSchema.hasKey(key)
    override fun getString(key: String): String = settings.getString(key)
    override fun setString(key: String, value: String): Boolean = settings.setString(key, value)
    override fun getStringArray(key: String): List<String> = settings.getStrv(key)?.toList() ?: emptyList()
    override fun setStringArray(key: String, value: List<String>): Boolean = settings.setStrv(key, value.toTypedArray())
    override fun getBoolean(key: String): Boolean = settings.getBoolean(key)
    override fun setBoolean(key: String, value: Boolean): Boolean = settings.setBoolean(key, value)
    override fun getInt(key: String): Int = settings.getInt(key)
    override fun setInt(key: String, value: Int): Boolean = settings.setInt(key, value)
}
