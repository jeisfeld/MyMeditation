package de.jeisfeld.mymeditation;

import androidx.preference.PreferenceManager;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS")
public class ApplicationBaselineTest {
    @Test public void firstRunSeedsAllRequiredLegacyFields() {
        Application app = (Application) RuntimeEnvironment.getApplication();
        PreferenceManager.getDefaultSharedPreferences(app).edit().clear().commit();
        app.onCreate();
        assertFalse(PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt).isEmpty());
        assertTrue(PreferenceUtil.getSharedPreferenceString(R.string.key_query_template).contains("@TEXT@"));
        assertFalse(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content).isEmpty());
        assertEquals(15, PreferenceUtil.getSharedPreferenceInt(R.string.key_pause_duration, -1));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }

    @Test public void startupPreservesUserChangesAndOnlyRepairsMissingDefaults() {
        Application app = (Application) RuntimeEnvironment.getApplication();
        PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt, "My instruction");
        PreferenceUtil.setSharedPreferenceString(R.string.key_query_template, "My @TEXT@ template");
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "My saved draft");
        PreferenceUtil.setSharedPreferenceInt(R.string.key_pause_duration, 7);
        PreferenceUtil.removeSharedPreference(R.string.key_meditation_content);
        app.onCreate();
        assertEquals("My instruction", PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
        assertEquals("My @TEXT@ template", PreferenceUtil.getSharedPreferenceString(R.string.key_query_template));
        assertEquals("My saved draft", PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
        assertEquals(7, PreferenceUtil.getSharedPreferenceInt(R.string.key_pause_duration, -1));
        assertNotNull(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
    }
}
