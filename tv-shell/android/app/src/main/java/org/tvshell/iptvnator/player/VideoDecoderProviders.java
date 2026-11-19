package org.tvshell.iptvnator.player;

import android.util.Log;

import org.tvshell.iptvnator.ShellApp;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Finds the build's {@link VideoDecoderProvider}, if it has one (see its documentation). */
public final class VideoDecoderProviders {
    private static VideoDecoderProvider provider;
    private static boolean loaded;

    private VideoDecoderProviders() {
    }

    /** The first registered provider, or null. */
    public static synchronized VideoDecoderProvider get() {
        if (loaded) return provider;
        loaded = true;
        try {
            Iterator<VideoDecoderProvider> found = ServiceLoader.load(
                VideoDecoderProvider.class, VideoDecoderProviders.class.getClassLoader()).iterator();
            if (found.hasNext()) provider = found.next();
        } catch (ServiceConfigurationError e) {
            Log.w(ShellApp.TAG, "video decoder provider unavailable: " + e.getClass().getSimpleName());
        }
        return provider;
    }
}
