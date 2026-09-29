package dev.aika.assistant;

import android.content.Context;

public final class Prefs {
    private static final String FILE = "aika";
    private static final String LISTENING = "listening_enabled";

    private Prefs() {}

    public static boolean isListeningEnabled(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .getBoolean(LISTENING, false);
    }

    public static void setListeningEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
                .edit().putBoolean(LISTENING, enabled).apply();
    }
}
