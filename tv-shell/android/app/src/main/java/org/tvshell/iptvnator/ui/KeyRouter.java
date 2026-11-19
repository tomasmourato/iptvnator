package org.tvshell.iptvnator.ui;

import android.content.Context;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebView;

import org.tvshell.iptvnator.R;

/**
 * Remote-control keys that the page cannot see as plain key events (Back, Menu, media keys) are
 * forwarded to the TV layer (tv.js); held Back or Menu opens the shell settings.
 */
final class KeyRouter {

    private static final long LONG_PRESS_MS = 650;

    private final MainActivity activity;
    private long menuDownAt;
    private boolean menuLongPressFired;
    private long backDownAt;
    private boolean backLongPressFired;

    KeyRouter(MainActivity activity) {
        this.activity = activity;
    }

    boolean dispatch(KeyEvent event) {
        int code = event.getKeyCode();
        int action = event.getAction();
        View errorPanel = activity.errorPanel();
        View customView = activity.fullscreen().view();
        if (customView == null && errorPanel.getVisibility() == View.VISIBLE) {
            // The WebView underneath stays focusable: keys must land on the panel's buttons.
            View focus = activity.getCurrentFocus();
            if (focus == null || focus == activity.webView()) errorPanel.findViewById(R.id.error_retry).requestFocus();
            return activity.superDispatchKeyEvent(event);
        }
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
                // A held Back opens the shell settings: remotes without a Menu key (Google TV, HDMI-CEC).
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        backDownAt = SystemClock.uptimeMillis();
                        backLongPressFired = false;
                    } else if (!backLongPressFired && SystemClock.uptimeMillis() - backDownAt >= LONG_PRESS_MS) {
                        backLongPressFired = true;
                        activity.openSettings();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    if (!backLongPressFired && !event.isCanceled()) handleBack();
                    backLongPressFired = false;
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_TV_MEDIA_CONTEXT_MENU: // HDMI-CEC remotes
            case KeyEvent.KEYCODE_BUTTON_Y: // game controllers
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        menuDownAt = SystemClock.uptimeMillis();
                        menuLongPressFired = false;
                    } else if (!menuLongPressFired && SystemClock.uptimeMillis() - menuDownAt >= LONG_PRESS_MS) {
                        menuLongPressFired = true;
                        activity.openSettings();
                    }
                } else if (action == KeyEvent.ACTION_UP) {
                    if (!menuLongPressFired) activity.js("window.__tvShell && window.__tvShell.menu()");
                    menuLongPressFired = false;
                }
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) media("playpause");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) media("play");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) media("pause");
                return true;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) media("stop");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (action == KeyEvent.ACTION_DOWN) media("rewind");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (action == KeyEvent.ACTION_DOWN) media("fastforward");
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                // A held OK is one activation. The WebView turns every Android key repeat into a fresh
                // Enter keydown + keypress with KeyboardEvent.repeat === false, so the page cannot filter
                // repeats itself; drop them here like the media keys above. D-pad repeats stay (hold to scroll).
                if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() > 0) return true;
                if (customView != null) return forwardToPage(event, "Enter");
                return activity.superDispatchKeyEvent(event);
            case KeyEvent.KEYCODE_DPAD_UP:
                if (customView != null) return forwardToPage(event, "ArrowUp");
                return activity.superDispatchKeyEvent(event);
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (customView != null) return forwardToPage(event, "ArrowDown");
                return activity.superDispatchKeyEvent(event);
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (customView != null) return forwardToPage(event, "ArrowLeft");
                return activity.superDispatchKeyEvent(event);
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (customView != null) return forwardToPage(event, "ArrowRight");
                return activity.superDispatchKeyEvent(event);
            default:
                return activity.superDispatchKeyEvent(event);
        }
    }

    private void handleBack() {
        if (activity.fullscreen().view() != null) {
            // Leaving fullscreen video is handled by the page (document.exitFullscreen).
            activity.js("window.__tvShell ? window.__tvShell.back() : (document.fullscreenElement && document.exitFullscreen(), true)");
            return;
        }
        WebView webView = activity.webView();
        if (!activity.pageReady() || webView == null || webView.getVisibility() != View.VISIBLE) {
            activity.finishAffinity();
            return;
        }
        activity.js("window.__tvShell ? window.__tvShell.back() : (history.length > 1 ? (history.back(), true) : false)");
    }

    /**
     * In fullscreen the WebView is hidden and Chromium's fullscreen view holds focus, so D-pad and
     * OK may not reach the page. Let the page route those keys to its controls and keep native
     * delivery for keys it declines, such as input inside a trailer frame or text caret movement.
     * The key names are constants from the switch above. Ctrl, Alt and Meta combinations stay on
     * the native path: the replay carries no modifier flags, so the page guards that skip modified
     * arrows would see a plain arrow and seek or move focus instead of moving the caret.
     */
    private boolean forwardToPage(KeyEvent event, String key) {
        if (event.isCtrlPressed() || event.isAltPressed() || event.isMetaPressed()) {
            return activity.superDispatchKeyEvent(event);
        }
        int action = event.getAction();
        WebView webView = activity.webView();
        if (webView == null || (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP)) {
            return activity.superDispatchKeyEvent(event);
        }
        final View fullscreenView = activity.fullscreen().view();
        final KeyEvent nativeKey = new KeyEvent(event);
        String type = action == KeyEvent.ACTION_DOWN ? "keydown" : "keyup";
        webView.evaluateJavascript(
            "window.__tvShell && window.__tvShell.key && window.__tvShell.key('" + type + "','" + key + "')",
            handled -> {
                if (!"true".equals(handled) && activity.fullscreen().view() == fullscreenView) {
                    fullscreenView.dispatchKeyEvent(nativeKey);
                }
            });
        return true;
    }

    void showKeyboard() {
        WebView webView = activity.webView();
        if (webView == null || activity.isFinishing()) return;
        // requestFocus(FOCUS_DOWN) on a WebView that already has focus moves the page focus to its
        // first element, away from the field that asked for the keyboard.
        View customView = activity.fullscreen().view();
        View inputView = customView != null ? customView : webView;
        if (!inputView.hasFocus()) inputView.requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        // The field may have become editable after the WebView last described it to the keyboard
        // (tv.js holds a D-pad-focused field read-only): describe it again before showing.
        imm.restartInput(inputView);
        imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
    }

    private void media(String action) {
        activity.js("window.__tvShell && window.__tvShell.media('" + action + "')");
    }
}
