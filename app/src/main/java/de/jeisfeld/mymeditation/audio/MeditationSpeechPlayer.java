package de.jeisfeld.mymeditation.audio;

import de.jeisfeld.mymeditation.i18n.UiText;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;

/** Main-thread playback state with session guards for late TTS, network and media callbacks. */
public final class MeditationSpeechPlayer {
    public interface Listener {
        void onSentence(int index);
        void onRunning(boolean running);
        void onError(String error);
        default void onStatus(String status) { }
        default void onFinished() { }
        default void onCompletedSentence(int index) { }
        default void onSpeaking(int index) { }
        default void onWaitingForAudioFocus(boolean waiting) { }
        default void onResumeAfterInterruption(int sentence) { }
    }
    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final ExecutorService elevenExecutor = Executors.newSingleThreadExecutor();
    private final Map<Integer, Download> downloads = new HashMap<>();
    private android.media.AudioFocusRequest focus;
    private final AudioFocusRecovery focusRecovery = new AudioFocusRecovery();
    private int focusSession;
    private TextToSpeech tts;
    private MediaPlayer media;
    private AudioTrack silence;
    private int session;
    private int index;
    private int pauseMs;
    private boolean running;
    private SpeechBackend backend;
    private String model;
    private String voice;
    private double speed;
    private String style;
    private List<String> sentences;
    private PlaybackRequest recordingRequest;

    private static final class Download {
        final CloudSpeech request = new CloudSpeech();
        CompletableFuture<File> result;
        String waiting = "";
        volatile boolean cancelled;
    }

    public MeditationSpeechPlayer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start(PlaybackRequest request, int position) {
        start(SpeechSegments.split(request.text, request.backend.segmentLimit), position, request.pauseSeconds,
                request.backend, request.model, request.voice, request.speed, request.style, request);
    }

    public void start(List<String> text, int position, double pauseSeconds, SpeechBackend backend, String model, String voice, double speed, String style) {
        start(text, position, pauseSeconds, backend, model, voice, speed, style, null);
    }

