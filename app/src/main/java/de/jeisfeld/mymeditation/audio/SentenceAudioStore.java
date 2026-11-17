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

/** Global source clips plus lightweight script manifests; timed pauses never have an audio file. */
public final class SentenceAudioStore {
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();
    private final File root;
    public interface Producer { void write(File destination) throws Exception; }
    public SentenceAudioStore(File root) { this.root = root; }

    public static String key(PlaybackRequest request) {
        try {
            JSONObject value = new JSONObject().put("version", 3).put("text", request.text)
                    .put("backend", request.backend.id).put("model", request.model).put("voice", request.voice)
                    .put("style", request.effectiveStyle()).put("speed", request.localSpeed() ? 1 : request.speed);
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
            if (value.getInt("version") != 2) { return null; }
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

    /** The sentence identity excludes surrounding text, positions, gaps and locally applied speed. */
    public static String clipKey(PlaybackRequest request, String sentence) {
        return key(new PlaybackRequest(sentence, request.backend, request.model, request.voice, request.speed, request.style, 0));
    }
    java.util.Set<String> clips(java.util.Set<String> identities) {
        java.util.Set<String> result = new java.util.HashSet<>();
        for (String identity : identities) {
            if (!identity.matches("[0-9a-f]{64}")) { continue; }
            Book book = find(identity);
            if (book != null) {
                for (String sentence : book.sentences) {
                    if (MeditationPauses.seconds(sentence) < 0) { result.add(clipKey(book.request, sentence)); }
                }
            } else {
                File references = new File(new File(root, identity), "clips.json");
                try {
                    if (!references.isFile() || references.length() > 2 * 1024 * 1024) { continue; }
                    org.json.JSONArray values = new org.json.JSONArray(new String(Files.readAllBytes(references.toPath()), StandardCharsets.UTF_8));
                    for (int i = 0; i < values.length(); i++) {
                        String value = values.optString(i);
                        if (value.matches("[0-9a-f]{64}")) { result.add(value); }
                    }
                } catch (Exception ignored) { /* Corrupt references never grant access outside the pool. */ }
            }
        }
        return result;
    }
    void removeClips(java.util.Set<String> candidates, java.util.Set<String> retained) {
        for (String key : candidates) {
            if (key.matches("[0-9a-f]{64}") && !retained.contains(key)) { new File(new File(root, "clips"), key + ".audio").delete(); }
        }
    }

    static void publish(File temporary, File target) throws IOException {
        try { Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static boolean validAudio(File file) {
        if (!file.isFile() || file.length() < 4) { return false; }
        try (FileInputStream input = new FileInputStream(file)) {
            int a = input.read(), b = input.read(), c = input.read();
            if (a == 'R' && b == 'I' && c == 'F') {
                byte[] header = new byte[12];
                try (FileInputStream wave = new FileInputStream(file)) { if (wave.read(header) != header.length) { return false; } }
                return file.length() > 12 && header[3] == 'F' && header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E';
            }
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
        File directory() { return directory; }
        public File audio(int index) {
            index(index);
            if (MeditationPauses.seconds(sentences.get(index)) >= 0) { return new File(directory, index + ".pause"); }
            return new File(new File(root, "clips"), clipKey(request, sentences.get(index)) + ".audio");
        }
        public File available(int index) { File file = audio(index); return validAudio(file) ? file : null; }
        private boolean ready(int index) { return MeditationPauses.seconds(sentences.get(index)) >= 0 || available(index) != null; }

        /** One producer per sentence, including concurrent playback requests. */
        public File obtain(int index, Producer producer, Runnable check) throws Exception {
            File target = audio(index);
            if (MeditationPauses.seconds(sentences.get(index)) >= 0) { throw new IllegalArgumentException("Pauses have no audio"); }
            initialize();
            synchronized (LOCKS.computeIfAbsent(target.getAbsolutePath(), ignored -> new Object())) {
                check.run();
                if (validAudio(target)) { return target; }
                File clips = target.getParentFile();
                if (!clips.isDirectory() && !clips.mkdirs()) { throw new IOException("Cannot create sentence pool"); }
                File temporary = File.createTempFile("sentence-", ".part", clips);
                try {
                    producer.write(temporary);
                    check.run();
                    if (!validAudio(temporary)) { throw new IOException("Empty or invalid sentence MP3"); }
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
                        JSONObject value = new JSONObject().put("version", 2).put("request", request.json());
                        byte[] encoded = value.toString().getBytes(StandardCharsets.UTF_8);
                        if (encoded.length > 2 * 1024 * 1024) { throw new IOException("Recording metadata too large"); }
                        Files.write(temporary.toPath(), encoded);
                        publish(temporary, metadata);
                        org.json.JSONArray references = new org.json.JSONArray();
                        for (String sentence : sentences) {
                            if (MeditationPauses.seconds(sentence) < 0) { references.put(clipKey(request, sentence)); }
                        }
                        Files.write(new File(directory, "clips.json").toPath(), references.toString().getBytes(StandardCharsets.UTF_8));
                    } finally { temporary.delete(); }
                }
            }
        }
        public boolean isPublished() { return new File(directory, "complete").isFile(); }
        /** Completeness is about valid generated clips, independent of listening progress. */
        public boolean isComplete() {
            for (int i = 0; i < sentences.size(); i++) {
                if (!ready(i)) { new File(directory, "complete").delete(); return false; }
            }
            if (!new File(directory, "complete").isFile()) { try { finish(); } catch (Exception e) { return false; } }
            return true;
        }
        public List<Integer> missingSpeech() {
            List<Integer> missing = new java.util.ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) { if (!ready(i)) { missing.add(i); } }
            return missing;
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
                if (!ready(i)) { throw new IOException("Incomplete sentence recording"); }
            }
            Files.write(new File(directory, "complete").toPath(), new byte[] {1});
        }
        /** Listening markers are retained separately and never gate offline availability. */
        public boolean played(int index) throws Exception {
            index(index);
            if (!ready(index)) { return false; }
            initialize();
            Files.write(new File(directory, "played-" + index).toPath(), new byte[] {1});
            return isComplete();
        }
    }
}
