package de.jeisfeld.mymeditation.http;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CancellationException;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONObject;

/** Cancellable native speech generation; Qwen audio downloads never receive the provider key. */
public final class CloudSpeech {
    private static final java.util.concurrent.Semaphore GEMINI_GATE = new java.util.concurrent.Semaphore(1, true);
    private static final java.util.concurrent.Semaphore ELEVEN_GATE = new java.util.concurrent.Semaphore(1, true);
    private java.util.function.BiConsumer<Integer, Long> retryListener = (attempt, delay) -> { };
    public void setRetryListener(java.util.function.BiConsumer<Integer, Long> listener) { retryListener = listener; }

    /** Retain paid ElevenLabs audio outside Android's disposable cache, reusing old cached files. */
    public File audioWithPersistentCache(File cache, File durable, SpeechBackend backend, String model, String voice,
            String text, double speed, String style) throws Exception {
        check();
        if (backend != SpeechBackend.ELEVENLABS) { return audio(cache, backend, model, voice, text, speed, style); }
        String name = NativeSpeechProtocol.cacheName(backend, model, voice, text, style);
        File destination = new File(durable, "native-speech/" + name);
        File previous = new File(cache, "native-speech/" + name);
        if (!destination.exists() && previous.isFile() && previous.length() > 0 && previous.length() <= 20 * 1024 * 1024) {
            File folder = destination.getParentFile();
            if (folder.isDirectory() || folder.mkdirs()) {
                File temporary = File.createTempFile("migrate-", ".tmp", folder);
                try {
                    Files.copy(previous.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    check();
                    synchronized (CloudSpeech.class) {
                        trim(folder, 236L * 1024 * 1024);
                        check();
                        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (java.io.IOException ignored) { /* Existing cache remains usable if migration fails. */ }
                finally { temporary.delete(); }
            }
            if (!destination.exists()) { return audio(cache, backend, model, voice, text, speed, style); }
        }
        return audio(durable, backend, model, voice, text, speed, style);
    }

    private final OpenAiSpeech openai = new OpenAiSpeech();
    private volatile HttpsURLConnection connection;
    private volatile boolean cancelled;
    public void cancel() { cancelled = true; openai.cancel(); HttpsURLConnection c = connection; if (c != null) { c.disconnect(); } }
    private void check() { if (cancelled || Thread.currentThread().isInterrupted()) { throw new CancellationException(); } }
    public File audio(File root, SpeechBackend backend, String model, String voice, String text, double speed, String style) throws Exception {
        if (backend == SpeechBackend.OPENAI) { return openai.audio(root, model, voice, text, speed, style); }
        SpeechRetry.Operation<File> operation = () -> {
            boolean acquired = false;
            java.util.concurrent.Semaphore gate = backend == SpeechBackend.GEMINI ? GEMINI_GATE
                    : backend == SpeechBackend.ELEVENLABS ? ELEVEN_GATE : null;
            try {
                if (gate != null) {
                    while (!gate.tryAcquire(100, java.util.concurrent.TimeUnit.MILLISECONDS)) { check(); }
                    acquired = true;
                }
                return attempt(root, backend, model, voice, text, style);
            } catch (org.json.JSONException e) {
                if (backend == SpeechBackend.GEMINI) { throw new SpeechRetry.TransientGenerationFailure("Gemini returned malformed speech data."); }
                throw e;
            } finally { if (acquired) { gate.release(); } }
        };
        return backend == SpeechBackend.ELEVENLABS
                ? SpeechRetry.untilCancelled(operation, this::check, Thread::sleep, retryListener)
                : SpeechRetry.run(operation, this::check, Thread::sleep);
    }
    private File attempt(File root, SpeechBackend backend, String model, String voice, String text, String style) throws Exception {
        check();
        String name = NativeSpeechProtocol.cacheName(backend, model, voice, text, style);
        File directory = new File(root, "native-speech");
        if (!directory.isDirectory() && !directory.mkdirs()) { throw new IllegalStateException("Speech cache unavailable."); }
        File target = new File(directory, name);
        if (target.isFile() && target.length() > 0) { target.setLastModified(System.currentTimeMillis()); return target; }
        String key = LocalCredentials.read(backend.key);
        if (key.isEmpty()) { throw new IllegalStateException("Set HttpCredentials." + backend.key + " and rebuild the app."); }
        File temporary = null;
        try {
            connection = connect(new URL(NativeSpeechProtocol.endpoint(backend, model, voice)));
            connection.setRequestMethod("POST"); connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty(backend == SpeechBackend.GEMINI ? "x-goog-api-key"
                    : backend == SpeechBackend.ELEVENLABS ? "xi-api-key" : "Authorization",
                    backend == SpeechBackend.QWEN ? "Bearer " + key : key);
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(NativeSpeechProtocol.request(backend, model, voice, text, style).toString().getBytes(StandardCharsets.UTF_8));
            }
            verify(connection, backend.id);
            byte[] audio;
            if (backend == SpeechBackend.ELEVENLABS) {
                requireAudio(connection, "ElevenLabs");
                long expected = connection.getContentLengthLong();
                audio = read(connection.getInputStream(), 20 * 1024 * 1024);
                if (expected > 0 && audio.length != expected) { throw new java.io.EOFException("Incomplete ElevenLabs audio download."); }
            } else {
                byte[] json = read(connection.getInputStream(), 32 * 1024 * 1024);
                JSONObject response = new JSONObject(new String(json, StandardCharsets.UTF_8));
                if (backend == SpeechBackend.GEMINI) { audio = NativeSpeechProtocol.geminiAudio(response); }
                else {
                    URL url = NativeSpeechProtocol.qwenAudioUrl(response);
                    connection.disconnect();
                    connection = connect(url); // No Authorization header on the storage download.
                    verify(connection, "Qwen audio download");
                    requireAudio(connection, "Qwen");
                    audio = read(connection.getInputStream(), 20 * 1024 * 1024);
                }
            }
            check();
            if (audio.length == 0) { throw new SpeechRetry.TransientGenerationFailure("Provider returned empty audio."); }
            temporary = File.createTempFile("speech-", ".tmp", directory);
            Files.write(temporary.toPath(), audio);
            synchronized (CloudSpeech.class) {
                check();
                trim(directory, (backend == SpeechBackend.ELEVENLABS ? 236L : 30L) * 1024 * 1024);
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            if (temporary != null) { temporary.delete(); }
            HttpsURLConnection current = connection; connection = null; if (current != null) { current.disconnect(); }
        }
    }
    private static void trim(File directory, long limit) {
        File[] files = directory.listFiles((d, n) -> n.endsWith(".audio"));
        if (files == null) { return; }
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        long size = 0;
        for (File file : files) { size += file.length(); }
        for (File file : files) {
            if (size <= limit) { break; }
            long length = file.length(); if (file.delete()) { size -= length; }
        }
    }
    private static void requireAudio(HttpsURLConnection connection, String provider) throws java.io.IOException {
        String type = connection.getContentType();
        if (type == null || !(type.startsWith("audio/") || type.startsWith("application/octet-stream"))) {
            if ("ElevenLabs".equals(provider)) { throw new SpeechRetry.TransientGenerationFailure("ElevenLabs returned a non-audio response."); }
            throw new IllegalStateException(provider + " returned a non-audio response.");
        }
    }
    private HttpsURLConnection connect(URL url) throws Exception {
        check();
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        connection = c;
        check();
        c.setInstanceFollowRedirects(false); c.setConnectTimeout(15000); c.setReadTimeout(90000);
        return c;
    }
    private static void verify(HttpsURLConnection c, String provider) throws Exception {
        int status = c.getResponseCode();
        if (status < 200 || status >= 300) {
            if (status == 408 || status == 429 || status == 500 || status == 502 || status == 503 || status == 504) {
                throw new SpeechRetry.HttpFailure(status, SpeechRetry.retryAfter(c.getHeaderField("Retry-After")));
            }
            throw new IllegalStateException(provider + " speech failed (HTTP " + status + "). Check key, model access, region and credit.");
        }
    }
    private byte[] read(InputStream input, int limit) throws Exception {
        try (InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = in.read(bytes)) != -1) { check(); if (out.size() + count > limit) { throw new IllegalStateException("Speech response too large."); } out.write(bytes, 0, count); }
            return out.toByteArray();
        }
    }
}
