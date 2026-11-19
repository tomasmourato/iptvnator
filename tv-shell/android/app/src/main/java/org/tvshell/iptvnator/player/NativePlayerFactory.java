package org.tvshell.iptvnator.player;

import android.content.Context;

import androidx.annotation.OptIn;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.ForwardingPlayer;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import okhttp3.OkHttpClient;

/** Builds the built-in player's ExoPlayer: OkHttp data source, seek increments, audio focus. */
@OptIn(markerClass = UnstableApi.class)
public final class NativePlayerFactory {
    private NativePlayerFactory() {
    }

    public static ExoPlayer build(Context context, OkHttpClient httpClient, RenderersFactory renderers) {
        OkHttpDataSource.Factory dataSource = new OkHttpDataSource.Factory(httpClient);
        return new ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(new DefaultMediaSourceFactory(dataSource))
            .setSeekBackIncrementMs(15_000)
            .setSeekForwardIncrementMs(30_000)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setUsePlatformDiagnostics(false)
            .build();
    }

    /** The app seeds captions. Android's caption language must not turn them on by itself. */
    public static TrackSelectionParameters initialTracks(ExoPlayer player) {
        return player.getTrackSelectionParameters().buildUpon()
            .setPreferredTextLanguages()
            .setIgnoredTextSelectionFlags(~C.SELECTION_FLAG_FORCED).build();
    }

    /** The player the controls see: live streams offer no seeking. */
    public static Player forControls(ExoPlayer player, boolean live) {
        if (!live) return player;
        return new ForwardingPlayer(player) {
            @Override
            public Player.Commands getAvailableCommands() {
                return super.getAvailableCommands().buildUpon().removeAll(
                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_MEDIA_ITEM, Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD
                ).build();
            }

            @Override
            public boolean isCommandAvailable(int command) {
                return getAvailableCommands().contains(command);
            }
        };
    }
}
