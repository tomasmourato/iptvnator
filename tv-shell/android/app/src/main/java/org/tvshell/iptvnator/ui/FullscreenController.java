package org.tvshell.iptvnator.ui;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;

/** Chromium's fullscreen view (a fullscreen video) shown over the hidden WebView. */
final class FullscreenController {

    private final MainActivity activity;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    FullscreenController(MainActivity activity) {
        this.activity = activity;
    }

    /** The fullscreen view, or null outside fullscreen. */
    View view() {
        return customView;
    }

    void show(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        activity.root().addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        activity.webView().setVisibility(View.GONE);
        view.setFocusable(true);
        view.requestFocus();
    }

    void hide() {
        if (customView == null) return;
        activity.root().removeView(customView);
        customView = null;
        WebView webView = activity.webView();
        webView.setVisibility(View.VISIBLE);
        webView.requestFocus(View.FOCUS_DOWN);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }
}
