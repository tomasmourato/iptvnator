package org.tvshell.iptvnator.player;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Handler;

import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.exoplayer.video.VideoRendererEventListener;

import org.tvshell.iptvnator.backend.DecoderChain.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Test hook, debug builds only: {@code adb shell run-as <package> touch files/debug-force-stall}
 * makes the video decoder of the stage playback starts on swallow every frame without an error,
 * as the stalled decoders seen on real devices do. With the content {@code all}, every
 * MediaCodec stage stalls. The file is ignored unless the app is debuggable.
 */
@OptIn(markerClass = UnstableApi.class)
final class DebugForcedStall {
    private static final String FILE = "debug-force-stall";

    private final boolean everyStage;
    private final boolean enabled;

    private DebugForcedStall(boolean enabled, boolean everyStage) {
        this.enabled = enabled;
        this.everyStage = everyStage;
    }

    static DebugForcedStall read(Context context) {
        if ((context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) return new DebugForcedStall(false, false);
        File file = new File(context.getFilesDir(), FILE);
        if (!file.isFile()) return new DebugForcedStall(false, false);
        byte[] content = new byte[16];
        int length = 0;
        try (FileInputStream in = new FileInputStream(file)) {
            length = Math.max(0, in.read(content));
        } catch (IOException ignored) {
            // An unreadable file still asks for the first stage.
        }
        return new DebugForcedStall(true, new String(content, 0, length, StandardCharsets.US_ASCII).trim().equals("all"));
    }

    /** Whether this stage's video decoder must stall. The FFmpeg provider's renderer is never touched. */
    boolean stalls(Stage stage, Stage firstStage) {
        return enabled && stage != Stage.FFMPEG && (everyStage || stage == firstStage);
    }

    /** Media3's renderers with the MediaCodec video renderer replaced by one that drops every frame. */
    static final class RenderersFactory extends DefaultRenderersFactory {
        RenderersFactory(Context context) {
            super(context);
        }

        @Override
        protected void buildVideoRenderers(Context context, int extensionRendererMode, MediaCodecSelector selector,
                                           boolean enableDecoderFallback, Handler eventHandler,
                                           VideoRendererEventListener eventListener, long allowedVideoJoiningTimeMs,
                                           ArrayList<Renderer> out) {
            int first = out.size();
            super.buildVideoRenderers(context, extensionRendererMode, selector, enableDecoderFallback, eventHandler,
                eventListener, allowedVideoJoiningTimeMs, out);
            for (int index = first; index < out.size(); index++) {
                if (!(out.get(index) instanceof MediaCodecVideoRenderer)) continue;
                out.set(index, new StallingVideoRenderer(context, selector, allowedVideoJoiningTimeMs,
                    enableDecoderFallback, eventHandler, eventListener));
            }
        }
    }

    /** Takes every input buffer and drops every decoded frame, so nothing is ever rendered. */
    private static final class StallingVideoRenderer extends MediaCodecVideoRenderer {
        StallingVideoRenderer(Context context, MediaCodecSelector selector, long allowedJoiningTimeMs,
                              boolean enableDecoderFallback, Handler eventHandler, VideoRendererEventListener eventListener) {
            super(context, selector, allowedJoiningTimeMs, enableDecoderFallback, eventHandler, eventListener,
                DefaultRenderersFactory.MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY);
        }

        @Override
        protected boolean processOutputBuffer(long positionUs, long elapsedRealtimeUs, MediaCodecAdapter codec,
                                              ByteBuffer buffer, int bufferIndex, int bufferFlags, int sampleCount,
                                              long bufferPresentationTimeUs, boolean isDecodeOnlyBuffer,
                                              boolean isLastBuffer, Format format) throws ExoPlaybackException {
            if (codec == null || isLastBuffer) {
                return super.processOutputBuffer(positionUs, elapsedRealtimeUs, codec, buffer, bufferIndex, bufferFlags,
                    sampleCount, bufferPresentationTimeUs, isDecodeOnlyBuffer, isLastBuffer, format);
            }
            dropOutputBuffer(codec, bufferIndex, bufferPresentationTimeUs);
            return true;
        }
    }
}
