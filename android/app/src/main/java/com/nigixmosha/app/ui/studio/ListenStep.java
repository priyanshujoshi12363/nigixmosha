package com.nigixmosha.app.ui.studio;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.drawable.Animatable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.chip.Chip;
import com.nigixmosha.app.R;
import com.nigixmosha.app.databinding.ViewStepListenBinding;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.Lang;
import com.nigixmosha.app.engine.Texts;
import com.nigixmosha.app.engine.model.Types;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;
import com.nigixmosha.app.ui.Avatars;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.widget.WaveSeekView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ListenStep extends StepView implements AudioManager.OnAudioFocusChangeListener {
    private static final float[] RATES = {0.75f, 1f, 1.25f, 1.5f, 2f};

    private final ViewStepListenBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateTime();
            if (playing) handler.postDelayed(this, 60);
        }
    };
    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            if (b.coverLogo.getDrawable() instanceof Animatable && Ui.motionEnabled()) ((Animatable) b.coverLogo.getDrawable()).start();
            if (playing) handler.postDelayed(this, 3200);
        }
    };

    private MediaPlayer player;
    private String loadedUrl;
    private boolean prepared;
    private boolean playing;
    private boolean triedFallback;
    private float rate = 1f;
    private double time;
    private String shownSegment;
    private String currentProduceId;
    private String waveKey;
    private final Map<String, Integer> colors = new HashMap<>();

    public ListenStep(Context context, StudioHost host) {
        super(context, host);
        b = ViewStepListenBinding.inflate(LayoutInflater.from(context), this, true);
        audio = ContextCompat.getSystemService(context, AudioManager.class);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(this, handler).build();
        b.stopRecording.setOnClickListener(v -> host.jobs().cancelProduce());
        b.errorSettings.setOnClickListener(v -> host.openProfile());
        b.errorEngine.setOnClickListener(v -> host.store().setStep(StudioStore.Step.CAST));
        b.startRecording.setOnClickListener(v -> host.jobs().startProduce());
        b.castFirst.setOnClickListener(v -> host.store().setStep(StudioStore.Step.CAST));
        b.rerecord.setOnClickListener(v -> {
            stop();
            host.jobs().startProduce();
        });
        b.libraryStatus.setOnClickListener(v -> {
            if ("error".equals(host.store().state().audioSave.status)) host.sync().retryAudioUpload();
        });
        b.playPause.setOnClickListener(v -> toggle());
        b.back10.setOnClickListener(v -> seek(time - 10, true));
        b.forward10.setOnClickListener(v -> seek(time + 10, true));
        b.wave.setOnSeek((seconds, done) -> {
            time = seconds;
            updateTime();
            if (done) seek(seconds, false);
        });
        b.download.setOnClickListener(v -> download());
        b.producingGlow.setBreathing(true);
        buildRates();
    }

    private void buildRates() {
        String[] labels = {"0.75×", "1×", "1.25×", "1.5×", "2×"};
        for (int i = 0; i < RATES.length; i++) {
            float r = RATES[i];
            TextView t = new TextView(getContext());
            t.setText(labels[i]);
            t.setTextSize(11);
            t.setGravity(Gravity.CENTER);
            t.setFontFeatureSettings("tnum");
            int h = Ui.dpi(getContext(), 10);
            t.setPadding(h, Ui.dpi(getContext(), 5), h, Ui.dpi(getContext(), 5));
            t.setOnClickListener(v -> {
                rate = r;
                applyRate();
                bindRates();
            });
            b.rates.addView(t);
        }
        bindRates();
    }

    private void bindRates() {
        for (int i = 0; i < RATES.length; i++) {
            TextView t = (TextView) b.rates.getChildAt(i);
            boolean on = RATES[i] == rate;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(Ui.dp(getContext(), 999));
            bg.setColor(on ? 0xFFFFFFFF : 0);
            t.setBackground(bg);
            t.setTextColor(on ? ContextCompat.getColor(getContext(), R.color.night) : 0x99FFFFFF);
        }
    }

    @Override
    public void render() {
        StudioStore.State s = host.store().state();
        if (s.analysis == null) return;
        StudioJobs jobs = host.jobs();
        boolean producing = jobs.produceStatus == StudioJobs.Status.RUNNING;
        colors.clear();
        for (Types.CastCharacter c : s.analysis.characters) colors.put(c.id, Format.color(c.color, c.name));

        b.errorCard.setVisibility(!producing && jobs.produceStatus == StudioJobs.Status.ERROR && jobs.produceError != null ? View.VISIBLE : View.GONE);
        b.errorText.setText(jobs.produceError);

        show(b.producingCard, producing);
        if (producing) {
            stop();
            renderProducing(s, jobs);
            b.playerCard.setVisibility(View.GONE);
            b.infoBlock.setVisibility(View.GONE);
            b.readyCard.setVisibility(View.GONE);
            return;
        }
        currentProduceId = null;

        StudioStore.Output out = s.output;
        show(b.playerCard, out != null);
        b.infoBlock.setVisibility(out != null ? View.VISIBLE : View.GONE);
        show(b.readyCard, out == null);
        if (out != null) renderPlayer(s, out);
        else {
            stop();
            renderReady(s);
        }
    }

    private void show(View view, boolean visible) {
        boolean now = view.getVisibility() == View.VISIBLE;
        if (visible == now) return;
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && Ui.motionEnabled()) {
            view.setAlpha(0f);
            view.setTranslationY(Ui.dp(getContext(), 14));
            view.animate().alpha(1f).translationY(0f).setDuration(420).setInterpolator(Ui.DECELERATE).start();
        }
    }

    private void renderProducing(StudioStore.State s, StudioJobs jobs) {
        double pct = jobs.produceTotal == 0 ? 0 : jobs.produceDone / (double) jobs.produceTotal;
        b.percent.setText(Math.round(pct * 100) + "%");
        b.lineOf.setText(getContext().getString(R.string.line_of, jobs.produceDone, jobs.produceTotal));
        b.produceProgress.setProgressCompat((int) Math.max(20, pct * 1000), true);
        Types.Segment cur = jobs.produceCurrent;
        if (cur == null) return;
        Types.CastCharacter c = s.analysis.character(cur.speaker);
        int color = c != null ? Format.color(c.color, c.name) : Format.color(Catalog.get().narratorColor, "");
        String name = c != null ? c.name : getContext().getString(R.string.narrator);
        b.producingGlow.setColor(color);
        if (cur.id.equals(currentProduceId)) return;
        currentProduceId = cur.id;
        Runnable apply = () -> {
            Avatars.style(b.currentAvatar, Format.initial(name), color, 40, 0);
            b.currentName.setText(name);
            b.currentEmotion.setText(cap(cur.emotion));
            b.currentText.setText(cur.text);
            b.currentEq.setColor(color);
            b.currentEq.setPlaying(true);
            b.current.setVisibility(View.VISIBLE);
        };
        if (!Ui.motionEnabled() || b.current.getVisibility() != View.VISIBLE) {
            apply.run();
            return;
        }
        b.current.animate().alpha(0f).translationY(-Ui.dp(getContext(), 10)).setDuration(140).withEndAction(() -> {
            apply.run();
            b.current.setTranslationY(Ui.dp(getContext(), 12));
            b.current.animate().alpha(1f).translationY(0f).setDuration(240).setInterpolator(Ui.DECELERATE).start();
        }).start();
    }

    private void renderReady(StudioStore.State s) {
        int words = 0;
        for (Types.Segment seg : s.segments) words += Texts.wordCount(seg.text);
        b.readyBody.setText(getContext().getString(R.string.ready_body, s.analysis.characters.size() + 1, s.segments.size()));
        Types.ProviderMeta engine = s.castFor != null ? Catalog.get().provider(s.castFor.provider) : null;
        b.readyMeta.setText("≈ " + Format.clock(Math.round(words / 150.0 * 60000)) + "   ·   "
                + (engine != null ? engine.name : getContext().getString(R.string.no_engine)));
        List<Avatars.Person> people = new ArrayList<>();
        for (Types.CastCharacter c : s.analysis.characters) people.add(Avatars.person(c.name, c.color));
        Avatars.stack(b.readyAvatars, people, 9, 36, ContextCompat.getColor(getContext(), R.color.surface),
                Avatars.person(getContext().getString(R.string.narrator), Catalog.get().narratorColor));
        boolean ready = s.castFor != null && host.settings().isReady(s.castFor.provider);
        b.startRecording.setEnabled(ready);
        b.castFirst.setVisibility(s.castFor == null ? View.VISIBLE : View.GONE);
    }

    private void renderPlayer(StudioStore.State s, StudioStore.Output out) {
        Context c = getContext();
        b.bookTitle.setText(s.analysis.title);
        b.bookMeta.setText(Lang.languageName(s.analysis.language) + " · " + c.getResources().getQuantityString(R.plurals.voices,
                s.analysis.characters.size() + 1, s.analysis.characters.size() + 1));
        b.download.setText(out.remote != null ? R.string.download_mp3 : R.string.download_wav);

        String key = out.duration + ":" + out.peaks.size() + ":" + out.timeline.size() + ":" + colors.hashCode();
        if (!key.equals(waveKey)) {
            waveKey = key;
            b.wave.setData(out.peaks, out.duration, out.timeline, colors, Format.color(Catalog.get().narratorOnDark, ""));
            buildJumps(s);
        }
        b.total.setText(Format.clock(Math.round(out.duration * 1000)));

        String url = out.playableUrl();
        if (url != null && !url.equals(loadedUrl)) {
            releasePlayer();
            loadedUrl = url;
            triedFallback = false;
            time = 0;
            shownSegment = null;
            prepare(url);
        }
        updateTime();

        Types.ProviderMeta engine = Catalog.get().provider(out.provider);
        CharSequence when = DateUtils.formatDateTime(c, out.createdAt, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_MONTH);
        StringBuilder info = new StringBuilder(c.getString(R.string.recorded_with, engine.name, when));
        if (out.backup > 0) info.append("\n").append(c.getString(R.string.backup_lines, out.backup));
        if (out.failed > 0) info.append("\n").append(c.getString(R.string.failed_lines, out.failed));
        b.recordedWith.setText(info);
        bindLibraryStatus(s);
        b.rerecord.setEnabled(s.castFor != null && host.settings().isReady(s.castFor.provider));
    }

    private void bindLibraryStatus(StudioStore.State s) {
        Context c = getContext();
        StudioStore.AudioSave save = s.audioSave;
        boolean cloud = host.settings().server().storage.db && host.settings().server().storage.media;
        b.uploadProgress.setVisibility(View.GONE);
        int iconTint = R.color.text_tertiary;
        int icon = 0;
        String text;
        int color = R.color.text_tertiary;
        if (!cloud) {
            text = c.getString(R.string.cloud_off);
        } else if ("uploading".equals(save.status)) {
            text = c.getString(R.string.saving_history, Math.round(save.progress * 100));
            icon = R.drawable.ic_cloud_upload;
            iconTint = R.color.violet;
            color = R.color.text_secondary;
            b.uploadProgress.setVisibility(View.VISIBLE);
            b.uploadProgress.setProgressCompat((int) Math.round(save.progress * 1000), true);
        } else if ("saved".equals(save.status)) {
            text = c.getString(R.string.saved_history);
            icon = R.drawable.ic_cloud_done;
            iconTint = R.color.accent;
            color = R.color.text_secondary;
        } else if ("error".equals(save.status)) {
            text = c.getString(R.string.save_failed_retry, save.error == null ? "" : save.error);
            icon = R.drawable.ic_warning;
            iconTint = R.color.error;
            color = R.color.error;
        } else {
            text = "";
        }
        b.libraryStatus.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        b.libraryStatus.setText(text);
        b.libraryStatus.setTextColor(ContextCompat.getColor(c, color));
        b.libraryStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(b.libraryStatus, ColorStateList.valueOf(ContextCompat.getColor(c, iconTint)));
    }

    private void buildJumps(StudioStore.State s) {
        b.jumps.removeAllViews();
        addJump(Types.NARRATOR_ID, getContext().getString(R.string.narrator), Format.color(Catalog.get().narratorOnDark, ""));
        for (Types.CastCharacter c : s.analysis.characters) addJump(c.id, c.name, Format.color(c.color, c.name));
    }

    private void addJump(String id, String name, int color) {
        Chip chip = new Chip(getContext());
        chip.setTag(id);
        chip.setText(name);
        chip.setTextSize(12);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setChipMinHeight(Ui.dp(getContext(), 30));
        chip.setChipBackgroundColor(ColorStateList.valueOf(0));
        chip.setChipStrokeWidth(Ui.dp(getContext(), 1));
        chip.setChipStrokeColor(ColorStateList.valueOf(0x1AFFFFFF));
        chip.setTextColor(0x99FFFFFF);
        android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color);
        int size = Ui.dpi(getContext(), 8);
        dot.setSize(size, size);
        chip.setChipIcon(dot);
        chip.setChipIconSize(size);
        chip.setOnClickListener(v -> jumpTo(id));
        b.jumps.addView(chip);
    }

    private void highlightJump(String speaker) {
        for (int i = 0; i < b.jumps.getChildCount(); i++) {
            Chip chip = (Chip) b.jumps.getChildAt(i);
            boolean on = speaker != null && speaker.equals(chip.getTag());
            chip.setChipBackgroundColor(ColorStateList.valueOf(on ? 0x26FFFFFF : 0));
            chip.setChipStrokeColor(ColorStateList.valueOf(on ? 0x66FFFFFF : 0x1AFFFFFF));
            chip.setTextColor(on ? 0xFFFFFFFF : 0x99FFFFFF);
        }
    }

    private void jumpTo(String speaker) {
        StudioStore.Output out = host.store().state().output;
        if (out == null) return;
        Types.TimelineEntry next = null;
        for (Types.TimelineEntry e : out.timeline) {
            if (e.speaker.equals(speaker) && e.start > time + 0.2) {
                next = e;
                break;
            }
        }
        if (next == null) {
            for (Types.TimelineEntry e : out.timeline) if (e.speaker.equals(speaker)) {
                next = e;
                break;
            }
        }
        if (next == null) return;
        seek(next.start, false);
        if (!playing) play();
    }

    private void updateTime() {
        StudioStore.State s = host.store().state();
        StudioStore.Output out = s.output;
        if (out == null || s.analysis == null) return;
        if (player != null && prepared && playing) {
            try {
                time = player.getCurrentPosition() / 1000.0;
            } catch (IllegalStateException ignored) {
            }
        }
        b.wave.setTime(time);
        b.elapsed.setText(Format.clock(Math.round(time * 1000)));
        int idx = WaveSeekView.find(out.timeline, time);
        Types.TimelineEntry entry = idx >= 0 ? out.timeline.get(idx) : null;
        Types.Segment seg = null;
        if (entry != null) for (Types.Segment g : s.segments) if (g.id.equals(entry.segmentId)) {
            seg = g;
            break;
        }
        String segId = seg != null ? seg.id : "idle";
        String speaker = entry != null ? entry.speaker : null;
        Types.CastCharacter character = speaker != null ? s.analysis.character(speaker) : null;
        int color = character != null ? Format.color(character.color, character.name) : Format.color(Catalog.get().narratorColor, "");
        b.playerGlow.setColor(color);
        highlightJump(speaker);
        if (segId.equals(shownSegment)) return;
        shownSegment = segId;
        String name = character != null ? character.name : getContext().getString(R.string.narrator);
        Types.Segment finalSeg = seg;
        StringBuilder up = new StringBuilder();
        if (idx >= 0) {
            for (int k = idx + 1; k < Math.min(out.timeline.size(), idx + 3); k++) {
                for (Types.Segment g : s.segments) if (g.id.equals(out.timeline.get(k).segmentId)) {
                    if (up.length() > 0) up.append('\n');
                    up.append(g.text);
                    break;
                }
            }
        }
        Runnable apply = () -> {
            Avatars.style(b.speakingAvatar, Format.initial(name), color, 36, 0);
            b.speakingName.setText(name);
            b.speakingEmotion.setVisibility(finalSeg != null ? View.VISIBLE : View.GONE);
            if (finalSeg != null) b.speakingEmotion.setText(cap(finalSeg.emotion));
            b.speakingText.setText(finalSeg != null ? finalSeg.text : getContext().getString(R.string.press_play));
            b.upcoming.setText(up);
            b.upcoming.setVisibility(up.length() > 0 ? View.VISIBLE : View.GONE);
        };
        if (!Ui.motionEnabled()) {
            apply.run();
            return;
        }
        b.speakingText.animate().cancel();
        b.speakingText.animate().alpha(0f).translationY(-Ui.dp(getContext(), 8)).setDuration(120).withEndAction(() -> {
            apply.run();
            b.speakingText.setTranslationY(Ui.dp(getContext(), 12));
            b.speakingText.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Ui.DECELERATE).start();
        }).start();
    }

    private void prepare(String url) {
        MediaPlayer mp = new MediaPlayer();
        player = mp;
        prepared = false;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(url);
            mp.setOnPreparedListener(m -> {
                if (m != player) return;
                prepared = true;
                if (time > 0) m.seekTo((int) Math.round(time * 1000));
            });
            mp.setOnCompletionListener(m -> {
                if (m != player) return;
                setPlaying(false);
                time = 0;
                try {
                    m.seekTo(0);
                } catch (IllegalStateException ignored) {
                }
                updateTime();
            });
            mp.setOnErrorListener((m, what, extra) -> {
                if (m != player) return true;
                StudioStore.Output out = host.store().state().output;
                if (playing && out != null && time >= out.duration - 2) {
                    setPlaying(false);
                    time = 0;
                    updateTime();
                    return true;
                }
                if (!triedFallback && out != null && out.remote != null && out.remote.url != null && !out.remote.url.equals(loadedUrl)) {
                    triedFallback = true;
                    boolean resume = playing;
                    releasePlayer();
                    loadedUrl = out.remote.url;
                    prepare(out.remote.url);
                    if (resume) handler.postDelayed(this::play, 400);
                    return true;
                }
                setPlaying(false);
                host.snackbar(getContext().getString(R.string.player_error));
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            host.snackbar(getContext().getString(R.string.player_error));
        }
    }

    private void toggle() {
        if (playing) pause();
        else play();
    }

    private void play() {
        if (player == null) {
            String url = host.store().state().output != null ? host.store().state().output.playableUrl() : null;
            if (url == null) return;
            loadedUrl = url;
            prepare(url);
        }
        if (!prepared) {
            player.setOnPreparedListener(m -> {
                prepared = true;
                if (time > 0) m.seekTo((int) Math.round(time * 1000));
                play();
            });
            setPlaying(true);
            return;
        }
        if (audio != null && audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
        try {
            player.start();
            applyRate();
            setPlaying(true);
        } catch (IllegalStateException e) {
            setPlaying(false);
        }
    }

    private void pause() {
        if (player != null && prepared) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
            }
        }
        setPlaying(false);
    }

    private void applyRate() {
        if (player == null || !prepared || !playing) return;
        try {
            PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(rate);
            player.setPlaybackParams(params);
        } catch (Exception ignored) {
        }
    }

    private void seek(double seconds, boolean relative) {
        StudioStore.Output out = host.store().state().output;
        if (out == null) return;
        time = Math.max(0, Math.min(out.duration, seconds));
        if (player != null && prepared) {
            try {
                player.seekTo((int) Math.round(time * 1000));
            } catch (IllegalStateException ignored) {
            }
        }
        shownSegment = null;
        updateTime();
    }

    private void setPlaying(boolean on) {
        playing = on;
        b.playIcon.setImageResource(on ? R.drawable.ic_pause : R.drawable.ic_play);
        b.playPause.setContentDescription(getContext().getString(on ? R.string.pause : R.string.play));
        b.coverEq.setPlaying(on);
        b.playerGlow.setBreathing(on);
        handler.removeCallbacks(tick);
        handler.removeCallbacks(blink);
        if (on) {
            handler.post(tick);
            handler.post(blink);
        } else if (audio != null) {
            audio.abandonAudioFocusRequest(focus);
        }
    }

    private void stop() {
        if (playing) pause();
    }

    private void releasePlayer() {
        handler.removeCallbacks(tick);
        if (player != null) {
            MediaPlayer old = player;
            player = null;
            prepared = false;
            try {
                old.release();
            } catch (Exception ignored) {
            }
        }
        if (playing) setPlaying(false);
    }

    @Override
    public void onAudioFocusChange(int change) {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause();
        else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK && player != null) player.setVolume(0.25f, 0.25f);
        else if (change == AudioManager.AUDIOFOCUS_GAIN && player != null) player.setVolume(1f, 1f);
    }

    private void download() {
        StudioStore.State s = host.store().state();
        StudioStore.Output out = s.output;
        if (out == null || s.analysis == null) return;
        String base = slug(s.analysis.title);
        Context c = getContext();
        if (out.remote != null && out.remote.downloadUrl != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                DownloadManager dm = ContextCompat.getSystemService(c, DownloadManager.class);
                if (dm != null) {
                    dm.enqueue(new DownloadManager.Request(Uri.parse(out.remote.downloadUrl))
                            .setTitle(s.analysis.title)
                            .setMimeType("audio/mpeg")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, base + ".mp3"));
                    host.snackbar(c.getString(R.string.downloading, base + ".mp3"));
                    return;
                }
            }
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(out.remote.downloadUrl)));
            } catch (Exception e) {
                host.snackbar(c.getString(R.string.no_browser));
            }
            return;
        }
        if (out.localPath == null) return;
        File file = new File(out.localPath);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            host.api().run(() -> {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, base + ".wav");
                values.put(MediaStore.Downloads.MIME_TYPE, "audio/wav");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new java.io.IOException("insert failed");
                try (InputStream in = new FileInputStream(file); OutputStream o = c.getContentResolver().openOutputStream(uri)) {
                    if (o == null) throw new java.io.IOException("open failed");
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) != -1) o.write(buf, 0, n);
                }
                return base + ".wav";
            }, new com.nigixmosha.app.data.ApiClient.Callback<String>() {
                @Override
                public void onSuccess(String name) {
                    host.snackbar(c.getString(R.string.saved_to_downloads, name));
                }

                @Override
                public void onError(com.nigixmosha.app.data.ApiException error) {
                    host.snackbar(c.getString(R.string.save_failed));
                }
            });
            return;
        }
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".files", file);
        Intent share = new Intent(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        c.startActivity(Intent.createChooser(share, c.getString(R.string.download_wav)));
    }

    private static String slug(String s) {
        String out = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        if (out.length() > 60) out = out.substring(0, 60);
        return out.isEmpty() ? "audiobook" : out;
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    @Override
    public void onHidden() {
        stop();
    }

    @Override
    public void onInsets() {
        b.content.setPadding(b.content.getPaddingLeft(), b.content.getPaddingTop(), b.content.getPaddingRight(),
                Ui.dpi(getContext(), 32) + host.bottomInset());
    }

    @Override
    public void release() {
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        loadedUrl = null;
    }
}
