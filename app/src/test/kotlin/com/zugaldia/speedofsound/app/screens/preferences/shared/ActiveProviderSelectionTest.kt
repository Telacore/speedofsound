package com.zugaldia.speedofsound.app.screens.preferences.shared

import com.zugaldia.speedofsound.core.desktop.settings.SelectableProviderSetting
import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveProviderSelectionTest {
    @Test
    fun `resolveSelectedProviderIndex returns matching index when provider is visible`() {
        val providers = listOf(
            TestProvider(id = "voice-a", name = "Alpha"),
            TestProvider(id = "voice-z", name = "Zulu"),
        )

        assertEquals(1, resolveSelectedProviderIndex("voice-z", providers))
        assertEquals(-1, resolveSelectedProviderIndex("missing", providers))
    }

    @Test
    fun `restoreSelectedProviderIndex falls back to first provider when selection is missing`() {
        val providers = listOf(
            TestProvider(id = "voice-a", name = "Alpha"),
            TestProvider(id = "voice-z", name = "Zulu"),
        )

        assertEquals(0, restoreSelectedProviderIndex("missing", providers))
        assertEquals(1, restoreSelectedProviderIndex("voice-z", providers))
    }

    private data class TestProvider(
        override val id: String,
        override val name: String,
    ) : SelectableProviderSetting
}
