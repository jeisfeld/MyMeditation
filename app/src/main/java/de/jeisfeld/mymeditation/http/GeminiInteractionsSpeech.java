package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import org.json.JSONArray;
import org.json.JSONObject;

/** Gemini 3.8 uses structured delivery annotations, never instructions in the transcript. */
final class GeminiInteractionsSpeech {
    private GeminiInteractionsSpeech() { }

    static boolean uses(String model) { return model != null && model.startsWith("gemini-3.8-"); }

    static JSONObject request(String model, String voice, String text, String style) throws Exception {
        JSONObject passage = new JSONObject().put("type", "text").put("text", text);
        if (style != null && !style.trim().isEmpty()) {
            passage.put("annotations", new JSONArray().put(new JSONObject()
                    .put("type", "speech_metadata").put("style", style.trim())));
        }
        return new JSONObject().put("model", model).put("input", new JSONArray().put(passage))
                .put("store", false)
                .put("response_format", new JSONObject().put("type", "audio"))
                .put("generation_config", new JSONObject().put("speech_config",
                        new JSONArray().put(new JSONObject().put("voice", voice))));
    }

    static byte[] audio(JSONObject response) throws Exception {
        String status = response.optString("status");
        if (!"completed".equals(status)) {
            if (java.util.Arrays.asList("", "incomplete", "in_progress", "queued", "requires_continuation").contains(status)) {
                throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_INCOMPLETE_REASON.text(
                        status.isEmpty() ? "UNSPECIFIED" : status));
            }
            throw new IllegalStateException(UiText.GEMINI_FAILED.text(status));
        }
        JSONArray errors = response.optJSONArray("errors");
        if (errors != null && errors.length() > 0) { throw new IllegalStateException(UiText.GEMINI_FAILED.text(status + "/errors")); }
        JSONArray parts = new JSONArray();
        JSONObject direct = response.optJSONObject("output_audio");
        if (direct != null) {
            addAudio(parts, direct);
        } else {
            JSONArray steps = response.optJSONArray("steps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    if (!"model_output".equals(step.optString("type"))) { continue; }
                    JSONObject error = step.optJSONObject("error");
                    if (error != null && error.optInt("code", 0) != 0) {
                        throw new IllegalStateException(UiText.GEMINI_FAILED.text("code " + error.optInt("code")));
                    }
                    JSONArray content = step.optJSONArray("content");
                    if (content == null) { continue; }
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject item = content.getJSONObject(j);
                        if ("audio".equals(item.optString("type"))) { addAudio(parts, item); }
                    }
                }
            }
        }
        return NativeSpeechProtocol.geminiPartsAudio(parts);
    }

    private static void addAudio(JSONArray parts, JSONObject audio) throws Exception {
        if (audio.optInt("channels", 1) != 1) { throw new IllegalStateException(UiText.GEMINI_UNSUPPORTED_AUDIO.text()); }
        String data = audio.optString("data", "");
        if (data.isEmpty()) { throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_NO_AUDIO.text()); }
        String mime = audio.optString("mime_type", "").toLowerCase(java.util.Locale.ROOT);
        if (mime.startsWith("audio/l16") || mime.startsWith("audio/pcm")) {
            mime = "audio/L16;rate=" + audio.optInt("sample_rate", 24000);
        }
        parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", mime).put("data", data)));
    }
}
