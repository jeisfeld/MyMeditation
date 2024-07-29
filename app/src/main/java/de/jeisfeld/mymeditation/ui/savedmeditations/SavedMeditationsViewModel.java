package de.jeisfeld.mymeditation.ui.savedmeditations;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SavedMeditationsViewModel extends ViewModel {

	private final MutableLiveData<String> mText;

	public SavedMeditationsViewModel() {
		mText = new MutableLiveData<>();
		mText.setValue("This is notifications fragment");
	}

	public LiveData<String> getText() {
		return mText;
	}
}