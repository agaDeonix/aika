package dev.aika.assistant.ui

import dev.aika.assistant.actions.Command

data class MainState(
    val microphoneGranted: Boolean = false,
    val assistantSelected: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val listeningEnabled: Boolean = false,
    val foregroundPackage: String = "",
    val diagnosticEvents: List<String> = emptyList(),
    val message: String? = null,
) {
    val setupComplete: Boolean
        get() = microphoneGranted && assistantSelected && accessibilityEnabled
}

sealed interface MainIntent {
    data object Refresh : MainIntent
    data object GrantMicrophone : MainIntent
    data object SelectAssistant : MainIntent
    data object EnableAccessibility : MainIntent
    data object StartListening : MainIntent
    data object StopListening : MainIntent
    data class TestCommand(val command: Command) : MainIntent
    data object MessageShown : MainIntent
}

sealed interface MainEffect {
    data object RequestMicrophone : MainEffect
    data object OpenAssistantSettings : MainEffect
    data object OpenAccessibilitySettings : MainEffect
}

data class PlatformSnapshot(
    val microphoneGranted: Boolean,
    val assistantSelected: Boolean,
    val accessibilityEnabled: Boolean,
    val listeningEnabled: Boolean,
    val foregroundPackage: String,
    val diagnosticEvents: List<String>,
)

sealed interface MainMutation {
    data class SnapshotLoaded(val snapshot: PlatformSnapshot) : MainMutation
    data class ShowMessage(val text: String) : MainMutation
    data object ClearMessage : MainMutation
}

object MainReducer {
    fun reduce(state: MainState, mutation: MainMutation): MainState = when (mutation) {
        is MainMutation.SnapshotLoaded -> state.copy(
            microphoneGranted = mutation.snapshot.microphoneGranted,
            assistantSelected = mutation.snapshot.assistantSelected,
            accessibilityEnabled = mutation.snapshot.accessibilityEnabled,
            listeningEnabled = mutation.snapshot.listeningEnabled,
            foregroundPackage = mutation.snapshot.foregroundPackage,
            diagnosticEvents = mutation.snapshot.diagnosticEvents,
        )
        is MainMutation.ShowMessage -> state.copy(message = mutation.text)
        MainMutation.ClearMessage -> state.copy(message = null)
    }
}
