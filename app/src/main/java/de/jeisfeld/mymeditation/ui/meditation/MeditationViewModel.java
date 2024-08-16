package de.jeisfeld.mymeditation.ui.meditation;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class MeditationViewModel extends ViewModel {

	private final MutableLiveData<String> mMeditationContent;

	private final MutableLiveData<String> mMeditationText;

	private final MutableLiveData<Integer> mPauseDuration;

	private final MutableLiveData<Integer> mSeekBarMax;

	private final MutableLiveData<Integer> mSeekBarProgress;

	private final MutableLiveData<Boolean> mIsMeditationRunning;

	public MeditationViewModel() {
		mMeditationContent = new MutableLiveData<>();
		mMeditationText = new MutableLiveData<>();
		mPauseDuration = new MutableLiveData<>();
		getStoredData();
		mSeekBarMax = new MutableLiveData<>();
		mSeekBarMax.setValue(0);
		mSeekBarProgress = new MutableLiveData<>();
		mSeekBarProgress.setValue(0);
		mIsMeditationRunning = new MutableLiveData<>();
		mIsMeditationRunning.setValue(false);
	}

	public void getStoredData() {
		mMeditationContent.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
		mMeditationText.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
		mPauseDuration.setValue(PreferenceUtil.getSharedPreferenceInt(R.string.key_pause_duration, 0));
	}

	protected LiveData<String> getMeditationContent() {
		return mMeditationContent;
	}

	protected LiveData<String> getMeditationText() {
		return mMeditationText;
	}

	protected LiveData<Integer> getPauseDuration() {
		return mPauseDuration;
	}

	protected LiveData<Integer> getSeekBarMax() {
		return mSeekBarMax;
	}

	protected LiveData<Integer> getSeekBarProgress() {
		return mSeekBarProgress;
	}

	protected LiveData<Boolean> isMeditationRunning() {
		return mIsMeditationRunning;
	}

	protected void setMeditationContent(final String meditationContent) {
		mMeditationContent.postValue(meditationContent);
		PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content, meditationContent);
	}

	protected void setMeditationText(final String meditationText) {
		mMeditationText.postValue(meditationText);
		PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text, meditationText);
	}

	protected void setPauseDuration(final int pauseDuration) {
		mPauseDuration.postValue(pauseDuration);
		PreferenceUtil.setSharedPreferenceInt(R.string.key_pause_duration, pauseDuration);
	}

	protected void setSeekBarMax(final int seekBarMax) {
		mSeekBarMax.postValue(seekBarMax);
	}

	protected void setSeekBarProgress(final int seekBarProgress) {
		mSeekBarProgress.postValue(seekBarProgress);
	}

	protected void setMeditationRunning(final Boolean isMeditationRunning) {
		mIsMeditationRunning.postValue(isMeditationRunning);
	}

}