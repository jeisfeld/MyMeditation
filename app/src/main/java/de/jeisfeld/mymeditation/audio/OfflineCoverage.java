package de.jeisfeld.mymeditation.audio;

import java.io.File;
import java.text.BreakIterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Spoken-word-weighted coverage; waits have no weight and repeated sentences count on each occurrence. */
public final class OfflineCoverage {
    public final long availableWords, totalWords;
    public final boolean complete;
    public final int perMille;
    private OfflineCoverage(long available, long total, boolean complete) {
        availableWords = available; totalWords = total; this.complete = complete;
        perMille = complete ? 1000 : total == 0 ? 0 : (int) Math.min(999, available * 1000 / total);
    }
    public static OfflineCoverage measure(SentenceAudioStore.Book book) { return new Counter(book).snapshot(); }
    static long words(String text) {
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ROOT); iterator.setText(text);
        long count = 0; int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            for (int i = start; i < end;) {
                int value = text.codePointAt(i);
                if (Character.isLetterOrDigit(value)) { count++; break; }
                i += Character.charCount(value);
            }
        }
        return count;
    }
    static final class Counter {
        private final SentenceAudioStore.Book book;
        private final Map<File, Long> weights = new HashMap<>();
        private final Set<File> available = new HashSet<>();
        private long total, loaded;
        Counter(SentenceAudioStore.Book book) {
            this.book = book;
            for (int i = 0; i < book.sentences.size(); i++) {
                String sentence = book.sentences.get(i);
                if (MeditationPauses.seconds(sentence) >= 0) { continue; }
                long count = words(sentence); total += count;
                weights.merge(book.audio(i), count, Long::sum);
            }
            for (int i = 0; i < book.sentences.size(); i++) {
                if (MeditationPauses.seconds(book.sentences.get(i)) < 0 && book.available(i) != null) { loaded(i); }
            }
        }
        void loaded(int index) { File file = book.audio(index); if (available.add(file)) { loaded += weights.get(file); } }
        OfflineCoverage snapshot() { return new OfflineCoverage(loaded, total, available.size() == weights.size()); }
    }
}
