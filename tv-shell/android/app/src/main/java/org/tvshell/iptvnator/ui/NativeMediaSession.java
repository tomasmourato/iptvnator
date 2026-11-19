package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.annotation.OptIn;
import androidx.media3.common.ForwardingSimpleBasePlayer;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaSession;
import androidx.media3.session.SessionCommands;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.UUID;

/** The built-in player's system media session: play/pause, stop and (VOD) seeking, for trusted controllers only. */
@OptIn(markerClass = UnstableApi.class)
final class NativeMediaSession {
    /** Media Stop from a trusted controller: the activity decides whether it leaves. */
    interface ExitKeys {
        boolean handle(KeyEvent event);
    }

    private NativeMediaSession() {
    }

    static MediaSession open(Activity activity, Player player, boolean live, ExitKeys exitKeys) {
        Player sessionPlayer = new ForwardingSimpleBasePlayer(player) {
            @Override
            protected State getState() {
                State state = super.getState();
                // Media3 errors can carry provider URLs, which the system session does not need.
                return state.buildUpon().setPlayerError(null).build();
            }

            @Override
            protected ListenableFuture<?> handleStop() {
                activity.finish();
                return Futures.immediateVoidFuture();
            }
        };
        return new MediaSession.Builder(activity, sessionPlayer)
            .setId(UUID.randomUUID().toString())
            .setCallback(new MediaSession.Callback() {
                @Override
                public ListenableFuture<MediaSession.ConnectionResult> onConnectAsync(
                    MediaSession session, MediaSession.ControllerInfo controller) {
                    if (!controller.isTrusted()) {
                        return Futures.immediateFuture(MediaSession.ConnectionResult.reject());
                    }
                    // Keep provider URLs and media-item replacement out of Android's transport controls.
                    Player.Commands.Builder commands = new Player.Commands.Builder()
                        .addAll(Player.COMMAND_PLAY_PAUSE, Player.COMMAND_STOP);
                    if (!live) commands.addAll(Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD);
                    return Futures.immediateFuture(MediaSession.ConnectionResult.accept(SessionCommands.EMPTY, commands.build()));
                }

                @Override
                public boolean onMediaButtonEvent(MediaSession session, MediaSession.ControllerInfo controller, Intent intent) {
                    KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    return controller.isTrusted() && event != null
                        && event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_STOP && exitKeys.handle(event);
                }
            }).build();
    }
}
