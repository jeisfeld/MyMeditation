package de.jeisfeld.mymeditation.http;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeechErrorDetailTest {
    private ByteArrayInputStream body(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
    @Test public void extractsUsefulFieldErrorAndRedactsTheKey() throws Exception {
        String json = new JSONObject().put("error", new JSONObject().put("code", 400)
                .put("message", "Unsupported sample_rate.\nKey: secret-key")).toString();
        assertEquals("Unsupported sample_rate. Key: [redacted]", SpeechErrorDetail.read(body(json), "secret-key"));
    }
    @Test public void missingOrInvalidErrorDetailsKeepTheGenericHttpMessage() {
        for (String text : new String[] {"<html>Bad request</html>", "{}", "{\"error\":{\"message\":12}}", "{bad"}) {
            assertEquals("", SpeechErrorDetail.read(body(text), ""));
        }
        assertEquals("", SpeechErrorDetail.read(null, ""));
    }
    @Test public void readsAndDisplaysOnlyBoundedErrorText() throws Exception {
        String message = String.join("", java.util.Collections.nCopies(1000, "x"));
        String json = new JSONObject().put("error", new JSONObject().put("message", message)).toString();
        String result = SpeechErrorDetail.read(body(json), "");
        assertEquals(500, result.length());
        assertTrue(result.endsWith("…"));
        ByteArrayInputStream huge = body(String.join("", java.util.Collections.nCopies(9000, "x")));
        assertEquals("", SpeechErrorDetail.read(huge, ""));
        assertEquals(808, huge.available());
    }
}
