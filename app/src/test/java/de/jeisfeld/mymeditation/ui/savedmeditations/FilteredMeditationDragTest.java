package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.*;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

public class FilteredMeditationDragTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private final List<Integer> ids = new ArrayList<>();
    private void open() {
        for (String name : new String[] {"Hidden start", "Match A", "Hidden middle", "Match B", "Hidden gap", "Match C", "Hidden end"}) {
            ids.add(SavedMeditations.save(name, "s", "@TEXT@", "c", "Breathe."));
        }
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_saved_meditations); idle();
    }
    private void filter(String name, boolean audio) {
        ((EditText) activity.findViewById(R.id.editTextMeditationFilter)).setText(name);
        ((CheckBox) activity.findViewById(R.id.checkBoxAudioOnly)).setChecked(audio); idle();
    }
    private SavedMeditationsViewAdapter adapter() {
        return (SavedMeditationsViewAdapter) ((RecyclerView) activity.findViewById(R.id.recyclerViewSavedMeditations)).getAdapter();
    }
    private View row(int index) {
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        SavedMeditationsViewAdapter.MyViewHolder holder = adapter().createViewHolder(list, 0);
        adapter().bindViewHolder(holder, index); return holder.itemView;
    }
    private void order(int... indices) {
        List<Integer> expected = new ArrayList<>();
        for (int index : indices) { expected.add(ids.get(index)); }
        assertEquals(expected, PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
    }
    private void visible(String... names) {
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < adapter().getItemCount(); i++) {
            actual.add(((TextView) row(i).findViewById(R.id.textViewMeditationName)).getText().toString());
        }
        assertEquals(Arrays.asList(names), actual);
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }

    @Test public void draggingDownPlacesTheEntryImmediatelyAfterTheVisibleTargetAndSurvivesReopening() {
        open(); filter("match", false);
        assertTrue(adapter().canReorder());
        assertEquals(View.VISIBLE, row(0).findViewById(R.id.imageViewDragHandle).getVisibility());
        adapter().onRowMoved(0, 2); idle();
        order(0, 2, 3, 4, 5, 1, 6); visible("Match B", "Match C", "Match A");
        filter("", false); assertEquals(7, adapter().getItemCount());
        controller.recreate(); activity = controller.get(); idle();
        order(0, 2, 3, 4, 5, 1, 6);
        filter("match", false); visible("Match B", "Match C", "Match A");
    }

    @Test public void successiveUpwardDragStepsPlaceTheEntryImmediatelyBeforeEachTarget() {
        open(); filter("MATCH", false);
        adapter().onRowMoved(2, 1); idle();
        order(0, 1, 2, 5, 3, 4, 6);
        adapter().onRowMoved(1, 0); idle();
        order(0, 5, 1, 2, 3, 4, 6); visible("Match C", "Match A", "Match B");
    }

    @Test public void audioOnlyAndCombinedFiltersKeepTextOnlyEntriesWhenDraggingEitherWay() throws Exception {
        open();
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, "gemini");
        PlaybackRequest request = MeditationPlaybackService.currentRequest("Breathe.", 0);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        book.obtain(0, file -> {
            try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
        }, () -> { });
        book.finish();
        for (int index : new int[] {1, 3, 5}) { SavedAudioLibrary.attachAvailable(Application.getAppContext(), ids.get(index)); }
        filter("", true); visible("Match A", "Match B", "Match C");
        assertTrue(adapter().canReorder());
        adapter().onRowMoved(0, 1); idle(); order(0, 2, 3, 1, 4, 5, 6);
        filter("match", true); assertTrue(adapter().canReorder());
        adapter().onRowMoved(1, 0); idle(); order(0, 2, 1, 3, 4, 5, 6);
        for (int index : new int[] {1, 3, 5}) { assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), ids.get(index))); }
    }

    @Test public void emptySingleAndStaleDragPositionsDoNotLoseEntriesOrNewlyAddedHiddenRows() {
        open(); filter("missing", false); adapter().onRowMoved(0, 1); order(0, 1, 2, 3, 4, 5, 6);
        filter("Match A", false); assertFalse(adapter().canReorder());
        adapter().onRowMoved(0, 0); order(0, 1, 2, 3, 4, 5, 6);
        filter("match", false);
        adapter().onRowMoved(-1, 0); adapter().onRowMoved(0, 3); order(0, 1, 2, 3, 4, 5, 6);
        ids.add(SavedMeditations.save("New hidden", "s", "@TEXT@", "c", "Breathe."));
        adapter().onRowMoved(0, 1); idle(); order(0, 2, 3, 1, 4, 5, 6, 7);
    }
}
