package dev.aika.assistant

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.aika.assistant.actions.Command
import dev.aika.assistant.ui.MainEffect
import dev.aika.assistant.ui.MainIntent
import dev.aika.assistant.ui.MainState
import dev.aika.assistant.ui.MainStore

class MainActivity : ComponentActivity() {
    private val store: MainStore by viewModels()
    private val microphonePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { store.dispatch(MainIntent.Refresh) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AikaTheme {
                val state by store.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(store) {
                    store.effects.collect(::handleEffect)
                }
                LaunchedEffect(state.message) {
                    state.message?.let {
                        snackbar.showSnackbar(it)
                        store.dispatch(MainIntent.MessageShown)
                    }
                }
                AikaScreen(state, store::dispatch, snackbar)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        store.dispatch(MainIntent.Refresh)
    }

    private fun handleEffect(effect: MainEffect) {
        when (effect) {
            MainEffect.RequestMicrophone -> microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            MainEffect.OpenAccessibilitySettings -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            MainEffect.OpenAssistantSettings -> openAssistantSettings()
        }
    }

    private fun openAssistantSettings() {
        runCatching {
            // ROLE_ASSISTANT is selected by the user in system settings on Android 16.
            startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}

private val Purple = Color(0xFF6750A4)
private val PalePurple = Color(0xFFF7F2FA)
private val Ink = Color(0xFF1D1B20)
private val Success = Color(0xFF216E39)

@Composable
private fun AikaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Purple,
            background = PalePurple,
            surface = Color.White,
            onBackground = Ink,
        ),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AikaScreen(
    state: MainState,
    onIntent: (MainIntent) -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.app_subtitle), fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PalePurple),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = PalePurple,
    ) { padding ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
        ) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { HeroCard(state, onIntent) }
                        item { SetupCard(state, onIntent) }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { CommandsCard(onIntent) }
                        item { DiagnosticsCard(state) }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { HeroCard(state, onIntent) }
                    item { SetupCard(state, onIntent) }
                    item { CommandsCard(onIntent) }
                    item { DiagnosticsCard(state) }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(state: MainState, onIntent: (MainIntent) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Purple),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(if (state.listeningEnabled) R.string.status_listening else R.string.status_paused),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(if (state.listeningEnabled) R.string.listening_hint_on else R.string.listening_hint_off),
                color = Color.White.copy(alpha = 0.86f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.listeningEnabled,
                    onCheckedChange = {
                        onIntent(if (it) MainIntent.StartListening else MainIntent.StopListening)
                    },
                )
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.local_listening), color = Color.White, fontWeight = FontWeight.Medium)
            }
            Text(
                stringResource(R.string.privacy_summary),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SetupCard(state: MainState, onIntent: (MainIntent) -> Unit) {
    SectionCard(
        stringResource(R.string.setup_title),
        stringResource(if (state.setupComplete) R.string.setup_ready else R.string.setup_steps),
    ) {
        SetupRow("1", stringResource(R.string.microphone), state.microphoneGranted, stringResource(R.string.allow)) {
            onIntent(MainIntent.GrantMicrophone)
        }
        HorizontalDivider()
        SetupRow("2", stringResource(R.string.system_assistant), state.assistantSelected, stringResource(R.string.select)) {
            onIntent(MainIntent.SelectAssistant)
        }
        HorizontalDivider()
        SetupRow("3", stringResource(R.string.screen_control), state.accessibilityEnabled, stringResource(R.string.enable)) {
            onIntent(MainIntent.EnableAccessibility)
        }
    }
}

@Composable
private fun SetupRow(
    number: String,
    label: String,
    enabled: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.width(36.dp).height(36.dp),
            shape = RoundedCornerShape(18.dp),
            color = if (enabled) Success else PalePurple,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(if (enabled) "✓" else number, color = if (enabled) Color.White else Purple, fontWeight = FontWeight.Bold)
            }
        }
        Text(label, Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Medium)
        if (enabled) Text(stringResource(R.string.enabled), color = Success, fontSize = 13.sp)
        else OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun CommandsCard(onIntent: (MainIntent) -> Unit) {
    SectionCard(stringResource(R.string.commands_title), stringResource(R.string.commands_subtitle)) {
        CommandRow(stringResource(R.string.command_next), stringResource(R.string.youtube_swipe)) {
            onIntent(MainIntent.TestCommand(Command.NEXT))
        }
        CommandRow(stringResource(R.string.command_stop), stringResource(R.string.youtube_pause)) {
            onIntent(MainIntent.TestCommand(Command.PAUSE))
        }
        CommandRow(stringResource(R.string.command_continue), stringResource(R.string.youtube_play)) {
            onIntent(MainIntent.TestCommand(Command.PLAY))
        }
        CommandRow(stringResource(R.string.command_back), stringResource(R.string.global_back)) {
            onIntent(MainIntent.TestCommand(Command.BACK))
        }
        CommandRow(stringResource(R.string.command_home), stringResource(R.string.global_home)) {
            onIntent(MainIntent.TestCommand(Command.HOME))
        }
    }
}

@Composable
private fun CommandRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Ink.copy(alpha = 0.62f), fontSize = 13.sp)
        }
        Button(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp)) {
            Text(stringResource(R.string.test))
        }
    }
}

@Composable
private fun DiagnosticsCard(state: MainState) {
    val foregroundPackage = state.foregroundPackage.ifBlank { stringResource(R.string.unknown) }
    SectionCard(
        stringResource(R.string.diagnostics_title),
        stringResource(R.string.current_app, foregroundPackage),
    ) {
        if (state.diagnosticEvents.isEmpty()) {
            Text(stringResource(R.string.no_events), color = Ink.copy(alpha = 0.6f))
        } else {
            state.diagnosticEvents.take(12).forEach { event ->
                Text(event, fontSize = 12.sp, modifier = Modifier.padding(vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Ink.copy(alpha = 0.62f), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
