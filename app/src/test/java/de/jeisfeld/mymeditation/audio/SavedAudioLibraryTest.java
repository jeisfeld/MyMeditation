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
        int existing = SavedMeditations.save("My named meditation", snapshot.system, snapshot.query, snapshot.content, request().text);
        int linked = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot);
        assertEquals(existing, linked);
        assertEquals(existing, SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot));
        assertEquals(1, PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).size());
        assertEquals("My named meditation", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, existing));
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), existing));
    }
    @Test public void loadingAudioRestoresVoiceModelStyleAndPerVoiceSpeedButKeepsTheCurrentPause() throws Exception {
        audio(true);
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(),
                SavedAudioLibrary.Snapshot.current(Application.getAppContext()));
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
                SavedAudioLibrary.Snapshot.current(Application.getAppContext())));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
    @Test public void aPlainTextSaveCanAttachAvailableAudioOnlyForMatchingSettings() throws Exception {
        audio(true);
        int id = SavedMeditations.save("Saved", "s", "@TEXT@", "c", request().text);
        SavedAudioLibrary.attachAvailable(Application.getAppContext(), id);
        assertFalse(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        SavedAudioLibrary.restoreVoice(request());
        SavedAudioLibrary.attachAvailable(Application.getAppContext(), id);
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
    }
    @Test public void deletingAnEntryPreventsALatePlaybackCallbackFromResurrectingIt() throws Exception {
        audio(true);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot);
        SavedAudioLibrary.unlink(id);
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, java.util.Collections.emptyList());
        assertEquals(0, SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
    @Test public void deletingOneSharedEntryKeepsAudioUntilTheLastReferenceIsRemoved() throws Exception {
        SentenceAudioStore.Book book = audio(true);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        int first = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot);
        int second = SavedMeditations.save("Second name", snapshot.system, snapshot.query, snapshot.content, request().text);
        SavedAudioLibrary.restoreVoice(request());
        SavedAudioLibrary.attachAvailable(Application.getAppContext(), second);
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
    @Test public void restoredTextEntryReceivesAudioEvenIfAnotherIdenticalEntryExists() throws Exception {
        audio(true);
        int other = SavedMeditations.save("Other", "s", "q", "c", request().text);
        int source = SavedMeditations.save("Chosen name", "s", "q", "c", request().text);
        SavedAudioLibrary.restore(Application.getAppContext(), source);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        assertEquals(source, snapshot.sourceId);
        assertEquals(source, SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), snapshot));
        assertFalse(SavedAudioLibrary.hasAudio(Application.getAppContext(), other));
        assertEquals(java.util.Arrays.asList(other, source), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
    }
    @Test public void changedVoiceCreatesSameNamedVariantDirectlyBelowRestoredSourceAndOnlyOnce() throws Exception {
        audio(true);
        int before = SavedMeditations.save("Before", "s", "q", "c", "Other.");
        int source = SavedMeditations.save("Chosen name", "s", "q", "c", request().text);
        int after = SavedMeditations.save("After", "s", "q", "c", "Other.");
        SavedAudioLibrary.restoreVoice(request()); SavedAudioLibrary.attachAvailable(Application.getAppContext(), source);
        SavedAudioLibrary.restore(Application.getAppContext(), source);
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        PlaybackRequest changed = new PlaybackRequest(request().text, request().backend, request().model, "Sulafat", 1, "Calm", 0);
        SavedAudioLibrary.begin(changed);
        SentenceAudioStore.Book variant = SentenceAudio.store(Application.getAppContext()).open(changed);
        for (int i = 0; i < variant.sentences.size(); i++) {
            variant.obtain(i, file -> {
                try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
        }
        variant.finish();
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, source, "Renamed while playing");
        int saved = SavedAudioLibrary.keepComplete(Application.getAppContext(), changed, snapshot);
        assertNotEquals(source, saved);
        assertEquals("Renamed while playing", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, saved));
        assertEquals(java.util.Arrays.asList(before, source, saved, after), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        assertEquals(saved, SavedAudioLibrary.keepComplete(Application.getAppContext(), changed, snapshot));
        assertEquals(SentenceAudioStore.key(request()), SavedAudioLibrary.book(Application.getAppContext(), source).identity);
        assertEquals(variant.identity, SavedAudioLibrary.book(Application.getAppContext(), saved).identity);
    }
    @Test public void editingRestoredTextDetachesItsSourceAndDoesNotOverwriteTheSavedEntry() {
        int source = SavedMeditations.save("Original", "s", "q", "c", request().text);
        SavedAudioLibrary.restore(Application.getAppContext(), source);
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "Changed text.");
        assertEquals(0, SavedAudioLibrary.Snapshot.current(Application.getAppContext()).sourceId);
        assertEquals(request().text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, source));
    }
    @Test public void legacyCleanupDeletesOldRecordingsButPreservesTextAndSentenceAudio() throws Exception {
        SentenceAudioStore.Book book = audio(true);
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request(), SavedAudioLibrary.Snapshot.current(Application.getAppContext()));
        java.io.File old = new java.io.File(Application.getAppContext().getFilesDir(), "offline-meditations");
        assertTrue(old.mkdirs() || old.isDirectory());
        try (FileOutputStream output = new FileOutputStream(new java.io.File(old, id + ".mp3"))) { output.write(1); }
        try (FileOutputStream output = new FileOutputStream(new java.io.File(old, "unfinished.part"))) { output.write(1); }
        PreferenceUtil.setSharedPreferenceString("offline_settings_" + id, request().json());
        LegacyAudioCleanup.run(Application.getAppContext());
        LegacyAudioCleanup.run(Application.getAppContext());
        assertFalse(old.exists());
        assertTrue(book.isComplete());
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertEquals(request().text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
        assertEquals("", PreferenceUtil.getSharedPreferenceString("offline_settings_" + id, ""));
    }

}
