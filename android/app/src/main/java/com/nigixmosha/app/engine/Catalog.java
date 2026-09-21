package com.nigixmosha.app.engine;

import android.content.Context;

import com.google.gson.Gson;
import com.nigixmosha.app.engine.model.Types;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Catalog {
    private static volatile Catalog instance;

    public List<Types.ProviderMeta> providers = new ArrayList<>();
    public Map<String, List<Types.VoiceProfile>> voices;
    public List<Types.VoiceProfile> edgeFallback = new ArrayList<>();
    public List<Types.Language> languages = new ArrayList<>();
    public List<Types.Sample> samples = new ArrayList<>();
    public List<String> colors = new ArrayList<>();
    public String narratorColor;
    public String narratorOnDark;

    public static Catalog get() {
        if (instance == null) throw new IllegalStateException("Catalog not loaded");
        return instance;
    }

    public static synchronized void load(Context context) {
        if (instance != null) return;
        try (Reader reader = new InputStreamReader(context.getAssets().open("catalog.json"), StandardCharsets.UTF_8)) {
            instance = new Gson().fromJson(reader, Catalog.class);
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the voice catalog", e);
        }
    }

    public static synchronized void loadFrom(Reader reader) {
        instance = new Gson().fromJson(reader, Catalog.class);
    }

    public Types.ProviderMeta provider(String id) {
        for (Types.ProviderMeta p : providers) if (p.id.equals(id)) return p;
        return providers.get(0);
    }

    public List<Types.VoiceProfile> staticVoices(String provider, String model) {
        Types.ProviderMeta meta = provider(provider);
        List<Types.VoiceProfile> list = voices.get(meta.id + "|" + model);
        if (list == null) list = voices.get(meta.id + "|" + meta.defaultModel);
        return list == null ? Collections.emptyList() : list;
    }

    public String color(int index) {
        return colors.get(Math.floorMod(index, colors.size()));
    }

    public String backupVoice(String gender, String language, String seed) {
        String prefix = language.split("-")[0].toLowerCase(Locale.ROOT);
        List<Types.VoiceProfile> sameLang = new ArrayList<>();
        for (Types.VoiceProfile v : edgeFallback) {
            for (String l : v.langs) {
                if (l.split("-")[0].toLowerCase(Locale.ROOT).equals(prefix)) {
                    sameLang.add(v);
                    break;
                }
            }
        }
        List<Types.VoiceProfile> pool = sameLang;
        if (pool.isEmpty()) {
            pool = new ArrayList<>();
            for (Types.VoiceProfile v : edgeFallback) if (v.langs.get(0).startsWith("en")) pool.add(v);
        }
        List<Types.VoiceProfile> matched = new ArrayList<>();
        for (Types.VoiceProfile v : pool) if (v.gender.equals(gender)) matched.add(v);
        List<Types.VoiceProfile> list = matched.isEmpty() ? pool : matched;
        int hash = 0;
        String s = seed == null ? "" : seed;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int unit = s.charAt(i);
            hash = (hash * 31 + unit) % 9973;
            i += Character.charCount(cp);
        }
        return list.get(hash % list.size()).id;
    }
}
