package de.jeisfeld.mymeditation.ui.home;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class HomeViewModel extends ViewModel {

	private final MutableLiveData<String> mMeditationContent;

	private final MutableLiveData<String> mMeditationText;

	private final MutableLiveData<Integer> mPauseDuration;

	public HomeViewModel() {
		mMeditationContent = new MutableLiveData<>();
		mMeditationContent.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
		mMeditationText = new MutableLiveData<>();
		mMeditationText.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
		mPauseDuration = new MutableLiveData<>();
		mPauseDuration.setValue(PreferenceUtil.getSharedPreferenceInt(R.string.key_pause_duration, 0));
	}

	public LiveData<String> getMeditationContent() {
		return mMeditationContent;
	}

	public LiveData<String> getMeditationText() {
		return mMeditationText;
	}

	public LiveData<Integer> getPauseDuration() {
		return mPauseDuration;
	}

	protected void setMeditationContent(final String meditationContent) {
		mMeditationContent.postValue(meditationContent);
		PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, meditationContent);
	}

	protected void setMeditationText(final String meditationText) {
		mMeditationText.postValue(meditationText);
		PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, meditationText);
	}

	protected void setPauseDuration(final String pauseDuration) {
		int pauseDurationInt;
		try {
			pauseDurationInt = Integer.parseInt(pauseDuration);
		}
		catch (NumberFormatException e) {
			pauseDurationInt = 0;
		}
		mPauseDuration.postValue(pauseDurationInt);
		PreferenceUtil.setSharedPreferenceInt(R.string.key_pause_duration, pauseDurationInt);
	}

}