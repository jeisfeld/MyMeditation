package de.jeisfeld.mymeditation.http;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import static org.junit.Assert.*;

public class NativeSpeechTest {
    @Test public void providerDefaultsAndSelectionsAreIndependent() {
        assertEquals(SpeechBackend.GEMINI, SpeechBackend.fromId("gemini"));
        assertEquals(SpeechBackend.QWEN, SpeechBackend.fromId("qwen"));
        assertEquals(SpeechBackend.DEVICE, SpeechBackend.fromId("unknown"));
        assertEquals("GEMINI_API_KEY", SpeechBackend.GEMINI.key);
        assertEquals("QWEN_API_KEY", SpeechBackend.QWEN.key);
        assertEquals("speech_model", SpeechBackend.OPENAI.modelKey());
        assertNotEquals(SpeechBackend.GEMINI.modelKey(), SpeechBackend.QWEN.modelKey());
        SpeechBackend.GEMINI.validate("gemini-2.5-pro-preview-tts", "Aoede");
        SpeechBackend.QWEN.validate("qwen3-tts-flash", "Cherry");
        assertThrows(IllegalArgumentException.class, () -> SpeechBackend.QWEN.validate("qwen-audio", "Cherry"));
        assertThrows(IllegalArgumentException.class, () -> SpeechBackend.QWEN.validate("qwen3-tts-realtime", "Cherry"));
        assertThrows(IllegalArgumentException.class, () -> SpeechBackend.GEMINI.validate("gemini-3.8-flash-tts", "Kore"));
    }
    @Test public void requestsUseNativeAudioFormatsAndDeliveryInstructions() throws Exception {
        JSONObject gemini = NativeSpeechProtocol.request(SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore", "Breathe.", "Warm.");
        assertEquals("AUDIO", gemini.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0));
        assertEquals("Kore", gemini.getJSONObject("generationConfig").getJSONObject("speechConfig").getJSONObject("voiceConfig")
                .getJSONObject("prebuiltVoiceConfig").getString("voiceName"));
        assertTrue(gemini.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text").contains("Warm."));
        JSONObject qwen = NativeSpeechProtocol.request(SpeechBackend.QWEN, "qwen3-tts-flash", "Cherry", "Breathe.", "Warm.");
        assertEquals("Auto", qwen.getJSONObject("input").getString("language_type"));
        assertEquals("Breathe.", qwen.getJSONObject("input").getString("text"));
        assertFalse(qwen.getJSONObject("parameters").has("instructions"));
        JSONObject instruct = NativeSpeechProtocol.request(SpeechBackend.QWEN, "qwen3-tts-instruct-flash", "Cherry", "Breathe.", "Firm.");
        assertEquals("Firm.", instruct.getJSONObject("parameters").getString("instructions"));
    }
    private JSONObject pcmResponse(byte[] bytes) throws Exception {
        return new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "STOP")
                .put("content", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("inlineData", new JSONObject()
                        .put("mimeType", "audio/L16;codec=pcm;rate=24000").put("data", Base64.getEncoder().encodeToString(bytes))))))));
    }
    @Test public void geminiPcmIsWrappedAsPlayableWavWithoutChangingSamples() throws Exception {
        byte[] pcm = {1, 0, 2, 0};
        byte[] wav = NativeSpeechProtocol.geminiAudio(pcmResponse(pcm));
        assertEquals(48, wav.length);
        assertEquals("RIFF", new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(24000, ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(24));
        assertArrayEquals(pcm, java.util.Arrays.copyOfRange(wav, 44, wav.length));
        assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> NativeSpeechProtocol.geminiAudio(new JSONObject()));
        assertThrows(IllegalStateException.class, () -> NativeSpeechProtocol.geminiAudio(pcmResponse(new byte[] {1})));
    }
    @Test public void qwenDownloadsAreRestrictedToHttpsAlibabaStorage() throws Exception {
        for (String address : new String[] {"https://example.com/a.wav", "https://files.aliyuncs.com.evil.com/a.wav", "https://user:secret@files.aliyuncs.com/a.wav"}) {
            JSONObject response = new JSONObject().put("output", new JSONObject().put("audio", new JSONObject().put("url", address)));
            assertThrows(IllegalStateException.class, () -> NativeSpeechProtocol.qwenAudioUrl(response));
        }
        JSONObject valid = new JSONObject().put("output", new JSONObject().put("audio", new JSONObject().put("url", "https://dashscope-result-sg.oss-ap-southeast-1.aliyuncs.com/speech.wav?signature=test")));
        valid.put("code", JSONObject.NULL);
        assertEquals("https", NativeSpeechProtocol.qwenAudioUrl(valid).getProtocol());
    }
    @Test public void providerSpecificSegmentsKeepEveryCharacterWithinTheirLimits() {
        String text = String.join("", java.util.Collections.nCopies(2000, "x"));
        for (SpeechBackend backend : new SpeechBackend[] {SpeechBackend.GEMINI, SpeechBackend.QWEN}) {
            java.util.List<String> parts = de.jeisfeld.mymeditation.audio.SpeechSegments.split(text, backend.segmentLimit);
            assertEquals(text, String.join("", parts));
            for (String part : parts) { assertTrue(part.length() <= backend.segmentLimit); }
        }
    }
    @Test public void qwenHttpStorageIsUpgradedWithoutChangingSignedPathOrQuery() throws Exception {
        JSONObject response = new JSONObject().put("output", new JSONObject().put("audio", new JSONObject()
                .put("url", "http://files.aliyuncs.com:80/audio.wav?signature=keep%2Fthis&expires=1")));
        java.net.URL url = NativeSpeechProtocol.qwenAudioUrl(response);
        assertEquals("https", url.getProtocol());
        assertEquals(-1, url.getPort());
        assertEquals("/audio.wav?signature=keep%2Fthis&expires=1", url.getFile());
    }

    @Test public void transientGeminiGenerationFailuresRetryButSafetyBlocksDoNot() throws Exception {
        int[] attempts = {0};
        byte[] audio = SpeechRetry.run(() -> {
            if (++attempts[0] < 3) { return NativeSpeechProtocol.geminiAudio(new JSONObject()); }
            return NativeSpeechProtocol.geminiAudio(pcmResponse(new byte[] {0, 0}));
        }, () -> { }, ms -> { });
        assertEquals(3, attempts[0]);
        assertEquals(46, audio.length);
        JSONObject blocked = new JSONObject().put("promptFeedback", new JSONObject().put("blockReason", "SAFETY"));
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> NativeSpeechProtocol.geminiAudio(blocked));
        assertFalse(SpeechRetry.retryable(error));
        JSONObject incomplete = new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "OTHER")));
        assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> NativeSpeechProtocol.geminiAudio(incomplete));
    }

    @Test public void elevenLabsUsesARealVoiceIdAndNeverPrependsStyleToSpokenText() throws Exception {
        String voice = "EXAVITQu4vr4xnSDxMaL";
        assertEquals("ELEVENLABS_API_KEY", SpeechBackend.ELEVENLABS.key);
        SpeechBackend.ELEVENLABS.validate("eleven_multilingual_v2", voice);
        assertThrows(IllegalArgumentException.class, () -> SpeechBackend.ELEVENLABS.validate("eleven_v3", ""));
        assertThrows(IllegalArgumentException.class, () -> SpeechBackend.ELEVENLABS.validate("eleven_v3", "../bad?key=secret"));
        JSONObject body = NativeSpeechProtocol.request(SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", voice, "Breathe.", "Do not speak this style instruction.");
        assertEquals("Breathe.", body.getString("text"));
        assertEquals("eleven_multilingual_v2", body.getString("model_id"));
        assertFalse(body.has("instructions"));
        assertEquals("https://api.elevenlabs.io/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128",
                NativeSpeechProtocol.endpoint(SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", voice));
        assertNotEquals(NativeSpeechProtocol.cacheName(SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", voice, "Breathe.", "style"),
                NativeSpeechProtocol.cacheName(SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", "JBFqnCBsd6RMkjVDRZzb", "Breathe.", "style"));
        assertNotEquals(NativeSpeechProtocol.cacheName(SpeechBackend.ELEVENLABS, "eleven_v3", voice, "Breathe.", "style"),
                NativeSpeechProtocol.cacheName(SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", voice, "Breathe.", "style"));
    }

    @Test public void geminiAcceptsWavAndRetriesStopResponsesWithoutAudio() throws Exception {
        byte[] wav = NativeSpeechProtocol.geminiAudio(pcmResponse(new byte[] {0, 0}));
        JSONObject wrapped = new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "STOP")
                .put("content", new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("inlineData", new JSONObject()
                        .put("mimeType", "audio/wav").put("data", Base64.getEncoder().encodeToString(wav))))))));
        assertArrayEquals(wav, NativeSpeechProtocol.geminiAudio(wrapped));
        JSONObject noAudio = new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "STOP")));
        assertThrows(SpeechRetry.TransientGenerationFailure.class, () -> NativeSpeechProtocol.geminiAudio(noAudio));
        JSONObject safety = new JSONObject().put("candidates", new JSONArray().put(new JSONObject().put("finishReason", "SAFETY")));
        assertThrows(IllegalStateException.class, () -> NativeSpeechProtocol.geminiAudio(safety));
    }

}
