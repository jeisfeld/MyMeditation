package de.jeisfeld.mymeditation.ui.meditation;

import android.os.Bundle;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.audio.MeditationSpeechPlayer;
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

    private MeditationSpeechPlayer speechPlayer;
    private List<String> sentences;

    private MeditationViewModel meditationViewModel;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        meditationViewModel = new ViewModelProvider(requireActivity()).get(MeditationViewModel.class);

        binding = FragmentMeditationBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        speechPlayer = new MeditationSpeechPlayer(requireContext(),
                new MeditationSpeechPlayer.Listener() {
                    @Override public void onSentence(int index) { meditationViewModel.setSeekBarProgress(index); }
                    @Override public void onRunning(boolean running) { meditationViewModel.setMeditationRunning(running); }
                    @Override public void onStatus(String status) {
                        if (binding != null) {
                            binding.textSpeechStatus.setText(status);
                            binding.textSpeechStatus.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
                        }
                    }
                    @Override public void onError(String error) {
                        if (binding != null) {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle(R.string.button_speak_meditation).setMessage(error)
                                    .setPositiveButton(android.R.string.ok, null).show();
                        }
                    }
                });

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
                pauseDuration = 1;
            }
            binding.seekBarPauseDuration.setProgress(pauseDuration);
            binding.textPauseDuration.setText(getString(R.string.text_pause_duration, pauseDuration));
        });
        meditationViewModel.getSeekBarMax().observe(getViewLifecycleOwner(), binding.seekBarAudio::setMax);
        meditationViewModel.getSeekBarProgress().observe(getViewLifecycleOwner(), binding.seekBarAudio::setProgress);
        meditationViewModel.isMeditationRunning().observe(getViewLifecycleOwner(), isMeditationRunning -> {
            binding.buttonSpeakMeditation.setVisibility(isMeditationRunning ? View.GONE : View.VISIBLE);
            binding.buttonPauseMeditation.setVisibility(isMeditationRunning ? View.VISIBLE : View.GONE);
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
                            .setTitle("Create Meditation").setMessage(error)
                            .setPositiveButton(android.R.string.ok, null).show();
                }
            });
        });

        binding.buttonSpeakMeditation.setOnClickListener(v -> {
            meditationViewModel.setPauseDuration(binding.seekBarPauseDuration.getProgress());
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
                    meditationViewModel.setPauseDuration(progress);
                    if (speechPlayer != null) { speechPlayer.setPauseSeconds(progress); }
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
                if (resumeAfterSeek) { startAudio(seekBar.getProgress()); }
            }
        });

        return root;
    }

    public static List<String> splitIntoIndividualSentences(String input) {
        return SpeechSegments.split(input);
    }

    private void startAudio(int position) {
        if (speechPlayer == null || sentences == null || sentences.isEmpty()) { return; }
        speechPlayer.start(sentences, position, binding.seekBarPauseDuration.getProgress(),
                speechBackend(),
                PreferenceUtil.getSharedPreferenceString(speechBackend().modelKey(), speechBackend().defaultModel),
                PreferenceUtil.getSharedPreferenceString(speechBackend().voiceKey(), speechBackend().defaultVoice),
                speechSpeed(), PreferenceUtil.getSharedPreferenceString(R.string.key_speech_style, R.string.default_speech_style));
    }

    private SpeechBackend speechBackend() {
        return SpeechBackend.fromId(PreferenceUtil.getSharedPreferenceString(R.string.key_speech_backend, R.string.default_speech_backend));
    }

    private double speechSpeed() {
        try {
            double legacySpeed = Double.parseDouble(PreferenceUtil.getSharedPreferenceString(R.string.key_speech_speed, R.string.default_speech_speed));
            de.jeisfeld.mymeditation.http.VoiceProfiles profiles = new de.jeisfeld.mymeditation.http.VoiceProfiles(
                    PreferenceUtil.getSharedPreferenceString(de.jeisfeld.mymeditation.http.VoiceProfiles.PREFERENCE, "{}"));
            double speed = profiles.speed(speechBackend(), PreferenceUtil.getSharedPreferenceString(
                    speechBackend().voiceKey(), speechBackend().defaultVoice), legacySpeed);
            de.jeisfeld.mymeditation.http.SpeechProtocol.validateSpeed(speed);
            return speed;
        } catch (IllegalArgumentException e) { return 1.0; }
    }

    private void stopAudio() { if (speechPlayer != null) { speechPlayer.stop(); } }

    @Override public void onDestroyView() {
        if (speechPlayer != null) { speechPlayer.close(); speechPlayer = null; }
        binding = null;
        super.onDestroyView();
    }
}
