package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SentenceAudioStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private PlaybackRequest request(String text, double speed, String voice, String style, double pause) {
        return new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-3.8-flash-tts", voice, speed, style, pause);
    }
    private PlaybackRequest request() { return request("Breathe. Relax.", 1, "Kore", "Calm", 2); }
    private void mp3(File file) throws Exception {
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) {
            writer.silence(0.1, () -> { });
        }
    }
    @Test public void skippedTimedPausesNeedNoAudioAndDoNotPreventAutomaticRetention() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        SentenceAudioStore.Book book = store.open(request("[wait 1s]Breathe.[wait 18s]Rest.[wait 2s]", 1, "Kore", "", 2));
        assertFalse(book.played(0)); // A leading silence can finish before any audio is generated.
        book.obtain(1, this::mp3, () -> { }); book.obtain(3, this::mp3, () -> { });
        assertTrue(book.played(1)); assertTrue(book.played(3));
        assertTrue(book.isComplete());
        assertFalse(book.audio(0).exists()); assertFalse(book.audio(2).exists()); assertFalse(book.audio(4).exists());
        assertTrue(store.find(book.identity).isComplete());
        assertEquals(book.identity, SentenceAudioStore.key(book.request.withPause(30)));
        book.invalidate(1); assertFalse(book.isComplete());
    }
    @Test public void oldGeneratedCachesRecoverWithoutPlaybackMarkersOrRegeneratingAudio() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        SentenceAudioStore.Book book = store.open(request("[wait 5s]Breathe.[wait 5s]Relax.[wait 5s]", 1, "Kore", "", 0));
        book.obtain(1, this::mp3, () -> { }); assertFalse(book.isComplete());
        book.obtain(3, this::mp3, () -> { });
        assertFalse(book.isPublished());
        assertTrue(store.find(book.identity).isComplete()); assertTrue(book.isPublished());
        for (int i : new int[] {0, 2, 4}) { assertFalse(book.audio(i).exists()); }
        assertFalse(new File(book.directory(), "played-1").exists());
        book.invalidate(3); assertFalse(book.isComplete());
    }
    @Test public void pauseDoesNotInvalidateAudioButEverySynthesisSettingDoes() {
        PlaybackRequest original = request(); String key = SentenceAudioStore.key(original);
        assertEquals(key, SentenceAudioStore.key(original.withSpeed(1.5)));
        assertEquals(key, SentenceAudioStore.key(original.withPause(30)));
        for (PlaybackRequest changed : new PlaybackRequest[] {
                request("Breathe. Relax!", 1, "Kore", "Calm", 2),
                request("Breathe. Relax.", 1, "Sulafat", "Calm", 2),
                request("Breathe. Relax.", 1, "Kore", "Commanding", 2),
                new PlaybackRequest(original.text, SpeechBackend.GEMINI, "gemini-2.5-flash-preview-tts", "Kore", 1, "Calm", 2),
                new PlaybackRequest(original.text, SpeechBackend.OPENAI, "gpt-4o-mini-tts", "coral", 1, "Calm", 2)}) {
            assertNotEquals(key, SentenceAudioStore.key(changed));
        }
    }
    @Test public void generatedAudioIsOfflineWithoutPlaybackAndReuseNeverCallsProducer() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        SentenceAudioStore.Book book = store.open(request());
        book.obtain(0, this::mp3, () -> { }); book.obtain(1, this::mp3, () -> { });
        assertTrue(book.isComplete());
        assertTrue(book.played(0)); assertTrue(book.played(1)); assertTrue(book.isComplete());
        SentenceAudioStore.Book reopened = store.find(book.identity);
        assertNotNull(reopened); assertTrue(reopened.isComplete());
        File audio = reopened.obtain(0, file -> fail("Offline replay must not contact a provider"), () -> { });
        assertEquals(book.audio(0), audio);
    }
    @Test public void differentMeditationsReuseGlobalSentencesRegardlessOfOrderWaitsOrLocalSpeed() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        SentenceAudioStore.Book first = store.open(request());
        first.obtain(0, this::mp3, () -> { }); first.obtain(1, this::mp3, () -> { });
        SentenceAudioStore.Book second = store.open(request("Relax.[wait 10s]Breathe.Relax.", 1.7, "Kore", "Calm", 5));
        assertTrue(second.isComplete());
        assertEquals(first.audio(1), second.audio(0)); assertEquals(first.audio(0), second.audio(2));
        assertEquals(second.audio(0), second.audio(3));
        second.obtain(2, file -> fail("Stored source must not be generated again"), () -> { });
        assertEquals(2, new File(temporary.getRoot(), "clips").listFiles().length);
        SentenceAudioStore.Book changed = store.open(request("Relax. Different.", 1, "Kore", "Calm", 0));
        assertEquals(java.util.Arrays.asList(1), changed.missingSpeech());
        assertNull(store.open(request("Breathe.", 1, "Sulafat", "Calm", 0)).available(0));
    }
    @Test public void serverSideSpeedRemainsPartOfOpenAiSentenceIdentity() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        PlaybackRequest request = new PlaybackRequest("Breathe.", SpeechBackend.OPENAI, "gpt-4o-mini-tts", "coral", 1, "Calm", 0);
        SentenceAudioStore.Book first = store.open(request); first.obtain(0, this::mp3, () -> { });
        assertFalse(store.open(request.withSpeed(1.5)).isComplete());
        assertNotEquals(first.audio(0), store.open(request.withSpeed(1.5)).audio(0));
        assertEquals(first.audio(0), store.open(new PlaybackRequest("Relax.Breathe.", request.backend, request.model,
                request.voice, 1, "Calm", 0)).audio(1));
    }
    @Test public void cancellationAndFailedRenderingNeverPublishPartialAudio() throws Exception {
        SentenceAudioStore.Book book = new SentenceAudioStore(temporary.getRoot()).open(request());
        AtomicInteger checks = new AtomicInteger();
        assertThrows(CancellationException.class, () -> book.obtain(0, this::mp3, () -> {
            if (checks.incrementAndGet() == 2) { throw new CancellationException(); }
        }));
        assertNull(book.available(0)); assertFalse(book.isComplete());
        assertThrows(java.io.IOException.class, () -> book.obtain(0, file -> Files.write(file.toPath(), new byte[0]), () -> { }));
        assertThrows(java.io.IOException.class, book::finish);
        assertNull(book.available(0));
    }
    @Test public void interruptedPlaybackCanResumeLaterWithoutMixingSpeeds() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        SentenceAudioStore.Book original = store.open(request());
        original.obtain(0, this::mp3, () -> { }); assertFalse(original.played(0));
        SentenceAudioStore.Book changed = store.open(request("Breathe. Relax.", 1.2, "Kore", "Calm", 2));
        changed.obtain(1, this::mp3, () -> { }); assertTrue(changed.played(1));
        assertTrue(original.isComplete()); assertTrue(changed.isComplete());
        SentenceAudioStore.Book resumed = store.find(original.identity);
        resumed.obtain(1, this::mp3, () -> { }); assertTrue(resumed.played(1));
        assertTrue(resumed.isComplete()); assertTrue(changed.isComplete());
    }
    @Test public void explicitSaveCanCompleteWithoutPlaybackAndMissingFilesInvalidateCompletion() throws Exception {
        SentenceAudioStore.Book book = new SentenceAudioStore(temporary.getRoot()).open(request());
        book.obtain(0, this::mp3, () -> { }); book.obtain(1, this::mp3, () -> { });
        book.finish(); assertTrue(book.isComplete());
        assertTrue(book.audio(0).delete()); assertFalse(book.isComplete());
        book.obtain(0, this::mp3, () -> { }); book.finish(); assertTrue(book.isComplete());
    }
    @Test public void invalidMetadataAndPathTraversalAreUnavailableAndMetadataCanBeRepaired() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        SentenceAudioStore.Book book = store.open(request());
        book.obtain(0, this::mp3, () -> { }); book.obtain(1, this::mp3, () -> { }); book.finish();
        File manifest = new File(book.directory(), "manifest.json");
        Files.write(manifest.toPath(), "bad json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertNull(store.find(book.identity)); assertNull(store.find("../outside"));
        book.finish(); assertNotNull(store.find(book.identity));
        assertThrows(IllegalArgumentException.class, () -> book.audio(-1));
        assertThrows(IllegalArgumentException.class, () -> book.audio(2));
    }
    @Test public void simultaneousPlaybackAndSaveGenerateASentenceOnlyOnce() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(temporary.getRoot());
        AtomicInteger generated = new AtomicInteger();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch producing = new CountDownLatch(1), release = new CountDownLatch(1);
        SentenceAudioStore.Producer producer = file -> {
            generated.incrementAndGet(); producing.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS)); mp3(file);
        };
        try {
            Future<File> first = workers.submit(() -> store.open(request()).obtain(0, producer, () -> { }));
            assertTrue(producing.await(5, TimeUnit.SECONDS));
            Future<File> second = workers.submit(() -> store.open(request()).obtain(0, producer, () -> { }));
            release.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, generated.get());
        } finally { release.countDown(); workers.shutdownNow(); }
    }
}
