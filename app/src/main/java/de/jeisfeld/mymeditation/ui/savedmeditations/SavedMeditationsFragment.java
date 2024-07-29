package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import de.jeisfeld.mymeditation.databinding.FragmentSavedMeditationsBinding;

public class SavedMeditationsFragment extends Fragment {

	private FragmentSavedMeditationsBinding binding;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		SavedMeditationsViewModel savedMeditationsViewModel =
				new ViewModelProvider(this).get(SavedMeditationsViewModel.class);

		binding = FragmentSavedMeditationsBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		final TextView textView = binding.textNotifications;
		savedMeditationsViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);
		return root;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		binding = null;
	}
}