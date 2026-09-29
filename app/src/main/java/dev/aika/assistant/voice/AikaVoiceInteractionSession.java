package dev.aika.assistant.voice;

import android.content.Context;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;

import dev.aika.assistant.DiagnosticLog;
import dev.aika.assistant.Prefs;
import dev.aika.assistant.audio.MicrophoneService;

final class AikaVoiceInteractionSession extends VoiceInteractionSession {
    private final Context context;

    AikaVoiceInteractionSession(Context context) {
        super(context);
        this.context = context;
    }

    @Override public void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        DiagnosticLog.add(context, "assistant invoked by system gesture");
        Prefs.setListeningEnabled(context, true);
        MicrophoneService.start(context);
        finish();
    }
}
