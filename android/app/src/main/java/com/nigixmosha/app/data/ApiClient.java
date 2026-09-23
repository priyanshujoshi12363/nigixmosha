package com.nigixmosha.app.data;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.nigixmosha.app.BuildConfig;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.model.Project;
import com.nigixmosha.app.data.model.ProjectRecord;
import com.nigixmosha.app.data.model.Responses;
import com.nigixmosha.app.data.model.ServerConfig;
import com.nigixmosha.app.data.model.User;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Util;
import com.nigixmosha.app.engine.model.Types;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

public final class ApiClient {
    public interface Callback<T> {
        void onSuccess(T value);

        void onError(ApiException error);
    }

    public interface UploadProgress {
        void onProgress(double fraction);
    }

    private interface Parser<T> {
        T parse(String body) throws Exception;
    }

    public static final class UploadTicket {
        public String uploadUrl;
        public String apiKey;
        public Map<String, Object> params;
        public String signature;
        public long maxBytes;
    }

    public static final class Uploaded {
        public String publicId;
        public long version;
        public long bytes;
    }

    public static final class Extracted {
        public String text;
        public String title;
    }

    private static final String SESSION_COOKIE = "nigix_session";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static volatile ApiClient instance;

    private final Context app;
    private final TokenStore store;
    private final OkHttpClient http;
    private final OkHttpClient slow;
    private final OkHttpClient upload;
    private final HttpUrl base;
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newCachedThreadPool();

    public static ApiClient get(Context context) {
        if (instance == null) {
            synchronized (ApiClient.class) {
                if (instance == null) instance = new ApiClient(context.getApplicationContext());
            }
        }
        return instance;
    }

