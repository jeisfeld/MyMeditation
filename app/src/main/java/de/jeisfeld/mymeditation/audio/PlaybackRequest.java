package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.i18n.UiText;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.SpeechProtocol;
import org.json.JSONObject;

/** Persisted playback configuration contains text/settings, never API keys. */
public final class PlaybackRequest {
    public final String text, model, voice, style;
    public final SpeechBackend backend;
    public final double speed;
    public final int pauseSeconds;
    public PlaybackRequest(String text, SpeechBackend backend, String model, String voice, double speed, String style, int pauseSeconds) {
        if (SpeechSegments.split(text, backend.segmentLimit).isEmpty()) { throw new IllegalArgumentException(UiText.NO_MEDITATION.text()); }
        SpeechProtocol.validateSpeed(speed);
        if (backend != SpeechBackend.DEVICE) { backend.validate(model, voice); }
        this.text = text; this.backend = backend; this.model = model; this.voice = voice;
        this.speed = speed; this.style = style; this.pauseSeconds = Math.max(0, Math.min(30, pauseSeconds));
    }
    public PlaybackRequest withPause(int pause) { return new PlaybackRequest(text, backend, model, voice, speed, style, pause); }
    /** Compare effective synthesis settings so renames/ignored instructions do not restart audio. */
    public boolean sameSynthesis(PlaybackRequest other) {
        return text.equals(other.text) && backend == other.backend && model.equals(other.model) && voice.equals(other.voice)
                && java.util.Objects.equals(effectiveStyle(), other.effectiveStyle());
    }
    private String effectiveStyle() {
        if (backend == SpeechBackend.DEVICE || backend == SpeechBackend.ELEVENLABS
                || (backend == SpeechBackend.OPENAI && model.startsWith("tts-1"))
                || (backend == SpeechBackend.QWEN && !model.contains("instruct"))) { return ""; }
        return style == null ? "" : style.trim();
    }
    /** Match the current text location when providers have different segment length limits. */
    public int positionFor(PlaybackRequest replacement, int currentPosition) {
        if (!text.equals(replacement.text)) { throw new IllegalArgumentException(UiText.PRESERVE_TEXT.text()); }
        java.util.List<String> oldSegments = SpeechSegments.split(text, backend.segmentLimit);
        java.util.List<String> newSegments = SpeechSegments.split(text, replacement.backend.segmentLimit);
        int target = Math.max(0, Math.min(currentPosition, oldSegments.size() - 1));
        int offset = 0;
        for (int i = 0; i <= target; i++) {
            offset = text.indexOf(oldSegments.get(i), offset);
            if (i < target) { offset += oldSegments.get(i).length(); }
        }
        int search = 0;
        for (int i = 0; i < newSegments.size(); i++) {
            int start = text.indexOf(newSegments.get(i), search);
            int end = start + newSegments.get(i).length();
            if (offset < end) { return i; }
            search = end;
        }
        return newSegments.size() - 1;
    }
    public String json() {
        try {
            return new JSONObject().put("text", text).put("backend", backend.id).put("model", model).put("voice", voice)
                    .put("speed", speed).put("style", style).put("pause", pauseSeconds).toString();
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    public static PlaybackRequest parse(String json) throws Exception {
        JSONObject value = new JSONObject(json);
        String id = value.getString("backend");
        SpeechBackend backend = SpeechBackend.fromId(id);
        if (!backend.id.equals(id)) { throw new IllegalArgumentException(UiText.UNKNOWN_SAVED_PROVIDER.text()); }
        return new PlaybackRequest(value.getString("text"), backend, value.getString("model"), value.getString("voice"),
                value.getDouble("speed"), value.optString("style", ""), value.getInt("pause"));
    }
}
