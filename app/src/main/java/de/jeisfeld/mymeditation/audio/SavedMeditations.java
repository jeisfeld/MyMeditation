package de.jeisfeld.mymeditation.audio;

import java.util.ArrayList;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/** Saved text entries use stable numeric IDs. All saves run on main. */
public final class SavedMeditations {
    public static final String NAME_FILTER = "saved_meditation_name_filter", AUDIO_ONLY = "saved_meditation_audio_only";
    private SavedMeditations() { }
    public static String nameFilter() { return PreferenceUtil.getSharedPreferenceString(NAME_FILTER, ""); }
    public static boolean audioOnly() { return Boolean.parseBoolean(PreferenceUtil.getSharedPreferenceString(AUDIO_ONLY, "false")); }
    public static void setNameFilter(String query) { PreferenceUtil.setSharedPreferenceString(NAME_FILTER, query); }
    public static void setAudioOnly(boolean value) { PreferenceUtil.setSharedPreferenceString(AUDIO_ONLY, Boolean.toString(value)); }
    public static void select(int id) {
        setNameFilter(SavedMeditationFilter.namePrefix(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id)));
    }
    public static void rename(int id, String name) {
        if (name != null && !name.trim().isEmpty() && PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(id)) {
            PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, name.trim());
            SavedAudioLibrary.notifyChanged();
        }
    }
    public static int save(String name, String system, String query, String content, String text) {
        return save(name, system, query, content, text, MeditationPauses.Guidance.fromId(
                PreferenceUtil.getSharedPreferenceString(MeditationPauses.PREFERENCE, "few")));
    }
    public static MeditationPauses.Guidance guidance(int id) {
        return MeditationPauses.Guidance.fromId(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_pause_guidance, id));
    }
    public static int save(String name, String system, String query, String content, String text, MeditationPauses.Guidance guidance) {
        int id = PreferenceUtil.getSharedPreferenceInt(R.string.key_max_saved_meditation_id, 0) + 1;
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, name);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_system_prompt, id, system);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_query_template, id, query);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_content, id, content);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id, text);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_pause_guidance, id, guidance.id);
        ArrayList<Integer> ids = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
        java.util.List<Integer> selected = SavedMeditationFilter.select(ids, nameFilter(), audioOnly(),
                entry -> PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, entry),
                entry -> SavedAudioLibrary.hasAudio(de.jeisfeld.mymeditation.Application.getAppContext(), entry));
        int position = selected.isEmpty() ? ids.size() : ids.indexOf(selected.get(selected.size() - 1)) + 1;
        ids.add(position, id);
        PreferenceUtil.setSharedPreferenceInt(R.string.key_max_saved_meditation_id, id);
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, ids);
        return id;
    }
}
