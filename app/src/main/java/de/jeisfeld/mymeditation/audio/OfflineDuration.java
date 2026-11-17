package de.jeisfeld.mymeditation.audio;

import android.media.MediaMetadataRetriever;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Duration of source audio at playback speed, plus wall-clock gaps and explicit waits. */
public final class OfflineDuration {
    interface Reader { long milliseconds(File file) throws Exception; }
    private final Reader reader;
    private final Map<String, Long> cache = new LinkedHashMap<String, Long>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) { return size() > 2048; }
    };
    public OfflineDuration() { this(OfflineDuration::read); }
    OfflineDuration(Reader reader) { this.reader = reader; }

    /** Call off the UI thread. Unknown metadata leaves the existing offline label available. */
    public Long milliseconds(SentenceAudioStore.Book book, double pause) {
        double total = 0;
        for (int i = SpeechSegments.nextSpeech(book.sentences, 0); i < book.sentences.size(); i++) {
            if (Thread.currentThread().isInterrupted()) { return null; }
            total += MeditationPauses.gapBefore(book.sentences, i, pause) * 1000;
            double wait = MeditationPauses.seconds(book.sentences.get(i));
            if (wait >= 0) { total += MeditationPauses.scaledWait(wait, pause) * 1000; continue; }
            File file = book.available(i);
            if (file == null) { return null; }
            String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
            Long duration = cache.get(key);
            if (duration == null) {
                try { duration = reader.milliseconds(file); }
                catch (Exception e) { return null; }
                if (duration <= 0) { return null; }
                cache.put(key, duration);
            }
            total += duration / (book.request.localSpeed() ? book.request.speed : 1);
        }
        return Math.round(total);
    }

    private static long read(File file) throws Exception {
        MediaMetadataRetriever metadata = new MediaMetadataRetriever();
        try {
            metadata.setDataSource(file.getAbsolutePath());
            return Long.parseLong(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally { metadata.release(); }
    }

    public static String format(long milliseconds) {
        long seconds = Math.round(milliseconds / 1000.0);
        return String.format(java.util.Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
