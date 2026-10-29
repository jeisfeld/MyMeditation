package de.jeisfeld.mymeditation.audio;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.*;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.MainActivity;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/** Local MP3 playback needs no network, credentials, or speech service, and survives screen lock. */
public final class OfflinePlaybackService extends Service {
    private static final String PLAY = "offline.PLAY", PAUSE = "offline.PAUSE", STOP = "offline.STOP",
            BACK = "offline.BACK", FORWARD = "offline.FORWARD", CHANNEL = "offline_playback";
    private static final int NOTIFICATION = 44;
    public static final class State {
        public final int id, position;
        public final boolean playing;
        public final String error;
        public final long errorId;
        State(int id, int position, boolean playing, String error, long errorId) {
            this.id = id; this.position = position; this.playing = playing; this.error = error; this.errorId = errorId;
        }
    }
    private static final MutableLiveData<State> STATE = new MutableLiveData<>(new State(0, 0, false, "", 0));
    public static LiveData<State> state() { return STATE; }
    public static void toggle(Context context, int id) {
        State state = STATE.getValue();
        Intent intent = new Intent(context, OfflinePlaybackService.class).putExtra("id", id);
        if (state.id == id && state.playing) { context.startService(intent.setAction(PAUSE)); }
        else { context.startForegroundService(intent.setAction(PLAY)); }
    }
    public static void stopIfPlaying(Context context, int id) {
        if (STATE.getValue().id == id) { context.startService(new Intent(context, OfflinePlaybackService.class).setAction(STOP)); }
    }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable idle = () -> {
        // Remove controls when releasing the session; the saved-list button can still resume.
        keepNotification = false; stopSelf();
    };
    private MediaPlayer player;
    private MediaSession session;
    private AudioManager audioManager;
    private AudioFocusRequest focus;
    private int id, resumeAt;
    private String title = "";
    private boolean playing, prepared, keepNotification, destroyed, stopping;
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { pause(); }
    };
    @Override public void onCreate() {
        super.onCreate();
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.offline_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null); channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> { if (change < 0) { handler.post(this::pause); } }, handler).build();
        session = new MediaSession(this, "Offline meditation");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(id); }
            @Override public void onPause() { pause(); }
            @Override public void onStop() { stop(); }
            @Override public void onSeekTo(long position) { seek(position); }
            @Override public void onCustomAction(String action, Bundle extras) {
                if (STOP.equals(action)) { stop(); }
                else if (BACK.equals(action)) { seek(position() - 15000L); }
                else if (FORWARD.equals(action)) { seek(position() + 15000L); }
            }
        });
        session.setActive(true);
        if (Build.VERSION.SDK_INT >= 33) { registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED); }
        else { registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)); }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) { stop(); }
        else if (PAUSE.equals(intent.getAction())) { pause(); }
        else if (BACK.equals(intent.getAction())) { seek(position() - 15000L); }
        else if (FORWARD.equals(intent.getAction())) { seek(position() + 15000L); }
        else { play(intent.getIntExtra("id", STATE.getValue().id)); }
        return START_NOT_STICKY;
    }
    private void play(int requested) {
        try {
            handler.removeCallbacks(idle); keepNotification = false; stopping = false;
            startForeground(NOTIFICATION, notification(true)); // Before requesting audio focus on Android 15+.
            if (!OfflineMeditations.file(this, requested).isFile()) { throw new java.io.IOException(); }
            if (requested == id && player != null) {
                playing = true; publish();
                if (prepared) { begin(player); }
                return;
            }
            release();
            State previous = STATE.getValue();
            resumeAt = previous.id == requested ? previous.position : 0;
            id = requested;
            title = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id);
            if (title == null) { title = getString(R.string.default_offline_name); }
            prepared = false; playing = true; publish();
            player = new MediaPlayer(); MediaPlayer current = player;
            current.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
            current.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            current.setDataSource(OfflineMeditations.file(this, id).getAbsolutePath());
            current.setOnErrorListener((mp, what, extra) -> { if (mp == player && !destroyed) { fail(); } return true; });
            current.setOnCompletionListener(mp -> { if (mp == player && !destroyed) { stop(); } });
            current.setOnPreparedListener(mp -> {
                if (mp != player || destroyed) { return; }
                prepared = true;
                session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, getString(R.string.offline_channel))
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, mp.getDuration()).build());
                mp.setOnSeekCompleteListener(done -> {
                    if (done != player || destroyed || stopping) { return; }
                    if (playing && !done.isPlaying()) { begin(done); }
                    else { publish(); updateSession(); }
                });
                if (resumeAt > 0) { mp.seekTo(Math.min(resumeAt, Math.max(0, mp.getDuration() - 1)), MediaPlayer.SEEK_CLOSEST); }
                else if (playing) { begin(mp); }
            });
            current.prepareAsync();
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(true));
        } catch (Exception e) { fail(); }
    }
    private void begin(MediaPlayer current) {
        try {
            if (audioManager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { fail(); return; }
            current.start(); playing = true; publish(); updateSession();
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(true));
        } catch (RuntimeException e) { fail(); }
    }
    private int position() {
        try { return prepared && player != null ? player.getCurrentPosition() : resumeAt; }
        catch (RuntimeException e) { return resumeAt; }
    }
    private void pause() {
        if (destroyed || stopping) { return; }
        if (id == 0) { stop(); return; }
        try {
            resumeAt = position(); playing = false;
            if (prepared && player != null && player.isPlaying()) { player.pause(); }
            audioManager.abandonAudioFocusRequest(focus);
            publish(); updateSession(); keepNotification = true;
            stopForeground(STOP_FOREGROUND_DETACH);
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(false));
            handler.removeCallbacks(idle); handler.postDelayed(idle, 10 * 60 * 1000L);
        } catch (RuntimeException e) { fail(); }
    }
    private void seek(long position) {
        try {
            if (!prepared || player == null) { return; }
            resumeAt = (int) Math.max(0, Math.min(position, Math.max(0, player.getDuration() - 1)));
            player.seekTo(resumeAt, MediaPlayer.SEEK_CLOSEST); publish(); updateSession();
        } catch (RuntimeException e) { fail(); }
    }
    private void publish() { STATE.setValue(new State(id, position(), playing, "", 0)); }
    private void updateSession() {
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO)
                .addCustomAction(BACK, getString(R.string.offline_back), R.drawable.ic_playback_previous)
                .addCustomAction(FORWARD, getString(R.string.offline_forward), R.drawable.ic_playback_next)
                .addCustomAction(STOP, getString(R.string.notification_stop), R.drawable.ic_playback_stop)
                .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED, position(), playing ? 1f : 0f).build());
    }
    private void fail() {
        STATE.setValue(new State(0, 0, false, getString(R.string.offline_play_failed), System.nanoTime()));
        finish();
    }
    private void stop() { STATE.setValue(new State(0, 0, false, "", 0)); finish(); }
    private void finish() {
        stopping = true; playing = false; keepNotification = false; release();
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf();
    }
    private void release() {
        if (player != null) { player.release(); player = null; }
        prepared = false; audioManager.abandonAudioFocusRequest(focus);
    }
    private PendingIntent command(String action, int code, boolean foreground) {
        Intent intent = new Intent(this, OfflinePlaybackService.class).setAction(action).putExtra("id", id);
        return foreground ? PendingIntent.getForegroundService(this, code, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT)
                : PendingIntent.getService(this, code, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification notification(boolean active) {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_meditation)
                .setContentTitle(title.isEmpty() ? getString(R.string.offline_channel) : title)
                .setContentText(getString(active ? R.string.notification_playing : R.string.notification_paused))
                .setContentIntent(open).setDeleteIntent(command(STOP, 54, false)).setOnlyAlertOnce(true).setOngoing(active)
                .setVisibility(Notification.VISIBILITY_PUBLIC).setCategory(Notification.CATEGORY_TRANSPORT)
                .addAction(new Notification.Action.Builder(R.drawable.ic_playback_previous, getString(R.string.offline_back), command(BACK, 51, false)).build())
                .addAction(new Notification.Action.Builder(active ? R.drawable.ic_playback_pause : R.drawable.ic_playback_play,
                        getString(active ? R.string.notification_pause : R.string.notification_resume), command(active ? PAUSE : PLAY, 52, !active)).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_playback_stop, getString(R.string.notification_stop), command(STOP, 53, false)).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_playback_next, getString(R.string.offline_forward), command(FORWARD, 55, false)).build())
                .setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(0, 1, 2)).build();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null); release(); session.release(); unregisterReceiver(noisy);
        if (!keepNotification) { getSystemService(NotificationManager.class).cancel(NOTIFICATION); }
        if (STATE.getValue().id == id && STATE.getValue().playing) { STATE.setValue(new State(0, 0, false, "", 0)); }
        super.onDestroy();
    }
}
