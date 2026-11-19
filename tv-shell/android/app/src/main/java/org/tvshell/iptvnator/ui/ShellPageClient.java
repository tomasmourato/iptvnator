package org.tvshell.iptvnator.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.server.TvShellServer;

/** Navigation, load errors and renderer deaths of the shell's page. */
final class ShellPageClient extends WebViewClient {
    /** Renderer deaths are counted across Activity recreations; a burst means the page itself is the problem. */
    private static final long RENDERER_DEATH_WINDOW_MS = 60_000;
    private static final int RENDERER_DEATH_LIMIT = 3;
    private static int rendererDeaths;
    private static long lastRendererDeathAt;

    private final MainActivity activity;
    private final PageStatus status;

    ShellPageClient(MainActivity activity, PageStatus status) {
        this.activity = activity;
        this.status = status;
    }

    /** True once when the renderer died too often in a short time: show the error, not another load. */
    static boolean rendererLooping() {
        if (rendererDeaths < RENDERER_DEATH_LIMIT
            || SystemClock.uptimeMillis() - lastRendererDeathAt >= RENDERER_DEATH_WINDOW_MS) return false;
        rendererDeaths = 0;
        return true;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (!request.isForMainFrame()) return false;
        Uri uri = request.getUrl();
        TvShellServer server = activity.server();
        if (server != null && uri.toString().startsWith(server.baseUrl())) return false;
        // Outside links (YouTube trailers, provider sites): hand http(s) to the system, the
        // same allowlist as the Electron shell. Any other scheme stays inside the shell.
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            Log.w(ShellApp.TAG, "blocked navigation to scheme " + scheme);
            return true;
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (RuntimeException e) {
            activity.toast(activity.getString(R.string.cannot_open_link));
        }
        return true;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        return activity.loopback().intercept(request);
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (!request.isForMainFrame()) return;
        int code = error.getErrorCode();
        boolean serverUnreachable = code == ERROR_CONNECT || code == ERROR_HOST_LOOKUP || code == ERROR_TIMEOUT
            || code == ERROR_IO || code == ERROR_FAILED_SSL_HANDSHAKE;
        // The embedded server was not answering (its thread died while the app sat in the
        // background, or the process was restored before it listened): load() restarts it.
        if (serverUnreachable && status.retryLoad()) {
            Log.w(ShellApp.TAG, "main frame load failed (" + code + "), restarting the server and retrying once");
            return;
        }
        status.showError(activity.getString(R.string.load_error, String.valueOf(error.getDescription()), String.valueOf(code)));
    }

    /** The server answered the page itself with an error (the bundle missing from the APK, out of memory). */
    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
        if (!request.isForMainFrame()) return;
        status.showError(activity.getString(R.string.load_error, String.valueOf(errorResponse.getReasonPhrase()),
            String.valueOf(errorResponse.getStatusCode())));
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (activity.nativePlayback().onPageStarted()) status.ready = false;
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        boolean first = !status.finished;
        status.finished = true;
        if (!activity.resumed()) activity.setPageForeground(false);
        if (first && !status.ready && !status.showingError()) {
            // The bundle has run: Angular's bootstrap (IndexedDB, playlists) gets a period of its own.
            status.armWatchdog(activity.resumed());
        }
        // Never take the focus away from the error panel (an error page commits after onReceivedError).
        if (!status.showingError()) activity.focusPage();
    }

    /** The sandboxed renderer died (OOM abort or system kill). Unhandled, Chromium crashes or kills the app. */
    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        boolean crashed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail.didCrash();
        boolean resumed = activity.resumed();
        long now = SystemClock.uptimeMillis();
        if (crashed || resumed) {
            // The system reclaiming the hidden renderer while another app (VLC, the launcher) is in
            // front is routine on a 1 GB device, not a sign that the page is broken: not counted.
            rendererDeaths = now - lastRendererDeathAt < RENDERER_DEATH_WINDOW_MS ? rendererDeaths + 1 : 1;
            lastRendererDeathAt = now;
        }
        Log.w(ShellApp.TAG, "web renderer gone (crashed=" + crashed + ", resumed=" + resumed + ", count=" + rendererDeaths + ")");
        activity.onRendererGone();
        return true;
    }
}
