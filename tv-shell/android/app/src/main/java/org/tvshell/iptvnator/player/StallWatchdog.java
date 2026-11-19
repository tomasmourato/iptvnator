package org.tvshell.iptvnator.player;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.OptIn;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;

import org.tvshell.iptvnator.backend.VideoOutputStall;

/** Polls the player's video decoder counters once a second; the rule is {@link VideoOutputStall}. */
@OptIn(markerClass = UnstableApi.class)
final class StallWatchdog {
    interface Listener {
        /** The decoder takes input and renders nothing. The watchdog stops for this player. */
        void onStall(DecoderCounters counters);

        /** The decoder rendered its first frames; once per player. */
        void onRendering(DecoderCounters counters);
    }

    private static final long POLL_MS = 1_000;
    /** More than the first frame a frozen decoder can still show. */
    private static final int RENDERING_FRAMES = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final VideoOutputStall stall = new VideoOutputStall();
    private final Listener listener;
    private final Runnable poll = this::poll;
    private ExoPlayer player;
    private boolean reportedRendering;

    StallWatchdog(Listener listener) {
        this.listener = listener;
    }

    void start(ExoPlayer player) {
        this.player = player;
        stall.reset();
        reportedRendering = false;
        handler.removeCallbacks(poll);
        handler.postDelayed(poll, POLL_MS);
    }

    void stop() {
        handler.removeCallbacks(poll);
        player = null;
    }

    /** A seek: the decoder is flushed, so the window starts again. */
    void reset() {
        stall.reset();
    }

    private void poll() {
        if (player == null) return;
        DecoderCounters counters = player.getVideoDecoderCounters();
        if (counters == null) {
            stall.reset(); // no video renderer enabled (audio only, or not prepared yet)
        } else {
            counters.ensureUpdated();
            int state = player.getPlaybackState();
            boolean counting = player.getPlayWhenReady() && state != Player.STATE_IDLE && state != Player.STATE_ENDED;
            if (!reportedRendering && counters.renderedOutputBufferCount >= RENDERING_FRAMES) {
                reportedRendering = true;
                listener.onRendering(counters);
            }
            if (stall.sample(SystemClock.elapsedRealtime(), counters.queuedInputBufferCount,
                counters.renderedOutputBufferCount, counting)) {
                stop();
                listener.onStall(counters);
                return;
            }
        }
        handler.postDelayed(poll, POLL_MS);
    }
}
