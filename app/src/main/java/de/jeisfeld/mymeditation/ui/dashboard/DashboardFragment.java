package de.jeisfeld.mymeditation.ui.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.databinding.FragmentDashboardBinding;

public class DashboardFragment extends Fragment {

	private FragmentDashboardBinding binding;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		DashboardViewModel dashboardViewModel =
				new ViewModelProvider(this).get(DashboardViewModel.class);

		binding = FragmentDashboardBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		dashboardViewModel.getSystemPrompt().observe(getViewLifecycleOwner(), binding.editTextSystemPrompt::setText);
		dashboardViewModel.getQueryTemplate().observe(getViewLifecycleOwner(), binding.editTextQueryTemplate::setText);
		return root;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}
}