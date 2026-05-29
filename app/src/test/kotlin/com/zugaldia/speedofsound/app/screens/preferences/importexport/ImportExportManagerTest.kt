package com.zugaldia.speedofsound.app.screens.preferences.importexport

import com.zugaldia.speedofsound.app.screens.preferences.PreferencesViewModel
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSchedulerState
import com.zugaldia.speedofsound.core.desktop.settings.AlarmAction
import com.zugaldia.speedofsound.core.desktop.settings.AlarmSetting
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_ALARM_SCHEDULER_STATE
import com.zugaldia.speedofsound.core.desktop.settings.DEFAULT_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_SCHEDULER_STATE
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.desktop.settings.SettingsExport
import com.zugaldia.speedofsound.core.getDataDir
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            settingsClient.loadAlarmSchedulerState()
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
        assertEquals(AlarmSchedulerState(), settingsClient.loadAlarmSchedulerState())
    }

    @Test
    fun `export does not migrate legacy alarm scheduler state`() {
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_LAST_TRIGGERED_DATES to Json.encodeToString(
                    mapOf(
                        "alarm-1" to "2026-05-29",
                    )
                ),
                com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARM_LAST_CHECK_AT to "2026-05-29T09:15:30",
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(
            AlarmSchedulerState(
                lastCheckAt = "2026-05-29T09:15:30",
                lastTriggeredDates = mapOf("alarm-1" to "2026-05-29"),
            ),
            exported.alarmSchedulerState
        )
        assertEquals(
            DEFAULT_ALARM_SCHEDULER_STATE,
            store.getString(KEY_ALARM_SCHEDULER_STATE, DEFAULT_ALARM_SCHEDULER_STATE)
        )
    }

    @Test
    fun `export does not heal invalid alarms`() {
        val validAlarm = AlarmSetting(id = "alarm-1", name = "Morning", hour = 6, minute = 0, action = AlarmAction.ATTENTION)
        val invalidAlarm = AlarmSetting(id = "alarm-2", name = "Broken", hour = 25, minute = 0, action = AlarmAction.URGENT)
        val rawJson = Json.encodeToString(listOf(validAlarm, invalidAlarm))
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to rawJson,
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val exportPath = manager.export().getOrThrow()
        val exported = Json.decodeFromString<SettingsExport>(File(exportPath).readText())

        assertEquals(listOf(validAlarm), exported.alarms)
        assertEquals(rawJson, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `import heals malformed alarms during merge`() {
        val rawJson = "{not-json"
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to rawJson,
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        exportFile.writeText(Json.encodeToString(SettingsExport(version = 6)))

        val result = manager.importSettings().getOrThrow()

        assertTrue(result.filePath.isNotBlank())
        assertEquals(DEFAULT_ALARMS, store.getString(KEY_ALARMS, DEFAULT_ALARMS))
    }

    @Test
    fun `import rejects directory export paths`() {
        exportFile.mkdirs()

        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val result = manager.importSettings()

        assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }
    }

    @Test
    fun `import rejects malformed export json`() {
        exportFile.writeText("{bad")

        val settingsClient = SettingsClient(MapSettingsStore())
        val viewModel = PreferencesViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )
        val manager = ImportExportManager(viewModel)

        val result = manager.importSettings()

        val exception = assertFailsWith<IllegalStateException> {
            result.getOrThrow()
        }

        assertTrue(exception.message?.contains("Malformed export file") == true)
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
