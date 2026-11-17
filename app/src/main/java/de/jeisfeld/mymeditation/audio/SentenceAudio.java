package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.File;

/** Original source audio is shared globally; native-provider speed is applied during playback/export. */
public final class SentenceAudio {
    private SentenceAudio() { }
    public static SentenceAudioStore store(Context context) {
        return new SentenceAudioStore(new File(context.getFilesDir(), "sentence-audio"));
    }
    public static File obtain(Context context, PlaybackRequest request, int index, CloudSpeech speech, Runnable check) throws Exception {
        SentenceAudioStore.Book book = store(context).open(request);
        if (MeditationPauses.seconds(book.sentences.get(index)) >= 0) { throw new IllegalArgumentException("Pause commands have no speech audio"); }
        return book.obtain(index, destination -> {
            File source = CloudSpeech.cachedAudio(context, request.backend, request.model, request.voice,
                    book.sentences.get(index), request.speed, request.style);
            if (source == null || !SentenceAudioStore.validAudio(source)) {
                source = speech.audioWithPersistentCache(context.getCacheDir(), context.getNoBackupFilesDir(), request.backend,
                        request.model, request.voice, book.sentences.get(index), request.speed, request.style);
            }
            check.run();
            java.nio.file.Files.copy(source.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }, check);
    }
    static void invalidate(Context context, PlaybackRequest request, int index) {
        SentenceAudioStore.Book book = store(context).open(request);
        book.invalidate(index);
        try {
            // A failed decoder must not repeatedly copy the same bad provider cache into the pool.
            for (int i = 0; i < 3; i++) {
                File source = CloudSpeech.cachedAudio(context, request.backend, request.model, request.voice,
                        book.sentences.get(index), request.speed, request.style);
                if (source == null || !source.delete()) { break; }
            }
        } catch (Exception ignored) { /* Invalid audio stays unavailable even if an old cache cannot be removed. */ }
    }
}
