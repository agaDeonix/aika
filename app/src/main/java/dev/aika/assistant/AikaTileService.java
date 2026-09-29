package dev.aika.assistant;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import dev.aika.assistant.audio.MicrophoneService;

public final class AikaTileService extends TileService {
    @Override public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override public void onClick() {
        super.onClick();
        boolean enable = !Prefs.isListeningEnabled(this);
        if (enable && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Intent activity = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openApp = PendingIntent.getActivity(
                    this, 2, activity, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            openPermissionScreen(activity, openApp);
            return;
        }
        Prefs.setListeningEnabled(this, enable);
        if (enable) MicrophoneService.start(this);
        else stopService(new Intent(this, MicrophoneService.class));
        updateTile();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    private void openPermissionScreen(Intent intent, PendingIntent pendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean enabled = Prefs.isListeningEnabled(this);
        tile.setState(enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setSubtitle(getString(enabled ? R.string.tile_listening : R.string.tile_paused));
        tile.updateTile();
    }
}
