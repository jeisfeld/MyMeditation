package de.jeisfeld.mymeditation.http;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelCatalogTest {
    @Test public void everyAiPresetRoutesToAnExistingProviderAndManualIdsRemainAllowed() {
        EnumSet<AiProvider> covered = EnumSet.noneOf(AiProvider.class);
        for (String model : ModelCatalog.aiModels()) { covered.add(AiProvider.forModel(model)); }
        assertEquals(EnumSet.allOf(AiProvider.class), covered);
        assertEquals(ModelCatalog.aiModels().size(), new HashSet<>(ModelCatalog.aiModels()).size());
        assertEquals(AiProvider.CLAUDE, AiProvider.forModel("claude-custom-future-model"));
    }
    @Test public void speechPresetsAndOfferedVoicesPassPlaybackValidation() {
        for (SpeechBackend backend : SpeechBackend.values()) {
            if (backend == SpeechBackend.DEVICE) { assertTrue(ModelCatalog.speechModels(backend).isEmpty()); continue; }
            assertTrue(ModelCatalog.speechModels(backend).contains(backend.defaultModel));
            for (String model : ModelCatalog.speechModels(backend)) {
                List<String> voices = backend == SpeechBackend.ELEVENLABS
                        ? java.util.Collections.singletonList("TestSavedVoice123") : backend.voices(model);
                assertFalse(voices.isEmpty());
                for (String voice : voices) { backend.validate(model, voice); }
            }
        }
        assertFalse(SpeechBackend.OPENAI.voices("tts-1").contains("coral"));
        assertTrue(SpeechBackend.OPENAI.voices("gpt-4o-mini-tts").contains("coral"));
    }
    @Test public void deprecatedGoogleSelectionsFallBackWithoutInvalidatingOldAudioRequests() {
        for (String old : java.util.Arrays.asList("gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts",
                "gemini-3.1-flash-tts-preview")) {
            assertFalse(ModelCatalog.speechModels(SpeechBackend.GEMINI, old).contains(old));
            assertEquals(SpeechBackend.GEMINI.defaultModel, ModelCatalog.selectableSpeechModel(SpeechBackend.GEMINI, old));
            // Validation of a saved speech snapshot must still work for cached/offline playback.
            SpeechBackend.GEMINI.validate(old, "Kore");
        }
        String custom = "gemini-3.8-flash-tts-custom-date";
        assertEquals(custom, ModelCatalog.selectableSpeechModel(SpeechBackend.GEMINI, custom));
        assertTrue(ModelCatalog.speechModels(SpeechBackend.GEMINI, custom).contains(custom));
        assertThrows(IllegalArgumentException.class, () -> SpeechBackend.GEMINI.validate("gemini-3.8-live", "Kore"));
    }
    @Test public void migrationKeepsSavedSpeechIdsWithoutDuplicatesOrChangingPresets() {
        String saved = "gemini-2.5-flash-preview-tts-custom-date";
        List<String> choices = ModelCatalog.speechModels(SpeechBackend.GEMINI, saved);
        assertTrue(choices.contains(saved));
        assertFalse(ModelCatalog.speechModels(SpeechBackend.GEMINI).contains(saved));
        for (String model : ModelCatalog.speechModels(SpeechBackend.GEMINI)) {
            List<String> unchanged = ModelCatalog.speechModels(SpeechBackend.GEMINI, model);
            assertEquals(unchanged.size(), new HashSet<>(unchanged).size());
        }
    }
}
