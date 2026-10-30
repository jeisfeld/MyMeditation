package de.jeisfeld.mymeditation;

import android.app.AlertDialog;
import android.view.View;
import android.widget.EditText;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.audio.SavedMeditations;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.ui.savedmeditations.SavedMeditationsViewAdapter;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

/** Tests the original user workflows through real fragments, adapters, preferences and dialogs. */
public class OriginalWorkflowBaselineTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;

    private void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get();
        idle();
    }
    private void tab(int id) {
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(id);
        activity.getSupportFragmentManager().executePendingTransactions();
        idle();
    }
    private SavedMeditationsViewAdapter adapter() {
        return (SavedMeditationsViewAdapter) ((RecyclerView) activity.findViewById(R.id.recyclerViewSavedMeditations)).getAdapter();
    }
    private View row(int position) {
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        SavedMeditationsViewAdapter adapter = adapter();
        SavedMeditationsViewAdapter.MyViewHolder holder = adapter.createViewHolder(list, 0);
        adapter.bindViewHolder(holder, position);
        return holder.itemView;
    }
    private AlertDialog dialog() {
        idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertNotNull(dialog);
        assertTrue(dialog.isShowing());
        return dialog;
    }
    @After public void close() {
        if (controller != null) { controller.pause().stop().destroy(); idle(); }
    }

    @Test public void editedDraftPersistsAcrossNavigationAndActivityRecreation() {
        open();
        EditText content = activity.findViewById(R.id.editTextMeditationContent);
        assertTrue("Topic must receive focus", content.requestFocus());
        content.setText("A freshly edited topic");
        EditText text = activity.findViewById(R.id.editTextMeditationText);
        assertTrue("Meditation text must receive focus", text.requestFocus());
        text.setText("Fresh text.\nKeep my changes.");
        assertTrue("Topic must regain focus", content.requestFocus());
        idle();
        tab(R.id.navigation_configuration);
        tab(R.id.navigation_meditation);
        assertEquals("A freshly edited topic", ((EditText) activity.findViewById(R.id.editTextMeditationContent)).getText().toString());
        controller.recreate(); activity = controller.get(); idle();
        assertEquals("Fresh text.\nKeep my changes.", ((EditText) activity.findViewById(R.id.editTextMeditationText)).getText().toString());
    }

    @Test public void saveDialogCancellationAndConfirmationHaveDifferentEffects() {
        open(); tab(R.id.navigation_saved_meditations);
        activity.findViewById(R.id.button_save_current_meditation).performClick();
        AlertDialog cancelled = dialog();
        ((EditText) cancelled.findViewById(R.id.editTextDialog)).setText("Cancelled");
        cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
        activity.findViewById(R.id.button_save_current_meditation).performClick();
        AlertDialog saved = dialog();
        ((EditText) saved.findViewById(R.id.editTextDialog)).setText("Garden meditation");
        saved.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertEquals(1, adapter().getItemCount());
        int id = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).get(0);
        assertEquals("Garden meditation", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id));
        assertEquals("Guide a calm meditation.", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_system_prompt, id));
        assertEquals("Meditate on @TEXT@.", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_query_template, id));
        assertEquals("A quiet garden", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_content, id));
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
    }

    @Test public void selectingASavedNameRestoresBothPromptsAndTheMeditationDraft() {
        SavedMeditations.save("Restored", "Saved system", "Saved @TEXT@", "Saved topic", "Saved meditation.");
        open(); tab(R.id.navigation_saved_meditations);
        row(0).findViewById(R.id.textViewMeditationName).performClick(); idle();
        tab(R.id.navigation_meditation);
        assertEquals("Saved topic", ((EditText) activity.findViewById(R.id.editTextMeditationContent)).getText().toString());
        assertEquals("Saved meditation.", ((EditText) activity.findViewById(R.id.editTextMeditationText)).getText().toString());
        tab(R.id.navigation_configuration);
        assertEquals("Saved system", ((EditText) activity.findViewById(R.id.editTextSystemPrompt)).getText().toString());
        assertEquals("Saved @TEXT@", ((EditText) activity.findViewById(R.id.editTextQueryTemplate)).getText().toString());
    }

    @Test public void reorderingAndConfirmedDeletionPreserveOtherEntriesAndDoNotReuseIds() {
        int first = SavedMeditations.save("First", "s1", "q1 @TEXT@", "c1", "t1");
        int second = SavedMeditations.save("Second", "s2", "q2 @TEXT@", "c2", "t2");
        int third = SavedMeditations.save("Third", "s3", "q3 @TEXT@", "c3", "t3");
        open(); tab(R.id.navigation_saved_meditations);
        adapter().onRowMoved(0, 2); idle();
        assertEquals(Arrays.asList(second, third, first), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        row(1).findViewById(R.id.imageViewDelete).performClick();
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
        assertEquals(3, adapter().getItemCount());
        row(1).findViewById(R.id.imageViewDelete).performClick();
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertEquals(Arrays.asList(second, first), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        for (int key : new int[] {R.string.key_saved_meditation_name, R.string.key_saved_system_prompt,
                R.string.key_saved_query_template, R.string.key_saved_meditation_content, R.string.key_saved_meditation_text}) {
            assertNull(PreferenceUtil.getIndexedSharedPreferenceString(key, third));
        }
        assertEquals("t2", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, second));
        int next = SavedMeditations.save("Next", "s", "@TEXT@", "c", "t");
        assertTrue(next > third);
    }

    @Test public void failedCreationKeepsOldMeditationAndPersistsTheVisibleUnfocusedTopic() throws Exception {
        // An invalid model is rejected before looking up credentials or opening a connection.
        PreferenceUtil.setSharedPreferenceString(R.string.key_ai_model, "unsupported-baseline-test-model");
        open();
        EditText content = activity.findViewById(R.id.editTextMeditationContent);
        content.requestFocus(); content.setText("Current topic, not the stored topic");
        View create = activity.findViewById(R.id.buttonCreateMeditation);
        create.performClick();
        long deadline = System.nanoTime() + 5000000000L;
        while (!create.isEnabled() && System.nanoTime() < deadline) { idle(); Thread.sleep(10); }
        idle();
        assertTrue(create.isEnabled());
        assertEquals("Current topic, not the stored topic", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
        assertEquals("Breathe slowly. Relax.", ((EditText) activity.findViewById(R.id.editTextMeditationText)).getText().toString());
    }
}
