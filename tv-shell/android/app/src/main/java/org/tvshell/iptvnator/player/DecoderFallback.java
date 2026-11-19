package org.tvshell.iptvnator.player;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.DecoderReuseEvaluation;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.analytics.AnalyticsListener;

import org.tvshell.iptvnator.ShellApp;
import org.tvshell.iptvnator.backend.DecoderChain;
import org.tvshell.iptvnator.backend.DecoderChain.Stage;
import org.tvshell.iptvnator.backend.DecoderMemory;

/**
 * Runs the built-in player's video decoder chain ({@link DecoderChain}): builds the current
 * stage's renderers, watches its decoder, and moves to the next stage after a stall, a video
 * decoder error or a video track the stage cannot decode. The host then rebuilds the player on
 * the same stream. A stage after hardware that renders is remembered for the stream
 * ({@link DecoderMemory}), and the next playback starts on it; when it stalls or fails there, the
 * entry is dropped and the chain restarts from hardware. Each change is one log line with the
 * stage, the reason, the decoder name and its counters, never the stream URL.
 */
@OptIn(markerClass = UnstableApi.class)
public final class DecoderFallback implements StallWatchdog.Listener {
    public interface Host {
        /** The stage changed: rebuild the player on the same media item. */
        void restartOnNewStage();

        /** The current stage stalled and no stage is left. */
        void decodersExhausted();
    }

    private final Context context;
    private final Host host;
    private final VideoDecoderProvider provider = VideoDecoderProviders.get();
    private final StallWatchdog watchdog = new StallWatchdog(this);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DebugForcedStall debug;
    private final Stage firstStage;
    private final DecoderMemory memory;
    private final String streamKey;
    private boolean fromMemory;
    private Stage stage;
    private String decoderName = "none";
    private String videoMimeType;
    private ExoPlayer player;
    private boolean switching;
    private final AnalyticsListener analytics = new AnalyticsListener() {
        @Override
        public void onVideoDecoderInitialized(EventTime eventTime, String name, long initializedAtMs, long initializationMs) {
            decoderName = name;
        }

        @Override
        public void onVideoInputFormatChanged(EventTime eventTime, Format format, DecoderReuseEvaluation evaluation) {
            if (format.sampleMimeType != null) videoMimeType = format.sampleMimeType;
        }

        @Override
        public void onPositionDiscontinuity(EventTime eventTime, Player.PositionInfo oldPosition,
                                            Player.PositionInfo newPosition, int reason) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) watchdog.reset();
        }
    };

    /** @param savedStage the stage of a recreated activity, or null for the stream's remembered stage or hardware */
    public DecoderFallback(Context context, Host host, String streamUrl, String savedStage) {
        this.context = context.getApplicationContext();
        this.host = host;
        memory = new DecoderMemory(new DecoderMemoryStore(this.context));
        streamKey = DecoderMemory.key(streamUrl);
        Stage saved = Stage.parse(savedStage);
        Stage remembered = saved == null ? memory.recall(streamKey, System.currentTimeMillis()) : null;
        Stage initial = saved != null ? saved : remembered;
        stage = initial != null && DecoderChain.contains(initial, provider != null) ? initial : Stage.HARDWARE;
        if (remembered != null && stage != remembered) memory.forget(streamKey); // from a build with another chain
        fromMemory = remembered != null && stage == remembered;
        firstStage = stage;
        debug = DebugForcedStall.read(context);
        if (fromMemory) log("starting on " + stage.id() + " (remembered)", null);
    }

    public String stage() {
        return stage.id();
    }

    public RenderersFactory renderers() {
        return DecoderStages.renderers(context, stage, provider, debug.stalls(stage, firstStage));
    }

    public void attach(ExoPlayer player) {
        this.player = player;
        switching = false;
        decoderName = "none";
        player.addAnalyticsListener(analytics);
        watchdog.start(player);
    }

    public void detach() {
        watchdog.stop();
        if (player != null) player.removeAnalyticsListener(analytics);
        player = null;
    }

    /** A player error: true when it was a video decoder error and the next stage takes over. */
    public boolean onError(PlaybackException error) {
        if (switching) return true;
        if (!(error instanceof ExoPlaybackException)) return false;
        ExoPlaybackException playback = (ExoPlaybackException) error;
        Format format = playback.rendererFormat;
        boolean videoDecoder = playback.type == ExoPlaybackException.TYPE_RENDERER && format != null
            && MimeTypes.isVideo(format.sampleMimeType) && isDecoderError(error.errorCode);
        if (!videoDecoder) return false;
        videoMimeType = format.sampleMimeType;
        return advance(error.getErrorCodeName());
    }

    /** The stage has no decoder for the video track: true when the next stage takes over. */
    public boolean onUnsupportedVideo(Tracks tracks) {
        if (switching) return true;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() == C.TRACK_TYPE_VIDEO && group.length > 0) {
                videoMimeType = group.getTrackFormat(0).sampleMimeType;
            }
        }
        return advance("no " + stage.id() + " decoder for " + videoMimeType);
    }

    @Override
    public void onStall(DecoderCounters counters) {
        if (!advance("stall")) host.decodersExhausted();
    }

    @Override
    public void onRendering(DecoderCounters counters) {
        log(stage.id() + " rendering", counters);
        if (stage != Stage.HARDWARE) memory.remember(streamKey, stage, System.currentTimeMillis());
    }

    private boolean advance(String reason) {
        DecoderChain.Availability available = candidate -> DecoderStages.available(candidate, videoMimeType, provider);
        if (memory.recall(streamKey, System.currentTimeMillis()) == stage) memory.forget(streamKey);
        Stage next;
        if (fromMemory) {
            // The remembered stage no longer works for this stream: start the chain over.
            fromMemory = false;
            next = available.has(Stage.HARDWARE) ? Stage.HARDWARE : DecoderChain.next(Stage.HARDWARE, provider != null, available);
            reason += ", remembered stage dropped";
        } else {
            next = DecoderChain.next(stage, provider != null, available);
        }
        log(stage.id() + " -> " + next.id() + " (" + reason + ")", player == null ? null : player.getVideoDecoderCounters());
        if (next == Stage.FAILED) return false;
        stage = next;
        switching = true;
        watchdog.stop();
        // Not from inside the player's own callback: the host releases it.
        handler.post(host::restartOnNewStage);
        return true;
    }

    private void log(String what, DecoderCounters counters) {
        String detail = counters == null ? "" : ", queued=" + counters.queuedInputBufferCount
            + ", rendered=" + counters.renderedOutputBufferCount + ", dropped=" + counters.droppedBufferCount;
        String extension = stage == Stage.FFMPEG && provider != null ? " via " + provider.id() : "";
        Log.i(ShellApp.TAG, "native decoder: " + what + ", decoder=" + decoderName + extension + detail);
    }

    private static boolean isDecoderError(int code) {
        return code == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
            || code == PlaybackException.ERROR_CODE_DECODING_FAILED
            || code == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
            || code == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED;
    }
}
