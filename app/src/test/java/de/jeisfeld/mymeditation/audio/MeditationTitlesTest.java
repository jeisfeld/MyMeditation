package de.jeisfeld.mymeditation.audio;

import android.app.AlertDialog;
import android.widget.EditText;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.testutil.ShadowTitleAi;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

public class MeditationTitlesTest extends BaselineTest {
    private ActivityController<MainActivity> activity;
    @Before public void resetTransientRequests() {
        org.robolectric.util.ReflectionHelpers.<java.util.Map<?, ?>>getStaticField(MeditationTitles.class, "PENDING").clear();
        org.robolectric.util.ReflectionHelpers.<java.util.Map<?, ?>>getStaticField(MeditationTitles.class, "FAILED").clear();
    }
    private int save(String name, String content) { return SavedMeditations.save(name, "s", "@TEXT@", content, "Breathe."); }
    private String suggestion(String content) { return MeditationTitles.suggestion(Application.getAppContext(), content); }
    private EditText manual() {
        activity = Robolectric.buildActivity(MainActivity.class).setup().visible(); idle();
        ((BottomNavigationView) activity.get().findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_saved_meditations); idle();
        activity.get().findViewById(R.id.button_save_current_meditation).performClick(); idle();
        return ((AlertDialog) ShadowDialog.getLatestDialog()).findViewById(R.id.editTextDialog);
    }
    private int callback(String content) { return ShadowTitleAi.contents.indexOf(content); }
    @After public void close() { if (activity != null) { activity.pause().stop().destroy(); idle(); } }

