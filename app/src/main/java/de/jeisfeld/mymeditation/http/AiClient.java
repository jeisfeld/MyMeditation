package de.jeisfeld.mymeditation.http;

import de.jeisfeld.mymeditation.i18n.UiText;
import de.jeisfeld.mymeditation.audio.MeditationPauses;
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
    public interface Call { void cancel(); }
    static final int MEDITATION_READ_TIMEOUT = 10 * 60 * 1000;
    private AiClient() { }

    public static Call create(String model, String system, String prompt, Callback callback) {
        return request(model, system, prompt, false, callback);
    }

    public static void createTitle(String model, String content, Callback callback) {
        request(model, "", content, true, callback);
    }

    static String failureMessage(Exception error) {
        Throwable cause = error;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            if (cause instanceof java.net.SocketTimeoutException) { return UiText.AI_TIMEOUT.text(); }
        }
        if (error instanceof javax.net.ssl.SSLException) { return UiText.AI_TLS_FAILED.text(); }
        if (error instanceof java.io.IOException) { return UiText.AI_NETWORK_FAILED.text(); }
        if (error instanceof org.json.JSONException) { return UiText.AI_INVALID_RESPONSE.text(); }
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            return error.getMessage() == null ? UiText.CREATE_FAILED.text() : error.getMessage();
        }
        return UiText.CREATE_FAILED.text();
    }

    private static Call request(String model, String system, String prompt, boolean title, Callback callback) {
        // Capture the selection before starting the request; later UI edits affect the next request.
        final MeditationPauses.Guidance guidance =
                MeditationPauses.Guidance.fromId(
                        de.jeisfeld.mymeditation.util.PreferenceUtil.getSharedPreferenceString(
                                MeditationPauses.PREFERENCE, "few"));
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<HttpsURLConnection> active = new java.util.concurrent.atomic.AtomicReference<>();
        Thread worker = new Thread(() -> {
            HttpsURLConnection connection = null;
            String text = null;
            String error = null;
            long started = android.os.SystemClock.elapsedRealtime();
            String stage = "configuration";
            AiProvider provider = null;
            try {
                String modelId = model == null ? "" : model.trim();
                provider = AiProvider.forModel(modelId);
                String apiKey = LocalCredentials.read(provider.keyField);
                if (apiKey.isEmpty()) {
                    throw new IllegalStateException(UiText.LOCAL_KEY.text(provider.keyField));
                }
                JSONObject body = title ? AiProtocol.titleRequest(provider, modelId, prompt)
                        : AiProtocol.request(provider, modelId, system, prompt, guidance);
                connection = (HttpsURLConnection) new URL(provider.url(modelId)).openConnection();
                active.set(connection);
                if (cancelled.get()) { return; }
                stage = "connecting";
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(title ? 30000 : MEDITATION_READ_TIMEOUT);
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
                stage = "waiting_response";
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    throw new IllegalStateException(UiText.AI_HTTP.text(provider.displayName, status));
                }
                stage = "reading_response";
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = in.read(buffer)) != -1) { bytes.write(buffer, 0, count); }
                }
                stage = "parsing_response";
                text = AiProtocol.response(provider, new JSONObject(new String(bytes.toByteArray(), StandardCharsets.UTF_8)));
            } catch (Exception e) {
                error = failureMessage(e);
                // Never log prompt/response bodies, exception messages, URLs or credentials.
                android.util.Log.w("MeditationGeneration", "kind=" + (title ? "title" : "meditation") + " provider=" + provider + " stage=" + stage + " exception=" + e.getClass().getSimpleName()
                        + " elapsedSeconds=" + (android.os.SystemClock.elapsedRealtime() - started) / 1000);
            } finally {
                if (connection != null) { connection.disconnect(); }
            }
            final String result = text;
            final String failure = error;
            new Handler(Looper.getMainLooper()).post(() -> { if (!cancelled.get()) { callback.complete(result, failure); } });
        }, "ai-meditation");
        worker.start();
        return () -> {
            cancelled.set(true);
            HttpsURLConnection connection = active.get();
            if (connection != null) { connection.disconnect(); }
            worker.interrupt();
        };
    }
}
