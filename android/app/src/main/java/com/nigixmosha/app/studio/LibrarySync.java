package com.nigixmosha.app.studio;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.SettingsStore;
import com.nigixmosha.app.data.model.ProjectRecord;
import com.nigixmosha.app.engine.Cancel;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LibrarySync {
    private static volatile LibrarySync instance;

    private final Context app;
    private final ApiClient api;
    private final StudioStore store;
    private final SettingsStore settings;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean saving;
    private boolean again;
    private File lastFile;
    private int uploadToken;

    public static LibrarySync get(Context context) {
        if (instance == null) {
            synchronized (LibrarySync.class) {
                if (instance == null) instance = new LibrarySync(context.getApplicationContext());
            }
        }
        return instance;
    }

    private LibrarySync(Context context) {
        app = context;
        api = ApiClient.get(context);
        store = StudioStore.get(context);
        settings = SettingsStore.get(context);
        store.setAutosave(() -> saveProjectNow(null));
    }

    private JsonObject payload(StudioStore.State s) {
        if (s.analysis == null) return null;
        JsonObject body = new JsonObject();
        String title = s.analysis.title != null && !s.analysis.title.isEmpty() ? s.analysis.title
                : s.title != null && !s.title.isEmpty() ? s.title : "Untitled chapter";
        body.addProperty("title", title);
        body.addProperty("text", s.text);
        body.addProperty("languageHint", s.languageHint);
        body.add("analysis", gson.toJsonTree(s.analysis));
        body.add("segments", gson.toJsonTree(s.segments));
        body.add("warnings", gson.toJsonTree(s.warnings));
        body.add("cast", gson.toJsonTree(s.cast));
        body.add("castFor", s.castFor == null ? com.google.gson.JsonNull.INSTANCE : gson.toJsonTree(s.castFor));
        body.add("castReasons", gson.toJsonTree(s.castReasons));
        if (s.castBy == null) body.add("castBy", com.google.gson.JsonNull.INSTANCE);
        else body.addProperty("castBy", s.castBy);
        body.addProperty("shareNarrator", s.shareNarrator);
        return body;
    }

    public void saveProjectNow(Runnable done) {
        if (!settings.server().storage.db) {
            if (done != null) done.run();
            return;
        }
        if (saving) {
            again = true;
            if (done != null) pendingDone = chain(pendingDone, done);
            return;
        }
        StudioStore.State s = store.state();
        JsonObject body = payload(s);
        if (body == null) {
            if (done != null) done.run();
            return;
        }
        saving = true;
        String id = s.projectId;
        store.setSaveState(StudioStore.SaveState.SAVING, null);
        worker.execute(() -> {
            String createdId = null;
            ApiException failure = null;
            boolean missing = false;
            try {
                if (id != null) api.updateProject(id, body, new Cancel());
                else createdId = api.createProject(body, new Cancel());
            } catch (ApiException e) {
                if (e.status() == 404 && id != null) missing = true;
                else failure = e;
            } catch (Exception e) {
                failure = new ApiException(ApiException.Kind.PARSE, 0, null, app.getString(R.string.err_unexpected));
            }
            String finalCreated = createdId;
            ApiException finalFailure = failure;
            boolean finalMissing = missing;
            main.post(() -> {
                if (finalMissing) {
                    store.setProjectId(null, null, false);
                    again = true;
                } else if (finalFailure != null) {
                    store.setSaveState(StudioStore.SaveState.ERROR, finalFailure.getMessage());
                } else {
                    if (finalCreated != null) store.setProjectId(finalCreated, null, false);
                    store.setSaveState(StudioStore.SaveState.SAVED, null);
                }
                saving = false;
                Runnable callbacks = done;
                if (again) {
                    again = false;
                    Runnable queued = chain(pendingDone, callbacks);
                    pendingDone = null;
                    saveProjectNow(queued);
                } else {
                    Runnable queued = chain(pendingDone, callbacks);
                    pendingDone = null;
                    if (queued != null) queued.run();
                }
            });
        });
    }

    private Runnable pendingDone;

    private static Runnable chain(Runnable a, Runnable b) {
        if (a == null) return b;
        if (b == null) return a;
        return () -> {
            a.run();
            b.run();
        };
    }

    public void saveAudiobook(File file) {
        if (!settings.server().storage.db || !settings.server().storage.media) return;
        if (file != null) lastFile = file;
        File target = file != null ? file : lastFile;
        if (target == null || !target.exists()) return;
        int token = ++uploadToken;
        report(token, new StudioStore.AudioSave("uploading", 0, null));
        saveProjectNow(() -> {
            StudioStore.State s = store.state();
            if (s.projectId == null || s.output == null) {
                report(token, new StudioStore.AudioSave("error", 0, app.getString(R.string.err_library_save)));
                return;
            }
            String projectId = s.projectId;
            StudioStore.Output output = s.output;
            worker.execute(() -> {
                try {
                    ApiClient.UploadTicket ticket = api.signUpload(projectId, new Cancel());
                    ApiClient.Uploaded up = api.upload(ticket, target, fraction ->
                            main.post(() -> report(token, new StudioStore.AudioSave("uploading", fraction, null))), new Cancel());
                    JsonObject body = new JsonObject();
                    JsonObject audio = new JsonObject();
                    audio.addProperty("publicId", up.publicId);
                    audio.addProperty("version", up.version);
                    audio.addProperty("bytes", up.bytes);
                    audio.addProperty("duration", output.duration);
                    audio.add("timeline", gson.toJsonTree(output.timeline));
                    audio.add("peaks", gson.toJsonTree(output.peaks));
                    audio.addProperty("provider", output.provider);
                    audio.addProperty("failed", output.failed);
                    body.add("audio", audio);
                    ProjectRecord rec = api.updateProject(projectId, body, new Cancel());
                    main.post(() -> {
                        if (token != uploadToken) return;
                        if (rec != null && rec.audio != null) {
                            StudioStore.Remote remote = new StudioStore.Remote();
                            remote.url = rec.audio.url;
                            remote.streamUrl = rec.audio.streamUrl;
                            remote.downloadUrl = rec.audio.downloadUrl;
                            store.setRemote(remote);
                        }
                        report(token, new StudioStore.AudioSave("saved", 1, null));
                        lastFile = null;
                    });
                } catch (Exception e) {
                    String message = e.getMessage() != null ? e.getMessage() : app.getString(R.string.err_unexpected);
                    main.post(() -> report(token, new StudioStore.AudioSave("error", 0, message)));
                }
            });
        });
    }

    public void retryAudioUpload() {
        saveAudiobook(null);
    }

    private void report(int token, StudioStore.AudioSave save) {
        if (token == uploadToken) store.setAudioSave(save);
    }
}
