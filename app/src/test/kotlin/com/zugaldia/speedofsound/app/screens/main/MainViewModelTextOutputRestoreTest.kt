package com.zugaldia.speedofsound.app.screens.main

import com.zugaldia.speedofsound.app.plugins.textoutput.ClipboardTextOutput
import com.zugaldia.speedofsound.app.plugins.textoutput.PortalTextOutput
import com.zugaldia.speedofsound.core.desktop.portals.PortalsClient
import com.zugaldia.speedofsound.core.desktop.settings.KEY_TEXT_OUTPUT_METHOD
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_CLIPBOARD
import com.zugaldia.speedofsound.core.desktop.settings.TEXT_OUTPUT_METHOD_PORTAL
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
import kotlin.test.assertFalse
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

    @Test
    fun `activateSelectedTextOutput tolerates failing text output switch`() {
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

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val activeClipboard = ThrowingDisableTextOutputPlugin(ClipboardTextOutput.ID)
        val portalOutput = RecordingTextOutputPlugin(PortalTextOutput.ID)

        registry.register(AppPluginCategory.TEXT_OUTPUT, activeClipboard)
        registry.register(AppPluginCategory.TEXT_OUTPUT, portalOutput)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, activeClipboard.id)

        val activated = invokePrivateBoolean(viewModel, "activateSelectedTextOutput")

        assertFalse(activated)
        assertEquals(1, activeClipboard.enableCount)
        assertEquals(1, activeClipboard.disableCount)
        assertEquals(0, portalOutput.enableCount)
        assertEquals(0, portalOutput.disableCount)
        assertSame(activeClipboard, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
    }

    @Test
    fun `refreshTextOutputMethodSetting persists clipboard fallback when portal is unavailable`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to TEXT_OUTPUT_METHOD_PORTAL,
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val portalsClient = PortalsClient(portalConnector = { Result.failure<DesktopPortal>(IllegalStateException("no portal")) })
        val viewModel = MainViewModel(settingsClient, portalsClient)
        viewModel.state.updateStage(AppStage.IDLE)

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val activeClipboard = RecordingTextOutputPlugin(ClipboardTextOutput.ID)
        val failingPortal = ThrowingEnableTextOutputPlugin(PortalTextOutput.ID)

        registry.register(AppPluginCategory.TEXT_OUTPUT, activeClipboard)
        registry.register(AppPluginCategory.TEXT_OUTPUT, failingPortal)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, activeClipboard.id)

        invokePrivateUnit(viewModel, "refreshTextOutputMethodSetting")

        assertEquals(TEXT_OUTPUT_METHOD_CLIPBOARD, settingsClient.loadTextOutputMethod())
        assertSame(activeClipboard, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
    }

    @Test
    fun `refreshTextOutputMethodSetting falls back to clipboard when portal activation fails with no active text output`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to TEXT_OUTPUT_METHOD_PORTAL,
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val portalsClient = PortalsClient(portalConnector = { Result.failure<DesktopPortal>(IllegalStateException("no portal")) })
        val viewModel = MainViewModel(settingsClient, portalsClient)

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val clipboardOutput = RecordingTextOutputPlugin(ClipboardTextOutput.ID)
        val failingPortal = ThrowingEnableTextOutputPlugin(PortalTextOutput.ID)

        registry.register(AppPluginCategory.TEXT_OUTPUT, clipboardOutput)
        registry.register(AppPluginCategory.TEXT_OUTPUT, failingPortal)

        invokePrivateUnit(viewModel, "refreshTextOutputMethodSetting")

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, settingsClient.loadTextOutputMethod())
        assertSame(clipboardOutput, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(0, clipboardOutput.disableCount)
        assertEquals(1, clipboardOutput.enableCount)
        assertEquals(1, failingPortal.enableCount)
        assertEquals(1, failingPortal.disableCount)
    }

    @Test
    fun `refreshTextOutputMethodSetting uses runtime clipboard fallback when portal activation fails but portal is available`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to TEXT_OUTPUT_METHOD_PORTAL,
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val portalsClient = PortalsClient(
            portalConnector = { Result.success(unsafeAllocateDesktopPortal()) },
            portalCloser = { },
        )
        val viewModel = MainViewModel(settingsClient, portalsClient)

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val clipboardOutput = RecordingTextOutputPlugin(ClipboardTextOutput.ID)
        val failingPortal = ThrowingEnableTextOutputPlugin(PortalTextOutput.ID)

        registry.register(AppPluginCategory.TEXT_OUTPUT, clipboardOutput)
        registry.register(AppPluginCategory.TEXT_OUTPUT, failingPortal)

        invokePrivateUnit(viewModel, "refreshTextOutputMethodSetting")

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, settingsClient.loadTextOutputMethod())
        assertSame(clipboardOutput, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(0, clipboardOutput.disableCount)
        assertEquals(1, clipboardOutput.enableCount)
        assertEquals(1, failingPortal.enableCount)
        assertEquals(1, failingPortal.disableCount)
    }

    @Test
    fun `refreshTextOutputMethodSetting does not persist clipboard fallback when clipboard activation fails`() {
        val settingsStore = MapSettingsStore(
            initialValues = mutableMapOf(
                KEY_TEXT_OUTPUT_METHOD to TEXT_OUTPUT_METHOD_PORTAL,
            )
        )
        val settingsClient = SettingsClient(settingsStore)
        val portalsClient = PortalsClient(portalConnector = { Result.failure<DesktopPortal>(IllegalStateException("no portal")) })
        val viewModel = MainViewModel(settingsClient, portalsClient)

        val registry = getPrivateField<AppPluginRegistry>(viewModel, "registry")
        val activePortal = RecordingTextOutputPlugin(PortalTextOutput.ID)
        val failingClipboard = ThrowingEnableTextOutputPlugin(ClipboardTextOutput.ID)

        registry.register(AppPluginCategory.TEXT_OUTPUT, activePortal)
        registry.register(AppPluginCategory.TEXT_OUTPUT, failingClipboard)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, activePortal.id)

        invokePrivateUnit(viewModel, "refreshTextOutputMethodSetting")

        assertEquals(TEXT_OUTPUT_METHOD_PORTAL, settingsClient.loadTextOutputMethod())
        assertSame(activePortal, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(1, failingClipboard.enableCount)
        assertEquals(1, failingClipboard.disableCount)
        assertEquals(1, activePortal.enableCount)
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

    private fun invokePrivateBoolean(instance: Any, fieldName: String): Boolean {
        val method = instance.javaClass.getDeclaredMethod(fieldName)
        method.isAccessible = true
        return method.invoke(instance) as Boolean
    }

    private fun invokePrivateUnit(instance: Any, fieldName: String) {
        val method = instance.javaClass.getDeclaredMethod(fieldName)
        method.isAccessible = true
        method.invoke(instance)
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

    private class ThrowingEnableTextOutputPlugin(
        override val id: String,
    ) : TextOutputPlugin<EmptyOptions>(EmptyOptions) {
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set

        override fun enable() {
            enableCount += 1
            throw IllegalStateException("enable failed")
        }

        override fun disable() {
            disableCount += 1
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
