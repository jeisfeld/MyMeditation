package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.databinding.FragmentSavedMeditationsBinding;
import de.jeisfeld.mymeditation.util.DialogUtil;
import de.jeisfeld.mymeditation.util.DialogUtil.RequestInputDialogFragment.RequestInputDialogListener;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

public class SavedMeditationsFragment extends Fragment {

	private FragmentSavedMeditationsBinding binding;

	public View onCreateView(@NonNull LayoutInflater inflater,
							 ViewGroup container, Bundle savedInstanceState) {
		binding = FragmentSavedMeditationsBinding.inflate(inflater, container, false);
		View root = binding.getRoot();

		final RecyclerView recyclerView = binding.recyclerViewSavedMeditations;
		populateRecyclerView(recyclerView);

		binding.buttonSaveCurrentMeditation.setOnClickListener(v -> DialogUtil.displayInputDialog(requireActivity(), new RequestInputDialogListener() {
			@Override
			public void onDialogPositiveClick(DialogFragment dialog, String text) {
				int newMeditationId = PreferenceUtil.getSharedPreferenceInt(R.string.key_max_saved_meditation_id, 0) + 1;
				PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, newMeditationId, text);
				PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_system_prompt, newMeditationId,
						PreferenceUtil.getSharedPreferenceString(R.string.key_system_prompt));
				PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_query_template, newMeditationId,
						PreferenceUtil.getSharedPreferenceString(R.string.key_query_template));
				PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_content, newMeditationId,
						PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_content));
				PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_text, newMeditationId,
						PreferenceUtil.getSharedPreferenceString(R.string.key_meditation_text));
				ArrayList<Integer> savedMeditationIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
				savedMeditationIds.add(newMeditationId);
				PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, savedMeditationIds);
				PreferenceUtil.setSharedPreferenceInt(R.string.key_max_saved_meditation_id, newMeditationId);
				populateRecyclerView(recyclerView);
			}

			@Override
			public void onDialogNegativeClick(DialogFragment dialog) {
				// do nothing
			}
		}, R.string.title_dialog_save_meditation, R.string.button_save, "", R.string.text_dialog_save_meditation));

		return root;
	}

	@Override
	public void onDestroyView() {
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
		ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
		adapter.setStartDragListener(touchHelper::startDrag);
		touchHelper.attachToRecyclerView(recyclerView);

		recyclerView.setAdapter(adapter);
	}
}