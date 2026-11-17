package de.jeisfeld.mymeditation.ui.meditation;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.widget.AppCompatEditText;
import java.util.function.IntConsumer;

/** Reports cursor movement from touch, keyboard and accessibility, excluding text replacement. */
public final class MeditationEditText extends AppCompatEditText {
    private IntConsumer cursorChanged;
    private boolean replacingText;
    public MeditationEditText(Context context, AttributeSet attrs) { super(context, attrs); }
    public void setOnCursorChangedListener(IntConsumer listener) { cursorChanged = listener; }
    @Override public void setText(CharSequence text, BufferType type) {
        boolean previous = replacingText;
        replacingText = true;
        try { super.setText(text, type); }
        finally { replacingText = previous; }
    }
    @Override protected void onSelectionChanged(int start, int end) {
        super.onSelectionChanged(start, end);
        if (!replacingText && start >= 0 && start == end && cursorChanged != null) { cursorChanged.accept(start); }
    }
}
