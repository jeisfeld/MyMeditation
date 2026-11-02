package de.jeisfeld.mymeditation.ui.configuration;

import androidx.preference.PreferenceManager;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.MeditationPauses;
import de.jeisfeld.mymeditation.audio.SavedMeditations;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.Map;
import java.util.TreeMap;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsBackupTest extends BaselineTest {
    private Map<String, String> capture() { return SettingsBackup.capture(Application.getAppContext()); }
    @Test public void restoresAllSettingsAndLibrariesExactlyWithoutTouchingMeditationsSessionsOrCredentials() throws Exception {
        PreferenceUtil.setSharedPreferenceString("system_prompt", "  Wärme 🌿\n静かな言葉  ");
        PreferenceUtil.setSharedPreferenceString("query_template", "  Über @TEXT@ und @TEXT@.\n");
        PreferenceUtil.setSharedPreferenceString("speech_backend", "elevenlabs");
        PreferenceUtil.setSharedPreferenceString("elevenlabs_speech_voice", "voice123456789");
        PreferenceUtil.setSharedPreferenceString("gemini_speech_model", "gemini-3.8-flash-tts");
        PreferenceUtil.setSharedPreferenceString("gemini_speech_voice", "Aoede");
        PreferenceUtil.setSharedPreferenceString("speech_style", "Warm and clear");
        PreferenceUtil.setSharedPreferenceString("speech_speed", "1.2");
        PreferenceUtil.setSharedPreferenceString(MeditationPauses.PREFERENCE, "many");
        PreferenceUtil.setPauseSeconds(2.5);
        VoiceProfiles voices = new VoiceProfiles("{}");
        voices.saveVoice("voice123456789", "Meine Stimme"); voices.setSpeed(SpeechBackend.ELEVENLABS, "voice123456789", 1.2);
        voices.setSpeed(SpeechBackend.GEMINI, "Aoede", 0.8);
        PreferenceUtil.setSharedPreferenceString(VoiceProfiles.PREFERENCE, voices.json());
        for (PromptTemplates.Kind kind : PromptTemplates.Kind.values()) {
            PromptTemplates templates = new PromptTemplates(kind, "{}");
            templates.save(null, "Vorlage", "  🌿 @TEXT@\n  ");
            PreferenceUtil.setSharedPreferenceString(kind.preference, templates.json());
        }
        int saved = SavedMeditations.save("Keep me", "s", "@TEXT@", "c", "Recorded text.");
        PreferenceUtil.setSharedPreferenceString("sentence_audio_" + saved, "keep-audio-link");
        PreferenceUtil.setSharedPreferenceString("playback_session", "keep-session");
        PreferenceUtil.setSharedPreferenceString("OPENAI_API_KEY", "never-export-this");
        Map<String, String> expected = capture();
        String json = SettingsBackup.encode(expected);
        assertFalse(json.contains("never-export-this")); assertFalse(json.contains("keep-audio-link"));
        assertFalse(json.contains("Recorded text.")); assertFalse(json.contains("keep-session"));
        for (String key : expected.keySet()) { PreferenceManager.getDefaultSharedPreferences(Application.getAppContext()).edit().remove(key).commit(); }
        SettingsBackup.restore(Application.getAppContext(), SettingsBackup.decode(json));
        assertEquals(expected, capture());
        assertEquals("keep-audio-link", PreferenceUtil.getSharedPreferenceString("sentence_audio_" + saved, ""));
        assertEquals("keep-session", PreferenceUtil.getSharedPreferenceString("playback_session", ""));
        assertEquals("never-export-this", PreferenceUtil.getSharedPreferenceString("OPENAI_API_KEY", ""));
        assertEquals("Recorded text.", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, saved));
    }
    @Test public void absentCollectionsKeepLocalEntriesWhileAbsentScalarSettingsReturnToDefaults() throws Exception {
        Map<String, String> clean = capture();
        clean.remove(VoiceProfiles.PREFERENCE); clean.remove(PromptTemplates.Kind.SYSTEM.preference); clean.remove("speech_style");
        VoiceProfiles voices = new VoiceProfiles("{}"); voices.saveVoice("voice123456789", "Keep this voice");
        PromptTemplates templates = new PromptTemplates(PromptTemplates.Kind.SYSTEM, "{}"); templates.save(null, "Keep", "Existing text");
        PreferenceUtil.setSharedPreferenceString(VoiceProfiles.PREFERENCE, voices.json());
        PreferenceUtil.setSharedPreferenceString(PromptTemplates.Kind.SYSTEM.preference, templates.json());
        PreferenceUtil.setSharedPreferenceString("speech_style", "Different style");
        SettingsBackup.restore(Application.getAppContext(), clean);
        assertEquals("Keep this voice", new VoiceProfiles(capture().get(VoiceProfiles.PREFERENCE)).voices().get(0).name);
        assertEquals("Existing text", new PromptTemplates(PromptTemplates.Kind.SYSTEM, capture().get(PromptTemplates.Kind.SYSTEM.preference)).list().get(0).text);
        assertEquals(Application.getAppContext().getString(R.string.default_speech_style), capture().get("speech_style"));
    }
    @Test public void invalidFilesAndDisallowedKeysCannotChangeAnyPreferences() throws Exception {
        Map<String, String> before = capture();
        for (String key : new String[] {"saved_meditation_ids", "OPENAI_API_KEY", "playback_session", "query_template", "speech_speed", "speech_backend", "pause_seconds", VoiceProfiles.PREFERENCE, PromptTemplates.Kind.QUERY.preference}) {
            Map<String, String> invalid = new TreeMap<>(before);
            invalid.put(key, "invalid");
            assertThrows(key, Exception.class, () -> SettingsBackup.restore(Application.getAppContext(), invalid));
            assertEquals(before, capture());
        }
        JSONObject root = new JSONObject(SettingsBackup.encode(before));
        root.put("version", 9); assertThrows(Exception.class, () -> SettingsBackup.decode(root.toString()));
        root.put("version", 1).getJSONObject("settings").put("speech_speed", 1.2);
        assertThrows(Exception.class, () -> SettingsBackup.decode(root.toString()));
        assertThrows(Exception.class, () -> SettingsBackup.decode("{broken"));
        assertThrows(Exception.class, () -> SettingsBackup.decode(new String(new char[SettingsBackup.MAX_BYTES + 1])));
        assertEquals(before, capture());
    }
    @Test public void nestedInvalidVoiceSpeedsAndTemplatesAreRejectedInsteadOfPartiallyRestoring() throws Exception {
        for (String invalid : new String[] {"{\"speeds\":{\"elevenlabs:voice123456789\":9}}", "{\"voices\":[{\"id\":\"bad\",\"name\":\"Voice\"}]}"}) {
            Map<String, String> settings = capture(); settings.put(VoiceProfiles.PREFERENCE, invalid);
            assertThrows(Exception.class, () -> SettingsBackup.encode(settings));
        }
        Map<String, String> settings = capture();
        settings.put(PromptTemplates.Kind.QUERY.preference, "{\"version\":1,\"templates\":[{\"id\":\"1\",\"name\":\"Bad\",\"text\":\"No placeholder\"}]}");
        assertThrows(Exception.class, () -> SettingsBackup.encode(settings));
    }
}
