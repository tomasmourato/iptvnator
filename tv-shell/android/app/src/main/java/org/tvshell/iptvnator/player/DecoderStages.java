package org.tvshell.iptvnator.player;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil;

import org.tvshell.iptvnator.backend.DecoderChain.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * The renderers of each decoder stage. Hardware: the device's video decoders that are not
 * software-only, with Media3's extension renderers after them (the AC-3 audio fallback).
 * Software: only the platform's software video decoders. FFmpeg: the provider's renderers, its
 * video decoder first. When a provider exists, the hardware and software stages use its renderers
 * too, with its extensions after the platform's (so its audio decoders replace Media3's AC-3
 * extension). Audio decoders are never filtered.
 */
@OptIn(markerClass = UnstableApi.class)
final class DecoderStages {
    private static final MediaCodecSelector HARDWARE = videoDecoders(false);
    private static final MediaCodecSelector SOFTWARE = videoDecoders(true);

    private DecoderStages() {
    }

    static RenderersFactory renderers(Context context, Stage stage, VideoDecoderProvider provider, boolean forceStall) {
        if (stage == Stage.FFMPEG && provider != null) {
            return provider.renderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        }
        // With a provider, its renderers back the earlier stages too: its extensions (an FFmpeg
        // audio decoder, say) then come after the platform decoders, as Media3's own would.
        DefaultRenderersFactory factory = forceStall ? new DebugForcedStall.RenderersFactory(context)
            : provider != null ? provider.renderersFactory(context) : new DefaultRenderersFactory(context);
        return factory.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setMediaCodecSelector(stage == Stage.SOFTWARE ? SOFTWARE : HARDWARE);
    }

    /** Whether the device can decode this video MIME type at a stage; an unknown type counts as yes. */
    static boolean available(Stage stage, String mimeType, VideoDecoderProvider provider) {
        if (stage == Stage.FFMPEG) return provider != null;
        if (stage == Stage.FAILED) return false;
        if (mimeType == null) return true;
        try {
            return !(stage == Stage.SOFTWARE ? SOFTWARE : HARDWARE).getDecoderInfos(mimeType, false, false).isEmpty();
        } catch (MediaCodecUtil.DecoderQueryException e) {
            return false;
        }
    }

    private static MediaCodecSelector videoDecoders(boolean softwareOnly) {
        return (mimeType, secure, tunneling) -> {
            List<MediaCodecInfo> decoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling);
            if (!MimeTypes.isVideo(mimeType)) return decoders;
            List<MediaCodecInfo> kept = new ArrayList<>();
            for (MediaCodecInfo decoder : decoders) {
                if (decoder.softwareOnly == softwareOnly) kept.add(decoder);
            }
            return kept;
        };
    }
}
