package de.jeisfeld.mymeditation.audio;

import android.os.Looper;
import android.speech.tts.TextToSpeech;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.robolectric.shadows.ShadowTextToSpeech;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

public class DeviceTimedPauseTest extends BaselineTest {
    private MeditationSpeechPlayer player;
    private final List<Integer> completed = new ArrayList<>();
    private boolean finished;
    private ShadowTextToSpeech start(String text, int position) { return start(text, position, 30); }
    private ShadowTextToSpeech start(String text, int position, double pause) {
        if (player == null) {
            player = new MeditationSpeechPlayer(Application.getAppContext(), new MeditationSpeechPlayer.Listener() {
                @Override public void onSentence(int index) { }
                @Override public void onRunning(boolean running) { }
                @Override public void onError(String error) { fail(error); }
                @Override public void onCompletedSentence(int index) { completed.add(index); }
                @Override public void onFinished() { finished = true; }
            });
        }
        player.start(SpeechSegments.split(text), position, pause, SpeechBackend.DEVICE, "", "", 4, "");
        ShadowTextToSpeech tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance());
        tts.getOnInitListener().onInit(TextToSpeech.SUCCESS); idle();
        return tts;
    }
    private void advance(long millis) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis)); }
    @After public void close() { if (player != null) { player.close(); idle(); } }

    @Test public void activityPauseAddsConfiguredGapOnceAndIgnoresVoiceSpeed() {
        ShadowTextToSpeech tts = start("Breathe. [wait 1.25s] Relax.", 0);
        advance(500); assertEquals(Arrays.asList("Breathe."), tts.getSpokenTextList());
        player.setPauseSeconds(0);
        advance(31249); assertEquals(Arrays.asList("Breathe."), tts.getSpokenTextList());
        advance(1); assertEquals(Arrays.asList("Breathe.", "Relax."), tts.getSpokenTextList());
        assertEquals(Arrays.asList(0, 1, 2), completed); assertTrue(finished);
    }

    @Test public void fiftyPercentScalesWaitAndOneSecondBaseTogether() {
        ShadowTextToSpeech tts = start("Breathe.[wait 10s]Relax.", 0, 0.5);
        advance(500); assertEquals(Arrays.asList("Breathe."), tts.getSpokenTextList());
        advance(5499); assertEquals(Arrays.asList("Breathe."), tts.getSpokenTextList());
        advance(1); assertEquals(Arrays.asList("Breathe.", "Relax."), tts.getSpokenTextList());
        assertTrue(finished);
    }
    @Test public void zeroPercentSkipsWaitsWithoutBlockingCompletion() {
        ShadowTextToSpeech tts = start("Breathe.[wait 10s]Relax.", 0, 0);
        advance(500);
        assertEquals(Arrays.asList("Breathe.", "Relax."), tts.getSpokenTextList());
        assertTrue(finished);
    }

    @Test public void manualStartSkipsLeadingWaitsButNaturalTrailingWaitStillRuns() {
        ShadowTextToSpeech tts = start("[wait 1s][wait 2s]Rest.[wait 3s]", 0);
        advance(499); assertNull(tts.getLastSpokenText());
        advance(1); assertEquals(Arrays.asList("Rest."), tts.getSpokenTextList());
        assertFalse(finished);
        advance(32999); assertFalse(finished);
        advance(1); assertTrue(finished); assertEquals(Arrays.asList(2, 3), completed);
    }

    @Test public void selectingAWaitSkipsConsecutiveWaitsAndStartsWithSpeech() {
        ShadowTextToSpeech tts = start("Breathe.[wait 300s][wait 2s]Rest.", 1);
        advance(500); assertEquals(Arrays.asList("Rest."), tts.getSpokenTextList());
        assertTrue(finished); assertEquals(Arrays.asList(3), completed);
    }

    @Test public void stopCancelsANaturallyReachedPauseWithoutStaleCallbacks() {
        String text = "Breathe.[wait 300s]Rest.";
        ShadowTextToSpeech old = start(text, 0);
        advance(500); advance(1000); player.stop(); advance(330000);
        assertEquals(Arrays.asList("Breathe."), old.getSpokenTextList()); assertFalse(finished);
        assertEquals(Arrays.asList(0), completed);
        ShadowTextToSpeech current = start(text, 2);
        advance(500); assertEquals(Arrays.asList("Rest."), current.getSpokenTextList());
        assertTrue(finished); assertEquals(Arrays.asList(0, 2), completed);
    }
}
