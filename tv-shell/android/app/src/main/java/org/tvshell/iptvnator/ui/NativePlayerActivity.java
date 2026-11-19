package org.tvshell.iptvnator.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;

import androidx.annotation.OptIn;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.PlaybackStatsListener;
import androidx.media3.exoplayer.source.UnrecognizedInputFormatException;
import androidx.media3.session.MediaSession;

import org.tvshell.iptvnator.R;
import org.tvshell.iptvnator.player.NativePlayerFactory;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@OptIn(markerClass = UnstableApi.class)
public final class NativePlayerActivity extends Activity {
    private ExoPlayer player;
    private MediaSession mediaSession;
    private NativePlayerControls controls;
    private HttpUrl stream;
    private OkHttpClient httpClient;
    private NativePlaybackState state;
    private String requestId;
    private NativeResumeCheckpoint checkpoint;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        requestId = getIntent().getStringExtra("requestId");
        checkpoint = new NativeResumeCheckpoint(requestId);
        state = new NativePlaybackState(getIntent(), savedState);
        String url = getIntent().getStringExtra("url");
        stream = url == null ? null : HttpUrl.parse(url);
        if (stream == null) {
            finish();
            return;
        }
        setContentView(R.layout.activity_native_player);
        controls = new NativePlayerControls(this, state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (stream == null || isFinishing()) return;
        if (state.failed) {
            showError();
            return;
        }
        // Media3's default exception logging can include signed media URLs.
        Log.setLogLevel(Log.LOG_LEVEL_OFF);
        if (httpClient == null) {
            httpClient = (OkHttpClient) getLastNonConfigurationInstance();
            if (httpClient == null) {
                httpClient = NativePlaybackRequest.client(stream, getIntent().getBundleExtra("headers"));
            }
        }
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON);
        player = NativePlayerFactory.build(this, httpClient, renderers);
        player.setVolume(state.volume);
        if (state.trackPreferences == null) state.trackPreferences = NativePlayerFactory.initialTracks(player);
        player.setTrackSelectionParameters(state.trackPreferences);
        PlaybackStatsListener stats = new PlaybackStatsListener(false, null);
        player.addAnalyticsListener(stats);
        state.attach(player, stats);
        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                state.started |= isPlaying;
                if (isPlaying) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }

            @Override
            public void onTracksChanged(Tracks tracks) {
                if (hasUnsupportedTrack(tracks, C.TRACK_TYPE_VIDEO)
                    || hasUnsupportedTrack(tracks, C.TRACK_TYPE_AUDIO)) {
                    fail();
                    return;
                }
                state.seedCaption(tracks);
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (state.failed) return;
                if (playbackState == Player.STATE_READY) state.onReady();
                if (playbackState == Player.STATE_ENDED && player.getPlaybackState() == Player.STATE_ENDED
                    && !isFinishing()) {
                    state.ended = true;
                    finish();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (!state.hls && !isFinishing() && error.getCause() instanceof UnrecognizedInputFormatException) {
                    // A manifest may be served behind an extensionless URL or a redirect.
                    state.hls = true;
                    prepareStream();
                    return;
                }
                state.failed = true;
                showError();
            }
        });
        controls.attach(player);
        prepareStream();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !state.failed && !isFinishing()) {
            mediaSession = NativeMediaSession.open(this, player, state.live, this::handleExitKey);
            player.setPlayWhenReady(state.autoplay);
        }
        checkpoint.startCaching(() -> player == null || isFinishing() ? null : state.result());
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return httpClient;
    }

    private void prepareStream() {
        state.preparing();
        MediaItem.Builder media = new MediaItem.Builder().setUri(stream.toString());
        if (state.hls) media.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(media.build());
        player.prepare();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) NativePlayerControls.hideSystemBars(getWindow());
    }

    private static boolean hasUnsupportedTrack(Tracks tracks, int type) {
        return tracks.containsType(type) && !tracks.isTypeSupported(type, true);
    }

    private void fail() {
        state.failed = true;
        player.stop();
        showError();
    }

    private void showError() {
        controls.showError();
        releaseMediaSession();
    }

    private void releaseMediaSession() {
        if (mediaSession == null) return;
        mediaSession.release();
        mediaSession = null;
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Home can be followed by a launcher relaunch before onStop delivers the result.
        if (!isFinishing()) finish();
    }

    @Override
    protected void onPause() {
        checkpoint.stopCaching();
        releaseMediaSession();
        if (player != null) {
            state.autoplay = player.getPlayWhenReady();
            player.pause();
            if (!isFinishing() && checkpoint.owned()) checkpoint.store(state.result());
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (player != null) {
            state.detach();
            controls.attach(null);
            player.release();
            player = null;
        }
        super.onStop();
        // Other ways of leaving the player may stop it without a user leave hint.
        if (!isFinishing() && !isChangingConfigurations()) finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        state.save(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return handleExitKey(event) || super.dispatchKeyEvent(event);
    }

    private boolean handleExitKey(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MEDIA_STOP) {
            if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) finish();
            return true;
        }
        return false;
    }

    @Override
    public void finish() {
        Intent result = state.result();
        if (checkpoint.owned() && !isFinishing()) {
            checkpoint.store(result);
            Bundle content = getIntent().getBundleExtra("checkpointContent");
            if (content != null) {
                new NativePlaybackCheckpoints(this).saveResult(content, requestId, result);
            }
        }
        setResult(RESULT_OK, result);
        // Android can route media buttons to this session until onStop runs.
        releaseMediaSession();
        super.finish();
    }
}
