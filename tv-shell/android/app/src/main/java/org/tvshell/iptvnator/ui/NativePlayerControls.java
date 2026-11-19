package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.view.Window;
import android.widget.ImageButton;

import androidx.annotation.OptIn;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.player.NativePlayerFactory;

/** The built-in player's view: Media3's controls, the mute button shown with them, and immersive bars. */
@OptIn(markerClass = UnstableApi.class)
final class NativePlayerControls {
    private final Activity activity;
    private final NativePlaybackState state;
    private final PlayerView view;
    private final ImageButton muteButton;
    private ExoPlayer player;

    NativePlayerControls(Activity activity, NativePlaybackState state) {
        this.activity = activity;
        this.state = state;
        view = activity.findViewById(R.id.native_player);
        muteButton = activity.findViewById(R.id.native_mute);
        muteButton.setOnClickListener(button -> {
            if (player == null) return;
            state.volume = state.volume == 0f ? state.audibleVolume : 0f;
            player.setVolume(state.volume);
            updateMuteButton();
            view.showController();
        });
        updateMuteButton();
        view.setControllerVisibilityListener(
            (PlayerView.ControllerVisibilityListener) muteButton::setVisibility);
        view.setShowRewindButton(!state.live);
        view.setShowFastForwardButton(!state.live);
        view.requestFocus();
    }

    /** The player the controls drive, or null before it is released. */
    void attach(ExoPlayer player) {
        this.player = player;
        view.setPlayer(player == null ? null : NativePlayerFactory.forControls(player, state.live));
    }

    void showError() {
        view.setCustomErrorMessage(activity.getString(R.string.native_playback_failed));
        view.setUseController(false);
    }

    private void updateMuteButton() {
        boolean muted = state.volume == 0f;
        muteButton.setImageResource(muted
            ? android.R.drawable.ic_lock_silent_mode : android.R.drawable.ic_lock_silent_mode_off);
        muteButton.setContentDescription(activity.getString(muted
            ? R.string.native_playback_unmute : R.string.native_playback_mute));
    }

    static void hideSystemBars(Window window) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(window, window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }
}
