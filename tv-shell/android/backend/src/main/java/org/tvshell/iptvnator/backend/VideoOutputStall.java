package org.tvshell.iptvnator.backend;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Detects a video decoder that takes input but shows nothing. Some hardware and platform software
 * decoders accept thousands of buffers of an open-GOP stream and render no frame, or only the
 * first one, without reporting any error; the built-in player must notice and switch decoders.
 *
 * <p>Fed once a second with the decoder's cumulative counters. It fires when, over the last
 * {@link #WINDOW_MS}, the decoder queued at least {@link #MIN_QUEUED} input buffers and rendered
 * no new frame. Network starvation queues nothing, so it never fires; a pause, a seek or a new
 * decoder restarts the window. Pure Java, tested on the JVM.
 */
public final class VideoOutputStall {
    public static final long WINDOW_MS = 6_000;
    public static final int MIN_QUEUED = 30;

    private final ArrayDeque<long[]> samples = new ArrayDeque<>();

    /**
     * @param nowMs    a monotonic clock
     * @param queued   the decoder's queued input buffers so far
     * @param rendered the decoder's rendered output buffers so far
     * @param counting playback is wanted (play when ready, neither idle nor ended)
     * @return true when the decoder is stalled
     */
    public boolean sample(long nowMs, int queued, int rendered, boolean counting) {
        if (!counting) {
            reset();
            return false;
        }
        long[] last = samples.peekLast();
        // Lower counters belong to a new decoder instance.
        if (last != null && (queued < last[1] || rendered < last[2] || nowMs < last[0])) reset();
        samples.addLast(new long[] {nowMs, queued, rendered});
        // Keep one sample at or before the window's start as its baseline.
        while (samples.size() > 2 && second()[0] <= nowMs - WINDOW_MS) samples.removeFirst();
        long[] first = samples.peekFirst();
        if (nowMs - first[0] < WINDOW_MS) return false;
        return queued - first[1] >= MIN_QUEUED && rendered == first[2];
    }

    /** A seek, a pause or a stage change: start a new window. */
    public void reset() {
        samples.clear();
    }

    private long[] second() {
        Iterator<long[]> it = samples.iterator();
        it.next();
        return it.next();
    }
}
