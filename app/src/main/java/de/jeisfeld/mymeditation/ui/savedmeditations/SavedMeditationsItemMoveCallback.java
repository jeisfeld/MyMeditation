package de.jeisfeld.mymeditation.ui.savedmeditations;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Callback for handling drag of stored colors.
 */
public class SavedMeditationsItemMoveCallback extends ItemTouchHelper.Callback {
	/**
	 * The adapter.
	 */
	private final ItemTouchHelperContract mAdapter;

	/**
	 * Constructor.
	 *
	 * @param adapter The adapter.
	 */
	public SavedMeditationsItemMoveCallback(final ItemTouchHelperContract adapter) {
		mAdapter = adapter;
	}

	@Override
	public final boolean isLongPressDragEnabled() {
		return false;
	}

	@Override
	public final boolean isItemViewSwipeEnabled() {
		return false;
	}

	@Override
	public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, final int direction) {

	}

	@Override
	public final int getMovementFlags(@NonNull final RecyclerView recyclerView, @NonNull final RecyclerView.ViewHolder viewHolder) {
		int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
		return makeMovementFlags(dragFlags, 0);
	}

	@Override
	public final boolean onMove(@NonNull final RecyclerView recyclerView, final RecyclerView.ViewHolder viewHolder,
								final RecyclerView.ViewHolder target) {
		mAdapter.onRowMoved(viewHolder.getAdapterPosition(), target.getAdapterPosition());
		return true;
	}

	@Override
	public final void onSelectedChanged(final RecyclerView.ViewHolder viewHolder, final int actionState) {
		if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
			if (viewHolder instanceof SavedMeditationsViewAdapter.MyViewHolder) {
				SavedMeditationsViewAdapter.MyViewHolder myViewHolder = (SavedMeditationsViewAdapter.MyViewHolder) viewHolder;
				mAdapter.onRowSelected(myViewHolder);
			}
		}
		super.onSelectedChanged(viewHolder, actionState);
	}

	@Override
	public final void clearView(@NonNull final RecyclerView recyclerView, @NonNull final RecyclerView.ViewHolder viewHolder) {
		super.clearView(recyclerView, viewHolder);
		if (viewHolder instanceof SavedMeditationsViewAdapter.MyViewHolder) {
			SavedMeditationsViewAdapter.MyViewHolder myViewHolder = (SavedMeditationsViewAdapter.MyViewHolder) viewHolder;
			mAdapter.onRowClear(myViewHolder);
		}
	}

	/**
	 * Callback for row actions.
	 */
	public interface ItemTouchHelperContract {
		/**
		 * Callback called when row is moved.
		 *
		 * @param fromPosition start position.
		 * @param toPosition end position.
		 */
		void onRowMoved(int fromPosition, int toPosition);

		/**
		 * Callback called when a row is selected.
		 *
		 * @param myViewHolder The holder of the selected view.
		 */
		void onRowSelected(SavedMeditationsViewAdapter.MyViewHolder myViewHolder);

		/**
		 * Callback called when a row is deselected.
		 *
		 * @param myViewHolder The holder of the selected view.
		 */
		void onRowClear(SavedMeditationsViewAdapter.MyViewHolder myViewHolder);
	}

}
