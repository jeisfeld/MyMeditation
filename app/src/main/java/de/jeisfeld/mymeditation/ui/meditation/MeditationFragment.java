package de.jeisfeld.mymeditation.ui.meditation;

import de.jeisfeld.mymeditation.i18n.UiText;
import android.os.Bundle;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.audio.MeditationPlaybackService;
import de.jeisfeld.mymeditation.audio.SpeechSegments;
import androidx.appcompat.app.AlertDialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentMeditationBinding;
import de.jeisfeld.mymeditation.http.AiClient;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class MeditationFragment extends Fragment {

    private FragmentMeditationBinding binding;

    private List<String> sentences;
    private String pendingSpeechStatus = "";
    private final Runnable showSpeechStatus = () -> {
        if (binding != null && !pendingSpeechStatus.isEmpty()) {
            binding.textSpeechStatus.setText(pendingSpeechStatus);
            binding.textSpeechStatus.setVisibility(View.VISIBLE);
        }
    };

    private MeditationViewModel meditationViewModel;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        meditationViewModel = new ViewModelProvider(requireActivity()).get(MeditationViewModel.class);

        binding = FragmentMeditationBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        root.post(() -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView());

            if (insets != null) {
                Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

                View navView = requireActivity().findViewById(R.id.nav_view);
                int bottomNavHeight = navView.getHeight();
                int navOnlyHeight = Math.max(0, bottomNavHeight - systemInsets.bottom);

                root.setPadding(
                        systemInsets.left,
                        systemInsets.top + navOnlyHeight,
                        systemInsets.right,
                        systemInsets.bottom + navOnlyHeight
                );
            }
        });

        meditationViewModel.getMeditationContent().observe(getViewLifecycleOwner(), binding.editTextMeditationContent::setText);
        meditationViewModel.getMeditationText().observe(getViewLifecycleOwner(), binding.editTextMeditationText::setText);
        meditationViewModel.getPauseDuration().observe(getViewLifecycleOwner(), pauseDuration -> {
            if (pauseDuration == null) {
                pauseDuration = 1.0;
            }
            binding.seekBarPauseDuration.setProgress(de.jeisfeld.mymeditation.audio.PlaybackScales.pauseProgress(pauseDuration));
            binding.textPauseDuration.setText(number(pauseDuration) + " s");
        });
        meditationViewModel.getSeekBarMax().observe(getViewLifecycleOwner(), binding.seekBarAudio::setMax);
        meditationViewModel.getSeekBarProgress().observe(getViewLifecycleOwner(), binding.seekBarAudio::setProgress);
        meditationViewModel.isMeditationRunning().observe(getViewLifecycleOwner(), isMeditationRunning -> {
            binding.buttonSpeakMeditation.setVisibility(isMeditationRunning ? View.GONE : View.VISIBLE);
            binding.buttonPauseMeditation.setVisibility(isMeditationRunning ? View.VISIBLE : View.GONE);
        });

        MeditationPlaybackService.state().observe(getViewLifecycleOwner(), state -> {
            meditationViewModel.setMeditationRunning(state.running);
            if (!state.sentences.isEmpty()) {
                sentences = state.sentences;
                meditationViewModel.setSeekBarMax(Math.max(0, sentences.size() - 1));
                meditationViewModel.setSeekBarProgress(state.position);
            }
            updateSpeechStatus(state.status);
            if (!state.error.isEmpty() && meditationViewModel.consumePlaybackError(state.errorId)) {
                new AlertDialog.Builder(requireContext()).setTitle(R.string.button_speak_meditation)
                        .setMessage(state.error).setPositiveButton(R.string.button_ok, null).show();
            }
        });


        binding.buttonCreateMeditation.setOnClickListener(v -> {
            meditationViewModel.setMeditationContent(binding.editTextMeditationContent.getText().toString());

            stopAudio();
            String systemMessage = PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt);
            String queryTemplate = PreferenceUtil.getSharedPreferenceString(R.string.key_query_template);
            String userMessage = (queryTemplate == null ? "@TEXT@" : queryTemplate).replace("@TEXT@", meditationViewModel.getMeditationContent().getValue());
            String oldMeditation = meditationViewModel.getMeditationText().getValue();
            meditationViewModel.setMeditationText(getString(R.string.text_creating_meditation));
            binding.buttonCreateMeditation.setEnabled(false);
            String model = PreferenceUtil.getSharedPreferenceString(R.string.key_ai_model, R.string.default_ai_model);
            AiClient.create(model, systemMessage, userMessage, (text, error) -> {
                meditationViewModel.setMeditationText(error == null ? text : oldMeditation);
                if (binding == null) { return; }
                binding.buttonCreateMeditation.setEnabled(true);
                if (error == null) {
                    sentences = splitIntoIndividualSentences(text);
                    meditationViewModel.setSeekBarMax(Math.max(0, sentences.size() - 1));
                    meditationViewModel.setSeekBarProgress(0);
                } else {
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.button_create_meditation).setMessage(error)
                            .setPositiveButton(R.string.button_ok, null).show();
                }
            });
        });

        binding.buttonSpeakMeditation.setOnClickListener(v -> {
            meditationViewModel.setPauseDuration(PreferenceUtil.getPauseSeconds());
            meditationViewModel.setMeditationText(binding.editTextMeditationText.getText().toString());

            String meditationText = binding.editTextMeditationText.getText().toString();
            List<String> newSentences = SpeechSegments.split(meditationText, speechBackend().segmentLimit);
            if (!newSentences.equals(sentences)) {
                meditationViewModel.setSeekBarProgress(0);
                binding.seekBarAudio.setProgress(0);
            }
            sentences = newSentences;
            meditationViewModel.setSeekBarMax(Math.max(0, sentences.size() - 1));
            startAudio(binding.seekBarAudio.getProgress());
        });

        binding.buttonPauseMeditation.setOnClickListener(v -> stopAudio());

        binding.editTextMeditationContent.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                meditationViewModel.setMeditationContent(binding.editTextMeditationContent.getText().toString());
            }
        });

        binding.seekBarPauseDuration.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    setPause(de.jeisfeld.mymeditation.audio.PlaybackScales.pause(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        binding.editTextMeditationText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                meditationViewModel.setMeditationText(binding.editTextMeditationText.getText().toString());
            }
        });

        binding.seekBarAudio.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            private boolean resumeAfterSeek;
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) { meditationViewModel.setSeekBarProgress(progress); }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                resumeAfterSeek = Boolean.TRUE.equals(meditationViewModel.isMeditationRunning().getValue());
                if (resumeAfterSeek) { stopAudio(); }
            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (resumeAfterSeek) { MeditationPlaybackService.seek(requireContext(), seekBar.getProgress()); }
            }
        });

        binding.seekBarVoiceSpeed.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            private boolean dragging;
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    double speed = de.jeisfeld.mymeditation.audio.PlaybackScales.speed(progress);
                    binding.textVoiceSpeed.setText(number(speed) + "×");
                    if (!dragging) { setSpeed(speed); }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) { dragging = false; setSpeed(de.jeisfeld.mymeditation.audio.PlaybackScales.speed(bar.getProgress())); }
        });
        binding.textPauseDuration.setOnClickListener(v -> valueDialog(false));
        binding.textVoiceSpeed.setOnClickListener(v -> valueDialog(true));
        refreshSpeed();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null) { refreshSpeed(); }
    }
    private String number(double value) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(getResources().getConfiguration().getLocales().get(0));
        format.setMaximumFractionDigits(3);
        format.setGroupingUsed(false);
        return format.format(value);
    }
    private double voiceSpeed() {
        try {
            return new de.jeisfeld.mymeditation.http.VoiceProfiles(PreferenceUtil.getSharedPreferenceString(
                    de.jeisfeld.mymeditation.http.VoiceProfiles.PREFERENCE, "{}")).speed(speechBackend(),
                    PreferenceUtil.getSharedPreferenceString(speechBackend().voiceKey(), speechBackend().defaultVoice),
                    Double.parseDouble(PreferenceUtil.getSharedPreferenceString(R.string.key_speech_speed, R.string.default_speech_speed)));
        } catch (IllegalArgumentException e) { return 1; }
    }
    private void refreshSpeed() {
        double speed = voiceSpeed();
        binding.seekBarVoiceSpeed.setProgress(de.jeisfeld.mymeditation.audio.PlaybackScales.speedProgress(speed));
        binding.textVoiceSpeed.setText(number(speed) + "×");
    }
    private void setPause(double value) {
        meditationViewModel.setPauseDuration(value);
        MeditationPlaybackService.setPause(requireContext(), value);
    }
    private void setSpeed(double value) {
        de.jeisfeld.mymeditation.http.SpeechProtocol.validateSpeed(value);
        de.jeisfeld.mymeditation.http.VoiceProfiles profiles = new de.jeisfeld.mymeditation.http.VoiceProfiles(
                PreferenceUtil.getSharedPreferenceString(de.jeisfeld.mymeditation.http.VoiceProfiles.PREFERENCE, "{}"));
        profiles.setSpeed(speechBackend(), PreferenceUtil.getSharedPreferenceString(speechBackend().voiceKey(), speechBackend().defaultVoice), value);
        PreferenceUtil.setSharedPreferenceString(de.jeisfeld.mymeditation.http.VoiceProfiles.PREFERENCE, profiles.json());
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_speed, Double.toString(value));
        refreshSpeed();
        MeditationPlaybackService.applySavedSettings(requireContext());
    }
    private void valueDialog(boolean speed) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789.,"));
        input.setText(number(speed ? voiceSpeed() : PreferenceUtil.getPauseSeconds()));
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(speed ? R.string.label_voice_speed : R.string.label_pause_duration)
                .setView(input).setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double value = Double.parseDouble(input.getText().toString().trim().replace(',', '.'));
                if (!Double.isFinite(value) || value < (speed ? 0.25 : 0) || value > (speed ? 4 : 30)) { throw new IllegalArgumentException(); }
                if (speed) { setSpeed(value); } else { setPause(value); }
                dialog.dismiss();
            } catch (IllegalArgumentException e) { input.setError(getString(speed ? R.string.speech_speed_label : R.string.pause_value_range)); }
        }));
        dialog.show();
    }

    public static List<String> splitIntoIndividualSentences(String input) {
        return SpeechSegments.split(input);
    }

    private void startAudio(int position) {
        if (sentences == null || sentences.isEmpty()) { return; }
        try { MeditationPlaybackService.play(requireContext(), position); }
        catch (RuntimeException e) {
            meditationViewModel.setMeditationRunning(false);
            new AlertDialog.Builder(requireContext()).setTitle(R.string.button_speak_meditation)
                    .setMessage(UiText.VISIBLE_SERVICE_FAILED.text())
                    .setPositiveButton(R.string.button_ok, null).show();
        }
    }

    private SpeechBackend speechBackend() {
        return SpeechBackend.fromId(PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend, R.string.default_speech_backend));
    }

    private void updateSpeechStatus(String status) {
        if (status.equals(pendingSpeechStatus)) { return; }
        binding.textSpeechStatus.removeCallbacks(showSpeechStatus);
        pendingSpeechStatus = status;
        if (status.isEmpty()) {
            binding.textSpeechStatus.setText("");
            // No idle gap: only sustained status messages use a compact single line.
            binding.textSpeechStatus.setVisibility(View.GONE);
        } else if (binding.textSpeechStatus.getVisibility() == View.VISIBLE) {
            binding.textSpeechStatus.setText(status);
        } else {
            // Cached/prefetched audio often clears "Preparing" within the same frame.
            binding.textSpeechStatus.postDelayed(showSpeechStatus, 300);
        }
    }

    private void stopAudio() { MeditationPlaybackService.pause(requireContext()); }

    @Override public void onDestroyView() {
        binding.textSpeechStatus.removeCallbacks(showSpeechStatus);
        pendingSpeechStatus = "";
        binding = null;
        super.onDestroyView();
    }
}
