package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackRequestTest {
    @Test public void sessionRoundTripPreservesResumeConfigurationWithoutCredentials() throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe. Relax", SpeechBackend.ELEVENLABS,
                "eleven_multilingual_v2", "EXAVITQu4vr4xnSDxMaL", 1.2, "Warm", 4);
        PlaybackRequest restored = PlaybackRequest.parse(request.json());
        assertEquals(request.text, restored.text);
        assertEquals(request.backend, restored.backend);
        assertEquals(request.voice, restored.voice);
        assertEquals(request.model, restored.model);
        assertEquals(1.2, restored.speed, 0.001);
        assertEquals(4, restored.pauseSeconds);
        assertEquals("Warm", restored.style);
        assertEquals(7, new JSONObject(request.json()).length());
        assertFalse(request.json().contains("API_KEY"));
    }
    @Test public void devicePlaybackCanBeSavedWithoutCloudCredentials() throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe", SpeechBackend.DEVICE, "", "", 1.0, "", 2);
        assertEquals(SpeechBackend.DEVICE, PlaybackRequest.parse(request.json()).backend);
        assertEquals(7, request.withPause(7).pauseSeconds);
        assertEquals(2, request.pauseSeconds);
    }
    @Test public void rejectsInvalidSavedSessionsAndClampsPauseBounds() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new PlaybackRequest(" ", SpeechBackend.DEVICE, "", "", 1, "", 1));
        assertThrows(IllegalArgumentException.class, () -> new PlaybackRequest("Breathe", SpeechBackend.OPENAI, "bad-model", "coral", 1, "", 1));
        PlaybackRequest request = new PlaybackRequest("Breathe", SpeechBackend.DEVICE, "", "", 1, "", 1);
        String invalid = new JSONObject(request.json()).put("backend", "unknown").toString();
        assertThrows(IllegalArgumentException.class, () -> PlaybackRequest.parse(invalid));
        assertEquals(30, request.withPause(900).pauseSeconds);
        assertEquals(0, request.withPause(-1).pauseSeconds);
    }
}
