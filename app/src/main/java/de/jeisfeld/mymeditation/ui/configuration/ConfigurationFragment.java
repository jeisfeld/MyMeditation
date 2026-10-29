package de.jeisfeld.mymeditation.ui.configuration;

import android.os.Bundle;
import de.jeisfeld.mymeditation.http.VoiceProfiles;
import de.jeisfeld.mymeditation.http.SpeechBackend;
import de.jeisfeld.mymeditation.databinding.DialogAiSettingsBinding;
import de.jeisfeld.mymeditation.util.PreferenceUtil;
import de.jeisfeld.mymeditation.http.SpeechProtocol;
import de.jeisfeld.mymeditation.http.AiProvider;
import androidx.appcompat.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentConfigurationBinding;

public class ConfigurationFragment extends Fragment {

    private FragmentConfigurationBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        ConfigurationViewModel configurationViewModel =
                new ViewModelProvider(requireActivity()).get(ConfigurationViewModel.class);

        binding = FragmentConfigurationBinding.inflate(inflater, container, false);
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

        configurationViewModel.getSystemPrompt().observe(getViewLifecycleOwner(), binding.editTextSystemPrompt::setText);
        configurationViewModel.getQueryTemplate().observe(getViewLifecycleOwner(), binding.editTextQueryTemplate::setText);

