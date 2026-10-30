package de.jeisfeld.mymeditation.ui.meditation;

import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeditationBaselineTest extends BaselineTest {
    @Test public void topicAndMeditationTextRoundTripThroughPreferences() {
        MeditationViewModel model = new MeditationViewModel();
        model.setMeditationContent("Wärme und Ruhe");
        model.setMeditationText("Atme ein.\nAtme aus.\n休息。");
        idle();
        MeditationViewModel reopened = new MeditationViewModel();
        assertEquals("Wärme und Ruhe", reopened.getMeditationContent().getValue());
        assertEquals("Atme ein.\nAtme aus.\n休息。", reopened.getMeditationText().getValue());
    }

    @Test public void pauseEditsReplaceTheLegacyPauseValueAndRemainAfterReopening() {
        MeditationViewModel model = new MeditationViewModel();
        assertEquals(15, model.getPauseDuration().getValue(), 0);
        model.setPauseDuration(0.5);
        idle();
        assertEquals(0.5, new MeditationViewModel().getPauseDuration().getValue(), 0);
        model.setPauseDuration(0);
        idle();
        assertEquals(0, new MeditationViewModel().getPauseDuration().getValue(), 0);
    }

    @Test public void restoringSavedMeditationRefreshesCurrentDraftWithoutStartingPlayback() {
        MeditationViewModel model = new MeditationViewModel();
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, "Restored topic");
        PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, "Restored meditation.");
        model.getStoredData();
        assertEquals("Restored topic", model.getMeditationContent().getValue());
        assertEquals("Restored meditation.", model.getMeditationText().getValue());
        assertFalse(model.isMeditationRunning().getValue());
    }
}
