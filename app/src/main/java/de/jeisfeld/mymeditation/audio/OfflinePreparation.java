package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import java.util.function.Consumer;

/** Silent preparation: only missing source clips are generated, never waits or a whole-file MP3. */
final class OfflinePreparation {
    private OfflinePreparation() { }
    static void prepare(Context context, PlaybackRequest request, CloudSpeech speech,
                        Consumer<OfflineCoverage> progress, Runnable check) throws Exception {
        SentenceAudioStore.Book book = SentenceAudio.store(context).open(request);
        OfflineCoverage.Counter counter = new OfflineCoverage.Counter(book); progress.accept(counter.snapshot());
        for (int i = 0; i < book.sentences.size(); i++) {
            check.run();
            if (MeditationPauses.seconds(book.sentences.get(i)) >= 0) { continue; }
            if (book.available(i) == null) { SentenceAudio.obtain(context, request, i, speech, check); }
            counter.loaded(i); progress.accept(counter.snapshot());
        }
        check.run(); book.finish();
    }
}
