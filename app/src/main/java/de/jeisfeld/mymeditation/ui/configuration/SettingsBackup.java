package de.jeisfeld.mymeditation.ui.configuration;

import android.content.Context;
import androidx.preference.PreferenceManager;
import de.jeisfeld.mymeditation.audio.MeditationPauses;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.AiProvider;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.SpeechProtocol;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.json.JSONArray;
import org.json.JSONObject;

/** Explicit settings-only format. No draft/library/session data or credentials can be imported. */
public final class SettingsBackup {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String FORMAT = "MyMeditation settings";
    private SettingsBackup() { }
    static Set<String> keys() {
        Set<String> keys = new LinkedHashSet<>(Arrays.asList("system_prompt", "query_template", "claude_model",
                "speech_backend", "speech_style", "speech_speed", VoiceProfiles.PREFERENCE,
                PromptTemplates.Kind.SYSTEM.preference, PromptTemplates.Kind.QUERY.preference,
                MeditationPauses.PREFERENCE, "pause_seconds", SettingsCollections.AI_MODELS));
        for (SpeechBackend backend : SpeechBackend.values()) {
            if (backend != SpeechBackend.DEVICE) { keys.add(backend.modelKey()); keys.add(backend.voiceKey()); keys.add(SettingsCollections.speechModelsKey(backend)); }
        }
        return keys;
    }
    public static Map<String, String> capture(Context context) {
        Map<String, ?> all = PreferenceManager.getDefaultSharedPreferences(context).getAll();
        Map<String, String> settings = new TreeMap<>();
        for (String key : keys()) { if (all.get(key) instanceof String) { settings.put(key, (String) all.get(key)); } }
        settings.putIfAbsent("system_prompt", "");
        settings.putIfAbsent("query_template", "@TEXT@");
        settings.putIfAbsent("claude_model", context.getString(R.string.default_ai_model));
        settings.putIfAbsent("speech_backend", context.getString(R.string.default_speech_backend));
        settings.putIfAbsent("speech_style", context.getString(R.string.default_speech_style));
        settings.putIfAbsent("speech_speed", context.getString(R.string.default_speech_speed));
        settings.putIfAbsent(VoiceProfiles.PREFERENCE, new VoiceProfiles("{}").json());
        settings.putIfAbsent(MeditationPauses.PREFERENCE, "few");
        for (PromptTemplates.Kind kind : PromptTemplates.Kind.values()) {
            settings.putIfAbsent(kind.preference, new PromptTemplates(kind, "{}").json());
        }
        for (SpeechBackend backend : SpeechBackend.values()) {
            if (backend == SpeechBackend.DEVICE) { continue; }
            settings.putIfAbsent(backend.modelKey(), backend.defaultModel);
            settings.putIfAbsent(backend.voiceKey(), backend.defaultVoice);
        }
        settings.put("pause_seconds", Double.toString(PreferenceUtil.getPauseSeconds()));
        try { SettingsCollections.mergeModels(settings, settings, java.util.Collections.emptyMap()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid custom model choices", e); }
        return settings;
    }
    public static String encode(Map<String, String> settings) throws Exception {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, String> entry : settings.entrySet()) { values.put(entry.getKey(), entry.getValue()); }
        String json = new JSONObject().put("format", FORMAT).put("version", 2).put("settings", values).toString(2);
        decode(json); // Never publish a backup that this version cannot restore.
        return json;
    }
    public static Map<String, String> decode(String json) throws Exception {
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) { throw new IllegalArgumentException("File too large"); }
        JSONObject root = new JSONObject(json);
        if (!FORMAT.equals(root.optString("format")) || !(root.get("version") instanceof Number)
                || (root.getDouble("version") != 1 && root.getDouble("version") != 2)) { throw new IllegalArgumentException("Unsupported settings format"); }
        JSONObject values = root.getJSONObject("settings");
        Map<String, String> settings = new TreeMap<>();
        java.util.Iterator<String> names = values.keys();
        while (names.hasNext()) {
            String key = names.next(); Object value = values.get(key);
            if (!keys().contains(key) || !(value instanceof String)) { throw new IllegalArgumentException("Invalid setting"); }
            settings.put(key, (String) value);
        }
        if (!settings.containsKey("system_prompt") || !settings.containsKey("query_template")
                || !settings.get("query_template").contains("@TEXT@")) { throw new IllegalArgumentException("Invalid prompts"); }
        if (settings.containsKey("claude_model")) { AiProvider.forModel(settings.get("claude_model")); }
        if (settings.containsKey("speech_speed")) { SpeechProtocol.validateSpeed(Double.parseDouble(settings.get("speech_speed"))); }
        if (settings.containsKey("speech_backend") && Arrays.stream(SpeechBackend.values())
                .noneMatch(b -> b.id.equals(settings.get("speech_backend")))) { throw new IllegalArgumentException("Invalid provider"); }
        if (settings.containsKey(MeditationPauses.PREFERENCE) && Arrays.stream(MeditationPauses.Guidance.values())
                .noneMatch(g -> g.id.equals(settings.get(MeditationPauses.PREFERENCE)))) { throw new IllegalArgumentException("Invalid pacing"); }
        if (settings.containsKey("pause_seconds")) {
            double pause = Double.parseDouble(settings.get("pause_seconds"));
            if (!Double.isFinite(pause) || pause < 0 || pause > 30) { throw new IllegalArgumentException("Invalid pause"); }
        }
        for (SpeechBackend backend : SpeechBackend.values()) {
            if (backend == SpeechBackend.DEVICE) { continue; }
            String model = settings.getOrDefault(backend.modelKey(), backend.defaultModel);
            String voice = settings.getOrDefault(backend.voiceKey(), backend.defaultVoice);
            // ElevenLabs may have no saved voices yet, including when another engine is selected.
            if (backend == SpeechBackend.ELEVENLABS && voice.isEmpty()) {
                if (!model.matches("eleven_[A-Za-z0-9_]+")) { throw new IllegalArgumentException("Invalid speech model"); }
            } else { backend.validate(model, voice); }
        }
        for (PromptTemplates.Kind kind : PromptTemplates.Kind.values()) {
            if (!settings.containsKey(kind.preference)) { continue; }
            JSONObject library = new JSONObject(settings.get(kind.preference));
            JSONArray entries = library.getJSONArray("templates");
            if (library.getInt("version") != 1 || new PromptTemplates(kind, library.toString()).list().size() != entries.length()) {
                throw new IllegalArgumentException("Invalid prompt templates");
            }
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                for (String field : new String[] {"id", "name", "text"}) {
                    if (!(entry.get(field) instanceof String)) { throw new IllegalArgumentException("Invalid template field"); }
                }
            }
        }
        if (settings.containsKey(VoiceProfiles.PREFERENCE)) {
            JSONObject profiles = new JSONObject(settings.get(VoiceProfiles.PREFERENCE));
            if (profiles.has("voices")) {
                JSONArray voices = profiles.getJSONArray("voices");
                if (new VoiceProfiles(profiles.toString()).voices().size() != voices.length()) { throw new IllegalArgumentException("Invalid voices"); }
                for (int i = 0; i < voices.length(); i++) {
                    JSONObject voice = voices.getJSONObject(i);
                    if (!(voice.get("id") instanceof String) || !(voice.get("name") instanceof String)) { throw new IllegalArgumentException("Invalid voice"); }
                }
            }
            if (profiles.has("speeds")) {
                JSONObject speeds = profiles.getJSONObject("speeds");
                java.util.Iterator<String> speedKeys = speeds.keys();
                while (speedKeys.hasNext()) {
                    String key = speedKeys.next();
                    if (!(speeds.get(key) instanceof Number) || !key.matches("(openai|gemini|qwen|elevenlabs):.+")) { throw new IllegalArgumentException("Invalid voice speed"); }
                    SpeechProtocol.validateSpeed(speeds.getDouble(key));
                }
            }
        }
        SettingsCollections.validateModels(settings);
        return settings;
    }
    public static void restore(Context context, Map<String, String> settings) throws Exception {
        Map<String, String> checked = decode(encode(settings));
        Map<String, String> existing = capture(context);
        for (PromptTemplates.Kind kind : PromptTemplates.Kind.values()) {
            checked.put(kind.preference, SettingsCollections.mergeTemplates(existing.get(kind.preference),
                    checked.getOrDefault(kind.preference, new PromptTemplates(kind, "{}").json())));
        }
        checked.put(VoiceProfiles.PREFERENCE, SettingsCollections.mergeVoices(existing.get(VoiceProfiles.PREFERENCE),
                checked.getOrDefault(VoiceProfiles.PREFERENCE, "{}")));
        SettingsCollections.mergeModels(checked, existing, checked);
        checked = decode(encode(checked));
        android.content.SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        for (String key : keys()) { editor.remove(key); }
        for (Map.Entry<String, String> entry : checked.entrySet()) { editor.putString(entry.getKey(), entry.getValue()); }
        if (!editor.commit()) { throw new java.io.IOException("Cannot save settings"); }
    }
}
