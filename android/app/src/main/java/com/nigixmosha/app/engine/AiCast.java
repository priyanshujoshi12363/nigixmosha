package com.nigixmosha.app.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AiCast {
    public interface Brain {
        String ask(String system, String prompt) throws Exception;
    }

    public static final class Outcome {
        public Map<String, Types.CastEntry> cast;
        public Map<String, String> reasons = new LinkedHashMap<>();
        public String by;
        public String note;
    }

    private static final int MAX_VOICES = 90;
    private static final String FALLBACK_NOTE = "The director couldn't finish casting this time, so voices were matched automatically.";
    private static final String SYSTEM =
            "You are the voice casting director of a professional audiobook studio. You match every speaker of a story to the single best voice from a voice catalogue, judging gender, age, personality, speaking style and the story's language, and you keep every main character clearly distinguishable. You always answer with a single JSON object and nothing else.";

    private AiCast() {
    }

    private static String voiceLine(Types.VoiceProfile v) {
        List<String> who = new ArrayList<>();
        if (v.gender != null && !v.gender.isEmpty()) who.add(v.gender);
        if (v.age != null && !v.age.isEmpty()) who.add(v.age);
        String langs = v.langs.contains("*") ? "any language" : String.join("/", v.langs);
        List<String> tags = v.tags.subList(0, Math.min(5, v.tags.size()));
        return v.id + " | " + v.name + " | " + String.join(", ", who) + " | " + langs
                + (v.isMultilingual() ? " (multilingual)" : "") + " | " + String.join(", ", tags);
    }

    private static String prompt(Types.Analysis a, List<Types.VoiceProfile> voices, boolean pitchOK, String engine) {
        StringBuilder speakers = new StringBuilder();
        speakers.append("- narrator: ").append(a.narrator.gender).append(" narrator; tone: ").append(a.narrator.tone)
                .append("; ").append(a.pov).append("-person story");
        for (Types.CastCharacter c : Casting.byLines(a)) {
            speakers.append("\n- ").append(c.id).append(": ").append(c.name).append(" — ").append(c.gender).append(", ")
                    .append(c.age).append(", ").append(c.role).append(", ").append(c.lineCount).append(" lines; personality: ")
                    .append(c.personality.isEmpty() ? "unknown" : String.join(", ", c.personality))
                    .append("; speaks: ").append(c.speakingStyle == null || c.speakingStyle.isEmpty() ? "n/a" : c.speakingStyle)
                    .append("; ideal voice: ").append(c.voiceDescription == null || c.voiceDescription.isEmpty() ? "n/a" : c.voiceDescription);
        }
        StringBuilder catalogue = new StringBuilder();
        for (int i = 0; i < voices.size(); i++) {
            if (i > 0) catalogue.append('\n');
            catalogue.append(voiceLine(voices.get(i)));
        }
        return "Cast the audiobook \"" + a.title + "\" (" + Lang.languageName(a.language) + ") with the " + engine + " voice engine.\n\n"
                + "Return JSON: {\"cast\": {\"narrator\": {\"voice\": \"voice id\", \"pitch\": 0, \"rate\": 0, \"why\": \"one short sentence\"}, \"c1\": {\"voice\": \"...\", \"pitch\": 0, \"rate\": 0, \"why\": \"...\"}}}\n"
                + "Include every speaker listed below, keyed by its id.\n\n"
                + "Rules:\n"
                + "1. Use only voice ids from the catalogue, copied exactly.\n"
                + "2. Match gender first, then age: youthful voices for children and teens, mature voices for elders.\n"
                + "3. Give every main character a different voice. Reuse a voice only for minor characters, and then shift pitch or rate so they still sound distinct.\n"
                + "4. The narrator needs a clear, steady storytelling voice that is different from the lead characters.\n"
                + "5. Prefer voices native to the story's language; use multilingual voices when needed.\n"
                + "6. \"rate\" is a pace offset in percent from -30 to 30 that fits how the speaker talks: slow, wise or menacing below 0; quick, excitable or young above 0.\n"
                + "7. \"pitch\" is an offset in percent from -20 to 20" + (pitchOK ? "" : ". This engine cannot shift pitch, so always use 0") + ".\n"
                + "8. \"why\" explains the choice in one short sentence about the voice, without naming any software.\n\n"
                + "SPEAKERS:\n" + speakers + "\n\n"
                + "VOICE CATALOGUE (id | name | gender, age | languages | traits):\n" + catalogue;
    }

    private static void keepDistinct(Map<String, Types.CastEntry> cast, Types.Analysis a, boolean pitchOK) {
        int[] nudges = {0, 8, -8, 14, -14, 5, -5};
        Map<String, Integer> seen = new HashMap<>();
        List<String> order = new ArrayList<>();
        order.add(Types.NARRATOR_ID);
        for (Types.CastCharacter c : Casting.byLines(a)) order.add(c.id);
        for (String id : order) {
            Types.CastEntry entry = cast.get(id);
            if (entry == null) continue;
            int n = seen.getOrDefault(entry.voiceId, 0);
            seen.put(entry.voiceId, n + 1);
            if (n == 0) continue;
            int k = nudges[n % nudges.length];
            cast.put(id, new Types.CastEntry(entry.voiceId,
                    pitchOK ? Util.clamp(entry.pitch + k, -30, 30) : entry.pitch,
                    Util.clamp(entry.rate + (pitchOK ? k / 2.0 : k), -35, 35)));
        }
    }

    private static double number(JsonElement el) {
        try {
            return el == null || el.isJsonNull() ? 0 : el.getAsDouble();
        } catch (Exception e) {
            return 0;
        }
    }

    public static Outcome castVoices(Types.Analysis analysis, List<Types.VoiceProfile> voices, Types.ProviderMeta meta,
                                     String model, Brain brain, Cancel cancel) throws Exception {
        Map<String, Types.CastEntry> rules = Casting.autoCast(analysis, voices, meta, model);
        List<Casting.Candidate> pool = Casting.candidateVoices(voices, meta, analysis.language);
        List<Types.VoiceProfile> catalogue = new ArrayList<>();
        for (Casting.Candidate c : pool) if (c.nativeVoice) catalogue.add(c.voice);
        for (Casting.Candidate c : pool) if (!c.nativeVoice) catalogue.add(c.voice);
        if (catalogue.size() > MAX_VOICES) catalogue = new ArrayList<>(catalogue.subList(0, MAX_VOICES));
        Outcome outcome = new Outcome();
        if (catalogue.isEmpty()) {
            outcome.cast = rules;
            outcome.by = "rules";
            return outcome;
        }
        boolean pitchOK = Casting.supportsPitch(meta.id, model);
        Set<String> exact = new HashSet<>();
        Map<String, String> loose = new HashMap<>();
        for (Types.VoiceProfile v : catalogue) {
            exact.add(v.id);
            loose.put(v.id.toLowerCase(java.util.Locale.ROOT), v.id);
        }
        List<Types.VoiceProfile> finalCatalogue = catalogue;
        try {
            String raw = Util.withRetry(() -> brain.ask(SYSTEM, prompt(analysis, finalCatalogue, pitchOK, meta.name)), 2, cancel);
            JsonElement parsed = Util.extractJSON(raw);
            JsonObject root = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
            JsonObject picks = root.has("cast") && root.get("cast").isJsonObject() ? root.getAsJsonObject("cast") : root;
            List<String> speakerIds = new ArrayList<>();
            speakerIds.add(Types.NARRATOR_ID);
            for (Types.CastCharacter c : analysis.characters) speakerIds.add(c.id);
            Map<String, Types.CastEntry> cast = new LinkedHashMap<>(rules);
            Map<String, String> reasons = new LinkedHashMap<>();
            int hits = 0;
            for (String id : speakerIds) {
                JsonElement pickEl = picks.get(id);
                if (pickEl == null || !pickEl.isJsonObject()) continue;
                JsonObject pick = pickEl.getAsJsonObject();
                JsonElement voiceEl = pick.get("voice");
                if (voiceEl == null || !voiceEl.isJsonPrimitive() || !voiceEl.getAsJsonPrimitive().isString()) continue;
                String voice = voiceEl.getAsString();
                String voiceId = exact.contains(voice) ? voice : loose.get(voice.trim().toLowerCase(java.util.Locale.ROOT));
                if (voiceId == null) continue;
                hits++;
                cast.put(id, new Types.CastEntry(voiceId,
                        pitchOK ? Util.clamp(number(pick.get("pitch")), -30, 30) : 0,
                        Util.clamp(number(pick.get("rate")), -35, 35)));
                String why = Util.str(pick.get("why"));
                if (!why.isEmpty()) reasons.put(id, why.length() > 220 ? why.substring(0, 220) : why);
            }
            if (hits < Math.ceil(speakerIds.size() / 2.0)) {
                outcome.cast = rules;
                outcome.by = "rules";
                outcome.note = FALLBACK_NOTE;
                return outcome;
            }
            keepDistinct(cast, analysis, pitchOK);
            outcome.cast = cast;
            outcome.reasons = reasons;
            outcome.by = "ai";
            return outcome;
        } catch (Cancel.Cancelled c) {
            throw c;
        } catch (Exception e) {
            if (Util.status(e) == 401) throw e;
            outcome.cast = rules;
            outcome.by = "rules";
            outcome.note = FALLBACK_NOTE;
            return outcome;
        }
    }
}
