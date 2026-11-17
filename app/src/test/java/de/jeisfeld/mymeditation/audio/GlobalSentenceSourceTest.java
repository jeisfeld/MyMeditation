package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import static org.junit.Assert.*;

@Config(shadows = GlobalSentenceSourceTest.Source.class)
public class GlobalSentenceSourceTest extends BaselineTest {
    @Implements(value = CloudSpeech.class, isInAndroidSdk = false)
    public static class Source {
        static File original;
        static int calls;
        @Implementation public File audioWithPersistentCache(File cache, File durable, SpeechBackend backend,
                String model, String voice, String text, double speed, String style) { calls++; return original; }
    }
    @Before public void source() throws Exception {
        Source.calls = 0; Source.original = new File(Application.getAppContext().getCacheDir(), "original-source.mp3");
        try (FileOutputStream output = new FileOutputStream(Source.original); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
    }
    private PlaybackRequest request(String text, double speed) {
        return new PlaybackRequest(text, SpeechBackend.ELEVENLABS, "eleven_multilingual_v2", "voice-test", speed, "Ignored", 0);
    }
    private File obtain(PlaybackRequest request, int index) throws Exception {
        return SentenceAudio.obtain(Application.getAppContext(), request, index, new CloudSpeech(), () -> { });
    }
    @Test public void originalSourceIsStoredOnceAndLocalSpeedsNeverRenderOrRegenerateIt() throws Exception {
        PlaybackRequest first = request("Breathe.", 0.7);
        File source = obtain(first, 0);
        assertArrayEquals(Files.readAllBytes(Source.original.toPath()), Files.readAllBytes(source.toPath()));
        assertEquals(source, obtain(first.withSpeed(1.8), 0)); assertEquals(1, Source.calls);
        assertTrue(SentenceAudio.store(Application.getAppContext()).open(first.withSpeed(4)).isComplete());
    }
    @Test public void changedScriptOnlyGeneratesMissingSentencesAndReorderedScriptsAreImmediatelyOffline() throws Exception {
        PlaybackRequest first = request("Breathe.Relax.", 1.25); obtain(first, 0); obtain(first, 1);
        PlaybackRequest reordered = request("Relax.[wait 12s]Breathe.Relax.", 0.8);
        assertTrue(SentenceAudio.store(Application.getAppContext()).open(reordered).isComplete()); assertEquals(2, Source.calls);
        PlaybackRequest changed = request("Relax.Notice.Breathe.", 1);
        assertEquals(java.util.Arrays.asList(1), SentenceAudio.store(Application.getAppContext()).open(changed).missingSpeech());
        obtain(changed, 0); obtain(changed, 1); obtain(changed, 2); assertEquals(3, Source.calls);
        assertTrue(SentenceAudio.store(Application.getAppContext()).open(changed).isComplete());
    }
    @Test public void invalidatingASharedSourceMakesEveryDependentScriptUnavailableUntilReplaced() throws Exception {
        PlaybackRequest first = request("Breathe.", 1), second = request("Breathe.Breathe.", 1.4);
        obtain(first, 0); assertTrue(SentenceAudio.store(Application.getAppContext()).open(second).isComplete());
        SentenceAudio.invalidate(Application.getAppContext(), first, 0);
        assertFalse(SentenceAudio.store(Application.getAppContext()).open(second).isComplete());
        obtain(second, 1); assertEquals(2, Source.calls);
        assertTrue(SentenceAudio.store(Application.getAppContext()).open(first).isComplete());
    }
}
