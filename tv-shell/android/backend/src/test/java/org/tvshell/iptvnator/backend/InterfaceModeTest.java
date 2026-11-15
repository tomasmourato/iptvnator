package org.tvshell.iptvnator.backend;

import static org.tvshell.iptvnator.backend.TestMain.assertEquals;
import static org.tvshell.iptvnator.backend.TestMain.assertTrue;

public class InterfaceModeTest {

    public void testAutomaticDetection() {
        assertTrue("phone or tablet", InterfaceMode.touch(false, false, true, "auto"));
        assertTrue("Android TV (TV ui mode)", !InterfaceMode.touch(true, false, false, "auto"));
        assertTrue("TV emulator reporting a touchscreen", !InterfaceMode.touch(true, true, true, "auto"));
        assertTrue("leanback without TV ui mode", !InterfaceMode.touch(false, true, true, "auto"));
        assertTrue("Fire TV stick: no touchscreen", !InterfaceMode.touch(false, false, false, "auto"));
    }

    public void testTheUserChoiceWins() {
        assertTrue("forced TV on a phone", !InterfaceMode.touch(false, false, true, "tv"));
        assertTrue("forced touch on a TV", InterfaceMode.touch(true, true, false, "touch"));
    }

    public void testUnknownChoicesMeanAutomatic() {
        assertEquals("null", "auto", InterfaceMode.normalize(null));
        assertEquals("garbage", "auto", InterfaceMode.normalize("phone"));
        assertEquals("tv kept", "tv", InterfaceMode.normalize("tv"));
        assertEquals("touch kept", "touch", InterfaceMode.normalize("touch"));
        assertTrue("unknown choice detects", InterfaceMode.touch(false, false, true, "TOUCH-ish"));
    }
}
