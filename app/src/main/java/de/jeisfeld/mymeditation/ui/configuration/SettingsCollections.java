package de.jeisfeld.mymeditation.ui.configuration;

import de.jeisfeld.mymeditation.http.AiProvider;
import de.jeisfeld.mymeditation.http.ModelCatalog;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Additive dropdown collections. Existing identities, names and explicit voice speeds win. */
final class SettingsCollections {
    static final String AI_MODELS = "custom_ai_models";
    static String speechModelsKey(SpeechBackend backend) { return "custom_" + backend.id + "_speech_models"; }
    private SettingsCollections() { }
    static String mergeTemplates(String old, String imported) throws Exception {
        JSONArray result = new JSONObject(old).getJSONArray("templates");
        JSONArray added = new JSONObject(imported).getJSONArray("templates");
        Set<String> ids = new HashSet<>(), names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER), texts = new HashSet<>();
        for (int i = 0; i < result.length(); i++) {
            JSONObject item = result.getJSONObject(i);
            ids.add(item.getString("id")); names.add(item.getString("name").trim()); texts.add(item.getString("text"));
        }
        for (int i = 0; i < added.length(); i++) {
            JSONObject item = new JSONObject(added.getJSONObject(i).toString());
            if (!texts.add(item.getString("text"))) { continue; }
            String id = item.getString("id");
            if (ids.contains(id)) { do { id = UUID.randomUUID().toString(); } while (ids.contains(id)); }
            ids.add(id); item.put("id", id);
            item.put("name", uniqueName(item.getString("name"), names)); result.put(item);
        }
        return new JSONObject().put("version", 1).put("templates", result).toString();
    }
    static String mergeVoices(String old, String imported) throws Exception {
        JSONObject local = new JSONObject(old), incoming = new JSONObject(imported);
        JSONArray result = local.optJSONArray("voices"); if (result == null) { result = new JSONArray(); }
        JSONArray added = incoming.optJSONArray("voices"); if (added == null) { added = new JSONArray(); }
        Set<String> ids = new HashSet<>(), names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < result.length(); i++) {
            ids.add(result.getJSONObject(i).getString("id")); names.add(result.getJSONObject(i).getString("name").trim());
        }
        for (int i = 0; i < added.length(); i++) {
            JSONObject voice = added.getJSONObject(i);
            if (ids.add(voice.getString("id"))) {
                result.put(new JSONObject().put("id", voice.getString("id")).put("name", uniqueName(voice.getString("name"), names)));
            }
        }
        JSONObject speeds = local.optJSONObject("speeds"); if (speeds == null) { speeds = new JSONObject(); }
        JSONObject extra = incoming.optJSONObject("speeds");
        if (extra != null) {
            java.util.Iterator<String> keys = extra.keys();
            while (keys.hasNext()) { String key = keys.next(); if (!speeds.has(key)) { speeds.put(key, extra.get(key)); } }
        }
        return new JSONObject().put("voices", result).put("speeds", speeds).toString();
    }
    private static String uniqueName(String original, Set<String> names) {
        String base = original.trim(), name = base; int suffix = 2;
        while (!names.add(name)) { name = base + " (" + suffix++ + ")"; }
        return name;
    }
    static List<String> models(String json) throws Exception {
        JSONArray array = new JSONArray(json); LinkedHashSet<String> models = new LinkedHashSet<>();
        for (int i = 0; i < array.length(); i++) {
            if (!(array.get(i) instanceof String) || array.getString(i).trim().isEmpty()) { throw new IllegalArgumentException("Invalid custom model"); }
            models.add(array.getString(i));
        }
        return new ArrayList<>(models);
    }
    static void validateModels(Map<String, String> settings) throws Exception {
        for (String model : models(settings.getOrDefault(AI_MODELS, "[]"))) { AiProvider.forModel(model); }
        for (SpeechBackend backend : SpeechBackend.values()) {
            if (backend == SpeechBackend.DEVICE) { continue; }
            for (String model : models(settings.getOrDefault(speechModelsKey(backend), "[]"))) {
                backend.validate(model, backend == SpeechBackend.ELEVENLABS ? "voice123456789" : backend == SpeechBackend.OPENAI ? "nova" : backend.defaultVoice);
            }
        }
    }
    static void mergeModels(Map<String, String> result, Map<String, String> old, Map<String, String> imported) throws Exception {
        result.put(AI_MODELS, mergedModels(AI_MODELS, "claude_model", ModelCatalog.aiModels(), old, imported));
        for (SpeechBackend backend : SpeechBackend.values()) {
            if (backend != SpeechBackend.DEVICE) {
                result.put(speechModelsKey(backend), mergedModels(speechModelsKey(backend), backend.modelKey(), ModelCatalog.speechModels(backend), old, imported));
            }
        }
    }
    private static String mergedModels(String key, String active, List<String> builtIn, Map<String, String> old, Map<String, String> imported) throws Exception {
        LinkedHashSet<String> choices = new LinkedHashSet<>(models(old.getOrDefault(key, "[]")));
        if (old.containsKey(active)) { choices.add(old.get(active)); }
        choices.addAll(models(imported.getOrDefault(key, "[]")));
        if (imported.containsKey(active)) { choices.add(imported.get(active)); }
        choices.removeAll(builtIn); choices.remove("");
        return new JSONArray(choices).toString();
    }
    private static List<String> readModels(String key) {
        try { return models(PreferenceUtil.getSharedPreferenceString(key, "[]")); }
        catch (Exception ignored) { return new ArrayList<>(); }
    }
    static List<String> aiModels() {
        LinkedHashSet<String> choices = new LinkedHashSet<>(ModelCatalog.aiModels()); choices.addAll(readModels(AI_MODELS));
        String active = PreferenceUtil.getSharedPreferenceString("claude_model", "");
        if (!active.isEmpty()) { choices.add(active); }
        return new ArrayList<>(choices);
    }
    static List<String> speechModels(SpeechBackend backend, String active) {
        LinkedHashSet<String> choices = new LinkedHashSet<>(ModelCatalog.speechModels(backend, active));
        choices.addAll(readModels(speechModelsKey(backend))); return new ArrayList<>(choices);
    }
}
