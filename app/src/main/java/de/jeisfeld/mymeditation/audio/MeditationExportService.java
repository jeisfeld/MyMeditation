package de.jeisfeld.mymeditation.audio;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.*;

/** Assembles a temporary whole MP3 only for an explicit Share action. */
public final class MeditationExportService extends Service {
    private static final String CHANNEL = "meditation_export", CANCEL = "export.CANCEL";
    private static final int NOTIFICATION = 43;
    public static final class State {
        public final boolean active;
        public final String status, error, name;
        public final long resultId;
        public final int savedId;
        public final File sharedFile;
        State(boolean active, String status, String error, String name, long resultId, int savedId) {
            this(active, status, error, name, resultId, savedId, null);
        }
        State(boolean active, String status, String error, String name, long resultId, int savedId, File sharedFile) {
            this.active = active; this.status = status; this.error = error; this.name = name;
            this.resultId = resultId; this.savedId = savedId; this.sharedFile = sharedFile;
        }
    }
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(false, "", "", "", 0, 0));
    public static LiveData<State> state() { return STATE; }
    public static void share(Context context, int id, double pause) {
        if (!Double.isFinite(pause) || pause < 0 || pause > 30) { throw new IllegalArgumentException("Invalid share pause"); }
        context.startForegroundService(new Intent(context, MeditationExportService.class).putExtra("shareId", id).putExtra("pause", pause));
    }
    public static void cancel(Context context) {
        if (STATE.getValue().active) { context.startService(new Intent(context, MeditationExportService.class).setAction(CANCEL)); }
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile int generation;
    private volatile boolean destroyed;
    private boolean working;
    private int shareId;
    private PowerManager.WakeLock wakeLock;
    private String name = "", status = "";
    private final Runnable renew = new Runnable() {
        @Override public void run() {
            if (working) { wakeLock.acquire(10 * 60 * 1000L); handler.postDelayed(this, 5 * 60 * 1000L); }
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.export_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":export");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || CANCEL.equals(intent.getAction())) { cancelWork(); return START_NOT_STICKY; }
        if (working) { return START_NOT_STICKY; }
        int token = ++generation;
        shareId = intent.getIntExtra("shareId", 0);
        name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, shareId);
        if (name == null || name.isEmpty()) { name = getString(R.string.default_offline_name); }
        status = getString(R.string.export_preparing);
        startForeground(NOTIFICATION, notification());
        try {
            SentenceAudioStore.Book book = SavedAudioLibrary.book(this, shareId);
            if (book == null || !book.isComplete()) { throw new IllegalArgumentException(getString(R.string.recorded_audio_unavailable)); }
            PlaybackRequest request = book.request.withPause(intent.getDoubleExtra("pause", 2));
            working = true;
            STATE.setValue(new State(true, status, "", name, 0, 0));
            renew.run();
            int sharing = shareId;
            executor.execute(() -> render(token, request, sharing));
        } catch (Exception e) { failure(e); }
        return START_NOT_STICKY;
    }
    private void check(int token) {
        if (generation != token || destroyed || Thread.currentThread().isInterrupted()) { throw new CancellationException(); }
    }
    private void progress(int token, String message) {
        handler.post(() -> {
            if (generation != token || destroyed) { return; }
            status = message; STATE.setValue(new State(true, status, "", name, 0, 0));
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
        });
    }
    private void render(int token, PlaybackRequest request, int sharing) {
        File temporary = null;
        try {
            SentenceAudioStore.Book book = SentenceAudio.store(this).open(request);
            File directory = new File(getCacheDir(), "shared-meditations");
            if (!directory.isDirectory() && !directory.mkdirs()) { throw new java.io.IOException(); }
            // Share output is disposable; durable sentence recordings are never removed here.
            File[] expired = directory.listFiles((dir, filename) -> System.currentTimeMillis() - new File(dir, filename).lastModified() > 86400000L);
            if (expired != null) { for (File file : expired) { file.delete(); } }
            temporary = File.createTempFile("share-", ".part", directory);
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                SentenceAudioAssembler.write(book, request.pauseSeconds, output,
                        (audio, writer, check) -> OfflineAudioRenderer.append(audio, 1, writer, check),
                        index -> progress(token, getString(R.string.share_audio_progress, index, book.sentences.size())), () -> check(token));
            }
            check(token);
            File target = new File(directory, "meditation-" + sharing + "-" + System.nanoTime() + ".mp3");
            if (!temporary.renameTo(target)) { throw new java.io.IOException(); }
            temporary = null;
            handler.post(() -> {
                if (generation != token || destroyed) { target.delete(); return; }
                STATE.setValue(new State(false, "", "", name, System.nanoTime(), sharing, target));
                finish();
            });
        } catch (CancellationException ignored) {
            // The cancel/timeout/destroy path already published its state.
        } catch (Exception e) {
            handler.post(() -> { if (generation == token && !destroyed) { failure(e); } });
        } finally { if (temporary != null) { temporary.delete(); } }
    }
    private void failure(Exception e) {
        generation++;
        String message = e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : getString(R.string.export_failed);
        STATE.setValue(new State(false, "", message == null ? getString(R.string.export_failed) : message, name, System.nanoTime(), 0));
        finish();
    }
    private void cancelWork() {
        generation++;
        STATE.setValue(new State(false, "", "", name, 0, 0)); finish();
    }
    private void finish() {
        working = false; handler.removeCallbacks(renew);
        if (wakeLock.isHeld()) { wakeLock.release(); }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, MainActivity.notificationIntent(this,
                R.id.navigation_saved_meditations), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel = PendingIntent.getService(this, 43, new Intent(this, MeditationExportService.class).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_saved_meditations)
                .setContentTitle(getString(R.string.button_share_offline)).setContentText(status).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, true)
                .addAction(new Notification.Action.Builder(R.drawable.ic_playback_stop, getString(R.string.button_cancel), cancel).build()).build();
    }
    @Override public void onTimeout(int startId, int fgsType) { failure(new java.io.IOException()); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true; generation++; executor.shutdownNow(); handler.removeCallbacksAndMessages(null);
        if (wakeLock.isHeld()) { wakeLock.release(); }
        if (STATE.getValue().active) { STATE.setValue(new State(false, "", getString(R.string.export_failed), name, System.nanoTime(), 0)); }
        SavedAudioLibrary.cleanupDeleted(getApplicationContext());
        super.onDestroy();
    }
}
