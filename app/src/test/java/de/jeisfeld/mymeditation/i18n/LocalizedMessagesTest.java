package de.jeisfeld.mymeditation.i18n;

import de.jeisfeld.mymeditation.R;
import java.util.Locale;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalizedMessagesTest {
    @Test public void everyRuntimeMessageResolvesToItsAndroidResource() throws Exception {
        for (UiText message : UiText.values()) {
            int expected = R.string.class.getField("message_" + message.name().toLowerCase(Locale.ROOT)).getInt(null);
            assertNotEquals(message.name(), 0, expected);
            assertEquals(message.name(), expected, LocalizedMessages.resource(message));
        }
    }
}
