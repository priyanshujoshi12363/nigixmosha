package com.nigixmosha.app.studio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.SettingsStore;
import com.nigixmosha.app.data.SoundCache;
import com.nigixmosha.app.engine.AiCast;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.Director;
import com.nigixmosha.app.engine.Mixer;
import com.nigixmosha.app.engine.Producer;
import com.nigixmosha.app.engine.SoundDesigner;
import com.nigixmosha.app.engine.Sounds;
import com.nigixmosha.app.engine.model.Types;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StudioJobs {
    public enum Status { IDLE, RUNNING, ERROR }

    public interface Listener {
        void onJobsChanged();
    }

    private static volatile StudioJobs instance;

    private final Context app;
    private final ApiClient api;
    private final StudioStore store;
    private final SettingsStore settings;
    private final LibrarySync sync;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, List<Types.VoiceProfile>> liveVoices = new HashMap<>();

    public Status directStatus = Status.IDLE;
    public String directPhase;
    public String directLabel;
    public double directValue;
    public List<Types.CastCharacter> directCharacters = new ArrayList<>();
    public String directError;
    public Status castStatus = Status.IDLE;
    public String castNote;
    public Status produceStatus = Status.IDLE;
    public int produceDone;
    public int produceTotal;
    public Types.Segment produceCurrent;
    public String produceError;
    public boolean produceSoundStage;
    public String previewKey;
    public String previewState;
    public String previewError;

    private Cancel directCancel;
    private Cancel castCancel;
    private Cancel produceCancel;
    private Cancel previewCancel;
    private MediaPlayer previewPlayer;
    private File previewFile;
    private int previewToken;

    public static StudioJobs get(Context context) {
        if (instance == null) {
            synchronized (StudioJobs.class) {
                if (instance == null) instance = new StudioJobs(context.getApplicationContext());
            }
        }
        return instance;
    }

    private StudioJobs(Context context) {
        app = context;
        api = ApiClient.get(context);
        store = StudioStore.get(context);
        settings = SettingsStore.get(context);
        sync = LibrarySync.get(context);
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        for (Listener l : listeners) l.onJobsChanged();
    }

    private void post(Runnable r) {
        main.post(r);
    }

    private static String message(Throwable e, Context c) {
        return e.getMessage() != null ? e.getMessage() : c.getString(R.string.err_unexpected);
    }

    public List<Types.VoiceProfile> cachedLiveVoices(String provider, String apiKey) {
        synchronized (liveVoices) {
            return liveVoices.get(liveKey(provider, apiKey));
        }
    }

    public static String liveKey(String provider, String apiKey) {
        String k = apiKey == null ? "" : apiKey.trim();
        return provider + ":" + (k.length() > 8 ? k.substring(k.length() - 8) : k);
    }

    public boolean wantsLiveVoices(String provider, String apiKey) {
        Types.ProviderMeta meta = Catalog.get().provider(provider);
        return meta.liveVoices && ("edge".equals(provider) || (apiKey != null && !apiKey.trim().isEmpty()) || settings.server().hasServerKey(provider));
    }

    public List<Types.VoiceProfile> loadLiveVoices(String provider, String apiKey, Cancel cancel) throws Exception {
        String key = liveKey(provider, apiKey);
        synchronized (liveVoices) {
            List<Types.VoiceProfile> hit = liveVoices.get(key);
            if (hit != null) return hit;
        }
        List<Types.VoiceProfile> list = api.voices(provider, apiKey == null ? "" : apiKey.trim(), cancel);
        if (!list.isEmpty()) {
            synchronized (liveVoices) {
                liveVoices.put(key, list);
            }
        }
        return list;
    }

    private static final class Casting {
        AiCast.Outcome outcome;
        Types.CastFor castFor;
    }

    private Casting castWithBrain(Types.Analysis analysis, Cancel cancel) throws Exception {
        String tts = settings.activeTTS();
        Types.ProviderMeta meta = Catalog.get().provider(tts);
        Types.ProviderConfig cfg = settings.config(tts);
        String model = cfg.model == null || cfg.model.isEmpty() ? meta.defaultModel : cfg.model;
        List<Types.VoiceProfile> voices = Catalog.get().staticVoices(tts, model);
        if (wantsLiveVoices(tts, cfg.apiKey)) {
            try {
                List<Types.VoiceProfile> live = loadLiveVoices(tts, cfg.apiKey, cancel);
                if (!live.isEmpty()) voices = live;
            } catch (Cancel.Cancelled c) {
                throw c;
            } catch (Exception ignored) {
            }
        }
        Casting result = new Casting();
        result.outcome = AiCast.castVoices(analysis, voices, meta, model, (system, prompt) -> api.brain(system, prompt, cancel), cancel);
        result.castFor = new Types.CastFor(tts, model);
        return result;
    }

    public void startDirect() {
        StudioStore.State p = store.state();
        if (p.text.trim().isEmpty()) return;
        if (directCancel != null) directCancel.cancel();
        if (castCancel != null) castCancel.cancel();
        Cancel cancel = new Cancel();
        directCancel = cancel;
        directStatus = Status.RUNNING;
        directPhase = "reading";
        directLabel = app.getString(R.string.director_opening);
        directValue = 0.02;
        directCharacters = new ArrayList<>();
        directError = null;
        castNote = null;
        changed();
        String text = p.text;
        String title = p.title;
        String hint = p.languageHint;
        worker.execute(() -> {
            try {
                Director.Result result = Director.direct(text, title, hint, (system, prompt) -> api.brain(system, prompt, cancel),
                        new Director.Listener() {
                            @Override
                            public void onProgress(String phase, String label, double value) {
                                post(() -> {
                                    if (directCancel != cancel) return;
                                    directPhase = phase;
                                    directLabel = label;
                                    directValue = value;
                                    changed();
                                });
                            }

                            @Override
                            public void onCharacters(List<Types.CastCharacter> characters) {
                                List<Types.CastCharacter> copy = new ArrayList<>(characters);
                                post(() -> {
                                    if (directCancel != cancel) return;
                                    directCharacters = copy;
                                    changed();
                                });
                            }
                        }, cancel);
                cancel.check();
                String engineName = Catalog.get().provider(settings.activeTTS()).name;
                post(() -> {
                    if (directCancel != cancel) return;
                    directCharacters = new ArrayList<>(result.analysis.characters);
                    directPhase = "casting";
                    directLabel = app.getString(R.string.director_casting, engineName);
                    directValue = 0.97;
                    changed();
                });
                Casting casting = castWithBrain(result.analysis, cancel);
                cancel.check();
                post(() -> {
                    if (directCancel != cancel) return;
                    StudioStore.State s = store.state();
                    String hash = StudioStore.hashText(text);
                    if (!hash.equals(s.projectTextHash)) store.setProjectId(null, hash, true);
                    store.setOutput(null);
                    store.setDirected(result.analysis, result.segments, result.warnings);
                    store.setCast(casting.outcome.cast, casting.castFor, casting.outcome.reasons, casting.outcome.by);
                    directStatus = Status.IDLE;
                    directPhase = null;
                    directCharacters = new ArrayList<>();
                    castNote = casting.outcome.note;
                    directCancel = null;
                    changed();
                });
            } catch (Exception e) {
                post(() -> {
                    if (directCancel != cancel) return;
                    directCancel = null;
                    directPhase = null;
                    if (e instanceof Cancel.Cancelled || cancel.isCancelled()) {
                        directStatus = Status.IDLE;
                    } else {
                        directStatus = Status.ERROR;
                        directError = message(e, app);
                    }
                    changed();
                });
            }
        });
    }

    public void cancelDirect() {
        if (directCancel != null) directCancel.cancel();
        directCancel = null;
        directStatus = Status.IDLE;
        directPhase = null;
        directCharacters = new ArrayList<>();
        changed();
    }

    public void startCast() {
        Types.Analysis analysis = store.state().analysis;
        if (analysis == null) return;
        if (castCancel != null) castCancel.cancel();
        Cancel cancel = new Cancel();
        castCancel = cancel;
        castStatus = Status.RUNNING;
        castNote = null;
        changed();
        worker.execute(() -> {
            try {
                Casting casting = castWithBrain(analysis, cancel);
                cancel.check();
                post(() -> {
                    if (castCancel != cancel) return;
                    store.setCast(casting.outcome.cast, casting.castFor, casting.outcome.reasons, casting.outcome.by);
                    castStatus = Status.IDLE;
                    castNote = casting.outcome.note;
                    castCancel = null;
                    changed();
                });
            } catch (Exception e) {
                post(() -> {
                    if (castCancel != cancel) return;
                    castCancel = null;
                    if (e instanceof Cancel.Cancelled || cancel.isCancelled()) castStatus = Status.IDLE;
                    else {
                        castStatus = Status.ERROR;
                        castNote = message(e, app);
                    }
                    changed();
                });
            }
        });
    }

    private Producer.Context voiceContext() {
        StudioStore.State p = store.state();
        if (p.analysis == null || p.castFor == null) throw new IllegalStateException(app.getString(R.string.cast_first));
        Producer.Context ctx = new Producer.Context();
        ctx.analysis = p.analysis;
        ctx.cast = new HashMap<>(p.cast);
        ctx.shareNarrator = p.shareNarrator;
        ctx.provider = p.castFor.provider;
        ctx.config = settings.config(p.castFor.provider);
        ctx.config.model = p.castFor.model;
        return ctx;
    }

    private Producer.Speech speech() {
        return api::speech;
    }

    public void startProduce() {
        StudioStore.State p = store.state();
        if (p.analysis == null || p.castFor == null || p.segments.isEmpty()) return;
        stopPreview();
        if (produceCancel != null) produceCancel.cancel();
        Cancel cancel = new Cancel();
        produceCancel = cancel;
        store.setStep(StudioStore.Step.LISTEN);
        produceStatus = Status.RUNNING;
        produceDone = 0;
        produceTotal = p.segments.size();
        produceCurrent = null;
        produceError = null;
        produceSoundStage = false;
        changed();
        Producer.Context ctx;
        try {
            ctx = voiceContext();
        } catch (Exception e) {
            produceStatus = Status.ERROR;
            produceError = message(e, app);
            produceCancel = null;
            changed();
            return;
        }
        List<Types.Segment> segments = new ArrayList<>(p.segments);
        Types.Advanced advanced = settings.advanced();
        Types.Analysis analysis = p.analysis;
        Types.Soundscape existing = p.soundscape;
        File dir = new File(app.getFilesDir(), "audio");
        dir.mkdirs();
        worker.execute(() -> {
            try {
                String level = advanced.soundscape == null ? "off" : advanced.soundscape;
                Types.Soundscape plan = existing;
                if (!"off".equals(level) && !Sounds.get().isEmpty() && plan == null && analysis != null) {
                    post(() -> {
                        if (produceCancel != cancel) return;
                        produceSoundStage = true;
                        changed();
                    });
                    try {
                        plan = SoundDesigner.plan(analysis, segments, (system, prompt) -> api.brain(system, prompt, cancel), null, cancel);
                        Types.Soundscape saved = plan;
                        post(() -> {
                            if (produceCancel == cancel) store.setSoundscape(saved);
                        });
                    } catch (Cancel.Cancelled c) {
                        throw c;
                    } catch (Exception ignored) {
                        plan = null;
                    }
                    post(() -> {
                        if (produceCancel != cancel) return;
                        produceSoundStage = false;
                        changed();
                    });
                }
                Mixer.Source source = "off".equals(level) ? null : new SoundCache(app, api.http()).source(cancel);
                Producer.Output out = Producer.produce(segments, ctx, advanced, speech(), (done, total, current) -> post(() -> {
                    if (produceCancel != cancel) return;
                    produceDone = done;
                    produceTotal = total;
                    produceCurrent = current;
                    changed();
                }), cancel, dir, plan, level, source, (done, total) -> post(() -> {
                    if (produceCancel != cancel) return;
                    produceSoundStage = true;
                    changed();
                }));
                post(() -> {
                    if (produceCancel != cancel || cancel.isCancelled()) {
                        out.file.delete();
                        return;
                    }
                    StudioStore.Output o = new StudioStore.Output();
                    o.localPath = out.file.getAbsolutePath();
                    o.duration = out.duration;
                    o.timeline = out.timeline;
                    o.peaks = out.peaks;
                    o.provider = ctx.provider;
                    o.createdAt = System.currentTimeMillis();
                    o.failed = out.failed.size();
                    o.backup = out.backup.size();
                    store.setOutput(o);
                    produceStatus = Status.IDLE;
                    produceCurrent = null;
                    produceSoundStage = false;
                    produceCancel = null;
                    changed();
                    sync.saveAudiobook(out.file);
                });
            } catch (Exception e) {
                post(() -> {
                    if (produceCancel != cancel) return;
                    produceCancel = null;
                    produceCurrent = null;
                    if (e instanceof Cancel.Cancelled || cancel.isCancelled()) produceStatus = Status.IDLE;
                    else {
                        produceStatus = Status.ERROR;
                        produceError = message(e, app);
                    }
                    changed();
                });
            }
        });
    }

    public void cancelProduce() {
        produceStatus = Status.IDLE;
        produceCurrent = null;
        if (produceCancel != null) produceCancel.cancel();
        produceCancel = null;
        changed();
    }

    public void preview(String key, Types.Segment seg) {
        stopPreview();
        int token = ++previewToken;
        previewKey = key;
        previewState = "loading";
        previewError = null;
        changed();
        Producer.Context ctx;
        try {
            ctx = voiceContext();
        } catch (Exception e) {
            previewKey = null;
            previewState = null;
            previewError = message(e, app);
            changed();
            return;
        }
        Cancel cancel = new Cancel();
        previewCancel = cancel;
        File dir = app.getCacheDir();
        worker.execute(() -> {
            try {
                File file = Producer.renderPreview(seg, ctx, speech(), cancel, dir);
                post(() -> {
                    if (token != previewToken) {
                        file.delete();
                        return;
                    }
                    playPreview(token, file);
                });
            } catch (Exception e) {
                post(() -> {
                    if (token != previewToken) return;
                    previewKey = null;
                    previewState = null;
                    if (!(e instanceof Cancel.Cancelled)) previewError = message(e, app);
                    changed();
                });
            }
        });
    }

    private void playPreview(int token, File file) {
        previewFile = file;
        MediaPlayer mp = new MediaPlayer();
        previewPlayer = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnCompletionListener(done -> {
                if (token == previewToken) stopPreview();
            });
            mp.prepare();
            mp.start();
            previewState = "playing";
            changed();
        } catch (Exception e) {
            previewKey = null;
            previewState = null;
            previewError = message(e, app);
            releasePreview();
            changed();
        }
    }

    private void releasePreview() {
        if (previewPlayer != null) {
            try {
                previewPlayer.release();
            } catch (Exception ignored) {
            }
            previewPlayer = null;
        }
        if (previewFile != null) {
            previewFile.delete();
            previewFile = null;
        }
    }

    public void stopPreview() {
        previewToken++;
        if (previewCancel != null) previewCancel.cancel();
        previewCancel = null;
        releasePreview();
        boolean had = previewKey != null;
        previewKey = null;
        previewState = null;
        if (had) changed();
    }

    public void clearPreviewError() {
        previewError = null;
        changed();
    }

    public void cancelAll() {
        cancelDirect();
        cancelProduce();
        stopPreview();
    }

    public boolean isBusy() {
        return directStatus == Status.RUNNING || produceStatus == Status.RUNNING;
    }

    public static boolean isAuthError(Throwable e) {
        return e instanceof ApiException && ((ApiException) e).isUnauthorized();
    }
}
