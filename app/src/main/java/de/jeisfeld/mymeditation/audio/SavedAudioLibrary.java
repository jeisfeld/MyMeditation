package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread library links. Multiple named entries can share the same immutable sentence recording. */
public final class SavedAudioLibrary {
    private static final MutableLiveData<Long> CHANGES = new MutableLiveData<>(0L);
    private static final Set<String> DELETED = ConcurrentHashMap.newKeySet();
    private static final Object LIFECYCLE = new Object();
    private static final java.util.concurrent.ExecutorService CLEANER = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "sentence-audio-cleanup"); thread.setDaemon(true); return thread;
    });
    private SavedAudioLibrary() { }
    public static LiveData<Long> changes() { return CHANGES; }
    private static final String RESTORED_SOURCE = "restored_meditation_source";
    public static void clearSource() { PreferenceUtil.setSharedPreferenceString(RESTORED_SOURCE, "0"); }
    private static String link(int id) { return "sentence_audio_" + id; }

    public static final class Snapshot {
        public final String name, system, query, content;
        public final int sourceId;
        public Snapshot(String name, String system, String query, String content) {
            this(name, system, query, content, 0);
        }
        private Snapshot(String name, String system, String query, String content, int sourceId) {
            this.sourceId = sourceId;
            this.name = clean(name); this.system = clean(system); this.query = clean(query); this.content = clean(content);
        }
        public static Snapshot current(Context context) {
            String content = clean(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
            String name = content.trim();
            if (name.length() > 80) { name = name.substring(0, Character.isHighSurrogate(name.charAt(79)) ? 79 : 80); }
            if (name.isEmpty()) { name = context.getString(R.string.default_offline_name); }
            Snapshot draft = new Snapshot(name, PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt),
                    PreferenceUtil.getSharedPreferenceString(R.string.key_query_template), content);
            int source;
            try { source = Integer.parseInt(PreferenceUtil.getSharedPreferenceString(RESTORED_SOURCE, "0")); }
            catch (NumberFormatException e) { source = 0; }
            if (PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(source)
                    && draft.matches(source, PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text))) {
                return new Snapshot(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, source),
                        draft.system, draft.query, draft.content, source);
            }
            return draft;
        }
        boolean matches(int id, String text) {
            return Objects.equals(system, clean(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_system_prompt, id)))
                    && Objects.equals(query, clean(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_query_template, id)))
                    && Objects.equals(content, clean(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_content, id)))
                    && Objects.equals(text, PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id));
        }
    }
    private static String clean(String value) { return value == null ? "" : value; }
    public static SentenceAudioStore.Book book(Context context, int id) {
        SentenceAudioStore.Book book = SentenceAudio.store(context).find(PreferenceUtil.getSharedPreferenceString(link(id), ""));
        return book != null && book.request.text.equals(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id)) ? book : null;
    }
    public static boolean hasAudio(Context context, int id) {
        SentenceAudioStore.Book book = book(context, id);
        // The completion marker is atomic publication. Full file validation happens on play/share,
        // rather than opening hundreds of MP3s every time a saved-list row is bound or filtered.
        return book != null && book.isPublished();
    }
    public static void begin(PlaybackRequest request) {
        synchronized (LIFECYCLE) {
            if (request.backend != SpeechBackend.DEVICE) { DELETED.remove(SentenceAudioStore.key(request)); }
        }
    }

    /** Only called on main: ID allocation remains serialized with ordinary text saves. */
    public static int keepComplete(Context context, PlaybackRequest request, Snapshot snapshot) {
        synchronized (LIFECYCLE) { return keep(context, request, snapshot); }
    }
    private static int keep(Context context, PlaybackRequest request, Snapshot snapshot) {
        SentenceAudioStore.Book book = SentenceAudio.store(context).open(request);
        if (!book.isComplete() || DELETED.contains(book.identity)) { return 0; }
        int target = 0;
        List<Integer> ids = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
        int source = ids.contains(snapshot.sourceId) && snapshot.matches(snapshot.sourceId, request.text) ? snapshot.sourceId : 0;
        if (source > 0) {
            if (book.identity.equals(PreferenceUtil.getSharedPreferenceString(link(source), ""))) { return source; }
            if (!hasAudio(context, source)) { target = source; }
            else {
                // Repeated completion callbacks/replays reuse the already-created variant.
                for (int id : ids) {
                    if (PreferenceUtil.getSharedPreferenceString("audio_variant_source_" + id, "").equals(Integer.toString(source))
                            && book.identity.equals(PreferenceUtil.getSharedPreferenceString(link(id), ""))
                            && snapshot.matches(id, request.text)) { return id; }
                }
            }
        } else {
            for (int id : ids) {
                if (book.identity.equals(PreferenceUtil.getSharedPreferenceString(link(id), ""))
                        && request.text.equals(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id))) { return id; }
                if (target == 0 && snapshot.matches(id, request.text) && !hasAudio(context, id)) { target = id; }
            }
        }
        if (target == 0) {
            String name = source > 0 ? PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, source) : snapshot.name;
            target = SavedMeditations.save(name, snapshot.system, snapshot.query, snapshot.content, request.text);
            if (source > 0) {
                java.util.ArrayList<Integer> order = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
                order.remove(Integer.valueOf(target));
                order.add(order.indexOf(source) + 1, target);
                PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, order);
                PreferenceUtil.setSharedPreferenceString("audio_variant_source_" + target, Integer.toString(source));
            }
        }
        PreferenceUtil.setSharedPreferenceString(link(target), book.identity);
        DELETED.remove(book.identity);
        CHANGES.setValue(CHANGES.getValue() + 1);
        return target;
    }
    public static void attachAvailable(Context context, int id) {
        synchronized (LIFECYCLE) { attach(context, id); }
    }
    private static void attach(Context context, int id) {
        try {
            PlaybackRequest request = MeditationPlaybackService.currentRequest(
                    PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, id), PreferenceUtil.getPauseSeconds());
            if (request.backend != SpeechBackend.DEVICE && SentenceAudio.store(context).open(request).isComplete()) {
                PreferenceUtil.setSharedPreferenceString(link(id), SentenceAudioStore.key(request));
            }
        } catch (IllegalArgumentException ignored) { }
    }
    /** Restore only synthesis settings. Pauses remain independently adjustable during replay. */
    public static void restoreVoice(PlaybackRequest request) {
        VoiceProfiles profiles = new VoiceProfiles(PreferenceUtil.getSharedPreferenceString(VoiceProfiles.PREFERENCE, "{}"));
        profiles.setSpeed(request.backend, request.voice, request.speed);
        if (request.backend == SpeechBackend.ELEVENLABS) { profiles.migrateVoice(request.voice); }
        PreferenceUtil.setSharedPreferenceString(VoiceProfiles.PREFERENCE, profiles.json());
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, request.backend.id);
        PreferenceUtil.setSharedPreferenceString(request.backend.modelKey(), request.model);
        PreferenceUtil.setSharedPreferenceString(request.backend.voiceKey(), request.voice);
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_speed, Double.toString(request.speed));
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_style, request.style);
    }
    public static void restore(Context context, int id) {
        PreferenceUtil.setSharedPreferenceString(RESTORED_SOURCE, Integer.toString(id));
        int[][] fields = {{R.string.key_system_prompt, R.string.key_saved_system_prompt},
                {R.string.key_query_template, R.string.key_saved_query_template},
                {R.string.key_meditation_content, R.string.key_saved_meditation_content},
                {R.string.key_meditation_text, R.string.key_saved_meditation_text}};
        for (int[] field : fields) {
            PreferenceUtil.setSharedPreferenceString(field[0], PreferenceUtil.getIndexedSharedPreferenceString(field[1], id));
        }
        SentenceAudioStore.Book recording = book(context, id);
        if (recording != null) { restoreVoice(recording.request); return; }

    }
    public static void unlink(int id) {
        synchronized (LIFECYCLE) {
            String identity = PreferenceUtil.getSharedPreferenceString(link(id), "");
            if (!identity.isEmpty()) { DELETED.add(identity); }
            PreferenceUtil.setSharedPreferenceString(link(id), "");
            PreferenceUtil.setSharedPreferenceString("audio_variant_source_" + id, "");
            if (Integer.toString(id).equals(PreferenceUtil.getSharedPreferenceString(RESTORED_SOURCE, "0"))) { clearSource(); }
        }
    }
    public static void cleanupDeleted(Context context) {
        Context application = context.getApplicationContext();
        CLEANER.execute(() -> {
            synchronized (LIFECYCLE) {
                if (MeditationPlaybackService.state().getValue().active || MeditationExportService.state().getValue().active) { return; }
                List<Integer> ids = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
                for (String identity : DELETED) {
                    boolean referenced = false;
                    for (int id : ids) {
                        if (identity.equals(PreferenceUtil.getSharedPreferenceString(link(id), ""))) { referenced = true; break; }
                    }
                    if (!referenced) {
                        SentenceAudio.store(application).delete(identity);
                    }
                }
            }
        });
    }
}
