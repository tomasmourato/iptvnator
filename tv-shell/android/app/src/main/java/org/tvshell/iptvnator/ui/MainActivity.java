package org.tvshell.iptvnator.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;
import org.tvshell.iptvnator.Prefs;
import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.backend.DiagnosticRedactor;
import org.tvshell.iptvnator.backend.ShellSite;
import org.tvshell.iptvnator.server.TvShellServer;

import java.util.Collections;
import java.util.List;

/**
 * Full-screen WebView showing the bundled IPTVnator PWA from the embedded
 * server. Remote-control keys that the page cannot see as plain key events
 * (Back, Menu, media keys) are forwarded to the TV layer (tv.js).
 */
public class MainActivity extends Activity {

    private static final int NATIVE_PLAYBACK_REQUEST = 7103;
    /**
     * How long the web app may take to report readiness before the error panel explains the
     * situation; doubled once while the page is still being fetched (a cold start on a slow stick
     * right after an update parses the whole bundle again).
     */
    private static final long READY_TIMEOUT_MS = 30_000;
    /** A main-frame load error from the loopback server is retried once, after the server is restarted. */
    private static final long LOAD_RETRY_DELAY_MS = 1_200;
    /** Renderer deaths are counted across Activity recreations; a burst means the page itself is the problem. */
    private static final long RENDERER_DEATH_WINDOW_MS = 60_000;
    private static final int RENDERER_DEATH_LIMIT = 3;
    private static final String STATE_URL = "url";
    private static final String STATE_PLAYBACK_ID = "nativePlaybackId";
    private static final String STATE_PLAYBACK_CONTENT = "nativePlaybackContent";
    private static final String STATE_PLAYBACK_CHECKPOINT = "nativePlaybackCheckpoint";
    /** Launcher shortcut (res/xml/shortcuts.xml): open the shell settings over the app. */
    static final String ACTION_OPEN_SETTINGS = "org.tvshell.iptvnator.action.OPEN_SETTINGS";
    private static int rendererDeaths;
    private static long lastRendererDeathAt;

