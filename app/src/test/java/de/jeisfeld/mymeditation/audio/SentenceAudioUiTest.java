package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.ui.savedmeditations.SavedMeditationsViewAdapter;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.FileOutputStream;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

public class SentenceAudioUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int recorded() throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI,
                "gemini-3.8-flash-tts", "Kore", 1.25, "Calm", 5);
        SavedAudioLibrary.begin(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        for (int i = 0; i < book.sentences.size(); i++) {
            book.obtain(i, file -> {
                try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
            }, () -> { });
        }
        book.finish();
        return SavedAudioLibrary.keepComplete(Application.getAppContext(), request, SavedAudioLibrary.Snapshot.current(Application.getAppContext()));
    }
    private void openSaved() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_saved_meditations); idle();
    }
    private View row(int index) {
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        SavedMeditationsViewAdapter adapter = (SavedMeditationsViewAdapter) list.getAdapter();
        SavedMeditationsViewAdapter.MyViewHolder holder = adapter.createViewHolder(list, 0); adapter.bindViewHolder(holder, index);
        return holder.itemView;
    }
    private EditText input(View view) {
        if (view instanceof EditText) { return (EditText) view; }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                EditText found = input(((ViewGroup) view).getChildAt(i)); if (found != null) { return found; }
            }
        }
        return null;
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }

    @Test public void audioOnlyFilterIncludesSentenceRecordingsAndPlayUsesTheHighlightedMeditationPage() throws Exception {
        int id = recorded();
        SavedMeditations.save("Text only", "s", "@TEXT@", "c", "Unrecorded.");
        openSaved();
        ((android.widget.CheckBox) activity.findViewById(R.id.checkBoxAudioOnly)).setChecked(true); idle();
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        assertEquals(1, list.getAdapter().getItemCount());
        assertEquals(View.VISIBLE, row(0).findViewById(R.id.buttonPlayOffline).getVisibility());
        row(0).findViewById(R.id.buttonPlayOffline).performClick(); idle();
        Intent start = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertEquals(MeditationPlaybackService.class.getName(), start.getComponent().getClassName());
        assertEquals(id, start.getIntExtra("savedId", 0));
        assertEquals(R.id.navigation_meditation, ((BottomNavigationView) activity.findViewById(R.id.nav_view)).getSelectedItemId());
        assertEquals("Breathe slowly. Relax.", ((EditText) activity.findViewById(R.id.editTextMeditationText)).getText().toString());
    }
    @Test public void shareAsksForPauseDefaultsToTwoAndDoesNotChangePlaybackPauseOrSettings() throws Exception {
        int id = recorded(); PreferenceUtil.setPauseSeconds(7.5); openSaved();
        row(0).findViewById(R.id.buttonShareOffline).performClick(); idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        EditText input = input(dialog.getWindow().getDecorView()); assertNotNull(input);
        assertEquals("2", input.getText().toString());
        input.setText("-1"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing()); assertNotNull(input.getError());
        input.setText("2,5"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        Intent start = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertEquals(MeditationExportService.class.getName(), start.getComponent().getClassName());
        assertEquals(id, start.getIntExtra("shareId", 0)); assertEquals(2.5, start.getDoubleExtra("pause", -1), 0);
        assertEquals(7.5, PreferenceUtil.getPauseSeconds(), 0);
        assertEquals("device", PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend));
    }
    private void awaitLabel(String expected) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        android.widget.TextView label = activity.findViewById(R.id.labelMeditationText);
        while (!expected.equals(label.getText().toString()) && System.nanoTime() < deadline) { idle(); Thread.sleep(2); }
        assertEquals(expected, label.getText().toString());
    }
    @Test public void offlineHeadingTracksExactVoiceSpeedAndTextButIgnoresPauses() throws Exception {
        int id = recorded(); SavedAudioLibrary.restore(Application.getAppContext(), id);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text_offline));
        PreferenceUtil.setPauseSeconds(0.5); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text_offline));
        SavedAudioLibrary.restoreVoice(new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI,
                "gemini-3.8-flash-tts", "Kore", 1.5, "Calm", 0)); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text));
        SavedAudioLibrary.restore(Application.getAppContext(), id); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text_offline));
        ((EditText) activity.findViewById(R.id.editTextMeditationText)).setText("Different meditation."); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text));
    }

}
