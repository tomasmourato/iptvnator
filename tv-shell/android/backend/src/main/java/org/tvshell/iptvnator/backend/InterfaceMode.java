package org.tvshell.iptvnator.backend;

/**
 * TV or touch interface. TV keeps the remote-control layer and the pinned desktop layout; touch
 * (phones, tablets) gets a device-width page without the D-pad focus layer. Pure Java so the rule
 * is tested on the JVM; the Android adapter feeds it the device facts.
 */
public final class InterfaceMode {

    public static final String AUTO = "auto";
    public static final String TV = "tv";
    public static final String TOUCH = "touch";

    private InterfaceMode() {
    }

    /** The stored choice, or {@link #AUTO} for anything unknown. */
    public static String normalize(String choice) {
        return TV.equals(choice) || TOUCH.equals(choice) ? choice : AUTO;
    }

    /**
     * @param television  {@code UiModeManager} reports a TV
     * @param leanback    the device has the Leanback feature (Android TV, Google TV)
     * @param touchscreen the device has a touchscreen
     * @param choice      the user's setting: auto, tv or touch
     * @return true for the touch interface
     */
    public static boolean touch(boolean television, boolean leanback, boolean touchscreen, String choice) {
        String normalized = normalize(choice);
        if (TV.equals(normalized)) return false;
        if (TOUCH.equals(normalized)) return true;
        return !television && !leanback && touchscreen;
    }
}
