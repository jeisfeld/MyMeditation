package de.jeisfeld.mymeditation.ui.meditation;

import de.jeisfeld.mymeditation.i18n.UiText;
import de.jeisfeld.mymeditation.audio.MeditationPauses;
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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentMeditationBinding;
import de.jeisfeld.mymeditation.http.AiClient;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class MeditationFragment extends Fragment {

    private de.jeisfeld.mymeditation.ui.PageInsets pageInsets;
    private FragmentMeditationBinding binding;
    private List<String> sentences;
    private boolean reading;
    private android.text.method.KeyListener editorKeyListener;
    private int editorInputType;
    private int highlightedPosition = -2;
    private String highlightedText = "";
    private final android.text.style.StyleSpan spokenBold = new android.text.style.StyleSpan(android.graphics.Typeface.BOLD);
    private android.text.style.BackgroundColorSpan spokenBackground;
    private String pendingSpeechStatus = "";
    private final Runnable showSpeechStatus = () -> {
        if (binding != null && !pendingSpeechStatus.isEmpty()) {
            binding.textSpeechStatus.setText(pendingSpeechStatus);
            binding.textSpeechStatus.setVisibility(View.VISIBLE);
        }
    };

    private final java.util.concurrent.ExecutorService offlineChecker = java.util.concurrent.Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "offline-availability"); thread.setDaemon(true); return thread;
    });
    private final de.jeisfeld.mymeditation.audio.OfflineDuration offlineDuration = new de.jeisfeld.mymeditation.audio.OfflineDuration();
    private int offlineCheck;
    private String offlineIdentity = "";
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshOfflineLabel = this::checkOfflineLabel;
    private final Runnable prefetchTitle = () -> {
        if (binding != null) {
            de.jeisfeld.mymeditation.audio.MeditationTitles.prefetch(requireContext(), binding.editTextMeditationContent.getText().toString());
        }
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener offlinePreferences = (preferences, key) -> {
        scheduleOfflineLabel();
        if (binding != null && MeditationPauses.PREFERENCE.equals(key)) {
            int selected = MeditationPauses.Guidance.fromId(preferences.getString(key, "few")).ordinal();
            if (binding.spinnerPauseGuidance.getSelectedItemPosition() != selected) { binding.spinnerPauseGuidance.setSelection(selected); }
        }
    };

    private void scheduleOfflineLabel() {
        if (binding == null) { return; }
        offlineCheck++;
        ui.removeCallbacks(refreshOfflineLabel);
        ui.post(refreshOfflineLabel);
    }
    private String meditationHeading(boolean offline) { return meditationHeading(offline, null); }
    private String meditationHeading(boolean offline, Long duration) {
        MeditationPlaybackService.State state = MeditationPlaybackService.state().getValue();
        int source = state.active ? state.savedId : de.jeisfeld.mymeditation.audio.SavedAudioLibrary.restoredSource(
                binding.editTextMeditationText.getText().toString());
        if (source > 0 && PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(source)) {
            String name = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, source);
            if (name != null && !name.trim().isEmpty()) {
                if (!offline) { return name; }
                return duration == null ? getString(R.string.label_named_meditation_offline, name)
                        : getString(R.string.label_named_meditation_offline_duration, name,
                                de.jeisfeld.mymeditation.audio.OfflineDuration.format(duration));
            }
        }
        if (offline && duration != null) {
            return getString(R.string.label_meditation_text_offline_duration,
                    de.jeisfeld.mymeditation.audio.OfflineDuration.format(duration));
        }
        return getString(offline ? R.string.label_meditation_text_offline : R.string.label_meditation_text);
    }
    private void checkOfflineLabel() {
        if (binding == null) { return; }
        final int check = offlineCheck;
        final de.jeisfeld.mymeditation.audio.PlaybackRequest request;
        try {
            request = MeditationPlaybackService.currentRequest(binding.editTextMeditationText.getText().toString(), PreferenceUtil.getPauseSeconds());
        } catch (IllegalArgumentException e) {
            offlineIdentity = ""; binding.labelMeditationText.setText(meditationHeading(false)); return;
        }
        if (request.backend == SpeechBackend.DEVICE) {
            offlineIdentity = ""; binding.labelMeditationText.setText(meditationHeading(false)); return;
        }
        String identity = de.jeisfeld.mymeditation.audio.SentenceAudioStore.key(request) + ":" + request.speed + ":" + request.pauseSeconds;
        if (!identity.equals(offlineIdentity)) {
            binding.labelMeditationText.setText(meditationHeading(false));
            offlineIdentity = identity;
        }
        android.content.Context context = requireContext().getApplicationContext();
        offlineChecker.execute(() -> {
            de.jeisfeld.mymeditation.audio.SentenceAudioStore.Book book = de.jeisfeld.mymeditation.audio.SentenceAudio.store(context).open(request);
            boolean offline = book.isComplete();
            Long duration = offline ? offlineDuration.milliseconds(book, request.pauseSeconds) : null;
            ui.post(() -> {
                if (binding != null && check == offlineCheck) {
                    binding.labelMeditationText.setText(meditationHeading(offline, duration));
                }
            });
        });
    }

    private MeditationViewModel meditationViewModel;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        meditationViewModel = new ViewModelProvider(requireActivity()).get(MeditationViewModel.class);

        binding = FragmentMeditationBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        android.widget.ArrayAdapter<CharSequence> pauseChoices = android.widget.ArrayAdapter.createFromResource(
                requireContext(), R.array.pause_guidance_options, R.layout.spinner_settings_item);
        pauseChoices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPauseGuidance.setAdapter(pauseChoices);
        MeditationPauses.Guidance selectedGuidance =
                MeditationPauses.Guidance.fromId(PreferenceUtil.getSharedPreferenceString(
                        MeditationPauses.PREFERENCE, "few"));
        binding.spinnerPauseGuidance.setSelection(selectedGuidance.ordinal());
        binding.spinnerPauseGuidance.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                PreferenceUtil.setSharedPreferenceString(MeditationPauses.PREFERENCE,
                        MeditationPauses.Guidance.values()[position].id);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        editorKeyListener = binding.editTextMeditationText.getKeyListener();
        editorInputType = binding.editTextMeditationText.getInputType();
        spokenBackground = new android.text.style.BackgroundColorSpan(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.spoken_text_highlight));

        pageInsets = new de.jeisfeld.mymeditation.ui.PageInsets(root, keyboard -> renderEditingLayout(keyboard));

        meditationViewModel.getMeditationContent().observe(getViewLifecycleOwner(), binding.editTextMeditationContent::setText);
        meditationViewModel.getMeditationText().observe(getViewLifecycleOwner(), text -> {
            if (!reading) { binding.editTextMeditationText.setText(text); }
        });
        meditationViewModel.getPauseDuration().observe(getViewLifecycleOwner(), pauseDuration -> {
            if (pauseDuration == null) {
                pauseDuration = 1.0;
            }
            binding.seekBarPauseDuration.setProgress(de.jeisfeld.mymeditation.audio.PlaybackScales.pauseProgress(pauseDuration));
            binding.textPauseDuration.setText(de.jeisfeld.mymeditation.audio.PauseSetting.display(pauseDuration, getResources().getConfiguration().getLocales().get(0)));
        });
        meditationViewModel.getSeekBarMax().observe(getViewLifecycleOwner(), binding.seekBarAudio::setMax);
        meditationViewModel.getSeekBarProgress().observe(getViewLifecycleOwner(), position -> {
            binding.seekBarAudio.setProgress(position); refreshSentencePosition();
        });
        MeditationPlaybackService.state().observe(getViewLifecycleOwner(), state -> {
            renderReadingMode(state);
            scheduleOfflineLabel();
            binding.idlePlaybackActions.setVisibility(state.active ? View.GONE : View.VISIBLE);
            binding.playbackControls.setVisibility(state.active ? View.VISIBLE : View.GONE);
            binding.buttonResumeMeditation.setVisibility(state.running ? View.GONE : View.VISIBLE);
            binding.buttonPauseMeditation.setVisibility(state.running ? View.VISIBLE : View.GONE);
            binding.buttonEndMeditation.setEnabled(state.active);
            binding.buttonPreviousMeditation.setEnabled(state.active && state.position > 0);
            binding.buttonNextMeditation.setEnabled(state.active && state.position < state.sentences.size() - 1);
            meditationViewModel.setMeditationRunning(state.running);
            meditationViewModel.setSeekBarProgress(state.position);
            if (!state.sentences.isEmpty()) {
                sentences = state.sentences;
                meditationViewModel.setSeekBarMax(Math.max(0, sentences.size() - 1));
                meditationViewModel.setSeekBarProgress(state.position);
            }
            refreshSentencePosition();
            updateSpeechStatus(state.status);
            if (!state.error.isEmpty() && meditationViewModel.consumePlaybackError(state.errorId)) {
                new AlertDialog.Builder(requireContext()).setTitle(R.string.button_speak_meditation)
                        .setMessage(state.error).setPositiveButton(R.string.button_ok, null).show();
            }
        });


        de.jeisfeld.mymeditation.audio.MeditationExportService.state().observe(getViewLifecycleOwner(), state -> {
            if (meditationViewModel.consumeExportResult(state.resultId)) {
                if (!state.error.isEmpty()) {
                    new AlertDialog.Builder(requireContext()).setTitle(R.string.button_share_offline).setMessage(state.error)
                            .setPositiveButton(R.string.button_ok, null).show();
                } else if (state.sharedFile != null) {
                    de.jeisfeld.mymeditation.audio.AudioSharing.open(this, state.sharedFile, state.name);
                }
            }
        });

        binding.buttonCreateMeditation.setOnClickListener(v -> {
            de.jeisfeld.mymeditation.audio.SavedAudioLibrary.clearSource();
            // postValue updates the ViewModel asynchronously; build this request from the visible text.
            String meditationContent = binding.editTextMeditationContent.getText().toString();
            meditationViewModel.setMeditationContent(meditationContent);

            stopAudio();
            String systemMessage = PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt);
            String queryTemplate = PreferenceUtil.getSharedPreferenceString(R.string.key_query_template);
            String userMessage = MeditationPrompt.render(queryTemplate, meditationContent);
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
            final List<String> newSentences;
            try { newSentences = SpeechSegments.split(meditationText, speechBackend().segmentLimit); }
            catch (IllegalArgumentException e) {
                binding.editTextMeditationText.setError(e.getMessage());
                return;
            }
            if (!newSentences.equals(sentences)) {
                meditationViewModel.setSeekBarProgress(0);
                binding.seekBarAudio.setProgress(0);
            }
            sentences = newSentences;
            meditationViewModel.setSeekBarMax(Math.max(0, sentences.size() - 1));
            startAudio(binding.seekBarAudio.getProgress());
        });

        binding.buttonResumeMeditation.setOnClickListener(v -> binding.buttonSpeakMeditation.performClick());
        binding.buttonPreviousMeditation.setOnClickListener(v -> MeditationPlaybackService.skip(requireContext(), -1));
        binding.buttonNextMeditation.setOnClickListener(v -> MeditationPlaybackService.skip(requireContext(), 1));
        binding.buttonPauseMeditation.setOnClickListener(v -> stopAudio());
        binding.buttonEndMeditation.setOnClickListener(v -> MeditationPlaybackService.end(requireContext()));

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
                refreshSentencePosition();
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
        binding.editTextMeditationText.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { scheduleOfflineLabel(); refreshSentencePosition(); }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        binding.editTextMeditationContent.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                ui.removeCallbacks(prefetchTitle); ui.postDelayed(prefetchTitle, 1500);
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        de.jeisfeld.mymeditation.audio.SavedAudioLibrary.changes().observe(getViewLifecycleOwner(), version -> scheduleOfflineLabel());
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(offlinePreferences);
        refreshSpeed();
        refreshSentencePosition();
        scheduleOfflineLabel();
        return root;
    }

    @Override public void onResume() {
        super.onResume();
        if (binding != null) { refreshSpeed(); scheduleOfflineLabel(); ui.removeCallbacks(prefetchTitle); ui.postDelayed(prefetchTitle, 1500); }
    }
    private String number(double value) {
        java.text.NumberFormat format = java.text.NumberFormat.getNumberInstance(getResources().getConfiguration().getLocales().get(0));
        format.setMaximumFractionDigits(3);
        format.setGroupingUsed(false);
        return format.format(value);
    }
    private double voiceSpeed() {
        return MeditationPlaybackService.currentSpeed();
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
        input.setKeyListener(android.text.method.DigitsKeyListener.getInstance(speed ? "0123456789.," : "0123456789.,%"));
        if (!speed) { input.setRawInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS); }
        input.setText(speed ? number(voiceSpeed()) : de.jeisfeld.mymeditation.audio.PauseSetting.input(
                PreferenceUtil.getPauseSeconds(), getResources().getConfiguration().getLocales().get(0)));
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setTitle(speed ? R.string.label_voice_speed : R.string.label_pause_duration)
                .setView(input).setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_save, null).create();
        if (!speed) { dialog.setMessage(getString(R.string.pause_setting_help)); }
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                double value = speed ? Double.parseDouble(input.getText().toString().trim().replace(',', '.'))
                        : de.jeisfeld.mymeditation.audio.PauseSetting.parse(input.getText().toString());
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

    private void renderEditingLayout(boolean keyboard) {
        if (binding == null) { return; }
        boolean text = keyboard && !reading && binding.editTextMeditationText.hasFocus();
        boolean topic = keyboard && !reading && binding.editTextMeditationContent.hasFocus();
        boolean editing = text || topic;
        binding.meditationContentSection.setVisibility(!reading && !text ? View.VISIBLE : View.GONE);
        binding.buttonCreateMeditation.setVisibility(!reading && !editing ? View.VISIBLE : View.GONE);
        binding.speechSettingsSection.setVisibility(editing ? View.GONE : View.VISIBLE);
        binding.meditationTextSection.setVisibility(topic ? View.GONE : View.VISIBLE);
        binding.meditationPlaybackSection.setVisibility(editing ? View.GONE : View.VISIBLE);
    }

    private void renderReadingMode(MeditationPlaybackService.State state) {
        android.widget.EditText editor = binding.editTextMeditationText;
        if (reading != state.active) {
            reading = state.active;

            editor.setKeyListener(reading ? null : editorKeyListener);
            editor.setRawInputType(reading ? android.text.InputType.TYPE_NULL : editorInputType);
            editor.setCursorVisible(!reading);
            editor.setShowSoftInputOnFocus(!reading);
            if (reading) {
                editor.clearFocus();
                ((android.view.inputmethod.InputMethodManager) requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(editor.getWindowToken(), 0);
            } else {
                editor.setText(meditationViewModel.getMeditationText().getValue());
            }
            highlightedPosition = -2;
        }
        if (reading && !editor.getText().toString().equals(state.text)) {
            editor.setText(state.text);
            highlightedPosition = -2;
        }
        if (pageInsets != null) { pageInsets.refresh(); }
        int target = reading ? state.spokenPosition : -1;
        String text = editor.getText().toString();
        if (target == highlightedPosition && text.equals(highlightedText)) { return; }
        highlightedPosition = target; highlightedText = text;
        android.text.Editable editable = editor.getText();
        editable.removeSpan(spokenBackground); editable.removeSpan(spokenBold);
        int[] range = de.jeisfeld.mymeditation.audio.SpokenTextRanges.range(text, state.sentences, target);
        if (range[0] < 0) { return; }
        editable.setSpan(spokenBackground, range[0], range[1], android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        editable.setSpan(spokenBold, range[0], range[1], android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        androidx.core.view.OneShotPreDrawListener.add(editor, () -> {
            if (binding == null || binding.editTextMeditationText != editor || highlightedPosition != target
                    || !editor.getText().toString().equals(text)) { return; }
            android.text.Layout layout = editor.getLayout();
            if (layout == null) { return; }
            int top = layout.getLineTop(layout.getLineForOffset(range[0]));
            int bottom = layout.getLineBottom(layout.getLineForOffset(Math.max(range[0], range[1]-1)));
            int height = editor.getHeight() - editor.getCompoundPaddingTop() - editor.getCompoundPaddingBottom();
            if (top < editor.getScrollY() || bottom > editor.getScrollY() + height) {
                editor.scrollTo(0, Math.max(0, Math.min(top - height / 4, layout.getHeight() - height)));
            }
        });
    }

    private void refreshSentencePosition() {
        if (binding == null) { return; }
        MeditationPlaybackService.State state = MeditationPlaybackService.state().getValue();
        List<String> segments;
        try {
            segments = state.active ? state.sentences : SpeechSegments.split(
                    binding.editTextMeditationText.getText().toString(), speechBackend().segmentLimit);
        } catch (IllegalArgumentException e) { segments = java.util.Collections.emptyList(); }
        if (!state.active) { binding.seekBarAudio.setMax(Math.max(0, segments.size() - 1)); }
        int position = binding.seekBarAudio.getProgress(), total = 0, current = 0;
        for (int i = 0; i < segments.size(); i++) {
            if (MeditationPauses.seconds(segments.get(i)) < 0) {
                total++;
                if (i <= position) { current++; }
            }
        }
        android.widget.TextView counter = binding.textSentencePosition;
        String maximum = getString(R.string.sentence_position, total, total);
        // Tabular digits and space for the largest numerator keep the seek bar stable.
        counter.setWidth((int) Math.ceil(counter.getPaint().measureText(maximum))
                + counter.getCompoundPaddingLeft() + counter.getCompoundPaddingRight());
        counter.setText(getString(R.string.sentence_position, current, total));
        counter.setContentDescription(getString(R.string.sentence_position_description, current, total));
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

    @Override public void onDestroy() { offlineChecker.shutdownNow(); super.onDestroy(); }

    @Override public void onDestroyView() {
        if (pageInsets != null) { pageInsets.close(); pageInsets = null; }
        androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(offlinePreferences);
        offlineCheck++;
        offlineIdentity = "";
        ui.removeCallbacks(refreshOfflineLabel);
        ui.removeCallbacks(prefetchTitle);
        binding.textSpeechStatus.removeCallbacks(showSpeechStatus);
        pendingSpeechStatus = "";
        reading = false; highlightedPosition = -2; highlightedText = "";
        binding = null;
        super.onDestroyView();
    }
}
