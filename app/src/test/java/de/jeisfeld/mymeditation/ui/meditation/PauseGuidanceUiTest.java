package de.jeisfeld.mymeditation.ui.meditation;

import android.view.View;
import android.widget.Spinner;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.MeditationPauses;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

public class PauseGuidanceUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get(); idle();
    }
    private Spinner picker() { return activity.findViewById(R.id.spinnerPauseGuidance); }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }

    @Test public void defaultsToFewAndSelectionSurvivesNavigationAndRecreationWithoutEditingPromptsOrText() {
        open(); assertEquals(1, picker().getSelectedItemPosition());
        String prompt = PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt);
        String text = PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text);
        picker().setSelection(0); idle();
        assertEquals("none", PreferenceUtil.getSharedPreferenceString(MeditationPauses.PREFERENCE, ""));
        controller.recreate(); activity = controller.get(); idle();
        assertEquals(0, picker().getSelectedItemPosition());
        picker().setSelection(2); idle();
        BottomNavigationView nav = activity.findViewById(R.id.nav_view);
        nav.setSelectedItemId(R.id.navigation_configuration); idle();
        nav.setSelectedItemId(R.id.navigation_meditation); idle();
        assertEquals(2, picker().getSelectedItemPosition());
        assertEquals("many", PreferenceUtil.getSharedPreferenceString(MeditationPauses.PREFERENCE, ""));
        assertEquals(prompt, PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
        assertEquals(text, PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
    }

    @Test public void loadingFromSavedTabRestoresPacingWhenReturningToMeditation() {
        PreferenceUtil.setSharedPreferenceString(MeditationPauses.PREFERENCE, "none");
        de.jeisfeld.mymeditation.audio.SavedMeditations.save("No pauses", "s", "@TEXT@", "c", "Breathe.");
        open(); picker().setSelection(2); idle();
        BottomNavigationView nav = activity.findViewById(R.id.nav_view);
        nav.setSelectedItemId(R.id.navigation_saved_meditations); idle();
        androidx.recyclerview.widget.RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        de.jeisfeld.mymeditation.ui.savedmeditations.SavedMeditationsViewAdapter adapter =
                (de.jeisfeld.mymeditation.ui.savedmeditations.SavedMeditationsViewAdapter) list.getAdapter();
        de.jeisfeld.mymeditation.ui.savedmeditations.SavedMeditationsViewAdapter.MyViewHolder holder = adapter.createViewHolder(list, 0);
        adapter.bindViewHolder(holder, 0);
        holder.itemView.findViewById(R.id.textViewMeditationName).performClick(); idle();
        assertEquals("none", PreferenceUtil.getSharedPreferenceString(MeditationPauses.PREFERENCE, ""));
        nav.setSelectedItemId(R.id.navigation_meditation); idle();
        assertEquals(0, picker().getSelectedItemPosition());
        assertEquals("none", PreferenceUtil.getSharedPreferenceString(MeditationPauses.PREFERENCE, ""));
    }

    @Test public void restoringASavedMeditationUpdatesTheVisiblePausePicker() {
        open(); picker().setSelection(0); idle();
        int id = de.jeisfeld.mymeditation.audio.SavedMeditations.save("Saved", "s", "@TEXT@", "c", "Breathe.");
        picker().setSelection(2); idle();
        de.jeisfeld.mymeditation.audio.SavedAudioLibrary.restore(activity, id); idle();
        assertEquals(0, picker().getSelectedItemPosition());
    }

    @Test @Config(qualifiers = "de-rDE-w320dp-h800dp")
    @org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
    public void compactDropdownsHaveRoomForTheSelectedPacingAndSpeechProvider() {
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, "elevenlabs");
        open();
        assertSelectedTextFits(picker());
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_configuration); idle();
        Spinner backend = activity.findViewById(R.id.spinnerSpeechBackend);
        assertSelectedTextFits(backend);
    }
    private void assertSelectedTextFits(Spinner spinner) {
        android.widget.TextView selected = (android.widget.TextView) spinner.getSelectedView();
        assertNotNull(selected);
        float textWidth = selected.getPaint().measureText(selected.getText().toString());
        int available = selected.getWidth() - selected.getCompoundPaddingLeft() - selected.getCompoundPaddingRight();
        assertTrue(selected.getText() + " needs " + textWidth + " pixels, has " + available, textWidth <= available);
        assertNotNull(selected.getLayout());
        assertEquals(selected.getText().toString(), 0, selected.getLayout().getEllipsisCount(0));
    }

    @Test @Config(qualifiers = "de-rDE-w320dp-h800dp")
    public void germanPickerSitsToTheRightOfTheHeadingOnANarrowPhone() {
        open();
        View heading = activity.findViewById(R.id.labelMeditationContent);
        assertEquals("Wenige Pausen", picker().getSelectedItem().toString());
        assertTrue(heading.getWidth() > 0);
        assertTrue(picker().getLeft() >= heading.getRight());
        assertTrue(picker().getRight() <= ((View) picker().getParent()).getWidth());
    }
}
