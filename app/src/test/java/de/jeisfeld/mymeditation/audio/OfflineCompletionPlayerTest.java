package de.jeisfeld.mymeditation.audio;

import android.media.MediaPlayer;
import android.os.Looper;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.i18n.UiText;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

/** Real persistence and player scheduling; provider generation is replaced with a controlled producer. */
public class OfflineCompletionPlayerTest extends BaselineTest {
    private static final String TEXT = "Zero.[wait 300s]One.Two.Three.Four.Five.Six.";
    private MeditationSpeechPlayer player;
    private CountDownLatch gate;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<Integer> created = new CopyOnWriteArrayList<>();
    private final List<MediaPlayer> media = new CopyOnWriteArrayList<>();
    private final List<String> errors = new CopyOnWriteArrayList<>(), statuses = new CopyOnWriteArrayList<>();
    private int spoken = -1;
    private boolean finished;
    private int failIndex = -1;
    private PlaybackRequest request(double speed) {
        return new PlaybackRequest(TEXT, SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", "voice123456789", speed, "", 0);
    }
    private void mp3(File file) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
    }
    private void register(File file) {
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(60000, 0));
    }
    private SentenceAudioStore.Book prepare(PlaybackRequest request, Integer... missing) throws Exception {
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        for (int i = 0; i < book.sentences.size(); i++) {
            if (MeditationPauses.seconds(book.sentences.get(i)) >= 0 || Arrays.asList(missing).contains(i)) { continue; }
            register(book.obtain(i, this::mp3, () -> { }));
        }
        return book;
    }
    private void start(PlaybackRequest request) {
        if (player == null) {
            ShadowMediaPlayer.setCreateListener((actual, shadow) -> media.add(actual));
            player = new MeditationSpeechPlayer(Application.getAppContext(), new MeditationSpeechPlayer.Listener() {
                @Override public void onSentence(int index) { }
                @Override public void onRunning(boolean value) { }
                @Override public void onSpeaking(int index) { spoken = index; }
                @Override public void onError(String error) { errors.add(error); }
                @Override public void onStatus(String status) { statuses.add(status); }
                @Override public void onFinished() { finished = true; }
            }, (captured, index, speech, check) -> {
                SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(captured);
                assertTrue(MeditationPauses.seconds(book.sentences.get(index)) < 0);
                return book.obtain(index, file -> {
                    created.add(index);
                    if (gate != null) { assertTrue(gate.await(5, TimeUnit.SECONDS)); }
                    try { check.run(); } catch (java.util.concurrent.CancellationException e) { cancelled.set(true); throw e; }
                    if (index == failIndex) { throw new java.io.IOException("Test generation failure"); }
                    mp3(file); register(book.audio(index));
                }, check);
            });
        }
        player.start(request, 7); // Start at the cached last sentence; missing clips are earlier in the text.
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(1);
        }
        assertTrue(errors.toString(), errors.isEmpty()); assertTrue(condition.getAsBoolean());
    }
    private void completeLastSpeech() {
        MediaPlayer current = media.get(media.size() - 1);
        shadowOf(current).getOnCompletionListener().onCompletion(current); idle();
    }
    @After public void close() { if (gate != null) { gate.countDown(); } if (player != null) { player.close(); idle(); } }

    @Test public void threeMissingClipsAreCreatedInBackgroundWithoutPlayingThemOrGeneratingWaitAudio() throws Exception {
        SentenceAudioStore.Book book = prepare(request(1), 0, 3, 5);
        start(request(1)); await(book::isComplete);
        assertTrue(created.containsAll(Arrays.asList(0, 3, 5))); assertEquals(3, created.size());
        assertFalse(book.audio(1).exists());
        assertFalse(new File(book.audio(0).getParentFile(), "played-0").exists());
    }
    @Test public void fourMissingClipsDoNotTriggerBackgroundCompletion() throws Exception {
        SentenceAudioStore.Book book = prepare(request(1), 0, 2, 3, 4);
        start(request(1)); await(() -> spoken == 7); completeLastSpeech(); await(() -> finished);
        assertTrue(created.isEmpty()); assertFalse(book.isComplete());
    }
    @Test public void naturalPlaybackEndWaitsForPendingCompletion() throws Exception {
        gate = new CountDownLatch(1);
        SentenceAudioStore.Book book = prepare(request(1), 0, 3);
        start(request(1)); await(() -> spoken == 7 && !created.isEmpty());
        completeLastSpeech(); await(() -> statuses.contains(UiText.COMPLETING_OFFLINE.text()));
        assertNotNull(shadowOf((android.media.AudioManager) Application.getAppContext().getSystemService(android.content.Context.AUDIO_SERVICE))
                .getLastAbandonedAudioFocusRequest());
        assertFalse(finished); gate.countDown(); await(() -> finished);
        assertTrue(book.isComplete());
    }
    @Test public void explicitStopCancelsPendingCreationAndKeepsCompletedClips() throws Exception {
        gate = new CountDownLatch(1);
        SentenceAudioStore.Book book = prepare(request(1), 0, 3);
        start(request(1)); await(() -> !created.isEmpty()); player.stop(); gate.countDown(); await(cancelled::get);
        assertNull(book.available(0)); assertNotNull(book.available(7)); assertFalse(book.isComplete()); assertFalse(finished);
    }
    @Test public void settingsRestartCancelsOldCompletionAndKeepsIdentitiesSeparate() throws Exception {
        gate = new CountDownLatch(1);
        SentenceAudioStore.Book old = prepare(request(1), 0, 3);
        SentenceAudioStore.Book replacement = prepare(request(1.25));
        start(request(1)); await(() -> !created.isEmpty());
        start(request(1.25)); gate.countDown(); await(cancelled::get); await(() -> spoken == 7);
        assertFalse(old.isComplete()); assertTrue(replacement.isComplete()); assertNotEquals(old.identity, replacement.identity);
    }
    @Test public void backgroundFailureDoesNotInterruptPlayableSpeechOrCreateFalseOfflineAvailability() throws Exception {
        failIndex = 0;
        SentenceAudioStore.Book book = prepare(request(1), 0);
        start(request(1)); await(() -> spoken == 7 && created.contains(0));
        completeLastSpeech(); await(() -> finished);
        assertFalse(book.isComplete()); assertEquals(Arrays.asList(0), created); assertTrue(errors.isEmpty());
    }
}
