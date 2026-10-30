package de.jeisfeld.mymeditation.testutil;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.robolectric.Shadows.shadowOf;
/** Isolated Android preferences and neutral fixtures; never calls a provider API. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "en-rUS-w411dp-h891dp")
@LooperMode(LooperMode.Mode.PAUSED)
public abstract class BaselineTest {
    @Before public void seedLocalPreferences() {
        PreferenceManager.getDefaultSharedPreferences(Application.getAppContext()).edit().clear().commit();
        PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt, "Guide a calm meditation.");
        PreferenceUtil.setSharedPreferenceString(R.string.key_query_template, "Meditate on @TEXT@.");
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, "A quiet garden");
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "Breathe slowly. Relax.");
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, "device");
        PreferenceUtil.setSharedPreferenceInt(R.string.key_pause_duration, 15);
    }
    protected void idle() { shadowOf(Looper.getMainLooper()).idle(); }
}
