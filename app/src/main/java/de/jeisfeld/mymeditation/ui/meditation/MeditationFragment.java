package de.jeisfeld.mymeditation.ui.meditation;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentMeditationBinding;
import de.jeisfeld.mymeditation.http.HttpSender;
import de.jeisfeld.mymeditation.util.Logger;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class MeditationFragment extends Fragment implements OnInitListener {

	private FragmentMeditationBinding binding;

	private TextToSpeech textToSpeech = null;
	private List<String> sentences;
	private final Handler handler = new Handler();

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
			String userMessage = queryTemplate.replace("@TEXT@", meditationViewModel.getMeditationContent().getValue());
			String oldMeditation = meditationViewModel.getMeditationText().getValue();
			meditationViewModel.setMeditationText(getString(R.string.text_creating_meditation));
			new HttpSender(getActivity()).sendMessage("openai/queryopenai.php", (response, responseData) -> {
				Logger.log(response);
				if (responseData.isSuccess()) {
					String meditationText = (String) responseData.getData().get("message");
					if (meditationText == null) {
						meditationText = "";
					}
					meditationViewModel.setMeditationText(meditationText);
					sentences = splitIntoIndividualSentences(meditationText);
					meditationViewModel.setSeekBarMax(sentences.size() - 1);
					meditationViewModel.setSeekBarProgress(0);
				}
				else {
					Log.e(Application.TAG, "Failed to retrieve data from OpenAI - " + responseData.getErrorMessage());
					meditationViewModel.setMeditationText(oldMeditation);
				}
			}, userMessage, systemMessage);
		});

		binding.buttonSpeakMeditation.setOnClickListener(v -> {
			meditationViewModel.setPauseDuration(binding.seekBarPauseDuration.getProgress());
			meditationViewModel.setMeditationText(binding.editTextMeditationText.getText().toString());

			String meditationText = meditationViewModel.getMeditationText().getValue();
			if (meditationText != null && !meditationText.isEmpty()) {
				List<String> newSentences = splitIntoIndividualSentences(meditationText);
				if (sentences == null || newSentences.size() != sentences.size()) {
					meditationViewModel.setSeekBarMax(newSentences.size() - 1);
					meditationViewModel.setSeekBarProgress(0);
				}
				sentences = newSentences;
				meditationViewModel.setMeditationRunning(true);
				textToSpeech = new TextToSpeech(MeditationFragment.this.getActivity(), MeditationFragment.this);
			}
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
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					meditationViewModel.setSeekBarProgress(progress);
				}
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {

			}
		});

		return root;
	}

	public static List<String> splitIntoIndividualSentences(String input) {
		List<String> result = new ArrayList<>();
		String regex = "[^.!?]+[.!?]";
		Pattern pattern = Pattern.compile(regex);

		Matcher matcher = pattern.matcher(input);
		while (matcher.find()) {
			result.add(matcher.group().trim());
		}

		return result;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		stopAudio();
		binding = null;
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			// Set the UtteranceProgressListener
			textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
				private void run() {
					speakNextSentence(false);
				}

				@Override
				public void onStart(String utteranceId) {
					// Called when the utterance starts
				}

				@Override
				public void onDone(String utteranceId) {
					// Called when the utterance completes
					Integer sentenceIndexObject = meditationViewModel.getSeekBarProgress().getValue();
					int sentenceIndex = sentenceIndexObject == null ? 0 : sentenceIndexObject;
					if (sentenceIndex == sentences.size() - 1) {
						stopAudio();
					}
					else {
						meditationViewModel.setSeekBarProgress(sentenceIndex + 1);
						handler.post(this::run);
					}
				}

				@Override
				public void onError(String utteranceId) {
					// Called when an error occurs
				}
			});

			// Start speaking sentences with delay
			speakNextSentence(true);
		}

	}

	private void stopAudio() {
		if (textToSpeech != null) {
			meditationViewModel.setMeditationRunning(false);
			textToSpeech.stop();
			textToSpeech.shutdown();
		}
	}

	private void speakNextSentence(final boolean isFirst) {
		Boolean isAudioRunning = meditationViewModel.isMeditationRunning().getValue();
		if (isAudioRunning != null && isAudioRunning) {
			Integer sentenceIndexObject = meditationViewModel.getSeekBarProgress().getValue();
			int sentenceIndex = sentenceIndexObject == null ? 0 : sentenceIndexObject;
			String sentence = sentences.get(sentenceIndex);
			Integer pauseDurationObj = meditationViewModel.getPauseDuration().getValue();
			int pauseDuration = pauseDurationObj == null ? 0 : pauseDurationObj * 1000;

			// Play silent sound to wake up Bluetooth speaker
			playSilence(isFirst ? 500 : sentence.startsWith("\n") ? pauseDuration + Math.max(pauseDuration, 2000) : pauseDuration,
					() -> {
						Integer newSentenceIndexObject = meditationViewModel.getSeekBarProgress().getValue();
						int newSentenceIndex = sentenceIndexObject == null ? 0 : newSentenceIndexObject;
						String newSentence = sentences.get(newSentenceIndex).trim();
						Logger.log(newSentence);
						textToSpeech.speak(newSentence, TextToSpeech.QUEUE_FLUSH, null, "utteranceId");
					});
		}
	}

	private void playSilence(int durationMs, Runnable onCompletion) {
		int sampleRate = 44100;
		int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
		short[] silence = new short[numSamples];
		final AudioTrack audioTrack;

		if (durationMs > 0) {
			audioTrack = new AudioTrack.Builder()
					.setAudioAttributes(new AudioAttributes.Builder()
							.setUsage(AudioAttributes.USAGE_MEDIA)
							.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
					.setAudioFormat(new AudioFormat.Builder()
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(sampleRate)
							.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
							.build())
					.setBufferSizeInBytes(silence.length * 2)
					.setTransferMode(AudioTrack.MODE_STATIC)
					.build();

			audioTrack.write(silence, 0, silence.length);
			audioTrack.play();
		}
		else {
			audioTrack = null;
		}

		handler.postDelayed(() -> {
			if (audioTrack != null) {
				audioTrack.release();
			}
			onCompletion.run();
		}, durationMs);
	}
}