package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StopPositionUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private final String line = "Repeated sentence.\n";
    private final String text = String.join("", java.util.Collections.nCopies(80, line));
    private void publish(boolean active, int position) { publish(active, position, false); }
    private void publish(boolean active, int position, boolean completed) {
        ((MutableLiveData<MeditationPlaybackService.State>) MeditationPlaybackService.state()).setValue(
                new MeditationPlaybackService.State(active, position, SpeechSegments.split(text), "", "", 0,
                        active, text, active ? position : -1, false, 0, completed));
        idle();
    }
    private EditText openAtSixty() {
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, text);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        publish(true, 60); layout();
        EditText editor = activity.findViewById(R.id.editTextMeditationText);
        int top = editor.getLayout().getLineTop(editor.getLayout().getLineForOffset(60 * line.length()));
        editor.scrollTo(0, Math.max(0, top - editor.getHeight() / 4));
        assertTrue(editor.getScrollY() > 0);
        return editor;
    }
    private void layout() {
        View container = activity.findViewById(R.id.container);
        container.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(891, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, 411, 891);
        activity.findViewById(R.id.editTextMeditationText).getViewTreeObserver().dispatchOnPreDraw();
        idle();
    }
    private Intent started() { return shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService(); }
    @After public void close() {
        if (controller != null) { controller.pause().stop().destroy(); idle(); }
        publish(false, 0);
    }
    @Test public void normalStopPreservesScrollSentenceAndSubsequentReadPosition() {
        EditText editor = openAtSixty(); int scroll = editor.getScrollY();
        activity.findViewById(R.id.buttonEndMeditation).performClick();
        assertFalse(started().getBooleanExtra("rewind", true));
        publish(false, 60); layout();
        assertEquals(scroll, editor.getScrollY());
        assertEquals(60 * line.length(), editor.getSelectionStart());
        assertEquals(60, ((SeekBar) activity.findViewById(R.id.seekBarAudio)).getProgress());
        activity.findViewById(R.id.buttonSpeakMeditation).performClick(); idle();
        assertEquals(60, started().getIntExtra("position", -1));
    }
    @Test public void naturalCompletionRewindsSentenceCursorAndScroll() {
        EditText editor = openAtSixty();
        publish(false, 0, true); layout();
        assertEquals(0, editor.getScrollY()); assertEquals(0, editor.getSelectionStart());
        assertEquals(0, ((SeekBar) activity.findViewById(R.id.seekBarAudio)).getProgress());
        activity.findViewById(R.id.buttonSpeakMeditation).performClick(); idle();
        assertEquals(0, started().getIntExtra("position", -1));
    }
    @Test public void longPressStopRewindsSentenceCursorAndScroll() {
        EditText editor = openAtSixty();
        assertTrue(activity.findViewById(R.id.buttonEndMeditation).performLongClick());
        assertTrue(started().getBooleanExtra("rewind", false));
        publish(false, 0); layout();
        assertEquals(0, editor.getScrollY()); assertEquals(0, editor.getSelectionStart());
        assertEquals(0, ((SeekBar) activity.findViewById(R.id.seekBarAudio)).getProgress());
    }
}
