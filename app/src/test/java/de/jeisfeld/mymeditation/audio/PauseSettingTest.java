package de.jeisfeld.mymeditation.audio;

import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class PauseSettingTest {
    @Test public void percentagesAndSecondsRoundTripInBothUiLanguages() {
        for (Locale locale : new Locale[]{Locale.US, Locale.GERMANY}) {
            for (double value : new double[]{0, 0.5, 0.999, 1, 1.5, 30}) {
                assertEquals(value, PauseSetting.parse(PauseSetting.input(value, locale)), 0.000001);
            }
        }
        assertEquals("50%", PauseSetting.display(0.5, Locale.US));
        assertEquals("1 s", PauseSetting.display(1, Locale.US));
        assertEquals(0.505, PauseSetting.parse("50,5%"), 0);
        assertEquals(1, PauseSetting.parse("100%"), 0);
        assertEquals(0.5, PauseSetting.parse("0.5"), 0);
    }
    @Test public void invalidPercentagesAndSecondsAreRejected() {
        for (String text : new String[]{"101%", "-1%", "NaN", "Infinity%", "31", "", "%%"}) {
            assertThrows(text, IllegalArgumentException.class, () -> PauseSetting.parse(text));
        }
    }
}
