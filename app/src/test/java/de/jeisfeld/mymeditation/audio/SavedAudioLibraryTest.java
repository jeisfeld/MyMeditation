package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.FileOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class SavedAudioLibraryTest extends BaselineTest {
    private PlaybackRequest request() {
        return new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1.25, "Calm", 5);
    }
    private SentenceAudioStore.Book audio(boolean complete) throws Exception {
        PlaybackRequest request = request();
        SavedAudioLibrary.begin(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        for (int i = 0; i < book.sentences.size(); i++) {
            book.obtain(i, file -> {
                try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) {
                    writer.silence(0.1, () -> { });
                }
            }, () -> { });
        }
        if (complete) { book.finish(); }
        return book;
    }
    @Test public void automaticRetentionAttachesToExistingTextSnapshotAndDoesNotCreateDuplicates() throws Exception {
        audio(true);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        int existing = OfflineMeditations.save("My named meditation", snapshot.system, snapshot.query, snapshot.content, request().text);
        int linked = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot, false);
        assertEquals(existing, linked);
        assertEquals(existing, SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot, false));
        assertEquals(1, PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).size());
        assertEquals("My named meditation", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, existing));
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), existing));
        assertFalse(OfflineMeditations.file(Application.getAppContext(), existing).exists());
    }
    @Test public void loadingAudioRestoresVoiceModelStyleAndPerVoiceSpeedButKeepsTheCurrentPause() throws Exception {
        audio(true);
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(),
                SavedAudioLibrary.Snapshot.current(Application.getAppContext()), true);
        PreferenceUtil.setPauseSeconds(0.5);
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, "openai");
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_style, "Different");
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "Different text.");
        PreferenceUtil.setSharedPreferenceString("offline_settings_" + id, "bad legacy metadata");
        SavedAudioLibrary.restore(Application.getAppContext(), id);
        PlaybackRequest restored = MeditationPlaybackService.currentRequest(
                PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text), PreferenceUtil.getPauseSeconds());
        assertEquals(SentenceAudioStore.key(request()), SentenceAudioStore.key(restored));
        assertEquals(0.5, restored.pauseSeconds, 0);
        assertEquals(1.25, new VoiceProfiles(PreferenceUtil.getSharedPreferenceString(VoiceProfiles.PREFERENCE, "{}"))
                .speed(SpeechBackend.GEMINI, "Kore", 3), 0);
    }
    @Test public void downloadingAllSentencesWithoutFinishingDoesNotPublishALibraryEntry() throws Exception {
        audio(false);
        assertEquals(0, SavedAudioLibrary.keepComplete(Application.getAppContext(), request(),
                SavedAudioLibrary.Snapshot.current(Application.getAppContext()), false));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
    @Test public void aPlainTextSaveCanAttachAvailableAudioOnlyForMatchingSettings() throws Exception {
        audio(true);
        int id = OfflineMeditations.save("Saved", "s", "@TEXT@", "c", request().text);
        SavedAudioLibrary.attachAvailable(Application.getAppContext(), id);
        assertFalse(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        SavedAudioLibrary.restoreVoice(request());
        SavedAudioLibrary.attachAvailable(Application.getAppContext(), id);
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
    }
    @Test public void deletingAnEntryPreventsALatePlaybackCallbackFromResurrectingIt() throws Exception {
        audio(true);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot, true);
        SavedAudioLibrary.unlink(id);
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, java.util.Collections.emptyList());
        assertEquals(0, SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot, false));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
    @Test public void legacyWholeMp3sRemainAvailableAndTheirStoredVoiceSettingsCanBeRestored() throws Exception {
        int id = OfflineMeditations.save("Legacy", "s", "@TEXT@", "c", request().text);
        assertTrue(OfflineMeditations.directory(Application.getAppContext()).mkdirs());
        try (FileOutputStream output = new FileOutputStream(OfflineMeditations.file(Application.getAppContext(), id))) { output.write(1); }
        PreferenceUtil.setSharedPreferenceString("offline_settings_" + id, request().json());
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertNull(SavedAudioLibrary.book(Application.getAppContext(), id));
        SavedAudioLibrary.restore(Application.getAppContext(), id);
        assertEquals("gemini", PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend));
        assertEquals("Kore", PreferenceUtil.getSharedPreferenceString(SpeechBackend.GEMINI.voiceKey(), ""));
    }
    @Test public void deletingOneSharedEntryKeepsAudioUntilTheLastReferenceIsRemoved() throws Exception {
        SentenceAudioStore.Book book = audio(true);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        int first = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot, true);
        int second = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot, true);
        SavedAudioLibrary.unlink(first);
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, java.util.Collections.singletonList(second));
        SavedAudioLibrary.cleanupDeleted(Application.getAppContext());
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), second));
        SavedAudioLibrary.unlink(second);
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, java.util.Collections.emptyList());
        SavedAudioLibrary.cleanupDeleted(Application.getAppContext());
        long deadline = System.nanoTime() + 5000000000L;
        while (book.audio(0).exists() && System.nanoTime() < deadline) { Thread.sleep(1); }
        assertFalse(book.audio(0).exists());
    }
}
