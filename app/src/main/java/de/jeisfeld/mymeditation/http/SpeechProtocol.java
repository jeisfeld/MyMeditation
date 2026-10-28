package de.jeisfeld.mymeditation.http;

import org.json.JSONException;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

/** OpenAI speech request settings also form the cache identity. */
public final class SpeechProtocol {
    public static final String DEFAULT_MODEL = "gpt-4o-mini-tts";
    public static final String DEFAULT_VOICE = "coral";
    public static final List<String> VOICES = java.util.Collections.unmodifiableList(Arrays.asList(
            "alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer", "verse", "marin", "cedar"));
    public static final String DEFAULT_STYLE = "Speak naturally as a meditation guide, with a warm, clear tone.";
    private static final String INSTRUCTIONS = "Read only the supplied text, in its original language. Do not add words.";
    private SpeechProtocol() { }

    public static void validate(String model, String voice) {
        if (model == null || !(DEFAULT_MODEL.equals(model) || model.startsWith(DEFAULT_MODEL + "-")
                || "tts-1".equals(model) || "tts-1-hd".equals(model))) {
            throw new IllegalArgumentException("Use gpt-4o-mini-tts, tts-1 or tts-1-hd for OpenAI speech.");
        }
        if (!VOICES.contains(voice)) { throw new IllegalArgumentException("Choose a supported OpenAI voice."); }
        if (model.startsWith("tts-1") && !Arrays.asList("alloy", "echo", "fable", "onyx", "nova", "shimmer").contains(voice)) {
            throw new IllegalArgumentException("For tts-1 models, choose alloy, echo, fable, onyx, nova or shimmer.");
        }
    }

    public static void validateSpeed(double speed) {
        if (Double.isNaN(speed) || Double.isInfinite(speed) || speed < 0.25 || speed > 4.0) {
            throw new IllegalArgumentException("Speed must be between 0.25 and 4.0 (1.0 is normal).");
        }
    }

    static JSONObject request(String model, String voice, String text) throws JSONException {
        return request(model, voice, text, 1.0, DEFAULT_STYLE);
    }

    static JSONObject request(String model, String voice, String text, double speed, String style) throws JSONException {
        validateSpeed(speed);
        validate(model, voice);
        if (text == null || text.trim().isEmpty() || text.length() > 3500) {
            throw new IllegalArgumentException("Speech segments must contain 1–3500 characters.");
        }
        JSONObject body = new JSONObject().put("model", model).put("voice", voice).put("input", text)
                .put("response_format", "mp3").put("speed", speed);
        if (model.startsWith(DEFAULT_MODEL)) { body.put("instructions", (style == null ? "" : style.trim()) + "\n" + INSTRUCTIONS); }
        return body;
    }

    static String cacheName(String model, String voice, String text) throws Exception {
        return cacheName(model, voice, text, 1.0, DEFAULT_STYLE);
    }

    static String cacheName(String model, String voice, String text, double speed, String style) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(request(model, voice, text, speed, style).toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte value : digest) { name.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
        return name + ".mp3";
    }
}