        binding.editTextSystemPrompt.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                configurationViewModel.setSystemPrompt(binding.editTextSystemPrompt.getText().toString());
            }
        });
        binding.editTextQueryTemplate.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                configurationViewModel.setQueryTemplate(binding.editTextQueryTemplate.getText().toString());
            }
        });


        binding.buttonAiSettings.setOnClickListener(v -> showAiSettings());

        return root;
    }

    private void showAiSettings() {
        DialogAiSettingsBinding settings =
                DialogAiSettingsBinding.inflate(getLayoutInflater());
        settings.editTextAiModel.setText(PreferenceUtil.getSharedPreferenceString(
                R.string.key_ai_model, R.string.default_ai_model));
        settings.editTextSpeechSpeed.setText(PreferenceUtil.getSharedPreferenceString(
                R.string.key_speech_speed, R.string.default_speech_speed));
        settings.editTextSpeechStyle.setText(PreferenceUtil.getSharedPreferenceString(
                R.string.key_speech_style, R.string.default_speech_style));
        SpeechBackend[] selected = {SpeechBackend.fromId(PreferenceUtil.getSharedPreferenceString(
                R.string.key_speech_backend, R.string.default_speech_backend))};
        VoiceProfiles profiles = new VoiceProfiles(PreferenceUtil.getSharedPreferenceString(VoiceProfiles.PREFERENCE, "{}"));
        String priorVoice = PreferenceUtil.getSharedPreferenceString(selected[0].voiceKey(), selected[0].defaultVoice);
        profiles.migrateVoice(PreferenceUtil.getSharedPreferenceString(SpeechBackend.ELEVENLABS.voiceKey(), ""));
        if (!profiles.hasSpeed(selected[0], priorVoice)) {
            try { profiles.setSpeed(selected[0], priorVoice, Double.parseDouble(settings.editTextSpeechSpeed.getText().toString())); }
            catch (IllegalArgumentException ignored) { }
        }
        String[] activeVoice = {""};
        boolean[] loading = {false};
        java.util.List<String> voiceIds = new java.util.ArrayList<>();
        Runnable rememberSpeed = () -> {
            try {
                profiles.setSpeed(selected[0], activeVoice[0], Double.parseDouble(
                        settings.editTextSpeechSpeed.getText().toString().trim().replace(',', '.')));
            } catch (IllegalArgumentException ignored) { }
        };
        java.util.Map<SpeechBackend, String> models = new java.util.HashMap<>();
        java.util.Map<SpeechBackend, String> voices = new java.util.HashMap<>();
        Runnable remember = () -> {
            if (selected[0] != SpeechBackend.DEVICE) {
                rememberSpeed.run();
                models.put(selected[0], settings.editTextSpeechModel.getText().toString().trim());
                voices.put(selected[0], activeVoice[0]);
            }
        };
        Runnable load = () -> {
            SpeechBackend backend = selected[0];
            settings.openAiSpeechSettings.setVisibility(backend == SpeechBackend.DEVICE ? View.GONE : View.VISIBLE);
            if (backend == SpeechBackend.DEVICE) { return; }
            settings.editTextSpeechModel.setText(models.containsKey(backend) ? models.get(backend)
                    : PreferenceUtil.getSharedPreferenceString(backend.modelKey(), backend.defaultModel));
            loading[0] = true;
            java.util.List<String> labels = new java.util.ArrayList<>();
            voiceIds.clear();
            if (backend == SpeechBackend.ELEVENLABS) {
                for (VoiceProfiles.Voice saved : profiles.voices()) { labels.add(saved.name); voiceIds.add(saved.id); }
            } else { labels.addAll(backend.voices()); voiceIds.addAll(backend.voices()); }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, labels);
            adapter.setDropDownViewResource(R.layout.dropdown_voice_item);
            settings.spinnerSpeechVoice.setAdapter(adapter);
            String voice = voices.containsKey(backend) ? voices.get(backend)
                    : PreferenceUtil.getSharedPreferenceString(backend.voiceKey(), backend.defaultVoice);
            int position = Math.max(0, voiceIds.indexOf(voice));
            activeVoice[0] = voiceIds.isEmpty() ? "" : voiceIds.get(position);
            settings.spinnerSpeechVoice.setSelection(position);
            settings.spinnerSpeechVoice.setVisibility(View.VISIBLE);
            settings.elevenVoiceButtons.setVisibility(backend == SpeechBackend.ELEVENLABS ? View.VISIBLE : View.GONE);
            settings.buttonEditVoice.setEnabled(!activeVoice[0].isEmpty());
            settings.editTextSpeechSpeed.setText(Double.toString(profiles.speed(backend, activeVoice[0], 1.0)));
            loading[0] = false;
        };
        settings.spinnerSpeechVoice.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (loading[0] || position < 0 || position >= voiceIds.size()) { return; }
                String next = voiceIds.get(position);
                if (!next.equals(activeVoice[0])) {
                    rememberSpeed.run();
                    activeVoice[0] = next;
                    settings.editTextSpeechSpeed.setText(Double.toString(profiles.speed(selected[0], next, 1.0)));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        settings.buttonAddVoice.setOnClickListener(v -> editSavedVoice(profiles, null, id -> {
            remember.run(); voices.put(SpeechBackend.ELEVENLABS, id); load.run();
        }));
        settings.buttonEditVoice.setOnClickListener(v -> editSavedVoice(profiles, activeVoice[0], id -> {
            remember.run();
            if (id == null) {
                profiles.removeVoice(activeVoice[0]);
                java.util.List<VoiceProfiles.Voice> remaining = profiles.voices();
                voices.put(SpeechBackend.ELEVENLABS, remaining.isEmpty() ? "" : remaining.get(0).id);
                if (remaining.isEmpty()) {
                    activeVoice[0] = "";
                    selected[0] = SpeechBackend.DEVICE;
                    settings.spinnerSpeechBackend.setSelection(selected[0].ordinal());
                }
            } else { voices.put(SpeechBackend.ELEVENLABS, id); }
            load.run();
        }));
        load.run();
        settings.spinnerSpeechBackend.setSelection(selected[0].ordinal());
        settings.spinnerSpeechBackend.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (selected[0] != SpeechBackend.values()[position]) {
                    remember.run();
                    selected[0] = SpeechBackend.values()[position];
                    load.run();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ai_settings)
                .setView(settings.getRoot())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ai_settings_save, null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String model = settings.editTextAiModel.getText().toString().trim();
                if (model.isEmpty()) { model = getString(R.string.default_ai_model); }
                try {
                    AiProvider.forModel(model);
                } catch (IllegalArgumentException e) {
                    settings.editTextAiModel.setError(getString(R.string.ai_model_invalid));
                    return;
                }
                remember.run();
                SpeechBackend backend = selected[0];
                if (backend != SpeechBackend.DEVICE) {
                    String speechModel = models.get(backend);
                    if (speechModel == null || speechModel.isEmpty()) { speechModel = backend.defaultModel; models.put(backend, speechModel); }
                    try { backend.validate(speechModel, voices.get(backend)); }
                    catch (IllegalArgumentException e) {
                        if (backend == SpeechBackend.ELEVENLABS && !voices.get(backend).matches("[A-Za-z0-9_-]{10,128}")) {
                            new AlertDialog.Builder(requireContext()).setMessage(R.string.add_voice_first)
                                    .setPositiveButton(android.R.string.ok, null).show();
                        } else { settings.editTextSpeechModel.setError(e.getMessage()); }
                        return;
                    }
                }
                double speed;
                try {
                    speed = Double.parseDouble(settings.editTextSpeechSpeed.getText().toString().trim().replace(',', '.'));
                    SpeechProtocol.validateSpeed(speed);
                } catch (IllegalArgumentException e) {
                    settings.editTextSpeechSpeed.setError(getString(R.string.speech_speed_label));
                    return;
                }
                profiles.setSpeed(backend, activeVoice[0], speed);
                PreferenceUtil.setSharedPreferenceString(VoiceProfiles.PREFERENCE, profiles.json());
                PreferenceUtil.setSharedPreferenceString(R.string.key_speech_speed, Double.toString(speed));
                PreferenceUtil.setSharedPreferenceString(R.string.key_speech_style, settings.editTextSpeechStyle.getText().toString().trim());
                PreferenceUtil.setSharedPreferenceString(R.string.key_speech_backend, backend.id);
                for (SpeechBackend provider : models.keySet()) {
                    String value = models.get(provider);
                    PreferenceUtil.setSharedPreferenceString(provider.modelKey(), value.isEmpty() ? provider.defaultModel : value);
                    PreferenceUtil.setSharedPreferenceString(provider.voiceKey(), voices.get(provider));
                }
                PreferenceUtil.setSharedPreferenceString(R.string.key_ai_model, model);
                de.jeisfeld.mymeditation.audio.MeditationPlaybackService.applySavedSettings(requireContext());
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void editSavedVoice(VoiceProfiles profiles, String existingId, java.util.function.Consumer<String> onSaved) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        layout.setPadding(padding, 0, padding, 0);
        android.widget.EditText name = new android.widget.EditText(requireContext());
        name.setHint(R.string.saved_voice_name);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        layout.addView(name);
        android.widget.EditText id = new android.widget.EditText(requireContext());
        id.setHint(R.string.elevenlabs_voice_id_hint);
        id.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        id.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        id.setSingleLine(true);
        layout.addView(id);
        if (existingId != null) { id.setText(existingId); id.setEnabled(false); name.setText(profiles.name(existingId)); }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(existingId == null ? R.string.add_saved_voice : R.string.edit_saved_voice)
                .setView(layout).setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.button_save, null);
        if (existingId != null) { builder.setNeutralButton(R.string.remove_saved_voice, (dialog, which) -> onSaved.accept(null)); }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String voiceId = id.getText().toString().trim();
                profiles.saveVoice(voiceId, name.getText().toString());
                onSaved.accept(voiceId);
                dialog.dismiss();
            } catch (IllegalArgumentException e) { name.setError(e.getMessage()); }
        }));
        dialog.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}