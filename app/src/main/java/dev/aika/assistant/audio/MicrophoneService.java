package dev.aika.assistant.audio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Build;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.File;

import dev.aika.assistant.DiagnosticLog;
import dev.aika.assistant.MainActivity;
import dev.aika.assistant.Prefs;
import dev.aika.assistant.R;
import dev.aika.assistant.actions.AikaAccessibilityService;
import dev.aika.assistant.actions.Command;
import dev.aika.assistant.actions.CommandParser;

public final class MicrophoneService extends Service {
    private static final int NOTIFICATION_ID = 41;
    private static final String CHANNEL = "aika_listening";
    private static final String ACTION_STOP = "dev.aika.assistant.STOP";
    private static final int SAMPLE_RATE = 16_000;
    private static final String GRAMMAR = "[\"айка дальше\",\"айка стоп\",\"айка продолжи\",\"айка назад\",\"айка домой\",\"айка закрой\",\"[unk]\"]";

    private volatile boolean running;
    private volatile boolean destroyed;
    private Thread worker;
    private AudioRecord audioRecord;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                stopRecognizer("screen locked/off");
            } else {
                main.postDelayed(() -> {
                    if (canListenNow()) startRecognizer();
                }, 500);
            }
        }
    };

    public static void start(Context context) {
        context.startForegroundService(new Intent(context, MicrophoneService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        destroyed = false;
        createChannel();
        startForeground(NOTIFICATION_ID, notification(getString(R.string.notification_preparing)), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(screenReceiver, filter);
        }
        DiagnosticLog.add(this, "microphone service created");
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            Prefs.setListeningEnabled(this, false);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!Prefs.isListeningEnabled(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (canListenNow()) startRecognizer();
        else updateNotification(getString(R.string.notification_locked));
        return START_STICKY;
    }

    @Override public void onDestroy() {
        destroyed = true;
        main.removeCallbacksAndMessages(null);
        stopRecognizer("service destroyed");
        unregisterReceiver(screenReceiver);
        DiagnosticLog.add(this, "microphone service destroyed");
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private synchronized void startRecognizer() {
        if (running || worker != null) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            DiagnosticLog.add(this, "microphone permission missing");
            stopSelf();
            return;
        }
        running = true;
        worker = new Thread(this::recognitionLoop, "aika-recognizer");
        worker.start();
    }

    private void recognitionLoop() {
        Model model = null;
        Recognizer recognizer = null;
        try {
            updateNotification(getString(R.string.notification_preparing_model));
            File modelDir = ModelInstaller.install(this);
            model = new Model(modelDir.getAbsolutePath());
            recognizer = new Recognizer(model, SAMPLE_RATE, GRAMMAR);

            int min = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int size = Math.max(min * 2, 8192);
            audioRecord = createAudioRecord(size);
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord not initialized");
            }
            byte[] buffer = new byte[size];
            audioRecord.startRecording();
            DiagnosticLog.add(this, "local recognizer listening");
            updateNotification(getString(R.string.notification_listening));

            while (running) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) continue;
                if (recognizer.acceptWaveForm(buffer, read)) {
                    JSONObject result = new JSONObject(recognizer.getResult());
                    handleText(result.optString("text", ""));
                }
            }
        } catch (Exception exception) {
            DiagnosticLog.add(this, "recognizer error: " + exception.getClass().getSimpleName()
                    + ": " + exception.getMessage());
            updateNotification(getString(R.string.notification_model_error));
        } finally {
            releaseAudio();
            if (recognizer != null) recognizer.close();
            if (model != null) model.close();
            synchronized (this) { worker = null; }
            if (!destroyed && canListenNow()) {
                main.postDelayed(this::startRecognizer, 250);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private AudioRecord createAudioRecord(int bufferSize) {
        // startRecognizer checks RECORD_AUDIO immediately before starting this worker.
        // SecurityException is still caught by recognitionLoop if permission is revoked mid-start.
        return new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    }

    private void handleText(String text) {
        if (text.isBlank()) return;
        String foreground = AikaAccessibilityService.foregroundPackage();
        Command command = CommandParser.parse(text, foreground);
        DiagnosticLog.add(this, "recognized: " + text + (command == null ? " (ignored)" : " -> " + command));
        if (command != null) AikaAccessibilityService.enqueue(this, command);
    }

    private synchronized void stopRecognizer(String reason) {
        if (!running && worker == null) return;
        running = false;
        releaseAudio();
        DiagnosticLog.add(this, "recognizer stopped: " + reason);
        updateNotification(getString(R.string.notification_paused));
    }

    private void releaseAudio() {
        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record == null) return;
        try { record.stop(); } catch (IllegalStateException ignored) {}
        record.release();
    }

    private boolean canListenNow() {
        PowerManager power = getSystemService(PowerManager.class);
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        return Prefs.isListeningEnabled(this)
                && power != null && power.isInteractive()
                && keyguard != null && !keyguard.isKeyguardLocked();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification notification(String state) {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stopIntent = new Intent(this, MicrophoneService.class).setAction(ACTION_STOP);
        PendingIntent stop = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(state)
                .setContentIntent(open)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, getString(R.string.notification_stop), stop).build())
                .build();
    }

    private void updateNotification(String state) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(state));
    }
}
