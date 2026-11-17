package org.tvshell.iptvnator.ui;

import android.content.Context;
import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;

import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.backend.ApiSecret;
import org.tvshell.iptvnator.server.TvShellServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Registers {@code assets/tvshell/api-secret.js}, filled in with the launch's {@link ApiSecret}, as
 * a document-start script for the origin the server is bound to right now: the saved port or, for
 * this run only, a fallback port. Other origins (other local ports, providers, opaque frames) never
 * get the script. Registered before loadUrl, so the page's first /api request already has the header.
 */
final class ApiScript {

    private static final String ASSET = "tvshell/api-secret.js";
    private final Context context;
    private String template;
    private WebView boundView;
    private String boundOrigin;
    private ScriptHandler handler;

    ApiScript(Context context) {
        this.context = context.getApplicationContext();
    }

    /** False when the script cannot be registered (the asset is missing or the WebView refused it). */
    boolean bind(WebView webView, TvShellServer server) {
        String origin = "http://127.0.0.1:" + server.port();
        if (webView == boundView && origin.equals(boundOrigin)) return true;
        try {
            if (template == null) template = readAsset();
            if (handler != null && webView == boundView) handler.remove();
            handler = WebViewCompat.addDocumentStartJavaScript(webView, ShellApp.apiSecret().startScript(template),
                Collections.singleton(origin));
            boundView = webView;
            boundOrigin = origin;
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e(ShellApp.TAG, "cannot register the /api script: " + e.getClass().getSimpleName());
            handler = null;
            boundView = null;
            boundOrigin = null;
            return false;
        }
    }

    private String readAsset() throws IOException {
        InputStream in = context.getAssets().open(ASSET);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
