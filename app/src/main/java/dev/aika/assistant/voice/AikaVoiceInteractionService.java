package dev.aika.assistant.voice;

import android.Manifest;
import android.content.pm.PackageManager;
import android.service.voice.VoiceInteractionService;

import dev.aika.assistant.DiagnosticLog;
import dev.aika.assistant.Prefs;
import dev.aika.assistant.audio.MicrophoneService;

public final class AikaVoiceInteractionService extends VoiceInteractionService {
    @Override public void onReady() {
        super.onReady();
        DiagnosticLog.add(this, "voice interaction service ready");
        if (Prefs.isListeningEnabled(this)
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            MicrophoneService.start(this);
        }
    }

    @Override public void onShutdown() {
        DiagnosticLog.add(this, "voice interaction service shutdown");
        super.onShutdown();
    }
}
