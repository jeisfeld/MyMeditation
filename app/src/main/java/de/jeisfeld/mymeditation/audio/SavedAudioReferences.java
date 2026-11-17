package de.jeisfeld.mymeditation.audio;

import android.content.Context;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.http.CloudSpeech;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Shared ownership rule for accounting and deletion, independent of complete/offline publication. */
final class SavedAudioReferences {
    final Set<String> books = new HashSet<>(), clips = new HashSet<>();
    final Set<Path> caches = new HashSet<>();
    static SavedAudioReferences collect(Context context) {
        SavedAudioReferences result = new SavedAudioReferences();
        SentenceAudioStore store = SentenceAudio.store(context);
        result.books.addAll(SavedAudioLibrary.referencedRecordings());
        result.clips.addAll(store.clips(result.books));
        for (int id : PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids)) {
            if (SavedAudioLibrary.audioDetached(id)) { continue; }
            PlaybackRequest request = SavedAudioLibrary.storedSpeechRequest(context, id);
            if (request != null) { result.add(context, request); }
            SentenceAudioStore.Book linked = SavedAudioLibrary.book(context, id);
            if (linked != null) { result.add(context, linked.request); }
        }
        return result;
    }
    private void add(Context context, PlaybackRequest request) {
        if (request.backend == SpeechBackend.DEVICE) { return; }
        books.add(SentenceAudioStore.key(request));
        for (String sentence : SpeechSegments.split(request.text, request.backend.segmentLimit)) {
            if (MeditationPauses.seconds(sentence) >= 0) { continue; }
            clips.add(SentenceAudioStore.clipKey(request, sentence));
            try {
                for (File file : CloudSpeech.cacheFiles(context, request.backend, request.model, request.voice, sentence, request.speed, request.style)) {
                    caches.add(file.toPath().toAbsolutePath().normalize());
                }
            } catch (Exception ignored) { /* Known global sources remain referenced if an older cache format is unsupported. */ }
        }
    }
}
