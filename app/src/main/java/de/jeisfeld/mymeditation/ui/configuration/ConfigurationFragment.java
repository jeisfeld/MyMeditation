package de.jeisfeld.mymeditation.ui.configuration;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentConfigurationBinding;

public class ConfigurationFragment extends Fragment {

	private FragmentConfigurationBinding binding;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		ConfigurationViewModel configurationViewModel =
				new ViewModelProvider(requireActivity()).get(ConfigurationViewModel.class);

		binding = FragmentConfigurationBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		root.post(() -> {
			WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(requireActivity().getWindow().getDecorView());

			if (insets != null) {
				Insets systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

				View navView = requireActivity().findViewById(R.id.nav_view);
				int bottomNavHeight = navView.getHeight();
				int navOnlyHeight = Math.max(0, bottomNavHeight - systemInsets.bottom);

				root.setPadding(
						systemInsets.left,
						systemInsets.top + navOnlyHeight,
						systemInsets.right,
						systemInsets.bottom + navOnlyHeight
				);
			}
		});

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