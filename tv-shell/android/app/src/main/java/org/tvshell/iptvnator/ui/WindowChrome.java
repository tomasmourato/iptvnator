package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * The system bars around the shell's window. TV: immersive, the page fills the screen. Touch: the
 * status and navigation bars stay visible outside fullscreen video and the page is laid out between
 * them (and above the camera cutout and the soft keyboard) by padding the root view, so it never
 * depends on the WebView's own safe-area support.
 */
final class WindowChrome {

    private final Activity activity;
    private final View root;
    private boolean touch;
    private boolean fullscreen;

    WindowChrome(Activity activity, View root) {
        this.activity = activity;
        this.root = root;
    }

    /** Switches between the TV and the touch window; nothing changes while the mode stays TV. */
    void setTouch(boolean touch) {
        if (touch == this.touch) return;
        this.touch = touch;
        Window window = activity.getWindow();
        if (touch) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); // the theme is a fullscreen TV theme
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(false);
            window.getDecorView().setSystemUiVisibility(0);
            root.setOnApplyWindowInsetsListener(this::applyInsets);
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN); // the manifest's value
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) window.setDecorFitsSystemWindows(true);
            root.setOnApplyWindowInsetsListener(null);
            root.setPadding(0, 0, 0, 0);
        }
        refresh();
    }

    boolean touch() {
        return touch;
    }

    /** Fullscreen video in touch mode hides the bars (a swipe shows them for a moment). */
    void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        if (touch) refresh();
    }

    /** Re-applies the bars after a resume or a configuration change (the system may have reset them). */
    void refresh() {
        if (!touch) {
            hideSystemUi(activity);
            return;
        }
        Window window = activity.getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                if (fullscreen) controller.hide(WindowInsets.Type.systemBars());
                else controller.show(WindowInsets.Type.systemBars());
            }
        } else if (fullscreen) {
            hideSystemUi(activity);
        } else {
            window.getDecorView().setSystemUiVisibility(0);
        }
        root.requestApplyInsets();
    }

    private WindowInsets applyInsets(View view, WindowInsets insets) {
        if (fullscreen) {
            view.setPadding(0, 0, 0, 0);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
        } else {
            // Before Android 11 the decor view keeps the content between the bars (and adjustResize
            // above the keyboard) itself; whatever reaches the root is still to be avoided.
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        }
        // Applied here once: the WebView below must not react to the same insets again.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? WindowInsets.CONSUMED : insets;
    }

    /** TV: the page fills the screen, the system bars stay hidden (immersive sticky). */
    static void hideSystemUi(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
