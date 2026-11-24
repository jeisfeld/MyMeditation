package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.util.List;

/** Audible wall-clock timeline: measured clips where possible, estimated speech otherwise; no network waiting. */
final class MediaTimeline {
    enum Part { BUFFERING, SILENCE, READY, SPEECH }
    static final class Sample {
        final int index;
        final Part part;
        final long elapsed;
        final boolean advancing;
        Sample(int index, Part part, long elapsed, boolean advancing) {
            this.index = index; this.part = part; this.elapsed = elapsed; this.advancing = advancing;
        }
    }
    final long duration;
    final boolean estimated;
    private final long[] starts, gaps, speech, waits;
    private final List<String> sentences;
    private MediaTimeline(List<String> sentences, long[] gaps, long[] speech, long[] waits, boolean estimated) {
        this.estimated = estimated;
        this.sentences = sentences; this.gaps = gaps; this.speech = speech; this.waits = waits;
        starts = new long[speech.length];
        long total = 0;
        for (int i = 0; i < starts.length; i++) { starts[i] = total; total += gaps[i] + speech[i] + waits[i]; }
        duration = total;
    }
    static MediaTimeline create(PlaybackRequest request, SentenceAudioStore store, OfflineDuration metadata) {
        List<String> sentences = SpeechSegments.split(request.text, request.backend.segmentLimit);
        int size = sentences.size(), first = SpeechSegments.nextSpeech(sentences, 0);
        long[] gaps = new long[size], speech = new long[size], waits = new long[size];
        boolean[] missing = new boolean[size];
        long known = 0, silence = 0, knownLetters = 0, missingLetters = 0;
        SentenceAudioStore.Book book = request.backend == SpeechBackend.DEVICE ? null : store.open(request);
        for (int i = first; i < size; i++) {
            gaps[i] = Math.round(MeditationPauses.gapBefore(sentences, i, request.pauseSeconds) * 1000);
            double wait = MeditationPauses.seconds(sentences.get(i));
            if (wait >= 0) { waits[i] = Math.round(MeditationPauses.scaledWait(wait, request.pauseSeconds) * 1000); }
            else {
                Long measured = book == null ? null : metadata.speechMilliseconds(book, i);
                long letters = letters(sentences.get(i));
                if (measured == null) { missing[i] = true; missingLetters += letters; }
                else { speech[i] = measured; known += measured; knownLetters += letters; }
            }
            silence += gaps[i] + waits[i];
        }
        if (missingLetters > 0) {
            Long estimated = book == null ? null : metadata.estimate(store, book, request.pauseSeconds);
            // Device speech and a voice with no samples still need an approximate media progress scale.
            double perLetter = estimated != null ? Math.max(0, estimated - silence - known) / (double) missingLetters
                    : knownLetters > 0 ? known / (double) knownLetters : 70.0 / request.speed;
            for (int i = first; i < size; i++) { if (missing[i]) { speech[i] = Math.round(letters(sentences.get(i)) * perLetter); } }
        }
        return new MediaTimeline(sentences, gaps, speech, waits, missingLetters > 0);
    }
    private static long letters(String text) { return text.codePoints().filter(Character::isLetterOrDigit).count(); }
    long position(Sample sample) {
        if (starts.length == 0) { return 0; }
        int index = Math.max(0, Math.min(sample.index, starts.length - 1));
        long within = 0;
        if (sample.part == Part.SILENCE) { within = Math.min(Math.max(0, sample.elapsed), gaps[index] + waits[index]); }
        else if (sample.part == Part.READY) { within = gaps[index]; }
        else if (sample.part == Part.SPEECH) { within = gaps[index] + Math.min(Math.max(0, sample.elapsed), speech[index]); }
        return Math.min(duration, starts[index] + within);
    }
    int sentenceAt(long position) {
        long target = Math.max(0, Math.min(position, Math.max(0, duration - 1)));
        int index = SpeechSegments.nextSpeech(sentences, 0);
        while (index + 1 < starts.length && starts[index + 1] <= target) { index++; }
        return SpeechSegments.nextSpeech(sentences, index);
    }
}
