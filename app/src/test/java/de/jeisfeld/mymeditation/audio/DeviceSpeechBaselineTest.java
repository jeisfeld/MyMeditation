package de.jeisfeld.mymeditation.audio;

import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

/** Real player logic with Android's TTS boundary simulated; no audio engine or network needed. */
public class DeviceSpeechBaselineTest extends BaselineTest {
    private final Events events = new Events();
    private MeditationSpeechPlayer player;
    private static final class Events implements MeditationSpeechPlayer.Listener {
        final List<Integer> positions = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
        boolean running, finished;
        int finishes;
        @Override public void onSentence(int index) { positions.add(index); }
        @Override public void onRunning(boolean value) { running = value; }
        @Override public void onError(String message) { errors.add(message); }
        @Override public void onFinished() { finished = true; finishes++; }
    }
    private ShadowTextToSpeech start(List<String> sentences, int position, double pause) {
        if (player == null) { player = new MeditationSpeechPlayer(Application.getAppContext(), events); }
        player.start(sentences, position, pause, SpeechBackend.DEVICE, "", "", 1, "");
        return shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
    }
    private void advance(long millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    @After public void release() { if (player != null) { player.close(); idle(); } }

    @Test public void sentencesAreSpokenInOrderWithConfiguredPauseAndFinishOnce() {
        ShadowTextToSpeech tts = start(Arrays.asList("Breathe.", "Relax."), 0, 2);
        tts.getOnInitListener().onInit(TextToSpeech.SUCCESS); idle();
        advance(499); assertNull(tts.getLastSpokenText());
        advance(1); assertEquals("Breathe.", tts.getLastSpokenText());
        advance(1999); assertEquals("Breathe.", tts.getLastSpokenText());
        advance(1); assertEquals(Arrays.asList("Breathe.", "Relax."), tts.getSpokenTextList());
        assertEquals(Arrays.asList(0, 1), events.positions);
        assertTrue(events.finished); assertEquals(1, events.finishes);
        assertFalse(events.running); assertTrue(events.errors.isEmpty());
        assertTrue(tts.isShutdown());
    }

    @Test public void stoppingDuringThePausePreventsRemainingSpeech() {
        ShadowTextToSpeech tts = start(Arrays.asList("First.", "Second."), 0, 3);
        tts.getOnInitListener().onInit(TextToSpeech.SUCCESS); idle(); advance(500);
        player.stop(); advance(10000);
        assertEquals(Collections.singletonList("First."), tts.getSpokenTextList());
        assertFalse(events.finished); assertFalse(events.running); assertTrue(tts.isStopped());
    }

    @Test public void selectedPositionIsUsedAndLateCallbacksFromOldPlaybackAreIgnored() {
        List<String> sentences = Arrays.asList("First.", "Second.", "Third.");
        ShadowTextToSpeech old = start(sentences, 1, 3);
        old.getOnInitListener().onInit(TextToSpeech.SUCCESS); idle(); advance(500);
        assertEquals("Second.", old.getLastSpokenText());
        UtteranceProgressListener oldCallbacks = old.getUtteranceProgressListener();
        ShadowTextToSpeech current = start(sentences, 0, 3);
        current.getOnInitListener().onInit(TextToSpeech.SUCCESS); idle();
        oldCallbacks.onDone("old-sentence"); idle(); advance(500);
        assertEquals("First.", current.getLastSpokenText());
        assertTrue(old.isShutdown());
        assertTrue(events.errors.isEmpty());
    }

    @Test public void ttsInitializationFailureStopsPlaybackWithoutDiscardingTextOrContinuing() {
        ShadowTextToSpeech tts = start(Collections.singletonList("Keep this text."), 0, 1);
        tts.getOnInitListener().onInit(TextToSpeech.ERROR); idle(); advance(10000);
        assertFalse(events.running); assertFalse(events.finished);
        assertEquals(1, events.errors.size()); assertFalse(events.errors.get(0).isEmpty());
        assertNull(tts.getLastSpokenText()); assertTrue(tts.isShutdown());
    }

    @Test public void emptyMeditationDoesNotInitializeTtsOrStartPlayback() {
        player = new MeditationSpeechPlayer(Application.getAppContext(), events);
        player.start(Collections.emptyList(), 0, 1, SpeechBackend.DEVICE, "", "", 1, "");
        idle(); advance(10000);
        assertNull(ShadowTextToSpeech.getLastTextToSpeechInstance());
        assertFalse(events.running); assertTrue(events.errors.isEmpty());
    }
}
