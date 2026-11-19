package org.tvshell.iptvnator.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.SystemClock;

import org.tvshell.iptvnator.Prefs;
import org.tvshell.iptvnator.R;

/**
 * Ask before leaving the app for a stream, unless the user said not to. One dialog at a time; the
 * same URL is not offered again within a few seconds (a copy followed by the error of the same
 * stream, or the player's error burst), but a later attempt on the channel asks again.
 */
final class ExternalOffer {

    private final MainActivity activity;
    private final Prefs prefs;
    private String lastOfferedUrl;
    private long lastOfferedAt;
    private AlertDialog externalDialog;

    ExternalOffer(MainActivity activity, Prefs prefs) {
        this.activity = activity;
        this.prefs = prefs;
    }

    void offer(final String url, final String title, boolean becauseOfError) {
        if (url == null || url.isEmpty() || activity.webView() == null || activity.isFinishing()) return;
        if (becauseOfError && !prefs.promptExternalOnError()) return;
        if (externalDialog != null && externalDialog.isShowing()) return;
        long now = SystemClock.uptimeMillis();
        if (becauseOfError && url.equals(lastOfferedUrl) && now - lastOfferedAt < 5_000) return;
        lastOfferedUrl = url;
        lastOfferedAt = now;
        externalDialog = new AlertDialog.Builder(activity)
            .setTitle(becauseOfError ? R.string.external_prompt_title_error : R.string.external_prompt_title)
            .setMessage(activity.getString(R.string.external_prompt_message, title == null || title.isEmpty() ? url : title))
            .setPositiveButton(R.string.external_prompt_open, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    open(url, title);
                }
            })
            .setNegativeButton(R.string.external_prompt_cancel, null)
            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    externalDialog = null;
                    activity.focusPage();
                }
            })
            .show();
    }

    /** Opens a stream in the chosen external player, without asking. */
    void open(String url, String title) {
        if (url == null || url.trim().isEmpty()) {
            activity.toast(activity.getString(R.string.no_stream_to_open));
            return;
        }
        ExternalPlayer.open(activity, url.trim(), title, prefs.externalPlayerPackage());
    }

    /** Native playback is taking over the screen: close a pending offer. */
    void dismiss() {
        if (externalDialog != null) externalDialog.dismiss();
    }
}
