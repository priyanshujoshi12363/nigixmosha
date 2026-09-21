package com.nigixmosha.app.ui.library;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import com.nigixmosha.app.data.model.Project;

import java.io.IOException;

final class PlayerController implements AudioManager.OnAudioFocusChangeListener {
    enum State { IDLE, PREPARING, PLAYING, PAUSED, ERROR }

    interface Listener {
        void onPlayerState(Project project, State state);

        void onPlayerProgress(long positionMs, long durationMs);
    }

    private static final long TICK_MS = 250;
    private static final long RETRY_DELAY_MS = 1500;
    private static final int MAX_RETRIES = 1;
    private static final long END_TOLERANCE_MS = 2000;

    private final Context context;
    private final Listener listener;
    private final AudioManager audio;
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            publishProgress();
            handler.postDelayed(this, TICK_MS);
        }
    };
    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) pause();
        }
    };

    private MediaPlayer player;
    private Project current;
    private State state = State.IDLE;
    private boolean noisyRegistered;
    private int retries;
    private long durationMs;
    private long positionMs;

    PlayerController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        this.audio = ContextCompat.getSystemService(this.context, AudioManager.class);
        this.attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        this.focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener(this, handler)
                .setWillPauseWhenDucked(false)
                .build();
    }

    Project current() {
        return current;
    }

    State state() {
        return state;
    }

    void toggle(Project project) {
        if (current != null && current.id.equals(project.id)) {
            if (state == State.PLAYING) pause();
            else if (state == State.PAUSED) resume();
            else if (state != State.PREPARING) play(project);
            return;
        }
        play(project);
    }

    void togglePlayback() {
        if (current != null) toggle(current);
    }

    void play(Project project) {
        retries = 0;
        open(project);
    }

    private void open(Project project) {
        releasePlayer();
        current = project;
        String url = project.audioUrl();
        if (url == null) {
            setState(State.ERROR);
            return;
        }
        durationMs = project.durationMs();
        positionMs = 0;
        setState(State.PREPARING);
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setAudioAttributes(attributes);
        mp.setOnPreparedListener(prepared -> {
            if (prepared != player) return;
            int reported = prepared.getDuration();
            if (reported > 0) durationMs = reported;
            if (!requestFocus()) {
                setState(State.PAUSED);
                return;
            }
            prepared.start();
            setState(State.PLAYING);
        });
        mp.setOnCompletionListener(done -> {
            if (done == player) finish();
        });
        mp.setOnErrorListener((failed, what, extra) -> {
            if (failed == player) fail();
            return true;
        });
        try {
            mp.setDataSource(url);
            mp.prepareAsync();
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            fail();
        }
    }

    private void finish() {
        releasePlayer();
        positionMs = 0;
        setState(State.PAUSED);
        listener.onPlayerProgress(0, durationMs);
    }

    private void fail() {
        if (state == State.PLAYING && durationMs > 0 && positionMs >= durationMs - END_TOLERANCE_MS) {
            finish();
            return;
        }
        Project project = current;
        if (project != null && state == State.PREPARING && retries < MAX_RETRIES) {
            retries++;
            releasePlayer();
            handler.postDelayed(() -> {
                if (current == project && state == State.PREPARING) open(project);
            }, RETRY_DELAY_MS);
            return;
        }
        releasePlayer();
        setState(State.ERROR);
    }

    void pause() {
        if (player != null && state == State.PLAYING) {
            player.pause();
            setState(State.PAUSED);
        }
    }

    void resume() {
        if (player == null && current != null && state == State.PAUSED) {
            play(current);
            return;
        }
        if (player != null && state == State.PAUSED && requestFocus()) {
            player.start();
            setState(State.PLAYING);
        }
    }

    void stop() {
        releasePlayer();
        current = null;
        setState(State.IDLE);
    }

    void release() {
        releasePlayer();
        current = null;
        state = State.IDLE;
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (player == null) return;
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                player.setVolume(0.25f, 0.25f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                player.setVolume(1f, 1f);
                break;
            default:
                break;
        }
    }

    private void setState(State next) {
        state = next;
        handler.removeCallbacks(ticker);
        if (next == State.PLAYING) {
            registerNoisy();
            handler.post(ticker);
        } else {
            unregisterNoisy();
        }
        if (next == State.IDLE || next == State.ERROR) abandonFocus();
        listener.onPlayerState(current, next);
    }

    private void publishProgress() {
        if (player == null || current == null || (state != State.PLAYING && state != State.PAUSED)) return;
        try {
            positionMs = player.getCurrentPosition();
            listener.onPlayerProgress(positionMs, durationMs);
        } catch (IllegalStateException ignored) {
        }
    }

    private boolean requestFocus() {
        return audio == null || audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonFocus() {
        if (audio != null) audio.abandonAudioFocusRequest(focusRequest);
    }

    private void registerNoisy() {
        if (noisyRegistered) return;
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisy, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(noisy, filter);
        }
        noisyRegistered = true;
    }

    private void unregisterNoisy() {
        if (!noisyRegistered) return;
        try {
            context.unregisterReceiver(noisy);
        } catch (IllegalArgumentException ignored) {
        }
        noisyRegistered = false;
    }

    private void releasePlayer() {
        handler.removeCallbacks(ticker);
        unregisterNoisy();
        abandonFocus();
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            old.setOnPreparedListener(null);
            old.setOnCompletionListener(null);
            old.setOnErrorListener(null);
            old.release();
        }
    }
}
