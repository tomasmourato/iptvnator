package org.tvshell.iptvnator.ui;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.ShellApp;

/**
 * The page's start-up state: the splash, the error panel, the ready watchdog and the one retry of
 * a main-frame load the loopback server did not answer.
 */
final class PageStatus {
    /**
     * How long the web app may take to report readiness before the error panel explains the
     * situation; doubled once while the page is still being fetched (a cold start on a slow stick
     * right after an update parses the whole bundle again).
     */
    private static final long READY_TIMEOUT_MS = 30_000;
    /** A main-frame load error from the loopback server is retried once, after the server is restarted. */
    private static final long LOAD_RETRY_DELAY_MS = 1_200;

    private final MainActivity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final View splash;
    private final View errorPanel;
    private final TextView errorText;
    /** The web app reported itself ready. */
    boolean ready;
    /** The main document finished loading once. */
    boolean finished;
    private boolean watchdogExtended;
    private boolean loadRetried;
    private String lastConsoleError;
    /** Fires when the page never reported itself ready: a too-old WebView fails to parse the bundle silently. */
    private final Runnable readyWatchdog = new Runnable() {
        @Override
        public void run() {
            if (ready || showingError()) return;
            if (!finished && !watchdogExtended) {
                // Still fetching and parsing: slow, not broken. One more period before giving up.
                watchdogExtended = true;
                Log.i(ShellApp.TAG, "page still loading after " + READY_TIMEOUT_MS + " ms, waiting once more");
                handler.postDelayed(this, READY_TIMEOUT_MS);
                return;
            }
            showLoadTimeout();
        }
    };
    /** The one retry of a main-frame load the loopback server did not answer. */
    private final Runnable loadRetry = new Runnable() {
        @Override
        public void run() {
            if (!activity.isFinishing() && !activity.isDestroyed() && !ready) activity.load();
        }
    };

    PageStatus(MainActivity activity) {
        this.activity = activity;
        splash = activity.findViewById(R.id.splash);
        errorPanel = activity.findViewById(R.id.error_panel);
        errorText = activity.findViewById(R.id.error_text);
        activity.findViewById(R.id.error_retry).setOnClickListener(v -> activity.load());
        activity.findViewById(R.id.error_settings).setOnClickListener(v -> activity.openSettings());
    }

    View errorPanel() {
        return errorPanel;
    }

    boolean showingError() {
        return errorPanel.getVisibility() == View.VISIBLE;
    }

    /** A new load of the page starts: the splash, and a fresh ready budget. */
    void loading() {
        errorPanel.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        ready = false;
        finished = false;
        watchdogExtended = false;
        lastConsoleError = null;
        cancel();
    }

    void onReady() {
        ready = true;
        loadRetried = false;
        cancel();
        splash.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
    }

    /** (Re)starts the ready watchdog for a full period; while paused onResume() does it. */
    void armWatchdog(boolean resumed) {
        handler.removeCallbacks(readyWatchdog);
        if (!resumed) return; // startup time in the background does not count
        handler.postDelayed(readyWatchdog, READY_TIMEOUT_MS);
    }

    /** Startup time in the background does not count. */
    void pauseWatchdog() {
        handler.removeCallbacks(readyWatchdog);
    }

    void cancel() {
        handler.removeCallbacks(readyWatchdog);
        handler.removeCallbacks(loadRetry);
    }

    /** Schedules the one retry of an unanswered main-frame load; false once it was used. */
    boolean retryLoad() {
        if (loadRetried) return false;
        loadRetried = true;
        handler.removeCallbacks(loadRetry);
        handler.postDelayed(loadRetry, LOAD_RETRY_DELAY_MS);
        return true;
    }

    /** A redacted console error; a bundle this engine cannot parse shows the diagnostics now. */
    void consoleError(String redacted, boolean syntaxError) {
        lastConsoleError = redacted;
        if (!ready && syntaxError) {
            handler.removeCallbacks(readyWatchdog);
            showLoadTimeout();
        }
    }

    /** The page never reported itself ready (or cannot be parsed): the error panel with the diagnostics. */
    private void showLoadTimeout() {
        if (ready || showingError()) return;
        String message = activity.getString(R.string.load_timeout);
        if (lastConsoleError != null) message += "\n" + lastConsoleError;
        showError(message);
    }

    void showError(String message) {
        activity.setKeepScreenOn(false);
        splash.setVisibility(View.GONE);
        errorText.setText(message + "\n\n" + activity.diagnostics());
        errorPanel.setVisibility(View.VISIBLE);
        errorPanel.findViewById(R.id.error_retry).requestFocus();
    }
}
