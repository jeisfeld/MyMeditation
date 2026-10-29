package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

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
	private int lastOfflinePlaybackId;

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

		final RecyclerView recyclerView = binding.recyclerViewSavedMeditations;
		populateRecyclerView(recyclerView);

		binding.buttonSaveCurrentMeditation.setOnClickListener(v -> DialogUtil.displayInputDialog(requireActivity(), new RequestInputDialogListener() {
			@Override
			public void onDialogPositiveClick(DialogFragment dialog, String text) {
				de.jeisfeld.mymeditation.audio.OfflineMeditations.save(text,
						PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt),
						PreferenceUtil.getSharedPreferenceString(R.string.key_query_template),
						PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content),
						PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
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
					new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setTitle(R.string.export_title)
							.setMessage(state.error).setPositiveButton(R.string.button_ok, null).show();
				} else {
					android.widget.Toast.makeText(requireContext(), getString(R.string.export_saved, state.name), android.widget.Toast.LENGTH_LONG).show();
				}
			}
		});
		de.jeisfeld.mymeditation.audio.OfflinePlaybackService.state().observe(getViewLifecycleOwner(), state -> {
			if (recyclerView.getAdapter() instanceof SavedMeditationsViewAdapter) {
				((SavedMeditationsViewAdapter) recyclerView.getAdapter()).playbackChanged(lastOfflinePlaybackId, state.id);
			}
			lastOfflinePlaybackId = state.id;
			if (events.consumeOfflineError(state.errorId)) {
				new androidx.appcompat.app.AlertDialog.Builder(requireContext()).setMessage(state.error)
						.setPositiveButton(R.string.button_ok, null).show();
			}
		});

		return root;
	}

	@Override
	public void onDestroyView() {
		if (touchHelper != null) { touchHelper.attachToRecyclerView(null); touchHelper = null; }
		super.onDestroyView();
		binding = null;
	}

	/**
	 * Populate the recycler view for the stored colors.
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
	}
}