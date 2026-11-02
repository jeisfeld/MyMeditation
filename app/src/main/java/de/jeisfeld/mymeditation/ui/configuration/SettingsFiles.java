package de.jeisfeld.mymeditation.ui.configuration;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import de.jeisfeld.mymeditation.R;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/** Storage Access Framework transport; only settings snapshots cross this boundary. */
final class SettingsFiles {
    private final Fragment fragment;
    private final Runnable restored;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ActivityResultLauncher<String> export;
    private final ActivityResultLauncher<String[]> restore;
    private String pendingExport;
    SettingsFiles(Fragment fragment, Runnable restored) {
        this.fragment = fragment; this.restored = restored;
        export = fragment.registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), this::write);
        restore = fragment.registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::read);
    }
    void saveState(Bundle state) { state.putString("settingsExportFile", pendingExport); }
    void restoreState(Bundle state) { if (state != null) { pendingExport = state.getString("settingsExportFile"); } }
    boolean export(Map<String, String> settings) {
        try {
            String json = SettingsBackup.encode(settings);
            File temporary = File.createTempFile("settings-export-", ".json", fragment.requireContext().getCacheDir());
            Files.write(temporary.toPath(), json.getBytes(StandardCharsets.UTF_8));
            pendingExport = temporary.getName();
            export.launch("MyMeditation-settings.json"); return true;
        } catch (Exception e) {
            if (fragment.getContext() != null) { File file = pending(fragment.requireContext()); if (file != null) { file.delete(); } }
            pendingExport = null;
            message(R.string.settings_file_error); return false;
        }
    }
    void restore() {
        try { restore.launch(new String[] {"application/json", "text/plain", "application/octet-stream"}); }
        catch (RuntimeException e) { message(R.string.settings_file_error); }
    }
    private File pending(Context context) {
        return pendingExport != null && pendingExport.matches("settings-export-[A-Za-z0-9_-]+\\.json")
                ? new File(context.getCacheDir(), pendingExport) : null;
    }
    private void write(Uri uri) {
        Context context = fragment.requireContext().getApplicationContext();
        File source = pending(context); pendingExport = null;
        if (source == null) { if (uri != null) { message(R.string.settings_file_error); } return; }
        if (uri == null) { source.delete(); return; }
        new Thread(() -> {
            try (InputStream input = Files.newInputStream(source.toPath()); OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) { throw new java.io.IOException(); }
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) { output.write(buffer, 0, count); }
                output.flush();
            } catch (Exception e) { main.post(() -> message(R.string.settings_file_error)); return; }
            finally { source.delete(); }
            main.post(() -> message(R.string.settings_exported));
        }, "settings-export").start();
    }
    private void read(Uri uri) {
        if (uri == null) { return; }
        Context context = fragment.requireContext().getApplicationContext();
        new Thread(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(uri); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                if (input == null) { throw new java.io.IOException(); }
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    if (bytes.size() + count > SettingsBackup.MAX_BYTES) { throw new java.io.IOException(); }
                    bytes.write(buffer, 0, count);
                }
                Map<String, String> checked = SettingsBackup.decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
                main.post(() -> confirm(checked));
            } catch (Exception e) { main.post(() -> message(R.string.settings_import_error)); }
        }, "settings-import").start();
    }
    private void confirm(Map<String, String> settings) {
        if (!fragment.isAdded() || fragment.getView() == null) { return; }
        new AlertDialog.Builder(fragment.requireContext()).setTitle(R.string.import_settings)
                .setMessage(R.string.settings_replace_confirmation)
                .setNegativeButton(R.string.button_cancel, null)
                .setPositiveButton(R.string.import_settings, (dialog, which) -> {
                    try {
                        SettingsBackup.restore(fragment.requireContext(), settings);
                        restored.run(); message(R.string.settings_imported);
                    } catch (Exception e) { message(R.string.settings_restore_error); }
                }).show();
    }
    private void message(int resource) {
        Activity activity = fragment.getActivity();
        if (activity != null && !activity.isFinishing()) { Toast.makeText(activity, resource, Toast.LENGTH_LONG).show(); }
    }
}
