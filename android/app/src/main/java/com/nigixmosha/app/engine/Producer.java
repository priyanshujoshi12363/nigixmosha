package com.nigixmosha.app.engine;

import com.google.gson.Gson;
import com.nigixmosha.app.engine.model.Types;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public final class Producer {
    public interface Speech {
        byte[] speak(String provider, Types.ProviderConfig config, String voice, String text, String lang,
                     Types.SynthesisStyle style, Cancel cancel) throws Exception;
    }

    public interface Progress {
        void onProgress(int done, int total, Types.Segment current);
    }

    public static final class Context {
        public Types.Analysis analysis;
        public Map<String, Types.CastEntry> cast;
        public boolean shareNarrator;
        public String provider;
        public Types.ProviderConfig config;
    }

    public static final class Output {
        public File file;
        public double duration;
        public List<Types.TimelineEntry> timeline = new ArrayList<>();
        public List<Double> peaks = new ArrayList<>();
        public List<String> failed = new ArrayList<>();
        public List<String> backup = new ArrayList<>();
        public List<String> sounds = new ArrayList<>();
    }

    private static final long CACHE_BUDGET = 48L * 1024 * 1024;
    private static final Set<Integer> FATAL = new HashSet<>(Arrays.asList(400, 401, 402, 403));
    private static final Gson GSON = new Gson();
    private static final Map<String, short[]> CACHE = new LinkedHashMap<>(64, 0.75f, true);
    private static long cacheBytes;

    private Producer() {
    }

    private static short[] cached(String key) {
        synchronized (CACHE) {
            return CACHE.get(key);
        }
    }

    private static void remember(String key, short[] clip) {
        synchronized (CACHE) {
            short[] prev = CACHE.put(key, clip);
            if (prev != null) cacheBytes -= prev.length * 2L;
            cacheBytes += clip.length * 2L;
            java.util.Iterator<Map.Entry<String, short[]>> it = CACHE.entrySet().iterator();
            while (cacheBytes > CACHE_BUDGET && it.hasNext()) {
                Map.Entry<String, short[]> e = it.next();
                if (e.getKey().equals(key)) continue;
                cacheBytes -= e.getValue().length * 2L;
                it.remove();
            }
        }
    }

    private static boolean isFatal(Throwable e) {
        return e instanceof Cancel.Cancelled || FATAL.contains(Util.status(e));
    }

    public static short[] renderSegment(Types.Segment seg, Context ctx, Speech speech, Cancel cancel) throws Exception {
        Types.ProviderMeta meta = Catalog.get().provider(ctx.provider);
        Types.CastEntry entry = Casting.castFor(seg.speaker, ctx.cast, ctx.analysis, ctx.shareNarrator);
        if (entry == null) throw new IllegalStateException("No voice cast for this speaker");
        Types.SynthesisStyle style = Direction.styleFor(seg, ctx.analysis, entry);
        String lang = seg.lang != null ? seg.lang : ctx.analysis.language;
        String key = GSON.toJson(Arrays.asList(ctx.provider, ctx.config.model, ctx.config.baseUrl, entry.voiceId,
                Math.round(style.pitch), Math.round(style.rate), Math.round(style.volume), style.emotion,
                meta.instructable ? style.instructions : "", lang, seg.text));
        short[] hit = cached(key);
        if (hit != null) return hit;
        List<short[]> parts = new ArrayList<>();
        for (String piece : Texts.splitForTTS(seg.text, meta.maxChars)) {
            byte[] audio = Util.withRetry(() -> speech.speak(ctx.provider, ctx.config, entry.voiceId, piece, lang, style, cancel), 3, cancel);
            parts.add(AudioCodec.decode(audio));
        }
        short[] out = AudioCodec.concat(parts);
        remember(key, out);
        return out;
    }

    public static File renderPreview(Types.Segment seg, Context ctx, Speech speech, Cancel cancel, File dir) throws Exception {
        short[] samples = AudioCodec.normalizeLoudness(AudioCodec.trimSilence(renderSegment(seg, ctx, speech, cancel)));
        File file = File.createTempFile("preview-", ".wav", dir);
        AudioCodec.writeWav(file, samples);
        return file;
    }

    private static Context backupContext(Context ctx) {
        Catalog catalog = Catalog.get();
        String lang = ctx.analysis.language;
        Map<String, Types.CastEntry> cast = new LinkedHashMap<>();
        Types.CastEntry narrator = ctx.cast.get(Types.NARRATOR_ID);
        cast.put(Types.NARRATOR_ID, new Types.CastEntry(
                catalog.backupVoice(ctx.analysis.narrator.gender, lang, Types.NARRATOR_ID),
                narrator != null ? narrator.pitch : 0, narrator != null ? narrator.rate : 0));
        for (Types.CastCharacter c : ctx.analysis.characters) {
            Types.CastEntry own = ctx.cast.get(c.id);
            cast.put(c.id, new Types.CastEntry(catalog.backupVoice(c.gender, lang, c.id + c.name),
                    own != null ? own.pitch : 0, own != null ? own.rate : 0));
        }
        Context b = new Context();
        b.analysis = ctx.analysis;
        b.cast = cast;
        b.shareNarrator = ctx.shareNarrator;
        b.provider = "edge";
        b.config = new Types.ProviderConfig();
        b.config.model = "neural";
        return b;
    }

    public static Output produce(List<Types.Segment> segments, Context ctx, Types.Advanced advanced, Speech speech,
                                 Progress progress, Cancel parent, File dir) throws Exception {
        return produce(segments, ctx, advanced, speech, progress, parent, dir, null, "off", null, null);
    }

    public static Output produce(List<Types.Segment> segments, Context ctx, Types.Advanced advanced, Speech speech,
                                 Progress progress, Cancel parent, File dir, Types.Soundscape plan, String soundLevel,
                                 Mixer.Source soundSource, Mixer.Progress mixProgress) throws Exception {
        Types.ProviderMeta meta = Catalog.get().provider(ctx.provider);
        Cancel cancel = parent.child();
        int limit = Math.max(1, Math.min(meta.concurrency, advanced.ttsConcurrency));
        int total = segments.size();
        short[][] clips = new short[total][];
        List<String> failed = Collections.synchronizedList(new ArrayList<>());
        List<String> backup = Collections.synchronizedList(new ArrayList<>());
        Context[] backupCtx = {null};
        Exception[] firstError = {null};
        AtomicInteger done = new AtomicInteger();
        AtomicInteger next = new AtomicInteger();
        int lanes = Math.max(1, Math.min(limit, total));
        ExecutorService pool = Executors.newFixedThreadPool(lanes);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int lane = 0; lane < lanes; lane++) {
                futures.add(pool.submit(() -> {
                    while (true) {
                        cancel.check();
                        int i = next.getAndIncrement();
                        if (i >= total) return null;
                        Types.Segment seg = segments.get(i);
                        short[] samples = null;
                        try {
                            samples = renderSegment(seg, ctx, speech, cancel);
                        } catch (Exception err) {
                            if (isFatal(err)) {
                                cancel.cancel();
                                throw err;
                            }
                            if ("edge".equals(ctx.provider)) {
                                synchronized (firstError) {
                                    if (firstError[0] == null) firstError[0] = err;
                                }
                                failed.add(seg.id);
                            } else {
                                Context b;
                                synchronized (backupCtx) {
                                    if (backupCtx[0] == null) backupCtx[0] = backupContext(ctx);
                                    b = backupCtx[0];
                                }
                                try {
                                    samples = renderSegment(seg, b, speech, cancel);
                                    backup.add(seg.id);
                                } catch (Exception backupErr) {
                                    if (isFatal(backupErr)) {
                                        cancel.cancel();
                                        throw backupErr;
                                    }
                                    synchronized (firstError) {
                                        if (firstError[0] == null) firstError[0] = err;
                                    }
                                    failed.add(seg.id);
                                }
                            }
                        }
                        clips[i] = samples;
                        progress.onProgress(done.incrementAndGet(), total, seg);
                    }
                }));
            }
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    cancel.cancel();
                    Throwable cause = e.getCause();
                    if (parent.isCancelled()) throw new Cancel.Cancelled();
                    if (cause instanceof Exception) throw (Exception) cause;
                    throw e;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        parent.check();

        boolean any = false;
        for (short[] c : clips) if (c != null) {
            any = true;
            break;
        }
        if (!any) throw firstError[0] != null ? firstError[0] : new IllegalStateException("No audio was produced");

        List<short[]> finished = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        List<Integer> index = new ArrayList<>();
        String lastSpeaker = null;
        long totalSamples = AudioCodec.silenceSamples(300);
        for (int i = 0; i < total; i++) {
            if (clips[i] == null) continue;
            Types.Segment seg = segments.get(i);
            int gap = 0;
            if (lastSpeaker != null) {
                int ms = seg.isPara() ? advanced.paragraphGapMs : !seg.speaker.equals(lastSpeaker) ? advanced.speakerGapMs : advanced.lineGapMs;
                gap = AudioCodec.silenceSamples(ms);
            }
            short[] s = AudioCodec.trimSilence(clips[i]);
            if (advanced.normalize) s = AudioCodec.normalizeLoudness(s);
            clips[i] = null;
            finished.add(s);
            gaps.add(gap);
            index.add(i);
            totalSamples += gap + s.length;
            lastSpeaker = seg.speaker;
        }
        totalSamples += AudioCodec.silenceSamples(900);

        int lead = AudioCodec.silenceSamples(300);
        int tail = AudioCodec.silenceSamples(900);
        double[] starts = new double[total];
        double[] ends = new double[total];
        long walk = lead;
        double lastEnd = lead / (double) AudioCodec.RATE;
        int step = 0;
        for (int i = 0; i < total; i++) {
            if (step < index.size() && index.get(step) == i) {
                walk += gaps.get(step);
                starts[i] = walk / (double) AudioCodec.RATE;
                walk += finished.get(step).length;
                ends[i] = walk / (double) AudioCodec.RATE;
                lastEnd = ends[i];
                step++;
            } else {
                starts[i] = lastEnd;
                ends[i] = lastEnd;
            }
        }

        Mixer mixer = null;
        if (soundSource != null && Mixer.enabled(soundLevel, plan)) {
            mixer = Mixer.build(plan, soundLevel, starts, ends, soundSource, mixProgress, parent);
            if (!mixer.hasWork()) mixer = null;
        }

        Output out = new Output();
        out.file = new File(dir, "audiobook-" + System.currentTimeMillis() + ".wav");
        long cursor;
        try (AudioCodec.WavWriter w = new AudioCodec.WavWriter(out.file, totalSamples, 900)) {
            if (mixer != null) {
                short[] head = new short[lead];
                mixer.process(head, 0);
                w.write(head);
            } else {
                w.silence(lead);
            }
            cursor = lead;
            for (int k = 0; k < finished.size(); k++) {
                parent.check();
                int gap = gaps.get(k);
                if (gap > 0) {
                    if (mixer != null) {
                        short[] pause = new short[gap];
                        mixer.process(pause, cursor);
                        w.write(pause);
                    } else {
                        w.silence(gap);
                    }
                    cursor += gap;
                }
                short[] s = finished.get(k);
                Types.Segment seg = segments.get(index.get(k));
                Types.TimelineEntry t = new Types.TimelineEntry();
                t.segmentId = seg.id;
                t.speaker = seg.speaker;
                t.start = cursor / (double) AudioCodec.RATE;
                t.end = (cursor + s.length) / (double) AudioCodec.RATE;
                out.timeline.add(t);
                if (mixer != null) mixer.process(s, cursor);
                w.write(s);
                cursor += s.length;
                finished.set(k, null);
            }
            if (mixer != null) {
                short[] outro = new short[tail];
                mixer.process(outro, cursor);
                w.write(outro);
            } else {
                w.silence(tail);
            }
            for (float p : w.peaks()) out.peaks.add((double) p);
        } catch (Exception e) {
            out.file.delete();
            throw e;
        }
        if (mixer != null) out.sounds = mixer.used();
        out.duration = totalSamples / (double) AudioCodec.RATE;
        out.failed = new ArrayList<>(failed);
        out.backup = new ArrayList<>(backup);
        return out;
    }
}
