package dev.aika.assistant.actions;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

import dev.aika.assistant.DiagnosticLog;

public final class AikaAccessibilityService extends AccessibilityService {
    private static final int MAX_QUEUE = 3;
    private static volatile WeakReference<AikaAccessibilityService> instance = new WeakReference<>(null);
    private static volatile String foregroundPackage = "неизвестно";

    private final ArrayDeque<Command> queue = new ArrayDeque<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;

    @Override protected void onServiceConnected() {
        instance = new WeakReference<>(this);
        DiagnosticLog.add(this, "accessibility connected");
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() != null) foregroundPackage = event.getPackageName().toString();
    }

    @Override public void onInterrupt() {
        DiagnosticLog.add(this, "accessibility interrupted");
    }

    @Override public boolean onUnbind(android.content.Intent intent) {
        instance.clear();
        return super.onUnbind(intent);
    }

    public static String foregroundPackage() {
        return foregroundPackage;
    }

    public static boolean enqueue(Context context, Command command) {
        AikaAccessibilityService service = instance.get();
        if (service == null) {
            DiagnosticLog.add(context, "action rejected; accessibility disabled: " + command);
            return false;
        }
        service.main.post(() -> service.add(command));
        return true;
    }

    private void add(Command command) {
        if (queue.size() >= MAX_QUEUE) {
            DiagnosticLog.add(this, "action queue full; dropped: " + command);
            return;
        }
        queue.add(command);
        runNext();
    }

    private void runNext() {
        if (busy) return;
        Command command = queue.poll();
        if (command == null) return;
        busy = true;
        DiagnosticLog.add(this, "action start: " + command + "; app=" + foregroundPackage);
        switch (command) {
            case BACK -> finishSimple(command, performGlobalAction(GLOBAL_ACTION_BACK));
            case HOME -> finishSimple(command, performGlobalAction(GLOBAL_ACTION_HOME));
            case PAUSE -> finishSimple(command, sendMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE));
            case PLAY -> finishSimple(command, sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY));
            case NEXT -> swipeUp(command);
        }
    }

    private boolean sendMediaKey(int keyCode) {
        try {
            AudioManager audio = getSystemService(AudioManager.class);
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
            return true;
        } catch (SecurityException exception) {
            DiagnosticLog.add(this, "media key denied; using play/pause global action");
            return Build.VERSION.SDK_INT >= 36
                    && performGlobalAction(GLOBAL_ACTION_MEDIA_PLAY_PAUSE);
        }
    }

    private void swipeUp(Command command) {
        float width = getResources().getDisplayMetrics().widthPixels;
        float height = getResources().getDisplayMetrics().heightPixels;
        Path path = new Path();
        path.moveTo(width * 0.5f, height * 0.78f);
        path.lineTo(width * 0.5f, height * 0.22f);
        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(path, 0, 260);
        GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
        boolean accepted = dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription gestureDescription) {
                complete(command, true);
            }

            @Override public void onCancelled(GestureDescription gestureDescription) {
                complete(command, false);
            }
        }, null);
        if (!accepted) complete(command, false);
    }

    private void finishSimple(Command command, boolean success) {
        main.postDelayed(() -> complete(command, success), 120);
    }

    private void complete(Command command, boolean success) {
        DiagnosticLog.add(this, "action " + (success ? "done: " : "failed: ") + command);
        busy = false;
        runNext();
    }
}
