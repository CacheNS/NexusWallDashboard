package com.cachens.nexusdashboard;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class RadioController {
    static final String STREAM_URL = "https://stream.blokradio.com/hls/blok_radio/live.m3u8";
    enum State { STOPPED, BUFFERING, PLAYING }

    interface Listener {
        void onStateChanged(State state);
        void onError();
    }

    private final Context context;
    private final Listener listener;
    private final String streamUrl;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ExoPlayer player;
    private State state = State.STOPPED;
    private boolean released;
    private final Runnable bufferingTimeout = new Runnable() {
        @Override
        public void run() {
            if (state == State.BUFFERING) fail();
        }
    };

    RadioController(Context context, Listener listener) {
        this(context, listener, STREAM_URL);
    }

    RadioController(Context context, Listener listener, String streamUrl) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.streamUrl = streamUrl;
    }

    State getState() {
        return state;
    }

    void toggle() {
        if (released) return;
        if (player != null) {
            stop();
            return;
        }
        try {
            LegacyTls.initialize(context);
            final ExoPlayer current = new ExoPlayer.Builder(context).build();
            player = current;
            current.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true);
            current.setHandleAudioBecomingNoisy(true);
            current.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    if (player != current) return;
                    if (playbackState == Player.STATE_ENDED) {
                        stop();
                    } else if (playbackState == Player.STATE_READY) {
                        setState(State.PLAYING);
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        setState(State.BUFFERING);
                    }
                }

                @Override
                public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
                    if (player == current && !playWhenReady) stop();
                }

                @Override
                public void onPlaybackSuppressionReasonChanged(int reason) {
                    if (player == current && reason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) stop();
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    if (player != current) return;
                    Log.w("BlokRadio", "Playback failed", error);
                    fail();
                }
            });
            DefaultHttpDataSource.Factory dataSource = new DefaultHttpDataSource.Factory()
                    .setUserAgent("NexusWallDashboard")
                    .setConnectTimeoutMs(10000).setReadTimeoutMs(10000);
            MediaItem item = new MediaItem.Builder().setUri(streamUrl)
                    .setMimeType(MimeTypes.APPLICATION_M3U8).build();
            current.setMediaSource(new HlsMediaSource.Factory(dataSource).createMediaSource(item));
            setState(State.BUFFERING);
            current.prepare();
            current.play();
        } catch (RuntimeException error) {
            Log.w("BlokRadio", "Unable to start playback", error);
            fail();
        }
    }

    void stop() {
        ExoPlayer previous = player;
        player = null;
        if (previous != null) previous.release();
        setState(State.STOPPED);
    }

    void release() {
        released = true;
        stop();
    }

    private void fail() {
        stop();
        listener.onError();
    }

    private void setState(State value) {
        if (state == value) return;
        handler.removeCallbacks(bufferingTimeout);
        state = value;
        if (value == State.BUFFERING) handler.postDelayed(bufferingTimeout, 30000L);
        listener.onStateChanged(value);
    }
}