    private void start(List<String> text, int position, double pauseSeconds, SpeechBackend backend, String model, String voice,
                       double speed, String style, PlaybackRequest recordingRequest) {
        stop();
        if (text.isEmpty()) { return; }
        this.recordingRequest = recordingRequest;
        sentences = new ArrayList<>(text);
        index = Math.max(0, Math.min(position, text.size() - 1));
        pauseMs = (int) Math.round(Math.max(0, pauseSeconds) * 1000);
        this.backend = backend;
        this.model = model;
        this.voice = voice;
        this.speed = speed;
        this.style = style;
        focusRecovery.start();
        running = true;
        listener.onRunning(true);
        final int token = session;
        android.media.AudioManager audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        final int focusToken = focusSession;
        focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes()).setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(change -> {
                    // Audio callbacks have their own generation: temporary suspension invalidates
                    // speech/network callbacks, but must still accept a later focus GAIN.
                    if (focus != null && focusToken == focusSession) { audioFocusChanged(change); }
                }, handler).build();
        if (audio.requestAudioFocus(focus) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail(UiText.AUDIO_UNAVAILABLE.text());
            return;
        }
        if (backend != SpeechBackend.DEVICE) {
            next(token, true);
        } else {
            tts = new TextToSpeech(context, status -> handler.post(() -> {
                if (!valid(token)) { return; }
                if (status != TextToSpeech.SUCCESS) { fail(UiText.DEVICE_START_FAILED.text()); return; }
                tts.setAudioAttributes(attributes());
                tts.setSpeechRate((float) speed);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        int spoken = Integer.parseInt(id.substring(id.lastIndexOf('-') + 1));
                        handler.post(() -> { if (valid(token) && index == spoken) { listener.onSpeaking(spoken); } });
                    }
                    @Override public void onDone(String id) { handler.post(() -> complete(token)); }
                    @Override public void onError(String id) { handler.post(() -> { if (valid(token)) { fail(UiText.DEVICE_SPEECH_FAILED.text()); } }); }
                });
                next(token, true);
            }));
        }
    }

    private void audioFocusChanged(int change) {
        AudioFocusRecovery.Change event;
        switch (change) {
            case android.media.AudioManager.AUDIOFOCUS_GAIN: event = AudioFocusRecovery.Change.GAIN; break;
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                event = AudioFocusRecovery.Change.TEMPORARY_LOSS; break;
            case android.media.AudioManager.AUDIOFOCUS_LOSS: event = AudioFocusRecovery.Change.PERMANENT_LOSS; break;
            default: return;
        }
        switch (focusRecovery.change(event, index)) {
            case SUSPEND:
                // Retain the focus request so Android can return focus when navigation finishes.
                stopPlaybackResources();
                listener.onWaitingForAudioFocus(true);
                listener.onStatus(UiText.WAITING_FOR_AUDIO_FOCUS.text());
                listener.onRunning(false);
                break;
            case RESUME:
                listener.onWaitingForAudioFocus(false);
                listener.onResumeAfterInterruption(focusRecovery.sentence());
                break;
            case STOP: stop(); break;
            default: break;
        }
    }

    private boolean valid(int token) { return running && token == session; }

    private Download download(int position) {
        Download job = downloads.get(position);
        if (job == null) {
            job = new Download();
            final Download current = job;
            final int token = session;
            current.request.setRetryListener((attempt, delay) -> handler.post(() -> {
                if (!valid(token)) { return; }
                current.waiting = UiText.ELEVEN_RETRY.text(delay / 1000, attempt);
                if (index == position) { listener.onStatus(current.waiting); }
            }));
            final String text = sentences.get(position);
            final SpeechBackend speechBackend = backend;
            final String speechModel = model;
            final String speechVoice = voice;
            final double speechSpeed = speed;
            final String speechStyle = style;
            final PlaybackRequest recording = recordingRequest;
            job.result = CompletableFuture.supplyAsync(() -> {
                try {
                    if (recording != null) {
                        return SentenceAudio.obtain(context, recording, position, current.request, () -> {
                            if (current.cancelled || Thread.currentThread().isInterrupted()) { throw new java.util.concurrent.CancellationException(); }
                        });
                    }
                    return current.request.audioWithPersistentCache(context.getCacheDir(), context.getNoBackupFilesDir(), speechBackend, speechModel, speechVoice, text, speechSpeed, speechStyle);
                }
                catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
            }, speechBackend == SpeechBackend.ELEVENLABS ? elevenExecutor : executor);
            downloads.put(position, job);
        }
        return job;
    }

    private void next(int token, boolean first) {
        if (!valid(token)) { return; }
        listener.onSentence(index);
        if (backend != SpeechBackend.DEVICE) {
            final int position = index;
            Download job = download(position);
            listener.onStatus(job.waiting.isEmpty() && backend == SpeechBackend.ELEVENLABS ? UiText.PREPARING_ELEVEN.text() : job.waiting);
            if (backend != SpeechBackend.ELEVENLABS && position + 1 < sentences.size()) { download(position + 1); }
            job.result.whenComplete((file, error) -> handler.post(() -> {
                if (!valid(token) || index != position) { return; }
                if (error != null) {
                    Throwable reported = error;
                    while (reported instanceof java.util.concurrent.CompletionException && reported.getCause() != null) {
                        reported = reported.getCause();
                    }
                    fail(reported instanceof IllegalArgumentException || reported instanceof IllegalStateException
                            ? reported.getMessage() : UiText.GENERATE_SPEECH_FAILED.text());
                    return;
                }
                // Queue speculative synthesis only after the current recording is available.
                // This prevents a later sentence from acquiring a provider slot before startup.
                if (backend == SpeechBackend.ELEVENLABS) {
                    for (int next = position + 1; next < Math.min(sentences.size(), position + 4); next++) { download(next); }
                }
                listener.onStatus("");
                waitSilently(first ? 500 : pauseMs, token, () -> play(file, token));
            }));
        } else {
            waitSilently(first ? 500 : pauseMs, token, () -> {
                if (tts.speak(sentences.get(index), TextToSpeech.QUEUE_FLUSH, null, "sentence-" + token + "-" + index) == TextToSpeech.ERROR) {
                    fail(UiText.DEVICE_SPEECH_FAILED.text());
                }
            });
        }
    }

    private static AudioAttributes attributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void play(File file, int token) {
        try {
            releaseMedia();
            media = new MediaPlayer();
            media.setAudioAttributes(attributes());
            media.setDataSource(file.getAbsolutePath());
            media.setOnPreparedListener(player -> {
                if (valid(token) && media == player) {
                    try {
                        player.start();
                        if (recordingRequest == null && backend != SpeechBackend.OPENAI) {
                            player.setPlaybackParams(new android.media.PlaybackParams().setSpeed((float) speed).setPitch(1.0f));
                        }
                        listener.onSpeaking(index);
                    } catch (RuntimeException e) { fail(UiText.PLAYBACK_SPEED_FAILED.text()); }
                }
            });
            media.setOnCompletionListener(player -> { if (valid(token) && media == player) { releaseMedia(); complete(token); } });
            media.setOnErrorListener((player, what, extra) -> {
                if (valid(token) && media == player) {
                    invalidate(file);
                    fail(UiText.GENERATED_PLAYBACK_FAILED.text());
                }
                return true;
            });
            media.prepareAsync();
        } catch (Exception e) { invalidate(file); fail(UiText.GENERATED_PLAYBACK_FAILED.text()); }
    }

    private void invalidate(File file) {
        if (recordingRequest != null) { SentenceAudio.store(context).open(recordingRequest).invalidate(index); }
        else { file.delete(); }
    }

    private void complete(int token) {
        if (!valid(token)) { return; }
        listener.onCompletedSentence(index);
        downloads.remove(index);
        if (++index >= sentences.size()) { stop(); listener.onFinished(); }
        else { next(token, false); }
    }

    private void waitSilently(int duration, int token, Runnable action) {
        // Keep Bluetooth output awake across meditation pauses.
        if (duration > 0) {
            try {
                int samples = 16000 * duration / 1000;
                silence = new AudioTrack.Builder().setAudioAttributes(attributes())
                        .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(samples * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                silence.write(new short[samples], 0, samples);
                silence.play();
            } catch (RuntimeException e) { releaseSilence(); }
        }
        handler.postDelayed(() -> {
            if (!valid(token)) { return; }
            releaseSilence();
            action.run();
        }, duration);
    }

    private void fail(String error) { stop(); listener.onError(error); }
    private void releaseMedia() { if (media != null) { media.release(); media = null; } }
    private void releaseSilence() { if (silence != null) { silence.release(); silence = null; } }

    /** Native providers use local speed; update the current recording without regeneration. */
    public boolean setPlaybackSpeed(double value) {
        de.jeisfeld.mymeditation.http.SpeechProtocol.validateSpeed(value);
        if (backend != SpeechBackend.DEVICE && recordingRequest != null || backend == SpeechBackend.OPENAI) { return false; }
        speed = value;
        if (backend == SpeechBackend.DEVICE) { if (tts != null) { tts.setSpeechRate((float) value); } return true; }
        try {
            if (media != null && media.isPlaying()) {
                media.setPlaybackParams(new android.media.PlaybackParams().setSpeed((float) value).setPitch(1.0f));
            }
            return true;
        } catch (RuntimeException e) { return false; }
    }

    public void setPauseSeconds(double seconds) { pauseMs = (int) Math.round(Math.max(0, seconds) * 1000); }

    private void stopPlaybackResources() {
        session++;
        running = false;
        // Do not remove handler messages here: a focus GAIN may already be queued.
        for (Download job : downloads.values()) { job.cancelled = true; job.request.cancel(); job.result.cancel(true); }
        downloads.clear();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        releaseMedia(); releaseSilence();
    }

    public void stop() {
        focusRecovery.cancel(); focusSession++;
        stopPlaybackResources();
        handler.removeCallbacksAndMessages(null);
        if (focus != null) {
            ((android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocusRequest(focus);
            focus = null;
        }
        listener.onWaitingForAudioFocus(false);
        listener.onStatus("");
        listener.onRunning(false);
    }

    public void close() { stop(); executor.shutdownNow(); elevenExecutor.shutdownNow(); }
}
