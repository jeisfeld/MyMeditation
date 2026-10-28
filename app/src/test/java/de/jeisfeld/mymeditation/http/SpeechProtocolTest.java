package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.audio.SpeechSegments;
import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import static org.junit.Assert.*;

public class SpeechProtocolTest {
    @Test public void miniTtsUsesMeditationInstructionsAndMp3() throws Exception {
        JSONObject body = SpeechProtocol.request("gpt-4o-mini-tts", "coral", "Breathe.");
        assertEquals("Breathe.", body.getString("input"));
        assertEquals("mp3", body.getString("response_format"));
        assertEquals(1.0, body.getDouble("speed"), 0.001);
        assertTrue(body.getString("instructions").contains("original language"));
    }

    @Test public void legacyModelsOmitUnsupportedInstructions() throws Exception {
        JSONObject body = SpeechProtocol.request("tts-1-hd", "nova", "Breathe.");
        assertFalse(body.has("instructions"));
        assertThrows(IllegalArgumentException.class, () -> SpeechProtocol.validate("tts-1", "coral"));
        assertThrows(IllegalArgumentException.class, () -> SpeechProtocol.validate("gpt-4.1", "coral"));
        assertThrows(IllegalArgumentException.class, () -> SpeechProtocol.validate("gpt-4o-mini-tts", "unknown"));
    }

    @Test public void cacheIdentityChangesWithAudioSettingsAndText() throws Exception {
        String key = SpeechProtocol.cacheName("gpt-4o-mini-tts", "coral", "Breathe.");
        assertEquals(key, SpeechProtocol.cacheName("gpt-4o-mini-tts", "coral", "Breathe."));
        assertNotEquals(key, SpeechProtocol.cacheName("gpt-4o-mini-tts", "nova", "Breathe."));
        assertNotEquals(key, SpeechProtocol.cacheName("gpt-4o-mini-tts", "coral", "Relax."));
        assertNotEquals(key, SpeechProtocol.cacheName("tts-1", "nova", "Breathe."));
        assertTrue(key.matches("[a-f0-9]{64}\\.mp3"));
    }

    @Test public void splittingRetainsTrailingTextAndUnicodeSentences() {
        assertEquals(Arrays.asList("Breathe.", "Relax\n你好！", "Rest?"), SpeechSegments.split("Breathe. Relax\n你好！ Rest?"));
    }

    @Test public void splittingRetainsUnpunctuatedAndEditedText() {
        assertEquals(Arrays.asList("Breathe slowly"), SpeechSegments.split("Breathe slowly"));
        assertTrue(SpeechSegments.split("   ").isEmpty());
        assertTrue(SpeechSegments.split(null).isEmpty());
    }

    @Test public void longSegmentsStayWithinApiLimitWithoutLosingText() throws Exception {
        String text = String.join("", java.util.Collections.nCopies(8000, "a"));
        List<String> segments = SpeechSegments.split(text);
        assertEquals(3, segments.size());
        assertEquals(text, String.join("", segments));
        for (String segment : segments) {
            assertTrue(segment.length() <= 3500);
            SpeechProtocol.request("gpt-4o-mini-tts", "coral", segment);
        }
        assertThrows(IllegalArgumentException.class, () -> SpeechProtocol.request("gpt-4o-mini-tts", "coral", text));
        assertThrows(IllegalArgumentException.class, () -> SpeechProtocol.request("gpt-4o-mini-tts", "coral", ""));
    }

    @Test public void cachedAudioNeedsNeitherNetworkNorCredentialsAndCancellationWins() throws Exception {
        File root = Files.createTempDirectory("speech-cache-test").toFile();
        File directory = new File(root, "openai-speech");
        assertTrue(directory.mkdir());
        File cached = new File(directory, SpeechProtocol.cacheName("gpt-4o-mini-tts", "coral", "Breathe."));
        try {
            Files.write(cached.toPath(), "test-audio".getBytes(StandardCharsets.UTF_8));
            assertEquals(cached, new OpenAiSpeech().audio(root, "gpt-4o-mini-tts", "coral", "Breathe."));
            OpenAiSpeech request = new OpenAiSpeech();
            request.cancel();
            assertThrows(CancellationException.class, () -> request.audio(root, "gpt-4o-mini-tts", "coral", "Breathe."));
        } finally { cached.delete(); directory.delete(); root.delete(); }
    }
    @Test public void speedAndStyleAreSentAndChangeTheCache() throws Exception {
        JSONObject body = SpeechProtocol.request("gpt-4o-mini-tts", "nova", "Breathe.", 1.2, "Firm and commanding.");
        assertEquals(1.2, body.getDouble("speed"), 0.001);
        assertTrue(body.getString("instructions").contains("Firm and commanding."));
        assertTrue(body.getString("instructions").contains("Do not add words"));
        String key = SpeechProtocol.cacheName("gpt-4o-mini-tts", "nova", "Breathe.", 1.2, "Firm and commanding.");
        assertNotEquals(key, SpeechProtocol.cacheName("gpt-4o-mini-tts", "nova", "Breathe.", 1.0, "Firm and commanding."));
        assertNotEquals(key, SpeechProtocol.cacheName("gpt-4o-mini-tts", "nova", "Breathe.", 1.2, "Warm and sensual."));
        assertFalse(SpeechProtocol.request("tts-1-hd", "nova", "Breathe.", 1.2, "Firm").has("instructions"));
        for (double invalid : new double[] {0, 0.24, 4.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> SpeechProtocol.validateSpeed(invalid));
        }
    }

}
