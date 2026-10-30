package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Looper;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

public class SentencePlaybackServiceTest extends BaselineTest {
    private ServiceController<MeditationPlaybackService> controller;
    private MeditationPlaybackService service;
    private final List<MediaPlayer> players = new ArrayList<>();
    private PlaybackRequest request(double speed) {
        return new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", speed, "Calm", 0);
    }
    private SentenceAudioStore.Book prefetched(double speed) throws Exception {
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request(speed));
        for (int i = 0; i < book.sentences.size(); i++) {
            File file = book.obtain(i, target -> {
                try (FileOutputStream output = new FileOutputStream(target); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(60000, 0));
        }
        return book;
    }
    private void start() {
        PreferenceUtil.setPauseSeconds(0); SavedAudioLibrary.restoreVoice(request(1));
        ShadowMediaPlayer.setCreateListener((actual, shadow) -> players.add(actual));
        controller = Robolectric.buildService(MeditationPlaybackService.class).create(); service = controller.get();
        service.onStartCommand(new Intent().setAction("meditation.START"), 0, 1);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(1);
            assertEquals("", MeditationPlaybackService.state().getValue().error);
        }
        assertTrue(condition.getAsBoolean());
    }
    private void complete(int index) { shadowOf(players.get(index)).getOnCompletionListener().onCompletion(players.get(index)); idle(); }
    @After public void close() { if (controller != null) { controller.destroy(); idle(); } }

    @Test public void listeningToAllSentencesAutomaticallyKeepsACompleteAudioMeditation() throws Exception {
        SentenceAudioStore.Book book = prefetched(1); assertFalse(book.isComplete());
        start(); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        complete(0);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        complete(1);
        await(() -> !PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        assertTrue(book.isComplete());
        int id = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).get(0);
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertEquals(request(1).text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
    }
    @Test public void changingVoiceSpeedMidMeditationDoesNotPublishAMixedRecording() throws Exception {
        SentenceAudioStore.Book original = prefetched(1), changed = prefetched(1.25);
        start(); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        complete(0); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        SavedAudioLibrary.restoreVoice(request(1.25));
        service.onStartCommand(new Intent().setAction("meditation.APPLY_SETTINGS"), 0, 2);
        await(() -> players.size() == 3 && MeditationPlaybackService.state().getValue().spokenPosition == 1);
        complete(2);
        await(() -> new File(changed.audio(1).getParentFile(), "played-1").exists());
        idle();
        assertFalse(original.isComplete()); assertFalse(changed.isComplete());
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
}
