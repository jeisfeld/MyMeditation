package de.jeisfeld.mymeditation.ui.savedmeditations;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import de.jeisfeld.mymeditation.R;
import de.jeisfeld.mymeditation.ui.configuration.ConfigurationViewModel;
import de.jeisfeld.mymeditation.ui.meditation.MeditationViewModel;
import de.jeisfeld.mymeditation.util.DialogUtil;
import de.jeisfeld.mymeditation.util.PreferenceUtil;

/**
 * Adapter for the RecyclerView that allows to sort devices.
 */
public class SavedMeditationsViewAdapter extends RecyclerView.Adapter<SavedMeditationsViewAdapter.MyViewHolder>
		implements SavedMeditationsItemMoveCallback.ItemTouchHelperContract {
	/**
	 * The list of meditation ids.
	 */
	private final List<Integer> mMeditationIds;
	/**
	 * The listener identifying start of drag.
	 */
	private StartDragListener mStartDragListener;
	/**
	 * A reference to the fragment.
	 */
	private final WeakReference<Fragment> mFragment;

	/**
	 * Constructor.
	 *
	 * @param fragment     the calling fragment.
	 * @param recyclerView The recycler view.
	 */
	public SavedMeditationsViewAdapter(final Fragment fragment, final RecyclerView recyclerView) {
		mMeditationIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
		mFragment = new WeakReference<>(fragment);
	}

	/**
	 * Set the listener identifying start of drag.
	 *
	 * @param startDragListener The listener.
	 */
	public void setStartDragListener(final StartDragListener startDragListener) {
		mStartDragListener = startDragListener;
	}

	@NonNull
	@Override
	public final MyViewHolder onCreateViewHolder(final ViewGroup parent, final int viewType) {
		View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_view_saved_meditations, parent, false);
		return new MyViewHolder(itemView);
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public final void onBindViewHolder(@NonNull final MyViewHolder holder, final int position) {
		final Integer meditationId = mMeditationIds.get(position);

		String meditationName = PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, meditationId);
		holder.mTitle.setText(meditationName);

		holder.mDragHandle.setOnTouchListener((view, event) -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				mStartDragListener.requestDrag(holder);
			}
			else if (event.getAction() == MotionEvent.ACTION_UP) {
				view.performClick();
			}
			return false;
		});

		holder.mDeleteButton.setOnClickListener(v -> {
			Fragment fragment = mFragment.get();
			if (fragment != null && fragment.getActivity() != null) {
				DialogUtil.displayConfirmationMessage(fragment.getActivity(), dialog -> {
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_name, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_system_prompt, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_query_template, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_content, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_text, meditationId);
					ArrayList<Integer> savedMeditationIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
					savedMeditationIds.remove(Integer.valueOf(meditationId));
					PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, savedMeditationIds);
					mMeditationIds.remove(Integer.valueOf(meditationId));
					notifyItemRemoved(position);
					notifyItemRangeChanged(position, mMeditationIds.size() - position);
				}, null, R.string.button_cancel, R.string.button_delete, R.string.message_confirm_delete_meditation, meditationName);
			}
		});

		holder.mTitle.setOnClickListener(v -> {
			PreferenceUtil.setSharedPreferenceString(R.string.key_system_prompt,
					PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_system_prompt, meditationId));
			PreferenceUtil.setSharedPreferenceString(R.string.key_query_template,
					PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_query_template, meditationId));
			PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_content,
					PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_content, meditationId));
			PreferenceUtil.setSharedPreferenceString(R.string.key_meditation_text,
					PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_text, meditationId));

			Fragment fragment = mFragment.get();
			if (fragment != null) {
				new ViewModelProvider(fragment.getActivity()).get(ConfigurationViewModel.class).getStoredData();
				new ViewModelProvider(fragment.getActivity()).get(MeditationViewModel.class).getStoredData();
			}
			DialogUtil.displayToast(fragment.getActivity(), R.string.toast_meditation_restored, meditationName);
		});

	}

	@Override
	public final int getItemCount() {
		return mMeditationIds.size();
	}

	@Override
	public final void onRowMoved(final int fromPosition, final int toPosition) {
		if (fromPosition < toPosition) {
			for (int i = fromPosition; i < toPosition; i++) {
				Collections.swap(mMeditationIds, i, i + 1);
			}
		}
		else {
			for (int i = fromPosition; i > toPosition; i--) {
				Collections.swap(mMeditationIds, i, i - 1);
			}
		}
		PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, mMeditationIds);
		notifyItemMoved(fromPosition, toPosition);
	}

	@Override
	public final void onRowSelected(final MyViewHolder myViewHolder) {
		myViewHolder.mRowView.setBackgroundColor(android.graphics.Color.LTGRAY);

	}

	@Override
	public final void onRowClear(final MyViewHolder myViewHolder) {
		myViewHolder.mRowView.setBackgroundColor(android.graphics.Color.TRANSPARENT);

	}

	/**
	 * The view holder of the items.
	 */
	public static class MyViewHolder extends RecyclerView.ViewHolder {
		/**
		 * The whole item.
		 */
		private final View mRowView;
		/**
		 * The title.
		 */
		private final TextView mTitle;
		/**
		 * The image view.
		 */
		private final ImageView mDragHandle;
		/**
		 * The delete button.
		 */
		private final ImageView mDeleteButton;

		/**
		 * Constructor.
		 *
		 * @param itemView The item view.
		 */
		public MyViewHolder(final View itemView) {
			super(itemView);
			mRowView = itemView;
			mTitle = itemView.findViewById(R.id.textViewMeditationName);
			mDragHandle = itemView.findViewById(R.id.imageViewDragHandle);
			mDeleteButton = itemView.findViewById(R.id.imageViewDelete);
		}
	}

	/**
	 * A listener for starting the drag.
	 */
	public interface StartDragListener {
		/**
		 * Method for starting the drag.
		 *
		 * @param viewHolder The view Holder.
		 */
		void requestDrag(RecyclerView.ViewHolder viewHolder);
	}


}
