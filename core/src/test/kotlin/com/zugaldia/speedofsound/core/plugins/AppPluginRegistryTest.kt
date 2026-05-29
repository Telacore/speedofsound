package com.zugaldia.speedofsound.core.plugins

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private class RecordingPlugin(
        override val id: String,
        private val failOnEnable: Boolean = false,
        private val failOnDisable: Boolean = false,
    ) : AppPlugin<EmptyOptions>(EmptyOptions) {
        var enableCount: Int = 0
            private set
        var disableCount: Int = 0
            private set

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
    }
}
