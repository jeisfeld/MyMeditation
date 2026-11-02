package de.jeisfeld.mymeditation.ui.configuration;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.After;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import static org.junit.Assert.*;

public class PromptTemplatesUiTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    private void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible(); activity = controller.get(); idle();
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_configuration); idle();
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }
    private AlertDialog latest() { return (AlertDialog) ShadowDialog.getLatestDialog(); }
    private PromptTemplates library(PromptTemplates.Kind kind) {
        return new PromptTemplates(kind, PreferenceUtil.getSharedPreferenceString(kind.preference, "{}"));
    }
    private EditText input(View view) {
        if (view instanceof EditText) { return (EditText) view; }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                EditText result = input(((ViewGroup) view).getChildAt(i)); if (result != null) { return result; }
            }
        }
        return null;
    }
    private void name(String name) {
        idle(); // Install the dialog's on-show validation handler before simulating a tap.
        AlertDialog dialog = latest(); input(dialog.getWindow().getDecorView()).setText(name);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
    }
    @Test public void savingUsesTheVisibleDraftAndSelectionPersistsWithoutChangingTheOtherPrompt() {
        open(); String text = "  Visible draft.\n🌿 Ruhe.  ";
        EditText editor = activity.findViewById(R.id.editTextSystemPrompt); editor.requestFocus(); editor.setText(text);
        activity.findViewById(R.id.buttonSystemPromptTemplates).performClick(); idle(); AlertDialog manager = latest();
        manager.findViewById(R.id.buttonSavePromptTemplate).performClick(); name("  Calm  ");
        PromptTemplates.Entry saved = library(PromptTemplates.Kind.SYSTEM).list().get(0);
        assertEquals("Calm", saved.name); assertEquals(text, saved.text);
        manager.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        editor.setText("Unsaved edit"); idle();
        Spinner picker = activity.findViewById(R.id.spinnerSystemPromptTemplate);
        assertEquals(0, picker.getSelectedItemPosition());
        picker.setSelection(1); idle();
        assertEquals(text, editor.getText().toString());
        assertEquals(text, PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
        assertEquals("Meditate on @TEXT@.", PreferenceUtil.getSharedPreferenceString(R.string.key_query_template));
        controller.recreate(); activity = controller.get(); idle();
        assertEquals("Calm", ((Spinner) activity.findViewById(R.id.spinnerSystemPromptTemplate)).getSelectedItem().toString());
    }
    @Test public void renameAndConfirmedDeleteKeepTheActiveDraftAndOtherLibrary() {
        PromptTemplates system = library(PromptTemplates.Kind.SYSTEM);
        String id = system.save(null, "Original", "Guide a calm meditation.");
        PreferenceUtil.setSharedPreferenceString(PromptTemplates.Kind.SYSTEM.preference, system.json());
        PromptTemplates query = library(PromptTemplates.Kind.QUERY); query.save(null, "Original", "Meditate on @TEXT@.");
        PreferenceUtil.setSharedPreferenceString(PromptTemplates.Kind.QUERY.preference, query.json());
        open(); activity.findViewById(R.id.buttonSystemPromptTemplates).performClick(); idle(); AlertDialog manager = latest();
        manager.findViewById(R.id.buttonRenamePromptTemplate).performClick(); name("New name");
        assertEquals("New name", library(PromptTemplates.Kind.SYSTEM).get(id).name);
        assertEquals("Guide a calm meditation.", library(PromptTemplates.Kind.SYSTEM).get(id).text);
        manager.findViewById(R.id.buttonDeletePromptTemplate).performClick(); latest().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
        assertNotNull(library(PromptTemplates.Kind.SYSTEM).get(id));
        manager.findViewById(R.id.buttonDeletePromptTemplate).performClick(); latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertTrue(library(PromptTemplates.Kind.SYSTEM).list().isEmpty());
        assertEquals("Guide a calm meditation.", ((EditText) activity.findViewById(R.id.editTextSystemPrompt)).getText().toString());
        assertFalse(activity.findViewById(R.id.spinnerSystemPromptTemplate).isEnabled());
        assertEquals(1, library(PromptTemplates.Kind.QUERY).list().size());
    }
    @Test public void updatingSavedTextRequiresConfirmationAndInvalidQueriesCannotBeSaved() {
        PromptTemplates system = library(PromptTemplates.Kind.SYSTEM);
        String id = system.save(null, "Existing", "Old text"); PreferenceUtil.setSharedPreferenceString(PromptTemplates.Kind.SYSTEM.preference, system.json());
        open(); ((EditText) activity.findViewById(R.id.editTextSystemPrompt)).setText("Updated draft");
        activity.findViewById(R.id.buttonSystemPromptTemplates).performClick(); idle(); AlertDialog manager = latest();
        ((Spinner) manager.findViewById(R.id.spinnerManagedPromptTemplate)).setSelection(1); idle();
        manager.findViewById(R.id.buttonUpdatePromptTemplate).performClick(); latest().getButton(AlertDialog.BUTTON_NEGATIVE).performClick(); idle();
        assertEquals("Old text", library(PromptTemplates.Kind.SYSTEM).get(id).text);
        manager.findViewById(R.id.buttonUpdatePromptTemplate).performClick(); latest().getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        assertEquals("Updated draft", library(PromptTemplates.Kind.SYSTEM).get(id).text);
        manager.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); idle();
        ((EditText) activity.findViewById(R.id.editTextQueryTemplate)).setText("Missing placeholder");
        activity.findViewById(R.id.buttonQueryPromptTemplates).performClick(); idle();
        latest().findViewById(R.id.buttonSavePromptTemplate).performClick(); idle();
        assertEquals(activity.getString(R.string.prompt_template_placeholder_required),
                ((android.widget.TextView) latest().findViewById(android.R.id.message)).getText().toString());
        assertTrue(library(PromptTemplates.Kind.QUERY).list().isEmpty());
    }
    @Test @Config(qualifiers = "de-rDE-w360dp-h780dp") public void germanHeaderControlsFitBesideHeadingsOnANarrowScreen() {
        open(); View container = activity.findViewById(R.id.container);
        container.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(780, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, 360, 780);
        Spinner picker = activity.findViewById(R.id.spinnerSystemPromptTemplate);
        View manage = activity.findViewById(R.id.buttonSystemPromptTemplates);
        assertTrue(picker.getWidth() >= 70); assertTrue(picker.getRight() <= manage.getLeft());
        for (int id : new int[] {R.id.spinnerSystemPromptTemplate, R.id.spinnerQueryPromptTemplate}) {
            Spinner template = activity.findViewById(id);
            android.widget.TextView heading = (android.widget.TextView) ((ViewGroup) template.getParent()).getChildAt(0);
            assertTrue(template.getLeft() > heading.getRight());
            assertEquals(heading.getTop() + heading.getBaseline(), template.getTop() + template.getBaseline());
        }
        assertTrue(activity.findViewById(R.id.editTextSystemPrompt).getHeight() > 0);
        assertTrue(activity.findViewById(R.id.editTextQueryTemplate).getHeight() > 0);
    }
}
