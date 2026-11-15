package org.tvshell.iptvnator.ui;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.webkit.WebSettings;
import android.webkit.WebView;

import org.tvshell.iptvnator.Prefs;
import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.server.TvShellServer;

/** Screen metrics and the diagnostics text of the error panel. */
final class ShellDiagnostics {

    private ShellDiagnostics() {
    }

    /** Screen width in CSS px at scale 1 (Android dp): 960 on a 1080p Fire TV. */
    static int screenCssWidth(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.density > 0 ? Math.round(dm.widthPixels / dm.density) : 0;
    }

    /** "1920×1080 px, density 2.0 → 960 px CSS" for the diagnostics. */
    static String screenInfo(Context context) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return dm.widthPixels + "\u00d7" + dm.heightPixels + " px, density " + dm.density + " \u2192 " + screenCssWidth(context) + " px CSS";
    }

    /** @param layout the loaded layout width, or "touch" for the touch interface */
    static String describe(Context context, Prefs prefs, TvShellServer server, String layout) {
        String chrome = "?";
        String webViewPackage = "";
        try {
            String ua = WebSettings.getDefaultUserAgent(context);
            int at = ua.indexOf("Chrome/");
            if (at >= 0) {
                int end = ua.indexOf(' ', at);
                chrome = ua.substring(at + 7, end > 0 ? end : ua.length());
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.pm.PackageInfo info = WebView.getCurrentWebViewPackage();
                if (info != null) webViewPackage = info.packageName + " " + info.versionName;
            }
        } catch (RuntimeException e) {
            // The system WebView is missing, disabled or being updated: the rest still helps.
            webViewPackage = "unavailable (" + e.getClass().getSimpleName() + ")";
        }
        String port = server == null ? "-" : String.valueOf(server.port());
        if (server != null && ShellApp.onFallbackPort()) port += " (temporary, saved data on " + prefs.serverPort() + ")";
        return context.getString(R.string.diagnostics, chrome, webViewPackage, Build.MODEL, Build.VERSION.RELEASE,
            port, screenInfo(context) + ", layout " + layout);
    }
}
