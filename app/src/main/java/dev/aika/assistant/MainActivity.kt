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
                        Text("Айка", fontWeight = FontWeight.Bold)
                        Text("Локальный голосовой помощник", fontSize = 12.sp)
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
                if (state.listeningEnabled) "Айка слушает" else "Айка на паузе",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (state.listeningEnabled) "Скажите «Айка, дальше» или другую настроенную команду."
                else "Включите прослушивание, когда планшет разблокирован.",
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
                Text("Локальное прослушивание", color = Color.White, fontWeight = FontWeight.Medium)
            }
            Text(
                "Аудио не сохраняется и не отправляется в сеть. Android показывает системный индикатор микрофона.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SetupCard(state: MainState, onIntent: (MainIntent) -> Unit) {
    SectionCard("Настройка", if (state.setupComplete) "Готово к работе" else "Выполните три шага") {
        SetupRow("1", "Микрофон", state.microphoneGranted, "Разрешить") {
            onIntent(MainIntent.GrantMicrophone)
        }
        HorizontalDivider()
        SetupRow("2", "Системный ассистент", state.assistantSelected, "Выбрать") {
            onIntent(MainIntent.SelectAssistant)
        }
        HorizontalDivider()
        SetupRow("3", "Управление экраном", state.accessibilityEnabled, "Включить") {
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
        if (enabled) Text("Включено", color = Success, fontSize = 13.sp)
        else OutlinedButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun CommandsCard(onIntent: (MainIntent) -> Unit) {
    SectionCard("Команды", "Кнопки позволяют проверить действия без голоса") {
        CommandRow("Айка, дальше", "YouTube · свайп вверх") {
            onIntent(MainIntent.TestCommand(Command.NEXT))
        }
        CommandRow("Айка, стоп", "YouTube · пауза") {
            onIntent(MainIntent.TestCommand(Command.PAUSE))
        }
        CommandRow("Айка, продолжи", "YouTube · воспроизведение") {
            onIntent(MainIntent.TestCommand(Command.PLAY))
        }
        CommandRow("Айка, назад", "Глобально · Back") {
            onIntent(MainIntent.TestCommand(Command.BACK))
        }
        CommandRow("Айка, домой / закрой", "Глобально · Home") {
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
            Text("Тест")
        }
    }
}

@Composable
private fun DiagnosticsCard(state: MainState) {
    SectionCard("Диагностика", "Текущее приложение: ${state.foregroundPackage}") {
        if (state.diagnosticEvents.isEmpty()) {
            Text("Событий пока нет", color = Ink.copy(alpha = 0.6f))
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
