package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeditationPausesTest {
    @Test public void commandsKeepTheirPositionAcrossLanguagesAndDecimalPunctuation() {
        String text = "Atme dreimal. [wait 18s]\n继续呼吸。[wait 1.25s] Still werden";
        List<String> parts = SpeechSegments.split(text);
        assertEquals(Arrays.asList("Atme dreimal.", "[wait 18s]", "继续呼吸。", "[wait 1.25s]", "Still werden"), parts);
        assertEquals(1.25, MeditationPauses.seconds(parts.get(3)), 0);
        for (int i = 0; i < parts.size(); i++) {
            int[] range = SpokenTextRanges.range(text, parts, i);
            assertEquals(parts.get(i), text.substring(range[0], range[1]));
        }
    }

    @Test public void leadingTrailingAdjacentAndInlineCommandsSurviveProviderLimits() {
        String text = "[wait 1s]Breathe[wait 2s][wait 3s]Relax[wait 300s]";
        assertEquals(Arrays.asList("[wait 1s]", "Breathe", "[wait 2s]", "[wait 3s]", "Relax", "[wait 300s]"), SpeechSegments.split(text));
        String longText = String.join("", java.util.Collections.nCopies(1800, "呼吸"));
        List<String> parts = SpeechSegments.split(longText + "[wait 12.5s]" + longText, 500);
        assertEquals(longText + "[wait 12.5s]" + longText, String.join("", parts));
        for (String part : parts) { assertTrue(part.length() <= 500); }
        assertTrue(parts.contains("[wait 12.5s]"));
    }

    @Test public void malformedOrUnboundedCommandsCannotBeSpoken() {
        for (String command : new String[] {"[wait]", "[wait 0s]", "[wait -2s]", "[wait 301s]", "[wait NaNs]",
                "[wait 1,5s]", "[wait 10 seconds]", "[wait 1.0001s]", "[wait 2s", "[wait10s]", "[wait 1e2s]"}) {
            assertThrows(command, IllegalArgumentException.class, () -> SpeechSegments.split("Breathe. " + command + " Relax."));
        }
        assertEquals(10, MeditationPauses.seconds("[WAIT  10 S ]"), 0);
        assertEquals(0.001, MeditationPauses.seconds("[wait 0.001s]"), 0);
        assertEquals(-1, MeditationPauses.seconds("Breathe."), 0);
        assertEquals(Arrays.asList("[waiting quietly] Breathe."), SpeechSegments.split("[waiting quietly] Breathe."));
    }

    @Test public void providerChangesPreservePausePositionAndOrdinaryGapsStillApply() {
        String text = String.join("", java.util.Collections.nCopies(900, "a")) + "[wait 18s]Rest. Finish.";
        PlaybackRequest device = new PlaybackRequest(text, SpeechBackend.DEVICE, "", "", 1, "", 5);
        PlaybackRequest qwen = new PlaybackRequest(text, SpeechBackend.QWEN, "qwen3-tts-flash", "Cherry", 1, "", 5);
        assertEquals(2, device.positionFor(qwen, 1));
        List<String> parts = SpeechSegments.split(text);
        assertEquals(0, MeditationPauses.gapBefore(parts, 1, 5), 0);
        assertEquals(0, MeditationPauses.gapBefore(parts, 2, 5), 0);
        assertEquals(5, MeditationPauses.gapBefore(parts, 3, 5), 0);
    }
}
