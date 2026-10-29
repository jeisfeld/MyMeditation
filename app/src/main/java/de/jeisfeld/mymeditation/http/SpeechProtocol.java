package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
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
            "shimmer", "sage", "coral", "nova", "marin", "alloy", "fable", "verse", "cedar", "ash", "ballad", "echo", "onyx"));
    public static final String DEFAULT_STYLE = "Speak naturally as a meditation guide, with a warm, clear tone.";
    private static final String INSTRUCTIONS = "Read only the supplied text, in its original language. Do not add words.";
    private SpeechProtocol() { }

    public static void validate(String model, String voice) {
        if (model == null || !(DEFAULT_MODEL.equals(model) || model.startsWith(DEFAULT_MODEL + "-")
                || "tts-1".equals(model) || "tts-1-hd".equals(model))) {
            throw new IllegalArgumentException(UiText.OPENAI_TTS_MODEL.text());
        }
        if (!VOICES.contains(voice)) { throw new IllegalArgumentException(UiText.OPENAI_VOICE.text()); }
        if (model.startsWith("tts-1") && !Arrays.asList("shimmer", "nova", "alloy", "fable", "echo", "onyx").contains(voice)) {
            throw new IllegalArgumentException(UiText.LEGACY_VOICES.text());
        }
    }

    public static void validateSpeed(double speed) {
        if (Double.isNaN(speed) || Double.isInfinite(speed) || speed < 0.25 || speed > 4.0) {
            throw new IllegalArgumentException(UiText.INVALID_SPEED.text());
        }
    }

    static JSONObject request(String model, String voice, String text) throws JSONException {
        return request(model, voice, text, 1.0, DEFAULT_STYLE);
    }

    static JSONObject request(String model, String voice, String text, double speed, String style) throws JSONException {
        validateSpeed(speed);
        validate(model, voice);
        if (text == null || text.trim().isEmpty() || text.length() > 3500) {
            throw new IllegalArgumentException(UiText.SEGMENT_LENGTH.text());
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
