package de.jeisfeld.mymeditation.audio;

import java.util.ArrayList;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/** Saved text entries use stable numeric IDs. All saves run on main. */
public final class SavedMeditations {
    private SavedMeditations() { }
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
