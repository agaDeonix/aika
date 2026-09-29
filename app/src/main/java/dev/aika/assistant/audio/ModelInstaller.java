package dev.aika.assistant.audio;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class ModelInstaller {
    private static final String ASSET_ROOT = "model";

    private ModelInstaller() {}

    static File install(Context context) throws IOException {
        File destination = new File(context.getFilesDir(), "vosk-model-ru");
        File marker = new File(destination, ".ready");
        if (marker.isFile()) return destination;
        copyTree(context.getAssets(), ASSET_ROOT, destination);
        if (!marker.createNewFile() && !marker.isFile()) {
            throw new IOException("Cannot create model marker");
        }
        return destination;
    }

    private static void copyTree(AssetManager assets, String assetPath, File destination) throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            File parent = destination.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Cannot create " + parent);
            }
            try (InputStream input = assets.open(assetPath);
                 FileOutputStream output = new FileOutputStream(destination)) {
                byte[] buffer = new byte[32 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            }
            return;
        }
        if (!destination.exists() && !destination.mkdirs()) {
            throw new IOException("Cannot create " + destination);
        }
        for (String child : children) {
            copyTree(assets, assetPath + "/" + child, new File(destination, child));
        }
    }
}
