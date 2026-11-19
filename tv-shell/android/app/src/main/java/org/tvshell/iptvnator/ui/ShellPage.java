package org.tvshell.iptvnator.ui;

import android.content.res.Configuration;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;
import org.tvshell.iptvnator.Prefs;
import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.backend.ShellSite;
import org.tvshell.iptvnator.server.TvShellServer;

import java.util.Collections;

/** The shell's WebView and what its page was loaded with: the server, the layout, the interface and the bridge. */
final class ShellPage {
    private final MainActivity activity;
    private final Prefs prefs;
    private final PageStatus status;
    private final LoopbackFilter loopback = new LoopbackFilter();
    private WebView webView;
    private TvShellServer server;
    private String bridgeOrigin;
    private ApiScript apiScript;
    private ColorSchemeScript colorScheme;
    private int loadedLayoutWidth;
    private int loadedScreenWidth;
    /** The interface setting the page was loaded with; a change in the settings reloads. */
    private String loadedInterfaceMode;
    /** Touch interface (phone, tablet): device-width page, no remote-control focus layer. */
    private boolean touchMode;
    /** After a process restart, the route the user was on. */
    String restoreUrl;

    ShellPage(MainActivity activity, Prefs prefs, PageStatus status) {
        this.activity = activity;
        this.prefs = prefs;
        this.status = status;
    }

    WebView webView() {
        return webView;
    }

    TvShellServer server() {
        return server;
    }

    String bridgeOrigin() {
        return bridgeOrigin;
    }

    LoopbackFilter loopback() {
        return loopback;
    }

    boolean touchMode() {
        return touchMode;
    }

    String layoutDescription() {
        return touchMode ? "touch" : String.valueOf(loadedLayoutWidth);
    }

    /**
     * Creates the WebView in code, not inflated: when the system WebView is missing, disabled or
     * being updated (Amazon WebView updates in the background), its constructor throws an
     * AndroidRuntimeException, which from the inflater would kill the app before any message.
     */
    boolean create(FrameLayout root) {
        try {
            webView = new WebView(activity);
            WebView.setWebContentsDebuggingEnabled(prefs.webDebug() || WebViewSetup.isDebugBuild(activity));
        } catch (RuntimeException e) {
            Log.e(ShellApp.TAG, "WebView unavailable", e);
            webView = null;
            showStartupError(activity.getString(R.string.webview_unavailable) + "\n" + e);
            return false;
        }
        WebViewSetup.configure(activity, webView);
        root.addView(webView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setWebViewClient(new ShellPageClient(activity, status));
        webView.setWebChromeClient(new ShellChromeClient(activity, status));
        return true;
    }

    /** An error before any load: the diagnostics show the port and the layout, and onResume() does not reload over it. */
    void showStartupError(String message) {
        server = ShellApp.currentServer();
        loadedLayoutWidth = prefs.layoutWidth();
        loadedInterfaceMode = prefs.interfaceMode();
        status.showError(message);
    }

    void load() {
        if (webView == null) {
            activity.recreate();
            return;
        }
        status.loading();
        activity.nativePlayback().documentGone();
        activity.setKeepScreenOn(true); // through the splash; the page then reports its playback state
        loadedLayoutWidth = prefs.layoutWidth();
        loadedScreenWidth = ShellDiagnostics.screenCssWidth(activity);
        loadedInterfaceMode = prefs.interfaceMode();
        touchMode = ShellMode.isTouch(activity, prefs);
        WebViewSetup.applyZoom(webView.getSettings(), touchMode);
        server = ((ShellApp) activity.getApplication()).ensureServer();
        if (server == null) {
            status.showError(activity.getString(R.string.server_error));
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
            || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            status.showError(activity.getString(R.string.webview_bridge_unavailable));
            return;
        }
        bindBridge();
        // Before loadUrl: the page's first /api request (the bootstrap probe) already carries the secret.
        if (apiScript == null) apiScript = new ApiScript(activity);
        if (!apiScript.bind(webView, server)) {
            status.showError(activity.getString(R.string.api_script_error));
            return;
        }
        // Touch mode: the page's System theme follows Android's dark theme (ColorSchemeScript).
        if (colorScheme == null) colorScheme = new ColorSchemeScript(activity);
        if (touchMode) {
            colorScheme.bind(webView, "http://127.0.0.1:" + server.port(), ColorSchemeScript.isDark(activity.getResources().getConfiguration()));
        } else {
            colorScheme.unbind();
        }
        if (ShellApp.onFallbackPort()) activity.toast(activity.getString(R.string.port_fallback, prefs.serverPort(), server.port()));
        server.setLayoutWidth(loadedLayoutWidth);
        server.setScreenWidth(loadedScreenWidth);
        server.setTouchMode(touchMode);
        activity.chrome().setTouch(touchMode);
        loopback.setShellPort(server.port());
        // After a process restart the route the user was on (the SPA fallback serves it); else the root.
        String url = restoreUrl != null && restoreUrl.startsWith(server.baseUrl()) ? restoreUrl : server.baseUrl();
        restoreUrl = null;
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
        activity.focusPage();
        status.armWatchdog(activity.resumed());
    }

    private void bindBridge() {
        String origin = "http://127.0.0.1:" + server.port();
        if (origin.equals(bridgeOrigin)) return;
        if (bridgeOrigin != null) WebViewCompat.removeWebMessageListener(webView, "TvShell");
        TvShellBridge bridge = new TvShellBridge(activity);
        WebViewCompat.addWebMessageListener(webView, "TvShell", Collections.singleton(origin),
            (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                if (view == webView && server != null && isMainFrame && origin.equals(bridgeOrigin)
                    && origin.equals(sourceOrigin.toString()) && message.getType() == WebMessageCompat.TYPE_STRING) {
                    bridge.receive(message.getData());
                }
            });
        bridgeOrigin = origin;
    }

    /** The settings changed the layout or the interface, or the saved port is free again: load anew. */
    boolean needsReload() {
        if (server == null) return false;
        if (prefs.layoutWidth() != loadedLayoutWidth || !prefs.interfaceMode().equals(loadedInterfaceMode)) return true;
        // The saved port is free again: back to the origin that holds the user's data.
        return ShellApp.onFallbackPort() && ((ShellApp) activity.getApplication()).ensureServer() != server;
    }

    /** Display changes the manifest keeps the Activity through: the pinned page scale must follow. */
    void onConfigurationChanged(Configuration newConfig) {
        if (touchMode && colorScheme != null) colorScheme.update(ColorSchemeScript.isDark(newConfig));
        // Touch mode serves width=device-width: there is no pinned scale to follow, and a reload would
        // stop playback on every rotation.
        if (touchMode) return;
        int width = ShellDiagnostics.screenCssWidth(activity);
        if (webView == null || server == null || status.showingError() || width == loadedScreenWidth) return;
        if (!status.ready) {
            restoreUrl = webView.getUrl();
            load();
            return;
        }
        // Keep the playback owner and pending result in the same document.
        loadedScreenWidth = width;
        server.setScreenWidth(width);
        activity.js("(function(){var viewport=document.querySelector('meta[name=viewport]');if(viewport)viewport.content="
            + JSONObject.quote(ShellSite.viewport(loadedLayoutWidth, width)) + ";})()");
    }

    /** The renderer died or the Activity ends: the WebView goes (destroy() must not run on an attached one). */
    void destroy(FrameLayout root, boolean blankFirst) {
        if (webView == null) return;
        if (blankFirst) webView.loadUrl("about:blank");
        root.removeView(webView);
        webView.destroy();
        webView = null; // every remaining use is null-guarded
    }
}
