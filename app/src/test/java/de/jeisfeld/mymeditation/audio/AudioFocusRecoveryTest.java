package de.jeisfeld.mymeditation.audio;

import org.junit.Test;
import static org.junit.Assert.*;
import static de.jeisfeld.mymeditation.audio.AudioFocusRecovery.Action.*;
import static de.jeisfeld.mymeditation.audio.AudioFocusRecovery.Change.*;

public class AudioFocusRecoveryTest {
    @Test public void temporaryInterruptionReplaysTheInterruptedSentenceOnce() {
        AudioFocusRecovery recovery = new AudioFocusRecovery();
        recovery.start();
        assertEquals(NONE, recovery.change(GAIN, 4));
        assertEquals(SUSPEND, recovery.change(TEMPORARY_LOSS, 4));
        assertEquals(4, recovery.sentence());
        assertEquals(RESUME, recovery.change(GAIN, 4));
        assertEquals(4, recovery.sentence());
        assertEquals(NONE, recovery.change(GAIN, 4));
    }
    @Test public void repeatedLossDoesNotLoseTheOriginalInterruptedPosition() {
        AudioFocusRecovery recovery = new AudioFocusRecovery();
        recovery.start();
        assertEquals(SUSPEND, recovery.change(TEMPORARY_LOSS, 7));
        assertEquals(NONE, recovery.change(TEMPORARY_LOSS, 8));
        assertEquals(RESUME, recovery.change(GAIN, 8));
        assertEquals(7, recovery.sentence());
        assertEquals(SUSPEND, recovery.change(TEMPORARY_LOSS, 12));
        assertEquals(RESUME, recovery.change(GAIN, 12));
        assertEquals(12, recovery.sentence());
    }
    @Test public void manualPauseStopHeadphoneDisconnectAndShutdownCancelAutomaticResume() {
        for (boolean initiallyInterrupted : new boolean[] {false, true}) {
            AudioFocusRecovery recovery = new AudioFocusRecovery();
            recovery.start();
            if (initiallyInterrupted) { recovery.change(TEMPORARY_LOSS, 3); }
            recovery.cancel();
            assertEquals(NONE, recovery.change(GAIN, 3));
            assertEquals(NONE, recovery.change(TEMPORARY_LOSS, 3));
        }
    }
    @Test public void permanentLossDoesNotResumeWhenFocusLaterChanges() {
        AudioFocusRecovery recovery = new AudioFocusRecovery();
        recovery.start(); recovery.change(TEMPORARY_LOSS, 3);
        assertEquals(STOP, recovery.change(PERMANENT_LOSS, 3));
        assertEquals(NONE, recovery.change(GAIN, 3));
        recovery.start();
        assertEquals(SUSPEND, recovery.change(TEMPORARY_LOSS, 0));
        assertEquals(RESUME, recovery.change(GAIN, 0));
        assertEquals(0, recovery.sentence());
    }
}
