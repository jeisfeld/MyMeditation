package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.File;
import java.io.FileOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class OfflineCoverageTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private SentenceAudioStore.Book book(String text) {
        return new SentenceAudioStore(files.getRoot()).open(new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1, "", 0));
    }
    private void source(File file) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); }
    }
    @Test public void coverageUsesWordsAndRepeatedOccurrencesRatherThanSentenceCountOrWaits() throws Exception {
        SentenceAudioStore.Book book = book("One.This has four words.One.[wait 300s]");
        book.obtain(0, this::source, () -> { });
        OfflineCoverage coverage = OfflineCoverage.measure(book);
        assertEquals(6, coverage.totalWords); assertEquals(2, coverage.availableWords); assertEquals(333, coverage.perMille); assertFalse(coverage.complete);
        book.obtain(1, this::source, () -> { }); coverage = OfflineCoverage.measure(book);
        assertEquals(1000, coverage.perMille); assertTrue(coverage.complete);
        book.invalidate(0); assertEquals(666, OfflineCoverage.measure(book).perMille);
    }
    @Test public void unicodeAndWordlessSegmentsCannotGiveFalseCompleteCoverage() throws Exception {
        SentenceAudioStore.Book book = book("Hände fühlen.😀."); book.obtain(0, this::source, () -> { });
        OfflineCoverage partial = OfflineCoverage.measure(book);
        assertEquals(2, partial.totalWords); assertEquals(999, partial.perMille); assertFalse(partial.complete);
        book.obtain(1, this::source, () -> { }); assertTrue(OfflineCoverage.measure(book).complete);
        assertEquals(0, OfflineCoverage.measure(book("🌳.")).perMille);
        assertEquals(1000, OfflineCoverage.measure(book("[wait 5s]")).perMille);
    }
    @Test public void globalReuseAndNativeSpeedChangesAreIncludedInCoverage() throws Exception {
        SentenceAudioStore.Book first = book("One.Two words.");
        first.obtain(0, this::source, () -> { }); first.obtain(1, this::source, () -> { });
        SentenceAudioStore.Book reordered = new SentenceAudioStore(files.getRoot()).open(new PlaybackRequest("Two words.One.", first.request.backend,
                first.request.model, first.request.voice, 1.8, "", 5));
        assertEquals(3, OfflineCoverage.measure(reordered).availableWords); assertTrue(OfflineCoverage.measure(reordered).complete);
    }
}
