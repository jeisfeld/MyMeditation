package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.*;
import de.jeisfeld.mymeditation.databinding.DialogSavedMeditationBinding;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.text.NumberFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Name editing and word-weighted offline availability, scoped to the dialog's lifetime. */
final class SavedMeditationEditor implements AutoCloseable {
    private final Fragment owner;
    private final int id;
    private final DialogSavedMeditationBinding view;
    private final AlertDialog dialog;
    private final androidx.lifecycle.Lifecycle viewLifecycle;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> { Thread thread = new Thread(task, "offline-coverage"); thread.setDaemon(true); return thread; });
    private final Handler main = new Handler(Looper.getMainLooper());
    private PlaybackRequest request;
    private OfflineCoverage coverage;
    private String failure = "";
    private boolean closed, loading;
    private int revision;
    private final Observer<MeditationOfflineService.State> preparation = this::preparationChanged;
    private void preparationChanged(MeditationOfflineService.State state) {
        revision++;
        if (state.active && state.savedId == id) { if (state.coverage != null) { coverage = state.coverage; } render(); }
        else { refresh(); }
    }
    private final Observer<Boolean> cleanup = ignored -> changed();
    private final Observer<Long> changed = ignored -> changed();
    private void changed() { revision++; refresh(); }
    private final Observer<MeditationPlaybackService.State> playback = ignored -> render();
    private final Observer<MeditationExportService.State> export = ignored -> render();
    private final androidx.lifecycle.LifecycleEventObserver lifecycle = (source, event) -> destroyed(event);
    private void destroyed(androidx.lifecycle.Lifecycle.Event event) {
        if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) { dialog.dismiss(); close(); }
    }
    static void show(Fragment owner, int id) { new SavedMeditationEditor(owner, id); }
    private SavedMeditationEditor(Fragment owner, int id) {
        this.owner = owner; this.id = id;
        viewLifecycle = owner.getViewLifecycleOwner().getLifecycle();
        view = DialogSavedMeditationBinding.inflate(owner.getLayoutInflater());
        view.editSavedName.setText(PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id)); view.editSavedName.selectAll();
        try {
            request = SavedAudioLibrary.offlineRequest(owner.requireContext(), id);
            String voice = request.voice;
            if (request.backend == SpeechBackend.ELEVENLABS) {
                String named = new VoiceProfiles(PreferenceUtil.getSharedPreferenceString(VoiceProfiles.PREFERENCE, "{}")).name(voice);
                if (named != null && !named.isEmpty()) { voice = named; }
            }
            view.textOfflineVoice.setText(owner.getString(R.string.offline_voice, voice, request.model));
        } catch (Exception error) { failure = owner.getString(R.string.offline_prepare_failed); }
        dialog = new AlertDialog.Builder(owner.requireContext()).setTitle(R.string.rename_saved_meditation).setView(view.getRoot())
                .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_save, null).create();
        dialog.setOnDismissListener(ignored -> close()); dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = view.editSavedName.getText().toString().trim();
            if (name.isEmpty()) { view.editSavedName.setError(owner.getString(R.string.saved_meditation_name_required)); return; }
            SavedMeditations.rename(id, name); dialog.dismiss();
        });
        view.buttonPrepareOffline.setOnClickListener(v -> {
            failure = "";
            MeditationOfflineService.State state = MeditationOfflineService.state().getValue();
            if (state.active && state.savedId == id) { MeditationOfflineService.cancel(owner.requireContext(), id); }
            else {
                try { MeditationOfflineService.prepare(owner.requireContext(), id, request); }
                catch (RuntimeException error) { failure = owner.getString(R.string.offline_prepare_failed); render(); }
            }
        });
        owner.getViewLifecycleOwner().getLifecycle().addObserver(lifecycle);
        refresh();
        MeditationOfflineService.state().observe(owner.getViewLifecycleOwner(), preparation);
        AppStorage.cleaning().observe(owner.getViewLifecycleOwner(), cleanup);
        SavedAudioLibrary.changes().observe(owner.getViewLifecycleOwner(), changed);
        MeditationPlaybackService.state().observe(owner.getViewLifecycleOwner(), playback);
        MeditationExportService.state().observe(owner.getViewLifecycleOwner(), export);
    }
    private void render() {
        if (closed) { return; }
        MeditationOfflineService.State state = MeditationOfflineService.state().getValue();
        boolean active = state.active && state.savedId == id;
        boolean device = request != null && request.backend == SpeechBackend.DEVICE;
        view.progressOfflineCoverage.setVisibility(device || request == null ? View.GONE : View.VISIBLE);
        view.textOfflineVoice.setVisibility(device || request == null ? View.GONE : View.VISIBLE);
        view.textOfflinePreparationNote.setVisibility(!device && request != null && coverage != null && !coverage.complete ? View.VISIBLE : View.GONE);
        view.progressOfflineCoverage.setIndeterminate(coverage == null);
        if (coverage != null) {
            NumberFormat percent = NumberFormat.getPercentInstance(owner.getResources().getConfiguration().getLocales().get(0)); percent.setMaximumFractionDigits(1);
            view.textOfflineCoverage.setText(owner.getString(R.string.offline_coverage, percent.format(coverage.perMille / 1000.0), coverage.availableWords, coverage.totalWords));
            view.progressOfflineCoverage.setProgress(coverage.perMille / 10);
        } else { view.textOfflineCoverage.setText(device ? R.string.offline_device_notice : R.string.storage_calculating); }
        view.textOfflinePreparation.setText(!failure.isEmpty() ? failure : !active && AppStorage.isCleaning() ? owner.getString(R.string.storage_cleaning)
                : !active && AppStorage.audioActive() ? owner.getString(R.string.offline_prepare_busy) : state.savedId == id ? state.message : "");
        if (request == null) { view.textOfflineCoverage.setText(failure); }
        SentenceAudioStore.Book attached = request != null && !device ? SavedAudioLibrary.book(owner.requireContext(), id) : null;
        boolean linked = attached != null && attached.identity.equals(SentenceAudioStore.key(request));
        view.buttonPrepareOffline.setVisibility(!device && request != null && (active || coverage == null || !coverage.complete || !linked) ? View.VISIBLE : View.GONE);
        view.buttonPrepareOffline.setText(active ? R.string.offline_prepare_cancel : R.string.offline_prepare);
        view.buttonPrepareOffline.setEnabled(active || coverage != null && !loading && !AppStorage.audioActive() && !AppStorage.isCleaning());
    }
    private void refresh() {
        if (closed) { return; }
        if (request == null || request.backend == SpeechBackend.DEVICE || loading) { render(); return; }
        MeditationOfflineService.State state = MeditationOfflineService.state().getValue();
        if (state.active && state.savedId == id) { render(); return; }
        loading = true; render(); int version = revision;
        android.content.Context context = owner.requireContext().getApplicationContext(); PlaybackRequest captured = request;
        worker.execute(() -> {
            try {
                OfflineCoverage result = OfflineCoverage.measure(SentenceAudio.store(context).open(captured));
                main.post(() -> { if (!closed) { loading = false; if (version != revision) { refresh(); } else { coverage = result; render(); } } });
            } catch (Exception error) { main.post(() -> { if (!closed) { loading = false; failure = context.getString(R.string.offline_prepare_failed); render(); } }); }
        });
    }
    @Override public void close() {
        if (closed) { return; } closed = true;
        viewLifecycle.removeObserver(lifecycle);
        MeditationOfflineService.state().removeObserver(preparation); AppStorage.cleaning().removeObserver(cleanup);
        SavedAudioLibrary.changes().removeObserver(changed); MeditationPlaybackService.state().removeObserver(playback);
        MeditationExportService.state().removeObserver(export); worker.shutdownNow();
    }
}
