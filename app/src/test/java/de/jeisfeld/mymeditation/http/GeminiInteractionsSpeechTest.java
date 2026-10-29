package de.jeisfeld.mymeditation.http;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class GeminiInteractionsSpeechTest {
    @Test public void deliveryStyleIsMetadataAndEveryTranscriptStaysExact() throws Exception {
        for (String model : new String[] {"gemini-3.8-flash-tts", "gemini-3.8-flash-lite-tts"}) {
            assertEquals("https://generativelanguage.googleapis.com/v1beta/interactions",
                    NativeSpeechProtocol.endpoint(SpeechBackend.GEMINI, model, "Gacrux"));
            for (String text : new String[] {"Atme ruhig.", "Lass los.\nSpüre die Ruhe."}) {
                JSONObject request = NativeSpeechProtocol.request(SpeechBackend.GEMINI, model, "Gacrux", text, " Warm and calm. ");
                JSONObject input = request.getJSONArray("input").getJSONObject(0);
                assertEquals(text, input.getString("text"));
                assertEquals("speech_metadata", input.getJSONArray("annotations").getJSONObject(0).getString("type"));
                assertEquals("Warm and calm.", input.getJSONArray("annotations").getJSONObject(0).getString("style"));
                assertFalse(request.has("contents"));
                assertFalse(request.has("previous_interaction_id"));
                assertFalse(request.getBoolean("store"));
                assertFalse(request.has("stream"));
                assertFalse(request.has("background"));
                assertEquals(model, request.getString("model"));
                assertEquals("Gacrux", request.getJSONObject("generation_config").getJSONArray("speech_config").getJSONObject(0).getString("voice"));
                assertEquals("audio", request.getJSONObject("response_format").getString("type"));
                assertEquals(1, request.getJSONObject("response_format").length());
            }
        }
    }
    @Test public void emptyStyleIsOmittedAndLegacyModelsKeepTheirApi() throws Exception {
        for (String style : new String[] {null, "", "   "}) {
            JSONObject request = NativeSpeechProtocol.request(SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", "Breathe.", style);
            JSONObject input = request.getJSONArray("input").getJSONObject(0);
            assertEquals("Breathe.", input.getString("text"));
            assertFalse(input.has("annotations"));
        }
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-preview-tts:generateContent",
                NativeSpeechProtocol.endpoint(SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore"));
    }
    @Test public void cacheSeparatesModelsStylesAndVoices() throws Exception {
        String base = NativeSpeechProtocol.cacheName(SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore", "Breathe.", "Warm.");
        assertNotEquals(base, NativeSpeechProtocol.cacheName(SpeechBackend.GEMINI, "gemini-2.5-pro-preview-tts", "Kore", "Breathe.", "Warm."));
        String modern = NativeSpeechProtocol.cacheName(SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", "Breathe.", "Warm.");
        assertNotEquals(base, modern);
        assertNotEquals(modern, NativeSpeechProtocol.cacheName(SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Gacrux", "Breathe.", "Warm."));
        assertNotEquals(modern, NativeSpeechProtocol.cacheName(SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", "Breathe.", "Calm."));
    }
    private JSONObject audio(String mime, byte[] data, int rate) throws Exception {
        return new JSONObject().put("type", "audio").put("mime_type", mime).put("sample_rate", rate)
                .put("channels", 1).put("data", Base64.getEncoder().encodeToString(data));
    }
    private JSONObject response(JSONObject... items) throws Exception {
        return new JSONObject().put("status", "completed").put("steps", new JSONArray()
                .put(new JSONObject().put("type", "user_input").put("content", new JSONArray()
                        .put(audio("audio/l16", new byte[] {99, 99}, 24000))))
                .put(new JSONObject().put("type", "model_output").put("content", new JSONArray(items))));
    }
    @Test public void pcmOutputJoinsOnlyModelAudioAndUsesItsSampleRate() throws Exception {
        byte[] wav = GeminiInteractionsSpeech.audio(response(
                new JSONObject().put("type", "text").put("text", "ignored"),
                audio("audio/l16", new byte[] {1, 0}, 44100), audio("audio/l16", new byte[] {2, 0}, 44100)));
        assertEquals(44100, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(24));
        assertArrayEquals(new byte[] {1, 0, 2, 0}, java.util.Arrays.copyOfRange(wav, 44, wav.length));
    }
    @Test public void wavOutputPassesThroughStepsAndDirectAudioField() throws Exception {
        byte[] wav = GeminiInteractionsSpeech.audio(response(audio("audio/l16", new byte[] {1, 0}, 24000)));
        JSONObject output = audio("audio/wav", wav, 24000);
        assertArrayEquals(wav, GeminiInteractionsSpeech.audio(response(output)));
        assertArrayEquals(wav, GeminiInteractionsSpeech.audio(new JSONObject().put("status", "completed").put("output_audio", output)));
    }
    @Test public void missingMalformedAndIncompleteAudioAreRetriedRatherThanCached() throws Exception {
        assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> GeminiInteractionsSpeech.audio(response()));
        assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> GeminiInteractionsSpeech.audio(response(
                new JSONObject().put("type", "audio").put("mime_type", "audio/wav").put("uri", "https://example.com/audio"))));
        JSONObject malformed = audio("audio/l16", new byte[] {1, 0}, 24000).put("data", "%%%");
        assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> GeminiInteractionsSpeech.audio(response(malformed)));
        JSONObject incomplete = response(audio("audio/l16", new byte[] {1, 0}, 24000)).put("status", "incomplete");
        Exception error = assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> GeminiInteractionsSpeech.audio(incomplete));
        assertTrue(error.getMessage().contains("incomplete"));
    }
    @Test public void failuresAndUnsupportedAudioAreNotPlayed() throws Exception {
        assertThrows(IllegalStateException.class, () -> GeminiInteractionsSpeech.audio(response().put("status", "failed")));
        assertThrows(IllegalStateException.class, () -> GeminiInteractionsSpeech.audio(response(
                audio("audio/l16", new byte[] {1, 0}, 24000).put("channels", 2))));
        assertThrows(IllegalStateException.class, () -> GeminiInteractionsSpeech.audio(response(
                audio("audio/l16", new byte[] {1, 0}, 1))));
        JSONObject error = response(audio("audio/l16", new byte[] {1, 0}, 24000));
        error.getJSONArray("steps").getJSONObject(1).put("error", new JSONObject().put("code", 7));
        assertThrows(IllegalStateException.class, () -> GeminiInteractionsSpeech.audio(error));
    }
}
