package com.zugaldia.speedofsound.core.desktop.settings

import com.zugaldia.speedofsound.core.io.AtomicFileWriter
import com.zugaldia.speedofsound.core.getDataDir
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.util.Properties

open class PropertiesStore(
    filename: String = DEFAULT_PROPERTIES_FILENAME,
    private val baseDir: Path = getDataDir(),
) : SettingsStore {
    private val logger = LoggerFactory.getLogger(PropertiesStore::class.java)
    private val properties = Properties()
    private val filePath = baseDir.resolve(filename).toFile()
    private val writeLock = Any()

    init {
        load()
    }

    private fun load() {
        if (filePath.exists()) {
            try {
                logger.info("Loading properties from $filePath")
                FileInputStream(filePath).use { properties.load(it) }
            } catch (e: IOException) {
                logger.error("Error loading properties from $filePath: ${e.message}")
            }
        } else {
            logger.info("Properties file does not exist yet, creating it: $filePath")
            save()
        }
    }

    protected open fun save(): Boolean = try {
        AtomicFileWriter.write(filePath) { tempFile ->
            FileOutputStream(tempFile).use { properties.store(it, null) }
        }.getOrThrow()
        true
    } catch (e: IOException) {
        logger.error("Error saving properties to $filePath: ${e.message}")
        false
    }

    override fun isAvailable(): Boolean = true

    override fun getString(key: String, defaultValue: String): String =
        properties.getProperty(key, defaultValue)

    override fun setString(key: String, value: String): Boolean {
        return writeIfChanged(key, value)
    }

    override fun getStringArray(key: String, defaultValue: List<String>): List<String> {
        val value = properties.getProperty(key)
        return when {
            value == null -> defaultValue
            value.isEmpty() -> emptyList()
            else -> value.split(ARRAY_DELIMITER)
        }
    }

    override fun setStringArray(key: String, value: List<String>): Boolean {
        return writeIfChanged(key, value.joinToString(ARRAY_DELIMITER))
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        properties.getProperty(key)?.toBoolean() ?: defaultValue

    override fun setBoolean(key: String, value: Boolean): Boolean {
        return writeIfChanged(key, value.toString())
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        properties.getProperty(key)?.toIntOrNull() ?: defaultValue

    override fun setInt(key: String, value: Int): Boolean {
        return writeIfChanged(key, value.toString())
    }

    private fun writeIfChanged(key: String, value: String): Boolean {
        synchronized(writeLock) {
            if (properties.getProperty(key) == value) {
                return true
            }

            properties.setProperty(key, value)
            return save()
        }
    }

    companion object {
        private const val ARRAY_DELIMITER = "|||"
    }
}
