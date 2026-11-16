package org.tvshell.iptvnator.ui;

import android.content.pm.ActivityInfo;
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
        if (activity.chrome().touch()) enterTouchFullscreen(view);
    }

    void hide() {
        if (customView == null) return;
        activity.root().removeView(customView);
        customView = null;
        WebView webView = activity.webView();
        webView.setVisibility(View.VISIBLE);
        activity.focusPage();
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
        if (activity.chrome().touch()) {
            activity.chrome().setFullscreen(false);
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /**
     * Touch: fullscreen video hides the bars and turns to landscape like other video apps, unless
     * the video itself is portrait. A frame without a reachable video (a trailer) counts as landscape.
     */
    private void enterTouchFullscreen(final View view) {
        activity.chrome().setFullscreen(true);
        activity.webView().evaluateJavascript(
            "(function(){var e=document.fullscreenElement;var v=e&&(e.tagName==='VIDEO'?e:e.querySelector('video'));"
                + "return v&&v.videoWidth&&v.videoHeight<=v.videoWidth?'landscape':v&&v.videoWidth?'portrait':'landscape';})()",
            shape -> {
                if (customView != view) return; // left fullscreen meanwhile
                activity.setRequestedOrientation("\"portrait\"".equals(shape)
                    ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            });
    }
}
