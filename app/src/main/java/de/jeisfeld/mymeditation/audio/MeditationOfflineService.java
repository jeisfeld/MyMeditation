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
import java.util.concurrent.*;

/** Explicit foreground preparation of one saved entry, without playback or modifying the current draft. */
public final class MeditationOfflineService extends Service {
    private static final String CHANNEL = "meditation_offline", CANCEL = "offline.CANCEL";
    private static final int NOTIFICATION = 44;
    public static final class State {
        public final boolean active;
        public final int savedId;
        public final OfflineCoverage coverage;
        public final String message;
        State(boolean active, int id, OfflineCoverage coverage, String message) {
            this.active = active; savedId = id; this.coverage = coverage; this.message = message;
        }
    }
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(false, 0, null, ""));
    public static LiveData<State> state() { return STATE; }
    public static void prepare(Context context, int id, PlaybackRequest request) {
        context.startForegroundService(new Intent(context, MeditationOfflineService.class).putExtra("savedId", id).putExtra("request", request.json()));
    }
    public static void cancel(Context context, int id) {
        State state = STATE.getValue();
        if (state.active && state.savedId == id) { context.startService(new Intent(context, MeditationOfflineService.class).setAction(CANCEL)); }
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile int generation;
    private volatile boolean destroyed;
    private boolean working;
    private int savedId;
    private PlaybackRequest request;
    private CloudSpeech speech;
    private OfflineCoverage coverage;
    private String name = "";
    private PowerManager.WakeLock wakeLock;
    private final Runnable renew = new Runnable() {
        @Override public void run() { if (working) { wakeLock.acquire(10 * 60 * 1000L); main.postDelayed(this, 5 * 60 * 1000L); } }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.offline_prepare_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":offline");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || CANCEL.equals(intent.getAction())) { stopWork(getString(R.string.offline_prepare_cancelled)); return START_NOT_STICKY; }
        if (working) { return START_NOT_STICKY; }
        savedId = intent.getIntExtra("savedId", 0); coverage = null;
        name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, savedId);
        if (name == null || name.isEmpty()) { name = getString(R.string.default_offline_name); }
        startForeground(NOTIFICATION, notification(true, getString(R.string.export_preparing)));
        try {
            if (AppStorage.isCleaning() || AppStorage.audioActive()) { throw new IllegalStateException(getString(R.string.offline_prepare_busy)); }
            String serialized = intent.getStringExtra("request");
            if (serialized == null || serialized.length() > 2 * 1024 * 1024) { throw new IllegalArgumentException(getString(R.string.offline_entry_changed)); }
            request = PlaybackRequest.parse(serialized); validateEntry();
            if (request.backend == SpeechBackend.DEVICE) { throw new IllegalArgumentException(getString(R.string.offline_device_notice)); }
            SavedAudioLibrary.begin(request);
            SavedAudioLibrary.rememberPreparation(savedId, request);
            speech = new CloudSpeech(); speech.setBoundedRetries(true);
            working = true; int token = ++generation;
            STATE.setValue(new State(true, savedId, null, getString(R.string.export_preparing))); renew.run();
            PlaybackRequest captured = request; CloudSpeech synthesizer = speech;
            worker.execute(() -> {
                try {
                    OfflinePreparation.prepare(getApplicationContext(), captured, synthesizer, measured -> main.post(() -> {
                        if (valid(token)) {
                            coverage = measured; STATE.setValue(new State(true, savedId, coverage, ""));
                            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(true, coverageText()));
                        }
                    }), () -> { if (!valid(token) || Thread.currentThread().isInterrupted()) { throw new CancellationException(); } validateEntry(); });
                    main.post(() -> {
                        if (!valid(token)) { return; }
                        try { validateEntry(); SavedAudioLibrary.attachPrepared(this, savedId, captured); stopWork(getString(R.string.offline_ready)); }
                        catch (Exception error) { fail(error); }
                    });
                } catch (CancellationException ignored) { /* Keep completed clips; cancellation never attaches the entry. */ }
                catch (Exception error) { main.post(() -> { if (valid(token)) { fail(error); } }); }
            });
        } catch (Exception error) { fail(error); }
        return START_NOT_STICKY;
    }
    private boolean valid(int token) { return !destroyed && generation == token; }
    private void validateEntry() {
        if (!PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(savedId)
                || !request.text.equals(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, savedId))) {
            throw new IllegalArgumentException(getString(R.string.offline_entry_changed));
        }
    }
    private String coverageText() {
        return coverage == null ? getString(R.string.export_preparing) : getString(R.string.offline_prepare_progress, coverage.availableWords, coverage.totalWords);
    }
    private void fail(Exception error) {
        String message = error instanceof IllegalArgumentException || error instanceof IllegalStateException ? error.getMessage() : getString(R.string.offline_prepare_failed);
        stopWork(message == null ? getString(R.string.offline_prepare_failed) : message);
    }
    private void stopWork(String message) {
        generation++; working = false; if (speech != null) { speech.cancel(); }
        main.removeCallbacks(renew); if (wakeLock.isHeld()) { wakeLock.release(); }
        STATE.setValue(new State(false, savedId, coverage, message));
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
        SavedAudioLibrary.notifyChanged(); SavedAudioLibrary.cleanupDeleted(getApplicationContext());
    }
    private Notification notification(boolean active, String status) {
        PendingIntent open = PendingIntent.getActivity(this, 44, MainActivity.notificationIntent(this, R.id.navigation_saved_meditations), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 44, new Intent(this, MeditationOfflineService.class).setAction(CANCEL), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_launcher_meditation).setContentTitle(name)
                .setContentText(status).setContentIntent(open).setOngoing(active).setOnlyAlertOnce(true)
                .setProgress(100, coverage == null ? 0 : coverage.perMille / 10, coverage == null)
                .addAction(new Notification.Action.Builder(R.drawable.ic_playback_stop, getString(R.string.button_cancel), cancel).build()).build();
    }
    @Override public void onTimeout(int startId, int fgsType) { stopWork(getString(R.string.offline_prepare_failed)); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true; generation++; if (speech != null) { speech.cancel(); }
        worker.shutdownNow(); main.removeCallbacksAndMessages(null);
        if (wakeLock.isHeld()) { wakeLock.release(); }
        if (working) { working = false; STATE.setValue(new State(false, savedId, coverage, getString(R.string.offline_prepare_cancelled))); }
        SavedAudioLibrary.cleanupDeleted(getApplicationContext()); super.onDestroy();
    }
}
