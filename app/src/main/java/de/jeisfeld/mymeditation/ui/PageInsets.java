package de.jeisfeld.mymeditation.ui;

import android.view.View;
import android.view.ViewTreeObserver;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.util.function.Consumer;

/** Keeps pages above the IME and updates their editing layout without replacing focus/save listeners. */
public final class PageInsets implements AutoCloseable {
    private final View root;
    private final Consumer<Boolean> changed;
    private boolean keyboard, closed;
    private int navigationHeight;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focusChanged = (oldFocus, newFocus) -> refresh();

    public PageInsets(View root, Consumer<Boolean> changed) {
        this.root = root; this.changed = changed;
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focusChanged);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            apply(insets);
            return insets;
        });
        // Match the original pages' post-layout initialization, when the tab height is available.
        root.post(() -> {
            WindowInsetsCompat window = ViewCompat.getRootWindowInsets(root);
            if (window != null) { apply(window); }
        });
    }
    private void apply(WindowInsetsCompat insets) {
        if (closed) { return; }
        keyboard = insets.isVisible(WindowInsetsCompat.Type.ime());
        // AppCompat can consume the system bars before dispatching to the page. Use the
        // original window insets, as the pages did before keyboard editing was introduced.
        WindowInsetsCompat window = ViewCompat.getRootWindowInsets(root);
        Insets bars = (window == null ? insets : window).getInsets(WindowInsetsCompat.Type.systemBars());
        View navigation = root.getRootView().findViewById(de.jeisfeld.mymeditation.R.id.nav_view);
        if (!keyboard && navigation != null && navigation.getHeight() > 0) { navigationHeight = navigation.getHeight(); }
        // A restored activity may first receive insets before the tab bar has been measured.
        int navOnly = navigationHeight > 0 ? Math.max(0, navigationHeight - bars.bottom)
                : root.getResources().getDimensionPixelSize(com.google.android.material.R.dimen.design_bottom_navigation_height);
        // The top allowance also keeps content below the app header; hiding the bottom tabs
        // must only release bottom space, even while the focused editor fills the page.
        root.setPadding(bars.left, bars.top + navOnly, bars.right,
                keyboard ? Math.max(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom) : bars.bottom + navOnly);
        refresh();
    }
    public void refresh() { if (!closed) { changed.accept(keyboard); } }
    @Override public void close() {
        closed = true;
        if (root.getViewTreeObserver().isAlive()) { root.getViewTreeObserver().removeOnGlobalFocusChangeListener(focusChanged); }
        ViewCompat.setOnApplyWindowInsetsListener(root, null);
    }
}
