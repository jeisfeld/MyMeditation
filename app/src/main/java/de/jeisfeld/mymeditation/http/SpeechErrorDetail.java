package de.jeisfeld.mymeditation.http;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Bounded provider error text for the local error dialog; never includes the API key. */
final class SpeechErrorDetail {
    private SpeechErrorDetail() { }
    static String read(InputStream stream, String key) {
        if (stream == null) { return ""; }
        try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int count;
            while (bytes.size() < 8192 && (count = input.read(buffer, 0, Math.min(buffer.length, 8192 - bytes.size()))) != -1) {
                bytes.write(buffer, 0, count);
            }
            JSONObject error = new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)).optJSONObject("error");
            if (error == null || !(error.opt("message") instanceof String)) { return ""; }
            String message = error.getString("message");
            if (key != null && !key.isEmpty()) { message = message.replace(key, "[redacted]"); }
            message = message.replaceAll("[\\p{Cntrl}]", " ").trim();
            return message.length() > 500 ? message.substring(0, 499) + "…" : message;
        } catch (Exception ignored) { return ""; } // Keep the original HTTP error when details cannot be read.
    }
}
