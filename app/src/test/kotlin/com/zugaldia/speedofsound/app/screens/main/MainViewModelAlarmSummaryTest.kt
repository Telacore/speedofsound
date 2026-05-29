package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.KEY_ALARMS
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.stargate.sdk.DesktopPortal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MainViewModelAlarmSummaryTest {
    @Test
    fun `refresh alarm summary does not heal malformed alarm storage`() {
        val rawJson = "{not-json"
        val store = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_ALARMS to rawJson,
            )
        )
        val settingsClient = SettingsClient(store)
        val viewModel = MainViewModel(
            settingsClient = settingsClient,
            portalsClient = PortalsClient(portalConnector = {
                Result.failure<DesktopPortal>(IllegalStateException("no portal"))
            }),
        )

        invokeRefreshAlarmSummary(viewModel)

        assertEquals(rawJson, store.getString(KEY_ALARMS, rawJson))
        assertEquals("All alarms disabled", viewModel.state.currentAlarmSummary())
        assertTrue(viewModel.state.currentAlarmSummary().isNotBlank())
    }

    private fun invokeRefreshAlarmSummary(viewModel: MainViewModel) {
        val method = MainViewModel::class.java.getDeclaredMethod("refreshAlarmSummary")
        method.isAccessible = true
        method.invoke(viewModel)
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
