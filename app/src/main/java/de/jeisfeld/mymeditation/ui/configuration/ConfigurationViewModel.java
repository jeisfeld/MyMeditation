package de.jeisfeld.mymeditation.ui.configuration;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class ConfigurationViewModel extends ViewModel {

	private final MutableLiveData<String> mSystemPrompt;

	private final MutableLiveData<String> mQueryTemplate;

	public ConfigurationViewModel() {
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

	protected void setSystemPrompt(final String systemPrompt) {
		mSystemPrompt.postValue(systemPrompt);
		PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt, systemPrompt);
	}

	protected void setQueryTemplate(final String queryTemplate) {
		if (!queryTemplate.contains("@TEXT@")) {
			return;
		}
		mQueryTemplate.postValue(queryTemplate);
		PreferenceUtil.setSharedPreferenceString(R.string.key_query_template, queryTemplate);
	}

}