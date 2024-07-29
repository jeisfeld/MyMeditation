package de.jeisfeld.mymeditation.ui.configuration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.databinding.FragmentConfigurationBinding;

public class ConfigurationFragment extends Fragment {

	private FragmentConfigurationBinding binding;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		ConfigurationViewModel configurationViewModel =
				new ViewModelProvider(this).get(ConfigurationViewModel.class);

		binding = FragmentConfigurationBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		configurationViewModel.getSystemPrompt().observe(getViewLifecycleOwner(), binding.editTextSystemPrompt::setText);
		configurationViewModel.getQueryTemplate().observe(getViewLifecycleOwner(), binding.editTextQueryTemplate::setText);

		binding.editTextSystemPrompt.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				configurationViewModel.setSystemPrompt(binding.editTextSystemPrompt.getText().toString());
			}
		});
		binding.editTextQueryTemplate.setOnFocusChangeListener((v, hasFocus) -> {
			if (!hasFocus) {
				configurationViewModel.setQueryTemplate(binding.editTextQueryTemplate.getText().toString());
			}
		});


		return root;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}
}