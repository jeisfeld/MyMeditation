package de.jeisfeld.mymeditation.ui.configuration;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.AppStorage;
import de.jeisfeld.mymeditation.audio.MeditationExportService;
import de.jeisfeld.mymeditation.audio.MeditationPlaybackService;
import de.jeisfeld.mymeditation.databinding.DialogAiSettingsBinding;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dialog-scoped background accounting; cleanup never blocks the UI or touches a saved recording. */
final class StorageSettings implements AutoCloseable {
    private final Fragment owner;
    private final Context context;
    private final androidx.lifecycle.Lifecycle lifecycle;
    private final androidx.lifecycle.LifecycleEventObserver destroyed = (source, event) -> {
        if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY) { close(); }
    };
    private final DialogAiSettingsBinding binding;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> { Thread t = new Thread(task, "audio-storage"); t.setDaemon(true); return t; });
    private final Observer<Boolean> cleanup = ignored -> render();
    private final Observer<MeditationPlaybackService.State> playback = state -> render();
    private final Observer<MeditationExportService.State> sharing = state -> render();
    private AppStorage.Usage usage;
    private boolean loading, closed;
    StorageSettings(Fragment owner, DialogAiSettingsBinding binding) {
        this.owner = owner; this.binding = binding; context = owner.requireContext().getApplicationContext();
        lifecycle = owner.getViewLifecycleOwner().getLifecycle(); lifecycle.addObserver(destroyed);
        binding.buttonRefreshStorage.setOnClickListener(v -> refresh());
        binding.buttonClearOtherAudio.setOnClickListener(v -> new AlertDialog.Builder(owner.requireContext())
                .setTitle(R.string.clear_other_audio).setMessage(R.string.clear_other_audio_confirmation)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.button_delete, (dialog, which) -> clear()).show());
        AppStorage.cleaning().observe(owner.getViewLifecycleOwner(), cleanup);
        MeditationPlaybackService.state().observe(owner.getViewLifecycleOwner(), playback);
        MeditationExportService.state().observe(owner.getViewLifecycleOwner(), sharing);
        refresh();
    }
    private void render() {
        if (closed) { return; }
        boolean busy = AppStorage.audioActive() || AppStorage.isCleaning();
        binding.buttonRefreshStorage.setEnabled(!loading && !AppStorage.isCleaning());
        binding.buttonClearOtherAudio.setEnabled(!loading && !busy && usage != null && usage.otherAudio > 0);
        binding.textStorageStatus.setText(AppStorage.isCleaning() ? R.string.storage_cleaning : loading ? R.string.storage_calculating : busy ? R.string.storage_stop_first : R.string.storage_note);
    }
    private void show(AppStorage.Usage measured) {
        usage = measured;
        binding.textStorageOffline.setText(context.getString(R.string.storage_offline_size, Formatter.formatFileSize(context, measured.offlineAudio)));
        binding.textStorageOtherAudio.setText(context.getString(R.string.storage_other_audio_size, Formatter.formatFileSize(context, measured.otherAudio)));
        binding.textStorageOtherData.setText(context.getString(R.string.storage_other_data_size, Formatter.formatFileSize(context, measured.otherData)));
    }
    private void refresh() {
        if (closed || loading) { return; }
        loading = true; render();
        worker.execute(() -> {
            try {
                AppStorage.Usage measured = AppStorage.measure(context);
                main.post(() -> { if (!closed) { loading = false; show(measured); render(); } });
            } catch (Exception e) {
                main.post(() -> { if (!closed) { loading = false; render(); binding.textStorageStatus.setText(R.string.storage_measure_error); } });
            }
        });
    }
    private void clear() {
        if (closed) { return; }
        if (!AppStorage.beginCleaning()) { Toast.makeText(context, R.string.storage_stop_first, Toast.LENGTH_LONG).show(); return; }
        loading = true; render();
        worker.execute(() -> {
            int result = R.string.storage_cleared;
            try { AppStorage.clearOtherAudio(context); }
            catch (Exception e) { result = R.string.storage_clear_error; }
            finally { AppStorage.endCleaning(); }
            final int message = result;
            main.post(() -> {
                if (closed) { return; }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show(); loading = false; refresh();
            });
        });
    }
    @Override public void close() {
        if (closed) { return; }
        closed = true; lifecycle.removeObserver(destroyed);
        AppStorage.cleaning().removeObserver(cleanup);
        MeditationPlaybackService.state().removeObserver(playback);
        MeditationExportService.state().removeObserver(sharing);
        worker.shutdown();
    }
}