    @Test public void numberedCopiesUseHighestSuffixAndContentProvenanceSurvivesNewTextAndGeneration() {
        int source = save("Wald", "Ein Spaziergang im Wald");
        save("Wald 1", "Other"); save("Wald 2", "Other"); save("Wald 3", "Other");
        SavedAudioLibrary.restore(Application.getAppContext(), source);
        assertEquals("Wald 4", suggestion("Ein Spaziergang im Wald"));
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "New generated text.");
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(Application.getAppContext()).edit()
                .remove("restored_meditation_content_source").commit();
        SavedAudioLibrary.clearSource();
        assertEquals("Wald 4", suggestion("Ein Spaziergang im Wald"));
        assertNotEquals("Wald 4", suggestion("An entirely different topic"));
        int numbered = save("Wald 9", "Topic nine");
        SavedAudioLibrary.restore(Application.getAppContext(), numbered);
        assertEquals("Wald 10", suggestion("Topic nine"));
        assertEquals("Name 2", MeditationTitles.nextNumber("Name"));
        assertEquals("Wald 1000000000000000000000", MeditationTitles.nextNumber("Wald 999999999999999999999"));
        assertTrue(ShadowTitleAi.contents.isEmpty());
    }
    @Test public void titleRequestsCoalesceCacheAndDoNotConfuseDifferentContentOrModels() {
        ShadowTitleAi.deferred = true;
        List<String> first = new ArrayList<>(), second = new ArrayList<>();
        MeditationTitles.request("gpt-test", "Forest A", first::add);
        MeditationTitles.request("gpt-test", "Forest A", first::add);
        MeditationTitles.request("gpt-test", "Sea B", second::add);
        assertEquals(2, ShadowTitleAi.contents.size());
        ShadowTitleAi.callbacks.get(1).complete("Meeresruhe", null);
        ShadowTitleAi.callbacks.get(0).complete("„Waldruhe“", null);
        assertEquals(java.util.Arrays.asList("Waldruhe", "Waldruhe"), first);
        assertEquals(java.util.Arrays.asList("Meeresruhe"), second);
        MeditationTitles.request("gpt-test", "Forest A", first::add); assertEquals(2, ShadowTitleAi.contents.size());
        MeditationTitles.request("claude-test", "Forest A", first::add); assertEquals(3, ShadowTitleAi.contents.size());
        ShadowTitleAi.callbacks.get(2).complete(null, "Offline");
        MeditationTitles.request("claude-test", "Forest A", first::add); assertEquals(3, ShadowTitleAi.contents.size());
        assertEquals(30, MeditationTitles.shortText("😀".repeat(70)).codePointCount(0, MeditationTitles.shortText("😀".repeat(70)).length()));
    }
    @Test public void olderLongTitleCacheIsIgnoredAndNewSuggestionsStayCompact() throws Exception {
        String model = MeditationTitles.model(), content = "Anxiety before a job interview";
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest((model + "\n" + content).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder oldKey = new StringBuilder();
        for (byte value : hash) { oldKey.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); }
        PreferenceUtil.setSharedPreferenceString("meditation_title_cache", new org.json.JSONArray().put(new org.json.JSONObject()
                .put("key", oldKey.toString()).put("title", "Finding inner calm and peace before your next interview")).toString());
        ShadowTitleAi.deferred = true;
        MeditationTitles.prefetch(Application.getAppContext(), content);
        assertEquals(java.util.Arrays.asList(content), ShadowTitleAi.contents);
        ShadowTitleAi.callbacks.get(0).complete("Job interview anxiety", null);
        assertEquals("Job interview anxiety", suggestion(content));
        assertEquals("A very specific topic", MeditationTitles.shortText("A very specific topic with much more unnecessary text"));
        String compact = MeditationTitles.shortText("This title is much longer than the new character limit allows");
        assertTrue(compact.codePointCount(0, compact.length()) <= 30);
    }
    @Test public void manualSavePrefillsNumberedTitleWithoutRequestingAi() {
        int source = save("Evening", "Original evening topic");
        SavedAudioLibrary.restore(Application.getAppContext(), source);
        assertEquals("Evening 2", manual().getText().toString());
        assertTrue(ShadowTitleAi.contents.isEmpty());
    }
    @Test public void manualDialogUsesPrefetchedTitleAndAllowsCustomNames() {
        String content = "Unique forest topic for prefetched manual save";
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, content);
        ShadowTitleAi.deferred = true;
        MeditationTitles.prefetch(Application.getAppContext(), content);
        ShadowTitleAi.callbacks.get(callback(content)).complete("Forest calm", null);
        EditText input = manual(); assertEquals("Forest calm", input.getText().toString());
        input.setText("My chosen title");
        ((AlertDialog) ShadowDialog.getLatestDialog()).getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        List<Integer> ids = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
        assertEquals("My chosen title", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, ids.get(0)));
    }
    @Test public void lateTitleImprovesOnlyUntouchedManualSuggestion() {
        String content = "Unique late manual content about the mountains";
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, content); ShadowTitleAi.deferred = true;
        EditText input = manual(); assertFalse(input.getText().toString().isEmpty());
        ShadowTitleAi.callbacks.get(callback(content)).complete("Mountain stillness", null); idle();
        assertEquals("Mountain stillness", input.getText().toString());
    }
    @Test public void lateTitleNeverOverwritesManualTyping() {
        String content = "Unique manual typing topic";
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, content); ShadowTitleAi.deferred = true;
        EditText input = manual(); input.setText("Hand edited");
        ShadowTitleAi.callbacks.get(callback(content)).complete("AI suggestion", null); idle();
        assertEquals("Hand edited", input.getText().toString());
    }
    @Test public void lateAutomaticTitleUsesCapturedContentAndRespectsRenamingAndDeletion() {
        ShadowTitleAi.deferred = true;
        int id = save("Fallback", "Original automatic content");
        MeditationTitles.improveAutomaticName(id, "Original automatic content", "gpt-test", "Fallback");
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, "New current topic");
        ShadowTitleAi.callbacks.get(0).complete("Calm evening", null);
        assertEquals("Calm evening", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id));
        MeditationTitles.improveAutomaticName(id, "Original automatic content", "other-model", "Calm evening");
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, "User renamed");
        ShadowTitleAi.callbacks.get(1).complete("Another suggestion", null);
        assertEquals("User renamed", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id));
        MeditationTitles.improveAutomaticName(id, "Original automatic content", "third-model", "User renamed");
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, new ArrayList<>());
        ShadowTitleAi.callbacks.get(2).complete("Deleted entry", null);
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
    @Test public void automaticRecordingReceivesAiTitleEvenWhenItFinishesBeforePrefetch() throws Exception {
        String content = "A unique extended topic about finding peace in the forest";
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, content);
        ShadowTitleAi.deferred = true;
        SavedAudioLibrary.Snapshot snapshot = SavedAudioLibrary.Snapshot.current(Application.getAppContext());
        PlaybackRequest request = new PlaybackRequest("Breathe.", de.jeisfeld.mymeditation.http.SpeechBackend.GEMINI,
                "gemini-3.8-flash-tts", "Kore", 1, "", 0);
        SavedAudioLibrary.begin(request);
        SentenceAudioStore.Book book = SentenceAudio.store(Application.getAppContext()).open(request);
        book.obtain(0, file -> {
            try (FileOutputStream output = new FileOutputStream(file); Mp3Writer writer = new Mp3Writer(output)) { writer.silence(0.1, () -> { }); }
        }, () -> { });
        int id = SavedAudioLibrary.keepComplete(Application.getAppContext(), request, snapshot);
        assertTrue(id > 0); assertTrue(SavedAudioLibrary.hasAudio(Application.getAppContext(), id));
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, "Changed while waiting");
        ShadowTitleAi.callbacks.get(callback(content)).complete("Peaceful forest", null);
        assertEquals("Peaceful forest", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id));
        assertEquals(id, SavedAudioLibrary.keepComplete(Application.getAppContext(), request, snapshot));
    }
    @Test public void prefetchDebouncesVisibleTopicEdits() {
        ShadowTitleAi.deferred = true;
        activity = Robolectric.buildActivity(MainActivity.class).setup().visible(); idle();
        EditText content = activity.get().findViewById(R.id.editTextMeditationContent);
        content.setText("Partial");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700));
        content.setText("Complete visible topic");
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1400));
        assertTrue(ShadowTitleAi.contents.isEmpty());
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        assertEquals(java.util.Arrays.asList("Complete visible topic"), ShadowTitleAi.contents);
        ShadowTitleAi.callbacks.get(0).complete("Quiet focus", null);
    }
}
