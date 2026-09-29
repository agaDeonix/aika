package dev.aika.assistant;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        // Android 14+ forbids starting a microphone FGS directly from BOOT_COMPLETED.
        // The selected VoiceInteractionService restarts it through the assistant exemption.
        DiagnosticLog.add(context, "boot completed; waiting for assistant service");
    }
}
