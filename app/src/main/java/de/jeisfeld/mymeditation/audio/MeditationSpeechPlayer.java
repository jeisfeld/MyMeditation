package de.jeisfeld.mymeditation.audio;

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
    }
    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<Integer, Download> downloads = new HashMap<>();
    private android.media.AudioFocusRequest focus;
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

    private static final class Download {
        final CloudSpeech request = new CloudSpeech();
        CompletableFuture<File> result;
        String waiting = "";
    }

    public MeditationSpeechPlayer(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start(List<String> text, int position, int pauseSeconds, SpeechBackend backend, String model, String voice, double speed, String style) {
        stop();
        if (text.isEmpty()) { return; }
        sentences = new ArrayList<>(text);
        index = Math.max(0, Math.min(position, text.size() - 1));
        pauseMs = Math.max(0, pauseSeconds) * 1000;
        this.backend = backend;
        this.model = model;
        this.voice = voice;
        this.speed = speed;
        this.style = style;
        running = true;
        listener.onRunning(true);
        final int token = session;
        android.media.AudioManager audio = (android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        focus = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes()).setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && valid(token)) { stop(); }
                }, handler).build();
        if (audio.requestAudioFocus(focus) != android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            fail("Audio output is currently unavailable. Please try again.");
            return;
        }
        if (backend != SpeechBackend.DEVICE) {
            next(token, true);
        } else {
            tts = new TextToSpeech(context, status -> handler.post(() -> {
                if (!valid(token)) { return; }
                if (status != TextToSpeech.SUCCESS) { fail("Device text-to-speech could not start."); return; }
                tts.setAudioAttributes(attributes());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { handler.post(() -> complete(token)); }
                    @Override public void onError(String id) { handler.post(() -> { if (valid(token)) { fail("Device speech failed."); } }); }
                });
                next(token, true);
            }));
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
                current.waiting = "ElevenLabs temporarily unavailable. Retrying in " + (delay / 1000)
                        + " s (retry " + attempt + "). Pause to stop.";
                if (index == position) { listener.onStatus(current.waiting); }
            }));
            final String text = sentences.get(position);
            final SpeechBackend speechBackend = backend;
            final String speechModel = model;
            final String speechVoice = voice;
            final double speechSpeed = speed;
            final String speechStyle = style;
            job.result = CompletableFuture.supplyAsync(() -> {
                try { return current.request.audioWithPersistentCache(context.getCacheDir(), context.getNoBackupFilesDir(), speechBackend, speechModel, speechVoice, text, speechSpeed, speechStyle); }
                catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
            }, executor);
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
            listener.onStatus(job.waiting);
            int ahead = backend == SpeechBackend.ELEVENLABS ? 3 : 1;
            for (int next = position + 1; next < Math.min(sentences.size(), position + ahead + 1); next++) { download(next); }
            job.result.whenComplete((file, error) -> handler.post(() -> {
                if (!valid(token) || index != position) { return; }
                if (error != null) {
                    Throwable reported = error;
                    while (reported instanceof java.util.concurrent.CompletionException && reported.getCause() != null) {
                        reported = reported.getCause();
                    }
                    fail(reported instanceof IllegalArgumentException || reported instanceof IllegalStateException
                            ? reported.getMessage() : "Could not generate speech. Check your connection and try again.");
                    return;
                }
                listener.onStatus("");
                waitSilently(first ? 500 : pauseMs, token, () -> play(file, token));
            }));
        } else {
            waitSilently(first ? 500 : pauseMs, token, () -> {
                if (tts.speak(sentences.get(index), TextToSpeech.QUEUE_FLUSH, null, "sentence-" + token + "-" + index) == TextToSpeech.ERROR) {
                    fail("Device speech failed.");
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
                        if (backend != SpeechBackend.OPENAI) {
                            player.setPlaybackParams(new android.media.PlaybackParams().setSpeed((float) speed).setPitch(1.0f));
                        }
                    } catch (RuntimeException e) { fail("Audio playback failed. Try speed 1.0."); }
                }
            });
            media.setOnCompletionListener(player -> { if (valid(token) && media == player) { releaseMedia(); complete(token); } });
            media.setOnErrorListener((player, what, extra) -> {
                if (valid(token) && media == player) {
                    file.delete();
                    fail("Could not play the generated audio. Please try again.");
                }
                return true;
            });
            media.prepareAsync();
        } catch (Exception e) { file.delete(); fail("Could not play the generated audio. Please try again."); }
    }

    private void complete(int token) {
        if (!valid(token)) { return; }
        downloads.remove(index);
        if (++index >= sentences.size()) { stop(); }
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

    public void setPauseSeconds(int seconds) { pauseMs = Math.max(0, seconds) * 1000; }

    public void stop() {
        session++;
        running = false;
        handler.removeCallbacksAndMessages(null);
        for (Download job : downloads.values()) { job.request.cancel(); job.result.cancel(true); }
        downloads.clear();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        releaseMedia();
        releaseSilence();
        if (focus != null) {
            ((android.media.AudioManager) context.getSystemService(Context.AUDIO_SERVICE)).abandonAudioFocusRequest(focus);
            focus = null;
        }
        listener.onStatus("");
        listener.onRunning(false);
    }

    public void close() { stop(); executor.shutdownNow(); }
}
