package org.tvshell.iptvnator.player;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;

import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory;

/**
 * StaticTV only: NextLib's FFmpeg decoders (GPL-3.0) as the built-in player's last video
 * decoder. On the open-GOP H.264 channel of the phase 2 spike, only this decoder showed a
 * picture. Its FFmpeg audio decoders (AC-3, E-AC-3, DTS, TrueHD) also back the earlier stages,
 * after the device's own. Registered in META-INF/services; see {@link VideoDecoderProvider}.
 */
@OptIn(markerClass = UnstableApi.class)
public final class NextLibDecoderProvider implements VideoDecoderProvider {
    @Override
    public String id() {
        return "ffmpeg";
    }

    @Override
    public DefaultRenderersFactory renderersFactory(Context context) {
        return new NextRenderersFactory(context);
    }
}
