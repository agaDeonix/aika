package dev.aika.assistant.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainReducerTest {
    @Test
    fun `snapshot replaces platform state and derives setup readiness`() {
        val next = MainReducer.reduce(
            MainState(message = "keep me"),
            MainMutation.SnapshotLoaded(
                PlatformSnapshot(
                    microphoneGranted = true,
                    assistantSelected = true,
                    accessibilityEnabled = true,
                    listeningEnabled = true,
                    foregroundPackage = "com.google.android.youtube",
                    diagnosticEvents = listOf("ready"),
                )
            ),
        )

        assertTrue(next.setupComplete)
        assertTrue(next.listeningEnabled)
        assertEquals("com.google.android.youtube", next.foregroundPackage)
        assertEquals("keep me", next.message)
    }

    @Test
    fun `message is explicit one-shot state`() {
        val shown = MainReducer.reduce(MainState(), MainMutation.ShowMessage("Ошибка"))
        assertEquals("Ошибка", shown.message)
        assertFalse(shown.setupComplete)

        val cleared = MainReducer.reduce(shown, MainMutation.ClearMessage)
        assertNull(cleared.message)
    }
}
