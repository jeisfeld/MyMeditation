package de.jeisfeld.mymeditation.audio;

import android.app.AlertDialog;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.ui.savedmeditations.SavedMeditationsViewAdapter;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

public class StorageUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private int saved() throws Exception {
        PlaybackRequest request = new PlaybackRequest("Breathe.", SpeechBackend.GEMINI, "gemini-3.8-flash-tts", "Kore", 1, "", 0);
        SavedAudioLibrary.begin(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        book.obtain(0, file -> { try (FileOutputStream out = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(out)) { writer.silence(0.1, () -> { }); } }, () -> { });
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request, SavedAudioLibrary.Snapshot.current(Application.getAppContext()));
        assertTrue(id > 0); return id;
    }
    private void open(int tab) {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(tab); idle();
    }
    private View row() {
        RecyclerView list = activity.findViewById(R.id.recyclerViewSavedMeditations);
        SavedMeditationsViewAdapter adapter = (SavedMeditationsViewAdapter) list.getAdapter();
        SavedMeditationsViewAdapter.MyViewHolder holder = adapter.createViewHolder(list, 0); adapter.bindViewHolder(holder, 0); return holder.itemView;
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { idle(); Thread.sleep(2); }
        assertTrue(condition.getAsBoolean());
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } AppStorage.endCleaning(); }

    @Test public void additionalSettingsShowsThreeCategoriesAndClearsOnlyOtherAudioAfterConfirmation() throws Exception {
        int id = saved();
        File cache = new File(Application.getAppContext().getCacheDir(), "native-speech"); assertTrue(cache.mkdirs() || cache.isDirectory());
        File other = new File(cache, "raw.wav"); Files.write(other.toPath(), new byte[2048]);
        open(R.id.navigation_configuration); activity.findViewById(R.id.buttonAiSettings).performClick(); idle();
        androidx.appcompat.app.AlertDialog settings = (androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog();
        View clear = settings.findViewById(R.id.buttonClearOtherAudio); await(clear::isEnabled);
        assertTrue(((TextView) settings.findViewById(R.id.textStorageOffline)).getText().length() > 0);
        assertTrue(((TextView) settings.findViewById(R.id.textStorageOtherAudio)).getText().length() > 0);
        assertTrue(((TextView) settings.findViewById(R.id.textStorageOtherData)).getText().length() > 0);
        assertTrue(AppStorage.beginCleaning()); idle(); assertFalse(clear.isEnabled());
        AppStorage.endCleaning(); idle(); await(clear::isEnabled);
        clear.performClick(); idle();
        ((androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
        assertTrue(other.exists());
        clear.performClick(); idle();
        ((androidx.appcompat.app.AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        await(() -> !other.exists() && !AppStorage.isCleaning());
        assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertEquals("Guide a calm meditation.", PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
    }
    @Test public void savedMeditationDeleteOffersAudioOnlyAndKeepsTheEntry() throws Exception {
        int id = saved(); open(R.id.navigation_saved_meditations);
        row().findViewById(R.id.imageViewDelete).performClick(); idle();
        AlertDialog choices = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue(choices.getButton(AlertDialog.BUTTON_NEUTRAL).isShown());
        choices.getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle(); assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        row().findViewById(R.id.imageViewDelete).performClick(); idle();
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_NEUTRAL).performClick(); idle();
        assertFalse(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(id));
        assertEquals("Breathe.", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
        assertEquals(View.GONE, row().findViewById(R.id.buttonPlayOffline).getVisibility());
    }
}
