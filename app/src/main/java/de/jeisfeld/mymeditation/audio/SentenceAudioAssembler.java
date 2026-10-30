package de.jeisfeld.mymeditation.audio;

import java.io.File;
import java.io.OutputStream;
import java.util.function.IntConsumer;

/** One encoder, ordered sentence audio, and gaps only between sentences. No provider access. */
public final class SentenceAudioAssembler {
    private SentenceAudioAssembler() { }
    public interface Decoder { void append(File file, Mp3Writer writer, Runnable check) throws Exception; }
    public static long write(SentenceAudioStore.Book book, double pause, OutputStream output,
                             Decoder decoder, IntConsumer progress, Runnable check) throws Exception {
        if (!Double.isFinite(pause) || pause < 0 || pause > 30) { throw new IllegalArgumentException("Invalid share pause"); }
        if (!book.isComplete()) { throw new java.io.IOException("Incomplete sentence recording"); }
        try (Mp3Writer writer = new Mp3Writer(output)) {
            for (int i = 0; i < book.sentences.size(); i++) {
                check.run(); progress.accept(i + 1);
                File audio = book.available(i);
                if (audio == null) { throw new java.io.IOException("Missing sentence audio"); }
                long before = writer.frames();
                decoder.append(audio, writer, check);
                if (writer.frames() == before) { throw new java.io.IOException("Empty sentence audio"); }
                if (i + 1 < book.sentences.size()) { writer.silence(pause, check); }
            }
            check.run(); return writer.frames();
        }
    }
}
