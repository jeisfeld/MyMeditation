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
	private int sentenceIndex = 0;
	private final Handler handler = new Handler();
	private boolean isAudioRunning = false;

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


		binding.buttonCreateMeditation.setOnClickListener(v -> {
			String systemMessage = PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt);
			String queryTemplate = PreferenceUtil.getSharedPreferenceString(R.string.key_query_template);
			String userMessage = queryTemplate.replace("@TEXT@", homeViewModel.getMeditationContent().getValue());
			new HttpSender(getActivity()).sendMessage("openai/queryopenai.php", (response, responseData) -> {
				Logger.log(response);
				if (responseData.isSuccess()) {
					homeViewModel.setMeditationText((String) responseData.getData().get("message"));
				}
				else {
					Log.e(Application.TAG, "Failed to retrieve data from OpenAI - " + responseData.getErrorMessage());
				}
			}, userMessage, systemMessage);
		});

		binding.buttonSpeakMeditation.setOnClickListener(v -> {
			String meditationText = homeViewModel.getMeditationText().getValue();
			if (meditationText != null && !meditationText.isEmpty()) {
				sentences = meditationText.split("\\.");
				sentenceIndex = 0;
				isAudioRunning = true;
				textToSpeech = new TextToSpeech(HomeFragment.this.getActivity(), HomeFragment.this);
			}
		});

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

		return root;
	}

	@Override
	public void onDestroyView() {
		if (textToSpeech != null) {
			isAudioRunning = false;
			textToSpeech.stop();
			textToSpeech.shutdown();
		}

		super.onDestroyView();
		binding = null;
	}

	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			// Set the UtteranceProgressListener
			textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
				private void run() {
					speakNextSentence();
				}

				@Override
				public void onStart(String utteranceId) {
					// Called when the utterance starts
				}

				@Override
				public void onDone(String utteranceId) {
					// Called when the utterance completes
					handler.post(this::run);
				}

				@Override
				public void onError(String utteranceId) {
					// Called when an error occurs
				}
			});

			// Start speaking sentences with delay
			speakNextSentence();
		}

	}

	private void speakNextSentence() {
		if (isAudioRunning && sentenceIndex < sentences.length) {
			String sentence = sentences[sentenceIndex];
			sentenceIndex++;
			Integer pauseDurationObj = homeViewModel.getPauseDuration().getValue();
			int pauseDuration = pauseDurationObj == null ? 0 : pauseDurationObj * 1000;

			// Play silent sound to wake up Bluetooth speaker
			playSilence(sentenceIndex == 1 ? 500 : sentence.startsWith("\n") ? 2 * pauseDuration : pauseDuration,
					() -> textToSpeech.speak(sentence.trim(), TextToSpeech.QUEUE_FLUSH, null, "utteranceId"));
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