package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.http.SpeechBackend;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/** Durable, versioned, pause-free MP3s. Identity includes exact text and all voice settings. */
public final class SentenceAudioStore {
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private final File root;
    public interface Producer { void write(File destination) throws Exception; }
    public SentenceAudioStore(File root) { this.root = root; }

    public static String key(PlaybackRequest request) {
        try {
            JSONObject value = new JSONObject().put("version", 1).put("text", request.text)
                    .put("backend", request.backend.id).put("model", request.model).put("voice", request.voice)
                    .put("style", request.style == null ? "" : request.style).put("speed", request.speed);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray();
            char[] result = new char[hash.length * 2];
            for (int i = 0; i < hash.length; i++) { result[2 * i] = hex[(hash[i] & 255) >>> 4]; result[2 * i + 1] = hex[hash[i] & 15]; }
            return new String(result);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public Book open(PlaybackRequest request) {
        if (request.backend == SpeechBackend.DEVICE) { throw new IllegalArgumentException("Device speech is not a recording"); }
        return new Book(request.withPause(0));
    }

    /** Untrusted/corrupt metadata is unavailable, never a reason to follow an arbitrary path. */
    public Book find(String identity) {
        if (identity == null || !identity.matches("[0-9a-f]{64}")) { return null; }
        try {
            File metadata = new File(new File(root, identity), "manifest.json");
            if (!metadata.isFile() || metadata.length() > 2 * 1024 * 1024) { return null; }
            JSONObject value = new JSONObject(new String(Files.readAllBytes(metadata.toPath()), StandardCharsets.UTF_8));
            if (value.getInt("version") != 1) { return null; }
            Book book = open(PlaybackRequest.parse(value.getString("request")));
            return book.identity.equals(identity) ? book : null;
        } catch (Exception e) { return null; }
    }

    /** Deletes only the generated flat directory, even when its metadata has become unreadable. */
    void delete(String identity) {
        if (identity == null || !identity.matches("[0-9a-f]{64}")) { return; }
        File directory = new File(root, identity);
        File[] files = directory.listFiles();
        if (files != null) { for (File file : files) { if (!file.isDirectory()) { file.delete(); } } }
        directory.delete();
    }

    private static void publish(File temporary, File target) throws IOException {
        try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean mp3(File file) {
        if (!file.isFile() || file.length() < 4) { return false; }
        try (FileInputStream input = new FileInputStream(file)) {
            int a = input.read(), b = input.read(), c = input.read();
            return a == 255 && (b & 224) == 224 || a == 'I' && b == 'D' && c == '3';
        } catch (IOException e) { return false; }
    }

    public final class Book {
        public final PlaybackRequest request;
        public final String identity;
        public final List<String> sentences;
        private final File directory;
        private Book(PlaybackRequest request) {
            this.request = request;
            identity = key(request);
            directory = new File(root, identity);
            sentences = java.util.Collections.unmodifiableList(SpeechSegments.split(request.text, request.backend.segmentLimit));
        }
        private void index(int index) {
            if (index < 0 || index >= sentences.size()) { throw new IllegalArgumentException("Invalid sentence index"); }
        }
        public File audio(int index) { index(index); return new File(directory, index + ".mp3"); }
        public File available(int index) { File file = audio(index); return mp3(file) ? file : null; }

        /** One producer per sentence, including concurrent playback requests. */
        public File obtain(int index, Producer producer, Runnable check) throws Exception {
            File target = audio(index);
            synchronized (LOCKS.computeIfAbsent(target.getAbsolutePath(), ignored -> new Object())) {
                check.run();
                if (mp3(target)) { return target; }
                initialize();
                File temporary = File.createTempFile("sentence-" + index + "-", ".part", directory);
                try {
                    producer.write(temporary);
                    check.run();
                    if (!mp3(temporary)) { throw new IOException("Empty or invalid sentence MP3"); }
                    publish(temporary, target);
                    return target;
                } finally { temporary.delete(); }
            }
        }
        private void initialize() throws Exception {
            synchronized (LOCKS.computeIfAbsent(directory.getAbsolutePath(), ignored -> new Object())) {
                if (!directory.isDirectory() && !directory.mkdirs()) { throw new IOException("Cannot create recording directory"); }
                File metadata = new File(directory, "manifest.json");
                if (!metadata.isFile() || find(identity) == null) {
                    File temporary = File.createTempFile("manifest-", ".part", directory);
                    try {
                        JSONObject value = new JSONObject().put("version", 1).put("request", request.json());
                        byte[] encoded = value.toString().getBytes(StandardCharsets.UTF_8);
                        if (encoded.length > 2 * 1024 * 1024) { throw new IOException("Recording metadata too large"); }
                        Files.write(temporary.toPath(), encoded);
                        publish(temporary, metadata);
                    } finally { temporary.delete(); }
                }
            }
        }
        public boolean isPublished() { return new File(directory, "complete").isFile(); }
        public boolean isComplete() {
            if (!isPublished()) { return false; }
            for (int i = 0; i < sentences.size(); i++) {
                if (available(i) == null) { new File(directory, "complete").delete(); return false; }
            }
            return true;
        }
        public void invalidate(int index) {
            audio(index).delete();
            new File(directory, "complete").delete();
            new File(directory, "played-" + index).delete();
        }
        /** Explicit Save can finish without playing; partial/cancelled work never qualifies. */
        public void finish() throws Exception {
            initialize();
            for (int i = 0; i < sentences.size(); i++) {
                if (available(i) == null) { throw new IOException("Incomplete sentence recording"); }
            }
            Files.write(new File(directory, "complete").toPath(), new byte[] {1});
        }
        /** Automatic retention requires completed playback of every sentence, not speculative downloads. */
        public boolean played(int index) throws Exception {
            index(index);
            if (available(index) == null) { return false; }
            Files.write(new File(directory, "played-" + index).toPath(), new byte[] {1});
            for (int i = 0; i < sentences.size(); i++) {
                if (!new File(directory, "played-" + i).isFile() || available(i) == null) { return false; }
            }
            finish();
            return true;
        }
    }
}
