package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.app.plugins.textoutput.ClipboardTextOutput
import com.zugaldia.speedofsound.app.plugins.textoutput.PortalTextOutput
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_OUTPUT_METHOD
import com.zugaldia.speedofsound.core.desktop.settings.SettingsClient
import com.zugaldia.speedofsound.core.desktop.settings.SettingsStore
import com.zugaldia.speedofsound.core.plugins.AppPlugin
import com.zugaldia.speedofsound.core.plugins.AppPluginCategory
import com.zugaldia.speedofsound.core.plugins.AppPluginRegistry
import com.zugaldia.speedofsound.core.plugins.EmptyOptions
import com.zugaldia.speedofsound.core.plugins.textoutput.TextOutputPlugin
import com.zugaldia.speedofsound.core.plugins.textoutput.TextOutputRequest
import com.zugaldia.stargate.sdk.DesktopPortal
import java.lang.reflect.Field
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MainViewModelTextOutputRestoreTest {
    @Test
    fun `onTriggerAction tolerates failing text output restore`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_PORTAL,
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val portalsClient = PortalsClient(
            portalConnector = { Result.success(unsafeAllocateDesktopPortal()) },
            portalCloser = { },
        )
        val viewModel = MainViewModel(settingsClient, portalsClient)
        viewModel.state.updateStage(AppStage.IDLE)

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val activeClipboard = ThrowingDisableTextOutputPlugin(ClipboardTextOutput.ID)
        val portalOutput = RecordingTextOutputPlugin(PortalTextOutput.ID)

        registry.register(AppPluginCategory.TEXT_OUTPUT, activeClipboard)
        registry.register(AppPluginCategory.TEXT_OUTPUT, portalOutput)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, activeClipboard.id)

        viewModel.onTriggerAction()

        assertEquals(1, activeClipboard.enableCount)
        assertEquals(1, activeClipboard.disableCount)
        assertEquals(0, portalOutput.enableCount)
        assertEquals(0, portalOutput.disableCount)
        assertSame(activeClipboard, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(AppStage.IDLE, viewModel.state.currentStage())
    }

    private inline fun <reified T> getPrivateField(instance: Any, fieldName: String): T {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(instance) as T
    }

    private fun unsafeAllocateDesktopPortal(): DesktopPortal {
        val unsafeField: Field = Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as Unsafe
        return unsafe.allocateInstance(DesktopPortal::class.java) as DesktopPortal
    }

    private class RecordingTextOutputPlugin(
        override val id: String,
    ) : TextOutputPlugin<EmptyOptions>(EmptyOptions) {
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set

        override fun enable() {
            enableCount += 1
        }

        override fun disable() {
            disableCount += 1
        }

        override suspend fun outputText(request: TextOutputRequest): Result<Unit> = Result.success(Unit)
    }

    private class ThrowingDisableTextOutputPlugin(
        override val id: String,
    ) : TextOutputPlugin<EmptyOptions>(EmptyOptions) {
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set

        override fun enable() {
            enableCount += 1
        }

        override fun disable() {
            disableCount += 1
            throw IllegalStateException("disable failed")
        }

        override suspend fun outputText(request: TextOutputRequest): Result<Unit> = Result.success(Unit)
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
