package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.tvshell.iptvnator.Prefs;
import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.server.TvShellServer;

/**
 * Full-screen WebView showing the bundled IPTVnator PWA from the embedded
 * server. Remote-control keys that the page cannot see as plain key events
 * (Back, Menu, media keys) are forwarded to the TV layer (tv.js).
 */
public class MainActivity extends Activity {

    private static final String STATE_URL = "url";
    /** Launcher shortcut (res/xml/shortcuts.xml): open the shell settings over the app. */
    static final String ACTION_OPEN_SETTINGS = "org.tvshell.iptvnator.action.OPEN_SETTINGS";

    private final WebFileChooser fileChooser = new WebFileChooser(this);
    private final KeyRouter keys = new KeyRouter(this);
    private final FullscreenController fullscreen = new FullscreenController(this);
    private ExternalOffer externalOffer;
    private NativePlaybackHost nativePlayback;
    private PageStatus status;
    private ShellPage page;
    private WindowChrome chrome;
    private FrameLayout root;
    private Prefs prefs;
    private boolean resumed;
    private boolean webViewUnavailable;
    /** The route at the last pause, for the relaunch after the system reclaimed the hidden renderer. */
    private String lastRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        externalOffer = new ExternalOffer(this, prefs);
        nativePlayback = new NativePlaybackHost(this, prefs);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        chrome = new WindowChrome(this, root);
        status = new PageStatus(this);
        page = new ShellPage(this, prefs, status);
        if (savedInstanceState != null) {
            page.restoreUrl = savedInstanceState.getString(STATE_URL);
            nativePlayback.restore(savedInstanceState);
        }
        // Not on a recreate, and not when Recents replays the shortcut's intent: it was handled then.
        if (savedInstanceState == null && (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            handleShortcut(getIntent());
        }
        if (!page.create(root)) {
            webViewUnavailable = true;
        } else if (ShellPageClient.rendererLooping()) {
            page.showStartupError(getString(R.string.renderer_gone_loop));
        } else {
            load();
        }
    }

    void load() {
        page.load();
    }

    /** The screen stays on only while the page plays something (and during the start-up splash). */
    void setKeepScreenOn(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    String diagnostics() {
        return ShellDiagnostics.describe(this, prefs, page.server(), page.layoutDescription());
    }

    // ------------------------------------------------------------- keys
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return keys.dispatch(event);
    }

    /** The Activity's own key dispatch, for the keys {@link KeyRouter} leaves to the system. */
    boolean superDispatchKeyEvent(KeyEvent event) {
        return super.dispatchKeyEvent(event);
    }

    // ----------------------------------------------------- helper access
    WebView webView() {
        return page.webView();
    }

    FrameLayout root() {
        return root;
    }

    View errorPanel() {
        return status.errorPanel();
    }

    WindowChrome chrome() {
        return chrome;
    }

    FullscreenController fullscreen() {
        return fullscreen;
    }

    KeyRouter keys() {
        return keys;
    }

    WebFileChooser fileChooser() {
        return fileChooser;
    }

    NativePlaybackHost nativePlayback() {
        return nativePlayback;
    }

    TvShellServer server() {
        return page.server();
    }

    LoopbackFilter loopback() {
        return page.loopback();
    }

    String bridgeOrigin() {
        return page.bridgeOrigin();
    }

    boolean pageReady() {
        return status.ready;
    }

    boolean resumed() {
        return resumed;
    }

    void js(String script) {
        WebView webView = page.webView();
        if (webView != null) webView.evaluateJavascript(script, null);
    }

    void setPageForeground(boolean foreground) {
        js("window.__tvShell && typeof window.__tvShell.setForeground === 'function' && window.__tvShell.setForeground(" + foreground + ")");
    }

    // --------------------------------------------------------- callbacks
    void onWebAppReady() {
        status.onReady();
        nativePlayback.updateAvailability();
        nativePlayback.deliverCheckpoints();
        setKeepScreenOn(false); // from here the page says when something plays
        focusPage();
    }

    /** The renderer died: drop the WebView, and in front start over with a new Activity. */
    void onRendererGone() {
        fileChooser.cancel();
        if (resumed) lastRoute = null; // a death in front reloads the start view
        status.cancel();
        page.destroy(root, false);
        nativePlayback.documentGone();
        if (resumed) {
            toast(getString(R.string.renderer_restarted));
            recreate(); // new Activity + WebView; ShellApp keeps the server
        }
    }

    /**
     * Gives the page the input focus. TV: FOCUS_DOWN, which lands on the page's first element for
     * tv.js to take from there. Touch: window focus only, never a page element; on a phone that jump
     * scrolled lists away from the top and left a focus outline where nobody had tapped.
     */
    void focusPage() {
        WebView webView = page.webView();
        if (webView == null) return;
        if (!page.touchMode()) webView.requestFocus(View.FOCUS_DOWN);
        else if (!webView.hasFocus()) webView.requestFocus();
    }

    ExternalOffer externalOffer() {
        return externalOffer;
    }

    void showExitHint() {
        toast(getString(R.string.press_back_again));
    }

    void toast(String message) {
        if (message == null || message.isEmpty()) return; // Toast.show() throws on an empty text (API 30+)
        // The application context: a toast may outlive this Activity (renderer restart, exit).
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    /** singleTask: the shortcut reaches a running shell here, with the page kept as it is. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleShortcut(intent);
    }

    private void handleShortcut(Intent intent) {
        if (intent != null && ACTION_OPEN_SETTINGS.equals(intent.getAction())) openSettings();
    }

    // ---------------------------------------------------------- lifecycle
    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        chrome.refresh();
        WebView webView = page.webView();
        if (webView == null) {
            if (!webViewUnavailable) recreate(); // the renderer died while we were in the background
            return;
        }
        webView.onResume();
        setPageForeground(true);
        nativePlayback.updateAvailability();
        nativePlayback.onResume();
        if (!status.showingError()) focusPage();
        // Give a foreground startup a fresh ready budget.
        if (!status.ready && !status.showingError()) status.armWatchdog(true);
        if (SettingsActivity.dataCleared) {
            SettingsActivity.dataCleared = false;
            webView.clearCache(true);
            load();
        } else if (page.needsReload()) {
            load();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        status.pauseWatchdog();
        WebView webView = page.webView();
        if (webView != null) {
            lastRoute = status.ready ? webView.getUrl() : null;
            // Keep media events runnable: pauseTimers would also freeze the late-play guard.
            js("(function(){if(window.__tvShell && typeof window.__tvShell.setForeground === 'function'){window.__tvShell.setForeground(false);}else{document.querySelectorAll('video,audio').forEach(function(media){media.pause();});}})()");
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // The route the user was on, for a restart after the system killed the process in the background,
        // or for the relaunch after it reclaimed the hidden renderer (webView is null by then).
        WebView webView = page.webView();
        String url = webView != null ? (status.ready ? webView.getUrl() : null) : lastRoute;
        if (url != null) outState.putString(STATE_URL, url);
        nativePlayback.save(outState);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        chrome.refresh();
        page.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NativePlaybackHost.REQUEST_CODE) {
            nativePlayback.onResult(resultCode, data);
        } else if (!fileChooser.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        fileChooser.cancel();
        if (status != null) status.cancel();
        if (page != null) page.destroy(root, true);
        super.onDestroy();
    }
}
