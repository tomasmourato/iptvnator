package org.tvshell.iptvnator.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;
import android.webkit.WebView;

import androidx.webkit.ScriptHandler;
import androidx.webkit.WebViewCompat;

import org.tvshell.iptvnator.ShellApp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Touch mode: registers {@code assets/tvshell/color-scheme.js}, filled in with Android's dark
 * theme state, as a document-start script for the shell's origin, so the app's System theme
 * follows the phone. The system WebView reports a light colour scheme whatever that state is.
 * Re-registered when the state changes (for the next page load), and the open page is told.
 */
final class ColorSchemeScript {

    private static final String ASSET = "tvshell/color-scheme.js";
    private static final String PLACEHOLDER = "__TVSHELL_DARK__";
    private final Context context;
    private String template;
    private ScriptHandler handler;
    private WebView boundView;
    private String boundOrigin;
    private Boolean boundDark;

    ColorSchemeScript(Context context) {
        this.context = context.getApplicationContext();
    }

    static boolean isDark(Configuration configuration) {
        return (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Registers the script for {@code origin} with the current state; a no-op when unchanged. */
    void bind(WebView webView, String origin, boolean dark) {
        if (webView == boundView && origin.equals(boundOrigin) && Boolean.valueOf(dark).equals(boundDark)) return;
        try {
            if (template == null) template = readAsset();
            unbind();
            handler = WebViewCompat.addDocumentStartJavaScript(webView,
                template.replace(PLACEHOLDER, dark ? "true" : "false"), Collections.singleton(origin));
            boundView = webView;
            boundOrigin = origin;
            boundDark = dark;
        } catch (IOException | RuntimeException e) {
            // The page keeps the WebView's light scheme; nothing else depends on this script.
            Log.w(ShellApp.TAG, "cannot register the colour scheme script: " + e.getClass().getSimpleName());
            unbind();
        }
    }

    /** Re-registers for the next page load and tells the open page; false when nothing changed. */
    boolean update(boolean dark) {
        if (boundView == null || Boolean.valueOf(dark).equals(boundDark)) return false;
        WebView view = boundView;
        bind(view, boundOrigin, dark);
        view.evaluateJavascript("window.__tvShellColorScheme&&window.__tvShellColorScheme(" + dark + ")", null);
        return true;
    }

    /** TV mode, or a new WebView: no script. */
    void unbind() {
        if (handler != null) handler.remove();
        handler = null;
        boundView = null;
        boundOrigin = null;
        boundDark = null;
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
