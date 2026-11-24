package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.AiClient;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Main-thread title suggestions. Network callbacks never replace a name edited by the user. */
public final class MeditationTitles {
    private static final String CACHE = "meditation_title_cache";
    private static final String POLICY = "distinctive-title-v2";
    private static final int MAX_CHARACTERS = 30;
    private static final Map<String, List<Consumer<String>>> PENDING = new HashMap<>();
    private static final Map<String, Long> FAILED = new HashMap<>();
    private static final Pattern NUMBER = Pattern.compile("^(.+) ([0-9]+)$");
    private MeditationTitles() { }

    public static String model() { return PreferenceUtil.getSharedPreferenceString(R.string.key_ai_model, R.string.default_ai_model); }
    public static String suggestion(Context context, String content) {
        int source = SavedAudioLibrary.restoredContentSource(content);
        if (source > 0) { return nextNumber(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, source)); }
        return unique(base(context, content, model()), 0);
    }
    static String base(Context context, String content, String model) {
        String cached = cached(key(model, content));
        if (!cached.isEmpty()) { return cached; }
        String fallback = shortText(content);
        return fallback.isEmpty() ? context.getString(R.string.default_offline_name) : fallback;
    }
    public static String nextNumber(String name) {
        String base = name == null ? "" : name.trim();
        if (base.isEmpty()) { base = de.jeisfeld.mymeditation.Application.getAppContext().getString(R.string.default_offline_name); }
        int postfixStart = postfixStart(base);
        String postfix = base.substring(postfixStart);
        base = base.substring(0, postfixStart).trim();
        Matcher original = NUMBER.matcher(base);
        BigInteger maximum = BigInteger.ONE;
        if (original.matches()) { base = original.group(1); maximum = maximum.max(new BigInteger(original.group(2))); }
        for (int id : PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids)) {
            String saved = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id);
            String title = saved == null ? "" : saved.trim();
            Matcher match = NUMBER.matcher(title.substring(0, postfixStart(title)).trim());
            if (match.matches() && base.equals(match.group(1))) { maximum = maximum.max(new BigInteger(match.group(2))); }
        }
        return base + " " + maximum.add(BigInteger.ONE) + (postfix.isEmpty() ? "" : " " + postfix);
    }
    /** Strip only balanced trailing parenthetical groups; parentheses inside the topic remain part of its series. */
    private static int postfixStart(String title) {
        int end = title.length(), start = end;
        while (end > 0 && title.charAt(end - 1) == ')') {
            int depth = 0, opening = -1;
            for (int i = end - 1; i >= 0; i--) {
                char value = title.charAt(i);
                if (value == ')') { depth++; }
                else if (value == '(' && --depth == 0) { opening = i; break; }
            }
            if (opening < 0) { break; }
            start = opening; end = opening;
            while (end > 0 && Character.isWhitespace(title.charAt(end - 1))) { end--; }
        }
        // A title made entirely of parentheses has no separate topic to number.
        return end == 0 ? title.length() : start;
    }

    static String unique(String name, int excludingId) {
        for (int id : PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids)) {
            if (id != excludingId && name.equals(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id))) {
                return nextNumber(name);
            }
        }
        return name;
    }
    public static void prefetch(Context context, String content) {
        if (SavedAudioLibrary.restoredContentSource(content) == 0) { request(model(), content, ignored -> { }); }
    }
    /** A late result can improve the open save dialog, provided its suggestion remains untouched. */
    public static void request(String model, String content, Consumer<String> callback) {
        if (content == null || content.trim().isEmpty()) { return; }
        String key = key(model, content), cached = cached(key);
        if (!cached.isEmpty()) { callback.accept(cached); return; }
        if (FAILED.containsKey(key) && System.currentTimeMillis() - FAILED.get(key) < 300000) { return; }
        List<Consumer<String>> callbacks = PENDING.get(key);
        if (callbacks != null) { callbacks.add(callback); return; }
        callbacks = new ArrayList<>(); callbacks.add(callback); PENDING.put(key, callbacks);
        AiClient.createTitle(model, content, (text, error) -> {
            List<Consumer<String>> waiting = PENDING.remove(key);
            String title = error == null ? shortText(text) : "";
            if (title.isEmpty() || title.contains("[") || title.contains("]")) {
                if (FAILED.size() >= 32) { FAILED.clear(); }
                FAILED.put(key, System.currentTimeMillis()); return;
            }
            cache(key, title);
            if (waiting != null) { for (Consumer<String> consumer : waiting) { consumer.accept(title); } }
        });
    }
    static void improveAutomaticName(int id, String content, String model, String expected) {
        request(model, content, title -> {
            if (PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(id)
                    && expected.equals(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id))
                    && content.equals(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_content, id))) {
                PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, unique(title, id));
                SavedAudioLibrary.notifyChanged();
            }
        });
    }
    static String shortText(String text) {
        if (text == null) { return ""; }
        String normalized = text.replaceAll("\\s+", " ").trim().replaceAll("^[\"'„“«]+|[\"'“”»]+$", "");
        String[] words = normalized.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(4, words.length); i++) {
            if (result.codePointCount(0, result.length()) + words[i].codePointCount(0, words[i].length()) + (i == 0 ? 0 : 1) > MAX_CHARACTERS) { break; }
            if (i > 0) { result.append(' '); } result.append(words[i]);
        }
        // A single long word is still a useful fallback; never split a surrogate pair.
        if (result.length() == 0 && !normalized.isEmpty()) {
            return normalized.substring(0, normalized.offsetByCodePoints(0, Math.min(MAX_CHARACTERS, normalized.codePointCount(0, normalized.length()))));
        }
        return result.toString();
    }
    private static String key(String model, String content) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest((POLICY + "\n" + model + "\n" + content).getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder(); for (byte value : bytes) { key.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static JSONArray entries() {
        try { return new JSONArray(PreferenceUtil.getSharedPreferenceString(CACHE, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }
    private static String cached(String key) {
        JSONArray entries = entries();
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry != null && key.equals(entry.optString("key"))) { return entry.optString("title"); }
        }
        return "";
    }
    private static void cache(String key, String title) {
        try {
            JSONArray updated = new JSONArray().put(new JSONObject().put("key", key).put("title", title));
            JSONArray previous = entries();
            for (int i = 0; i < previous.length() && updated.length() < 16; i++) {
                JSONObject entry = previous.optJSONObject(i);
                if (entry != null && !key.equals(entry.optString("key"))) { updated.put(entry); }
            }
            PreferenceUtil.setSharedPreferenceString(CACHE, updated.toString());
        } catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
    }
}
