package de.jeisfeld.mymeditation.audio;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.io.File;

/** Sharing is the only operation which assembles sentence recordings into one MP3. */
public final class AudioSharing {
    private AudioSharing() { }
    public static void request(Fragment owner, int id) {
        SentenceAudioStore.Book book = SavedAudioLibrary.book(owner.requireContext(), id);
        String name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id);
        if (book == null || !book.isPublished() && OfflineMeditations.file(owner.requireContext(), id).isFile()) {
            open(owner, OfflineMeditations.file(owner.requireContext(), id), name); return;
        }
        if (MeditationExportService.state().getValue().active) {
            Toast.makeText(owner.requireContext(), R.string.audio_operation_active, Toast.LENGTH_LONG).show(); return;
        }
        if (!book.isComplete()) {
            Toast.makeText(owner.requireContext(), R.string.recorded_audio_unavailable, Toast.LENGTH_LONG).show(); return;
        }
        EditText input = new EditText(owner.requireContext());
        input.setSingleLine(true); input.setText("2"); input.selectAll();
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setContentDescription(owner.getString(R.string.share_pause_prompt));
        LinearLayout container = new LinearLayout(owner.requireContext());
        int padding = owner.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(owner.requireContext()).setTitle(R.string.button_share_offline)
                .setMessage(R.string.share_pause_prompt).setView(container)
                .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_share_offline, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double pause = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                if (!Double.isFinite(pause) || pause < 0 || pause > 30) { throw new IllegalArgumentException(); }
                if (MeditationExportService.state().getValue().active) {
                    Toast.makeText(owner.requireContext(), R.string.audio_operation_active, Toast.LENGTH_LONG).show(); return;
                }
                MeditationExportService.share(owner.requireContext(), id, pause);
                dialog.dismiss();
            } catch (IllegalArgumentException e) { input.setError(owner.getString(R.string.pause_value_range)); }
            catch (RuntimeException e) { Toast.makeText(owner.requireContext(), R.string.offline_share_failed, Toast.LENGTH_LONG).show(); }
        }));
        dialog.show();
    }
    public static void open(Fragment owner, File file, String name) {
        try {
            if (!file.isFile()) { throw new IllegalStateException(); }
            if (name == null || name.isEmpty()) { name = owner.getString(R.string.default_offline_name); }
            String filename = name.replace('/', '_').replace('\\', '_').replaceAll("[\\p{Cntrl}]", "_") + ".mp3";
            Uri uri = FileProvider.getUriForFile(owner.requireContext(), owner.requireContext().getPackageName() + ".offlinefiles", file, filename);
            Intent share = new Intent(Intent.ACTION_SEND).setType("audio/mpeg").putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TITLE, name).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share.setClipData(ClipData.newRawUri(name, uri));
            owner.startActivity(Intent.createChooser(share, owner.getString(R.string.button_share_offline)));
        } catch (RuntimeException e) { Toast.makeText(owner.requireContext(), R.string.offline_share_failed, Toast.LENGTH_LONG).show(); }
    }
}
