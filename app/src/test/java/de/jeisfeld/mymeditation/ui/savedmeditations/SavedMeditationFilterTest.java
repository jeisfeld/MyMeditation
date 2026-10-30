package de.jeisfeld.mymeditation.ui.savedmeditations;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class SavedMeditationFilterTest {
    private final List<Integer> ids = Arrays.asList(8, 3, 12, 5);
    private final Map<Integer, String> names = new HashMap<>();
    public SavedMeditationFilterTest() {
        names.put(8, "Morning calm");
        names.put(3, "Evening Calm");
        names.put(12, "Stärke und Ruhe");
        names.put(5, "Calm focus");
    }

    @Test public void nameAndAudioFiltersCombineWithoutChangingStoredIdsOrOrder() {
        assertEquals(Arrays.asList(8, 3, 5), SavedMeditationFilter.select(ids, "  cAlM  ", false, names::get, id -> false));
        assertEquals(Arrays.asList(8, 5), SavedMeditationFilter.select(ids, "calm", true, names::get, id -> id != 3));
        assertEquals(Arrays.asList(8, 3, 12, 5), ids);
        assertEquals(ids, SavedMeditationFilter.select(ids, " ", false, names::get, id -> false));
    }

    @Test public void caseMatchingIsIndependentOfDeviceLocaleAndSupportsGermanNames() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertEquals(Arrays.asList(3), SavedMeditationFilter.select(ids, "EVENING", false, names::get, id -> false));
            assertEquals(Arrays.asList(12), SavedMeditationFilter.select(ids, "STÄRKE", false, names::get, id -> false));
        } finally { Locale.setDefault(previous); }
    }

    @Test public void renamingChangesMatchesWhileKeepingTheSameIdAndAudioAssociation() {
        assertEquals(Arrays.asList(3), SavedMeditationFilter.select(ids, "evening", true, names::get, id -> id == 3));
        names.put(3, "Bedtime relaxation");
        assertTrue(SavedMeditationFilter.select(ids, "evening", true, names::get, id -> id == 3).isEmpty());
        assertEquals(Arrays.asList(3), SavedMeditationFilter.select(ids, "bedtime", true, names::get, id -> id == 3));
        assertEquals(Arrays.asList(8, 3, 12, 5), ids);
    }

    @Test public void audioOnlyUsesFilePresenceAndMissingNamesAreSafe() {
        names.remove(8);
        assertEquals(Arrays.asList(8, 12), SavedMeditationFilter.select(ids, "", true, names::get, id -> id == 8 || id == 12));
        assertEquals(Arrays.asList(3, 5), SavedMeditationFilter.select(ids, "calm", false, names::get, id -> false));
        assertTrue(SavedMeditationFilter.select(ids, "unmatched", false, names::get, id -> true).isEmpty());
    }
}
