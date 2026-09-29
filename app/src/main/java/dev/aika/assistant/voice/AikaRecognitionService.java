package dev.aika.assistant.voice;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

public final class AikaRecognitionService extends RecognitionService {
    @Override protected void onStartListening(Intent recognizerIntent, Callback listener) {
        try {
            Bundle ready = new Bundle();
            listener.readyForSpeech(ready);
            listener.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (RemoteException ignored) {
        }
    }

    @Override protected void onCancel(Callback listener) {}

    @Override protected void onStopListening(Callback listener) {
        try {
            listener.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (RemoteException ignored) {
        }
    }
}
