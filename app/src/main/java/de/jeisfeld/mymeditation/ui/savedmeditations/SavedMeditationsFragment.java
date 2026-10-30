package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.ui.meditation.MeditationViewModel;
import de.jeisfeld.mymeditation.databinding.FragmentSavedMeditationsBinding;
import de.jeisfeld.mymeditation.util.DialogUtil;
import de.jeisfeld.mymeditation.util.DialogUtil.RequestInputDialogFragment.RequestInputDialogListener;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class SavedMeditationsFragment extends Fragment {

	private FragmentSavedMeditationsBinding binding;
	private ItemTouchHelper touchHelper;
    private int lastSavedPlaybackId;
    private String nameFilter = "";
    private boolean audioOnly;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentSavedMeditationsBinding.inflate(inflater, container, false);
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

        if (savedInstanceState != null) {
            nameFilter = savedInstanceState.getString("savedNameFilter", "");
            audioOnly = savedInstanceState.getBoolean("savedAudioOnly", false);
        }
        binding.editTextMeditationFilter.setText(nameFilter);
        binding.checkBoxAudioOnly.setChecked(audioOnly);
        binding.editTextMeditationFilter.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                nameFilter = s.toString();
                applyFilters();
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });
        binding.checkBoxAudioOnly.setOnCheckedChangeListener((button, checked) -> {
            audioOnly = checked;
            applyFilters();
        });

		final RecyclerView recyclerView = binding.recyclerViewSavedMeditations;
		populateRecyclerView(recyclerView);

		binding.buttonSaveCurrentMeditation.setOnClickListener(v -> DialogUtil.displayInputDialog(requireActivity(), new RequestInputDialogListener() {
			@Override
			public void onDialogPositiveClick(DialogFragment dialog, String text) {
				int savedId = de.jeisfeld.mymeditation.audio.SavedMeditations.save(text,
						PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt),
						PreferenceUtil.getSharedPreferenceString(R.string.key_query_template),
						PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content),
						PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
                de.jeisfeld.mymeditation.audio.SavedAudioLibrary.attachAvailable(requireContext(), savedId);
				populateRecyclerView(recyclerView);
			}

			@Override
			public void onDialogNegativeClick(DialogFragment dialog) {
				// do nothing
			}
		}, R.string.title_dialog_save_meditation, R.string.button_save, "", R.string.text_dialog_save_meditation));

		MeditationViewModel events = new androidx.lifecycle.ViewModelProvider(requireActivity()).get(MeditationViewModel.class);
		de.jeisfeld.mymeditation.audio.MeditationExportService.state().observe(getViewLifecycleOwner(), state -> {
			if (!state.active && state.savedId > 0) { populateRecyclerView(recyclerView); }
			if (events.consumeExportResult(state.resultId)) {
				if (!state.error.isEmpty()) {
					new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(R.string.button_share_offline)
							.setMessage(state.error).setPositiveButton(R.string.button_ok, null).show();
                } else if (state.sharedFile != null) {
                    de.jeisfeld.mymeditation.audio.AudioSharing.open(this, state.sharedFile, state.name);
                }
			}
		});
        de.jeisfeld.mymeditation.audio.SavedAudioLibrary.changes().observe(getViewLifecycleOwner(), version -> populateRecyclerView(recyclerView));
        de.jeisfeld.mymeditation.audio.MeditationPlaybackService.state().observe(getViewLifecycleOwner(), state -> {
            if (recyclerView.getAdapter() instanceof SavedMeditationsViewAdapter) {
                ((SavedMeditationsViewAdapter) recyclerView.getAdapter()).playbackChanged(lastSavedPlaybackId, state.savedId);
            }
            lastSavedPlaybackId = state.savedId;
        });

		return root;
	}

    private void applyFilters() {
        if (binding != null && binding.recyclerViewSavedMeditations.getAdapter() instanceof SavedMeditationsViewAdapter) {
            ((SavedMeditationsViewAdapter) binding.recyclerViewSavedMeditations.getAdapter()).setFilter(nameFilter, audioOnly);
        }
    }

    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("savedNameFilter", nameFilter);
        outState.putBoolean("savedAudioOnly", audioOnly);
    }

	@Override
	public void onDestroyView() {
		if (touchHelper != null) { touchHelper.attachToRecyclerView(null); touchHelper = null; }
		super.onDestroyView();
		binding = null;
	}

	/**
	 * Populate the recycler view, retaining the current filters.
	 *
	 * @param recyclerView The recycler view.
	 */
	private void populateRecyclerView(final RecyclerView recyclerView) {
		SavedMeditationsViewAdapter adapter = new SavedMeditationsViewAdapter(this, recyclerView);
		ItemTouchHelper.Callback callback = new SavedMeditationsItemMoveCallback(adapter);
		if (touchHelper != null) { touchHelper.attachToRecyclerView(null); }
		touchHelper = new ItemTouchHelper(callback);
		adapter.setStartDragListener(touchHelper::startDrag);
		touchHelper.attachToRecyclerView(recyclerView);

		recyclerView.setAdapter(adapter);
        applyFilters();
	}
}