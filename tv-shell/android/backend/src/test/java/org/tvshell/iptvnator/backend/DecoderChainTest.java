package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertEquals;
import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

import org.tvshell.iptvnator.backend.DecoderChain.Stage;

public class DecoderChainTest {
    private static final DecoderChain.Availability ALL = stage -> true;

    public void testWithoutAProviderSoftwareFollowsHardware() {
        assertEquals("hardware", Stage.SOFTWARE, DecoderChain.next(Stage.HARDWARE, false, ALL));
        assertEquals("software", Stage.FAILED, DecoderChain.next(Stage.SOFTWARE, false, ALL));
    }

    public void testWithAProviderFfmpegFollowsHardware() {
        assertEquals("hardware", Stage.FFMPEG, DecoderChain.next(Stage.HARDWARE, true, ALL));
        assertEquals("ffmpeg", Stage.FAILED, DecoderChain.next(Stage.FFMPEG, true, ALL));
        assertTrue("no software stage", !DecoderChain.contains(Stage.SOFTWARE, true));
    }

    public void testStagesWithoutADecoderAreSkipped() {
        DecoderChain.Availability noSoftware = stage -> stage != Stage.SOFTWARE;
        assertEquals("no software decoder", Stage.FAILED, DecoderChain.next(Stage.HARDWARE, false, noSoftware));
        DecoderChain.Availability softwareOnly = stage -> stage == Stage.SOFTWARE;
        assertEquals("no hardware decoder (an emulator)", Stage.SOFTWARE, DecoderChain.next(Stage.HARDWARE, false, softwareOnly));
    }

    public void testFailedAndForeignStagesEndTheChain() {
        assertEquals("failed", Stage.FAILED, DecoderChain.next(Stage.FAILED, false, ALL));
        assertEquals("software in a provider build", Stage.FAILED, DecoderChain.next(Stage.SOFTWARE, true, ALL));
        assertTrue("ffmpeg without a provider", !DecoderChain.contains(Stage.FFMPEG, false));
        assertTrue("failed is never a stage to start on", !DecoderChain.contains(Stage.FAILED, true));
    }

    public void testIdsRoundTrip() {
        for (Stage stage : Stage.values()) assertEquals(stage.id(), stage, Stage.parse(stage.id()));
        assertEquals("unknown", null, Stage.parse("vulkan"));
        assertEquals("null", null, Stage.parse(null));
    }
}
