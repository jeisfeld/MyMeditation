package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OfflineDurationTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private SentenceAudioStore.Book book(String text, SpeechBackend backend, double speed, double pause) throws Exception {
        PlaybackRequest request = new PlaybackRequest(text, backend, backend.defaultModel, backend.defaultVoice, speed, "", pause);
        SentenceAudioStore.Book book = new SentenceAudioStore(files.getRoot()).open(request);
        for (int i = 0; i < book.sentences.size(); i++) {
            if (MeditationPauses.seconds(book.sentences.get(i)) >= 0) { continue; }
            book.obtain(i, file -> {
                try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) {
                    writer.silence(0.1, () -> { });
                }
            }, () -> { });
        }
        return book;
    }
    @Test public void durationUsesLocalSpeedAndWallClockPausesWithoutDoubleCountingGaps() throws Exception {
        int[] reads = {0};
        OfflineDuration duration = new OfflineDuration(file -> { reads[0]++; return 1000; });
        String text = "One.One.[wait 5s]Two.[wait 2s]";
        assertEquals(Long.valueOf(17500), duration.milliseconds(book(text, SpeechBackend.GEMINI, 2, 3), 3));
        assertEquals(2, reads[0]); // Repeated sentences use the same source metadata.
        assertEquals(Long.valueOf(13000), duration.milliseconds(book(text, SpeechBackend.GEMINI, 1, 1), 1));
        assertEquals(2, reads[0]);
        assertEquals(Long.valueOf(35000), duration.milliseconds(book("[wait 5s]", SpeechBackend.GEMINI, 4, 30), 30));
    }
    @Test public void percentageScalesWaitGroupsAndBaseGapButNotSpeechDuration() throws Exception {
        OfflineDuration duration = new OfflineDuration(file -> 1000);
        String text = "One.[wait 10s][wait 2s]Two.";
        assertEquals(Long.valueOf(8500), duration.milliseconds(book(text, SpeechBackend.GEMINI, 1, 0.5), 0.5));
        assertEquals(Long.valueOf(2000), duration.milliseconds(book(text, SpeechBackend.GEMINI, 1, 0), 0));
        assertEquals(Long.valueOf(15000), duration.milliseconds(book(text, SpeechBackend.GEMINI, 1, 1), 1));
    }
    @Test public void openAiAudioAlreadyContainsSpeedAndHasNoTrailingGap() throws Exception {
        OfflineDuration duration = new OfflineDuration(file -> 10000);
        assertEquals(Long.valueOf(23000), duration.milliseconds(book("One.Two.", SpeechBackend.OPENAI, 0.5, 3), 3));
        assertEquals(Long.valueOf(10000), duration.milliseconds(book("One.", SpeechBackend.OPENAI, 0.5, 30), 30));
    }
    @Test public void unavailableMetadataOrDeletedSourcesDoNotProduceFalseDurations() throws Exception {
        SentenceAudioStore.Book book = book("One.", SpeechBackend.GEMINI, 1, 2);
        assertNull(new OfflineDuration(file -> { throw new java.io.IOException(); }).milliseconds(book, 2));
        assertNull(new OfflineDuration(file -> 0).milliseconds(book, 2));
        OfflineDuration duration = new OfflineDuration(file -> 1000);
        assertEquals(Long.valueOf(1000), duration.milliseconds(book, 2));
        book.invalidate(0);
        assertNull(duration.milliseconds(book, 2));
    }
    @Test public void formattingSupportsLongMeditationsAndSecondCarry() {
        assertEquals("12:34", OfflineDuration.format(754000));
        assertEquals("1:00", OfflineDuration.format(59999));
        assertEquals("123:45", OfflineDuration.format(7425000));
    }
}
