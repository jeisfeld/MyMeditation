package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.File;
import java.io.FileOutputStream;

/** Shared generation path for live playback and explicit saves; speed is baked into each MP3. */
public final class SentenceAudio {
    private SentenceAudio() { }
    public static SentenceAudioStore store(Context context) {
        return new SentenceAudioStore(new File(context.getFilesDir(), "sentence-audio"));
    }
    public static File obtain(Context context, PlaybackRequest request, int index, CloudSpeech speech, Runnable check) throws Exception {
        SentenceAudioStore.Book book = store(context).open(request);
        return book.obtain(index, destination -> {
            File source = speech.audioWithPersistentCache(context.getCacheDir(), context.getNoBackupFilesDir(), request.backend,
                    request.model, request.voice, book.sentences.get(index), request.speed, request.style);
            check.run();
            try (FileOutputStream output = new FileOutputStream(destination); Mp3Writer writer = new Mp3Writer(output)) {
                OfflineAudioRenderer.append(source, request.backend == SpeechBackend.OPENAI ? 1 : request.speed, writer, check);
                if (writer.frames() == 0) { throw new java.io.IOException("Empty sentence audio"); }
            }
        }, check);
    }
}
