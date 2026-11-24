package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Looper;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.shadows.util.DataSource;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@org.robolectric.annotation.Config(shadows = SentencePlaybackServiceTest.CapturedMediaSession.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class SentencePlaybackServiceTest extends BaselineTest {
    @org.robolectric.annotation.Implements(android.media.session.MediaSession.class)
    public static class CapturedMediaSession extends org.robolectric.shadows.ShadowMediaSession {
        android.media.MediaMetadata metadata;
        android.media.session.PlaybackState playback;
        android.media.session.MediaSession.Callback callback;
        @org.robolectric.annotation.Implementation public void setMetadata(android.media.MediaMetadata value) { metadata = value; }
        @org.robolectric.annotation.Implementation public void setPlaybackState(android.media.session.PlaybackState value) { playback = value; }
        @org.robolectric.annotation.Implementation public void setCallback(android.media.session.MediaSession.Callback value) { callback = value; }
    }
    private ServiceController<MeditationPlaybackService> controller;
    private MeditationPlaybackService service;
    private final List<MediaPlayer> players = new ArrayList<>();
    private PlaybackRequest request(double speed) {
        return new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", speed, "Calm", 0);
    }
    private SentenceAudioStore.Book prefetched(double speed) throws Exception {
        return prefetched(request(speed));
    }
    private SentenceAudioStore.Book prefetched(PlaybackRequest request) throws Exception {
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        for (int i = 0; i < book.sentences.size(); i++) {
            if (MeditationPauses.seconds(book.sentences.get(i)) >= 0) { continue; }
            File file = book.obtain(i, target -> {
                try (FileOutputStream output = new FileOutputStream(target); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
            ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(60000, 0));
        }
        return book;
    }
    private void start() { start(0); }
    private void start(double pause) {
        PreferenceUtil.setPauseSeconds(pause); SavedAudioLibrary.restoreVoice(request(1));
        ShadowMediaPlayer.setCreateListener((actual, shadow) -> players.add(actual));
        controller = Robolectric.buildService(MeditationPlaybackService.class).create(); service = controller.get();
        service.onStartCommand(new Intent().setAction("meditation.START"), 0, 1);
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10)); Thread.sleep(1);
            assertEquals("", MeditationPlaybackService.state().getValue().error);
        }
        assertTrue(condition.getAsBoolean());
    }
    private void complete(int index) { shadowOf(players.get(index)).getOnCompletionListener().onCompletion(players.get(index)); idle(); }
    @After public void close() { if (controller != null) { controller.destroy(); idle(); } }

    @Test public void mediaPanelShowsSavedNameVioletArtworkAndProgressThatCanSeek() throws Exception {
        SentenceAudioStore.Book book = prefetched(1);
        for (int i = 0; i < book.sentences.size(); i++) {
            org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(book.audio(i).getAbsolutePath(),
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION, "60000");
        }
        int saved = SavedMeditations.save("Abendruhe", "s", "@TEXT@", "c", request(1).text);
        SavedAudioLibrary.restore(Application.getAppContext(), saved);
        start(); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        android.media.session.MediaSession session = org.robolectric.util.ReflectionHelpers.getField(service, "session");
        CapturedMediaSession controls = org.robolectric.shadow.api.Shadow.extract(session);
        await(() -> controls.metadata.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) == 120000);
        assertEquals("Abendruhe", controls.metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
        android.graphics.Bitmap artwork = controls.metadata.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART);
        assertNotNull(artwork);
        assertEquals(service.getColor(R.color.purple_500), artwork.getPixel(0, 0));
        assertEquals(artwork.getPixel(0, 0), artwork.getPixel(128, 128));
        assertEquals(artwork.getPixel(0, 0), artwork.getPixel(128, 40));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertTrue(controls.playback.getPosition() >= 1000);
        assertEquals(1f, controls.playback.getPlaybackSpeed(), 0.001);
        SavedMeditations.rename(saved, "Neue Abendruhe"); idle();
        assertEquals("Neue Abendruhe", controls.metadata.getString(android.media.MediaMetadata.METADATA_KEY_TITLE));
        controls.callback.onSeekTo(65000); idle();
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        assertTrue(controls.playback.getPosition() >= 60000);
        service.onStartCommand(new Intent().setAction("meditation.PAUSE"), 0, 2);
        long paused = controls.playback.getPosition();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals(paused, controls.playback.getPosition());
        assertEquals(0f, controls.playback.getPlaybackSpeed(), 0.001);
    }

    @Test public void mediaPauseRemainsAvailableWhileTheNextSentenceIsPreparing() throws Exception {
        SentenceAudioStore.Book book = prefetched(1);
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(book.audio(1).getAbsolutePath()), new ShadowMediaPlayer.MediaInfo(60000, 5000));
        start();
        android.media.session.MediaSession session = org.robolectric.util.ReflectionHelpers.getField(service, "session");
        CapturedMediaSession controls = org.robolectric.shadow.api.Shadow.extract(session);
        assertEquals(android.media.session.PlaybackState.STATE_PLAYING, controls.playback.getState());
        assertEquals(0f, controls.playback.getPlaybackSpeed(), 0.001);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        complete(0);
        await(() -> players.size() == 2);
        assertEquals(android.media.session.PlaybackState.STATE_PLAYING, controls.playback.getState());
        assertEquals(0f, controls.playback.getPlaybackSpeed(), 0.001);
        assertTrue((controls.playback.getActions() & android.media.session.PlaybackState.ACTION_PAUSE) != 0);
        controls.callback.onPause(); idle();
        assertEquals(android.media.session.PlaybackState.STATE_PAUSED, controls.playback.getState());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertFalse(MeditationPlaybackService.state().getValue().running);
        assertEquals(android.media.session.PlaybackState.STATE_PAUSED, controls.playback.getState());
    }

    @Test public void skippingWaitsStillKeepsOfflineAudioOnceEverySpokenSentenceFinishes() throws Exception {
        String text = "[wait 300s]Breathe slowly.[wait 300s]Relax.[wait 300s]";
        PlaybackRequest scripted = new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1, "Calm", 0);
        SentenceAudioStore.Book book = prefetched(scripted);
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, text);
        start(1);
        assertTrue(book.isComplete());
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        complete(0);
        await(() -> MeditationPlaybackService.state().getValue().position == 2);
        service.onStartCommand(new Intent().setAction("meditation.NEXT"), 0, 3);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 3);
        assertTrue(book.isComplete());
        complete(1);
        await(() -> !PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        assertTrue(book.isComplete());
        int id = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).get(0);
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        for (int i : new int[] {0, 2, 4}) { assertFalse(book.audio(i).exists()); }
        service.onStartCommand(new Intent().setAction("meditation.STOP"), 0, 4);
    }

    @Test public void manualNavigationSkipsWaitsAndStopsWhenNoSpeechRemains() throws Exception {
        String text = "[wait 300s]Breathe slowly.[wait 300s][wait 2s]Relax.[wait 300s]";
        prefetched(new PlaybackRequest(text, SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1, "Calm", 1));
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, text);
        start(1); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        service.onStartCommand(new Intent().setAction("meditation.NEXT"), 0, 2);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 4);
        service.onStartCommand(new Intent().setAction("meditation.PREVIOUS"), 0, 3);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        complete(players.size() - 1);
        await(() -> MeditationPlaybackService.state().getValue().position == 2);
        service.onStartCommand(new Intent().setAction("meditation.PAUSE"), 0, 4);
        service.onStartCommand(new Intent().setAction("meditation.RESUME"), 0, 5);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 4);
        service.onStartCommand(new Intent().setAction("meditation.SEEK").putExtra("position", 2), 0, 6);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 4);
        service.onStartCommand(new Intent().setAction("meditation.NEXT"), 0, 7);
        assertFalse(MeditationPlaybackService.state().getValue().active);
        assertFalse(MeditationPlaybackService.state().getValue().running);
    }

    @Test public void creatingAllSentencesKeepsOfflineAudioBeforeListeningFinishes() throws Exception {
        SentenceAudioStore.Book book = prefetched(1); assertTrue(book.isComplete());
        start(); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        await(() -> !PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        assertFalse(new File(book.directory(), "played-1").exists());
        complete(0);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        complete(1);
        await(() -> !MeditationPlaybackService.state().getValue().active);
        assertTrue(MeditationPlaybackService.state().getValue().completed);
        assertEquals(0, MeditationPlaybackService.state().getValue().position);
        assertEquals("0", PreferenceUtil.getSharedPreferenceString("playback_position", ""));
        await(() -> !PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        assertTrue(book.isComplete());
        int id = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).get(0);
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertEquals(request(1).text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
    }
    @Test public void stopRetainsPositionAcrossDestructionAndExplicitRewindResetsIt() throws Exception {
        prefetched(1); start(); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        service.onStartCommand(new Intent().setAction("meditation.NEXT"), 0, 2);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        MeditationPlaybackService.end(Application.getAppContext());
        Intent stop = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertFalse(stop.getBooleanExtra("rewind", true));
        service.onStartCommand(stop, 0, 3);
        assertFalse(MeditationPlaybackService.state().getValue().active);
        assertEquals(1, MeditationPlaybackService.state().getValue().position);
        assertFalse(MeditationPlaybackService.state().getValue().completed);
        assertEquals("1", PreferenceUtil.getSharedPreferenceString("playback_position", ""));
        controller.destroy(); controller = null;
        assertEquals(1, MeditationPlaybackService.state().getValue().position);
        controller = Robolectric.buildService(MeditationPlaybackService.class).create(); service = controller.get();
        service.onStartCommand(new Intent().setAction("meditation.START").putExtra("position", 1), 0, 1);
        await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        MeditationPlaybackService.end(Application.getAppContext(), true);
        Intent rewind = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertTrue(rewind.getBooleanExtra("rewind", false));
        service.onStartCommand(rewind, 0, 2);
        assertFalse(MeditationPlaybackService.state().getValue().active);
        assertEquals(0, MeditationPlaybackService.state().getValue().position);
        assertEquals("0", PreferenceUtil.getSharedPreferenceString("playback_position", ""));
    }

    @Test public void changingLocalSpeedKeepsOfflineFilesAndCurrentSentence() throws Exception {
        SentenceAudioStore.Book original = prefetched(1), changed = prefetched(1.25);
        start(); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 0);
        complete(0); await(() -> MeditationPlaybackService.state().getValue().spokenPosition == 1);
        SavedAudioLibrary.restoreVoice(request(1.25));
        service.onStartCommand(new Intent().setAction("meditation.APPLY_SETTINGS"), 0, 2);
        await(() -> players.size() == 2 && MeditationPlaybackService.state().getValue().spokenPosition == 1);
        assertEquals(1.25, PlaybackRequest.parse(PreferenceUtil.getSharedPreferenceString("playback_session", "")).speed, 0.001);
        complete(1);
        await(() -> new File(changed.directory(), "played-1").exists());
        idle();
        assertTrue(original.isComplete()); assertTrue(changed.isComplete());
        await(() -> PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).stream().anyMatch(id -> {
            SentenceAudioStore.Book saved = SavedAudioLibrary.book(Application.getAppContext(), id);
            return saved != null && saved.identity.equals(changed.identity);
        }));
        for (int id : PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids)) {
            String identity = SavedAudioLibrary.book(Application.getAppContext(), id).identity;
            assertTrue(identity.equals(original.identity) || identity.equals(changed.identity));
        }
    }
}
