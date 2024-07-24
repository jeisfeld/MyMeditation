package de.jeisfeld.mymeditation.ui.home;

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

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentHomeBinding;
import de.jeisfeld.mymeditation.http.HttpSender;
import de.jeisfeld.mymeditation.util.Logger;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class HomeFragment extends Fragment implements OnInitListener {

	private FragmentHomeBinding binding;

	private TextToSpeech textToSpeech = null;
	private String[] sentences;
	private final Handler handler = new Handler();

	private HomeViewModel homeViewModel;


	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

		binding = FragmentHomeBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		homeViewModel.getMeditationContent().observe(getViewLifecycleOwner(), binding.editTextMeditationContent::setText);
		homeViewModel.getMeditationText().observe(getViewLifecycleOwner(), binding.editTextMeditationText::setText);
		homeViewModel.getPauseDuration().observe(getViewLifecycleOwner(), pauseDuration -> {
			if (pauseDuration == null) {
				binding.editTextPauseDuration.setText("");
			}
			else {
				binding.editTextPauseDuration.setText(String.valueOf(pauseDuration));
			}
		});
		homeViewModel.getSeekBarMax().observe(getViewLifecycleOwner(), binding.seekBarAudio::setMax);
		homeViewModel.getSeekBarProgress().observe(getViewLifecycleOwner(), binding.seekBarAudio::setProgress);
		homeViewModel.isMeditationRunning().observe(getViewLifecycleOwner(), isMeditationRunning -> {
			binding.buttonSpeakMeditation.setVisibility(isMeditationRunning ? View.GONE : View.VISIBLE);
			binding.buttonPauseMeditation.setVisibility(isMeditationRunning ? View.VISIBLE : View.GONE);
		});


		binding.buttonCreateMeditation.setOnClickListener(v -> {
			stopAudio();
			String systemMessage = PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt);
			String queryTemplate = PreferenceUtil.getSharedPreferenceString(R.string.key_query_template);
			String userMessage = queryTemplate.replace("@TEXT@", homeViewModel.getMeditationContent().getValue());
			String oldMeditation = homeViewModel.getMeditationText().getValue();
			homeViewModel.setMeditationText(getString(R.string.text_creating_meditation));
			new HttpSender(getActivity()).sendMessage("openai/queryopenai.php", (response, responseData) -> {
				Logger.log(response);
				if (responseData.isSuccess()) {
					String meditationText = (String) responseData.getData().get("message");
					if (meditationText == null) {
						meditationText = "";
					}
					homeViewModel.setMeditationText(meditationText);
					sentences = meditationText.split("\\.+");
					homeViewModel.setSeekBarMax(sentences.length - 1);
					homeViewModel.setSeekBarProgress(0);
				}
				else {
					Log.e(Application.TAG, "Failed to retrieve data from OpenAI - " + responseData.getErrorMessage());
					homeViewModel.setMeditationText(oldMeditation);
				}
			}, userMessage, systemMessage);
		});

		binding.buttonSpeakMeditation.setOnClickListener(v -> {
			String meditationText = homeViewModel.getMeditationText().getValue();
			if (meditationText != null && !meditationText.isEmpty()) {
				String[] newSentences = meditationText.split("\\.+");
				if (sentences == null || newSentences.length != sentences.length) {
					homeViewModel.setSeekBarMax(newSentences.length - 1);
					homeViewModel.setSeekBarProgress(0);
				}
				sentences = newSentences;
				homeViewModel.setMeditationRunning(true);
				textToSpeech = new TextToSpeech(HomeFragment.this.getActivity(), HomeFragment.this);
			}
		});

		binding.buttonPauseMeditation.setOnClickListener(v -> stopAudio());

		binding.editTextMeditationContent.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				homeViewModel.setMeditationContent(binding.editTextMeditationContent.getText().toString());
			}
		});

		binding.editTextPauseDuration.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				homeViewModel.setPauseDuration(binding.editTextPauseDuration.getText().toString());
			}
		});

		binding.seekBarAudio.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) {
					homeViewModel.setSeekBarProgress(progress);
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
					Integer sentenceIndexObject = homeViewModel.getSeekBarProgress().getValue();
					int sentenceIndex = sentenceIndexObject == null ? 0 : sentenceIndexObject;
					if (sentenceIndex == sentences.length - 1) {
						stopAudio();
					}
					else {
						homeViewModel.setSeekBarProgress(sentenceIndex + 1);
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
			homeViewModel.setMeditationRunning(false);
			textToSpeech.stop();
			textToSpeech.shutdown();
		}
	}

	private void speakNextSentence(final boolean isFirst) {
		Boolean isAudioRunning = homeViewModel.isMeditationRunning().getValue();
		if (isAudioRunning != null && isAudioRunning) {
			Integer sentenceIndexObject = homeViewModel.getSeekBarProgress().getValue();
			int sentenceIndex = sentenceIndexObject == null ? 0 : sentenceIndexObject;
			String sentence = sentences[sentenceIndex];
			Integer pauseDurationObj = homeViewModel.getPauseDuration().getValue();
			int pauseDuration = pauseDurationObj == null ? 0 : pauseDurationObj * 1000;

			// Play silent sound to wake up Bluetooth speaker
			playSilence(isFirst ? 500 : sentence.startsWith("\n") ? pauseDuration + Math.max(pauseDuration, 2000) : pauseDuration,
					() -> {
						Integer newSentenceIndexObject = homeViewModel.getSeekBarProgress().getValue();
						int newSentenceIndex = sentenceIndexObject == null ? 0 : newSentenceIndexObject;
						String newSentence = sentences[newSentenceIndex].trim();
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