package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SentenceAudioAssemblerTest {
    @Rule public TemporaryFolder directory = new TemporaryFolder();
    @Test public void sharedMp3RendersExactExplicitPausesAndNormalGapsOnlyBetweenSpokenSentences() throws Exception {
        PlaybackRequest request = new PlaybackRequest("[wait 0.1s]First.[wait 0.25s][wait 0.5s]Second.Third.[wait 0.2s]",
                SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 4, "", 30);
        SentenceAudioStore.Book book = new SentenceAudioStore(directory.getRoot()).open(request);
        for (int index : new int[] {1, 4, 5}) {
            book.obtain(index, file -> {
                try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
        }
        book.finish();
        List<String> decoded = new ArrayList<>();
        long frames = SentenceAudioAssembler.write(book, 2, new ByteArrayOutputStream(), (file, writer, check) -> {
            decoded.add(file.getName()); writer.write(ByteBuffer.allocate(2000));
        }, count -> { }, () -> { });
        assertEquals(Arrays.asList("1.mp3", "4.mp3", "5.mp3"), decoded);
        assertEquals(3000 + Math.round(3.05 * Mp3Writer.SAMPLE_RATE), frames);
    }
    private SentenceAudioStore.Book book(boolean complete) throws Exception {
        PlaybackRequest request = new PlaybackRequest("First. Second. Third.", SpeechBackend.GEMINI,
                "gemini-3.8-flash-tts", "Kore", 1.5, "", 0);
        SentenceAudioStore.Book book = new SentenceAudioStore(directory.getRoot()).open(request);
        for (int i = 0; i < (complete ? 3 : 2); i++) {
            book.obtain(i, file -> {
                try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
        }
        if (complete) { book.finish(); }
        return book;
    }
    @Test public void assemblyUsesSentenceOrderAndOnlyInsertsTheChosenPausesBetweenSentences() throws Exception {
        SentenceAudioStore.Book book = book(true);
        List<String> order = new ArrayList<>(); List<Integer> progress = new ArrayList<>();
        ByteArrayOutputStream mp3 = new ByteArrayOutputStream();
        long frames = SentenceAudioAssembler.write(book, 2, mp3, (file, writer, check) -> {
            order.add(file.getName()); writer.write(ByteBuffer.allocate(2000));
        }, progress::add, () -> { });
        assertEquals(Arrays.asList("0.mp3", "1.mp3", "2.mp3"), order);
        assertEquals(Arrays.asList(1, 2, 3), progress);
        assertEquals(3000 + 4L * Mp3Writer.SAMPLE_RATE, frames);
        assertTrue(mp3.size() > 100); assertEquals(255, mp3.toByteArray()[0] & 255);
        assertTrue(book.isComplete()); // Sharing never modifies the stored speech settings or sentence files.
    }
    @Test public void zeroPauseAddsNoLeadingTrailingOrInterSentenceSilence() throws Exception {
        assertEquals(3000, SentenceAudioAssembler.write(book(true), 0, new ByteArrayOutputStream(),
                (file, writer, check) -> writer.write(ByteBuffer.allocate(2000)), count -> { }, () -> { }));
    }
    @Test public void incompleteOrInvalidRequestsCannotBeShared() throws Exception {
        SentenceAudioStore.Book incomplete = book(false);
        assertThrows(java.io.IOException.class, () -> SentenceAudioAssembler.write(incomplete, 2, new ByteArrayOutputStream(),
                (file, writer, check) -> fail("Must not decode incomplete recordings"), count -> { }, () -> { }));
        book(true); incomplete.finish();
        for (double pause : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1, 31}) {
            assertThrows(IllegalArgumentException.class, () -> SentenceAudioAssembler.write(incomplete, pause,
                    new ByteArrayOutputStream(), (file, writer, check) -> fail("Invalid pause"), count -> { }, () -> { }));
        }
    }
    @Test public void cancellationStopsAssemblyWithoutConsumingFurtherSentenceFiles() throws Exception {
        SentenceAudioStore.Book book = book(true);
        List<String> decoded = new ArrayList<>();
        assertThrows(CancellationException.class, () -> SentenceAudioAssembler.write(book, 2, new ByteArrayOutputStream(),
                (file, writer, check) -> { decoded.add(file.getName()); throw new CancellationException(); }, count -> { }, () -> { }));
        assertEquals(Arrays.asList("0.mp3"), decoded); assertTrue(book.isComplete());
    }
}
