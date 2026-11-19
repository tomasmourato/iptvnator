package org.tvshell.iptvnator.backend;

import java.util.Locale;

/**
 * The built-in player's video decoder stages, tried in order until one renders frames:
 * the device's hardware decoder first, then an FFmpeg decoder when a provider is installed,
 * otherwise the platform's software decoder; after that, playback has failed. A stage is skipped
 * when the device has no decoder of that kind for the stream's video. Pure Java, tested on the JVM.
 */
public final class DecoderChain {
    public enum Stage {
        HARDWARE, SOFTWARE, FFMPEG, FAILED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** The stage with this {@link #id()}, or null. */
        public static Stage parse(String id) {
            for (Stage stage : values()) {
                if (stage.id().equals(id)) return stage;
            }
            return null;
        }
    }

    /** Whether the device can decode the stream's video at a stage. */
    public interface Availability {
        boolean has(Stage stage);
    }

    private DecoderChain() {
    }

    /** The stages in order, without {@link Stage#FAILED}. */
    public static Stage[] order(boolean providerInstalled) {
        return providerInstalled
            ? new Stage[] {Stage.HARDWARE, Stage.FFMPEG}
            : new Stage[] {Stage.HARDWARE, Stage.SOFTWARE};
    }

    /** True when the stage belongs to this chain (a remembered stage may come from another build). */
    public static boolean contains(Stage stage, boolean providerInstalled) {
        for (Stage candidate : order(providerInstalled)) {
            if (candidate == stage) return true;
        }
        return false;
    }

    /** The stage after {@code current} that the device can use, or {@link Stage#FAILED}. */
    public static Stage next(Stage current, boolean providerInstalled, Availability available) {
        Stage[] order = order(providerInstalled);
        boolean after = false;
        for (Stage stage : order) {
            if (after && available.has(stage)) return stage;
            if (stage == current) after = true;
        }
        return Stage.FAILED;
    }
}
