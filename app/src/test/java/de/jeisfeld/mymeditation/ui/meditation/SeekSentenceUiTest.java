package de.jeisfeld.mymeditation.ui.meditation;

import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
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
public class SeekSentenceUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }
    private void open(String text) {
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, text);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        View container = activity.findViewById(R.id.container);
        container.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(891, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, 411, 891);
    }
    private void seek(int position) {
        SeekBar bar = activity.findViewById(R.id.seekBarAudio);
        SeekBar.OnSeekBarChangeListener listener = shadowOf(bar).getOnSeekBarChangeListener();
        listener.onStartTrackingTouch(bar); bar.setProgress(position);
        listener.onProgressChanged(bar, position, true); listener.onStopTrackingTouch(bar); idle();
        activity.findViewById(R.id.editTextMeditationText).getViewTreeObserver().dispatchOnPreDraw();
    }
    @Test public void releasingSeekSelectsTheCorrectRepeatedSentenceAndScrollsWithoutTakingFocus() {
        String line = "Repeated sentence.\n";
        String text = String.join("", java.util.Collections.nCopies(80, line));
        open(text);
        EditText editor = activity.findViewById(R.id.editTextMeditationText);
        seek(60);
        assertEquals(60 * line.length(), editor.getSelectionStart());
        assertEquals(editor.getSelectionStart(), editor.getSelectionEnd());
        assertTrue(editor.getScrollY() > 0);
        int top = editor.getLayout().getLineTop(editor.getLayout().getLineForOffset(editor.getSelectionStart()));
        assertTrue(top >= editor.getScrollY());
        assertTrue(top < editor.getScrollY() + editor.getHeight());
        assertFalse(editor.hasFocus());
        seek(0); assertEquals(0, editor.getSelectionStart()); assertEquals(0, editor.getScrollY());
    }
    @Test public void seekingWaitsSelectsNextSpeechAndReadStartsFromThatPosition() {
        String text = "First.[wait 20s][wait 30s]Second.[wait 10s]";
        open(text); seek(1);
        EditText editor = activity.findViewById(R.id.editTextMeditationText);
        assertEquals(text.indexOf("Second."), editor.getSelectionStart());
        assertEquals(3, ((SeekBar) activity.findViewById(R.id.seekBarAudio)).getProgress());
        activity.findViewById(R.id.buttonSpeakMeditation).performClick(); idle();
        android.content.Intent start = shadowOf((android.app.Application) activity.getApplication()).getNextStartedService();
        assertEquals(3, start.getIntExtra("position", -1));
    }
    @Test public void seekingPastLastSpeechPositionsCursorAtEnd() {
        String text = "First.[wait 20s]";
        open(text); seek(1);
        assertEquals(text.length(), ((EditText) activity.findViewById(R.id.editTextMeditationText)).getSelectionStart());
    }
}
