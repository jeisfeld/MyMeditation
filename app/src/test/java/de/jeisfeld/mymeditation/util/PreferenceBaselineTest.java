package de.jeisfeld.mymeditation.util;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;
public class PreferenceBaselineTest extends BaselineTest {
    @Test public void legacySavedIdListsRetainOrderAndCanBeCleared() {
        PreferenceUtil.setSharedPreferenceString(R.string.key_saved_meditation_ids, "17\r\n3\n24");
        assertEquals(Arrays.asList(17, 3, 24), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, Arrays.asList(24, 17, 3));
        assertEquals(Arrays.asList(24, 17, 3), PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
        PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, Collections.emptyList());
        assertNull(PreferenceUtil.getSharedPreferenceString(R.string.key_saved_meditation_ids));
        assertTrue(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).isEmpty());
    }
    @Test public void indexedTextPreservesUnicodeAndLineBreaksWithoutOverwritingOtherEntries() {
        String text = "Atme ruhig.\nSpüre Wärme – 你好。";
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_text, 2, text);
        PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_text, 9, "Other meditation");
        assertEquals(text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, 2));
        PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_text, 2);
        assertFalse(PreferenceUtil.hasIndexedSharedPreference(R.string.key_saved_meditation_text, 2));
        assertEquals("Other meditation", PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, 9));
    }
    @Test public void legacyPausePreferenceRemainsReadableUntilFractionalValueIsStored() {
        assertEquals(15, PreferenceUtil.getPauseSeconds(), 0);
        PreferenceUtil.setSharedPreferenceString("pause_seconds", "broken");
        assertEquals(15, PreferenceUtil.getPauseSeconds(), 0);
        PreferenceUtil.setPauseSeconds(2.5);
        assertEquals(2.5, PreferenceUtil.getPauseSeconds(), 0);
    }
}
