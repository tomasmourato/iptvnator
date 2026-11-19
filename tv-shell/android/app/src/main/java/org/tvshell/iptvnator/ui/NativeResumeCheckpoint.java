package org.tvshell.iptvnator.ui;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/**
 * The latest result of the running built-in player, kept in memory for the request that started
 * it: Home or Recents can be followed by a launcher relaunch before onStop delivers the result.
 */
final class NativeResumeCheckpoint {
    /** The current result, or null when playback is over and caching should stop. */
    interface Source {
        Intent current();
    }

    private static String checkpointId;
    private static Intent pausedCheckpoint;
    private final String requestId;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Source source;
    private final Runnable cache = new Runnable() {
        @Override
        public void run() {
            Intent result = source == null || !owned() ? null : source.current();
            if (result == null) return;
            pausedCheckpoint = result;
            handler.postDelayed(this, 1000);
        }
    };

    NativeResumeCheckpoint(String requestId) {
        this.requestId = requestId;
        if (requestId == null || !requestId.equals(checkpointId)) pausedCheckpoint = null;
        checkpointId = requestId;
    }

    boolean owned() {
        return requestId != null && requestId.equals(checkpointId);
    }

    void store(Intent result) {
        if (owned()) pausedCheckpoint = result;
    }

    /** Refreshes the checkpoint every second while the player is in front. */
    void startCaching(Source source) {
        this.source = source;
        handler.post(cache);
    }

    void stopCaching() {
        handler.removeCallbacks(cache);
    }

    static Intent take(String id) {
        if (!id.equals(checkpointId)) return null;
        Intent result = pausedCheckpoint;
        checkpointId = null;
        pausedCheckpoint = null;
        return result;
    }

    static void revoke(String id) {
        take(id);
    }
}
