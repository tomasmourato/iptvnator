package org.tvshell.iptvnator.ui;

import android.net.Uri;
import android.util.Log;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.backend.LoopbackGuard;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Refuses WebView requests for a backend path (/api) on a loopback host and any port but the
 * shell's own (see {@link LoopbackGuard}). Other local URLs, such as a stream from a local proxy
 * app, pass.
 */
final class LoopbackFilter {

    /** Read on the WebView's network thread; -1 until the first load blocks every loopback port. */
    private volatile int shellPort = -1;
    private final AtomicBoolean logged = new AtomicBoolean();

    void setShellPort(int port) {
        shellPort = port;
    }

    /** For {@code WebViewClient.shouldInterceptRequest}: null lets the request through. */
    WebResourceResponse intercept(WebResourceRequest request) {
        Uri url = request.getUrl();
        if (url == null || !LoopbackGuard.blocks(url.getScheme(), url.getHost(), url.getPort(), url.getPath(), shellPort)) return null;
        if (logged.compareAndSet(false, true)) {
            // No URL: a playlist's addresses may carry credentials.
            Log.w(ShellApp.TAG, "blocked a page request to another local backend (later ones are not logged)");
        }
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Cache-Control", "no-store");
        return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", headers, new ByteArrayInputStream(new byte[0]));
    }
}
