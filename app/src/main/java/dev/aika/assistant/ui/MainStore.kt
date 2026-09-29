package dev.aika.assistant.ui

import android.Manifest
import android.app.Application
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import dev.aika.assistant.DiagnosticLog
import dev.aika.assistant.Prefs
import dev.aika.assistant.R
import dev.aika.assistant.actions.AikaAccessibilityService
import dev.aika.assistant.audio.MicrophoneService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

class MainStore(application: Application) : AndroidViewModel(application) {
    private val context: Application
        get() = getApplication()
    private val mutableState = MutableStateFlow(MainState())
    private val effectChannel = Channel<MainEffect>(Channel.BUFFERED)

    val state: StateFlow<MainState> = mutableState.asStateFlow()
    val effects = effectChannel.receiveAsFlow()

    fun dispatch(intent: MainIntent) {
        when (intent) {
            MainIntent.Refresh -> refresh()
            MainIntent.GrantMicrophone -> effectChannel.trySend(MainEffect.RequestMicrophone)
            MainIntent.SelectAssistant -> effectChannel.trySend(MainEffect.OpenAssistantSettings)
            MainIntent.EnableAccessibility -> effectChannel.trySend(MainEffect.OpenAccessibilitySettings)
            MainIntent.StartListening -> startListening()
            MainIntent.StopListening -> stopListening()
            is MainIntent.TestCommand -> testCommand(intent)
            MainIntent.MessageShown -> mutate(MainMutation.ClearMessage)
        }
    }

    private fun startListening() {
        if (!hasMicrophonePermission()) {
            effectChannel.trySend(MainEffect.RequestMicrophone)
            mutate(MainMutation.ShowMessage(context.getString(R.string.microphone_permission_required)))
            return
        }
        Prefs.setListeningEnabled(context, true)
        runCatching { MicrophoneService.start(context) }
            .onFailure {
                mutate(MainMutation.ShowMessage(context.getString(R.string.microphone_start_failed, it.message.orEmpty())))
            }
        refresh()
    }

    private fun stopListening() {
        Prefs.setListeningEnabled(context, false)
        context.stopService(Intent(context, MicrophoneService::class.java))
        refresh()
    }

    private fun testCommand(intent: MainIntent.TestCommand) {
        val queued = AikaAccessibilityService.enqueue(context, intent.command)
        if (!queued) mutate(MainMutation.ShowMessage(context.getString(R.string.accessibility_required)))
        refresh()
    }

    private fun refresh() {
        val roles = context.getSystemService(RoleManager::class.java)
        val assistantSelected = roles?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true &&
            roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)
        val events = DiagnosticLog.recent(context, 24)
            .lineSequence().filter { it.isNotBlank() }.toList().asReversed()
        mutate(
            MainMutation.SnapshotLoaded(
                PlatformSnapshot(
                    microphoneGranted = hasMicrophonePermission(),
                    assistantSelected = assistantSelected,
                    accessibilityEnabled = isAccessibilityEnabled(),
                    listeningEnabled = Prefs.isListeningEnabled(context),
                    foregroundPackage = AikaAccessibilityService.foregroundPackage(),
                    diagnosticEvents = events,
                )
            )
        )
    }

    private fun hasMicrophonePermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val component = ComponentName(context, AikaAccessibilityService::class.java)
        return enabled.split(':').any { it.equals(component.flattenToString(), ignoreCase = true) }
    }

    private fun mutate(mutation: MainMutation) {
        mutableState.value = MainReducer.reduce(mutableState.value, mutation)
    }
}
