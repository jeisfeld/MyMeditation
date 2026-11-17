package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.view.View;
import android.widget.TextView;
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
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

public class OfflineEditorUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private AlertDialog edit() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_saved_meditations); idle();
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        SavedMeditationsViewAdapter adapter = (SavedMeditationsViewAdapter) list.getAdapter();
        SavedMeditationsViewAdapter.MyViewHolder holder = adapter.createViewHolder(list, 0); adapter.bindViewHolder(holder, 0);
        holder.itemView.findViewById(R.id.buttonRenameMeditation).performClick(); idle();
        return (AlertDialog) ShadowDialog.getLatestDialog();
    }
    private void await(BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < end) { idle(); Thread.sleep(2); }
        assertTrue(condition.getAsBoolean());
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }
    @Test public void editShowsWeightedCoverageAndUsesSavedVoiceWithoutLoadingTheEntryIntoDraft() throws Exception {
        PlaybackRequest request = new PlaybackRequest("One.This has four words.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1.2, "Calm", 0);
        int id = SavedMeditations.save("Name", "s", "@TEXT@", "topic", request.text);
        PreferenceUtil.setSharedPreferenceString("saved_speech_settings_" + id, request.json());
        SentenceAudio.store(Application.getAppContext()).open(request).obtain(0, target -> {
            try (FileOutputStream out = new FileOutputStream(target); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); }
        }, () -> { });
        String draft = PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text);
        AlertDialog dialog = edit(); View prepare = dialog.findViewById(R.id.buttonPrepareOffline); await(prepare::isEnabled);
        assertTrue(((TextView) dialog.findViewById(R.id.textOfflineCoverage)).getText().toString().contains("20% (1/5"));
        assertTrue(((TextView) dialog.findViewById(R.id.textOfflineVoice)).getText().toString().contains("Kore"));
        prepare.performClick(); idle();
        Intent intent = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertEquals(MeditationOfflineService.class.getName(), intent.getComponent().getClassName());
        assertEquals(id, intent.getIntExtra("savedId", 0)); assertEquals("Kore", PlaybackRequest.parse(intent.getStringExtra("request")).voice);
        assertEquals(draft, PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        assertEquals("device", PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend));
        ((android.widget.EditText) dialog.findViewById(R.id.editSavedName)).setText("Renamed");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertEquals("Renamed", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id));
        assertEquals(request.text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
    }
    @Test public void deviceOnlyEntryExplainsCloudVoiceRequirementAndDoesNotOfferGeneration() {
        SavedMeditations.save("Name", "s", "@TEXT@", "topic", "Breathe.");
        AlertDialog dialog = edit();
        assertEquals(View.GONE, dialog.findViewById(R.id.buttonPrepareOffline).getVisibility());
        assertEquals(activity.getString(R.string.offline_device_notice), ((TextView) dialog.findViewById(R.id.textOfflineCoverage)).getText().toString());
    }
}
