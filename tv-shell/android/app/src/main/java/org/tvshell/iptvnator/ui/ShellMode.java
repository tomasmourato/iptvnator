package org.tvshell.iptvnator.ui;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

import org.tvshell.iptvnator.Prefs;
import org.tvshell.iptvnator.backend.InterfaceMode;

/** Reads the device facts for {@link InterfaceMode}: TV (remote control) or touch (phone, tablet). */
final class ShellMode {

    private ShellMode() {
    }

    static boolean isTouch(Context context, Prefs prefs) {
        UiModeManager ui = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        boolean television = ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
        PackageManager pm = context.getPackageManager();
        boolean leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        boolean touchscreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
        return InterfaceMode.touch(television, leanback, touchscreen, prefs.interfaceMode());
    }
}
