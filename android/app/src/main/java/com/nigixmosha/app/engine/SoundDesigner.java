package com.nigixmosha.app.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SoundDesigner {
    private static final int LINES_PER_PASS = 150;
    private static final int TEXT_PREVIEW = 110;

    public interface Listener {
        void onProgress(int done, int total);
    }

    private static final String SOUND_SYSTEM =
            "You are the sound designer of an audiobook studio. You read a scripted chapter and decide what the listener should hear behind the voices: the ambience of each scene and the handful of sound effects the story actually calls for. You are restrained — silence is better than a sound that does not belong. You always answer with a single JSON object and nothing else.";

    private static String soundPrompt(Types.Analysis analysis, String lines, int offset, int count) {
        Sounds sounds = Sounds.get();
        int last = offset + count - 1;
        return "Chapter: \"" + analysis.title + "\"" + (analysis.summary == null || analysis.summary.isEmpty() ? "" : "\n" + analysis.summary) + "\n"
                + "\nAmbience tags (continuous backgrounds):\n" + sounds.menu("bed") + "\n"
                + "\nEffect tags (single sounds):\n" + sounds.menu("shot") + "\n"
                + "\nReturn JSON with exactly this shape:\n"
                + "{\n"
                + "  \"scenes\": [{ \"from\": " + offset + ", \"to\": " + last + ", \"tag\": \"ambience tag\", \"fallback\": \"second choice tag\", \"prompt\": \"what you would record if no tag fits\", \"intensity\": 0.6 }],\n"
                + "  \"cues\": [{ \"at\": " + offset + ", \"tag\": \"effect tag\", \"fallback\": \"second choice tag\", \"prompt\": \"what you would record if no tag fits\", \"place\": \"before\", \"gain\": 0.8 }]\n"
                + "}\n"
                + "\nRules:\n"
                + "1. A scene is a run of consecutive lines that share one place and mood. Cover the lines from " + offset + " to " + last + " in order, without gaps or overlaps. Most chapters have one to four scenes.\n"
                + "2. Use a tag from the lists above whenever one is close enough. Only when nothing fits, still give your nearest \"tag\" and describe the real sound in \"prompt\".\n"
                + "3. \"intensity\" is 0.2 for a barely-there room tone, 0.6 for ordinary weather or a street, 1.0 for a storm or a crowd at its loudest.\n"
                + "4. Cues are rare: at most one per ten lines, and only for a sound the text actually states, such as a knock, a gunshot, a phone, a door, thunder. Never add a cue for something merely imagined or remembered.\n"
                + "5. \"place\": \"before\" for a sound that happens just before the line is spoken, \"under\" for one during it, \"after\" for one just after.\n"
                + "6. \"gain\" is 0.3 for something distant, 1.0 for something in the room.\n"
                + "7. If a passage should be silent underneath, leave it out of \"scenes\" rather than inventing ambience.\n"
                + "\nLINES:\n" + lines;
    }

    private static String lineBlock(List<Types.Segment> segments, Types.Analysis analysis, int from, int to) {
        Map<String, String> names = new HashMap<>();
        for (Types.CastCharacter c : analysis.characters) names.put(c.id, c.name);
        StringBuilder sb = new StringBuilder();
        for (int i = from; i <= to; i++) {
            Types.Segment seg = segments.get(i);
            String who = "narrator".equals(seg.speaker) ? "narrator"
                    : names.containsKey(seg.speaker) ? names.get(seg.speaker) : seg.speaker;
            String text = seg.text == null ? "" : seg.text;
            if (text.length() > TEXT_PREVIEW) text = text.substring(0, TEXT_PREVIEW) + "…";
            if (sb.length() > 0) sb.append("\n");
            sb.append(i).append(". [").append(who).append("] ").append(text.replaceAll("\\s+", " "));
        }
        return sb.toString();
    }

    private static double num(JsonObject o, String key, double fallback) {
        try {
            JsonElement el = o.get(key);
            if (el == null || el.isJsonNull()) return fallback;
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) return el.getAsDouble();
            return Double.parseDouble(el.getAsString().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Types.Soundscape plan(Types.Analysis analysis, List<Types.Segment> segments,
                                        Director.Brain brain, Listener listener, Cancel cancel) throws Exception {
        Sounds sounds = Sounds.get();
        Types.Soundscape out = new Types.Soundscape();
        out.engine = "nigixmosha sound designer";
        if (sounds.isEmpty() || segments.isEmpty()) return out;

        int passes = Math.max(1, (int) Math.ceil(segments.size() / (double) LINES_PER_PASS));
        List<Types.SoundScene> scenes = new ArrayList<>();

        for (int pass = 0; pass < passes; pass++) {
            cancel.check();
            final int from = pass * LINES_PER_PASS;
            final int to = Math.min(segments.size() - 1, from + LINES_PER_PASS - 1);
            String raw = Util.withRetry(
                    () -> brain.ask(SOUND_SYSTEM, soundPrompt(analysis, lineBlock(segments, analysis, from, to), from, to - from + 1)),
                    2, cancel);
            JsonElement parsed = Util.extractJSON(raw);
            if (!parsed.isJsonObject()) continue;
            JsonObject root = parsed.getAsJsonObject();

            JsonArray rawScenes = root.has("scenes") && root.get("scenes").isJsonArray() ? root.getAsJsonArray("scenes") : new JsonArray();
            for (JsonElement el : rawScenes) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String wanted = Util.str(o.get("prompt"));
                if (wanted.isEmpty()) wanted = Util.str(o.get("tag"));
                String tag = sounds.resolve(Util.str(o.get("tag")), Util.str(o.get("fallback")));
                if (tag == null) {
                    if (!wanted.isEmpty() && !out.missing.contains(wanted)) out.missing.add(wanted);
                    continue;
                }
                Types.SoundScene scene = new Types.SoundScene();
                scene.from = (int) Math.max(from, Math.min(to, Math.round(num(o, "from", from))));
                scene.to = (int) Math.max(scene.from, Math.min(to, Math.round(num(o, "to", to))));
                scene.tag = tag;
                scene.intensity = Math.max(0.1, Math.min(1, num(o, "intensity", 0.6)));
                scene.wanted = wanted;
                scenes.add(scene);
            }

            JsonArray rawCues = root.has("cues") && root.get("cues").isJsonArray() ? root.getAsJsonArray("cues") : new JsonArray();
            for (JsonElement el : rawCues) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String wanted = Util.str(o.get("prompt"));
                if (wanted.isEmpty()) wanted = Util.str(o.get("tag"));
                String tag = sounds.resolve(Util.str(o.get("tag")), Util.str(o.get("fallback")));
                int at = (int) Math.round(num(o, "at", -1));
                if (at < from || at > to) continue;
                if (tag == null) {
                    if (!wanted.isEmpty() && !out.missing.contains(wanted)) out.missing.add(wanted);
                    continue;
                }
                Types.SoundCue cue = new Types.SoundCue();
                cue.at = at;
                cue.tag = tag;
                String place = Util.str(o.get("place")).toLowerCase(Locale.ROOT);
                cue.placement = "under".equals(place) ? "under" : "after".equals(place) ? "after" : "before";
                cue.gain = Math.max(0.15, Math.min(1, num(o, "gain", 0.8)));
                cue.wanted = wanted;
                out.cues.add(cue);
            }
            if (listener != null) listener.onProgress(pass + 1, passes);
        }

        scenes.sort((a, b) -> Integer.compare(a.from, b.from));
        for (Types.SoundScene scene : scenes) {
            Types.SoundScene prev = out.scenes.isEmpty() ? null : out.scenes.get(out.scenes.size() - 1);
            if (prev != null && prev.tag.equals(scene.tag) && scene.from <= prev.to + 1) {
                prev.to = Math.max(prev.to, scene.to);
                prev.intensity = Math.max(prev.intensity, scene.intensity);
                continue;
            }
            if (prev != null && scene.from <= prev.to) scene.from = prev.to + 1;
            if (scene.from <= scene.to) out.scenes.add(scene);
        }

        out.cues.sort((a, b) -> Integer.compare(a.at, b.at));
        List<Types.SoundCue> trimmed = new ArrayList<>();
        for (Types.SoundCue cue : out.cues) {
            Types.SoundCue prev = trimmed.isEmpty() ? null : trimmed.get(trimmed.size() - 1);
            if (prev != null && prev.at == cue.at && prev.tag.equals(cue.tag)) continue;
            trimmed.add(cue);
        }
        out.cues = trimmed;
        return out;
    }

    public static String summary(Types.Soundscape plan) {
        if (plan == null || plan.isEmpty()) return "";
        List<String> parts = new ArrayList<>();
        if (!plan.scenes.isEmpty()) parts.add(plan.scenes.size() + " ambience scene" + (plan.scenes.size() > 1 ? "s" : ""));
        if (!plan.cues.isEmpty()) parts.add(plan.cues.size() + " effect" + (plan.cues.size() > 1 ? "s" : ""));
        return String.join(" · ", parts);
    }
}
