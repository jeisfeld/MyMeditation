package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Native Gemini/Qwen speech formats. No credentials are put in URLs or cache keys. */
final class NativeSpeechProtocol {
    private NativeSpeechProtocol() { }
    static String endpoint(SpeechBackend backend, String model, String voice) {
        if (backend == SpeechBackend.ELEVENLABS) {
            backend.validate(model, voice);
            return "https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128";
        }
        if (backend == SpeechBackend.GEMINI) {
            return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        }
        return "https://dashscope-intl.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    }
    static JSONObject request(SpeechBackend backend, String model, String voice, String text, String style) throws Exception {
        backend.validate(model, voice);
        if (text == null || text.trim().isEmpty() || text.length() > backend.segmentLimit) {
            throw new IllegalArgumentException(UiText.PROVIDER_SEGMENT.text(backend.id));
        }
        if (backend == SpeechBackend.ELEVENLABS) {
            return new JSONObject().put("model_id", model).put("text", text)
                    .put("voice_settings", new JSONObject().put("stability", 0.5).put("similarity_boost", 0.75));
        }
        if (backend == SpeechBackend.GEMINI) {
            String prompt = "Read only the passage below exactly, in its original language. Do not add words. "
                    + "Delivery instructions: " + (style == null ? "" : style) + "\nPassage:\n" + text;
            return new JSONObject().put("contents", new JSONArray().put(new JSONObject().put("role", "user")
                    .put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                    .put("generationConfig", new JSONObject().put("responseModalities", new JSONArray().put("AUDIO"))
                            .put("speechConfig", new JSONObject().put("voiceConfig", new JSONObject()
                                    .put("prebuiltVoiceConfig", new JSONObject().put("voiceName", voice)))));
        }
        JSONObject input = new JSONObject().put("text", text).put("voice", voice).put("language_type", "Auto");
        JSONObject parameters = new JSONObject();
        if (model.contains("instruct") && style != null && !style.trim().isEmpty()) {
            parameters.put("instructions", style.trim());
        }
        return new JSONObject().put("model", model).put("input", input).put("parameters", parameters);
    }
    static String cacheName(SpeechBackend backend, String model, String voice, String text, String style) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest((backend.id + ":v1:" + (backend == SpeechBackend.ELEVENLABS ? voice + ":" : "")
                + request(backend, model, voice, text, style)).getBytes(StandardCharsets.UTF_8));
        StringBuilder value = new StringBuilder();
        for (byte b : digest) { value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255)); }
        return value + ".audio";
    }
    static java.net.URL qwenAudioUrl(JSONObject response) throws Exception {
        if (!response.optString("code", "").isEmpty()) { throw new IllegalStateException(UiText.QWEN_REJECTED.text()); }
        java.net.URL url = new java.net.URL(response.getJSONObject("output").getJSONObject("audio").getString("url"));
        String host = url.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!host.endsWith(".aliyuncs.com") || url.getUserInfo() != null
                || !("https".equals(url.getProtocol()) || "http".equals(url.getProtocol()))
                || (url.getPort() != -1 && url.getPort() != 443 && !("http".equals(url.getProtocol()) && url.getPort() == 80))) {
            throw new IllegalStateException(UiText.QWEN_ADDRESS.text(url.getProtocol(), host));
        }
        // Some DashScope responses contain HTTP OSS links. Upgrade known Alibaba storage to TLS.
        if ("http".equals(url.getProtocol())) { url = new java.net.URL("https", host, url.getFile()); }
        return url;
    }
    static byte[] geminiAudio(JSONObject response) throws Exception {
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            JSONObject feedback = response.optJSONObject("promptFeedback");
            String block = feedback == null ? "" : feedback.optString("blockReason", "");
            if (!block.isEmpty() && !"BLOCK_REASON_UNSPECIFIED".equals(block)) {
                throw new IllegalStateException(UiText.GEMINI_BLOCKED.text(block));
            }
            throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_NO_CANDIDATES.text());
        }
        JSONObject candidate = candidates.getJSONObject(0);
        String finish = candidate.optString("finishReason");
        if (!"STOP".equals(finish)) {
            if (java.util.Arrays.asList("", "OTHER", "MAX_TOKENS", "FINISH_REASON_UNSPECIFIED").contains(finish)) {
                throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_INCOMPLETE.text());
            }
            throw new IllegalStateException(UiText.GEMINI_FAILED.text(finish));
        }
        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        if (parts == null) { throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_NO_PARTS.text()); }
        java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
        int rate = 24000;
        for (int i = 0; i < parts.length(); i++) {
            JSONObject data = parts.getJSONObject(i).optJSONObject("inlineData");
            if (data == null) { continue; }
            String mime = data.getString("mimeType");
            if (mime.startsWith("audio/wav") || mime.startsWith("audio/x-wav")) {
                byte[] wav = java.util.Base64.getDecoder().decode(data.getString("data"));
                if (pcm.size() != 0 || parts.length() != 1 || wav.length < 44 || wav.length > 20 * 1024 * 1024
                        || !"RIFF".equals(new String(wav, 0, 4, StandardCharsets.US_ASCII))
                        || !"WAVE".equals(new String(wav, 8, 4, StandardCharsets.US_ASCII))) {
                    throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_INVALID_WAV.text());
                }
                return wav;
            }
            if (!mime.startsWith("audio/L16") && !mime.startsWith("audio/pcm")) { throw new IllegalStateException(UiText.GEMINI_UNSUPPORTED_AUDIO.text()); }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("rate=(\\d+)").matcher(mime);
            int partRate = matcher.find() ? Integer.parseInt(matcher.group(1)) : 24000;
            if (partRate < 8000 || partRate > 96000 || (pcm.size() > 0 && rate != partRate)) { throw new IllegalStateException(UiText.GEMINI_INVALID_RATE.text()); }
            rate = partRate;
            byte[] bytes = java.util.Base64.getDecoder().decode(data.getString("data"));
            if ((bytes.length & 1) != 0 || pcm.size() + bytes.length > 20 * 1024 * 1024) { throw new IllegalStateException(UiText.GEMINI_INVALID_SIZE.text()); }
            pcm.write(bytes);
        }
        if (pcm.size() == 0) { throw new SpeechRetry.TransientGenerationFailure(UiText.GEMINI_NO_AUDIO.text()); }
        byte[] bytes = pcm.toByteArray();
        ByteBuffer wav = ByteBuffer.allocate(44 + bytes.length).order(ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + bytes.length)
                .put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1)
                .putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(StandardCharsets.US_ASCII)).putInt(bytes.length).put(bytes);
        return wav.array();
    }
}
