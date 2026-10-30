package de.jeisfeld.mymeditation;

import android.view.View;
import android.widget.EditText;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

/** Exercise IME insets and real page layouts, including dismissal with the editor still focused. */
public class KeyboardEditingLayoutTest extends BaselineTest {
    private ActivityController<MainActivity> controller;
    private MainActivity activity;
    @Before public void open() {
        controller = Robolectric.buildActivity(MainActivity.class).setup().visible();
        activity = controller.get(); idle();
    }
    @After public void close() { if (controller != null) { controller.pause().stop().destroy(); idle(); } }
    private void keyboard(boolean shown) {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
                .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, shown ? 320 : 0))
                .setVisible(WindowInsetsCompat.Type.ime(), shown).build();
        View container = activity.findViewById(R.id.container);
        ViewCompat.dispatchApplyWindowInsets(container, insets);
        idle();
        container.measure(View.MeasureSpec.makeMeasureSpec(411, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        container.layout(0, 0, 411, 800);
    }
    private void visible(int id, boolean expected) {
        assertEquals(expected ? View.VISIBLE : View.GONE, activity.findViewById(id).getVisibility());
    }
    @Test public void meditationEditorFillsKeyboardSpaceAndRestoresControlsOnDismissal() {
        EditText editor = activity.findViewById(R.id.editTextMeditationText);
        editor.requestFocus(); editor.setText("An edited meditation."); editor.setSelection(7);
        keyboard(true);
        visible(R.id.nav_view, false); visible(R.id.meditationContentSection, false);
        visible(R.id.buttonCreateMeditation, false); visible(R.id.speechSettingsSection, false);
        visible(R.id.meditationPlaybackSection, false); visible(R.id.meditationTextSection, true);
        assertTrue(editor.getHeight() > 200);
        View page = (View) editor.getParent().getParent();
        assertEquals(320, page.getPaddingBottom());
        assertTrue(editor.hasFocus()); assertEquals(7, editor.getSelectionStart());
        keyboard(false);
        visible(R.id.nav_view, true); visible(R.id.meditationContentSection, true);
        visible(R.id.buttonCreateMeditation, true); visible(R.id.speechSettingsSection, true);
        visible(R.id.meditationPlaybackSection, true);
        assertEquals("An edited meditation.", editor.getText().toString());
    }
    @Test public void topicEditorAlsoGetsTheAvailablePage() {
        activity.findViewById(R.id.editTextMeditationContent).requestFocus(); keyboard(true);
        visible(R.id.meditationContentSection, true); visible(R.id.meditationTextSection, false);
        visible(R.id.meditationPlaybackSection, false);
        keyboard(false); visible(R.id.meditationTextSection, true);
    }
    @Test public void queryAndSystemEditorsExpandIndependentlyAndKeepFocusSaveBehavior() {
        ((BottomNavigationView) activity.findViewById(R.id.nav_view)).setSelectedItemId(R.id.navigation_configuration); idle();
        EditText query = activity.findViewById(R.id.editTextQueryTemplate);
        query.requestFocus(); query.setText("New request: @TEXT@"); keyboard(true);
        visible(R.id.nav_view, false); visible(R.id.queryTemplateSection, true);
        visible(R.id.systemPromptSection, false); visible(R.id.inlineAiSettingsSection, false);
        assertTrue(query.getHeight() > 200);
        keyboard(false);
        visible(R.id.systemPromptSection, true); visible(R.id.inlineAiSettingsSection, true);
        EditText system = activity.findViewById(R.id.editTextSystemPrompt); system.requestFocus(); keyboard(true);
        visible(R.id.systemPromptSection, true); visible(R.id.queryTemplateSection, false);
        assertEquals("New request: @TEXT@", de.jeisfeld.mymeditation.util.PreferenceUtil.getSharedPreferenceString(R.string.key_query_template));
    }
}
