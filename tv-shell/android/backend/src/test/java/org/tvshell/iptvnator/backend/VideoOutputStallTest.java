package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertEquals;
import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

public class VideoOutputStallTest {

    /** Feeds one sample a second from {@code fromS} to {@code toS}; returns the first second that fired, or -1. */
    private static int feed(VideoOutputStall stall, int fromS, int toS, int queuedPerS, int renderedPerS,
                            int queued0, int rendered0, boolean counting) {
        for (int s = fromS; s <= toS; s++) {
            int elapsed = s - fromS;
            if (stall.sample(s * 1000L, queued0 + elapsed * queuedPerS, rendered0 + elapsed * renderedPerS, counting)) {
                return s;
            }
        }
        return -1;
    }

    public void testNeverStartedFiresAfterTheWindow() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("25 inputs a second, nothing rendered", 6, feed(stall, 0, 20, 25, 0, 0, 0, true));
    }

    public void testFrozenAfterTheFirstFrame() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("the first frame rendered", -1, stall.sample(0, 10, 1, true) ? 0 : -1);
        assertEquals("then input with no new frame", 6, feed(stall, 1, 20, 25, 0, 35, 1, true));
    }

    public void testNetworkStarvationNeverFires() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("buffering: nothing queued", -1, feed(stall, 0, 60, 0, 0, 120, 100, true));
        assertEquals("a trickle under the threshold", -1, feed(new VideoOutputStall(), 0, 60, 4, 0, 0, 0, true));
    }

    public void testNormalPlaybackNeverFires() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("25 in, 25 out", -1, feed(stall, 0, 120, 25, 25, 0, 0, true));
        assertEquals("frames dropped but some rendered", -1, feed(new VideoOutputStall(), 0, 60, 25, 2, 0, 0, true));
    }

    public void testPausedTimeDoesNotCount() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("five seconds of a stall", -1, feed(stall, 0, 5, 25, 0, 0, 0, true));
        assertEquals("paused", -1, feed(stall, 6, 30, 0, 0, 125, 0, false));
        assertEquals("the window starts again on resume", 37, feed(stall, 31, 60, 25, 0, 125, 0, true));
    }

    public void testSeekRestartsTheWindow() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("five seconds of a stall", -1, feed(stall, 0, 5, 25, 0, 0, 0, true));
        stall.reset();
        assertEquals("six more seconds after the seek", 12, feed(stall, 6, 30, 25, 0, 125, 0, true));
    }

    public void testANewDecoderRestartsTheWindow() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("five seconds of a stall", -1, feed(stall, 0, 5, 25, 0, 500, 0, true));
        assertEquals("counters start over", 12, feed(stall, 6, 30, 25, 0, 0, 0, true));
    }

    public void testRecoveryClearsTheStall() {
        VideoOutputStall stall = new VideoOutputStall();
        assertEquals("five seconds of a stall", -1, feed(stall, 0, 5, 25, 0, 0, 0, true));
        assertTrue("a rendered frame inside the window", !stall.sample(6000, 150, 1, true));
        assertTrue("still within six seconds of that frame", !stall.sample(11_000, 275, 1, true));
        assertTrue("six seconds after it", stall.sample(12_000, 300, 1, true));
    }
}
