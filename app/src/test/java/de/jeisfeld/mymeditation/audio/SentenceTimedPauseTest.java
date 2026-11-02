package de.jeisfeld.mymeditation.audio;

import android.media.MediaPlayer;
import android.os.Looper;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.io.File;
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

/** Cached cloud audio with real player timing, no API keys and no audio file for commands. */
public class SentenceTimedPauseTest extends BaselineTest {
    private MeditationSpeechPlayer player;
    private final List<MediaPlayer> media = new ArrayList<>();
    private final List<Integer> spoken = new ArrayList<>(), completed = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private boolean finished;
    private void advance(long millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && errors.isEmpty() && System.nanoTime() < deadline) { idle(); Thread.sleep(1); }
        assertTrue(errors.toString(), errors.isEmpty()); assertTrue(condition.getAsBoolean());
    }
    private void completeLast() {
        MediaPlayer current = media.get(media.size() - 1);
        shadowOf(current).getOnCompletionListener().onCompletion(current); idle();
    }
    @After public void close() { if (player != null) { player.close(); idle(); } }

    @Test public void savedReplayPrefetchesOnlySpeechAndWaitsBeforeContinuingAndFinishing() throws Exception {
        assertReplay(SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", "testVoice123");
    }

    @Test public void parallelProviderPrefetchAlsoSkipsPauseCommands() throws Exception {
        assertReplay(SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore");
    }

    private void assertReplay(SpeechBackend backend, String model, String voice) throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe.[wait 1.25s]Rest.[wait 2s]", backend, model, voice, 4, "", 30);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        for (int index : new int[] {0, 2}) {
            File file = book.obtain(index, target -> {
                try (FileOutputStream output = new FileOutputStream(target); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(60000, 0));
        }
        book.finish();
        ShadowMediaPlayer.setCreateListener((actual, shadow) -> media.add(actual));
        player = new MeditationSpeechPlayer(Application.getAppContext(), new MeditationSpeechPlayer.Listener() {
            @Override public void onSentence(int index) { }
            @Override public void onRunning(boolean running) { }
            @Override public void onError(String error) { errors.add(error); }
            @Override public void onSpeaking(int index) { spoken.add(index); }
            @Override public void onCompletedSentence(int index) { completed.add(index); }
            @Override public void onFinished() { finished = true; }
        });
        player.start(request, 0);
        long deadline = System.nanoTime() + 5000000000L;
        while (spoken.isEmpty() && System.nanoTime() < deadline) { advance(10); Thread.sleep(1); }
        assertEquals(Arrays.asList(0), spoken);
        completeLast(); assertEquals(Arrays.asList(0, 1), spoken);
        player.setPauseSeconds(0);
        advance(1249); assertEquals(1, media.size());
        advance(1); await(() -> media.size() == 2); idle();
        assertEquals(Arrays.asList(0, 1, 2), spoken);
        completeLast(); assertFalse(finished); advance(1999); assertFalse(finished);
        advance(1); assertTrue(finished);
        assertEquals(Arrays.asList(0, 1, 2, 3), completed);
        assertFalse(book.audio(1).exists()); assertFalse(book.audio(3).exists());
        assertTrue(errors.toString(), errors.isEmpty());
    }
}
