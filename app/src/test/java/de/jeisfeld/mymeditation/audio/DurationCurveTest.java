package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class DurationCurveTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private PlaybackRequest request(String text, double pause) {
        return new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1, "", pause);
    }
    @Test public void solvesBothRangesAndTheirBoundaryWithoutSnappingToSliderSteps() {
        DurationCurve curve = new DurationCurve(request("[wait 9s]A.[wait 10s][wait 5s]B.C.[wait 2s]", 2), 83000);
        assertEquals(60000, curve.minimum()); assertEquals(167000, curve.maximum());
        assertEquals(83000, curve.current());
        assertEquals(0, curve.pauseFor(60000), 0);
        assertEquals(0.25, curve.pauseFor(65000), 0);
        assertEquals(1, curve.pauseFor(80000), 0);
        assertEquals(13 / 3.0, curve.pauseFor(90000), 0.000001);
        assertEquals(30, curve.pauseFor(167000), 0);
        assertEquals(90000, curve.milliseconds(curve.pauseFor(90000)));
        assertThrows(IllegalArgumentException.class, () -> curve.pauseFor(59000));
        assertThrows(IllegalArgumentException.class, () -> curve.pauseFor(168000));
    }
    @Test public void ordinaryGapsWithoutWaitsCanBeAdjustedInBothRanges() {
        DurationCurve curve = new DurationCurve(request("A.B.C.", 10), 80000);
        assertEquals(0.5, curve.pauseFor(61000), 0);
        assertEquals(20, curve.pauseFor(100000), 0);
    }
    @Test public void inversionMatchesMeasuredDurationForNativeAndServerSpeedAudio() throws Exception {
        for (SpeechBackend backend : new SpeechBackend[]{SpeechBackend.GEMINI, SpeechBackend.OPENAI}) {
            PlaybackRequest request = new PlaybackRequest("[wait 10s]A.[wait 3s][wait 4s]B.C.[wait 2s]",
                    backend, backend.defaultModel, backend.defaultVoice, 2, "", 2);
            SentenceAudioStore.Book book = new SentenceAudioStore(files.getRoot()).open(request);
            for (int i : new int[]{1, 4, 5}) {
                book.obtain(i, file -> Files.write(file.toPath(), new byte[]{'I', 'D', '3', 0}), () -> { });
            }
            OfflineDuration measured = new OfflineDuration(file -> 10000);
            DurationCurve curve = new DurationCurve(request, measured.milliseconds(book, 2));
            for (double pause : new double[]{0, 0.05, 0.5, 1, 1.5, 3.333, 30}) {
                long target = measured.milliseconds(book, pause);
                assertEquals(target, curve.milliseconds(pause));
                assertEquals(target, curve.milliseconds(curve.pauseFor(target)));
            }
        }
    }
    @Test public void unadjustableMeditationKeepsExistingPauseAndUsesDisplayedSecondRounding() {
        DurationCurve curve = new DurationCurve(request("[wait 10s]Only speech.", 5), 10499);
        assertFalse(curve.adjustable());
        assertEquals(5, curve.pauseFor(10000), 0);
        assertThrows(IllegalArgumentException.class, () -> curve.pauseFor(11000));
        DurationCurve roundUp = new DurationCurve(request("Only speech.", 5), 10500);
        assertEquals(5, roundUp.pauseFor(11000), 0);
        assertThrows(IllegalArgumentException.class, () -> roundUp.pauseFor(10000));
    }
    @Test public void parsesMinutesAndSecondsAndRejectsInvalidOrOverflowingDurations() {
        assertEquals(1200000, DurationCurve.parse("20:00"));
        assertEquals(7425000, DurationCurve.parse("123:45"));
        assertEquals(0, DurationCurve.parse(" 0:00 "));
        for (String input : new String[]{"20", "1:60", "-1:00", "0:1", "1:00:00", "NaN", "99999999999999999999:00"}) {
            assertThrows(input, IllegalArgumentException.class, () -> DurationCurve.parse(input));
        }
    }
}