    private ApiClient(Context app) {
        this.app = app;
        this.store = new TokenStore(app);
        this.base = HttpUrl.get(BuildConfig.BASE_URL);
        String agent = "nigixmosha-android/" + BuildConfig.VERSION_NAME
                + " (Android " + Build.VERSION.RELEASE + "; " + Build.MODEL + ")";
        this.http = new OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(75, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    boolean ours = original.url().host().equals(base.host());
                    Request.Builder request = original.newBuilder().header("User-Agent", agent);
                    if (ours) {
                        if (original.header("Accept") == null) request.header("Accept", "application/json");
                        String token = store.token();
                        if (token != null) request.header("Cookie", SESSION_COOKIE + "=" + token);
                    }
                    Response response = chain.proceed(request.build());
                    if (ours) captureSession(response);
                    return response;
                })
                .build();
        this.slow = http.newBuilder().readTimeout(310, TimeUnit.SECONDS).callTimeout(320, TimeUnit.SECONDS).build();
        this.upload = http.newBuilder().writeTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS).build();
    }

    public boolean hasSession() {
        return store.token() != null;
    }

    public User cachedUser() {
        return store.user();
    }

    public OkHttpClient http() {
        return http;
    }

    public TokenStore store() {
        return store;
    }

    public Call me(Callback<User> callback) {
        return enqueue(get("api/auth/me"), body -> {
            Responses.UserEnvelope envelope = gson.fromJson(body, Responses.UserEnvelope.class);
            User user = envelope == null ? null : envelope.user;
            if (user == null) store.clear();
            else store.saveUser(user);
            return user;
        }, callback);
    }

    public Call login(String username, String password, Callback<User> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        return enqueue(post("api/auth/login", body), this::signedInUser, callback);
    }

    public Call signup(String username, String password, String displayName, Callback<User> callback) {
        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);
        if (displayName != null && !displayName.isEmpty()) body.addProperty("displayName", displayName);
        return enqueue(post("api/auth/signup", body), this::signedInUser, callback);
    }

    public Call logout(boolean everywhere, Callback<Void> callback) {
        JsonObject body = new JsonObject();
        if (everywhere) body.addProperty("all", true);
        return enqueue(post("api/auth/logout", body), raw -> null, new Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                store.clear();
                callback.onSuccess(null);
            }

            @Override
            public void onError(ApiException error) {
                store.clear();
                callback.onSuccess(null);
            }
        });
    }

    public Call projects(Callback<List<Project>> callback) {
        return enqueue(get("api/projects"), body -> {
            Responses.ProjectsEnvelope envelope = gson.fromJson(body, Responses.ProjectsEnvelope.class);
            List<Project> out = new ArrayList<>();
            if (envelope != null && envelope.projects != null) {
                for (Project p : envelope.projects) if (p != null && p.id != null) out.add(p);
            }
            return out;
        }, callback);
    }

    public ServerConfig config(Cancel cancel) throws Exception {
        return call(http, get("api/config"), body -> gson.fromJson(body, ServerConfig.class), cancel);
    }

    public String voiceStatus(Cancel cancel) throws Exception {
        return call(http, get("api/voices/status"), body -> {
            JsonObject o = gson.fromJson(body, JsonObject.class);
            return o != null && o.has("state") ? o.get("state").getAsString() : "off";
        }, cancel);
    }

    public List<Types.VoiceProfile> voices(String provider, String apiKey, Cancel cancel) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("provider", provider);
        body.addProperty("apiKey", apiKey == null ? "" : apiKey);
        return call(http, post("api/voices", body), raw -> {
            JsonObject o = gson.fromJson(raw, JsonObject.class);
            List<Types.VoiceProfile> list = gson.fromJson(o.get("voices"), new TypeToken<List<Types.VoiceProfile>>() {
            }.getType());
            return list == null ? new ArrayList<>() : list;
        }, cancel);
    }

    public String brain(String system, String prompt, Cancel cancel) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("system", system);
        body.addProperty("prompt", prompt);
        body.addProperty("json", true);
        return call(slow, post("api/director", body), raw -> {
            JsonObject o = gson.fromJson(raw, JsonObject.class);
            return o.get("text").getAsString();
        }, cancel);
    }

    public byte[] speech(String provider, Types.ProviderConfig config, String voice, String text, String lang,
                         Types.SynthesisStyle style, Cancel cancel) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("provider", provider);
        body.addProperty("apiKey", config.apiKey == null ? "" : config.apiKey);
        body.addProperty("baseUrl", config.baseUrl == null ? "" : config.baseUrl);
        body.addProperty("model", config.model == null ? "" : config.model);
        body.addProperty("voice", voice);
        body.addProperty("text", text);
        body.addProperty("lang", lang);
        body.add("style", gson.toJsonTree(style));
        Request request = post("api/tts", body).newBuilder().header("Accept", "audio/*").build();
        return execute(slow, request, cancel, ResponseBody::bytes);
    }

    public Extracted extract(byte[] bytes, String fileName, String mime, Cancel cancel) throws Exception {
        RequestBody file = RequestBody.create(bytes, MediaType.parse(mime == null ? "application/octet-stream" : mime));
        MultipartBody form = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, file).build();
        Request request = new Request.Builder().url(base.resolve("api/extract")).post(form).build();
        return call(slow, request, raw -> gson.fromJson(raw, Extracted.class), cancel);
    }

    public ProjectRecord project(String id, Cancel cancel) throws Exception {
        return call(http, get("api/projects/" + id), raw -> gson.fromJson(gson.fromJson(raw, JsonObject.class).get("project"), ProjectRecord.class), cancel);
    }

    public String createProject(JsonObject payload, Cancel cancel) throws Exception {
        return call(http, post("api/projects", payload), raw -> gson.fromJson(raw, JsonObject.class).get("id").getAsString(), cancel);
    }

    public ProjectRecord updateProject(String id, JsonObject payload, Cancel cancel) throws Exception {
        Request request = new Request.Builder().url(base.resolve("api/projects/" + id))
                .put(RequestBody.create(gson.toJson(payload), JSON)).build();
        return call(slow, request, raw -> {
            JsonObject o = gson.fromJson(raw, JsonObject.class);
            JsonElement p = o == null ? null : o.get("project");
            return p == null || p.isJsonNull() ? null : gson.fromJson(p, ProjectRecord.class);
        }, cancel);
    }

    public void deleteProject(String id, Cancel cancel) throws Exception {
        Request request = new Request.Builder().url(base.resolve("api/projects/" + id)).delete().build();
        call(http, request, raw -> null, cancel);
    }

    public UploadTicket signUpload(String projectId, Cancel cancel) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("projectId", projectId);
        return call(http, post("api/uploads/sign", body), raw -> gson.fromJson(raw, UploadTicket.class), cancel);
    }

    public Uploaded upload(UploadTicket ticket, File file, UploadProgress progress, Cancel cancel) throws Exception {
        if (file.length() > ticket.maxBytes) {
            throw new ApiException(ApiException.Kind.HTTP, 413, null, app.getString(R.string.err_too_large));
        }
        MultipartBody.Builder form = new MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "audiobook.wav", new ProgressBody(file, progress));
        for (Map.Entry<String, Object> e : ticket.params.entrySet()) form.addFormDataPart(e.getKey(), plain(e.getValue()));
        form.addFormDataPart("api_key", ticket.apiKey);
        form.addFormDataPart("signature", ticket.signature);
        Request request = new Request.Builder().url(ticket.uploadUrl).post(form.build()).build();
        Call c = upload.newCall(request);
        cancel.register(c);
        try (Response res = c.execute()) {
            String raw = res.body() == null ? "" : res.body().string();
            JsonObject o;
            try {
                o = gson.fromJson(raw, JsonObject.class);
            } catch (Exception e) {
                o = null;
            }
            if (res.isSuccessful() && o != null && o.has("public_id") && o.has("version")) {
                Uploaded up = new Uploaded();
                up.publicId = o.get("public_id").getAsString();
                up.version = o.get("version").getAsLong();
                up.bytes = o.has("bytes") ? o.get("bytes").getAsLong() : file.length();
                return up;
            }
            String message = null;
            if (o != null && o.has("error") && o.get("error").isJsonObject() && o.getAsJsonObject("error").has("message")) {
                message = o.getAsJsonObject("error").get("message").getAsString();
            }
            throw new ApiException(ApiException.Kind.HTTP, res.code(), null,
                    message != null ? message : app.getString(R.string.err_upload_failed, res.code()));
        } catch (IOException e) {
            if (cancel.isCancelled()) throw new Cancel.Cancelled();
            throw new ApiException(ApiException.Kind.NETWORK, 0, null, app.getString(R.string.err_upload_network));
        } finally {
            cancel.unregister(c);
        }
    }

    public void changePassword(String current, String next, Cancel cancel) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("current", current);
        body.addProperty("next", next);
        call(http, post("api/auth/password", body), raw -> null, cancel);
    }

    public void deleteAccount(String password, Cancel cancel) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("password", password);
        Request request = new Request.Builder()
                .url(base.resolve("api/auth/account"))
                .delete(RequestBody.create(gson.toJson(body), JSON))
                .build();
        call(http, request, raw -> null, cancel);
        store.clear();
    }

    public HttpUrl pageUrl(String path) {
        return base.resolve(path);
    }

    public <T> Cancel run(Util.Task<T> task, Callback<T> callback) {
        Cancel cancel = new Cancel();
        background.execute(() -> {
            try {
                T value = task.run();
                main.post(() -> {
                    if (!cancel.isCancelled()) callback.onSuccess(value);
                });
            } catch (Cancel.Cancelled ignored) {
            } catch (Exception e) {
                ApiException error = e instanceof ApiException ? (ApiException) e
                        : new ApiException(ApiException.Kind.PARSE, 0, null,
                        e.getMessage() != null ? e.getMessage() : app.getString(R.string.err_unexpected));
                main.post(() -> {
                    if (!cancel.isCancelled()) callback.onError(error);
                });
            }
        });
        return cancel;
    }

    public ExecutorService background() {
        return background;
    }

    private static String plain(Object v) {
        if (v instanceof Double) {
            double d = (Double) v;
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return String.valueOf(v);
    }

    private interface BodyReader<T> {
        T read(ResponseBody body) throws IOException;
    }

    private <T> T execute(OkHttpClient client, Request request, Cancel cancel, BodyReader<T> reader) throws Exception {
        cancel.check();
        Call c = client.newCall(request);
        cancel.register(c);
        try (Response res = c.execute()) {
            ResponseBody body = res.body();
            if (!res.isSuccessful()) {
                String raw = body == null ? "" : body.string();
                ApiException error = httpError(res.code(), raw);
                if (error.isUnauthorized()) store.clear();
                throw error;
            }
            return reader.read(body);
        } catch (IOException e) {
            if (cancel.isCancelled()) throw new Cancel.Cancelled();
            throw transportError(e);
        } finally {
            cancel.unregister(c);
        }
    }

    private <T> T call(OkHttpClient client, Request request, Parser<T> parser, Cancel cancel) throws Exception {
        String raw = execute(client, request, cancel, b -> b == null ? "" : b.string());
        try {
            return parser.parse(raw);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(ApiException.Kind.PARSE, 200, null, app.getString(R.string.err_unexpected));
        }
    }

    private User signedInUser(String body) {
        Responses.UserEnvelope envelope = gson.fromJson(body, Responses.UserEnvelope.class);
        if (envelope == null || envelope.user == null || store.token() == null) {
            throw new IllegalStateException("missing session");
        }
        store.saveUser(envelope.user);
        return envelope.user;
    }

    private void captureSession(Response response) {
        for (String header : response.headers("Set-Cookie")) {
            if (!header.startsWith(SESSION_COOKIE + "=")) continue;
            int end = header.indexOf(';');
            String value = header.substring(SESSION_COOKIE.length() + 1, end < 0 ? header.length() : end).trim();
            String lower = header.toLowerCase(Locale.ROOT);
            boolean expired = value.isEmpty() || lower.contains("max-age=0") || lower.contains("expires=thu, 01 jan 1970");
            if (expired) store.clear();
            else store.saveToken(value);
        }
    }

    private Request get(String path) {
        return new Request.Builder().url(base.resolve(path)).get().build();
    }

    private Request post(String path, JsonObject body) {
        return new Request.Builder()
                .url(base.resolve(path))
                .post(RequestBody.create(gson.toJson(body), JSON))
                .build();
    }

    private <T> Call enqueue(Request request, Parser<T> parser, Callback<T> callback) {
        Call call = http.newCall(request);
        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull Call c, @NonNull IOException e) {
                ApiException error = transportError(e);
                deliver(c, () -> callback.onError(error));
            }

            @Override
            public void onResponse(@NonNull Call c, @NonNull Response response) {
                String raw;
                try (ResponseBody body = response.body()) {
                    raw = body == null ? "" : body.string();
                } catch (IOException e) {
                    ApiException error = transportError(e);
                    deliver(c, () -> callback.onError(error));
                    return;
                }
                if (!response.isSuccessful()) {
                    ApiException error = httpError(response.code(), raw);
                    if (error.isUnauthorized()) store.clear();
                    deliver(c, () -> callback.onError(error));
                    return;
                }
                try {
                    T value = parser.parse(raw);
                    deliver(c, () -> callback.onSuccess(value));
                } catch (Exception e) {
                    ApiException error = new ApiException(ApiException.Kind.PARSE, response.code(), null,
                            app.getString(R.string.err_unexpected));
                    deliver(c, () -> callback.onError(error));
                }
            }
        });
        return call;
    }

    private void deliver(Call call, Runnable action) {
        main.post(() -> {
            if (!call.isCanceled()) action.run();
        });
    }

    private ApiException transportError(IOException e) {
        if (e instanceof InterruptedIOException) {
            return new ApiException(ApiException.Kind.TIMEOUT, 0, null, app.getString(R.string.err_timeout));
        }
        return new ApiException(ApiException.Kind.NETWORK, 0, null, app.getString(R.string.err_network));
    }

    private ApiException httpError(int status, String raw) {
        String message = null;
        String code = null;
        try {
            Responses.ErrorEnvelope envelope = gson.fromJson(raw, Responses.ErrorEnvelope.class);
            if (envelope != null) {
                message = envelope.error;
                code = envelope.code;
            }
        } catch (Exception ignored) {
        }
        if (message == null || message.trim().isEmpty()) {
            if (status == 401) message = app.getString(R.string.err_session);
            else if (status >= 500) message = app.getString(R.string.err_server, status);
            else message = app.getString(R.string.err_unexpected);
        }
        return new ApiException(ApiException.Kind.HTTP, status, code, message);
    }

    private static final class ProgressBody extends RequestBody {
        private static final MediaType WAV = MediaType.get("audio/wav");
        private final File file;
        private final UploadProgress progress;

        ProgressBody(File file, UploadProgress progress) {
            this.file = file;
            this.progress = progress;
        }

        @Override
        public MediaType contentType() {
            return WAV;
        }

        @Override
        public long contentLength() {
            return file.length();
        }

        @Override
        public void writeTo(@NonNull BufferedSink sink) throws IOException {
            long total = file.length();
            long sent = 0;
            long lastReport = 0;
            byte[] buf = new byte[64 * 1024];
            try (InputStream in = new FileInputStream(file)) {
                int n;
                while ((n = in.read(buf)) != -1) {
                    sink.write(buf, 0, n);
                    sent += n;
                    if (progress != null && (sent - lastReport > total / 100 || sent == total)) {
                        lastReport = sent;
                        progress.onProgress(total == 0 ? 1 : sent / (double) total);
                    }
                }
            }
        }
    }
}
