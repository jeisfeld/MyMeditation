package de.jeisfeld.mymeditation.audio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.List;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.SpeechProtocol;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/** Started media service owns playback independently of activities and fragments. */
public final class MeditationPlaybackService extends Service {
    private static final String CHANNEL = "meditation_playback";
    private static final int NOTIFICATION = 42;
    private static final String START = "meditation.START", RESUME = "meditation.RESUME", PAUSE = "meditation.PAUSE",
            STOP = "meditation.STOP", SEEK = "meditation.SEEK", NEXT = "meditation.NEXT", PREVIOUS = "meditation.PREVIOUS", SET_PAUSE = "meditation.SET_PAUSE";
    private static final String SAVED = "playback_session", POSITION = "playback_position";

    public static final class State {
        public final boolean running;
        public final int position;
        public final List<String> sentences;
        public final String status, error;
        public final long errorId;
        State(boolean running, int position, List<String> sentences, String status, String error, long errorId) {
            this.running = running; this.position = position;
            this.sentences = Collections.unmodifiableList(new java.util.ArrayList<>(sentences));
            this.status = status; this.error = error; this.errorId = errorId;
        }
    }
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(false, 0, Collections.emptyList(), "", "", 0));
    public static LiveData<State> state() { return STATE; }
    public static void play(Context context, int position) {
        context.startForegroundService(new Intent(context, MeditationPlaybackService.class).setAction(START).putExtra("position", position));
    }
    public static void seek(Context context, int position) {
        context.startForegroundService(new Intent(context, MeditationPlaybackService.class).setAction(SEEK).putExtra("position", position));
    }
    public static void pause(Context context) {
        if (STATE.getValue().running) { context.startService(new Intent(context, MeditationPlaybackService.class).setAction(PAUSE)); }
    }
    public static void setPause(Context context, int seconds) {
        if (STATE.getValue().running) { context.startService(new Intent(context, MeditationPlaybackService.class).setAction(SET_PAUSE).putExtra("seconds", seconds)); }
    }

    private boolean running, transition, destroying, keepNotification, stopping;
    private final android.os.Handler idleHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable stopIdle = () -> { if (!running) { stopSelf(); } };
    private final Runnable renewWakeLock = new Runnable() {
        @Override public void run() {
            if (running && wakeLock != null) {
                wakeLock.acquire(10 * 60 * 1000L);
                idleHandler.postDelayed(this, 5 * 60 * 1000L);
            }
        }
    };
    private MeditationSpeechPlayer player;
    private MediaSession session;
    private PowerManager.WakeLock wakeLock;
    private PlaybackRequest request;
    private List<String> sentences = Collections.emptyList();
    private int position;
    private String status = "", error = "";
    private long errorId;
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { if (running) { pausePlayback(); } }
    };

    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Meditation playback", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":meditation");
        wakeLock.setReferenceCounted(false);
        session = new MediaSession(this, "Meditation");
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, getString(R.string.app_name))
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Guided meditation").build());
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { resumePlayback(); }
            @Override public void onPause() { pausePlayback(); }
            @Override public void onStop() { stopPlayback(); }
            @Override public void onSkipToNext() { skip(1); }
            @Override public void onSkipToPrevious() { skip(-1); }
        });
        session.setActive(true);
        player = new MeditationSpeechPlayer(this, new MeditationSpeechPlayer.Listener() {
            @Override public void onSentence(int index) {
                position = index; PreferenceUtil.setSharedPreferenceString(POSITION, Integer.toString(index)); publish(); updateNotification();
            }
            @Override public void onRunning(boolean active) {
                if (destroying || (transition && !active)) { return; }
                running = active;
                if (!active) {
                    releaseWakeLock();
                    if (!transition) { pausedNotification(); }
                }
                publish(); updateSession();
            }
            @Override public void onStatus(String value) { if (!destroying) { status = value; publish(); if (running) { updateNotification(); } } }
            @Override public void onError(String message) {
                running = false; error = message; errorId = System.nanoTime(); status = ""; publish(); finishService(false);
            }
            @Override public void onFinished() { position = 0; PreferenceUtil.setSharedPreferenceString(POSITION, "0"); publish(); finishService(false); }
        });
        if (Build.VERSION.SDK_INT >= 33) { registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED); }
        else { registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        String action = intent.getAction();
        if (PAUSE.equals(action)) { pausePlayback(); }
        else if (STOP.equals(action)) { stopPlayback(); }
        else if (SET_PAUSE.equals(action)) {
            if (request != null) { request = request.withPause(intent.getIntExtra("seconds", request.pauseSeconds)); saveRequest(); player.setPauseSeconds(request.pauseSeconds); }
        } else {
            // Promotion precedes audio focus and any synthesis work (Android 15+ requirement).
            try {
                startForeground(NOTIFICATION, notification(true));
                if (START.equals(action)) { request = configuredRequest(); startAt(intent.getIntExtra("position", 0)); }
                else {
                    restoreRequest();
                    if (SEEK.equals(action)) { startAt(intent.getIntExtra("position", position)); }
                    else if (NEXT.equals(action)) { skip(1); }
                    else if (PREVIOUS.equals(action)) { skip(-1); }
                    else { startAt(position); }
                }
            } catch (Exception e) { playbackError(e); }
        }
        return START_NOT_STICKY;
    }

    private PlaybackRequest configuredRequest() {
        SpeechBackend backend = SpeechBackend.fromId(PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend, R.string.default_speech_backend));
        String voice = PreferenceUtil.getSharedPreferenceString(backend.voiceKey(), backend.defaultVoice);
        double speed = 1.0;
        try {
            double legacy = Double.parseDouble(PreferenceUtil.getSharedPreferenceString(R.string.key_speech_speed, R.string.default_speech_speed));
            speed = new VoiceProfiles(PreferenceUtil.getSharedPreferenceString(VoiceProfiles.PREFERENCE, "{}")).speed(backend, voice, legacy);
            SpeechProtocol.validateSpeed(speed);
        } catch (IllegalArgumentException e) { speed = 1.0; }
        return new PlaybackRequest(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text), backend,
                PreferenceUtil.getSharedPreferenceString(backend.modelKey(), backend.defaultModel), voice, speed,
                PreferenceUtil.getSharedPreferenceString(R.string.key_speech_style, R.string.default_speech_style),
                PreferenceUtil.getSharedPreferenceInt(R.string.key_pause_duration, 1));
    }
    private void restoreRequest() throws Exception {
        if (request != null) { return; }
        String saved = PreferenceUtil.getSharedPreferenceString(SAVED, "");
        request = saved.isEmpty() ? configuredRequest() : PlaybackRequest.parse(saved);
        try { position = Integer.parseInt(PreferenceUtil.getSharedPreferenceString(POSITION, "0")); }
        catch (NumberFormatException e) { position = 0; }
    }
    private void saveRequest() { PreferenceUtil.setSharedPreferenceString(SAVED, request.json()); }
    private void startAt(int index) {
        sentences = SpeechSegments.split(request.text, request.backend.segmentLimit);
        position = Math.max(0, Math.min(index, sentences.size() - 1));
        idleHandler.removeCallbacks(stopIdle);
        error = ""; status = ""; keepNotification = false; stopping = false;
        saveRequest();
        startForeground(NOTIFICATION, notification(true));
        // A renewable CPU-only lease spans speech, pauses and synthesis/retry work.
        wakeLock.acquire(10 * 60 * 1000L);
        idleHandler.removeCallbacks(renewWakeLock);
        idleHandler.postDelayed(renewWakeLock, 5 * 60 * 1000L);
        transition = true;
        try { player.start(sentences, position, request.pauseSeconds, request.backend, request.model, request.voice, request.speed, request.style); }
        finally { transition = false; }
        if (!stopping) { publish(); updateSession(); updateNotification(); }
    }
    private void resumePlayback() {
        try { startForeground(NOTIFICATION, notification(true)); restoreRequest(); startAt(position); }
        catch (Exception e) { playbackError(e); }
    }
    private void skip(int direction) {
        try { restoreRequest(); startAt(position + direction); }
        catch (Exception e) { playbackError(e); }
    }
    private void pausePlayback() {
        transition = true;
        try { player.stop(); running = false; status = ""; releaseWakeLock(); publish(); updateSession(); pausedNotification(); }
        finally { transition = false; }
    }
    private void stopPlayback() { pausePlayback(); position = 0; PreferenceUtil.setSharedPreferenceString(POSITION, "0"); publish(); finishService(false); }
    private void pausedNotification() {
        keepNotification = true; stopping = true;
        stopForeground(STOP_FOREGROUND_DETACH);
        updateNotification();
        // Keep paused media controls responsive without keeping the CPU awake.
        idleHandler.removeCallbacks(stopIdle);
        idleHandler.postDelayed(stopIdle, 10 * 60 * 1000L);
    }
    private void playbackError(Exception exception) {
        error = exception instanceof IllegalArgumentException ? exception.getMessage() : "Could not start meditation playback. Please open the app and try again.";
        errorId = System.nanoTime(); running = false; publish(); finishService(false);
    }
    private void finishService(boolean keep) {
        keepNotification = keep; stopping = true;
        idleHandler.removeCallbacks(stopIdle);
        releaseWakeLock();
        stopForeground(keep ? STOP_FOREGROUND_DETACH : STOP_FOREGROUND_REMOVE);
        if (!keep) { getSystemService(NotificationManager.class).cancel(NOTIFICATION); }
        stopSelf();
    }
    private void releaseWakeLock() {
        idleHandler.removeCallbacks(renewWakeLock);
        if (wakeLock != null && wakeLock.isHeld()) { wakeLock.release(); }
    }
    private void publish() { STATE.setValue(new State(running, position, sentences, status, error, errorId)); }
    private void updateSession() {
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(running ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, running ? 1f : 0f).build());
    }
    private PendingIntent command(String action, int code, boolean foreground) {
        Intent intent = new Intent(this, MeditationPlaybackService.class).setAction(action);
        return foreground ? PendingIntent.getForegroundService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                : PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(boolean active) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String description = status.isEmpty() ? (active ? "Playing" : "Paused") + " · sentence " + (position + 1) + " of " + sentences.size() : status;
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_meditation).setContentTitle(getString(R.string.app_name))
                .setContentText(description).setContentIntent(open).setDeleteIntent(command(STOP, 5, false))
                .setOnlyAlertOnce(true).setOngoing(active)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_previous, "Previous", command(PREVIOUS, 1, true)).build())
                .addAction(new Notification.Action.Builder(active ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                        active ? "Pause" : "Resume", command(active ? PAUSE : RESUME, 2, !active)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_next, "Next", command(NEXT, 3, true)).build())
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", command(STOP, 4, false)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2)).build();
    }
    private void updateNotification() { if (!destroying) { getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(running)); } }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroying = true;
        idleHandler.removeCallbacksAndMessages(null);
        if (player != null) { player.close(); }
        releaseWakeLock();
        running = false; status = ""; publish();
        if (!keepNotification) { getSystemService(NotificationManager.class).cancel(NOTIFICATION); }
        if (session != null) { session.release(); }
        unregisterReceiver(noisy);
        super.onDestroy();
    }
}
