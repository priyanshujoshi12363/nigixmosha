package com.nigixmosha.app.studio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nigixmosha.app.data.model.ProjectRecord;
import com.nigixmosha.app.engine.model.Types;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StudioStore {
    public enum Step { MANUSCRIPT, CAST, SCRIPT, LISTEN }

    public enum SaveState { IDLE, SAVING, SAVED, ERROR }

    public interface Listener {
        void onStudioChanged();
    }

    public static final class Remote {
        public String url;
        public String streamUrl;
        public String downloadUrl;
    }

    public static final class Output {
        public String localPath;
        public double duration;
        public List<Types.TimelineEntry> timeline = new ArrayList<>();
        public List<Double> peaks = new ArrayList<>();
        public String provider;
        public long createdAt;
        public int failed;
        public int backup;
        public Remote remote;

        public String playableUrl() {
            if (localPath != null && new File(localPath).exists()) return localPath;
            return remote != null ? remote.streamUrl : null;
        }
    }

    public static final class AudioSave {
        public String status = "idle";
        public double progress;
        public String error;

        public AudioSave() {
        }

        public AudioSave(String status, double progress, String error) {
            this.status = status;
            this.progress = progress;
            this.error = error;
        }
    }

    public static final class State {
        public String title = "";
        public String text = "";
        public String languageHint = "auto";
        public Step step = Step.MANUSCRIPT;
        public Types.Analysis analysis;
        public List<Types.Segment> segments = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
        public Map<String, Types.CastEntry> cast = new LinkedHashMap<>();
        public Types.CastFor castFor;
        public Map<String, String> castReasons = new LinkedHashMap<>();
        public String castBy;
        public boolean shareNarrator;
        public Types.Soundscape soundscape;
        public Output output;
        public String projectId;
        public String projectTextHash;
        public transient SaveState saveState = SaveState.IDLE;
        public transient String saveError;
        public AudioSave audioSave = new AudioSave();
    }

    private static volatile StudioStore instance;

    private final File file;
    private final Gson gson = new GsonBuilder().create();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Runnable persist = this::persistNow;
    private State s;
    private Runnable autosave;

    public static StudioStore get(Context context) {
        if (instance == null) {
            synchronized (StudioStore.class) {
                if (instance == null) instance = new StudioStore(context.getApplicationContext());
            }
        }
        return instance;
    }

    private StudioStore(Context context) {
        file = new File(context.getFilesDir(), "studio.json");
        State loaded = null;
        if (file.exists()) {
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                loaded = gson.fromJson(r, State.class);
            } catch (Exception ignored) {
            }
        }
        s = loaded != null ? loaded : new State();
        if (s.segments == null) s.segments = new ArrayList<>();
        if (s.warnings == null) s.warnings = new ArrayList<>();
        if (s.cast == null) s.cast = new LinkedHashMap<>();
        if (s.castReasons == null) s.castReasons = new LinkedHashMap<>();
        if (s.audioSave == null) s.audioSave = new AudioSave();
        if (s.step == null) s.step = Step.MANUSCRIPT;
        if (s.languageHint == null) s.languageHint = "auto";
        s.saveState = s.projectId != null && s.analysis != null ? SaveState.SAVED : SaveState.IDLE;
        if (s.output != null && s.output.playableUrl() == null) s.output = null;
        if (s.step != Step.MANUSCRIPT && s.analysis == null) s.step = Step.MANUSCRIPT;
    }

    public void setAutosave(Runnable autosave) {
        this.autosave = autosave;
    }

    public State state() {
        return s;
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed(boolean sync) {
        main.removeCallbacks(persist);
        main.postDelayed(persist, 400);
        for (Listener l : listeners) l.onStudioChanged();
        if (sync && s.analysis != null && autosave != null) {
            main.removeCallbacks(autosave);
            main.postDelayed(autosave, 1200);
        }
    }

    private void persistNow() {
        State snapshot = gson.fromJson(gson.toJson(s), State.class);
        if (snapshot.step == Step.LISTEN && (s.output == null || s.output.remote == null)) snapshot.step = Step.SCRIPT;
        if (snapshot.output != null && snapshot.output.remote == null) snapshot.output = null;
        if (snapshot.output != null) snapshot.output.localPath = s.output != null ? s.output.localPath : null;
        if (!"saved".equals(snapshot.audioSave.status) || snapshot.output == null || snapshot.output.remote == null) {
            snapshot.audioSave = new AudioSave();
        }
        String json = gson.toJson(snapshot);
        new Thread(() -> {
            File tmp = new File(file.getPath() + ".tmp");
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                w.write(json);
            } catch (Exception e) {
                return;
            }
            if (!tmp.renameTo(file)) {
                file.delete();
                tmp.renameTo(file);
            }
        }).start();
    }

    public static String hashText(String text) {
        int h = 0x811c9dc5;
        for (int i = 0; i < text.length(); i++) {
            h ^= text.charAt(i);
            h *= 16777619;
        }
        return text.length() + ":" + Long.toString(h & 0xffffffffL, 36);
    }

    private void recount() {
        if (s.analysis == null) return;
        Map<String, Integer> counts = new HashMap<>();
        for (Types.Segment seg : s.segments) counts.merge(seg.speaker, 1, Integer::sum);
        for (Types.CastCharacter c : s.analysis.characters) c.lineCount = counts.getOrDefault(c.id, 0);
    }

    public void setDraft(String title, String text, String languageHint) {
        if (title != null) s.title = title;
        if (text != null) s.text = text;
        if (languageHint != null) s.languageHint = languageHint;
        changed(languageHint != null || title != null);
    }

    public void setStep(Step step) {
        s.step = step;
        changed(false);
    }

    public void setDirected(Types.Analysis analysis, List<Types.Segment> segments, List<String> warnings) {
        s.analysis = analysis;
        s.segments = segments;
        s.warnings = warnings;
        s.cast = new LinkedHashMap<>();
        s.castFor = null;
        s.castReasons = new LinkedHashMap<>();
        s.castBy = null;
        s.output = null;
        s.audioSave = new AudioSave();
        s.shareNarrator = analysis.narrator.characterId != null;
        s.soundscape = null;
        s.step = Step.CAST;
        changed(true);
    }

    public void setSoundscape(Types.Soundscape plan) {
        s.soundscape = plan;
        changed(false);
    }

    public void updateTitle(String title) {
        if (s.analysis == null) return;
        s.analysis.title = title;
        changed(true);
    }

    public void updateCharacter(String id, String name, String gender, String age) {
        if (s.analysis == null) return;
        Types.CastCharacter c = s.analysis.character(id);
        if (c == null) return;
        if (name != null) c.name = name;
        if (gender != null) c.gender = gender;
        if (age != null) c.age = age;
        changed(true);
    }

    public void setCast(Map<String, Types.CastEntry> cast, Types.CastFor castFor, Map<String, String> reasons, String by) {
        s.cast = new LinkedHashMap<>(cast);
        s.castFor = castFor;
        s.castReasons = reasons != null ? new LinkedHashMap<>(reasons) : new LinkedHashMap<>();
        s.castBy = by != null ? by : "rules";
        changed(true);
    }

    public void updateCast(String id, String voiceId, Double pitch, Double rate) {
        Types.CastEntry current = s.cast.get(id);
        if (current == null) current = s.cast.get(Types.NARRATOR_ID);
        Types.CastEntry next = current != null ? current.copy() : new Types.CastEntry("", 0, 0);
        if (voiceId != null) next.voiceId = voiceId;
        if (pitch != null) next.pitch = pitch;
        if (rate != null) next.rate = rate;
        s.cast.put(id, next);
        changed(true);
    }

    public void setShareNarrator(boolean v) {
        s.shareNarrator = v;
        changed(true);
    }

    public void updateSegment(String id, String speaker, String emotion, String text) {
        for (Types.Segment seg : s.segments) {
            if (!seg.id.equals(id)) continue;
            if (speaker != null) seg.speaker = speaker;
            if (emotion != null) seg.emotion = emotion;
            if (text != null) seg.text = text;
            break;
        }
        if (speaker != null) recount();
        changed(true);
    }

    public void mergeSegmentUp(String id) {
        for (int i = 1; i < s.segments.size(); i++) {
            if (!s.segments.get(i).id.equals(id)) continue;
            Types.Segment prev = s.segments.get(i - 1);
            prev.text = prev.text + " " + s.segments.get(i).text;
            s.segments.remove(i);
            recount();
            changed(true);
            return;
        }
    }

    public void deleteSegment(String id) {
        for (int i = 0; i < s.segments.size(); i++) {
            if (s.segments.get(i).id.equals(id)) {
                s.segments.remove(i);
                recount();
                changed(true);
                return;
            }
        }
    }

    public void setOutput(Output output) {
        Output prev = s.output;
        if (prev != null && prev.localPath != null && (output == null || !prev.localPath.equals(output.localPath))) {
            new File(prev.localPath).delete();
        }
        s.output = output;
        if (output == null || output.remote == null) s.audioSave = new AudioSave();
        changed(false);
    }

    public void setRemote(Remote remote) {
        if (s.output == null) return;
        s.output.remote = remote;
        changed(false);
    }

    public void setProjectId(String id, String textHash, boolean setHash) {
        s.projectId = id;
        if (setHash) s.projectTextHash = textHash;
        changed(false);
    }

    public void setSaveState(SaveState state, String error) {
        s.saveState = state;
        s.saveError = error;
        for (Listener l : listeners) l.onStudioChanged();
    }

    public void setAudioSave(AudioSave audioSave) {
        s.audioSave = audioSave;
        changed(false);
    }

    public void loadProject(ProjectRecord r) {
        if (s.output != null && s.output.localPath != null) new File(s.output.localPath).delete();
        State n = new State();
        n.projectId = r.id;
        n.projectTextHash = hashText(r.text == null ? "" : r.text);
        n.title = r.title == null ? "" : r.title;
        n.text = r.text == null ? "" : r.text;
        n.languageHint = r.languageHint == null || r.languageHint.isEmpty() ? "auto" : r.languageHint;
        n.analysis = r.analysis;
        n.segments = r.segments != null ? r.segments : new ArrayList<>();
        n.warnings = r.warnings != null ? r.warnings : new ArrayList<>();
        n.cast = r.cast != null ? new LinkedHashMap<>(r.cast) : new LinkedHashMap<>();
        n.castFor = r.castFor;
        n.castReasons = r.castReasons != null ? new LinkedHashMap<>(r.castReasons) : new LinkedHashMap<>();
        n.castBy = r.castBy;
        n.shareNarrator = r.shareNarrator;
        if (r.audio != null) {
            Output o = new Output();
            o.duration = r.audio.duration;
            o.timeline = r.audio.timeline != null ? r.audio.timeline : new ArrayList<>();
            o.peaks = r.audio.peaks != null ? r.audio.peaks : new ArrayList<>();
            o.provider = r.audio.provider;
            long created;
            try {
                created = Instant.parse(r.audio.createdAt).toEpochMilli();
            } catch (Exception e) {
                created = System.currentTimeMillis();
            }
            o.createdAt = created;
            o.failed = r.audio.failed;
            Remote remote = new Remote();
            remote.url = r.audio.url;
            remote.streamUrl = r.audio.streamUrl;
            remote.downloadUrl = r.audio.downloadUrl;
            o.remote = remote;
            n.output = o;
            n.audioSave = new AudioSave("saved", 1, null);
        }
        n.step = n.output != null ? Step.LISTEN : n.analysis != null ? Step.CAST : Step.MANUSCRIPT;
        n.saveState = SaveState.SAVED;
        s = n;
        changed(false);
    }

    public void reset() {
        if (s.output != null && s.output.localPath != null) new File(s.output.localPath).delete();
        s = new State();
        changed(false);
    }
}
