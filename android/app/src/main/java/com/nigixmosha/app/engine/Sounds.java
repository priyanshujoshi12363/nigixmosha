package com.nigixmosha.app.engine;

import android.content.Context;

import com.google.gson.Gson;
import com.nigixmosha.app.engine.model.Types;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Sounds {
    private static Sounds instance;

    private final List<Types.SoundTag> tags = new ArrayList<>();
    private final Map<String, Types.SoundTag> byTag = new HashMap<>();

    private Sounds(Types.SoundCatalog catalog) {
        if (catalog != null && catalog.tags != null) {
            for (Types.SoundTag tag : catalog.tags) {
                if (tag == null || tag.tag == null || tag.variants == null || tag.variants.isEmpty()) continue;
                tags.add(tag);
                byTag.put(tag.tag, tag);
            }
        }
    }

    public static synchronized Sounds get() {
        if (instance == null) instance = new Sounds(null);
        return instance;
    }

    public static synchronized void load(Context context) {
        if (instance != null) return;
        try {
            load(context.getAssets().open("sounds.json"));
        } catch (Exception e) {
            instance = new Sounds(null);
        }
    }

    public static synchronized void load(InputStream stream) {
        Types.SoundCatalog catalog = null;
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            catalog = new Gson().fromJson(reader, Types.SoundCatalog.class);
        } catch (Exception ignored) {
        }
        instance = new Sounds(catalog);
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public Types.SoundTag tag(String id) {
        return id == null ? null : byTag.get(id);
    }

    public String menu(String kind) {
        StringBuilder sb = new StringBuilder();
        for (Types.SoundTag tag : tags) {
            if (!tag.kind.equals(kind)) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(tag.tag).append(" (").append(tag.label.toLowerCase(Locale.ROOT)).append(")");
        }
        return sb.toString();
    }

    public Types.SoundVariant variant(String id, int nth) {
        Types.SoundTag tag = tag(id);
        if (tag == null || tag.variants.isEmpty()) return null;
        return tag.variants.get(Math.abs(nth) % tag.variants.size());
    }

    public String resolve(String wanted, String fallback) {
        for (String candidate : new String[]{wanted, fallback}) {
            if (candidate == null) continue;
            String key = candidate.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
            if (key.isEmpty()) continue;
            if (byTag.containsKey(key)) return key;
            for (Types.SoundTag tag : tags) {
                if (tag.tag.startsWith(key) || key.startsWith(tag.tag)) return tag.tag;
            }
        }
        return null;
    }
}
