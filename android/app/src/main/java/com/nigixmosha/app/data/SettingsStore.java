package com.nigixmosha.app.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.nigixmosha.app.data.model.ServerConfig;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.model.Types;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SettingsStore {
    public interface Listener {
        void onSettingsChanged();
    }

    private static final String PREFS = "nigix_settings";
    private static volatile SettingsStore instance;

    private final SharedPreferences prefs;
    private final SecureBox box = new SecureBox("nigix_voice_keys");
    private final Gson gson = new Gson();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Types.ProviderConfig> tts = new HashMap<>();
    private String activeTTS;
    private Types.Advanced advanced;
    private ServerConfig server;

    public static SettingsStore get(Context context) {
        if (instance == null) {
            synchronized (SettingsStore.class) {
                if (instance == null) instance = new SettingsStore(context.getApplicationContext());
            }
        }
        return instance;
    }

    private SettingsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Catalog catalog = Catalog.get();
        for (Types.ProviderMeta p : catalog.providers) {
            Types.ProviderConfig cfg = new Types.ProviderConfig();
            cfg.baseUrl = prefs.getString("base_" + p.id, p.defaultBaseUrl);
            cfg.model = prefs.getString("model_" + p.id, p.defaultModel);
            String sealed = prefs.getString("key_" + p.id, null);
            if (sealed != null) {
                try {
                    cfg.apiKey = box.open(sealed);
                } catch (Exception e) {
                    prefs.edit().remove("key_" + p.id).apply();
                    cfg.apiKey = "";
                }
            }
            tts.put(p.id, cfg);
        }
        String active = prefs.getString("active", "nigix");
        boolean known = false;
        for (Types.ProviderMeta p : catalog.providers) if (p.id.equals(active)) known = true;
        activeTTS = known ? active : "nigix";
        Types.Advanced adv = null;
        try {
            adv = gson.fromJson(prefs.getString("advanced", null), Types.Advanced.class);
        } catch (Exception ignored) {
        }
        advanced = adv != null ? adv : new Types.Advanced();
        ServerConfig sc = null;
        try {
            sc = gson.fromJson(prefs.getString("server", null), ServerConfig.class);
        } catch (Exception ignored) {
        }
        server = sc != null ? sc : new ServerConfig();
    }

    public void addListener(Listener l) {
        listeners.addIfAbsent(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void changed() {
        main.post(() -> {
            for (Listener l : listeners) l.onSettingsChanged();
        });
    }

    public synchronized String activeTTS() {
        return activeTTS;
    }

    public synchronized Types.ProviderConfig config(String id) {
        Types.ProviderConfig src = tts.get(id);
        Types.ProviderConfig copy = new Types.ProviderConfig();
        if (src != null) {
            copy.apiKey = src.apiKey;
            copy.baseUrl = src.baseUrl;
            copy.model = src.model;
        }
        return copy;
    }

    public synchronized String model(String id) {
        Types.ProviderConfig cfg = tts.get(id);
        String m = cfg == null ? "" : cfg.model;
        return m == null || m.isEmpty() ? Catalog.get().provider(id).defaultModel : m;
    }

    public synchronized Types.Advanced advanced() {
        Types.Advanced a = new Types.Advanced();
        a.ttsConcurrency = advanced.ttsConcurrency;
        a.lineGapMs = advanced.lineGapMs;
        a.speakerGapMs = advanced.speakerGapMs;
        a.paragraphGapMs = advanced.paragraphGapMs;
        a.normalize = advanced.normalize;
        a.soundscape = advanced.soundscape == null ? "subtle" : advanced.soundscape;
        return a;
    }

    public synchronized ServerConfig server() {
        return server;
    }

    public void setActiveTTS(String id) {
        synchronized (this) {
            if (id.equals(activeTTS)) return;
            activeTTS = id;
            prefs.edit().putString("active", id).apply();
        }
        changed();
    }

    public void setApiKey(String id, String key) {
        synchronized (this) {
            Types.ProviderConfig cfg = tts.get(id);
            if (cfg == null) return;
            cfg.apiKey = key;
            try {
                if (key.isEmpty()) prefs.edit().remove("key_" + id).apply();
                else prefs.edit().putString("key_" + id, box.seal(key)).apply();
            } catch (Exception ignored) {
            }
        }
        changed();
    }

    public void setModel(String id, String model) {
        synchronized (this) {
            Types.ProviderConfig cfg = tts.get(id);
            if (cfg == null) return;
            cfg.model = model;
            prefs.edit().putString("model_" + id, model).apply();
        }
        changed();
    }

    public void setBaseUrl(String id, String url) {
        synchronized (this) {
            Types.ProviderConfig cfg = tts.get(id);
            if (cfg == null) return;
            cfg.baseUrl = url;
            prefs.edit().putString("base_" + id, url).apply();
        }
        changed();
    }

    public void setAdvanced(Types.Advanced next) {
        synchronized (this) {
            advanced = next;
            prefs.edit().putString("advanced", gson.toJson(next)).apply();
        }
        changed();
    }

    public void resetAdvanced() {
        setAdvanced(new Types.Advanced());
    }

    public void clearKeys() {
        synchronized (this) {
            SharedPreferences.Editor e = prefs.edit();
            for (Map.Entry<String, Types.ProviderConfig> entry : tts.entrySet()) {
                entry.getValue().apiKey = "";
                e.remove("key_" + entry.getKey());
            }
            e.apply();
        }
        changed();
    }

    public void setServer(ServerConfig config) {
        synchronized (this) {
            server = config;
            prefs.edit().putString("server", gson.toJson(config)).apply();
        }
        changed();
    }

    public synchronized int themeMode() {
        return prefs.getInt("theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt("theme", mode).apply();
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode);
    }

    public synchronized boolean isReady(String id) {
        Types.ProviderMeta meta = Catalog.get().provider(id);
        Types.ProviderConfig cfg = tts.get(id);
        if (cfg == null) return false;
        if ("nigix".equals(id)) return server.hasServerKey("nigix");
        return !meta.needsKey || (cfg.apiKey != null && !cfg.apiKey.trim().isEmpty()) || server.hasServerKey(id);
    }
}
