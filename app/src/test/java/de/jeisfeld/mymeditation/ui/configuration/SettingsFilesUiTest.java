package de.jeisfeld.mymeditation.ui.configuration;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;
import static org.robolectric.Shadows.shadowOf;
import static org.junit.Assert.*;

public class SettingsFilesUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_configuration); idle();
    }
    private AlertDialog dialog() { return (AlertDialog) ShadowDialog.getLatestDialog(); }
    private AlertDialog additional() { activity.findViewById(R.id.buttonAiSettings).performClick(); idle(); return dialog(); }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + 5000000000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) { idle(); Thread.sleep(2); }
        assertTrue(condition.getAsBoolean());
    }
    private void chooseImport(String json) {
        additional().findViewById(R.id.buttonImportSettings).performClick(); idle();
        ShadowActivity.IntentForResult picker = shadowOf(activity).getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.intent.getAction());
        Uri uri = Uri.parse("content://settings-test/backup.json");
        shadowOf(activity.getContentResolver()).registerInputStream(uri, new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
        shadowOf(activity).receiveResult(picker.intent, Activity.RESULT_OK, new Intent().setData(uri)); idle();
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }

    @Test public void bottomButtonOpensAdditionalSettingsAndExportIncludesVisibleDraftsAcrossRotation() throws Exception {
        open();
        assertEquals(activity.getString(R.string.additional_settings), ((android.widget.TextView) activity.findViewById(R.id.buttonAiSettings)).getText().toString());
        ((EditText) activity.findViewById(R.id.editTextSystemPrompt)).setText("Visible prompt 🌿");
        AlertDialog settings = additional();
        ((EditText) settings.findViewById(R.id.editTextAiModel)).setText("gpt-4.1");
        ((EditText) settings.findViewById(R.id.editTextSpeechStyle)).setText("Exported delivery");
        settings.findViewById(R.id.buttonExportSettings).performClick(); idle();
        ShadowActivity.IntentForResult picker = shadowOf(activity).getNextStartedActivityForResult();
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, picker.intent.getAction());
        assertEquals("application/json", picker.intent.getType());
        assertNotEquals("gpt-4.1", PreferenceUtil.getSharedPreferenceString(R.string.key_ai_model));
        controller.recreate(); activity = controller.get(); idle();
        Uri uri = Uri.parse("content://settings-test/export.json");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        shadowOf(activity.getContentResolver()).registerOutputStream(uri, output);
        shadowOf(activity).receiveResult(picker.intent, Activity.RESULT_OK, new Intent().setData(uri)); idle();
        await(() -> activity.getString(R.string.settings_exported).equals(ShadowToast.getTextOfLatestToast()));
        Map<String, String> exported = SettingsBackup.decode(output.toString(StandardCharsets.UTF_8.name()));
        assertEquals("Visible prompt 🌿", exported.get("system_prompt"));
        assertEquals("gpt-4.1", exported.get("claude_model"));
        assertEquals("Exported delivery", exported.get("speech_style"));
    }
    @Test public void importRequiresConfirmationAndRefreshesVisiblePromptsAndTemplates() throws Exception {
        open();
        Map<String, String> values = SettingsBackup.capture(activity);
        values.put("claude_model", "gpt-imported-custom");
        PreferenceUtil.setSharedPreferenceString("claude_model", "gpt-local-custom");
        values.put("system_prompt", "Imported instruction"); values.put("query_template", "Imported @TEXT@");
        PromptTemplates templates = new PromptTemplates(PromptTemplates.Kind.SYSTEM, "{}");
        templates.save(null, "Imported template", "Imported instruction"); values.put(PromptTemplates.Kind.SYSTEM.preference, templates.json());
        PromptTemplates local = new PromptTemplates(PromptTemplates.Kind.SYSTEM, "{}");
        local.save(null, "Keep local", "Local instruction");
        PreferenceUtil.setSharedPreferenceString(PromptTemplates.Kind.SYSTEM.preference, local.json());
        String original = PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt);
        // Import must remain accessible even when a draft query needs repair.
        ((EditText) activity.findViewById(R.id.editTextQueryTemplate)).setText("Invalid unsaved query");
        chooseImport(SettingsBackup.encode(values));
        await(() -> dialog() != null && dialog().isShowing());
        assertEquals(original, PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
        dialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertEquals("Imported instruction", ((EditText) activity.findViewById(R.id.editTextSystemPrompt)).getText().toString());
        assertEquals("Imported @TEXT@", ((EditText) activity.findViewById(R.id.editTextQueryTemplate)).getText().toString());
        assertEquals(2, new PromptTemplates(PromptTemplates.Kind.SYSTEM,
                PreferenceUtil.getSharedPreferenceString(PromptTemplates.Kind.SYSTEM.preference, "{}")).list().size());
        assertEquals("Imported template", ((android.widget.Spinner) activity.findViewById(R.id.spinnerSystemPromptTemplate)).getSelectedItem().toString());
        android.widget.Spinner models = activity.findViewById(R.id.spinnerAiModelPreset);
        java.util.List<String> choices = new java.util.ArrayList<>();
        for (int i = 0; i < models.getCount(); i++) { choices.add(models.getItemAtPosition(i).toString()); }
        assertTrue(choices.contains("gpt-local-custom")); assertTrue(choices.contains("gpt-imported-custom"));
        assertEquals("gpt-imported-custom", models.getSelectedItem().toString());
    }
    @Test public void cancellationAndInvalidFilesLeaveSettingsUnchanged() throws Exception {
        open(); Map<String, String> original = SettingsBackup.capture(activity);
        Map<String, String> changed = new java.util.TreeMap<>(original); changed.put("system_prompt", "Discard me");
        chooseImport(SettingsBackup.encode(changed)); await(() -> dialog() != null && dialog().isShowing());
        dialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle(); assertEquals(original, SettingsBackup.capture(activity));
        chooseImport("{invalid");
        await(() -> activity.getString(R.string.settings_import_error).equals(ShadowToast.getTextOfLatestToast()));
        assertEquals(original, SettingsBackup.capture(activity));
    }
}
