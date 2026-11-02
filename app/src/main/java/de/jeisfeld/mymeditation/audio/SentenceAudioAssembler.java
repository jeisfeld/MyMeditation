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
                double explicitPause = MeditationPauses.seconds(book.sentences.get(i));
                if (explicitPause >= 0) { writer.silence(explicitPause, check); continue; }
                writer.silence(MeditationPauses.gapBefore(book.sentences, i, pause), check);
                File audio = book.available(i);
                if (audio == null) { throw new java.io.IOException("Missing sentence audio"); }
                long before = writer.frames();
                decoder.append(audio, writer, check);
                if (writer.frames() == before) { throw new java.io.IOException("Empty sentence audio"); }
            }
            check.run(); return writer.frames();
        }
    }
}
