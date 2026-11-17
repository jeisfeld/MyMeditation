package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Private app-data accounting and deletion restricted to known, unreferenced audio locations. */
public final class AppStorage {
    private static final AtomicBoolean CLEANING = new AtomicBoolean();
    private static final androidx.lifecycle.MutableLiveData<Boolean> CLEANING_STATE = new androidx.lifecycle.MutableLiveData<>(false);
    public static androidx.lifecycle.LiveData<Boolean> cleaning() { return CLEANING_STATE; }
    private AppStorage() { }
    public static final class Usage {
        public final long offlineAudio, otherAudio, otherData;
        Usage(long offlineAudio, long otherAudio, long otherData) {
            this.offlineAudio = offlineAudio; this.otherAudio = otherAudio; this.otherData = otherData;
        }
    }
    public static boolean isCleaning() { return CLEANING.get(); }
    public static boolean audioActive() {
        return MeditationPlaybackService.state().getValue().active || MeditationExportService.state().getValue().active;
    }
    /** Reserve on main, before starting the worker; services reject starts while reserved. */
    public static boolean beginCleaning() {
        if (audioActive() || !CLEANING.compareAndSet(false, true)) { return false; }
        CLEANING_STATE.setValue(true); return true;
    }
    public static void endCleaning() {
        CLEANING.set(false);
        Runnable notify = () -> { CLEANING_STATE.setValue(CLEANING.get()); SavedAudioLibrary.notifyChanged(); };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) { notify.run(); }
        else { new android.os.Handler(android.os.Looper.getMainLooper()).post(notify); }
    }
    private static Path sentences(Context context) { return new File(context.getFilesDir(), "sentence-audio").toPath().toAbsolutePath().normalize(); }
    private static List<Path> otherAudioRoots(Context context) {
        List<Path> roots = new ArrayList<>();
        roots.add(new File(context.getCacheDir(), "openai-speech").toPath());
        roots.add(new File(context.getCacheDir(), "native-speech").toPath());
        roots.add(new File(context.getCacheDir(), "shared-meditations").toPath());
        roots.add(new File(context.getNoBackupFilesDir(), "native-speech").toPath());
        roots.add(new File(context.getFilesDir(), "native-speech").toPath());
        roots.add(new File(context.getFilesDir(), "offline-meditations").toPath());
        roots.add(new File(context.getFilesDir(), "legacy-sentence-audio").toPath());
        roots.replaceAll(path -> path.toAbsolutePath().normalize());
        return roots;
    }
    public static Usage measure(Context context) throws IOException {
        Set<String> referenced = SavedAudioLibrary.referencedRecordings();
        Set<String> pinnedClips = SentenceAudio.store(context).clips(referenced);
        Path recordings = sentences(context); List<Path> other = otherAudioRoots(context);
        long[] totals = new long[3];
        Files.walkFileTree(context.getDataDir().toPath(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                if (error instanceof java.nio.file.NoSuchFileException) { return FileVisitResult.CONTINUE; }
                throw error;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (!attributes.isRegularFile()) { return FileVisitResult.CONTINUE; }
                Path path = file.toAbsolutePath().normalize(); int bucket = 2;
                if (path.startsWith(recordings)) {
                    Path relative = recordings.relativize(path);
                    boolean pinned = relative.getNameCount() > 1 && referenced.contains(relative.getName(0).toString());
                    if (relative.getNameCount() == 2 && "clips".equals(relative.getName(0).toString())) {
                        String filename = relative.getFileName().toString();
                        pinned = filename.endsWith(".audio") && pinnedClips.contains(filename.substring(0, filename.length() - 6));
                    }
                    bucket = pinned ? 0 : 1;
                } else {
                    for (Path root : other) { if (path.startsWith(root)) { bucket = 1; break; } }
                }
                totals[bucket] += attributes.size(); return FileVisitResult.CONTINUE;
            }
        });
        return new Usage(totals[0], totals[1], totals[2]);
    }
    /** Caller holds the maintenance reservation. Referenced recordings and other data are untouched. */
    public static void clearOtherAudio(Context context) throws IOException {
        if (!isCleaning() || audioActive()) { throw new IllegalStateException("Audio maintenance is not reserved"); }
        boolean failed = false;
        for (Path root : otherAudioRoots(context)) { try { deleteTree(context, root); } catch (IOException e) { failed = true; } }
        Set<String> referenced = SavedAudioLibrary.referencedRecordings();
        Set<String> pinnedClips = SentenceAudio.store(context).clips(referenced);
        Path recordings = sentences(context);
        if (Files.isSymbolicLink(recordings)) { throw new IOException("Invalid audio directory"); }
        File[] entries = recordings.toFile().listFiles();
        if (entries != null) {
            for (File entry : entries) {
                if (referenced.contains(entry.getName())) { continue; }
                if ("clips".equals(entry.getName())) {
                    if (Files.isSymbolicLink(entry.toPath())) { throw new IOException("Invalid sentence pool"); }
                    File[] clips = entry.listFiles();
                    if (clips != null) { for (File clip : clips) {
                        String filename = clip.getName();
                        if (filename.endsWith(".audio") && pinnedClips.contains(filename.substring(0, filename.length() - 6))) { continue; }
                        try { deleteTree(context, clip.toPath()); } catch (IOException e) { failed = true; }
                    } }
                    continue;
                }
                try { deleteTree(context, entry.toPath()); } catch (IOException e) { failed = true; }
            }
        }
        if (failed) { throw new IOException("Some audio files could not be deleted"); }
    }
    private static void deleteTree(Context context, Path root) throws IOException {
        root = root.toAbsolutePath().normalize();
        Path data = context.getDataDir().toPath().toAbsolutePath().normalize();
        if (!root.startsWith(data) || root.equals(data)) { throw new IOException("Invalid audio location"); }
        for (Path parent = root.getParent(); parent != null && !parent.equals(data); parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent)) { throw new IOException("Invalid audio directory"); }
        }
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) { return; }
        // Never follow symbolic links, even inside a known audio directory.
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFileFailed(Path file, IOException error) throws IOException {
                if (error instanceof java.nio.file.NoSuchFileException) { return FileVisitResult.CONTINUE; }
                throw error;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) { throw failure; }
                Files.deleteIfExists(directory); return FileVisitResult.CONTINUE;
            }
        });
    }
}
