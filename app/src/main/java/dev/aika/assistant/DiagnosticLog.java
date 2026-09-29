package dev.aika.assistant;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DiagnosticLog {
    private static final String FILE = "aika_log";
    private static final int MAX = 100;

    private DiagnosticLog() {}

    public static synchronized void add(Context context, String event) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            String value = prefs.getString("e" + i, null);
            if (value != null) entries.add(value);
        }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(new Date());
        entries.add(time + "  " + event);
        while (entries.size() > MAX) entries.remove(0);
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (int i = 0; i < entries.size(); i++) editor.putString("e" + i, entries.get(i));
        editor.apply();
    }

    public static synchronized String recent(Context context, int count) {
        SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            String value = prefs.getString("e" + i, null);
            if (value != null) entries.add(value);
        }
        int from = Math.max(0, entries.size() - count);
        return String.join("\n", entries.subList(from, entries.size()));
    }
}