    private WebView webView;
    private String bridgeOrigin;
    private String nativePlaybackId;
    private String lastNativePlaybackRequestId;
    private Bundle nativePlaybackContent;
    private NativePlaybackCheckpoints nativePlaybackCheckpoints;
    private boolean nativePlaybackDocumentCurrent;
    private Runnable pendingNativePlaybackResult;
    private final WebFileChooser fileChooser = new WebFileChooser(this);
    private final KeyRouter keys = new KeyRouter(this);
    private final FullscreenController fullscreen = new FullscreenController(this);
    private ExternalOffer externalOffer;
    private WindowChrome chrome;
    private FrameLayout root;
    private View splash;
    private View errorPanel;
    private TextView errorText;
    private Prefs prefs;
    private TvShellServer server;
    private final LoopbackFilter loopback = new LoopbackFilter();
    private ApiScript apiScript;
    private ColorSchemeScript colorScheme;
    private boolean resumed;
    private boolean pageReady;
    private boolean pageFinished;
    private boolean watchdogExtended;
    private boolean loadRetried;
    private boolean webViewUnavailable;
    private int loadedLayoutWidth;
    private int loadedScreenWidth;
    /** The interface setting the page was loaded with; a change in the settings reloads. */
    private String loadedInterfaceMode;
    /** Touch interface (phone, tablet): device-width page, no remote-control focus layer. */
    private boolean touchMode;
    private String restoreUrl;
    /** The route at the last pause, for the relaunch after the system reclaimed the hidden renderer. */
    private String lastRoute;
    private String lastConsoleError;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Fires when the page never reported itself ready: a too-old WebView fails to parse the bundle silently. */
    private final Runnable readyWatchdog = new Runnable() {
        @Override
        public void run() {
            if (pageReady || errorPanel.getVisibility() == View.VISIBLE) return;
            if (!pageFinished && !watchdogExtended) {
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
            if (!isFinishing() && !isDestroyed() && !pageReady) load();
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        externalOffer = new ExternalOffer(this, prefs);
        nativePlaybackCheckpoints = new NativePlaybackCheckpoints(this);
        if (savedInstanceState != null) {
            restoreUrl = savedInstanceState.getString(STATE_URL);
            nativePlaybackId = savedInstanceState.getString(STATE_PLAYBACK_ID);
            nativePlaybackContent = savedInstanceState.getBundle(STATE_PLAYBACK_CONTENT);
            Bundle checkpoints = savedInstanceState.getBundle(STATE_PLAYBACK_CHECKPOINT);
            if (checkpoints != null) {
                for (String id : checkpoints.keySet()) {
                    Bundle checkpoint = checkpoints.getBundle(id);
                    if (checkpoint != null) nativePlaybackCheckpoints.save(checkpoint);
                }
            }
        }
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        chrome = new WindowChrome(this, root);
        splash = findViewById(R.id.splash);
        errorPanel = findViewById(R.id.error_panel);
        errorText = findViewById(R.id.error_text);
        Button retry = findViewById(R.id.error_retry);
        Button settings = findViewById(R.id.error_settings);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                load();
            }
        });
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSettings();
            }
        });
        // Not on a recreate, and not when Recents replays the shortcut's intent: it was handled then.
        if (savedInstanceState == null && (getIntent().getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) {
            handleShortcut(getIntent());
        }

        // The WebView is created in code, not inflated: when the system WebView is missing, disabled
        // or being updated (Amazon WebView updates in the background), its constructor throws an
        // AndroidRuntimeException, which from the inflater would kill the app before any message.
        try {
            webView = new WebView(this);
            WebView.setWebContentsDebuggingEnabled(prefs.webDebug() || isDebugBuild());
        } catch (RuntimeException e) {
            Log.e(ShellApp.TAG, "WebView unavailable", e);
            webView = null;
            webViewUnavailable = true;
            server = ShellApp.currentServer();
            loadedLayoutWidth = prefs.layoutWidth();
            showError(getString(R.string.webview_unavailable) + "\n" + e);
            return;
        }
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(webView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
        s.setUserAgentString(s.getUserAgentString() + " IPTVnator/" + versionName());
        webView.setBackgroundColor(Color.BLACK);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (server != null && uri.toString().startsWith(server.baseUrl())) return false;
                // Outside links (YouTube trailers, provider sites): hand http(s) to the system, the
                // same allowlist as the Electron shell. Any other scheme stays inside the shell.
                String scheme = uri.getScheme();
                if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                    Log.w(ShellApp.TAG, "blocked navigation to scheme " + scheme);
                    return true;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (RuntimeException e) {
                    toast(getString(R.string.cannot_open_link));
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loopback.intercept(request);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (!request.isForMainFrame()) return;
                int code = error.getErrorCode();
                boolean serverUnreachable = code == ERROR_CONNECT || code == ERROR_HOST_LOOKUP || code == ERROR_TIMEOUT
                    || code == ERROR_IO || code == ERROR_FAILED_SSL_HANDSHAKE;
                if (serverUnreachable && !loadRetried) {
                    // The embedded server was not answering (its thread died while the app sat in the
                    // background, or the process was restored before it listened): load() restarts it.
                    loadRetried = true;
                    Log.w(ShellApp.TAG, "main frame load failed (" + code + "), restarting the server and retrying once");
                    handler.removeCallbacks(loadRetry);
                    handler.postDelayed(loadRetry, LOAD_RETRY_DELAY_MS);
                    return;
                }
                showError(getString(R.string.load_error, String.valueOf(error.getDescription()), String.valueOf(code)));
            }

            /** The server answered the page itself with an error (the bundle missing from the APK, out of memory). */
            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (!request.isForMainFrame()) return;
                showError(getString(R.string.load_error, String.valueOf(errorResponse.getReasonPhrase()), String.valueOf(errorResponse.getStatusCode())));
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                pendingNativePlaybackResult = null;
                if (nativePlaybackId != null || !nativePlaybackCheckpoints.isEmpty()) {
                    nativePlaybackDocumentCurrent = false;
                    pageReady = false;
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                boolean first = !pageFinished;
                pageFinished = true;
                if (!resumed) js("window.__tvShell && typeof window.__tvShell.setForeground === 'function' && window.__tvShell.setForeground(false)");
                if (first && !pageReady && errorPanel.getVisibility() != View.VISIBLE) {
                    // The bundle has run: Angular's bootstrap (IndexedDB, playlists) gets a period of its own.
                    armWatchdog();
                }
                // Never take the focus away from the error panel (an error page commits after onReceivedError).
                if (errorPanel.getVisibility() != View.VISIBLE) focusPage();
            }

            /** The sandboxed renderer died (OOM abort or system kill). Unhandled, Chromium crashes or kills the app. */
            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                fileChooser.cancel();
                boolean crashed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail.didCrash();
                long now = SystemClock.uptimeMillis();
                if (crashed || resumed) {
                    // The system reclaiming the hidden renderer while another app (VLC, the launcher) is in
                    // front is routine on a 1 GB device, not a sign that the page is broken: not counted.
                    rendererDeaths = now - lastRendererDeathAt < RENDERER_DEATH_WINDOW_MS ? rendererDeaths + 1 : 1;
                    lastRendererDeathAt = now;
                }
                Log.w(ShellApp.TAG, "web renderer gone (crashed=" + crashed + ", resumed=" + resumed + ", count=" + rendererDeaths + ")");
                if (resumed) lastRoute = null; // a death in front reloads the start view
                handler.removeCallbacks(readyWatchdog);
                handler.removeCallbacks(loadRetry);
                root.removeView(view);
                view.destroy();
                webView = null; // every remaining use is null-guarded
                nativePlaybackDocumentCurrent = false;
                if (resumed) {
                    toast(getString(R.string.renderer_restarted));
                    recreate(); // new Activity + WebView; ShellApp keeps the server
                }
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return fileChooser.show(callback, params);
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    lastConsoleError = DiagnosticRedactor.redact(message.message() + " (" + message.sourceId() + ":" + message.lineNumber() + ")");
                    Log.w(ShellApp.TAG, "[console] " + lastConsoleError);
                    // A bundle this engine cannot parse: show the diagnostics now rather than after the timeout.
                    if (!pageReady && message.message().startsWith("Uncaught SyntaxError")) {
                        handler.removeCallbacks(readyWatchdog);
                        showLoadTimeout();
                    }
                }
                // Handled here: do not delegate the original, unsanitized message to WebView.
                return true;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                fullscreen.show(view, callback);
            }

            @Override
            public void onHideCustomView() {
                fullscreen.hide();
            }
        });

        if (rendererDeaths >= RENDERER_DEATH_LIMIT && SystemClock.uptimeMillis() - lastRendererDeathAt < RENDERER_DEATH_WINDOW_MS) {
            rendererDeaths = 0;
            server = ShellApp.currentServer(); // so the diagnostics show the port
            loadedLayoutWidth = prefs.layoutWidth(); // and the layout; also keeps onResume() from reloading over the panel
            loadedInterfaceMode = prefs.interfaceMode();
            showError(getString(R.string.renderer_gone_loop));
        } else {
            load();
        }
    }

    /** TV: the pinned layout, no zoom. Touch: pinch-zoom on a device-width page. */
    private void applyZoom(WebSettings s) {
        s.setLoadWithOverviewMode(!touchMode);
        s.setSupportZoom(touchMode);
        s.setBuiltInZoomControls(touchMode);
        s.setDisplayZoomControls(false);
    }

    private boolean isDebugBuild() {
        return (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (RuntimeException | android.content.pm.PackageManager.NameNotFoundException e) {
            return "0";
        }
    }

    private void load() {
        if (webView == null) {
            recreate();
            return;
        }
        errorPanel.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        pageReady = false;
        nativePlaybackDocumentCurrent = false;
        pageFinished = false;
        watchdogExtended = false;
        lastConsoleError = null;
        handler.removeCallbacks(readyWatchdog);
        handler.removeCallbacks(loadRetry);
        setKeepScreenOn(true); // through the splash; the page then reports its playback state
        loadedLayoutWidth = prefs.layoutWidth();
        loadedScreenWidth = ShellDiagnostics.screenCssWidth(this);
        loadedInterfaceMode = prefs.interfaceMode();
        touchMode = ShellMode.isTouch(this, prefs);
        applyZoom(webView.getSettings());
        server = ((ShellApp) getApplication()).ensureServer();
        if (server == null) {
            showError(getString(R.string.server_error));
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
            || !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            showError(getString(R.string.webview_bridge_unavailable));
            return;
        }
        bindBridge();
        // Before loadUrl: the page's first /api request (the bootstrap probe) already carries the secret.
        if (apiScript == null) apiScript = new ApiScript(this);
        if (!apiScript.bind(webView, server)) {
            showError(getString(R.string.api_script_error));
            return;
        }
        // Touch mode: the page's System theme follows Android's dark theme (ColorSchemeScript).
        if (colorScheme == null) colorScheme = new ColorSchemeScript(this);
        if (touchMode) {
            colorScheme.bind(webView, "http://127.0.0.1:" + server.port(), ColorSchemeScript.isDark(getResources().getConfiguration()));
        } else {
            colorScheme.unbind();
        }
        if (ShellApp.onFallbackPort()) toast(getString(R.string.port_fallback, prefs.serverPort(), server.port()));
        server.setLayoutWidth(loadedLayoutWidth);
        server.setScreenWidth(loadedScreenWidth);
        server.setTouchMode(touchMode);
        chrome.setTouch(touchMode);
        loopback.setShellPort(server.port());
        // After a process restart the route the user was on (the SPA fallback serves it); else the root.
        String url = restoreUrl != null && restoreUrl.startsWith(server.baseUrl()) ? restoreUrl : server.baseUrl();
        restoreUrl = null;
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
        focusPage();
        armWatchdog();
    }

    private void bindBridge() {
        String origin = "http://127.0.0.1:" + server.port();
        if (origin.equals(bridgeOrigin)) return;
        if (bridgeOrigin != null) WebViewCompat.removeWebMessageListener(webView, "TvShell");
        TvShellBridge bridge = new TvShellBridge(this);
        WebViewCompat.addWebMessageListener(webView, "TvShell", Collections.singleton(origin),
            (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                if (view == webView && server != null && isMainFrame && origin.equals(bridgeOrigin)
                    && origin.equals(sourceOrigin.toString()) && message.getType() == WebMessageCompat.TYPE_STRING) {
                    bridge.receive(message.getData());
                }
            });
        bridgeOrigin = origin;
    }

    /** (Re)starts the ready watchdog for a full period; while paused onResume() does it. */
    private void armWatchdog() {
        handler.removeCallbacks(readyWatchdog);
        if (!resumed) return; // startup time in the background does not count
        handler.postDelayed(readyWatchdog, READY_TIMEOUT_MS);
    }

    /** The screen stays on only while the page plays something (and during the start-up splash). */
    void setKeepScreenOn(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    /** The page never reported itself ready (or cannot be parsed): the error panel with the diagnostics. */
    private void showLoadTimeout() {
        if (pageReady || errorPanel.getVisibility() == View.VISIBLE) return;
        String message = getString(R.string.load_timeout);
        if (lastConsoleError != null) message += "\n" + lastConsoleError;
        showError(message);
    }

    private void showError(String message) {
        setKeepScreenOn(false);
        splash.setVisibility(View.GONE);
        errorText.setText(message + "\n\n" + diagnostics());
        errorPanel.setVisibility(View.VISIBLE);
        errorPanel.findViewById(R.id.error_retry).requestFocus();
    }

    String diagnostics() {
        return ShellDiagnostics.describe(this, prefs, server, touchMode ? "touch" : String.valueOf(loadedLayoutWidth));
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
        return webView;
    }

    FrameLayout root() {
        return root;
    }

    View errorPanel() {
        return errorPanel;
    }

    WindowChrome chrome() {
        return chrome;
    }

    FullscreenController fullscreen() {
        return fullscreen;
    }

    boolean pageReady() {
        return pageReady;
    }

    void js(String script) {
        if (webView == null) return;
        webView.evaluateJavascript(script, null);
    }

    // --------------------------------------------------------- callbacks
    void onWebAppReady() {
        pageReady = true;
        updateNativePlaybackAvailability();
        deliverNativePlaybackCheckpoint();
        loadRetried = false;
        setKeepScreenOn(false); // from here the page says when something plays
        handler.removeCallbacks(readyWatchdog);
        handler.removeCallbacks(loadRetry);
        splash.setVisibility(View.GONE);
        errorPanel.setVisibility(View.GONE);
        focusPage();
    }

    /**
     * Gives the page the input focus. TV: FOCUS_DOWN, which lands on the page's first element for
     * tv.js to take from there. Touch: window focus only, never a page element; on a phone that jump
     * scrolled lists away from the top and left a focus outline where nobody had tapped.
     */
    void focusPage() {
        if (webView == null) return;
        if (!touchMode) webView.requestFocus(View.FOCUS_DOWN);
        else if (!webView.hasFocus()) webView.requestFocus();
    }

    private void updateNativePlaybackAvailability() {
        if (!pageReady) return;
        js("window.iptvnatorAndroidPlaybackEnabled=" + prefs.nativePlayback()
            + ";window.dispatchEvent(new Event('iptvnator:android-playback-availability'))");
    }

    void playNative(String id, Object payload) {
        lastNativePlaybackRequestId = id;
        Intent intent = prefs.nativePlayback() && resumed && pageReady && nativePlaybackId == null
            ? NativePlaybackRequest.toIntent(this, payload) : null;
        if (intent == null) {
            replyToNativePlayback(id, null);
            deliverNativePlaybackCheckpoint();
            return;
        }
        nativePlaybackId = id;
        nativePlaybackContent = NativePlaybackRequest.checkpointContent(payload, bridgeOrigin);
        nativePlaybackDocumentCurrent = true;
        externalOffer.dismiss();
        fullscreen.hide();
        startActivityForResult(intent.putExtra("requestId", id)
            .putExtra("checkpointContent", nativePlaybackContent), NATIVE_PLAYBACK_REQUEST);
    }

    private void replyToNativePlayback(String id, Intent result) {
        String data = result == null ? "null" : "{positionMs:" + result.getLongExtra("position", 0)
            + ",durationMs:" + result.getLongExtra("duration", 0)
            + ",playedMs:" + result.getLongExtra("played", 0)
            + ",savedAt:" + result.getLongExtra("savedAt", 0)
            + ",started:" + result.getBooleanExtra("started", false)
            + ",ended:" + result.getBooleanExtra("ended", false)
            + ",failed:" + result.getBooleanExtra("failed", false) + "}";
        js("window.dispatchEvent(new CustomEvent('iptvnator:android-playback-result',{detail:{id:"
            + JSONObject.quote(id) + ",result:" + data + "}}))");
    }

    private void deliverNativePlaybackResult() {
        if (!resumed || pendingNativePlaybackResult == null) return;
        Runnable result = pendingNativePlaybackResult;
        pendingNativePlaybackResult = null;
        result.run();
    }

    void stopNative(String id) {
        if (!id.equals(nativePlaybackId)) return;
        nativePlaybackContent = null;
        NativePlayerActivity.revokeCheckpoint(id);
        finishActivity(NATIVE_PLAYBACK_REQUEST);
    }

    private void deliverNativePlaybackCheckpoint() {
        if (!pageReady || webView == null || SettingsActivity.dataCleared) return;
        // A fallback port uses a different WebView data store.
        List<JSONObject> checkpoints = nativePlaybackCheckpoints.forOrigin(bridgeOrigin);
        for (JSONObject checkpoint : checkpoints) {
            js("window.dispatchEvent(new CustomEvent('iptvnator:android-playback-checkpoint',{detail:"
                + "JSON.parse(" + JSONObject.quote(checkpoint.toString()) + ")}))");
        }
        if (checkpoints.isEmpty()) publishNativePlaybackCheckpointIdle();
    }

    void acknowledgeNativePlaybackCheckpoint(String id) {
        nativePlaybackCheckpoints.acknowledge(bridgeOrigin, id);
        if (nativePlaybackCheckpoints.forOrigin(bridgeOrigin).isEmpty()) publishNativePlaybackCheckpointIdle();
    }

    private void publishNativePlaybackCheckpointIdle() {
        if (nativePlaybackId != null || !pageReady || webView == null || SettingsActivity.dataCleared) return;
        String lastRequest = lastNativePlaybackRequestId == null ? "null" : JSONObject.quote(lastNativePlaybackRequestId);
        js("window.dispatchEvent(new CustomEvent('iptvnator:android-playback-checkpoints-idle',{detail:"
            + lastRequest + "}))");
    }

    void openExternal(String url, String title) {
        if (url == null || url.trim().isEmpty()) {
            toast(getString(R.string.no_stream_to_open));
            return;
        }
        ExternalPlayer.open(this, url.trim(), title, prefs.externalPlayerPackage());
    }

    /** Offers an external player for a stream; see {@link ExternalOffer}. */
    void offerExternal(String url, String title, boolean becauseOfError) {
        externalOffer.offer(url, title, becauseOfError);
    }

    void showExitHint() {
        toast(getString(R.string.press_back_again));
    }

    void toast(String message) {
        if (message == null || message.isEmpty()) return; // Toast.show() throws on an empty text (API 30+)
        // The application context: a toast may outlive this Activity (renderer restart, exit).
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    void showKeyboard() {
        if (webView == null || isFinishing()) return;
        // requestFocus(FOCUS_DOWN) on a WebView that already has focus moves the page focus to its
        // first element, away from the field that asked for the keyboard.
        View customView = fullscreen.view();
        View inputView = customView != null ? customView : webView;
        if (!inputView.hasFocus()) inputView.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm == null) return;
        // The field may have become editable after the WebView last described it to the keyboard
        // (tv.js holds a D-pad-focused field read-only): describe it again before showing.
        imm.restartInput(inputView);
        imm.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
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
        if (webView == null) {
            if (!webViewUnavailable) recreate(); // the renderer died while we were in the background
            return;
        }
        webView.onResume();
        js("window.__tvShell && typeof window.__tvShell.setForeground === 'function' && window.__tvShell.setForeground(true)");
        updateNativePlaybackAvailability();
        deliverNativePlaybackResult();
        deliverNativePlaybackCheckpoint();
        if (errorPanel.getVisibility() != View.VISIBLE) focusPage();
        // Give a foreground startup a fresh ready budget.
        if (!pageReady && errorPanel.getVisibility() != View.VISIBLE) armWatchdog();
        if (SettingsActivity.dataCleared) {
            SettingsActivity.dataCleared = false;
            if (webView != null) webView.clearCache(true);
            load();
        } else if (server != null && (prefs.layoutWidth() != loadedLayoutWidth || !prefs.interfaceMode().equals(loadedInterfaceMode))) {
            load();
        } else if (server != null && ShellApp.onFallbackPort() && ((ShellApp) getApplication()).ensureServer() != server) {
            load(); // the saved port is free again: back to the origin that holds the user's data
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(readyWatchdog); // startup time in the background does not count
        if (webView != null) {
            lastRoute = pageReady ? webView.getUrl() : null;
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
        String url = webView != null ? (pageReady ? webView.getUrl() : null) : lastRoute;
        if (url != null) outState.putString(STATE_URL, url);
        outState.putString(STATE_PLAYBACK_ID, nativePlaybackId);
        outState.putBundle(STATE_PLAYBACK_CONTENT, nativePlaybackContent);
    }

    /** Display changes the manifest keeps the Activity through: the pinned page scale must follow. */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        chrome.refresh();
        if (touchMode && colorScheme != null) colorScheme.update(ColorSchemeScript.isDark(newConfig));
        // Touch mode serves width=device-width: there is no pinned scale to follow, and a reload would
        // stop playback on every rotation.
        if (touchMode) return;
        int width = ShellDiagnostics.screenCssWidth(this);
        if (webView == null || server == null || errorPanel.getVisibility() == View.VISIBLE || width == loadedScreenWidth) return;
        if (!pageReady) {
            restoreUrl = webView.getUrl();
            load();
            return;
        }
        // Keep the playback owner and pending result in the same document.
        loadedScreenWidth = width;
        server.setScreenWidth(width);
        js("(function(){var viewport=document.querySelector('meta[name=viewport]');if(viewport)viewport.content="
            + JSONObject.quote(ShellSite.viewport(loadedLayoutWidth, width)) + ";})()");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == NATIVE_PLAYBACK_REQUEST) {
            String id = nativePlaybackId;
            Bundle content = nativePlaybackContent;
            nativePlaybackId = null;
            nativePlaybackContent = null;
            Intent fallback = id == null ? null : NativePlayerActivity.takeCheckpoint(id);
            Intent result = resultCode == RESULT_OK && data != null ? data : fallback;
            if (result == fallback) nativePlaybackCheckpoints.saveResult(content, id, result);
            if (id != null && nativePlaybackDocumentCurrent) {
                // Autoplay can start from the result handler as soon as it runs.
                pendingNativePlaybackResult = () -> replyToNativePlayback(id, result);
            }
            if (resumed) {
                deliverNativePlaybackResult();
                deliverNativePlaybackCheckpoint();
            }
            return;
        }
        if (!fileChooser.onActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onDestroy() {
        fileChooser.cancel();
        handler.removeCallbacks(readyWatchdog);
        handler.removeCallbacks(loadRetry);
        if (webView != null) {
            webView.loadUrl("about:blank");
            root.removeView(webView); // destroy() must not run on an attached WebView
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
