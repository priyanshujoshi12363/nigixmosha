package com.nigixmosha.app.engine;

import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class Mixer {
    public interface Source {
        short[] clip(String tag, int nth) throws Exception;
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    private static final double SCENE_LEAD = 0.6;
    private static final double SCENE_TAIL = 1.2;
    private static final double FADE = 1.1;
    private static final double LOOP_FADE = 0.9;

    private static final class Level {
        final double bed;
        final double shot;
        final float duck;

        Level(double bed, double shot, float duck) {
            this.bed = bed;
            this.shot = shot;
            this.duck = duck;
        }
    }

    private static final double BED_RMS = 0.1;
    private static final double SHOT_PEAK = 0.8;

    private static Level level(String name) {
        if ("cinematic".equals(name)) return new Level(-19, -7, 0.38f);
        return new Level(-25, -12, 0.45f);
    }

    static short[] levelClip(short[] samples, boolean bed) {
        if (samples == null || samples.length == 0) return samples;
        double sum = 0;
        int peak = 0;
        for (short s : samples) {
            double v = s / 32768.0;
            sum += v * v;
            int a = Math.abs(s);
            if (a > peak) peak = a;
        }
        if (peak == 0) return samples;
        double rms = Math.sqrt(sum / samples.length);
        double target = bed ? (rms > 0 ? BED_RMS / rms : 1) : SHOT_PEAK / (peak / 32768.0);
        double gain = Math.max(0.05, Math.min(24, target));
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(samples[i] * gain)));
        }
        return out;
    }

    private static final class Event {
        long start;
        long end;
        short[] clip;
        float gain;
        boolean loop;
    }

    private final List<Event> events = new ArrayList<>();
    private final float duckDepth;
    private final Set<String> used = new LinkedHashSet<>();
    private final Set<String> skipped = new LinkedHashSet<>();
    private float smooth;
    private int cursorEvent;

    private Mixer(float duckDepth) {
        this.duckDepth = duckDepth;
    }

    public static boolean enabled(String level, Types.Soundscape plan) {
        return level != null && !"off".equals(level) && plan != null && !plan.isEmpty() && !Sounds.get().isEmpty();
    }

    private static double db(double value) {
        return Math.pow(10, value / 20);
    }

    private static long samples(double seconds) {
        return Math.round(seconds * AudioCodec.RATE);
    }

    public static Mixer build(Types.Soundscape plan, String levelName, double[] starts, double[] ends,
                              Source source, Progress progress, Cancel cancel) throws Exception {
        Level level = level(levelName);
        Mixer mixer = new Mixer(level.duck);
        Sounds sounds = Sounds.get();
        int total = plan.scenes.size() + plan.cues.size();
        int done = 0;
        int nth = 0;

        for (Types.SoundScene scene : plan.scenes) {
            cancel.check();
            Types.SoundTag tag = sounds.tag(scene.tag);
            short[] raw = tag == null ? null : safeClip(source, scene.tag, nth++, mixer, scene.tag);
            short[] clip = raw == null ? null : levelClip(raw, true);
            if (tag != null && clip != null && clip.length > AudioCodec.RATE) {
                Event e = new Event();
                e.start = Math.max(0, samples(at(starts, scene.from) - SCENE_LEAD));
                e.end = samples(at(ends, scene.to) + SCENE_TAIL);
                e.clip = clip;
                e.loop = true;
                e.gain = (float) (db(level.bed + tag.gain + 22) * (0.45 + 0.55 * scene.intensity));
                if (e.end > e.start) {
                    mixer.events.add(e);
                    mixer.used.add(scene.tag);
                }
            } else if (clip == null) {
                mixer.skipped.add(scene.tag);
            }
            if (progress != null) progress.onProgress(++done, total);
        }

        for (Types.SoundCue cue : plan.cues) {
            cancel.check();
            Types.SoundTag tag = sounds.tag(cue.tag);
            short[] raw = tag == null ? null : safeClip(source, cue.tag, nth++, mixer, cue.tag);
            short[] clip = raw == null ? null : levelClip(raw, false);
            if (tag != null && clip != null && clip.length > 0) {
                double start = at(starts, cue.at);
                double end = at(ends, cue.at);
                Event e = new Event();
                if ("before".equals(cue.placement)) e.start = samples(start - 0.18) - clip.length;
                else if ("after".equals(cue.placement)) e.start = samples(end + 0.12);
                else e.start = samples(start + Math.min(0.5, (end - start) * 0.3));
                e.start = Math.max(0, e.start);
                e.end = e.start + clip.length;
                e.clip = clip;
                e.loop = false;
                e.gain = (float) (db(level.shot + tag.gain + 8) * cue.gain);
                mixer.events.add(e);
                mixer.used.add(cue.tag);
            } else if (clip == null) {
                mixer.skipped.add(cue.tag);
            }
            if (progress != null) progress.onProgress(++done, total);
        }

        mixer.events.sort((a, b) -> Long.compare(a.start, b.start));
        return mixer;
    }

    private static short[] safeClip(Source source, String tag, int nth, Mixer mixer, String label) {
        try {
            return source.clip(tag, nth);
        } catch (Exception e) {
            mixer.skipped.add(label);
            return null;
        }
    }

    private static double at(double[] values, int index) {
        if (values.length == 0) return 0;
        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    public boolean hasWork() {
        return !events.isEmpty();
    }

    public List<String> used() {
        return new ArrayList<>(used);
    }

    public List<String> skipped() {
        return new ArrayList<>(skipped);
    }

    private static float loopSample(short[] clip, long local, int fade) {
        int step = clip.length - fade;
        if (step <= 0) return 0;
        long iteration = local / step;
        int pos = (int) (local - iteration * step);
        float value = clip[pos] * (iteration > 0 && pos < fade ? pos / (float) fade : 1f);
        int tail = pos + step;
        if (pos < fade && tail < clip.length) value += clip[tail] * ((fade - pos) / (float) fade);
        return value;
    }

    public void process(short[] chunk, long position) {
        if (chunk.length == 0) return;
        long chunkEnd = position + chunk.length;
        while (cursorEvent < events.size() && events.get(cursorEvent).end <= position) cursorEvent++;

        int window = (int) samples(0.03);
        float[] duck = new float[chunk.length];
        for (int base = 0; base < chunk.length; base += window) {
            int to = Math.min(chunk.length, base + window);
            float peak = 0;
            for (int i = base; i < to; i += 3) {
                float a = Math.abs(chunk[i] / 32768f);
                if (a > peak) peak = a;
            }
            float target = Math.min(1f, peak / 0.12f);
            smooth += (target - smooth) * (target > smooth ? 0.55f : 0.12f);
            float value = 1f - duckDepth * Math.min(1f, smooth);
            for (int i = base; i < to; i++) duck[i] = value;
        }

        for (int index = cursorEvent; index < events.size(); index++) {
            Event e = events.get(index);
            if (e.start >= chunkEnd) break;
            if (e.end <= position) continue;
            long from = Math.max(e.start, position);
            long to = Math.min(e.end, chunkEnd);
            int fade = (int) Math.min(samples(LOOP_FADE), e.clip.length / 3);
            long span = e.end - e.start;
            long fadeEdge = Math.min(samples(FADE), span / 2);
            for (long at = from; at < to; at++) {
                long local = at - e.start;
                float value = e.loop ? loopSample(e.clip, local, fade) : e.clip[(int) local];
                float gain = e.gain;
                if (e.loop && fadeEdge > 0) {
                    long left = e.end - at;
                    if (local < fadeEdge) gain *= local / (float) fadeEdge;
                    if (left < fadeEdge) gain *= left / (float) fadeEdge;
                }
                int i = (int) (at - position);
                float ducked = e.loop ? duck[i] : 0.5f + 0.5f * duck[i];
                int mixed = chunk[i] + Math.round(value * gain * ducked);
                chunk[i] = (short) Math.max(-31785, Math.min(31785, mixed));
            }
        }
    }
}
