package de.jeisfeld.mymeditation.ui.configuration;

import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigurationBaselineTest extends BaselineTest {
    @Test public void editsAreStoredAndLoadedByANewViewModel() {
        ConfigurationViewModel model = new ConfigurationViewModel();
        model.setSystemPrompt("Calm guidance.\nUse short sentences.");
        model.setQueryTemplate("Begin with @TEXT@. Return to @TEXT@ at the end.");
        idle();
        ConfigurationViewModel reopened = new ConfigurationViewModel();
        assertEquals("Calm guidance.\nUse short sentences.", reopened.getSystemPrompt().getValue());
        assertEquals("Begin with @TEXT@. Return to @TEXT@ at the end.", reopened.getQueryTemplate().getValue());
    }

    @Test public void templateWithoutPlaceholderDoesNotReplaceTheLastValidTemplate() {
        ConfigurationViewModel model = new ConfigurationViewModel();
        String original = model.getQueryTemplate().getValue();
        for (String invalid : new String[] {"", "No placeholder", "@text@"}) {
            model.setQueryTemplate(invalid);
            idle();
            assertEquals(original, model.getQueryTemplate().getValue());
            assertEquals(original, PreferenceUtil.getSharedPreferenceString(R.string.key_query_template));
        }
    }

    @Test public void restoringSavedSettingsRefreshesAnAlreadyExistingViewModel() {
        ConfigurationViewModel model = new ConfigurationViewModel();
        PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt, "Restored instruction");
        PreferenceUtil.setSharedPreferenceString(R.string.key_query_template, "Restored @TEXT@");
        model.getStoredData();
        assertEquals("Restored instruction", model.getSystemPrompt().getValue());
        assertEquals("Restored @TEXT@", model.getQueryTemplate().getValue());
    }
}
