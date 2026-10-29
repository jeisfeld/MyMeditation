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
    @Test public void providerSwitchMapsTheCurrentTextLocationAcrossDifferentChunkSizes() {
        String text = String.join("", java.util.Collections.nCopies(3400, "x")) + ". Same. Same.";
        PlaybackRequest gemini = new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore", 1, "Warm", 3);
        PlaybackRequest qwen = new PlaybackRequest(text, SpeechBackend.QWEN, "qwen3-tts-flash", "Cherry", 1, "Warm", 3);
        assertEquals(3, gemini.positionFor(qwen, 1));
        assertEquals(6, gemini.positionFor(qwen, 2));
        // Identical repeated sentences still map to their own occurrence.
        assertEquals(7, gemini.positionFor(qwen, 3));
        assertEquals(8, gemini.positionFor(qwen, 4));
        assertEquals(4, gemini.positionFor(gemini, 4));
    }
    @Test public void speedChangesDoNotRequireNewSynthesisForNativeProvidersAndIgnoredStylesAreEqual() {
        PlaybackRequest first = new PlaybackRequest("Breathe.", SpeechBackend.ELEVENLABS, "eleven_v4", "EXAVITQu4vr4xnSDxMaL", 1, "Warm", 3);
        PlaybackRequest faster = new PlaybackRequest("Breathe.", SpeechBackend.ELEVENLABS, "eleven_v4", "EXAVITQu4vr4xnSDxMaL", 1.3, "Different ignored style", 3);
        assertTrue(first.sameSynthesis(faster));
        PlaybackRequest voice = new PlaybackRequest("Breathe.", SpeechBackend.ELEVENLABS, "eleven_v4", "JBFqnCBsd6RMkjVDRZzb", 1, "Warm", 3);
        assertFalse(first.sameSynthesis(voice));
        PlaybackRequest gemini = new PlaybackRequest("Breathe.", SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore", 1, "Warm", 3);
        PlaybackRequest newStyle = new PlaybackRequest("Breathe.", SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore", 1, "Commanding", 3);
        assertFalse(gemini.sameSynthesis(newStyle));
        PlaybackRequest differentText = new PlaybackRequest("Different text.", SpeechBackend.DEVICE, "", "", 1, "", 1);
        assertThrows(IllegalArgumentException.class, () -> gemini.positionFor(differentText, 0));
    }

}
