package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class EstimatedDurationTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private final Map<File, Long> lengths = new HashMap<>();
    private final OfflineDuration duration = new OfflineDuration(file -> lengths.get(file));
    private SentenceAudioStore store() { return new SentenceAudioStore(files.getRoot()); }
    private static String sentence(String letter, int count) {
        return String.join("", java.util.Collections.nCopies(count, letter)) + ".";
    }
    private PlaybackRequest request(String text, SpeechBackend backend, double speed) {
        return new PlaybackRequest(text, backend, backend.defaultModel, backend.defaultVoice, speed, "", 0);
    }
    private SentenceAudioStore.Book source(PlaybackRequest request, int index, long milliseconds) throws Exception {
        SentenceAudioStore.Book book = store().open(request);
        File file = book.obtain(index, path -> Files.write(path.toPath(), new byte[]{'I', 'D', '3', 0}), () -> { });
        lengths.put(file, milliseconds);
        return book;
    }
    private void calibrate(SpeechBackend backend, double speed, long milliseconds) throws Exception {
        PlaybackRequest request = request(sentence("A", 100) + sentence("B", 100) + sentence("C", 100), backend, speed);
        for (int i = 0; i < 3; i++) { source(request, i, milliseconds); }
    }
    @Test public void missingAudioUsesVoiceAverageAndCachedPartsKeepTheirMeasuredDuration() throws Exception {
        calibrate(SpeechBackend.GEMINI, 4, 10000); // Native source audio remains at original speed.
        PlaybackRequest request = request("[wait 20s]" + sentence("D", 100) + "[wait 10s]"
                + sentence("E", 100) + sentence("F", 50), SpeechBackend.GEMINI, 2);
        SentenceAudioStore.Book target = source(request, 3, 20000);
        assertFalse(target.isComplete());
        // Four distinct samples: 50s / 400 letters. Known E stays 20s, not an estimate.
        // Speech: (12.5 + 20 + 6.25) / 2. Silence: (10 + 1) * .5 + .5.
        assertEquals(Long.valueOf(25375), duration.estimate(store(), target, 0.5));
        assertEquals(Long.valueOf(19375), duration.estimate(store(), target, 0));
    }
    @Test public void serverSpeedIsNormalizedForSamplesAndAppliedToMissingSpeechOnlyOnce() throws Exception {
        calibrate(SpeechBackend.OPENAI, 2, 5000);
        SentenceAudioStore.Book target = store().open(request(sentence("D", 100) + "[wait 10s]"
                + sentence("E", 100), SpeechBackend.OPENAI, 0.5));
        assertEquals(Long.valueOf(51000), duration.estimate(store(), target, 1));
    }
    @Test public void samplesFromOtherVoicesModelsOrProvidersCannotCalibrateThisVoice() throws Exception {
        calibrate(SpeechBackend.GEMINI, 1, 10000);
        PlaybackRequest request = request(sentence("D", 100), SpeechBackend.GEMINI, 1);
        assertNull(duration.estimate(store(), store().open(new PlaybackRequest(request.text, request.backend,
                request.model, "Puck", 1, "", 0)), 1));
        assertNull(duration.estimate(store(), store().open(new PlaybackRequest(request.text, request.backend,
                "gemini-3.8-flash-lite-tts", request.voice, 1, "", 0)), 1));
        assertNull(duration.estimate(store(), store().open(request(request.text, SpeechBackend.OPENAI, 1)), 1));
    }
    @Test public void repeatedSentencesAcrossPartialManifestsCountOnlyOnce() throws Exception {
        String a = sentence("A", 100), b = sentence("B", 100), c = sentence("C", 100);
        PlaybackRequest first = request(a + a + a + sentence("Z", 10), SpeechBackend.GEMINI, 1);
        source(first, 0, 10000);
        PlaybackRequest second = request(a + b + c, SpeechBackend.GEMINI, 1);
        source(second, 0, 10000);
        SentenceAudioStore.Book target = store().open(request(sentence("D", 100), SpeechBackend.GEMINI, 1));
        assertNull(duration.estimate(store(), target, 1));
        source(second, 1, 10000); source(second, 2, 10000);
        assertEquals(Long.valueOf(10000), duration.estimate(store(), target, 1));
    }
    @Test public void calibrationRequiresEnoughLettersAndSpeechAndDisappearsAfterAudioDeletion() throws Exception {
        calibrate(SpeechBackend.GEMINI, 1, 5000);
        SentenceAudioStore.Book target = store().open(request(sentence("D", 100), SpeechBackend.GEMINI, 1));
        assertNull(duration.estimate(store(), target, 1)); // Only 15 seconds of speech.
        lengths.replaceAll((file, value) -> 10000L);
        // Fresh metadata reader; the source audio itself has not changed in this fixture.
        OfflineDuration fresh = new OfflineDuration(file -> lengths.get(file));
        assertEquals(Long.valueOf(10000), fresh.estimate(store(), target, 1));
        SentenceAudioStore.Book sample = store().open(request(sentence("A", 100), SpeechBackend.GEMINI, 1));
        sample.invalidate(0);
        assertNull(fresh.estimate(store(), target, 1));
        for (File file : lengths.keySet()) { file.delete(); }
        PlaybackRequest shortSamples = request("One.Two.Three.", SpeechBackend.GEMINI, 1);
        for (int i = 0; i < 3; i++) { source(shortSamples, i, 10000); }
        assertNull(fresh.estimate(store(), target, 1)); // Enough time, too few letters.
    }
    @Test public void unicodeLettersUseCodePointsAndUnreadableMetadataIsIgnored() throws Exception {
        String letter = new String(Character.toChars(0x10400));
        PlaybackRequest samples = request(sentence(letter, 100) + sentence("呼", 100) + sentence("ü", 100), SpeechBackend.GEMINI, 1);
        for (int i = 0; i < 3; i++) { source(samples, i, 10000); }
        SentenceAudioStore.Book target = store().open(request(sentence("吸", 100), SpeechBackend.GEMINI, 1));
        assertEquals(Long.valueOf(10000), duration.estimate(store(), target, 1));
        OfflineDuration unreadable = new OfflineDuration(file -> { throw new java.io.IOException(); });
        assertNull(unreadable.estimate(store(), target, 1));
    }
    @Test public void germanAndOtherClosingQuotesDoNotSuppressEstimatesOrChangeSpeechSegments() throws Exception {
        calibrate(SpeechBackend.GEMINI, 1, 10000);
        for (String text : new String[]{"„Dies ist ein Zitat.“", "»Dies ist ein Zitat.«", "“Dies ist ein Zitat.”", "\"Dies ist ein Zitat.\""}) {
            SentenceAudioStore.Book target = store().open(request(text, SpeechBackend.GEMINI, 1));
            assertEquals(2, target.sentences.size());
            assertEquals(Long.valueOf(2500), duration.estimate(store(), target, 1));
            assertEquals(Long.valueOf(2000), duration.estimate(store(), target, 0.5));
            assertFalse(target.isComplete());
        }
    }
    @Test public void diagnosticsExplainFailuresDeduplicateAndExcludePrivateTextAndVoiceId() {
        java.util.List<String> logs = new java.util.ArrayList<>();
        OfflineDuration traced = new OfflineDuration(file -> 10000, logs::add);
        String privateText = "Private meditation topic that must stay off Logcat.";
        String privateVoice = "PrivateVoice123456";
        PlaybackRequest request = new PlaybackRequest(privateText, SpeechBackend.ELEVENLABS,
                "eleven_multilingual_v2", privateVoice, 1.25, "", 1);
        SentenceAudioStore.Book target = store().open(request);
        assertNull(traced.estimate(store(), target, 1));
        assertNull(traced.estimate(store(), target, 1));
        assertEquals(1, logs.size());
        String log = logs.get(0);
        assertTrue(log.contains("reason=insufficient_samples"));
        assertTrue(log.contains("requiredSamples=3")); assertTrue(log.contains("samples=0"));
        assertTrue(log.contains("voiceHash=")); assertTrue(log.contains("model=eleven_multilingual_v2"));
        assertFalse(log.contains(privateText)); assertFalse(log.contains(privateVoice));
    }
    @Test public void diagnosticsIdentifyUnreadableMetadataAndUnknownSymbolSegmentsWithoutExceptionMessages() throws Exception {
        calibrate(SpeechBackend.GEMINI, 1, 10000);
        java.util.List<String> logs = new java.util.ArrayList<>();
        OfflineDuration traced = new OfflineDuration(file -> lengths.get(file), logs::add);
        SentenceAudioStore.Book symbols = store().open(request("One.😀", SpeechBackend.GEMINI, 1));
        assertNull(traced.estimate(store(), symbols, 1));
        assertTrue(logs.get(0).contains("reason=segment_without_letters segment=2"));
        assertFalse(logs.get(0).contains("😀"));
        OfflineDuration unreadable = new OfflineDuration(file -> { throw new IllegalStateException("private exception message"); }, logs::add);
        assertNull(unreadable.estimate(store(), symbols, 1));
        String last = logs.get(logs.size() - 1);
        assertTrue(last.contains("unreadableClips=3"));
        assertTrue(last.contains("metadataProblem=IllegalStateException"));
        assertFalse(last.contains("private exception message"));
    }
    @Test public void diagnosticsIdentifyProfileMismatchesAndReportPunctuationHandling() throws Exception {
        calibrate(SpeechBackend.GEMINI, 1, 10000);
        java.util.List<String> logs = new java.util.ArrayList<>();
        OfflineDuration traced = new OfflineDuration(file -> lengths.get(file), logs::add);
        PlaybackRequest profile = request("One.", SpeechBackend.GEMINI, 1);
        SentenceAudioStore.Book wrongVoice = store().open(new PlaybackRequest(profile.text, profile.backend,
                profile.model, "Puck", 1, "", 1));
        assertNull(traced.estimate(store(), wrongVoice, 1));
        assertTrue(logs.get(0).contains("otherVoice=1"));
        assertTrue(logs.get(0).contains("matched=0"));
        assertEquals(Long.valueOf(2500), traced.estimate(store(), store().open(request("„Dies ist ein Zitat.“", SpeechBackend.GEMINI, 1)), 1));
        assertTrue(logs.get(logs.size() - 1).contains("outcome=ready"));
        assertTrue(logs.get(logs.size() - 1).contains("ignoredPunctuation=1"));
    }

}
