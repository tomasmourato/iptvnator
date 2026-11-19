package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONObject;
import org.tvshell.iptvnator.Prefs;

import java.util.List;

/**
 * The page side of the built-in player: starts {@link NativePlayerActivity} for a bridge request,
 * and hands its result and the saved checkpoints back to the document that asked.
 */
final class NativePlaybackHost {
    static final int REQUEST_CODE = 7103;
    private static final String STATE_PLAYBACK_ID = "nativePlaybackId";
    private static final String STATE_PLAYBACK_CONTENT = "nativePlaybackContent";
    private static final String STATE_PLAYBACK_CHECKPOINT = "nativePlaybackCheckpoint";

    private final MainActivity activity;
    private final Prefs prefs;
    private final NativePlaybackCheckpoints checkpoints;
    private String playbackId;
    private String lastRequestId;
    private Bundle content;
    private boolean documentCurrent;
    private Runnable pendingResult;

    NativePlaybackHost(MainActivity activity, Prefs prefs) {
        this.activity = activity;
        this.prefs = prefs;
        checkpoints = new NativePlaybackCheckpoints(activity);
    }

    void restore(Bundle state) {
        playbackId = state.getString(STATE_PLAYBACK_ID);
        content = state.getBundle(STATE_PLAYBACK_CONTENT);
        Bundle saved = state.getBundle(STATE_PLAYBACK_CHECKPOINT);
        if (saved == null) return;
        for (String id : saved.keySet()) {
            Bundle checkpoint = saved.getBundle(id);
            if (checkpoint != null) checkpoints.save(checkpoint);
        }
    }

    void save(Bundle state) {
        state.putString(STATE_PLAYBACK_ID, playbackId);
        state.putBundle(STATE_PLAYBACK_CONTENT, content);
    }

    /** A new document replaces the one that asked; true when its readiness must be reported again. */
    boolean onPageStarted() {
        pendingResult = null;
        if (playbackId == null && checkpoints.isEmpty()) return false;
        documentCurrent = false;
        return true;
    }

    /** The page was reloaded or its renderer is gone: a pending result has no document to go to. */
    void documentGone() {
        documentCurrent = false;
    }

    void updateAvailability() {
        if (!activity.pageReady()) return;
        activity.js("window.iptvnatorAndroidPlaybackEnabled=" + prefs.nativePlayback()
            + ";window.dispatchEvent(new Event('iptvnator:android-playback-availability'))");
    }

    void play(String id, Object payload) {
        lastRequestId = id;
        Intent intent = prefs.nativePlayback() && activity.resumed() && activity.pageReady() && playbackId == null
            ? NativePlaybackRequest.toIntent(activity, payload) : null;
        if (intent == null) {
            reply(id, null);
            deliverCheckpoints();
            return;
        }
        playbackId = id;
        content = NativePlaybackRequest.checkpointContent(payload, activity.bridgeOrigin());
        documentCurrent = true;
        activity.externalOffer().dismiss();
        activity.fullscreen().hide();
        activity.startActivityForResult(intent.putExtra("requestId", id)
            .putExtra("checkpointContent", content), REQUEST_CODE);
    }

    void stop(String id) {
        if (!id.equals(playbackId)) return;
        content = null;
        NativePlayerActivity.revokeCheckpoint(id);
        activity.finishActivity(REQUEST_CODE);
    }

    void onResult(int resultCode, Intent data) {
        String id = playbackId;
        Bundle requestContent = content;
        playbackId = null;
        content = null;
        Intent fallback = id == null ? null : NativePlayerActivity.takeCheckpoint(id);
        Intent result = resultCode == Activity.RESULT_OK && data != null ? data : fallback;
        if (result == fallback) checkpoints.saveResult(requestContent, id, result);
        if (id != null && documentCurrent) {
            // Autoplay can start from the result handler as soon as it runs.
            pendingResult = () -> reply(id, result);
        }
        if (activity.resumed()) onResume();
    }

    /** The shell is in front again: a pending result first, then the saved checkpoints. */
    void onResume() {
        if (activity.resumed() && pendingResult != null) {
            Runnable result = pendingResult;
            pendingResult = null;
            result.run();
        }
        deliverCheckpoints();
    }

    private void reply(String id, Intent result) {
        String data = result == null ? "null" : "{positionMs:" + result.getLongExtra("position", 0)
            + ",durationMs:" + result.getLongExtra("duration", 0)
            + ",playedMs:" + result.getLongExtra("played", 0)
            + ",savedAt:" + result.getLongExtra("savedAt", 0)
            + ",started:" + result.getBooleanExtra("started", false)
            + ",ended:" + result.getBooleanExtra("ended", false)
            + ",failed:" + result.getBooleanExtra("failed", false) + "}";
        activity.js("window.dispatchEvent(new CustomEvent('iptvnator:android-playback-result',{detail:{id:"
            + JSONObject.quote(id) + ",result:" + data + "}}))");
    }

    void deliverCheckpoints() {
        if (!activity.pageReady() || activity.webView() == null || SettingsActivity.dataCleared) return;
        // A fallback port uses a different WebView data store.
        List<JSONObject> pending = checkpoints.forOrigin(activity.bridgeOrigin());
        for (JSONObject checkpoint : pending) {
            activity.js("window.dispatchEvent(new CustomEvent('iptvnator:android-playback-checkpoint',{detail:"
                + "JSON.parse(" + JSONObject.quote(checkpoint.toString()) + ")}))");
        }
        if (pending.isEmpty()) publishIdle();
    }

    void acknowledge(String id) {
        checkpoints.acknowledge(activity.bridgeOrigin(), id);
        if (checkpoints.forOrigin(activity.bridgeOrigin()).isEmpty()) publishIdle();
    }

    private void publishIdle() {
        if (playbackId != null || !activity.pageReady() || activity.webView() == null || SettingsActivity.dataCleared) return;
        String lastRequest = lastRequestId == null ? "null" : JSONObject.quote(lastRequestId);
        activity.js("window.dispatchEvent(new CustomEvent('iptvnator:android-playback-checkpoints-idle',{detail:"
            + lastRequest + "}))");
    }
}
