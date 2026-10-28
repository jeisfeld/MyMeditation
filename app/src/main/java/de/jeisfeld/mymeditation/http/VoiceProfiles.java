package de.jeisfeld.mymeditation.http;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Local voice names and provider/voice speeds; contains no API credentials. */
public final class VoiceProfiles {
    public static final String PREFERENCE = "speech_voice_profiles";
    public static final class Voice {
        public final String id;
        public final String name;
        Voice(String id, String name) { this.id = id; this.name = name; }
    }
    private final Map<String, String> names = new LinkedHashMap<>();
    private final Map<String, Double> speeds = new LinkedHashMap<>();
    public VoiceProfiles(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray voices = root.optJSONArray("voices");
            if (voices != null) {
                for (int i = 0; i < voices.length(); i++) {
                    JSONObject voice = voices.getJSONObject(i);
                    try { saveVoice(voice.getString("id"), voice.getString("name")); } catch (IllegalArgumentException ignored) { }
                }
            }
            JSONObject stored = root.optJSONObject("speeds");
            if (stored != null) {
                java.util.Iterator<String> keys = stored.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    double speed = stored.optDouble(key, 1.0);
                    try { SpeechProtocol.validateSpeed(speed); speeds.put(key, speed); } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (org.json.JSONException ignored) { }
    }
    public List<Voice> voices() {
        List<Voice> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : names.entrySet()) { result.add(new Voice(entry.getKey(), entry.getValue())); }
        return result;
    }
    public void saveVoice(String id, String name) {
        id = id.trim(); name = name.trim();
        if (!id.matches("[A-Za-z0-9_-]{10,128}")) { throw new IllegalArgumentException("Enter a valid ElevenLabs voice ID."); }
        if (name.isEmpty()) { throw new IllegalArgumentException("Enter a name for this voice."); }
        for (Map.Entry<String, String> entry : names.entrySet()) {
            if (!entry.getKey().equals(id) && entry.getValue().equalsIgnoreCase(name)) {
                throw new IllegalArgumentException("That name is already used by another voice.");
            }
        }
        names.put(id, name);
    }
    public String name(String id) { return names.get(id); }
    public void migrateVoice(String id) {
        if (id == null || id.isEmpty() || names.containsKey(id)) { return; }
        int number = 1;
        while (true) {
            String label = "Saved voice " + number++;
            boolean exists = false;
            for (String name : names.values()) { if (name.equalsIgnoreCase(label)) { exists = true; break; } }
            if (!exists) { try { saveVoice(id, label); } catch (IllegalArgumentException ignored) { } return; }
        }
    }
    public boolean hasSpeed(SpeechBackend backend, String voice) { return speeds.containsKey(backend.id + ":" + voice); }
    public double speed(SpeechBackend backend, String voice, double fallback) {
        Double value = speeds.get(backend.id + ":" + voice);
        return value == null ? fallback : value;
    }
    public void setSpeed(SpeechBackend backend, String voice, double value) {
        SpeechProtocol.validateSpeed(value);
        if (backend != SpeechBackend.DEVICE && voice != null && !voice.isEmpty()) { speeds.put(backend.id + ":" + voice, value); }
    }
    public String json() {
        try {
            JSONArray voices = new JSONArray();
            for (Voice voice : voices()) { voices.put(new JSONObject().put("id", voice.id).put("name", voice.name)); }
            JSONObject values = new JSONObject();
            for (Map.Entry<String, Double> entry : speeds.entrySet()) { values.put(entry.getKey(), entry.getValue()); }
            return new JSONObject().put("voices", voices).put("speeds", values).toString();
        } catch (org.json.JSONException e) { throw new IllegalStateException("Could not save voice profiles.", e); }
    }
}
