package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.widget.EditText;
import android.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.*;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

public class SavedMeditationPlacementTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int save(String name) { return SavedMeditations.save(name, "s", "@TEXT@", "Topic", "Breathe."); }
    private void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle(); savedTab();
    }
    private void savedTab() { ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_saved_meditations); idle(); }
    private EditText filter() { return activity.findViewById(R.id.editTextMeditationFilter); }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }
    @Test public void selectingEditingNavigatingAndManualSavingRetainTheGroupAndInsertAfterItsLastEntry() {
        int first = save("Abendruhe 1"), middle = save("Hidden middle"), last = save("Abendruhe 2 (lang)"), tail = save("Hidden tail");
        open();
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        SavedMeditationsViewAdapter adapter = (SavedMeditationsViewAdapter) list.getAdapter();
        SavedMeditationsViewAdapter.MyViewHolder row = adapter.createViewHolder(list, 0); adapter.bindViewHolder(row, 0);
        row.itemView.findViewById(R.id.textViewMeditationName).performClick(); idle();
        assertEquals("Abendruhe", filter().getText().toString());
        assertEquals(2, list.getAdapter().getItemCount());
        SavedMeditations.rename(first, "Abendruhe 1 (edited)");
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_meditation); idle();
        SavedAudioLibrary.clearSource();
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "Edited new meditation.");
        savedTab();
        assertEquals("Abendruhe", filter().getText().toString());
        activity.findViewById(R.id.button_save_current_meditation).performClick(); idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        ((EditText) dialog.findViewById(R.id.editTextDialog)).setText("Abendruhe 3");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        int added = PreferenceUtil.getSharedPreferenceInt(R.string.key_max_saved_meditation_id, 0);
        assertEquals(Arrays.asList(first, middle, last, added, tail), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        assertEquals("Abendruhe", filter().getText().toString());
        assertEquals(3, ((RecyclerView) activity.findViewById(R.id.recyclerViewSavedMeditations)).getAdapter().getItemCount());
        controller.recreate(); activity = controller.get(); idle();
        assertEquals("Abendruhe", filter().getText().toString());
        filter().setText("Hidden"); idle();
        savedTab(); assertEquals("Hidden", SavedMeditations.nameFilter());
    }
    @Test public void emptyUnmatchedAndCaseInsensitiveSelectionsPreserveEveryExistingEntry() {
        int first = save("Calm 1"), hidden = save("Hidden"), last = save("CALM 2"), tail = save("Tail");
        SavedMeditations.setNameFilter(" cAlM ");
        int next = save("New name outside filter");
        assertEquals(Arrays.asList(first, hidden, last, next, tail), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        assertEquals(" cAlM ", SavedMeditations.nameFilter());
        SavedMeditations.setNameFilter("No matches"); int unmatched = save("Unmatched");
        SavedMeditations.setNameFilter(""); int end = save("Final");
        assertEquals(Arrays.asList(first, hidden, last, next, tail, unmatched, end), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
    }
    @Test public void audioOnlyInsertionUsesRecordedMatchesRatherThanLaterTextOnlyMatches() throws Exception {
        int first = save("Calm 1"), textOnly = save("Calm 2"), tail = save("Tail");
        PlaybackRequest request = new PlaybackRequest("Breathe.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1, "", 0);
        SavedAudioLibrary.begin(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        book.obtain(0, file -> java.nio.file.Files.write(file.toPath(), new byte[]{'I', 'D', '3', 0}), () -> { });
        SavedAudioLibrary.restoreVoice(request); SavedAudioLibrary.attachAvailable(Application.getAppContext(), first);
        SavedMeditations.setNameFilter("Calm"); SavedMeditations.setAudioOnly(true);
        int next = save("Calm 3");
        assertEquals(Arrays.asList(first, next, textOnly, tail), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        assertTrue(SavedMeditations.audioOnly()); assertEquals("Calm", SavedMeditations.nameFilter());
    }
}
