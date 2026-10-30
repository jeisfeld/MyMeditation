package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import de.jeisfeld.mymeditation.MainActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real audio-focus requests and MP3 playback; requires a device/emulator, no API keys. */
@RunWith(AndroidJUnit4.class)
public class OfflinePlaybackInterruptionTest {
    private OfflinePlaybackService.State await(Predicate<OfflinePlaybackService.State> condition) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            OfflinePlaybackService.State state = OfflinePlaybackService.state().getValue();
            assertTrue("Playback failed: " + state.error, state.error.isEmpty());
            if (condition.test(state)) { return state; }
            SystemClock.sleep(50);
        }
        fail("Timed out waiting for offline playback state");
        return null;
    }

    private void interruption(int focusType, boolean cancel) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AudioManager audio = context.getSystemService(AudioManager.class);
        AudioFocusRequest navigation = new AudioFocusRequest.Builder(focusType)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> { }).build();
        int id = Integer.MAX_VALUE - (int) (SystemClock.elapsedRealtime() % 1000000);
        File file = OfflineMeditations.file(context, id);
        assertFalse("Test file already exists", file.exists());
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        try (Mp3Writer writer = new Mp3Writer(new FileOutputStream(file))) { writer.silence(30, () -> { }); }
        try (ActivityScenario<MainActivity> activity = ActivityScenario.launch(MainActivity.class)) {
            try {
                activity.onActivity(owner -> OfflinePlaybackService.toggle(owner, id));
                await(state -> state.id == id && state.playing && audio.isMusicActive());
                SystemClock.sleep(500);
                assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(navigation));
                if (focusType == AudioManager.AUDIOFOCUS_GAIN) {
                    await(state -> state.id == id && !state.playing && !state.waitingForAudioFocus);
                } else {
                    OfflinePlaybackService.State interrupted = await(state -> state.id == id && state.waitingForAudioFocus && !state.playing);
                    assertTrue(interrupted.position > 0);
                    if (cancel) {
                        // Same action as the saved-list Pause icon, including while waiting for focus.
                        activity.onActivity(owner -> OfflinePlaybackService.toggle(owner, id));
                        await(state -> state.id == id && !state.playing && !state.waitingForAudioFocus);
                    }
                    audio.abandonAudioFocusRequest(navigation);
                    if (!cancel) {
                        OfflinePlaybackService.State resumed = await(state -> state.id == id && state.playing && !state.waitingForAudioFocus);
                        assertEquals(interrupted.position, resumed.position, 500);
                    }
                }
                audio.abandonAudioFocusRequest(navigation);
                if (cancel || focusType == AudioManager.AUDIOFOCUS_GAIN) {
                    SystemClock.sleep(500);
                    assertFalse(OfflinePlaybackService.state().getValue().playing);
                    assertFalse(OfflinePlaybackService.state().getValue().waitingForAudioFocus);
                }
            } finally {
                audio.abandonAudioFocusRequest(navigation);
                activity.onActivity(owner -> OfflinePlaybackService.stopIfPlaying(owner, id));
                await(state -> state.id == 0);
            }
        } finally { file.delete(); }
    }

    @Test public void temporaryNavigationFocusResumesMp3AtItsPausedPosition() throws Exception {
        interruption(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, false);
    }
    @Test public void navigationDuckingRequestAlsoPausesAndResumesSpeech() throws Exception {
        interruption(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
    }
    @Test public void manualPauseDuringNavigationCancelsResume() throws Exception {
        interruption(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, true);
    }
    @Test public void permanentFocusLossStaysPaused() throws Exception {
        interruption(AudioManager.AUDIOFOCUS_GAIN, false);
    }
}
