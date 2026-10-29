package de.jeisfeld.mymeditation.ui.configuration;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.audio.MeditationPlaybackService;
import de.jeisfeld.mymeditation.databinding.FragmentConfigurationBinding;
import de.jeisfeld.mymeditation.http.ModelCatalog;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Immediate settings controls; the full dialog still stages changes until Save. */
final class InlineAiSettings {
    private final FragmentConfigurationBinding binding;
    private final Context context;
    private int generation;
    private boolean disposed;

    InlineAiSettings(FragmentConfigurationBinding binding) {
        this.binding = binding;
        context = binding.getRoot().getContext();
    }

    void refresh() {
        if (disposed) { return; }
        generation++;
        String aiModel = PreferenceUtil.getSharedPreferenceString(R.string.key_ai_model, R.string.default_ai_model);
        List<String> aiModels = new ArrayList<>(ModelCatalog.aiModels());
        if (!aiModels.contains(aiModel)) { aiModels.add(0, aiModel); }
        bind(binding.spinnerAiModelPreset, aiModels, aiModels.indexOf(aiModel), index -> {
            PreferenceUtil.setSharedPreferenceString(R.string.key_ai_model, aiModels.get(index));
            refresh();
        });

        SpeechBackend backend = SpeechBackend.fromId(PreferenceUtil.getSharedPreferenceString(
                R.string.key_speech_backend, R.string.default_speech_backend));
        bind(binding.spinnerSpeechBackend, Arrays.asList(context.getResources().getStringArray(R.array.speech_backend_short_options)),
                backend.ordinal(), index -> selectSpeech(SpeechBackend.values()[index], null, null));

        boolean cloud = backend != SpeechBackend.DEVICE;
        binding.spinnerSpeechModel.setEnabled(cloud);
        binding.spinnerSpeechVoice.setEnabled(cloud);
        if (!cloud) {
            List<String> defaults = Collections.singletonList(context.getString(R.string.device_speech_default));
            bind(binding.spinnerSpeechModel, defaults, 0, index -> { });
            bind(binding.spinnerSpeechVoice, defaults, 0, index -> { });
            return;
        }

        String model = savedModel(backend);
        List<String> models = ModelCatalog.speechModels(backend, model);
        bind(binding.spinnerSpeechModel, models, models.indexOf(model),
                index -> selectSpeech(backend, models.get(index), null));
        List<String> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (backend == SpeechBackend.ELEVENLABS) {
            for (VoiceProfiles.Voice voice : profiles().voices()) { ids.add(voice.id); labels.add(voice.name); }
        } else {
            ids.addAll(backend.voices(model));
            labels.addAll(ids);
        }
        String savedVoice = PreferenceUtil.getSharedPreferenceString(backend.voiceKey(), backend.defaultVoice);
        bind(binding.spinnerSpeechVoice, labels, Math.max(0, ids.indexOf(savedVoice)),
                index -> selectSpeech(backend, model, ids.get(index)));
    }

    private String savedModel(SpeechBackend backend) {
        String model = PreferenceUtil.getSharedPreferenceString(backend.modelKey(), backend.defaultModel);
        return model.isEmpty() ? backend.defaultModel : model;
    }

    private VoiceProfiles profiles() {
        VoiceProfiles profiles = new VoiceProfiles(PreferenceUtil.getSharedPreferenceString(VoiceProfiles.PREFERENCE, "{}"));
        profiles.migrateVoice(PreferenceUtil.getSharedPreferenceString(SpeechBackend.ELEVENLABS.voiceKey(), ""));
        return profiles;
    }

    private void selectSpeech(SpeechBackend backend, String model, String voice) {
        VoiceProfiles profiles = profiles();
        if (model == null) { model = savedModel(backend); }
        if (backend != SpeechBackend.DEVICE) {
            List<String> available = new ArrayList<>();
            if (backend == SpeechBackend.ELEVENLABS) {
                for (VoiceProfiles.Voice saved : profiles.voices()) { available.add(saved.id); }
            } else { available.addAll(backend.voices(model)); }
            if (available.isEmpty()) {
                Toast.makeText(context, R.string.add_voice_first, Toast.LENGTH_LONG).show();
                refresh();
                return;
            }
            if (voice == null) {
                voice = PreferenceUtil.getSharedPreferenceString(backend.voiceKey(), backend.defaultVoice);
            }
            if (!available.contains(voice)) { voice = available.get(0); }
            try { backend.validate(model, voice); }
            catch (IllegalArgumentException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                refresh();
                return;
            }
        }

        // Preserve legacy speed for the previous voice before a first-time voice change.
        SpeechBackend previous = SpeechBackend.fromId(PreferenceUtil.getSharedPreferenceString(
                R.string.key_speech_backend, R.string.default_speech_backend));
        String previousVoice = PreferenceUtil.getSharedPreferenceString(previous.voiceKey(), previous.defaultVoice);
        if (!profiles.hasSpeed(previous, previousVoice)) {
            try {
                profiles.setSpeed(previous, previousVoice, Double.parseDouble(PreferenceUtil.getSharedPreferenceString(
                        R.string.key_speech_speed, R.string.default_speech_speed)));
            } catch (IllegalArgumentException ignored) { }
        }
        if (backend != SpeechBackend.DEVICE) {
            if (!profiles.hasSpeed(backend, voice)) { profiles.setSpeed(backend, voice, 1.0); }
            PreferenceUtil.setSharedPreferenceString(backend.modelKey(), model);
            PreferenceUtil.setSharedPreferenceString(backend.voiceKey(), voice);
        }
        PreferenceUtil.setSharedPreferenceString(VoiceProfiles.PREFERENCE, profiles.json());
        PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, backend.id);
        refresh();
        MeditationPlaybackService.applySavedSettings(context);
    }

    private void bind(Spinner spinner, List<String> labels, int selected, Consumer<Integer> onChange) {
        int currentGeneration = generation;
        spinner.setOnItemSelectedListener(null);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context, R.layout.spinner_settings_item, labels);
        adapter.setDropDownViewResource(R.layout.dropdown_voice_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Spinner posts initialization callbacks; they must never save settings or restart speech.
                if (disposed || currentGeneration != generation || position == selected || position < 0 || position >= labels.size()) { return; }
                onChange.accept(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    void dispose() {
        disposed = true;
        generation++;
    }
}
