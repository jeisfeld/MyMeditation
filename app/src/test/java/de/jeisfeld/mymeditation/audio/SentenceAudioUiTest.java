package de.jeisfeld.mymeditation.audio;

import android.content.Intent;
import android.view.View;
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
    private boolean publishedPlayback;
    private int recorded() throws Exception {
        return recorded(new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI,
                "gemini-3.8-flash-tts", "Kore", 1.25, "Calm", 5));
    }
    private int recorded(PlaybackRequest request) throws Exception {
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
    private void playbackHeading(int id, boolean active, boolean running) {
        publishedPlayback = true;
        String text = "Breathe slowly. Relax.";
        ((androidx.lifecycle.MutableLiveData<MeditationPlaybackService.State>) MeditationPlaybackService.state()).setValue(
                new MeditationPlaybackService.State(running, 0, SpeechSegments.split(text), "", "", 0,
                        active, text, -1, false, id));
        idle();
    }
    @After public void close() {
        if (controller != null) { controller.pause().stop().destroy(); idle(); }
        if (publishedPlayback) { playbackHeading(0, false, false); }
    }

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
    @Test public void sharePrefillsCurrentPauseAndVoiceProfileSpeedWithoutChangingSettings() throws Exception {
        int id = recorded(); PreferenceUtil.setPauseSeconds(7.5);
        de.jeisfeld.mymeditation.http.VoiceProfiles profiles = new de.jeisfeld.mymeditation.http.VoiceProfiles("{}");
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, SpeechBackend.GEMINI.id);
        PreferenceUtil.setSharedPreferenceString(SpeechBackend.GEMINI.voiceKey(), "Puck");
        profiles.setSpeed(SpeechBackend.GEMINI, "Puck", 1.1);
        PreferenceUtil.setSharedPreferenceString(de.jeisfeld.mymeditation.http.VoiceProfiles.PREFERENCE, profiles.json());
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_speed, "1.8");
        openSaved(); row(0).findViewById(R.id.buttonShareOffline).performClick(); idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        EditText pause = dialog.findViewById(R.id.inputSharePause), speed = dialog.findViewById(R.id.inputShareSpeed);
        assertEquals(7.5, Double.parseDouble(pause.getText().toString().replace(',', '.')), 0);
        assertEquals(1.1, Double.parseDouble(speed.getText().toString().replace(',', '.')), 0);
        assertEquals(View.VISIBLE, dialog.findViewById(R.id.shareSpeedFields).getVisibility());
        pause.setText("31"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing()); assertNotNull(pause.getError());
        pause.setText("2,5"); speed.setText("0"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(dialog.isShowing()); assertNotNull(speed.getError());
        speed.setText("1,2"); dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        Intent start = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertEquals(MeditationExportService.class.getName(), start.getComponent().getClassName());
        assertEquals(id, start.getIntExtra("shareId", 0)); assertEquals(2.5, start.getDoubleExtra("pause", -1), 0);
        assertEquals(1.2, start.getDoubleExtra("speed", -1), 0);
        PlaybackRequest original = SavedAudioLibrary.book(activity, id).request;
        PlaybackRequest export = MeditationExportService.exportRequest(original, start);
        assertEquals(1.2, export.speed, 0); assertEquals(2.5, export.pauseSeconds, 0);
        assertTrue(SentenceAudio.store(activity).open(export).isComplete());
        assertEquals(1.25, original.speed, 0); assertEquals(7.5, PreferenceUtil.getPauseSeconds(), 0);
        assertEquals(1.1, MeditationPlaybackService.currentSpeed(), 0);
        assertEquals("gemini", PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend));
    }
    @Test public void openAiShareKeepsServerGeneratedSpeedAndHidesSpeedInput() throws Exception {
        int id = recorded(new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.OPENAI,
                "gpt-4o-mini-tts", "coral", 0.9, "Calm", 5));
        PreferenceUtil.setPauseSeconds(3); openSaved();
        row(0).findViewById(R.id.buttonShareOffline).performClick(); idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertEquals(View.GONE, dialog.findViewById(R.id.shareSpeedFields).getVisibility());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        Intent start = shadowOf((android.app.Application) Application.getAppContext()).getNextStartedService();
        assertEquals(0.9, start.getDoubleExtra("speed", -1), 0);
        start.putExtra("speed", 1.8);
        PlaybackRequest original = SavedAudioLibrary.book(activity, id).request;
        PlaybackRequest export = MeditationExportService.exportRequest(original, start);
        assertEquals(0.9, export.speed, 0); assertEquals(3, export.pauseSeconds, 0);
        assertEquals(SentenceAudioStore.key(original), SentenceAudioStore.key(export));
    }
    @Test public void offlineHeadingIncludesDurationAndRefreshesForSpeedAndPause() throws Exception {
        int id = recorded(); SavedAudioLibrary.restore(Application.getAppContext(), id);
        SentenceAudioStore.Book book = SavedAudioLibrary.book(Application.getAppContext(), id);
        for (int i = 0; i < book.sentences.size(); i++) {
            org.robolectric.shadows.ShadowMediaMetadataRetriever.addMetadata(book.available(i).getAbsolutePath(),
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION, "10000");
        }
        PreferenceUtil.setPauseSeconds(2);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        String name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id);
        awaitLabel(activity.getString(R.string.label_named_meditation_offline_duration, name, "0:18"));
        SavedAudioLibrary.restoreVoice(book.request.withSpeed(2)); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline_duration, name, "0:12"));
        PreferenceUtil.setPauseSeconds(5); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline_duration, name, "0:15"));
        playbackHeading(id, true, true);
        awaitLabel(activity.getString(R.string.label_named_meditation_offline_duration, name, "0:15"));
    }
    private void publishPosition(java.util.List<String> segments, int position) {
        publishedPlayback = true;
        ((androidx.lifecycle.MutableLiveData<MeditationPlaybackService.State>) MeditationPlaybackService.state()).setValue(
                new MeditationPlaybackService.State(true, position, segments, "", "", 0,
                        true, String.join(" ", segments), -1, false, 0));
        idle();
    }
    @Test public void sentenceCounterKeepsWidthAcrossDigitBoundariesAndExcludesWaits() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        android.widget.TextView counter = activity.findViewById(R.id.textSentencePosition);
        assertEquals("1/2", counter.getText().toString());
        for (int total : new int[]{245, 1001}) {
            java.util.List<String> segments = java.util.Collections.nCopies(total, "Breathe.");
            publishPosition(segments, 0);
            assertEquals("1/" + total, counter.getText().toString());
            counter.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int width = counter.getMeasuredWidth();
            assertTrue(width >= Math.ceil(counter.getPaint().measureText(total + "/" + total)));
            publishPosition(segments, 99);
            assertEquals("100/" + total, counter.getText().toString());
            counter.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            assertEquals(width, counter.getMeasuredWidth());
            publishPosition(segments, total - 1);
            assertEquals(total + "/" + total, counter.getText().toString());
            counter.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            assertEquals(width, counter.getMeasuredWidth());
        }
        publishPosition(java.util.Arrays.asList("One.", "[wait 5s]", "Two."), 1);
        assertEquals("1/2", counter.getText().toString());
        publishPosition(java.util.Arrays.asList("One.", "[wait 5s]", "Two."), 2);
        assertEquals("2/2", counter.getText().toString());
    }
    @Test public void clearingNameFilterRestoresResultsAndPreservesAudioFilterAndSavedOrder() throws Exception {
        int recorded = recorded();
        int textOnly = SavedMeditations.save("Text only", "s", "@TEXT@", "c", "Unrecorded.");
        openSaved();
        assertEquals(View.GONE, activity.findViewById(R.id.buttonClearMeditationFilter).getVisibility());
        ((android.widget.CheckBox) activity.findViewById(R.id.checkBoxAudioOnly)).setChecked(true);
        ((EditText) activity.findViewById(R.id.editTextMeditationFilter)).setText("No matches"); idle();
        assertEquals(0, ((RecyclerView) activity.findViewById(R.id.recyclerViewSavedMeditations)).getAdapter().getItemCount());
        assertEquals(View.VISIBLE, activity.findViewById(R.id.buttonClearMeditationFilter).getVisibility());
        controller.recreate(); activity = controller.get(); idle();
        assertEquals("No matches", ((EditText) activity.findViewById(R.id.editTextMeditationFilter)).getText().toString());
        activity.findViewById(R.id.buttonClearMeditationFilter).performClick(); idle();
        assertEquals("", ((EditText) activity.findViewById(R.id.editTextMeditationFilter)).getText().toString());
        assertEquals(View.GONE, activity.findViewById(R.id.buttonClearMeditationFilter).getVisibility());
        assertTrue(((android.widget.CheckBox) activity.findViewById(R.id.checkBoxAudioOnly)).isChecked());
        assertEquals(1, ((RecyclerView) activity.findViewById(R.id.recyclerViewSavedMeditations)).getAdapter().getItemCount());
        ((android.widget.CheckBox) activity.findViewById(R.id.checkBoxAudioOnly)).setChecked(false); idle();
        assertEquals(2, ((RecyclerView) activity.findViewById(R.id.recyclerViewSavedMeditations)).getAdapter().getItemCount());
        assertEquals(java.util.Arrays.asList(recorded, textOnly), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
    }
    private void awaitLabel(String expected) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        android.widget.TextView label = activity.findViewById(R.id.labelMeditationText);
        while (!expected.equals(label.getText().toString()) && System.nanoTime() < deadline) { idle(); Thread.sleep(2); }
        assertEquals(expected, label.getText().toString());
    }
    @Test public void savedPlaybackHeadingUsesEntryNameWhilePlayingAndPausedAndTracksRename() throws Exception {
        int first = recorded(); SavedAudioLibrary.restore(Application.getAppContext(), first);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, first, "Abendruhe 🌙");
        int second = SavedMeditations.save("Morning", PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt),
                PreferenceUtil.getSharedPreferenceString(R.string.key_query_template),
                PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content), "Breathe slowly. Relax.");
        openSaved();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_meditation); idle();
        playbackHeading(first, true, true);
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, "Abendruhe 🌙"));
        playbackHeading(first, true, false);
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, "Abendruhe 🌙"));
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, first, "Evening"); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, "Evening"));
        playbackHeading(second, true, true);
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, "Morning"));
        playbackHeading(0, true, true);
        awaitLabel(activity.getString(R.string.label_meditation_text_offline));
        playbackHeading(first, false, false);
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, "Evening"));
    }
    @Test public void savedTextWithDeviceSpeechUsesItsNameWithoutOfflineSuffixAndHandlesDeletion() throws Exception {
        int id = SavedMeditations.save("Quiet garden", "s", "@TEXT@", "c", "Breathe slowly. Relax.");
        openSaved();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_meditation); idle();
        playbackHeading(id, true, true); awaitLabel("Quiet garden");
        playbackHeading(id, true, false); awaitLabel("Quiet garden");
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, " "); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text));
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, new java.util.ArrayList<>()); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text));
    }
    @Test public void loadedTextKeepsSavedHeadingUntilEditedAndUsesSelectedEntryForIdenticalTexts() throws Exception {
        int first = SavedMeditations.save("First name", "s", "@TEXT@", "c", "Breathe slowly. Relax.");
        SavedMeditations.save("Second name", "s", "@TEXT@", "c", "Breathe slowly. Relax.");
        openSaved();
        row(1).findViewById(R.id.textViewMeditationName).performClick(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_meditation); idle();
        awaitLabel("Second name");
        PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt, "New instructions");
        PreferenceUtil.setSharedPreferenceString(MeditationPauses.PREFERENCE, "none"); idle();
        awaitLabel("Second name");
        EditText editor = activity.findViewById(R.id.editTextMeditationText);
        editor.setText("Edited text, not yet saved to preferences."); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text));
        editor.setText("Breathe slowly. Relax."); idle(); awaitLabel("Second name");
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_saved_meditations); idle();
        row(0).findViewById(R.id.textViewMeditationName).performClick(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_meditation); idle();
        awaitLabel("First name");
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, first, "Renamed"); idle();
        awaitLabel("Renamed");
        controller.recreate(); activity = controller.get(); idle(); awaitLabel("Renamed");
        SavedAudioLibrary.clearSource(); idle(); awaitLabel(activity.getString(R.string.label_meditation_text));
    }
    @Test public void offlineHeadingTracksExactVoiceSpeedAndTextButIgnoresPauses() throws Exception {
        int id = recorded(); SavedAudioLibrary.restore(Application.getAppContext(), id);
        String name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, name));
        PreferenceUtil.setPauseSeconds(0.5); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, name));
        SavedAudioLibrary.restoreVoice(new PlaybackRequest("Breathe slowly. Relax.", SpeechBackend.GEMINI,
                "gemini-3.8-flash-tts", "Kore", 1.5, "Calm", 0)); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, name));
        SavedAudioLibrary.restore(Application.getAppContext(), id); idle();
        awaitLabel(activity.getString(R.string.label_named_meditation_offline, name));
        ((EditText) activity.findViewById(R.id.editTextMeditationText)).setText("Different meditation."); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text));
    }
    @Test public void newScriptBuiltFromStoredSentencesIsImmediatelyShownOfflineBeforePlayback() throws Exception {
        int id = recorded(); SavedAudioLibrary.restore(Application.getAppContext(), id);
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((EditText) activity.findViewById(R.id.editTextMeditationText)).setText("Relax.[wait 10s]Breathe slowly.Relax."); idle();
        awaitLabel(activity.getString(R.string.label_meditation_text_offline));
        assertFalse(MeditationPlaybackService.state().getValue().active);
    }

}
