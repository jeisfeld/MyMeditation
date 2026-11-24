package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class MediaTimelineTest {
    @Rule public TemporaryFolder files = new TemporaryFolder();
    private PlaybackRequest request(SpeechBackend backend, double speed, double pause) {
        return new PlaybackRequest("[wait 20s]One.[wait 10s][wait 5s]Two.[wait 2s]", backend,
                backend.defaultModel, backend.defaultVoice, speed, "", pause);
    }
    private SentenceAudioStore recorded(PlaybackRequest request) throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(files.getRoot());
        SentenceAudioStore.Book book = store.open(request);
        for (int i : new int[] {1, 4}) { book.obtain(i, file -> Files.write(file.toPath(), new byte[]{'I','D','3',0}), () -> { }); }
        return store;
    }
    @Test public void nativeTimelineTracksSpeechAndScaledWaitGroupsAndExcludesLeadingWaits() throws Exception {
        PlaybackRequest request = request(SpeechBackend.GEMINI, 2, 0.5);
        MediaTimeline timeline = MediaTimeline.create(request, recorded(request), new OfflineDuration(file -> 1000));
        assertFalse(timeline.estimated); assertEquals(10500, timeline.duration);
        assertEquals(3500, timeline.position(new MediaTimeline.Sample(2, MediaTimeline.Part.SILENCE, 3000, true)));
        assertEquals(7000, timeline.position(new MediaTimeline.Sample(3, MediaTimeline.Part.SILENCE, 1000, true)));
        assertEquals(8500, timeline.position(new MediaTimeline.Sample(4, MediaTimeline.Part.BUFFERING, 999999, false)));
        assertEquals(8750, timeline.position(new MediaTimeline.Sample(4, MediaTimeline.Part.SPEECH, 250, true)));
        assertEquals(9000, timeline.position(new MediaTimeline.Sample(4, MediaTimeline.Part.SPEECH, 999999, true)));
        assertEquals(4, timeline.sentenceAt(4000)); // A scrub into a wait starts at the next spoken sentence.
        assertEquals(1, timeline.sentenceAt(0));
        assertEquals(6, timeline.sentenceAt(timeline.duration)); // Trailing silence has no next speech.
    }
    @Test public void openAiDurationsAlreadyContainSpeedAndPauseChangesAreAppliedOnce() throws Exception {
        PlaybackRequest request = request(SpeechBackend.OPENAI, 2, 2);
        SentenceAudioStore store = recorded(request);
        MediaTimeline timeline = MediaTimeline.create(request, store, new OfflineDuration(file -> 1000));
        assertEquals(23000, timeline.duration); // 2s speech + 17s waits + two 2s gaps.
        assertEquals(13000, timeline.position(new MediaTimeline.Sample(3, MediaTimeline.Part.SILENCE, 0, true)));
    }
    @Test public void missingSpeechUsesTheSameVoiceCalibrationAndKeepsMeasuredClips() throws Exception {
        SentenceAudioStore store = new SentenceAudioStore(files.getRoot());
        String a = "A".repeat(100) + ".", b = "B".repeat(100) + ".", c = "C".repeat(100) + ".";
        PlaybackRequest samples = new PlaybackRequest(a + b + c, SpeechBackend.GEMINI,
                SpeechBackend.GEMINI.defaultModel, "Kore", 1, "", 1);
        SentenceAudioStore.Book source = store.open(samples);
        for (int i = 0; i < 3; i++) { source.obtain(i, file -> Files.write(file.toPath(), new byte[]{'I','D','3',0}), () -> { }); }
        PlaybackRequest target = new PlaybackRequest("D".repeat(100) + ".[wait 10s]" + a, SpeechBackend.GEMINI,
                SpeechBackend.GEMINI.defaultModel, "Kore", 2, "", 1);
        MediaTimeline timeline = MediaTimeline.create(target, store, new OfflineDuration(file -> 10000));
        assertTrue(timeline.estimated);
        assertEquals(21000, timeline.duration);
        assertEquals(17000, timeline.position(new MediaTimeline.Sample(2, MediaTimeline.Part.SPEECH, 1000, true)));
    }

    @Test public void unavailableAudioStillHasAnEstimatedProgressScaleWithSpeedAndPauseSemantics() {
        PlaybackRequest request = request(SpeechBackend.DEVICE, 1, 0);
        SentenceAudioStore store = new SentenceAudioStore(files.getRoot());
        MediaTimeline normal = MediaTimeline.create(request, store, new OfflineDuration(file -> 1000));
        MediaTimeline faster = MediaTimeline.create(request.withSpeed(2), store, new OfflineDuration(file -> 1000));
        assertTrue(normal.estimated); assertTrue(normal.duration > 0);
        assertEquals(normal.duration, 2 * faster.duration);
        assertEquals(1, normal.sentenceAt(0));
        MediaTimeline slowerPauses = MediaTimeline.create(request.withPause(1), store, new OfflineDuration(file -> 1000));
        assertTrue(slowerPauses.duration > normal.duration);
    }
}
