package com.zugaldia.speedofsound.core.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class AppPluginRegistryTest {

    @Test
    fun `setActiveById keeps previous plugin active when new enable fails`() {
        val registry = AppPluginRegistry()
        val first = RecordingPlugin(id = "first")
        val second = RecordingPlugin(id = "second", failOnEnable = true)

        registry.register(AppPluginCategory.TEXT_OUTPUT, first)
        registry.register(AppPluginCategory.TEXT_OUTPUT, second)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, first.id)

        assertFailsWith<IllegalStateException> {
            registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, second.id)
        }

        assertSame(first, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(1, first.enableCount)
        assertEquals(0, first.disableCount)
        assertEquals(1, second.enableCount)
        assertEquals(0, second.disableCount)
    }

    @Test
    fun `setActiveById disables previous plugin after new plugin is activated`() {
        val registry = AppPluginRegistry()
        val first = RecordingPlugin(id = "first")
        val second = RecordingPlugin(id = "second")

        registry.register(AppPluginCategory.TEXT_OUTPUT, first)
        registry.register(AppPluginCategory.TEXT_OUTPUT, second)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, first.id)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, second.id)

        assertSame(second, registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(1, first.enableCount)
        assertEquals(1, first.disableCount)
        assertEquals(1, second.enableCount)
        assertEquals(0, second.disableCount)
    }

    @Test
    fun `register does not retain plugin when initialize fails`() {
        val registry = AppPluginRegistry()
        val broken = RecordingPlugin(id = "broken", failOnInitialize = true)

        assertFailsWith<IllegalStateException> {
            registry.register(AppPluginCategory.TEXT_OUTPUT, broken)
        }

        assertEquals(1, broken.initializeCount)
        assertNull(registry.getPluginById(AppPluginCategory.TEXT_OUTPUT, broken.id))
        assertNull(registry.getActive(AppPluginCategory.TEXT_OUTPUT))
    }

    @Test
    fun `shutdownAll continues after disable and shutdown failures`() {
        val registry = AppPluginRegistry()
        val broken = RecordingPlugin(
            id = "broken",
            failOnDisable = true,
            failOnShutdown = true,
        )
        val healthy = RecordingPlugin(id = "healthy")

        registry.register(AppPluginCategory.TEXT_OUTPUT, broken)
        registry.register(AppPluginCategory.TEXT_OUTPUT, healthy)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, broken.id)

        registry.shutdownAll()

        assertEquals(1, broken.disableCount)
        assertEquals(1, broken.shutdownCount)
        assertEquals(0, healthy.disableCount)
        assertEquals(1, healthy.shutdownCount)
        assertNull(registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertEquals(true, registry.isShutdown())

        registry.shutdownAll()

        assertEquals(1, broken.disableCount)
        assertEquals(1, broken.shutdownCount)
        assertEquals(0, healthy.disableCount)
        assertEquals(1, healthy.shutdownCount)
    }

    @Test
    fun `shutdownAll only disables active plugin in matching category`() {
        val registry = AppPluginRegistry()
        val sharedTextOutput = RecordingPlugin(id = "shared")
        val sharedRecorder = RecordingPlugin(id = "shared")

        registry.register(AppPluginCategory.TEXT_OUTPUT, sharedTextOutput)
        registry.register(AppPluginCategory.RECORDER, sharedRecorder)
        registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, sharedTextOutput.id)

        registry.shutdownAll()

        assertEquals(1, sharedTextOutput.disableCount)
        assertEquals(1, sharedTextOutput.shutdownCount)
        assertEquals(0, sharedRecorder.disableCount)
        assertEquals(1, sharedRecorder.shutdownCount)
        assertNull(registry.getActive(AppPluginCategory.TEXT_OUTPUT))
        assertNull(registry.getActive(AppPluginCategory.RECORDER))
        assertEquals(true, registry.isShutdown())
    }

    @Test
    fun `register and setActiveById fail after shutdownAll`() {
        val registry = AppPluginRegistry()
        val plugin = RecordingPlugin(id = "plugin")

        registry.register(AppPluginCategory.TEXT_OUTPUT, plugin)
        registry.shutdownAll()

        assertFailsWith<IllegalStateException> {
            registry.register(AppPluginCategory.TEXT_OUTPUT, RecordingPlugin(id = "late"))
        }
        assertFailsWith<IllegalStateException> {
            registry.setActiveById(AppPluginCategory.TEXT_OUTPUT, plugin.id)
        }
    }

    private class RecordingPlugin(
        override val id: String,
        private val failOnInitialize: Boolean = false,
        private val failOnEnable: Boolean = false,
        private val failOnDisable: Boolean = false,
        private val failOnShutdown: Boolean = false,
    ) : AppPlugin<EmptyOptions>(EmptyOptions) {
        var initializeCount: Int = 0
            private set
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set
        var shutdownCount: Int = 0
            private set

        override fun initialize() {
            initializeCount += 1
            if (failOnInitialize) {
                error("initialize failed")
            }
        }

        override fun enable() {
            enableCount += 1
            if (failOnEnable) {
                error("enable failed")
            }
        }

        override fun disable() {
            disableCount += 1
            if (failOnDisable) {
                error("disable failed")
            }
        }

        override fun shutdown() {
            shutdownCount += 1
            if (failOnShutdown) {
                error("shutdown failed")
            }
        }
    }
}
