package de.jeisfeld.mymeditation.http;

import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceProfilesTest {
    private static final String ID = "EXAVITQu4vr4xnSDxMaL";
    private static final String OTHER = "JBFqnCBsd6RMkjVDRZzb";
    @Test public void namesAndVoiceIdsSurviveSaveAndRenameWithoutLosingSpeed() {
        VoiceProfiles profiles = new VoiceProfiles("{}");
        profiles.saveVoice(ID, "Sarah");
        profiles.setSpeed(SpeechBackend.ELEVENLABS, ID, 1.2);
        profiles.saveVoice(ID, "My narrator");
        VoiceProfiles restored = new VoiceProfiles(profiles.json());
        assertEquals(1, restored.voices().size());
        assertEquals("My narrator", restored.voices().get(0).name);
        assertEquals(ID, restored.voices().get(0).id);
        assertEquals(1.2, restored.speed(SpeechBackend.ELEVENLABS, ID, 1.0), 0.001);
    }
    @Test public void speedsAreIndependentForEveryProviderAndVoice() {
        VoiceProfiles profiles = new VoiceProfiles("{}");
        profiles.setSpeed(SpeechBackend.ELEVENLABS, ID, 1.2);
        profiles.setSpeed(SpeechBackend.ELEVENLABS, OTHER, 0.85);
        profiles.setSpeed(SpeechBackend.GEMINI, ID, 1.4);
        VoiceProfiles restored = new VoiceProfiles(profiles.json());
        assertEquals(1.2, restored.speed(SpeechBackend.ELEVENLABS, ID, 1.0), 0.001);
        assertEquals(0.85, restored.speed(SpeechBackend.ELEVENLABS, OTHER, 1.0), 0.001);
        assertEquals(1.4, restored.speed(SpeechBackend.GEMINI, ID, 1.0), 0.001);
        assertEquals(1.0, restored.speed(SpeechBackend.OPENAI, "nova", 1.0), 0.001);
    }
    @Test public void validatesNamesIdsAndSpeeds() {
        VoiceProfiles profiles = new VoiceProfiles("{}");
        profiles.saveVoice(ID, "Sarah");
        assertThrows(IllegalArgumentException.class, () -> profiles.saveVoice(OTHER, "sarah"));
        assertThrows(IllegalArgumentException.class, () -> profiles.saveVoice("not/id", "New"));
        assertThrows(IllegalArgumentException.class, () -> profiles.saveVoice(OTHER, "  "));
        assertThrows(IllegalArgumentException.class, () -> profiles.setSpeed(SpeechBackend.GEMINI, "Kore", 0));
    }
    @Test public void migratesExistingVoiceOnceAndHandlesInvalidStoredData() {
        VoiceProfiles profiles = new VoiceProfiles("{}");
        profiles.migrateVoice(ID);
        profiles.migrateVoice(ID);
        profiles.migrateVoice(OTHER);
        assertEquals(2, profiles.voices().size());
        assertNotEquals(profiles.name(ID), profiles.name(OTHER));
        assertTrue(new VoiceProfiles("broken-json").voices().isEmpty());
        assertFalse(profiles.hasSpeed(SpeechBackend.GEMINI, "Kore"));
    }
    @Test public void voiceNamesAreAlphabeticalAndRenamingReordersThem() {
        VoiceProfiles profiles = new VoiceProfiles("{}");
        profiles.saveVoice(ID, "Zoe");
        profiles.saveVoice(OTHER, "alice");
        assertEquals(OTHER, profiles.voices().get(0).id);
        profiles.saveVoice(ID, "Abby");
        assertEquals(ID, profiles.voices().get(0).id);
        assertEquals("Abby", new VoiceProfiles(profiles.json()).voices().get(0).name);
    }
    @Test public void removalPersistsAndKeepsOtherVoicesAndProviders() {
        VoiceProfiles profiles = new VoiceProfiles("{}");
        profiles.saveVoice(ID, "Sarah");
        profiles.saveVoice(OTHER, "George");
        profiles.setSpeed(SpeechBackend.ELEVENLABS, ID, 1.3);
        profiles.setSpeed(SpeechBackend.GEMINI, ID, 1.2);
        profiles.removeVoice(ID);
        VoiceProfiles restored = new VoiceProfiles(profiles.json());
        assertNull(restored.name(ID));
        assertFalse(restored.hasSpeed(SpeechBackend.ELEVENLABS, ID));
        assertEquals(1.2, restored.speed(SpeechBackend.GEMINI, ID, 1.0), 0.001);
        assertEquals(OTHER, restored.voices().get(0).id);
        restored.removeVoice(OTHER);
        assertTrue(new VoiceProfiles(restored.json()).voices().isEmpty());
    }

}
