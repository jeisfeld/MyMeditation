package de.jeisfeld.mymeditation;

import android.content.Context;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import de.jeisfeld.mymeditation.testutil.BaselineTest;
import de.jeisfeld.mymeditation.ui.PageInsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class PageInsetsTest extends BaselineTest {
    /** Simulate the original window bars before AppCompat consumes them for descendant dispatch. */
    private static final class WindowRoot extends FrameLayout {
        WindowRoot(Context context) { super(context); }
        @Override public WindowInsets getRootWindowInsets() {
            return new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars(),
                    android.graphics.Insets.of(8, 24, 12, 48)).build();
        }
    }
    @Test public void consumedInsetsStillReserveWindowBarsAndTabsBeforeAndAfterEditing() {
        WindowRoot root = new WindowRoot(Application.getAppContext());
        View navigation = new View(root.getContext()); navigation.setId(R.id.nav_view);
        root.addView(navigation);
        boolean[] editing = {false};
        try (PageInsets page = new PageInsets(root, value -> editing[0] = value)) {
            WindowInsetsCompat consumed = new WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
                    .setVisible(WindowInsetsCompat.Type.ime(), false).build();
            ViewCompat.dispatchApplyWindowInsets(root, consumed);
            assertEquals(80, root.getPaddingTop()); assertEquals(104, root.getPaddingBottom());
            // Initial dispatch can precede layout; measuring the tab bar must not change the safe area.
            navigation.layout(0, 0, 411, 104);
            ViewCompat.dispatchApplyWindowInsets(root, consumed);
            assertEquals(80, root.getPaddingTop()); assertEquals(104, root.getPaddingBottom());
            assertEquals(8, root.getPaddingLeft()); assertEquals(12, root.getPaddingRight());
            ViewCompat.dispatchApplyWindowInsets(root, new WindowInsetsCompat.Builder(consumed)
                    .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 320))
                    .setVisible(WindowInsetsCompat.Type.ime(), true).build());
            assertTrue(editing[0]); assertEquals(80, root.getPaddingTop()); assertEquals(320, root.getPaddingBottom());
            ViewCompat.dispatchApplyWindowInsets(root, consumed);
            assertFalse(editing[0]); assertEquals(80, root.getPaddingTop()); assertEquals(104, root.getPaddingBottom());
        }
    }
}
