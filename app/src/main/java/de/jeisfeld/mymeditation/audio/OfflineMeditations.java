package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/** Offline files use numeric saved IDs rather than user-provided paths. All saves run on main. */
public final class OfflineMeditations {
    private OfflineMeditations() { }
    public static File directory(Context context) { return new File(context.getFilesDir(), "offline-meditations"); }
    public static File file(Context context, int id) {
        if (id <= 0) { throw new IllegalArgumentException("Invalid saved meditation"); }
        return new File(directory(context), id + ".mp3");
    }
    public static int save(String name, String system, String query, String content, String text) {
        int id = PreferenceUtil.getSharedPreferenceInt(R.string.key_max_saved_meditation_id, 0) + 1;
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, name);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_system_prompt, id, system);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_query_template, id, query);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_content, id, content);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id, text);
        ArrayList<Integer> ids = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
        ids.add(id);
        PreferenceUtil.setSharedPreferenceInt(R.string.key_max_saved_meditation_id, id);
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, ids);
        return id;
    }
}
