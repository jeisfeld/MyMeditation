package de.jeisfeld.mymeditation.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class HomeViewModel extends ViewModel {

	private final MutableLiveData<String> mMeditationContent;

	private final MutableLiveData<String> mMeditationText;

	public HomeViewModel() {
		mMeditationContent = new MutableLiveData<>();
		mMeditationContent.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
		mMeditationText = new MutableLiveData<>();
		mMeditationText.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
	}

	public LiveData<String> getMeditationContent() {
		return mMeditationContent;
	}

	public LiveData<String> getMeditationText() {
		return mMeditationText;
	}

	public void setMeditationText(final String meditationText) {
		mMeditationText.postValue(meditationText);
		PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, meditationText);
	}

}