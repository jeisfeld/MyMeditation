package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.widget.EditText;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.MeditationGenerationService;
import de.jeisfeld.mymeditation.http.MeditationGenerationServiceTest.ShadowGenerationAi;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@Config(shadows = ShadowGenerationAi.class)
public class GenerationTextEditingTest extends BaselineTest {
    private ActivityController<MainActivity> activity;
    private ServiceController<MeditationGenerationService> generation;
    private void playback(boolean active) {
        String text = PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text);
        ((MutableLiveData<MeditationPlaybackService.State>) MeditationPlaybackService.state()).setValue(
                new MeditationPlaybackService.State(active, 0, SpeechSegments.split(text), "", "", 0,
                        active, text, active ? 0 : -1, false, 0));
        idle();
    }
    @After public void close() {
        if (generation != null) { generation.destroy(); }
        if (activity != null) { playback(false); activity.pause().stop().destroy(); }
        ShadowGenerationAi.generations.clear(); idle();
    }
    @Test public void editorStaysLockedUntilBothPlaybackAndGenerationHaveFinished() {
        ShadowGenerationAi.generations.clear();
        activity = Robolectric.buildActivity(MainActivity.class).setup().visible(); idle();
        EditText editor = activity.get().findViewById(R.id.editTextMeditationText);
        assertNotNull(editor.getKeyListener()); editor.requestFocus();
        generation = Robolectric.buildService(MeditationGenerationService.class).create();
        generation.get().onStartCommand(new Intent(Application.getAppContext(), MeditationGenerationService.class)
                .putExtra("model", "gpt-test").putExtra("system", "Guide").putExtra("prompt", "Topic"), 0, 1); idle();
        assertNull(editor.getKeyListener()); assertFalse(editor.isCursorVisible());
        assertTrue(editor.isEnabled());
        assertTrue(activity.get().findViewById(R.id.buttonSpeakMeditation).isEnabled());
        assertEquals("Breathe slowly. Relax.", editor.getText().toString());
        playback(true); assertNull(editor.getKeyListener());
        playback(false); assertNull(editor.getKeyListener());
        playback(true);
        ShadowGenerationAi.generations.get(0).complete("New meditation.", null); idle();
        assertNull(editor.getKeyListener()); // Generation completion cannot unlock active playback.
        playback(false);
        assertNotNull(editor.getKeyListener()); assertTrue(editor.isCursorVisible());
        assertEquals("New meditation.", editor.getText().toString());
    }
}
