package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@Config(shadows = OfflinePreparationTest.Speech.class)
public class OfflinePreparationTest extends BaselineTest {
    @Implements(value = CloudSpeech.class, isInAndroidSdk = false)
    public static class Speech {
        static File source;
        static List<String> calls;
        static CountDownLatch gate;
        static String fail;
        @Implementation public File audioWithPersistentCache(File cache, File durable, SpeechBackend backend, String model,
                String voice, String text, double speed, String style) throws Exception {
            synchronized (calls) { calls.add(text); }
            if (gate != null && !gate.await(5, TimeUnit.SECONDS)) { throw new java.io.IOException("test wait"); }
            if (text.equals(fail)) { throw new java.io.IOException("test failure"); }
            return source;
        }
        @Implementation public void cancel() { if (gate != null) { gate.countDown(); } }
    }
    private ServiceController<MeditationOfflineService> controller;
    private PlaybackRequest request(String text) { return new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1.2, "Calm", 0); }
    @Before public void setupSource() throws Exception {
        Speech.calls = java.util.Collections.synchronizedList(new ArrayList<>()); Speech.gate = null; Speech.fail = null;
        Speech.source = new File(Application.getAppContext().getCacheDir(), "preparation-test.mp3");
        try (FileOutputStream out = new FileOutputStream(Speech.source); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); }
    }
    private int saved(PlaybackRequest request) { return SavedMeditations.save("Saved name", "s", "@TEXT@", "topic", request.text); }
    private void start(int id, PlaybackRequest request) {
        controller = Robolectric.buildService(MeditationOfflineService.class).create();
        controller.get().onStartCommand(new Intent().putExtra("savedId", id).putExtra("request", request.json()), 0, 1);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < until) { idle(); Thread.sleep(2); }
        assertTrue(condition.getAsBoolean());
    }
    @After public void close() { if (Speech.gate != null) { Speech.gate.countDown(); } if (controller != null) { controller.destroy(); idle(); } AppStorage.endCleaning(); }
    @Test public void preparesOnlyMissingSpeechAndAttachesSameEntryWithoutTouchingDraftOrSettings() throws Exception {
        PlaybackRequest request = request("One.Four words are missing.[wait 100s]One."); int id = saved(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        book.obtain(0, target -> Files.copy(Speech.source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING), () -> { });
        String text = PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text), backend = PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend);
        start(id, request); await(() -> !MeditationOfflineService.state().getValue().active);
        assertEquals(java.util.Arrays.asList("Four words are missing."), Speech.calls);
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertEquals(java.util.Arrays.asList(id), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        assertEquals("Saved name", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id));
        assertEquals(text, PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        assertEquals(backend, PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend));
        assertEquals(1000, MeditationOfflineService.state().getValue().coverage.perMille);
    }
    @Test public void cancellationKeepsCompletedAudioAndBlocksCleanupAndPlaybackWhileActive() throws Exception {
        PlaybackRequest request = request("Missing speech."); int id = saved(request); Speech.gate = new CountDownLatch(1);
        start(id, request); await(() -> !Speech.calls.isEmpty());
        assertFalse(AppStorage.beginCleaning());
        ServiceController<MeditationPlaybackService> playback = Robolectric.buildService(MeditationPlaybackService.class).create();
        playback.get().onStartCommand(new Intent().setAction("meditation.START"), 0, 1);
        assertEquals(Application.getAppContext().getString(R.string.offline_prepare_busy), MeditationPlaybackService.state().getValue().error);
        playback.destroy();
        controller.get().onStartCommand(new Intent().setAction("offline.CANCEL"), 0, 2); idle();
        assertFalse(MeditationOfflineService.state().getValue().active);
        assertFalse(SavedAudioLibrary.hasRecording(id));
        assertEquals("gemini", SavedAudioLibrary.offlineRequest(Application.getAppContext(), id).backend.id);
    }
    @Test public void failureAndRetryReuseCompletedSentences() throws Exception {
        PlaybackRequest request = request("First sentence.Second sentence."); int id = saved(request); Speech.fail = "Second sentence.";
        start(id, request); await(() -> !MeditationOfflineService.state().getValue().active);
        assertFalse(SavedAudioLibrary.hasRecording(id));
        assertNotNull(SentenceAudio.store(Application.getAppContext()).open(request).available(0));
        assertEquals(500, MeditationOfflineService.state().getValue().coverage.perMille);
        controller.destroy(); controller = null; Speech.fail = null; Speech.calls.clear();
        start(id, request); await(() -> !MeditationOfflineService.state().getValue().active);
        assertEquals(java.util.Arrays.asList("Second sentence."), Speech.calls); assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
    }
    @Test public void deletingEntryDuringGenerationCannotResurrectIt() throws Exception {
        PlaybackRequest request = request("Slow missing speech."); int id = saved(request); Speech.gate = new CountDownLatch(1);
        start(id, request); await(() -> !Speech.calls.isEmpty());
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, new ArrayList<>());
        Speech.gate.countDown(); await(() -> !MeditationOfflineService.state().getValue().active);
        assertFalse(SavedAudioLibrary.hasRecording(id)); assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        assertEquals(Application.getAppContext().getString(R.string.offline_entry_changed), MeditationOfflineService.state().getValue().message);
    }
    @Test public void fullySharedAudioCanBeAttachedWithoutAnyGeneration() throws Exception {
        PlaybackRequest request = request("Already stored."); int id = saved(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        book.obtain(0, target -> Files.copy(Speech.source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING), () -> { });
        start(id, request); await(() -> !MeditationOfflineService.state().getValue().active);
        assertTrue(Speech.calls.isEmpty()); assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
    }
}
