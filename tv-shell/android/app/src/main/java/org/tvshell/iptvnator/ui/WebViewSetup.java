package org.tvshell.iptvnator.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** The WebView settings of the shell's page. */
final class WebViewSetup {
    private WebViewSetup() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    static void configure(Context context, WebView webView) {
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " IPTVnator/" + versionName(context));
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
    }

    /** TV: the pinned layout, no zoom. Touch: pinch-zoom on a device-width page. */
    static void applyZoom(WebSettings s, boolean touchMode) {
        s.setLoadWithOverviewMode(!touchMode);
        s.setSupportZoom(touchMode);
        s.setBuiltInZoomControls(touchMode);
        s.setDisplayZoomControls(false);
    }

    static boolean isDebugBuild(Context context) {
        return (context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private static String versionName(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (RuntimeException | PackageManager.NameNotFoundException e) {
            return "0";
        }
    }
}
