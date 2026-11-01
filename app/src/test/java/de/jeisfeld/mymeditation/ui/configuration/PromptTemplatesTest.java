package de.jeisfeld.mymeditation.ui.configuration;

import org.junit.Test;
import static org.junit.Assert.*;

public class PromptTemplatesTest {
    @Test public void roundTripRetainsUnicodeWhitespaceAndStableIdsAcrossRenameAndUpdate() {
        PromptTemplates library = new PromptTemplates(PromptTemplates.Kind.SYSTEM, "{}");
        String text = "  Ein ruhiger Ort.\n🌿 Ruhe.  ";
        String id = library.save(null, "  Quiet  ", text);
        library.rename(id, "Renamed");
        PromptTemplates restored = new PromptTemplates(PromptTemplates.Kind.SYSTEM, library.json());
        assertEquals("Renamed", restored.get(id).name); assertEquals(text, restored.get(id).text);
        assertEquals(id, restored.save(id, "Renamed", "Different text"));
        assertEquals("Different text", restored.get(id).text);
        assertEquals(text, library.get(id).text);
    }
    @Test public void namesAreUniqueIgnoringCaseAndBlankNamesOrTextsCannotOverwriteEntries() {
        PromptTemplates library = new PromptTemplates(PromptTemplates.Kind.SYSTEM, "{}");
        String first = library.save(null, "First", "One"), second = library.save(null, "Second", "Two");
        assertEquals(PromptTemplates.Problem.DUPLICATE_NAME,
                assertThrows(PromptTemplates.Invalid.class, () -> library.rename(second, " FIRST ")).problem);
        assertThrows(PromptTemplates.Invalid.class, () -> library.save(first, " ", "Changed"));
        assertThrows(PromptTemplates.Invalid.class, () -> library.save(first, "First", "\n "));
        assertEquals("One", library.get(first).text); assertEquals("Second", library.get(second).name);
    }
    @Test public void queryTemplatesRequireTheLiteralPlaceholderAndRejectInvalidUpdates() {
        PromptTemplates library = new PromptTemplates(PromptTemplates.Kind.QUERY, "{}");
        String id = library.save(null, "Query", "Please meditate on @TEXT@.");
        assertEquals(PromptTemplates.Problem.PLACEHOLDER_REQUIRED,
                assertThrows(PromptTemplates.Invalid.class, () -> library.save(id, "Query", "No placeholder")).problem);
        assertEquals("Please meditate on @TEXT@.", library.get(id).text);
        assertEquals(1, new PromptTemplates(PromptTemplates.Kind.QUERY, library.json()).list().size());
    }
    @Test public void removingAndSortingEntriesDoesNotChangeRemainingIdsOrText() {
        PromptTemplates library = new PromptTemplates(PromptTemplates.Kind.SYSTEM, "{}");
        String z = library.save(null, "Zulu", "z"), a = library.save(null, "Alpha", "a");
        assertEquals(a, library.list().get(0).id); library.remove(a); library.remove(a);
        assertEquals(z, library.list().get(0).id); assertEquals("z", library.get(z).text);
        assertThrows(PromptTemplates.Invalid.class, () -> library.save(a, "Missing", "text"));
    }
    @Test public void malformedEntriesDoNotHideOtherValidTemplatesAndKindsValidateIndependently() {
        String json = "{\"version\":1,\"templates\":[{}, {\"id\":\"a\",\"name\":\"Valid\",\"text\":\"No placeholder\"}]}";
        assertEquals(1, new PromptTemplates(PromptTemplates.Kind.SYSTEM, json).list().size());
        assertTrue(new PromptTemplates(PromptTemplates.Kind.QUERY, json).list().isEmpty());
        assertTrue(new PromptTemplates(PromptTemplates.Kind.SYSTEM, "broken").list().isEmpty());
    }
}
