package com.zugaldia.speedofsound.app.screens.preferences.importexport

import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.desktop.settings.SettingsExport
import com.zugaldia.speedofsound.core.getDataDir
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File

class ImportExportManagerTest {
    private val exportFile = getDataDir().resolve(ImportExportManager.EXPORT_FILENAME).toFile()

    @AfterTest
    fun cleanup() {
        exportFile.delete()
    }

    @Test
    fun `export includes alarm scheduler state and version 6`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        settingsClient.setAlarmSchedulerState(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf(
                    "alarm-1" to "2026-05-29",
                ),
            )
        )
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(6, exported.version)
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            exported.alarmSchedulerState
        )
    }

    @Test
    fun `import applies alarm scheduler state when present`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(
            Json.encodeToString(
                SettingsExport(
                    version = 6,
                    alarmSchedulerState = AlarmSchedulerState(
                        lastCheckAt = "2026-05-29T09:15:30",
                        lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
                    ),
                )
            )
        )

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertTrue(result.alarmSchedulerStateImported)
        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            settingsClient.getAlarmSchedulerState()
        )
    }

    @Test
    fun `import still accepts older export versions without scheduler state`() {
        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(Json.encodeToString(SettingsExport(version = 5)))

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertEquals(false, result.alarmSchedulerStateImported)
        assertEquals(AlarmSchedulerState(), settingsClient.getAlarmSchedulerState())
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
