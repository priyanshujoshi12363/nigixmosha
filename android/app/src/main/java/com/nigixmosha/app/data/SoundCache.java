package com.nigixmosha.app.data;

import android.content.Context;

import com.nigixmosha.app.engine.AudioCodec;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Mixer;
import com.nigixmosha.app.engine.Sounds;
import com.nigixmosha.app.engine.model.Types;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class SoundCache {
    private static final long MAX_CACHE_BYTES = 40L * 1024 * 1024;

    private final File dir;
    private final OkHttpClient http;
    private final Map<String, short[]> memory = new LinkedHashMap<>();

    public SoundCache(Context context, OkHttpClient http) {
        this.dir = new File(context.getCacheDir(), "sounds");
        this.http = http;
        if (!dir.exists()) dir.mkdirs();
    }

    public Mixer.Source source(Cancel cancel) {
        return (tag, nth) -> clip(tag, nth, cancel);
    }

    public short[] clip(String tag, int nth, Cancel cancel) throws Exception {
        Types.SoundVariant variant = Sounds.get().variant(tag, nth);
        if (variant == null || variant.url == null) return null;
        String key = tag + "/" + variant.id;
        synchronized (memory) {
            short[] hit = memory.get(key);
            if (hit != null) return hit;
        }
        File file = new File(dir, variant.id + ".mp3");
        if (!file.exists() || file.length() < 1024) download(variant.url, file, cancel);
        short[] samples = AudioCodec.decode(readAll(file));
        synchronized (memory) {
            if (memory.size() > 12) memory.remove(memory.keySet().iterator().next());
            memory.put(key, samples);
        }
        file.setLastModified(System.currentTimeMillis());
        return samples;
    }

    private void download(String url, File file, Cancel cancel) throws IOException {
        File temp = new File(file.getAbsolutePath() + ".part");
        Request request = new Request.Builder().url(url).get().build();
        okhttp3.Call call = http.newCall(request);
        cancel.register(call);
        try (Response response = call.execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) throw new IOException("sound " + response.code());
            try (InputStream in = body.byteStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            }
        } finally {
            cancel.unregister(call);
        }
        if (file.exists()) file.delete();
        if (!temp.renameTo(file)) throw new IOException("sound cache write failed");
        prune();
    }

    private static byte[] readAll(File file) throws IOException {
        byte[] out = new byte[(int) file.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int offset = 0;
            while (offset < out.length) {
                int read = in.read(out, offset, out.length - offset);
                if (read <= 0) break;
                offset += read;
            }
        }
        return out;
    }

    private void prune() {
        File[] files = dir.listFiles();
        if (files == null) return;
        long total = 0;
        for (File f : files) total += f.length();
        if (total <= MAX_CACHE_BYTES) return;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (File f : files) {
            if (total <= MAX_CACHE_BYTES) break;
            long size = f.length();
            if (f.delete()) total -= size;
        }
    }

    public void clear() {
        synchronized (memory) {
            memory.clear();
        }
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) f.delete();
    }
}
