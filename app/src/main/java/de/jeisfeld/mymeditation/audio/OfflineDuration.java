package de.jeisfeld.mymeditation.audio;

import android.media.MediaMetadataRetriever;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** Duration of source audio at playback speed, plus wall-clock gaps and explicit waits. */
public final class OfflineDuration {
    public static final String LOG_TAG = "MeditationDuration";
    interface Reader { long milliseconds(File file) throws Exception; }
    private final Reader reader;
    private final java.util.function.Consumer<String> logger;
    private final Map<String, Long> recentMessages = new LinkedHashMap<String, Long>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) { return size() > 128; }
    };
    private String metadataFailure = "";
    private final Map<String, Long> cache = new LinkedHashMap<String, Long>(128, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Long> entry) { return size() > 2048; }
    };
    public OfflineDuration() { this(OfflineDuration::read, message -> android.util.Log.i(LOG_TAG, message)); }
    OfflineDuration(Reader reader) { this(reader, message -> { }); }
    OfflineDuration(Reader reader, java.util.function.Consumer<String> logger) { this.reader = reader; this.logger = logger; }

    /** Rate-limited INFO diagnostics contain counts/settings and identifiers, never text or exception messages. */
    public synchronized void diagnostic(String message) {
        long now = System.nanoTime() / 1000000;
        Long previous = recentMessages.get(message);
        if (previous == null || now - previous >= 30000) { recentMessages.put(message, now); logger.accept(message); }
    }
    public static String diagnosticContext(PlaybackRequest request, double pause) {
        return "script=" + SentenceAudioStore.key(request).substring(0, 12) + " provider=" + request.backend.id
                + " model=" + request.model + " voiceHash=" + Integer.toHexString(request.voice.hashCode())
                + " speed=" + request.speed + " pause=" + pause;
    }

    /** Call off the UI thread. Unknown metadata leaves the existing offline label available. */
    public Long milliseconds(SentenceAudioStore.Book book, double pause) {
        String context = diagnosticContext(book.request, pause) + " mode=offline";
        double total = 0;
        for (int i = SpeechSegments.nextSpeech(book.sentences, 0); i < book.sentences.size(); i++) {
            if (Thread.currentThread().isInterrupted()) { return null; }
            total += MeditationPauses.gapBefore(book.sentences, i, pause) * 1000;
            double wait = MeditationPauses.seconds(book.sentences.get(i));
            if (wait >= 0) { total += MeditationPauses.scaledWait(wait, pause) * 1000; continue; }
            File file = book.available(i);
            if (file == null) {
                diagnostic(context + " outcome=unavailable reason=missing_audio segment=" + (i + 1)); return null;
            }
            Long duration = duration(file);
            if (duration == null) {
                diagnostic(context + " outcome=unavailable reason=duration_metadata segment=" + (i + 1)
                        + " clip=" + file.getName().substring(0, 12) + " problem=" + metadataFailure); return null;
            }
            total += duration / (book.request.localSpeed() ? book.request.speed : 1);
        }
        diagnostic(context + " outcome=ready milliseconds=" + Math.round(total));
        return Math.round(total);
    }

    private Long duration(File file) {
        metadataFailure = "";
        String key = file.getAbsolutePath() + ":" + file.length() + ":" + file.lastModified();
        Long cached = cache.get(key);
        if (cached != null) { return cached; }
        try {
            long measured = reader.milliseconds(file);
            if (measured > 0) { cache.put(key, measured); return measured; }
            metadataFailure = "nonpositive_duration";
        } catch (Exception error) { metadataFailure = error.getClass().getSimpleName(); }
        return null;
    }

    /** Estimate missing speech only after enough distinct cached samples from this model and voice. */
    public Long estimate(SentenceAudioStore store, SentenceAudioStore.Book target, double pause) {
        String context = diagnosticContext(target.request, pause) + " mode=estimate";
        Samples samples = new Samples();
        samples.add(target);
        int targetSamples = samples.sentences.size();
        int scanned = 0, matched = 0, invalid = 0, otherProvider = 0, otherModel = 0, otherVoice = 0;
        java.util.Set<String> otherProfiles = new java.util.LinkedHashSet<>();
        java.util.List<String> identities = store.identities();
        for (String identity : identities) {
            if (Thread.currentThread().isInterrupted()) { return null; }
            if (samples.sentences.size() >= 40) { break; }
            scanned++;
            SentenceAudioStore.Book candidate = store.find(identity);
            if (candidate == null) { invalid++; continue; }
            boolean match;
            if (candidate.request.backend != target.request.backend) { otherProvider++; match = false; }
            else if (!candidate.request.model.equals(target.request.model)) { otherModel++; match = false; }
            else if (!candidate.request.voice.equals(target.request.voice)) { otherVoice++; match = false; }
            else { match = true; }
            if (match) { matched++; samples.add(candidate); }
            else if (otherProfiles.size() < 3) {
                otherProfiles.add(candidate.request.backend.id + "/" + candidate.request.model + "/voiceHash="
                        + Integer.toHexString(candidate.request.voice.hashCode()));
            }
        }
        String stats = " manifests=" + identities.size() + " scanned=" + scanned + " matched=" + matched
                + " invalid=" + invalid + " otherProvider=" + otherProvider + " otherModel=" + otherModel + " otherVoice=" + otherVoice
                + " targetSamples=" + targetSamples + " samples=" + samples.sentences.size() + " letters=" + samples.letters
                + " speechMs=" + Math.round(samples.milliseconds) + " missingClips=" + samples.missing
                + " unreadableClips=" + samples.unreadable + " metadataProblem=" + samples.firstMetadataFailure
                + " duplicatePassages=" + samples.duplicates + " symbolOnlyPassages=" + samples.symbolOnly
                + " otherProfiles=" + otherProfiles;
        if (samples.sentences.size() < 3 || samples.letters < 300 || samples.milliseconds < 20000) {
            diagnostic(context + " outcome=unavailable reason=insufficient_samples requiredSamples=3 requiredLetters=300 requiredSpeechMs=20000" + stats);
            return null;
        }
        double perLetter = samples.milliseconds / samples.letters;
        double total = 0;
        int measuredSegments = 0, estimatedSegments = 0, ignoredPunctuation = 0;
        for (int i = SpeechSegments.nextSpeech(target.sentences, 0); i < target.sentences.size(); i++) {
            if (Thread.currentThread().isInterrupted()) { return null; }
            total += MeditationPauses.gapBefore(target.sentences, i, pause) * 1000;
            String sentence = target.sentences.get(i);
            double wait = MeditationPauses.seconds(sentence);
            if (wait >= 0) { total += MeditationPauses.scaledWait(wait, pause) * 1000; continue; }
            File file = target.available(i);
            Long measured = file == null ? null : duration(file);
            if (measured != null) { measuredSegments++; total += measured / (target.request.localSpeed() ? target.request.speed : 1); }
            else {
                long letters = letters(sentence);
                if (letters == 0) {
                    // Closing quotes may be separate segments after sentence punctuation. They have no spoken letters.
                    if (punctuationOnly(sentence)) { ignoredPunctuation++; continue; }
                    diagnostic(context + " outcome=unavailable reason=segment_without_letters segment=" + (i + 1)
                            + " codePoints=" + sentence.codePointCount(0, sentence.length()) + stats);
                    return null;
                }
                estimatedSegments++;
                total += perLetter * letters / target.request.speed;
            }
        }
        diagnostic(context + " outcome=ready milliseconds=" + Math.round(total) + " measuredSegments=" + measuredSegments
                + " estimatedSegments=" + estimatedSegments + " ignoredPunctuation=" + ignoredPunctuation + stats);
        return Math.round(total);
    }

    private static boolean punctuationOnly(String text) {
        return text.codePoints().allMatch(value -> {
            if (Character.isWhitespace(value)) { return true; }
            switch (Character.getType(value)) {
                case Character.CONNECTOR_PUNCTUATION:
                case Character.DASH_PUNCTUATION:
                case Character.START_PUNCTUATION:
                case Character.END_PUNCTUATION:
                case Character.INITIAL_QUOTE_PUNCTUATION:
                case Character.FINAL_QUOTE_PUNCTUATION:
                case Character.OTHER_PUNCTUATION: return true;
                default: return false;
            }
        });
    }

    private static long letters(String text) {
        return text.codePoints().filter(Character::isLetterOrDigit).count();
    }
    private final class Samples {
        final java.util.Set<String> sentences = new java.util.HashSet<>();
        long letters;
        double milliseconds;
        int missing, unreadable, duplicates, symbolOnly;
        String firstMetadataFailure = "none";
        void add(SentenceAudioStore.Book book) {
            for (int i = 0; i < book.sentences.size() && sentences.size() < 40; i++) {
                if (Thread.currentThread().isInterrupted()) { return; }
                String text = book.sentences.get(i);
                if (MeditationPauses.seconds(text) >= 0) { continue; }
                if (sentences.contains(text)) { duplicates++; continue; }
                long count = letters(text);
                if (count == 0) { symbolOnly++; continue; }
                File file = book.available(i);
                if (file == null) { missing++; continue; }
                Long measured = duration(file);
                if (measured == null) {
                    unreadable++;
                    if ("none".equals(firstMetadataFailure)) { firstMetadataFailure = metadataFailure; }
                    continue;
                }
                sentences.add(text); letters += count;
                // Native clips are original-speed sources; OpenAI applies speed on the server.
                milliseconds += measured * (book.request.localSpeed() ? 1 : book.request.speed);
            }
        }
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
