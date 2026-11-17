package de.jeisfeld.mymeditation.audio;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import de.jeisfeld.mymeditation.http.SpeechProtocol;
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
        if (MeditationExportService.state().getValue().active) {
            Toast.makeText(owner.requireContext(), R.string.audio_operation_active, Toast.LENGTH_LONG).show(); return;
        }
        if (book == null || !book.isComplete()) {
            Toast.makeText(owner.requireContext(), R.string.recorded_audio_unavailable, Toast.LENGTH_LONG).show(); return;
        }
        View container = owner.getLayoutInflater().inflate(R.layout.dialog_share_audio, null);
        EditText pauseInput = container.findViewById(R.id.inputSharePause);
        EditText speedInput = container.findViewById(R.id.inputShareSpeed);
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(owner.getResources().getConfiguration().getLocales().get(0));
        format.setGroupingUsed(false); format.setMaximumFractionDigits(3);
        pauseInput.setText(format.format(PreferenceUtil.getPauseSeconds()));
        speedInput.setText(format.format(MeditationPlaybackService.currentSpeed()));
        container.findViewById(R.id.shareSpeedFields).setVisibility(book.request.localSpeed() ? View.VISIBLE : View.GONE);
        AlertDialog dialog = new AlertDialog.Builder(owner.requireContext()).setTitle(R.string.button_share_offline)
                .setView(container)
                .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_share_offline, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            double pause, speed = book.request.speed;
            try {
                pause = Double.parseDouble(pauseInput.getText().toString().trim().replace(',', '.'));
                if (!Double.isFinite(pause) || pause < 0 || pause > 30) { throw new IllegalArgumentException(); }
            } catch (IllegalArgumentException e) { pauseInput.setError(owner.getString(R.string.pause_value_range)); return; }
            if (book.request.localSpeed()) {
                try {
                    speed = Double.parseDouble(speedInput.getText().toString().trim().replace(',', '.'));
                    SpeechProtocol.validateSpeed(speed);
                } catch (IllegalArgumentException e) { speedInput.setError(owner.getString(R.string.speech_speed_label)); return; }
            }
            try {
                if (MeditationExportService.state().getValue().active) {
                    Toast.makeText(owner.requireContext(), R.string.audio_operation_active, Toast.LENGTH_LONG).show(); return;
                }
                MeditationExportService.share(owner.requireContext(), id, pause, speed);
                dialog.dismiss();
            } catch (RuntimeException e) { Toast.makeText(owner.requireContext(), R.string.offline_share_failed, Toast.LENGTH_LONG).show(); }
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
