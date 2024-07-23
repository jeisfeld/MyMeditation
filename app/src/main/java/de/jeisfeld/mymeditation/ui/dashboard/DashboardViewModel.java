package de.jeisfeld.mymeditation.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class DashboardViewModel extends ViewModel {

	private final MutableLiveData<String> mSystemPrompt;

	private final MutableLiveData<String> mQueryTemplate;

	public DashboardViewModel() {
		mSystemPrompt = new MutableLiveData<>();
		mSystemPrompt.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
		mQueryTemplate = new MutableLiveData<>();
		mQueryTemplate.setValue(PreferenceUtil.getSharedPreferenceString(R.string.key_query_template));
	}

	public LiveData<String> getSystemPrompt() {
		return mSystemPrompt;
	}
	public LiveData<String> getQueryTemplate() {
		return mQueryTemplate;
	}

}