package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/** Upgrade cleanup: discard retired whole-file recordings, preserving every saved text and sentence recording. */
public final class LegacyAudioCleanup {
    private LegacyAudioCleanup() { }
    public static void run(Context context) {
        File directory = new File(context.getFilesDir(), "offline-meditations");
        // The old format contained only flat, numeric recording/temporary files.
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    if (file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()) && !file.isDirectory()) { file.delete(); }
                } catch (IOException ignored) { /* Retry remaining files on the next launch. */ }
            }
        }
        directory.delete();
        for (int id : PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids)) {
            PreferenceUtil.setSharedPreferenceString("offline_settings_" + id, "");
        }
    }
}
