package de.jeisfeld.mymeditation.http;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Direct provider APIs; callbacks run on the main thread. Credentials are never logged. */
public final class AiClient {
    public interface Callback { void complete(String text, String error); }
    private AiClient() { }

    public static void create(String model, String system, String prompt, Callback callback) {
        new Thread(() -> {
            HttpsURLConnection connection = null;
            String text = null;
            String error = null;
            try {
                String modelId = model == null ? "" : model.trim();
                AiProvider provider = AiProvider.forModel(modelId);
                String apiKey = LocalCredentials.read(provider.keyField);
                if (apiKey.isEmpty()) {
                    throw new IllegalStateException("Set HttpCredentials." + provider.keyField + " in your local credentials file, then rebuild the app.");
                }
                JSONObject body = AiProtocol.request(provider, modelId, system, prompt);
                connection = (HttpsURLConnection) new URL(provider.url(modelId)).openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(180000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                if (provider == AiProvider.CLAUDE) {
                    connection.setRequestProperty("x-api-key", apiKey);
                    connection.setRequestProperty("anthropic-version", "2023-06-01");
                } else if (provider == AiProvider.GEMINI) {
                    connection.setRequestProperty("x-goog-api-key", apiKey);
                } else {
                    connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                try (java.io.OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException(provider.displayName + " request failed (HTTP " + status
                            + "). Check your API key, model, credit balance and rate limit.");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) { bytes.write(buffer, 0, count); }
                }
                text = AiProtocol.response(provider, new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            } catch (IllegalArgumentException | IllegalStateException e) {
                error = e.getMessage();
            } catch (Exception e) {
                error = "Could not create meditation. Check your connection and local API key, then try again.";
            } finally {
                if (connection != null) { connection.disconnect(); }
            }
            final String result = text;
            final String failure = error;
            new Handler(Looper.getMainLooper()).post(() -> callback.complete(result, failure));
        }, "ai-meditation").start();
    }
}
