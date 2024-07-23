package de.jeisfeld.mymeditation.ui.home;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Locale;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.Application;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentHomeBinding;
import de.jeisfeld.mymeditation.http.HttpSender;
import de.jeisfeld.mymeditation.util.Logger;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class HomeFragment extends Fragment {

	private FragmentHomeBinding binding;

	private TextToSpeech textToSpeech = null;

	private SpeakingThread speakingThread = null;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		HomeViewModel homeViewModel =
				new ViewModelProvider(this).get(HomeViewModel.class);

		binding = FragmentHomeBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		homeViewModel.getMeditationContent().observe(getViewLifecycleOwner(), binding.editTextMeditationContent::setText);
		homeViewModel.getMeditationText().observe(getViewLifecycleOwner(), binding.editTextMeditationText::setText);

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
			}, userMessage, systemMessage, null);
		});


		binding.buttonSpeakMeditation.setOnClickListener(v -> {
			textToSpeech = new TextToSpeech(getActivity(), status -> {

				if (status == TextToSpeech.SUCCESS && textToSpeech != null) {
					int result = textToSpeech.setLanguage(Locale.GERMAN);

					if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
						// Language data is missing or the language is not supported.
						// Handle error here
					}
					else {
						speakingThread = new SpeakingThread(homeViewModel.getMeditationText().getValue().split("\\."));
						speakingThread.start();
					}
				}

			});
		});

		return root;
	}

	@Override
	public void onDestroyView() {
		if (speakingThread != null) {
			speakingThread.interrupt();
			speakingThread = null;
		}
		if (textToSpeech != null) {
			textToSpeech.stop();
			textToSpeech.shutdown();
		}

		super.onDestroyView();
		binding = null;
	}

	private class SpeakingThread extends Thread {

		private String[] sentences;

		private SpeakingThread(String[] sentences) {
			this.sentences = sentences;
		}

		@Override
		public void run() {
			for (String sentence : sentences) {
				textToSpeech.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, null);
				if (Thread.currentThread().isInterrupted()) {
					return;
				}
				try {
					Thread.sleep(sentence.length() * 100 + 15000);
				}
				catch (InterruptedException e) {
					return;
				}
			}
		}
	}
}