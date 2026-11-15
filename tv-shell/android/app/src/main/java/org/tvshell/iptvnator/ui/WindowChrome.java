package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.view.View;

/** The system bars around the shell's window. */
final class WindowChrome {

    private WindowChrome() {
    }

    /** TV: the page fills the screen, the system bars stay hidden (immersive sticky). */
    static void hideSystemUi(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}
