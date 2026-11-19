package org.tvshell.iptvnator.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.PlaybackStatsListener;

/**
 * What the built-in player reports back to the page: position, duration, played time and how
 * playback ended. It outlives each ExoPlayer instance and the activity's recreation.
 */
@OptIn(markerClass = UnstableApi.class)
final class NativePlaybackState {
    final boolean live;
    boolean hls;
    boolean autoplay = true;
    boolean failed;
    boolean ended;
    boolean started;
    boolean seedCaptions;
    float volume;
    /** The volume Unmute restores. */
    float audibleVolume;
    TrackSelectionParameters trackPreferences;
    private boolean positionVerified;
    private boolean canSnapshot;
    private boolean preserveSavedPosition;
    private long position;
    private long duration;
    private long played;
    private long initialSeek;
    private ExoPlayer player;
    private PlaybackStatsListener stats;

    NativePlaybackState(Intent intent, Bundle saved) {
        volume = intent.getFloatExtra("volume", 1f);
        audibleVolume = volume > 0f ? volume : intent.getFloatExtra("audibleVolume", 0.5f);
        if (audibleVolume == 0f) audibleVolume = 0.5f;
        live = intent.getBooleanExtra("live", false);
        hls = intent.getBooleanExtra("hls", false);
        seedCaptions = intent.getBooleanExtra("captions", false);
        position = live ? 0 : intent.getLongExtra("position", 0);
        if (saved == null) return;
        volume = saved.getFloat("volume", volume);
        position = saved.getLong("position");
        duration = saved.getLong("duration");
        positionVerified = saved.getBoolean("positionVerified");
        started = saved.getBoolean("started");
        played = saved.getLong("played");
        failed = saved.getBoolean("failed");
        hls = saved.getBoolean("hls", hls);
        autoplay = saved.getBoolean("playWhenReady");
        seedCaptions = saved.getBoolean("seedCaptions", seedCaptions);
        Bundle tracks = saved.getBundle("trackPreferences");
        if (tracks != null) trackPreferences = TrackSelectionParameters.fromBundle(tracks);
    }

    void save(Bundle out) {
        snapshot();
        out.putFloat("volume", volume);
        out.putLong("position", position);
        out.putLong("duration", duration);
        out.putBoolean("positionVerified", positionVerified);
        out.putBoolean("started", started);
        out.putLong("played", playedMs());
        out.putBoolean("failed", failed);
        out.putBoolean("hls", hls);
        out.putBoolean("playWhenReady", autoplay);
        out.putBoolean("seedCaptions", seedCaptions);
        TrackSelectionParameters tracks = player == null ? trackPreferences : player.getTrackSelectionParameters();
        if (tracks != null) out.putBundle("trackPreferences", tracks.toBundle());
    }

    void attach(ExoPlayer player, PlaybackStatsListener stats) {
        this.player = player;
        this.stats = stats;
    }

    /** The player is about to be released: keep what the next one, or the result, needs. */
    void detach() {
        if (player == null) return;
        snapshot();
        played = playedMs();
        trackPreferences = player.getTrackSelectionParameters();
        player = null;
        stats = null;
    }

    /** A new preparation of the stream: VOD resumes at the last verified position, live at the edge. */
    void preparing() {
        canSnapshot = false;
        preserveSavedPosition = false;
        initialSeek = live ? 0 : position;
    }

    void onReady() {
        applyInitialSeek();
        canSnapshot = !preserveSavedPosition;
        positionVerified |= canSnapshot;
    }

    private void applyInitialSeek() {
        long seek = initialSeek;
        initialSeek = 0;
        if (seek <= 0) return;
        // An out-of-range seek during preparation can leave a progressive stream at EOF.
        long length = player.getDuration();
        if (length != C.TIME_UNSET && seek >= length) return;
        preserveSavedPosition = !player.isCurrentMediaItemSeekable();
        if (!preserveSavedPosition) player.seekTo(seek);
    }

    /** Turns on the first non-forced caption track once, when the page asked for captions. */
    void seedCaption(Tracks tracks) {
        if (!seedCaptions) return;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)
                    || (group.getTrackFormat(index).selectionFlags & C.SELECTION_FLAG_FORCED) != 0) continue;
                seedCaptions = false;
                player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(), index)).build());
                return;
            }
        }
    }

    Intent result() {
        snapshot();
        boolean completed = ended && started && !live;
        // At natural EOF, the terminal playhead gives the runtime of a stream
        // that did not advertise its duration. Back and Home keep the resume guard.
        if (completed && player != null) {
            duration = Math.max(1000, Math.max(player.getDuration(), player.getCurrentPosition()));
            position = duration;
        }
        boolean savePosition = positionVerified || completed;
        return new Intent().putExtra("savedAt", System.currentTimeMillis())
            .putExtra("position", savePosition ? position : 0)
            .putExtra("duration", savePosition ? duration : 0)
            .putExtra("played", playedMs()).putExtra("started", started)
            .putExtra("ended", completed || (live && ended)).putExtra("failed", failed);
    }

    private long playedMs() {
        if (player == null) return played;
        return played + stats.getCombinedPlaybackStats().getTotalPlayTimeMs();
    }

    private void snapshot() {
        if (player == null || live || !canSnapshot) return;
        duration = Math.max(0, player.getDuration());
        position = Math.max(0, player.getCurrentPosition());
        if (duration > 0) position = Math.min(duration, position);
    }
}
