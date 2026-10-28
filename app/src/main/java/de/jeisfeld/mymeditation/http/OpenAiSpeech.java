package de.jeisfeld.mymeditation.http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CancellationException;
import javax.net.ssl.HttpsURLConnection;

/** One cancellable download; stores completed audio in the app's disposable cache. */
public final class OpenAiSpeech {
    private volatile boolean cancelled;
    private volatile HttpsURLConnection connection;

    public void cancel() {
        cancelled = true;
        HttpsURLConnection current = connection;
        if (current != null) { current.disconnect(); }
    }

    private void checkCancelled() {
        if (cancelled || Thread.currentThread().isInterrupted()) { throw new CancellationException(); }
    }

    public File audio(File cacheRoot, String model, String voice, String text) throws Exception {
        return audio(cacheRoot, model, voice, text, 1.0, SpeechProtocol.DEFAULT_STYLE);
    }

    public File audio(File cacheRoot, String model, String voice, String text, double speed, String style) throws Exception {
        return SpeechRetry.run(() -> attempt(cacheRoot, model, voice, text, speed, style), this::checkCancelled, Thread::sleep);
    }

    private File attempt(File cacheRoot, String model, String voice, String text, double speed, String style) throws Exception {
        checkCancelled();
        File directory = new File(cacheRoot, "openai-speech");
        if (!directory.isDirectory() && !directory.mkdirs()) { throw new java.io.IOException("Speech cache unavailable."); }
        File target = new File(directory, SpeechProtocol.cacheName(model, voice, text, speed, style));
        if (target.isFile() && target.length() > 0) { target.setLastModified(System.currentTimeMillis()); return target; }
        String key = LocalCredentials.read("OPENAI_API_KEY");
        if (key.isEmpty()) { throw new IllegalStateException("Set HttpCredentials.OPENAI_API_KEY and rebuild the app to use OpenAI speech."); }
        File temporary = null;
        try {
            connection = (HttpsURLConnection) new URL("https://api.openai.com/v1/audio/speech").openConnection();
            checkCancelled();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(90000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + key);
            try (java.io.OutputStream out = connection.getOutputStream()) {
                out.write(SpeechProtocol.request(model, voice, text, speed, style).toString().getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new SpeechRetry.HttpFailure(status, SpeechRetry.retryAfter(connection.getHeaderField("Retry-After")));
            }
            String contentType = connection.getContentType();
            if (contentType == null || !(contentType.startsWith("audio/") || contentType.startsWith("application/octet-stream"))) {
                throw new java.io.IOException("Expected audio response.");
            }
            temporary = File.createTempFile("speech-", ".tmp", directory);
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                long total = 0;
                while ((count = in.read(buffer)) != -1) {
                    checkCancelled();
                    total += count;
                    if (total > 20 * 1024 * 1024) { throw new java.io.IOException("Audio segment too large."); }
                    out.write(buffer, 0, count);
                }
            }
            checkCancelled();
            if (temporary.length() == 0) { throw new java.io.IOException("Empty speech response."); }
            synchronized (OpenAiSpeech.class) {
                checkCancelled();
                trimCache(directory, target);
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } finally {
            if (temporary != null) { temporary.delete(); }
            HttpsURLConnection current = connection;
            connection = null;
            if (current != null) { current.disconnect(); }
        }
    }

    private static synchronized void trimCache(File directory, File target) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (files == null) { return; }
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        long size = 0;
        for (File file : files) { size += file.length(); }
        for (File file : files) {
            if (size <= 30 * 1024 * 1024) { break; }
            if (!file.equals(target)) { long bytes = file.length(); if (file.delete()) { size -= bytes; } }
        }
    }
}
