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
 * Saved meditations, with filtered views over the full persisted ID list.
 */
public class SavedMeditationsViewAdapter extends RecyclerView.Adapter<SavedMeditationsViewAdapter.MyViewHolder>
		implements SavedMeditationsItemMoveCallback.ItemTouchHelperContract {
	/**
	 * The list of meditation ids.
	 */
	private final List<Integer> mMeditationIds = new ArrayList<>();
	private String mNameFilter = "";
	private boolean mAudioOnly;
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
		mMeditationIds.addAll(PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids));
		mFragment = new WeakReference<>(fragment);
	}

    @SuppressLint("NotifyDataSetChanged")
    public void setFilter(String query, boolean audioOnly) {
        mNameFilter = query;
        mAudioOnly = audioOnly;
        Fragment owner = mFragment.get();
        if (owner == null || owner.getContext() == null) { return; }
        List<Integer> all = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
        mMeditationIds.clear();
        mMeditationIds.addAll(SavedMeditationFilter.select(all, query, audioOnly,
                id -> PreferenceUtil.getIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id),
                id -> de.jeisfeld.mymeditation.audio.SavedAudioLibrary.hasAudio(owner.requireContext(), id)));
        notifyDataSetChanged();
    }

    @Override public boolean canReorder() { return mNameFilter.trim().isEmpty() && !mAudioOnly; }

    private void rename(Fragment fragment, int id, String oldName) {
        android.widget.EditText input = new android.widget.EditText(fragment.requireContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setHint(R.string.text_dialog_save_meditation);
        input.setText(oldName);
        input.selectAll();
        android.widget.FrameLayout container = new android.widget.FrameLayout(fragment.requireContext());
        int padding = fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        container.setPadding(padding, 0, padding, 0);
        container.addView(input);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.rename_saved_meditation).setView(container)
                .setNegativeButton(R.string.button_cancel, null).setPositiveButton(R.string.button_save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError(fragment.getString(R.string.saved_meditation_name_required)); return; }
            // Keep the stable ID: text, settings, order and the associated MP3 remain attached.
            if (PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids).contains(id)) {
                PreferenceUtil.setIndexedSharedPreferenceString(R.string.key_saved_meditation_name, id, name);
                setFilter(mNameFilter, mAudioOnly);
            }
            dialog.dismiss();
        }));
        dialog.show();
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
        holder.mTitle.setTooltipText(meditationName);
        holder.mRenameButton.setOnClickListener(v -> {
            Fragment fragment = mFragment.get();
            if (fragment != null && fragment.getContext() != null) { rename(fragment, meditationId, meditationName); }
        });
        holder.mDragHandle.setVisibility(canReorder() ? View.VISIBLE : View.INVISIBLE);
		Fragment owner = mFragment.get();
		boolean offline = owner != null && owner.getContext() != null
				&& de.jeisfeld.mymeditation.audio.SavedAudioLibrary.hasAudio(owner.requireContext(), meditationId);
		holder.mPlayButton.setVisibility(offline ? View.VISIBLE : View.GONE);
		holder.mShareButton.setVisibility(offline ? View.VISIBLE : View.GONE);
		de.jeisfeld.mymeditation.audio.OfflinePlaybackService.State state =
				de.jeisfeld.mymeditation.audio.OfflinePlaybackService.state().getValue();
		de.jeisfeld.mymeditation.audio.MeditationPlaybackService.State live = de.jeisfeld.mymeditation.audio.MeditationPlaybackService.state().getValue();
        boolean playing = state.id == meditationId && (state.playing || state.waitingForAudioFocus)
                || live.savedId == meditationId && (live.running || live.waitingForAudioFocus);
		holder.mPlayButton.setImageResource(playing ? R.drawable.ic_playback_pause : R.drawable.ic_playback_play);
		if (owner != null) {
			String action = owner.getString(playing ? R.string.notification_pause : R.string.button_play_offline);
			holder.mPlayButton.setContentDescription(action); holder.mPlayButton.setTooltipText(action);
		}
		holder.mPlayButton.setOnClickListener(v -> {
			Fragment fragment = mFragment.get();
			if (fragment == null || fragment.getContext() == null) { return; }
			try {
                de.jeisfeld.mymeditation.audio.SentenceAudioStore.Book book = de.jeisfeld.mymeditation.audio.SavedAudioLibrary.book(fragment.requireContext(), meditationId);
                if (book != null && book.isComplete()) {
                    de.jeisfeld.mymeditation.audio.MeditationPlaybackService.State current = de.jeisfeld.mymeditation.audio.MeditationPlaybackService.state().getValue();
                    if (!current.active || current.savedId != meditationId) {
                        de.jeisfeld.mymeditation.audio.SavedAudioLibrary.restore(fragment.requireContext(), meditationId);
                        refreshDraft(fragment);
                    }
                    de.jeisfeld.mymeditation.audio.MeditationPlaybackService.playSaved(fragment.requireContext(), meditationId);
                    ((com.google.android.material.bottomnavigation.BottomNavigationView) fragment.requireActivity().findViewById(R.id.nav_view))
                            .setSelectedItemId(R.id.navigation_meditation);
                } else { de.jeisfeld.mymeditation.audio.OfflinePlaybackService.toggle(fragment.requireContext(), meditationId); }
            }
			catch (RuntimeException e) { android.widget.Toast.makeText(fragment.requireContext(), R.string.offline_play_failed, android.widget.Toast.LENGTH_LONG).show(); }
		});
        holder.mShareButton.setOnClickListener(v -> {
            Fragment fragment = mFragment.get();
            if (fragment != null && fragment.getContext() != null) {
                de.jeisfeld.mymeditation.audio.AudioSharing.request(fragment, meditationId);
            }
        });

		holder.mDragHandle.setOnTouchListener((view, event) -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN && canReorder() && mStartDragListener != null) {
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
					de.jeisfeld.mymeditation.audio.OfflinePlaybackService.stopIfPlaying(fragment.requireContext(), meditationId);
					de.jeisfeld.mymeditation.audio.OfflineMeditations.file(fragment.requireContext(), meditationId).delete();
                    if (de.jeisfeld.mymeditation.audio.MeditationPlaybackService.state().getValue().savedId == meditationId) {
                        de.jeisfeld.mymeditation.audio.MeditationPlaybackService.end(fragment.requireContext());
                    }
                    de.jeisfeld.mymeditation.audio.SavedAudioLibrary.unlink(meditationId);
					PreferenceUtil.setSharedPreferenceString("offline_settings_" + meditationId, "");
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_name, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_system_prompt, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_query_template, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_content, meditationId);
					PreferenceUtil.removeIndexedSharedPreference(R.string.key_saved_meditation_text, meditationId);
					ArrayList<Integer> savedMeditationIds = PreferenceUtil.getSharedPreferenceIntList(R.string.key_saved_meditation_ids);
					savedMeditationIds.remove(Integer.valueOf(meditationId));
					PreferenceUtil.setSharedPreferenceIntList(R.string.key_saved_meditation_ids, savedMeditationIds);
                    de.jeisfeld.mymeditation.audio.SavedAudioLibrary.cleanupDeleted(fragment.requireContext());
					int removed = mMeditationIds.indexOf(meditationId);
					mMeditationIds.remove(Integer.valueOf(meditationId));
					if (removed >= 0) { notifyItemRemoved(removed); notifyItemRangeChanged(removed, mMeditationIds.size() - removed); }
				}, null, R.string.button_cancel, R.string.button_delete, R.string.message_confirm_delete_meditation, meditationName);
			}
		});

        holder.mTitle.setOnClickListener(v -> {
            Fragment fragment = mFragment.get();
            if (fragment == null || fragment.getContext() == null) { return; }
            if (de.jeisfeld.mymeditation.audio.MeditationPlaybackService.state().getValue().active) {
                de.jeisfeld.mymeditation.audio.MeditationPlaybackService.end(fragment.requireContext());
            }
            de.jeisfeld.mymeditation.audio.OfflinePlaybackService.State previous = de.jeisfeld.mymeditation.audio.OfflinePlaybackService.state().getValue();
            if (previous.id > 0) { de.jeisfeld.mymeditation.audio.OfflinePlaybackService.stopIfPlaying(fragment.requireContext(), previous.id); }
            de.jeisfeld.mymeditation.audio.SavedAudioLibrary.restore(fragment.requireContext(), meditationId);
            refreshDraft(fragment);
            DialogUtil.displayToast(fragment.requireActivity(), R.string.toast_meditation_restored, meditationName);
        });

	}

    private static void refreshDraft(Fragment fragment) {
        new ViewModelProvider(fragment.requireActivity()).get(ConfigurationViewModel.class).getStoredData();
        new ViewModelProvider(fragment.requireActivity()).get(MeditationViewModel.class).getStoredData();
    }

	public void playbackChanged(int previousId, int currentId) {
		int previous = mMeditationIds.indexOf(previousId), current = mMeditationIds.indexOf(currentId);
		if (previous >= 0) { notifyItemChanged(previous); }
		if (current >= 0 && current != previous) { notifyItemChanged(current); }
	}

	@Override
	public final int getItemCount() {
		return mMeditationIds.size();
	}

	@Override
	public final void onRowMoved(final int fromPosition, final int toPosition) {
        if (!canReorder() || fromPosition < 0 || toPosition < 0
                || fromPosition >= mMeditationIds.size() || toPosition >= mMeditationIds.size()) { return; }
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
		private final android.widget.ImageButton mPlayButton, mShareButton, mRenameButton;

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
			mPlayButton = itemView.findViewById(R.id.buttonPlayOffline);
			mShareButton = itemView.findViewById(R.id.buttonShareOffline);
            mRenameButton = itemView.findViewById(R.id.buttonRenameMeditation);
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
