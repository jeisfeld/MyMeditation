package de.jeisfeld.mymeditation.ui.meditation;

import org.junit.Test;
import static org.junit.Assert.*;

public class MeditationPromptTest {
    @Test public void replacesEveryPlaceholderWithLiteralUserText() {
        String content = "Ruhe – 你好\n$5 \\ path @TEXT@";
        assertEquals("Start " + content + ". Finish " + content + ".",
                MeditationPrompt.render("Start @TEXT@. Finish @TEXT@.", content));
    }
    @Test public void missingTemplateFallsBackToTheCurrentContent() {
        assertEquals("Current, freshly edited topic", MeditationPrompt.render(null, "Current, freshly edited topic"));
    }
    @Test public void emptyContentAndPlaceholderFreeLegacyTemplatesAreRetained() {
        assertEquals("Begin .", MeditationPrompt.render("Begin @TEXT@.", ""));
        assertEquals("Legacy literal template", MeditationPrompt.render("Legacy literal template", "Ignored"));
    }
}
