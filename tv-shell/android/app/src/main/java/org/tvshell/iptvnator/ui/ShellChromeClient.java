package org.tvshell.iptvnator.ui;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.backend.DiagnosticRedactor;

/** File inputs, redacted console errors and the video fullscreen of the shell's page. */
final class ShellChromeClient extends WebChromeClient {
    private final MainActivity activity;
    private final PageStatus status;

    ShellChromeClient(MainActivity activity, PageStatus status) {
        this.activity = activity;
        this.status = status;
    }

    @Override
    public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        return activity.fileChooser().show(callback, params);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage message) {
        if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
            String redacted = DiagnosticRedactor.redact(message.message() + " (" + message.sourceId() + ":" + message.lineNumber() + ")");
            Log.w(ShellApp.TAG, "[console] " + redacted);
            status.consoleError(redacted, message.message().startsWith("Uncaught SyntaxError"));
        }
        // Handled here: do not delegate the original, unsanitized message to WebView.
        return true;
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        activity.fullscreen().show(view, callback);
    }

    @Override
    public void onHideCustomView() {
        activity.fullscreen().hide();
    }
}
