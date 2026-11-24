package de.jeisfeld.mymeditation.http;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.*;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.i18n.UiText;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import org.json.JSONObject;

/** Owns a user-started generation independently of the activity; never automatically retries a billed request. */
public final class MeditationGenerationService extends Service {
    private static final String CHANNEL = "meditation_generation", RECORD = "meditation_generation_result", CANCEL = "generation.CANCEL";
    private static final int NOTIFICATION = 45;
    private static boolean running;
    public static final class State {
        public final boolean active;
        public final String resultId, text, error;
        State(boolean active, String resultId, String text, String error) {
            this.active = active; this.resultId = resultId; this.text = text; this.error = error;
        }
    }
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(false, "", null, null));
    public static LiveData<State> state() {
        State state;
        try {
            JSONObject saved = new JSONObject(PreferenceUtil.getSharedPreferenceString(RECORD, "{}"));
            state = new State(saved.optBoolean("active"), saved.optString("resultId"),
                    saved.has("text") ? saved.getString("text") : null, saved.has("error") ? saved.getString("error") : null);
        } catch (Exception ignored) { state = new State(false, "", null, null); }
        if (state.active && !running) {
            state = new State(false, java.util.UUID.randomUUID().toString(), null, UiText.GENERATION_INTERRUPTED.text());
            persist(state);
        }
        STATE.setValue(state);
        return STATE;
    }
    public static void acknowledge(String resultId) {
        State current = STATE.getValue();
        if (current != null && !current.active && current.resultId.equals(resultId)) {
            publish(new State(false, "", null, null));
        }
    }
    public static void create(Context context, String model, String system, String prompt) {
        context.startForegroundService(new Intent(context, MeditationGenerationService.class)
                .putExtra("model", model).putExtra("system", system).putExtra("prompt", prompt));
    }
    private static void persist(State state) {
        try {
            PreferenceUtil.setSharedPreferenceString(RECORD, new JSONObject().put("active", state.active)
                    .put("resultId", state.resultId).put("text", state.text).put("error", state.error).toString());
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    private static void publish(State state) { persist(state); STATE.setValue(state); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean working, destroyed;
    private int generation;
    private AiClient.Call call;
    private PowerManager.WakeLock wakeLock;
    private final Runnable deadline = () -> finish(null, UiText.AI_TIMEOUT.text());
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.button_create_meditation), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":generation");
        wakeLock.setReferenceCounted(false);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || CANCEL.equals(intent.getAction())) {
            if (working) { finish(null, UiText.GENERATION_CANCELLED.text()); }
            else { stopSelf(); }
            return START_NOT_STICKY;
        }
        try {
            // Every foreground start must promote promptly, including duplicate taps.
            startForeground(NOTIFICATION, notification(true, getString(R.string.text_creating_meditation)));
            if (working) { return START_NOT_STICKY; }
            working = true; running = true;
            wakeLock.acquire(13 * 60 * 1000L);
            main.postDelayed(deadline, 12 * 60 * 1000L);
            publish(new State(true, "", null, null));
            int token = ++generation;
            call = AiClient.create(intent.getStringExtra("model"), intent.getStringExtra("system"), intent.getStringExtra("prompt"),
                    (text, error) -> { if (working && !destroyed && token == generation) { finish(text, error); } });
        } catch (RuntimeException error) {
            if (!working) { working = true; }
            android.util.Log.w("MeditationGeneration", "stage=service_start exception=" + error.getClass().getSimpleName());
            finish(null, UiText.GENERATION_START_FAILED.text());
        }
        return START_NOT_STICKY;
    }
    private void finish(String text, String error) {
        if (!working) { return; }
        generation++; working = false; running = false;
        main.removeCallbacks(deadline);
        if (call != null) { call.cancel(); call = null; }
        if (wakeLock.isHeld()) { wakeLock.release(); }
        if (error == null && text != null) { PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, text); }
        publish(new State(false, java.util.UUID.randomUUID().toString(), text, error));
        stopForeground(STOP_FOREGROUND_REMOVE);
        getSystemService(NotificationManager.class).notify(NOTIFICATION,
                notification(false, error == null ? getString(R.string.generation_ready) : error));
        stopSelf();
    }
    private Notification notification(boolean active, String message) {
        PendingIntent open = PendingIntent.getActivity(this, NOTIFICATION, MainActivity.notificationIntent(this, R.id.navigation_meditation),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_saved_meditations)
                .setContentTitle(getString(R.string.button_create_meditation)).setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(open)
                .setOngoing(active).setOnlyAlertOnce(true).setAutoCancel(!active);
        if (active) {
            PendingIntent cancel = PendingIntent.getService(this, NOTIFICATION, new Intent(this, MeditationGenerationService.class).setAction(CANCEL),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setProgress(0, 0, true).addAction(new Notification.Action.Builder(R.drawable.ic_playback_stop,
                    getString(R.string.button_cancel), cancel).build());
        }
        return builder.build();
    }
    @Override public void onTimeout(int startId, int fgsType) { finish(null, UiText.GENERATION_INTERRUPTED.text()); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true;
        if (working) { finish(null, UiText.GENERATION_INTERRUPTED.text()); }
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
