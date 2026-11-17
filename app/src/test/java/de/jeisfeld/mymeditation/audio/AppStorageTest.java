package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import android.content.Intent;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import static org.junit.Assert.*;

public class AppStorageTest extends BaselineTest {
    private Context context() { return Application.getAppContext(); }
    private PlaybackRequest request() { return new PlaybackRequest("Breathe.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1.2, "Calm", 0); }
    private SentenceAudioStore.Book recording(PlaybackRequest request) throws Exception {
        SavedAudioLibrary.begin(request);
        SentenceAudioStore.Book book = SentenceAudio.store(context()).open(request);
        book.obtain(0, file -> { try (FileOutputStream out = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); } }, () -> { });
        return book;
    }
    private File file(File parent, String path, int size) throws Exception {
        File file = new File(parent, path); assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), new byte[size]); return file;
    }
    private long bytes(File directory) throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(directory.toPath())) { return paths.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum(); }
    }
    private void awaitGone(File file) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (file.exists() && System.nanoTime() < deadline) { Thread.sleep(2); }
        assertFalse(file.exists());
    }
    @After public void release() { AppStorage.endCleaning(); }

    @Test public void storageCountsSharedSavedAudioOnceAndClearsOnlyOtherAudio() throws Exception {
        SentenceAudioStore.Book saved = recording(request());
        int id = SavedAudioLibrary.keepComplete(context(), request(), SavedAudioLibrary.Snapshot.current(context()));
        int duplicate = SavedMeditations.save("Second", "s", "@TEXT@", "c", request().text);
        SavedAudioLibrary.restoreVoice(request()); SavedAudioLibrary.attachAvailable(context(), duplicate);
        PlaybackRequest other = new PlaybackRequest("Different. Incomplete.", request().backend, request().model, request().voice, 1, "", 0);
        SentenceAudioStore.Book partial = recording(other);
        File raw = file(context().getCacheDir(), "openai-speech/raw.mp3", 31);
        file(context().getCacheDir(), "native-speech/raw.wav", 41);
        file(context().getNoBackupFilesDir(), "native-speech/paid.wav", 51);
        File filesAudio = file(context().getFilesDir(), "native-speech/paid.mp3", 37);
        file(context().getCacheDir(), "shared-meditations/shared.mp3", 61);
        File notes = file(context().getFilesDir(), "notes.bin", 731);
        File settings = file(context().getCacheDir(), "settings-export-test.json", 17);
        AppStorage.Usage usage = AppStorage.measure(context());
        assertEquals(bytes(saved.directory()) + saved.audio(0).length(), usage.offlineAudio);
        assertEquals(31 + 41 + 51 + 37 + 61 + bytes(partial.directory()) + partial.audio(0).length(), usage.otherAudio);
        assertTrue(usage.otherData >= 731 + 17);
        assertTrue(AppStorage.beginCleaning()); AppStorage.clearOtherAudio(context()); AppStorage.endCleaning();
        assertFalse(filesAudio.exists());
        assertFalse(raw.exists()); assertFalse(partial.audio(0).exists()); assertTrue(notes.exists()); assertTrue(settings.exists());
        assertTrue(saved.isComplete()); assertTrue(SavedAudioLibrary.hasAudio(context(), id)); assertTrue(SavedAudioLibrary.hasAudio(context(), duplicate));
        assertEquals(0, AppStorage.measure(context()).otherAudio);
        assertEquals(Arrays.asList(id, duplicate), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
    }
    @Test public void audioOnlyRemovalPreservesTextsSettingsAndOtherSharedEntries() throws Exception {
        SentenceAudioStore.Book book = recording(request());
        int first = SavedAudioLibrary.keepComplete(context(), request(), SavedAudioLibrary.Snapshot.current(context()));
        int second = SavedMeditations.save("Second", "s", "@TEXT@", "c", request().text);
        SavedAudioLibrary.restoreVoice(request()); SavedAudioLibrary.attachAvailable(context(), second);
        String name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, first);
        SavedAudioLibrary.restore(context(), first);
        SavedAudioLibrary.Snapshot late = SavedAudioLibrary.Snapshot.current(context());
        assertEquals(first, late.sourceId);
        assertTrue(SavedAudioLibrary.removeAudio(context(), first));
        assertEquals(first, SavedAudioLibrary.Snapshot.current(context()).sourceId);
        assertFalse(SavedAudioLibrary.hasAudio(context(), first)); assertTrue(SavedAudioLibrary.hasAudio(context(), second));
        assertTrue(book.audio(0).exists());
        assertEquals(name, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, first));
        assertEquals(request().text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, first));
        assertEquals(0, SavedAudioLibrary.keepComplete(context(), request(), late));
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, "device");
        SavedAudioLibrary.restore(context(), first);
        assertEquals("gemini", PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend));
        assertEquals("1.2", PreferenceUtil.getSharedPreferenceString(R.string.key_speech_speed));
        assertTrue(SavedAudioLibrary.removeAudio(context(), second)); awaitGone(book.audio(0));
        assertEquals(Arrays.asList(first, second), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
    }
    @Test public void brokenRecordingsCanBeDetachedWithoutDeletingText() throws Exception {
        SentenceAudioStore.Book book = recording(request());
        int id = SavedAudioLibrary.keepComplete(context(), request(), SavedAudioLibrary.Snapshot.current(context()));
        Files.write(new File(book.directory(), "manifest.json").toPath(), "bad".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertFalse(SavedAudioLibrary.hasAudio(context(), id)); assertTrue(SavedAudioLibrary.hasRecording(id));
        assertTrue(SavedAudioLibrary.removeAudio(context(), id)); awaitGone(book.audio(0));
        assertEquals(request().text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
    }
    @Test public void deletingOneMeditationAndClearingOtherAudioKeepSentencesSharedWithAnotherScript() throws Exception {
        PlaybackRequest first = new PlaybackRequest("Breathe.Relax.", request().backend, request().model, request().voice, 1, "Calm", 0);
        SentenceAudioStore.Book a = recording(first);
        a.obtain(1, file -> { try (FileOutputStream out = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); } }, () -> { });
        int firstId = SavedAudioLibrary.keepComplete(context(), first, SavedAudioLibrary.Snapshot.current(context()));
        PlaybackRequest second = new PlaybackRequest("Breathe.Notice.", first.backend, first.model, first.voice, 1.8, "Calm", 0);
        SentenceAudioStore.Book b = recording(second);
        b.obtain(1, file -> { try (FileOutputStream out = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); } }, () -> { });
        int secondId = SavedAudioLibrary.keepComplete(context(), second, SavedAudioLibrary.Snapshot.current(context()));
        assertEquals(a.audio(0), b.audio(0));
        assertTrue(SavedAudioLibrary.removeAudio(context(), firstId)); awaitGone(a.audio(1));
        assertTrue(b.isComplete()); assertTrue(b.audio(0).isFile());
        PlaybackRequest draft = new PlaybackRequest("Breathe.Unsaved.", first.backend, first.model, first.voice, 1, "Calm", 0);
        SentenceAudioStore.Book partial = SentenceAudio.store(context()).open(draft);
        partial.obtain(1, file -> { try (FileOutputStream out = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); } }, () -> { });
        assertTrue(AppStorage.beginCleaning()); AppStorage.clearOtherAudio(context()); AppStorage.endCleaning();
        assertFalse(partial.audio(1).exists()); assertTrue(b.isComplete());
        assertTrue(SavedAudioLibrary.hasAudio(context(), secondId)); assertFalse(SavedAudioLibrary.hasAudio(context(), firstId));
    }
    @Test public void cleanupRequiresReservationAndNeverFollowsAudioSymlinks() throws Exception {
        File outside = Files.createTempDirectory("storage-outside-").toFile();
        File protectedFile = file(outside, "keep.bin", 47);
        File audio = new File(context().getCacheDir(), "native-speech"); assertTrue(audio.mkdirs() || audio.isDirectory());
        Files.createSymbolicLink(new File(audio, "outside").toPath(), outside.toPath());
        assertThrows(IllegalStateException.class, () -> AppStorage.clearOtherAudio(context()));
        assertTrue(protectedFile.exists()); assertEquals(0, AppStorage.measure(context()).otherAudio);
        assertTrue(AppStorage.beginCleaning()); AppStorage.clearOtherAudio(context()); AppStorage.endCleaning();
        assertTrue(protectedFile.exists()); assertFalse(Files.exists(new File(audio, "outside").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        Files.delete(protectedFile.toPath()); Files.delete(outside.toPath());
    }
    @Test public void activePlaybackBlocksCleanupAndAudioOnlyRemoval() throws Exception {
        recording(request());
        int id = SavedAudioLibrary.keepComplete(context(), request(), SavedAudioLibrary.Snapshot.current(context()));
        assertTrue(id > 0);
        SavedAudioLibrary.restoreVoice(request());
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, request().text);
        ServiceController<MeditationPlaybackService> service = Robolectric.buildService(MeditationPlaybackService.class).create();
        try {
            service.get().onStartCommand(new Intent().setAction("meditation.START"), 0, 1);
            assertTrue(AppStorage.audioActive()); assertFalse(AppStorage.beginCleaning());
            assertFalse(SavedAudioLibrary.removeAudio(context(), id)); assertTrue(SavedAudioLibrary.hasAudio(context(), id));
        } finally {
            service.get().onStartCommand(new Intent().setAction("meditation.STOP"), 0, 2); service.destroy(); idle();
        }
    }
    @Test public void partialSavedSourcesAndTheirMatchingCachesCountAsOfflineAndSurviveOtherCleanup() throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe.Missing sentence.", request().backend, request().model, request().voice, 1.2, "Calm", 0);
        SentenceAudioStore.Book partial = recording(request);
        int id = SavedMeditations.save("Partial", "s", "@TEXT@", "topic", request.text);
        SavedAudioLibrary.rememberPreparation(id, request);
        long cacheBytes = 0;
        for (File cache : de.jeisfeld.mymeditation.http.CloudSpeech.cacheFiles(context(), request.backend, request.model, request.voice, "Breathe.", request.speed, request.style)) {
            file(cache.getParentFile(), cache.getName(), 29); cacheBytes += 29;
        }
        File unrelated = file(context().getCacheDir(), "native-speech/unrelated.audio", 43);
        File backup = file(context().getFilesDir(), "legacy-sentence-audio/old/0.mp3", 53);
        assertFalse(SavedAudioLibrary.hasAudio(context(), id)); assertFalse(SavedAudioLibrary.hasRecording(id));
        AppStorage.Usage usage = AppStorage.measure(context());
        assertEquals(bytes(partial.directory()) + partial.audio(0).length() + cacheBytes, usage.offlineAudio);
        assertEquals(43 + 53, usage.otherAudio);
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, "openai");
        assertEquals(usage.offlineAudio, AppStorage.measure(context()).offlineAudio);
        assertTrue(AppStorage.beginCleaning()); AppStorage.clearOtherAudio(context()); AppStorage.endCleaning();
        assertTrue(partial.audio(0).exists()); assertFalse(unrelated.exists()); assertFalse(backup.exists());
        for (File cache : de.jeisfeld.mymeditation.http.CloudSpeech.cacheFiles(context(), request.backend, request.model, request.voice, "Breathe.", request.speed, request.style)) { assertTrue(cache.exists()); }
        assertEquals(usage.offlineAudio, AppStorage.measure(context()).offlineAudio);
        assertEquals(0, AppStorage.measure(context()).otherAudio);
    }
    @Test public void audioOnlyReleasesPartialOwnershipButKeepsSpeechSettingsForRetry() throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe.Missing.", request().backend, request().model, request().voice, 1.2, "Calm", 0);
        SentenceAudioStore.Book partial = recording(request);
        int id = SavedMeditations.save("Partial", "s", "@TEXT@", "topic", request.text);
        SavedAudioLibrary.rememberPreparation(id, request);
        assertTrue(SavedAudioLibrary.hasAudioMaterial(context(), id)); assertTrue(AppStorage.measure(context()).offlineAudio > 0);
        assertTrue(SavedAudioLibrary.removeAudio(context(), id)); awaitGone(partial.audio(0));
        assertFalse(SavedAudioLibrary.hasAudioMaterial(context(), id)); assertTrue(SavedAudioLibrary.audioDetached(id));
        assertEquals("Kore", SavedAudioLibrary.offlineRequest(context(), id).voice);
        assertEquals(0, AppStorage.measure(context()).offlineAudio);
        SavedAudioLibrary.rememberPreparation(id, request);
        recording(request);
        assertFalse(SavedAudioLibrary.audioDetached(id)); assertTrue(AppStorage.measure(context()).offlineAudio > 0);
    }
    @Test public void deletingCompleteBookCannotDeleteSourceOwnedByAnotherPartialSavedMeditation() throws Exception {
        SentenceAudioStore.Book full = recording(request());
        int complete = SavedAudioLibrary.keepComplete(context(), request(), SavedAudioLibrary.Snapshot.current(context()));
        PlaybackRequest request = new PlaybackRequest("Breathe.Not yet stored.", request().backend, request().model, request().voice, 1.8, "Calm", 0);
        int partial = SavedMeditations.save("Partial", "s", "@TEXT@", "topic", request.text);
        SavedAudioLibrary.rememberPreparation(partial, request);
        assertTrue(SavedAudioLibrary.removeAudio(context(), complete));
        java.util.concurrent.ExecutorService cleaner = org.robolectric.util.ReflectionHelpers.getStaticField(SavedAudioLibrary.class, "CLEANER");
        cleaner.submit(() -> { }).get(5, TimeUnit.SECONDS);
        assertTrue(full.audio(0).exists()); assertTrue(AppStorage.measure(context()).offlineAudio > 0);
        assertTrue(AppStorage.beginCleaning()); AppStorage.clearOtherAudio(context()); AppStorage.endCleaning();
        assertTrue(full.audio(0).exists());
    }
    @Test public void staleSpeechSnapshotDoesNotClaimAudioForDifferentSavedText() throws Exception {
        SentenceAudioStore.Book unlinked = recording(request());
        int id = SavedMeditations.save("Different", "s", "@TEXT@", "topic", "Different text.");
        SavedAudioLibrary.rememberPreparation(id, request());
        assertEquals(0, AppStorage.measure(context()).offlineAudio);
        assertTrue(AppStorage.beginCleaning()); AppStorage.clearOtherAudio(context()); AppStorage.endCleaning();
        assertFalse(unlinked.audio(0).exists());
    }
    @Test public void playbackAndSharingCannotStartWhileCleanupIsReserved() {
        assertTrue(AppStorage.beginCleaning());
        ServiceController<MeditationPlaybackService> playback = Robolectric.buildService(MeditationPlaybackService.class).create();
        playback.get().onStartCommand(new Intent().setAction("meditation.START"), 0, 1);
        assertEquals(context().getString(R.string.storage_cleaning), MeditationPlaybackService.state().getValue().error);
        assertFalse(MeditationPlaybackService.state().getValue().active); playback.destroy();
        ServiceController<MeditationExportService> sharing = Robolectric.buildService(MeditationExportService.class).create();
        sharing.get().onStartCommand(new Intent().putExtra("shareId", 1), 0, 1);
        assertEquals(context().getString(R.string.storage_cleaning), MeditationExportService.state().getValue().error);
        assertFalse(MeditationExportService.state().getValue().active); sharing.destroy();
        assertTrue(AppStorage.isCleaning());
    }
}
