package de.jeisfeld.mymeditation.http;

import android.content.Intent;
import android.os.PowerManager;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.testutil.ShadowTitleAi;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@Config(shadows = MeditationGenerationServiceTest.ShadowGenerationAi.class)
public class MeditationGenerationServiceTest extends BaselineTest {
    @Implements(value = AiClient.class, isInAndroidSdk = false)
    public static class ShadowGenerationAi extends ShadowTitleAi {
        public static final List<AiClient.Callback> generations = new ArrayList<>();
        static int cancellations;
        @Implementation public static AiClient.Call create(String model, String system, String prompt, AiClient.Callback callback) {
            generations.add(callback); return () -> cancellations++;
        }
    }
    private ServiceController<MeditationGenerationService> service;
    private ActivityController<MainActivity> activity;
    @Before public void reset() { ShadowGenerationAi.generations.clear(); ShadowGenerationAi.cancellations = 0; MeditationGenerationService.state(); }
    @After public void close() {
        if (activity != null) { activity.pause().stop().destroy(); }
        if (service != null) { service.destroy(); }
        idle();
    }
    private Intent command() { return new Intent(Application.getAppContext(), MeditationGenerationService.class)
            .putExtra("model", "gpt-test").putExtra("system", "Instructions").putExtra("prompt", "Topic"); }
    private void start() { service = Robolectric.buildService(MeditationGenerationService.class).create(); service.get().onStartCommand(command(), 0, 1); idle(); }
    private MeditationGenerationService.State state() { return MeditationGenerationService.state().getValue(); }
    private PowerManager.WakeLock lock() { return ReflectionHelpers.getField(service.get(), "wakeLock"); }
    private void open() { activity = Robolectric.buildActivity(MainActivity.class).setup().visible(); idle(); }
    @Test public void generationSurvivesActivityRecreationAndCompletionWhileBackgrounded() {
        start(); open();
        assertTrue(state().active); assertTrue(lock().isHeld());
        assertNull(((EditText) activity.get().findViewById(R.id.editTextMeditationText)).getKeyListener());
        assertFalse(((Button) activity.get().findViewById(R.id.buttonCreateMeditation)).isEnabled());
        activity.recreate(); idle();
        assertNull(((EditText) activity.get().findViewById(R.id.editTextMeditationText)).getKeyListener());
        assertFalse(((Button) activity.get().findViewById(R.id.buttonCreateMeditation)).isEnabled());
        activity.pause().stop();
        ShadowGenerationAi.generations.get(0).complete("Long generated meditation.", null); idle();
        assertEquals("Long generated meditation.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        assertFalse(lock().isHeld());
        activity.restart().start().resume().visible(); idle();
        assertEquals("Long generated meditation.", ((EditText) activity.get().findViewById(R.id.editTextMeditationText)).getText().toString());
        assertTrue(((Button) activity.get().findViewById(R.id.buttonCreateMeditation)).isEnabled());
        assertNotNull(((EditText) activity.get().findViewById(R.id.editTextMeditationText)).getKeyListener());
        assertEquals(1, ShadowGenerationAi.generations.size());
        assertTrue(state().resultId.isEmpty());
    }
    @Test public void backgroundErrorIsDisplayedOnReturnAndPreservesDraft() {
        start(); open(); activity.pause().stop();
        ShadowGenerationAi.generations.get(0).complete(null, "Timed out while waiting"); idle();
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        activity.restart().start().resume().visible(); idle();
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue(dialog.isShowing());
        assertEquals("Timed out while waiting", ((android.widget.TextView) dialog.findViewById(android.R.id.message)).getText().toString());
        assertFalse(lock().isHeld()); assertTrue(state().resultId.isEmpty());
        assertNotNull(((EditText) activity.get().findViewById(R.id.editTextMeditationText)).getKeyListener());
    }
    @Test public void duplicateStartsDoNotSubmitAnotherBilledRequestAndCancellationIgnoresLateResult() {
        start(); service.get().onStartCommand(command(), 0, 2);
        assertEquals(1, ShadowGenerationAi.generations.size());
        service.get().onStartCommand(new Intent().setAction("generation.CANCEL"), 0, 3);
        assertFalse(state().active); assertFalse(lock().isHeld()); assertEquals(1, ShadowGenerationAi.cancellations);
        ShadowGenerationAi.generations.get(0).complete("Late result", null); idle();
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        assertNotNull(state().error);
        service.get().onStartCommand(command(), 0, 4);
        assertEquals(2, ShadowGenerationAi.generations.size());
        ShadowGenerationAi.generations.get(0).complete("Stale result from cancelled request", null); idle();
        assertTrue(state().active);
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        ShadowGenerationAi.generations.get(1).complete("New meditation.", null); idle();
        assertFalse(state().active);
        assertEquals("New meditation.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
    }
    @Test public void unexpectedServiceTerminationPreservesDraftAndReportsInterruption() {
        start(); service.destroy(); service = null;
        assertFalse(state().active); assertNotNull(state().error);
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        ShadowGenerationAi.generations.get(0).complete("Late result", null); idle();
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
    }
    @Test public void overallDeadlineCancelsTheConnectionAndReleasesForegroundResources() {
        start();
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(12));
        assertFalse(state().active); assertFalse(lock().isHeld());
        assertEquals(1, ShadowGenerationAi.cancellations);
        assertEquals(de.jeisfeld.mymeditation.i18n.UiText.AI_TIMEOUT.text(), state().error);
        assertTrue(org.robolectric.Shadows.shadowOf(service.get()).isForegroundStopped());
        assertEquals("Breathe slowly. Relax.", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
    }
    @Test public void processDeathMarkerBecomesAnErrorWithoutResubmittingRequest() {
        PreferenceUtil.setSharedPreferenceString("meditation_generation_result", "{\"active\":true}");
        assertFalse(state().active); assertNotNull(state().error);
        assertEquals(0, ShadowGenerationAi.generations.size());
    }
}
