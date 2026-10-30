package de.jeisfeld.mymeditation.audio;

import android.media.MediaPlayer;
import android.os.Looper;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Existing sentence files must play with no credentials, speech synthesis or whole-file player. */
public class SentenceReplayTest extends BaselineTest {
    private MeditationSpeechPlayer player;
    private final List<MediaPlayer> media = new ArrayList<>();
    private final List<Integer> spoken = new ArrayList<>(), completed = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private int position, secondStatuses;
    private boolean finished;
    private final PlaybackRequest request = new PlaybackRequest("Breathe. Relax.", SpeechBackend.GEMINI,
            "gemini-3.8-flash-tts", "Kore", 1.5, "Calm", 3);

    private void start(int index) throws Exception {
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        for (int i = 0; i < book.sentences.size(); i++) {
            java.io.File file = book.obtain(i, target -> {
                try (FileOutputStream output = new FileOutputStream(target); Mp3Writer writer = new Mp3Writer(output)) {
                    writer.silence(0.1, () -> { });
                }
            }, () -> { });
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(60000, 0));
        }
        book.finish();
        ShadowMediaPlayer.setCreateListener((actual, shadow) -> media.add(actual));
        player = new MeditationSpeechPlayer(Application.getAppContext(), new MeditationSpeechPlayer.Listener() {
            @Override public void onSentence(int value) { position = value; }
            @Override public void onRunning(boolean value) { }
            @Override public void onSpeaking(int value) { spoken.add(value); }
            @Override public void onCompletedSentence(int value) { completed.add(value); }
            @Override public void onStatus(String value) { if (position == 1 && value.isEmpty()) { secondStatuses++; } }
            @Override public void onError(String error) { errors.add(error); }
            @Override public void onFinished() { finished = true; }
        });
        player.start(request, index);
        long deadline = System.nanoTime() + 5000000000L;
        while (spoken.isEmpty() && errors.isEmpty() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(1);
        }
        assertTrue(errors.toString(), errors.isEmpty()); assertFalse("Saved sentence never started", spoken.isEmpty());
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { idle(); Thread.sleep(1); }
        assertTrue(errors.toString(), errors.isEmpty()); assertTrue(condition.getAsBoolean());
    }
    private void completeLast() { shadowOf(media.get(media.size() - 1)).getOnCompletionListener().onCompletion(media.get(media.size() - 1)); idle(); }
    @After public void closePlayer() { if (player != null) { player.close(); idle(); } }

    @Test public void replayReportsSentenceHighlightsAndUsesTheUpdatedPauseWithoutRegeneration() throws Exception {
        start(0);
        assertEquals(Arrays.asList(0), spoken);
        assertFalse("A baked sentence must be restarted under a new speed identity", player.setPlaybackSpeed(1.7));
        player.setPauseSeconds(0.5);
        completeLast(); await(() -> secondStatuses >= 2);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(499));
        assertEquals(Arrays.asList(0), spoken);
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1));
        assertEquals(Arrays.asList(0, 1), spoken);
        completeLast();
        assertEquals(Arrays.asList(0, 1), completed); assertTrue(finished); assertTrue(errors.isEmpty());
    }
    @Test public void savedReplayCanStartAtASelectedSentence() throws Exception {
        start(1); assertEquals(Arrays.asList(1), spoken);
        completeLast(); assertEquals(Arrays.asList(1), completed); assertTrue(finished);
    }
    @Test public void stoppingDuringASavedSentencePauseCancelsThePendingReplay() throws Exception {
        start(0); completeLast(); await(() -> secondStatuses >= 2);
        player.stop(); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20));
        assertEquals(Arrays.asList(0), spoken); assertFalse(finished); assertTrue(errors.isEmpty());
    }
}
