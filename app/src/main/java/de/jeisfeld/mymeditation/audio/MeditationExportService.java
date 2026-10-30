package de.jeisfeld.mymeditation.audio;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.*;

/** User-started, cancellable export. Completed MP3s are independent of speech caches and API keys. */
public final class MeditationExportService extends Service {
    private static final String CHANNEL = "meditation_export", CANCEL = "export.CANCEL";
    private static final int NOTIFICATION = 43;
    public static final class State {
        public final boolean active;
        public final String status, error, name;
        public final long resultId;
        public final int savedId;
        State(boolean active, String status, String error, String name, long resultId, int savedId) {
            this.active = active; this.status = status; this.error = error; this.name = name;
            this.resultId = resultId; this.savedId = savedId;
        }
    }
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(false, "", "", "", 0, 0));
    public static LiveData<State> state() { return STATE; }
    public static void start(Context context, PlaybackRequest request, String name, String content) {
        context.startForegroundService(new Intent(context, MeditationExportService.class)
                .putExtra("request", request.json()).putExtra("name", name).putExtra("content", content)
                .putExtra("system", PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt))
                .putExtra("query", PreferenceUtil.getSharedPreferenceString(R.string.key_query_template)));
    }
    public static void cancel(Context context) {
        if (STATE.getValue().active) { context.startService(new Intent(context, MeditationExportService.class).setAction(CANCEL)); }
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CloudSpeech speech = new CloudSpeech();
    private volatile boolean cancelled, destroyed;
    private boolean working;
    private PowerManager.WakeLock wakeLock;
    private String name = "", status = "";
    private final Runnable renew = new Runnable() {
        @Override public void run() {
            if (working && !cancelled) { wakeLock.acquire(10 * 60 * 1000L); handler.postDelayed(this, 5 * 60 * 1000L); }
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
        name = intent.getStringExtra("name");
        status = getString(R.string.export_preparing);
        startForeground(NOTIFICATION, notification());
        try {
            PlaybackRequest request = PlaybackRequest.parse(intent.getStringExtra("request"));
            if (request.backend == SpeechBackend.DEVICE) { throw new IllegalArgumentException(getString(R.string.export_cloud_required)); }
            working = true; cancelled = false;
            STATE.setValue(new State(true, status, "", name, 0, 0));
            renew.run();
            executor.execute(() -> render(request, intent.getStringExtra("content"), intent.getStringExtra("system"), intent.getStringExtra("query")));
        } catch (Exception e) { failure(e); }
        return START_NOT_STICKY;
    }
    private void check() {
        if (cancelled || destroyed || Thread.currentThread().isInterrupted()) { throw new CancellationException(); }
    }
    private void progress(String message) {
        handler.post(() -> {
            if (cancelled || destroyed) { return; }
            status = message; STATE.setValue(new State(true, status, "", name, 0, 0));
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
        });
    }
    private void render(PlaybackRequest request, String content, String system, String query) {
        File temporary = null;
        try {
            File directory = OfflineMeditations.directory(this);
            if (!directory.isDirectory() && !directory.mkdirs()) { throw new java.io.IOException(); }
            // A killed process may leave an unpublished partial file; no completed recordings are touched.
            File[] abandoned = directory.listFiles((dir, filename) -> filename.endsWith(".part"));
            if (abandoned != null) { for (File file : abandoned) { file.delete(); } }
            temporary = File.createTempFile("render-", ".part", directory);
            List<String> segments = SpeechSegments.split(request.text, request.backend.segmentLimit);
            speech.setRetryListener((attempt, delay) -> progress(getString(R.string.export_retry, delay / 1000, attempt)));
            try (FileOutputStream output = new FileOutputStream(temporary); Mp3Writer writer = new Mp3Writer(output)) {
                for (int i = 0; i < segments.size(); i++) {
                    check(); progress(getString(R.string.export_progress, i + 1, segments.size()));
                    File audio = speech.audioWithPersistentCache(getCacheDir(), getFilesDir(), request.backend,
                            request.model, request.voice, segments.get(i), request.speed, request.style);
                    long before = writer.frames();
                    OfflineAudioRenderer.append(audio, request.backend == SpeechBackend.OPENAI ? 1 : request.speed, writer, this::check);
                    if (writer.frames() == before) { throw new java.io.IOException("Empty rendered audio"); }
                    if (i + 1 < segments.size()) { writer.silence(request.pauseSeconds, this::check); }
                }
            }
            check(); File completed = temporary;
            handler.post(() -> publishFile(completed, request, content, system, query));
            temporary = null;
        } catch (CancellationException ignored) {
            handler.post(() -> { if (!destroyed) { cancelWork(); } });
        } catch (Exception e) { handler.post(() -> { if (!destroyed && !cancelled) { failure(e); } }); }
        finally { if (temporary != null) { temporary.delete(); } }
    }
    private void publishFile(File completed, PlaybackRequest request, String content, String system, String query) {
        if (cancelled || destroyed) { completed.delete(); return; }
        File destination = null;
        try {
            int id = PreferenceUtil.getSharedPreferenceInt(R.string.key_max_saved_meditation_id, 0) + 1;
            destination = OfflineMeditations.file(this, id);
            if (!completed.renameTo(destination)) { throw new java.io.IOException(); }
            OfflineMeditations.save(name, system, query, content, request.text);
            PreferenceUtil.setSharedPreferenceString("offline_settings_" + id, request.json());
            STATE.setValue(new State(false, "", "", name, System.nanoTime(), id));
            finish();
        } catch (Exception e) { completed.delete(); if (destination != null) { destination.delete(); } failure(e); }
    }
    private void failure(Exception e) {
        String message = e instanceof IllegalArgumentException || e instanceof IllegalStateException ? e.getMessage() : getString(R.string.export_failed);
        STATE.setValue(new State(false, "", message == null ? getString(R.string.export_failed) : message, name, System.nanoTime(), 0));
        finish();
    }
    private void cancelWork() {
        cancelled = true; speech.cancel();
        STATE.setValue(new State(false, "", "", name, 0, 0)); finish();
    }
    private void finish() {
        working = false; handler.removeCallbacks(renew);
        if (wakeLock.isHeld()) { wakeLock.release(); }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, MainActivity.notificationIntent(this, R.id.navigation_meditation), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent cancel = PendingIntent.getService(this, 43, new Intent(this, MeditationExportService.class).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_saved_meditations)
                .setContentTitle(getString(R.string.export_title)).setContentText(status).setContentIntent(open)
                .setOngoing(true).setOnlyAlertOnce(true).setProgress(0, 0, true)
                .addAction(new Notification.Action.Builder(R.drawable.ic_playback_stop, getString(R.string.button_cancel), cancel).build()).build();
    }
    @Override public void onTimeout(int startId, int fgsType) {
        cancelled = true; speech.cancel(); failure(new java.io.IOException());
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true; cancelled = true; speech.cancel(); executor.shutdownNow(); handler.removeCallbacksAndMessages(null);
        if (wakeLock.isHeld()) { wakeLock.release(); }
        if (STATE.getValue().active) { STATE.setValue(new State(false, "", getString(R.string.export_failed), name, System.nanoTime(), 0)); }
        super.onDestroy();
    }
}
