package de.jeisfeld.mymeditation.audio;

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
        if (SpeechSegments.split(text, backend.segmentLimit).isEmpty()) { throw new IllegalArgumentException("There is no meditation text to speak."); }
        SpeechProtocol.validateSpeed(speed);
        if (backend != SpeechBackend.DEVICE) { backend.validate(model, voice); }
        this.text = text; this.backend = backend; this.model = model; this.voice = voice;
        this.speed = speed; this.style = style; this.pauseSeconds = Math.max(0, Math.min(30, pauseSeconds));
    }
    public PlaybackRequest withPause(int pause) { return new PlaybackRequest(text, backend, model, voice, speed, style, pause); }
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
        if (!backend.id.equals(id)) { throw new IllegalArgumentException("Unknown saved speech provider."); }
        return new PlaybackRequest(value.getString("text"), backend, value.getString("model"), value.getString("voice"),
                value.getDouble("speed"), value.optString("style", ""), value.getInt("pause"));
    }
}
