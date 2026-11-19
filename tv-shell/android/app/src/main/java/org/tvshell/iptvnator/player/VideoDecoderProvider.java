package org.tvshell.iptvnator.player;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;

/**
 * A video decoder the built-in player can fall back to when the device's own decoders show
 * nothing, for example an FFmpeg software decoder. This build ships none. A build that has one
 * lists its implementation in
 * {@code META-INF/services/org.tvshell.iptvnator.player.VideoDecoderProvider}; it needs a public
 * no-argument constructor.
 */
@OptIn(markerClass = UnstableApi.class)
public interface VideoDecoderProvider {
    /** A short name for the logs, such as "ffmpeg". */
    String id();

    /**
     * Renderers whose extension video renderer is the provider's decoder. The fallback stage sets
     * {@link DefaultRenderersFactory#EXTENSION_RENDERER_MODE_PREFER} on it. The earlier stages use
     * a fresh instance with {@link DefaultRenderersFactory#EXTENSION_RENDERER_MODE_ON} and their
     * own codec selector, so it must build the platform renderers from the selector it is given.
     */
    DefaultRenderersFactory renderersFactory(Context context);
}
