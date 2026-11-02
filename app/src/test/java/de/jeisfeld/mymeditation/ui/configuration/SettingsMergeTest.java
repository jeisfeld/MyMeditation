package de.jeisfeld.mymeditation.ui.configuration;

import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.Arrays;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsMergeTest extends BaselineTest {
    private Map<String, String> capture() { return SettingsBackup.capture(Application.getAppContext()); }
    @Test public void templatesMergeByContentAndPreserveBothValuesForNameAndIdConflicts() throws Exception {
        Map<String, String> imported = capture();
        for (PromptTemplates.Kind kind : PromptTemplates.Kind.values()) {
            PromptTemplates local = new PromptTemplates(kind, "{}");
            String oldId = local.save(null, "Calm", "Local @TEXT@");
            PreferenceUtil.setSharedPreferenceString(kind.preference, local.json());
            JSONArray entries = new JSONArray()
                    .put(new JSONObject().put("id", oldId).put("name", "Calm").put("text", "New @TEXT@"))
                    .put(new JSONObject().put("id", "same-content").put("name", "Other name").put("text", "Local @TEXT@"));
            imported.put(kind.preference, new JSONObject().put("version", 1).put("templates", entries).toString());
        }
        SettingsBackup.restore(Application.getAppContext(), imported);
        for (PromptTemplates.Kind kind : PromptTemplates.Kind.values()) {
            PromptTemplates merged = new PromptTemplates(kind, capture().get(kind.preference));
            assertEquals(2, merged.list().size());
            assertEquals("Calm", merged.list().get(0).name); assertEquals("Local @TEXT@", merged.list().get(0).text);
            assertEquals("Calm (2)", merged.list().get(1).name); assertEquals("New @TEXT@", merged.list().get(1).text);
        }
        Map<String, String> once = capture();
        SettingsBackup.restore(Application.getAppContext(), imported);
        assertEquals(once, capture()); // Repeated imports must not add IDs or numbered copies.
    }
    @Test public void existingVoiceIdsNamesAndSpeedsWinWhileNewIdsAndMissingSpeedsAreAdded() throws Exception {
        VoiceProfiles local = new VoiceProfiles("{}"); local.saveVoice("localVoice12345", "Calm");
        local.setSpeed(SpeechBackend.ELEVENLABS, "localVoice12345", 1.1);
        PreferenceUtil.setSharedPreferenceString(VoiceProfiles.PREFERENCE, local.json());
        VoiceProfiles other = new VoiceProfiles("{}");
        other.saveVoice("localVoice12345", "Renamed elsewhere"); other.setSpeed(SpeechBackend.ELEVENLABS, "localVoice12345", 2);
        other.saveVoice("newVoice123456", "Calm"); other.setSpeed(SpeechBackend.ELEVENLABS, "newVoice123456", 0.8);
        Map<String, String> imported = capture(); imported.put(VoiceProfiles.PREFERENCE, other.json());
        SettingsBackup.restore(Application.getAppContext(), imported);
        VoiceProfiles result = new VoiceProfiles(capture().get(VoiceProfiles.PREFERENCE));
        assertEquals(2, result.voices().size());
        assertEquals("Calm", result.name("localVoice12345")); assertEquals("Calm (2)", result.name("newVoice123456"));
        assertEquals(1.1, result.speed(SpeechBackend.ELEVENLABS, "localVoice12345", 1), 0);
        assertEquals(0.8, result.speed(SpeechBackend.ELEVENLABS, "newVoice123456", 1), 0);
        Map<String, String> once = capture(); SettingsBackup.restore(Application.getAppContext(), imported); assertEquals(once, capture());
    }
    @Test public void bothOldAndImportedCustomModelsRemainSelectableAndExportableWithoutDuplicates() throws Exception {
        PreferenceUtil.setSharedPreferenceString("claude_model", "gpt-local-custom");
        PreferenceUtil.setSharedPreferenceString("gemini_speech_model", "gemini-local-custom-tts");
        Map<String, String> imported = capture();
        imported.put("claude_model", "gpt-imported-custom");
        imported.put(SettingsCollections.AI_MODELS, "[\"gpt-imported-custom\",\"gpt-imported-custom\"]");
        imported.put("gemini_speech_model", "gemini-imported-custom-tts");
        SettingsBackup.restore(Application.getAppContext(), imported);
        assertEquals("gpt-imported-custom", capture().get("claude_model"));
        assertTrue(SettingsCollections.aiModels().containsAll(Arrays.asList("gpt-local-custom", "gpt-imported-custom")));
        assertEquals(1, java.util.Collections.frequency(SettingsCollections.aiModels(), "gpt-imported-custom"));
        assertTrue(SettingsCollections.speechModels(SpeechBackend.GEMINI, "gemini-imported-custom-tts")
                .containsAll(Arrays.asList("gemini-local-custom-tts", "gemini-imported-custom-tts")));
        Map<String, String> once = SettingsBackup.decode(SettingsBackup.encode(capture()));
        SettingsBackup.restore(Application.getAppContext(), imported); assertEquals(once, capture());
    }
    @Test public void versionOneFilesStillMergeAndBadNewModelListsLeaveCollectionsUntouched() throws Exception {
        Map<String, String> incoming = capture();
        JSONObject oldFile = new JSONObject(SettingsBackup.encode(incoming)); oldFile.put("version", 1);
        oldFile.getJSONObject("settings").remove(SettingsCollections.AI_MODELS);
        for (SpeechBackend backend : SpeechBackend.values()) { oldFile.getJSONObject("settings").remove(SettingsCollections.speechModelsKey(backend)); }
        Map<String, String> decoded = SettingsBackup.decode(oldFile.toString());
        assertFalse(decoded.containsKey(SettingsCollections.AI_MODELS));
        SettingsBackup.restore(Application.getAppContext(), decoded);
        Map<String, String> before = capture();
        incoming.put(SettingsCollections.AI_MODELS, "[\"unknown-model\"]");
        assertThrows(Exception.class, () -> SettingsBackup.restore(Application.getAppContext(), incoming));
        assertEquals(before, capture());
    }
}
