package com.nigixmosha.app.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public final class Director {
    public static final String NAME = "nigixmosha director";
    private static final int CHUNK_CHARS = 4000;
    private static final int EXCERPT_LIMIT = 90000;

    public interface Brain {
        String ask(String system, String prompt) throws Exception;
    }

    public interface Listener {
        void onProgress(String phase, String label, double value);

        void onCharacters(List<Types.CastCharacter> characters);
    }

    public static final class Result {
        public Types.Analysis analysis;
        public List<Types.Segment> segments = new ArrayList<>();
        public List<String> warnings = new ArrayList<>();
    }

    private static final Pattern BRACKETS = Pattern.compile("\\s*[(（\\[][^)）\\]]*[)）\\]]\\s*");
    private static final Pattern HONORIFIC = Pattern.compile("^(mr|mrs|ms|miss|dr|sir|lady|lord)\\.?\\s+", Util.UNICODE);
    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}\\s]", Util.UNICODE);
    private static final Pattern SPACES = Pattern.compile("\\s+", Util.UNICODE);
    private static final Pattern LOCALE = Pattern.compile("^[a-z]{2,3}(-[a-z0-9]{2,4})?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHAR_ID = Pattern.compile("^c\\d+$", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> EMOTION_ALIASES = new HashMap<>();

    static {
        String[][] pairs = {
                {"joyful", "happy"}, {"cheerful", "happy"}, {"scared", "fearful"}, {"afraid", "fearful"},
                {"nervous", "fearful"}, {"anxious", "fearful"}, {"shout", "shouting"}, {"yelling", "shouting"},
                {"whispering", "whisper"}, {"soft", "tender"}, {"gentle", "tender"}, {"loving", "tender"},
                {"furious", "angry"}, {"annoyed", "angry"}, {"sorrowful", "sad"}, {"shocked", "surprised"},
                {"amazed", "surprised"}, {"thrilled", "excited"}, {"grave", "serious"}, {"stern", "serious"},
                {"dry", "sarcastic"}, {"mocking", "sarcastic"},
        };
        for (String[] p : pairs) EMOTION_ALIASES.put(p[0], p[1]);
    }

    private static final String CAST_SYSTEM =
            "You are the casting director of a professional audiobook studio. You read a novel chapter in any language or script (including code-mixed text such as Hinglish) and identify every speaking character so each one can be given a distinct, fitting voice. You always answer with a single JSON object and nothing else.";
    private static final String SCRIPT_SYSTEM =
            "You are an audiobook script editor. You convert prose into a speaker-attributed script for a full-cast recording without changing, translating, or omitting a single word. You always answer with a single JSON object and nothing else.";

    private Director() {
    }

    private static String cleanCharName(String n) {
        return SPACES.matcher(BRACKETS.matcher(n).replaceAll(" ")).replaceAll(" ").trim();
    }

    public static String asGender(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        if (s.matches("^(f|woman|girl|lady).*")) return "female";
        if (s.matches("^(m|man|boy).*")) return "male";
        return "neutral";
    }

    public static String asAge(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        if (s.matches(".*(child|kid|little).*")) return "child";
        if (s.matches(".*(teen|adolesc).*")) return "teen";
        if (s.contains("young")) return "young";
        if (s.contains("middle")) return "middle";
        if (s.matches(".*(old|elder|senior|aged).*")) return "elderly";
        return "adult";
    }

    public static String asEmotion(String v) {
        String s = v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
        if (Types.EMOTIONS.contains(s)) return s;
        String alias = EMOTION_ALIASES.get(s);
        return alias != null ? alias : "neutral";
    }

    private static String keyOf(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        s = HONORIFIC.matcher(s).replaceFirst("");
        s = NON_WORD.matcher(s).replaceAll("");
        return SPACES.matcher(s).replaceAll(" ").trim();
    }

    static final class Registry {
        final List<Types.CastCharacter> characters = new ArrayList<>();
        private final Map<String, String> byKey = new HashMap<>();

        synchronized String add(String name, Types.CastCharacter partial) {
            String id = "c" + (characters.size() + 1);
            Types.CastCharacter c = partial != null ? partial : new Types.CastCharacter();
            c.id = id;
            c.name = name;
            if (c.aliases == null) c.aliases = new ArrayList<>();
            if (c.personality == null) c.personality = new ArrayList<>();
            c.color = Catalog.get().color(characters.size());
            c.lineCount = 0;
            characters.add(c);
            List<String> names = new ArrayList<>();
            names.add(name);
            names.addAll(c.aliases);
            for (String n : names) {
                String k = keyOf(n);
                if (!k.isEmpty() && !byKey.containsKey(k)) byKey.put(k, id);
                String first = k.split(" ")[0];
                if (first.length() > 2 && !byKey.containsKey(first)) byKey.put(first, id);
            }
            return id;
        }

        synchronized String find(String name) {
            String k = keyOf(name);
            String hit = byKey.get(k);
            return hit != null ? hit : byKey.get(k.split(" ")[0]);
        }

        synchronized String resolve(String raw) {
            String s = cleanCharName(raw);
            String lower = s.toLowerCase(Locale.ROOT);
            if (s.isEmpty() || lower.equals("narrator") || lower.equals("n")) return Types.NARRATOR_ID;
            for (Types.CastCharacter c : characters) if (c.id.equals(s)) return s;
            if (lower.startsWith("new:")) {
                String[] parts = s.substring(4).split("\\|", -1);
                String clean = parts.length > 0 ? parts[0].trim() : "";
                if (clean.isEmpty()) return Types.NARRATOR_ID;
                String found = find(clean);
                if (found != null) return found;
                Types.CastCharacter partial = new Types.CastCharacter();
                partial.gender = asGender(parts.length > 1 ? parts[1] : "");
                return add(clean, partial);
            }
            if (CHAR_ID.matcher(s).matches()) return Types.NARRATOR_ID;
            String found = find(s);
            return found != null ? found : add(s, null);
        }

        synchronized String findOrAdd(String name, String gender) {
            String found = find(name);
            if (found != null) return found;
            Types.CastCharacter partial = new Types.CastCharacter();
            if (gender != null) partial.gender = gender;
            return add(name, partial);
        }

        synchronized String roster() {
            StringBuilder b = new StringBuilder("- narrator: the narrator (all prose that is not spoken dialogue)");
            for (Types.CastCharacter c : characters) {
                String aka = c.aliases.isEmpty() ? "" : "; also called " + String.join(", ", c.aliases);
                b.append("\n- ").append(c.id).append(": ").append(c.name).append(" (").append(c.gender).append(", ").append(c.age).append(aka).append(")");
            }
            return b.toString();
        }

        synchronized List<Types.CastCharacter> snapshot() {
            return new ArrayList<>(characters);
        }
    }

    private static String castPrompt(String excerpt, String hint) {
        return "Analyse this chapter and return JSON with exactly this shape:\n"
                + "{\n"
                + "  \"title\": \"chapter title if present, otherwise a short evocative title in the chapter's language\",\n"
                + "  \"language\": \"BCP-47 code of the main narration language, e.g. en-US, en-GB, en-IN, hi-IN, ta-IN, bn-IN, es-ES, ja-JP\",\n"
                + "  \"mixedLanguages\": [\"BCP-47 codes of any other languages that appear in dialogue\"],\n"
                + "  \"pov\": \"first\" or \"third\",\n"
                + "  \"summary\": \"two sentence summary in English\",\n"
                + "  \"narrator\": { \"gender\": \"male|female|neutral\", \"tone\": \"ideal narration voice in a few words\", \"characterName\": \"name of the narrating character if first person, else null\" },\n"
                + "  \"characters\": [\n"
                + "    {\n"
                + "      \"name\": \"primary name exactly as written in the text\",\n"
                + "      \"aliases\": [\"other names, nicknames or titles used for them\"],\n"
                + "      \"gender\": \"male|female|neutral\",\n"
                + "      \"age\": \"child|teen|young|adult|middle|elderly\",\n"
                + "      \"role\": \"protagonist|antagonist|mentor|love interest|friend|family|minor\",\n"
                + "      \"personality\": [\"3 to 5 adjectives grounded in how they act here\"],\n"
                + "      \"speakingStyle\": \"how they talk: pace, vocabulary, formality, accent, verbal tics\",\n"
                + "      \"voiceDescription\": \"ideal voice timbre for casting, e.g. 'husky low female voice, unhurried'\"\n"
                + "    }\n"
                + "  ]\n"
                + "}\n"
                + "Rules:\n"
                + "- Include every character who speaks at least one line, even minor ones (a guard, a shopkeeper). Skip characters who are only mentioned.\n"
                + "- Infer gender and age from pronouns, titles, relationships, verb agreement and context.\n"
                + "- Order characters by how much they speak, most first."
                + (hint.isEmpty() ? "" : "\n- The author says the chapter is written in " + Lang.languageName(hint) + " (" + hint + ").")
                + "\n\nCHAPTER:\n\"\"\"\n" + excerpt + "\n\"\"\"";
    }

    private static String scriptPrompt(String roster, String context, String excerpt, boolean strict) {
        return "Speakers (use these ids):\n" + roster + "\n\n"
                + "Split the EXCERPT into consecutive segments and return:\n"
                + "{\"segments\":[{\"s\":\"speaker id\",\"e\":\"emotion\",\"t\":\"exact text\",\"p\":1}]}\n\n"
                + "Rules:\n"
                + "1. Copy the text verbatim and in order. Every word of the excerpt must appear exactly once across all \"t\" values. Never summarise, translate, correct, or skip anything."
                + (strict ? " Your previous answer dropped text — be exhaustive this time." : "") + "\n"
                + "2. Spoken dialogue goes to the character who speaks it. Everything else — description, action, and dialogue tags such as \"she said\" — goes to \"narrator\". Split whenever the speaker changes. Example: “Run!” Arjun shouted. “Now!” becomes [c1 \"Run!\"], [narrator \"Arjun shouted.\"], [c1 \"Now!\"].\n"
                + "3. Leave out the quotation marks around dialogue.\n"
                + "4. If a speaker is not in the list, use \"new:Name|male\" or \"new:Name|female\" (give an unnamed speaker a descriptive name like \"Guard\").\n"
                + "5. \"e\" is one of: " + String.join(", ", Types.EMOTIONS) + ". Judge it from context. Narration is usually neutral or calm.\n"
                + "6. \"p\": 1 when the segment begins a new paragraph, otherwise omit it.\n"
                + "7. Split narration longer than about 600 characters at sentence boundaries.\n"
                + (context.isEmpty() ? "" : "\nCONTEXT (the text just before the excerpt; do not output it):\n\"\"\"\n" + context + "\n\"\"\"\n")
                + "\nEXCERPT:\n\"\"\"\n" + excerpt + "\n\"\"\"";
    }

    private static boolean isFatal(Throwable e) {
        if (e instanceof Cancel.Cancelled) return true;
        int s = Util.status(e);
        return s == 400 || s == 401 || s == 402 || s == 403 || s == 404;
    }

    private static Types.Segment segment(String speaker, String emotion, String text, boolean para) {
        Types.Segment s = new Types.Segment();
        s.id = Util.uid("s");
        s.speaker = speaker;
        s.emotion = emotion;
        s.text = text;
        s.para = para;
        return s;
    }

    private static List<Types.Segment> fallbackSegments(String chunk, Registry reg) {
        Heuristic.Result h = Heuristic.lines(chunk);
        Map<String, Heuristic.Speaker> byKey = new HashMap<>();
        for (Heuristic.Speaker sp : h.speakers) byKey.put(sp.key, sp);
        List<Types.Segment> out = new ArrayList<>();
        for (Heuristic.Line l : h.lines) {
            String speaker = Types.NARRATOR_ID;
            if (l.speakerKey != null) {
                Heuristic.Speaker sp = byKey.get(l.speakerKey);
                String name = sp != null ? sp.name : l.speakerKey;
                String found = reg.find(name);
                if (found != null) speaker = found;
                else if (l.speakerKey.equals("__unknown")) speaker = reg.resolve("Unnamed voice");
                else speaker = reg.findOrAdd(name, sp != null ? sp.gender : null);
            }
            out.add(segment(speaker, l.emotion, l.text, l.para));
        }
        return out;
    }

    private static List<Types.Segment> alignWithQuotes(String chunk, List<Types.Segment> llm, Registry reg, String pov) {
        Heuristic.Result h = Heuristic.lines(chunk);
        boolean anyDialogue = false;
        for (Heuristic.Line l : h.lines) if (l.dialogue) {
            anyDialogue = true;
            break;
        }
        if (!anyDialogue) return null;
        Map<String, Heuristic.Speaker> byKey = new HashMap<>();
        for (Heuristic.Speaker sp : h.speakers) byKey.put(sp.key, sp);
        List<String> llmNorm = new ArrayList<>();
        for (Types.Segment s : llm) llmNorm.add(Texts.norm(s.text));
        int[] cursor = {0};
        List<Types.Segment> out = new ArrayList<>();
        for (Heuristic.Line l : h.lines) {
            String n = Texts.norm(l.text);
            String key = n.length() > 28 ? n.substring(0, 28) : n;
            int hit = key.length() >= 3 ? find(llm, llmNorm, key, !l.dialogue, cursor[0]) : -1;
            if (hit >= cursor[0]) cursor[0] = hit;
            Types.Segment src = hit >= 0 ? llm.get(hit) : null;
            String speaker = Types.NARRATOR_ID;
            String emotion = "neutral";
            if (l.dialogue) {
                boolean srcIsChar = src != null && !Types.NARRATOR_ID.equals(src.speaker);
                emotion = srcIsChar ? src.emotion : l.emotion;
                if (srcIsChar) speaker = src.speaker;
                else if (l.speakerKey != null && !l.speakerKey.equals("__unknown")) {
                    Heuristic.Speaker sp = byKey.get(l.speakerKey);
                    String name = sp != null ? sp.name : l.speakerKey;
                    speaker = reg.findOrAdd(name, sp != null ? sp.gender : null);
                } else if (l.speakerKey == null || "first".equals(pov)) speaker = Types.NARRATOR_ID;
                else speaker = reg.resolve("Unnamed voice");
            } else if (src != null && Types.NARRATOR_ID.equals(src.speaker)) {
                emotion = src.emotion;
            }
            out.add(segment(speaker, emotion, l.text, l.para));
        }
        return out;
    }

    private static int find(List<Types.Segment> llm, List<String> llmNorm, String key, boolean wantNarrator, int cursor) {
        int limit = Math.min(llm.size(), cursor + 12);
        for (int i = cursor; i < limit; i++) {
            if (llmNorm.get(i).contains(key) && Types.NARRATOR_ID.equals(llm.get(i).speaker) == wantNarrator) return i;
        }
        for (int i = cursor; i < llm.size(); i++) if (llmNorm.get(i).contains(key)) return i;
        for (int i = 0; i < Math.min(cursor, llm.size()); i++) if (llmNorm.get(i).contains(key)) return i;
        return -1;
    }

    private static Result directWithBrain(String text, String title, String languageHint, int concurrency,
                                          Brain brain, Listener listener, Cancel cancel) throws Exception {
        String hint = languageHint != null && !languageHint.equals("auto") ? languageHint : "";
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        listener.onProgress("reading", "The director is reading the chapter", 0.04);

        String excerpt = text.length() > EXCERPT_LIMIT ? text.substring(0, EXCERPT_LIMIT) : text;
        if (text.length() > EXCERPT_LIMIT) {
            warnings.add("Long chapter: characters were discovered from the opening; later speakers were added while scripting.");
        }

        JsonObject castRaw = Util.withRetry(() -> {
            JsonElement el = Util.extractJSON(brain.ask(CAST_SYSTEM, castPrompt(excerpt, hint)));
            return el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        }, 2, cancel);

        Registry reg = new Registry();
        JsonElement chars = castRaw.get("characters");
        if (chars != null && chars.isJsonArray()) {
            for (JsonElement el : chars.getAsJsonArray()) {
                if (!el.isJsonObject()) continue;
                JsonObject rc = el.getAsJsonObject();
                String name = cleanCharName(Util.str(rc.get("name")));
                if (name.isEmpty() || reg.find(name) != null) continue;
                Types.CastCharacter partial = new Types.CastCharacter();
                List<String> aliases = new ArrayList<>();
                for (String a : Util.strList(rc.get("aliases"))) if (!a.equalsIgnoreCase(name)) aliases.add(a);
                partial.aliases = aliases;
                partial.gender = asGender(Util.str(rc.get("gender")));
                partial.age = asAge(Util.str(rc.get("age")));
                String role = Util.str(rc.get("role"));
                partial.role = role.isEmpty() ? "minor" : role;
                List<String> personality = Util.strList(rc.get("personality"));
                partial.personality = new ArrayList<>(personality.subList(0, Math.min(5, personality.size())));
                partial.speakingStyle = Util.str(rc.get("speakingStyle"));
                partial.voiceDescription = Util.str(rc.get("voiceDescription"));
                reg.add(name, partial);
            }
        }
        listener.onCharacters(reg.snapshot());

        String llmLang = Util.str(castRaw.get("language"));
        String language = !hint.isEmpty() ? hint
                : LOCALE.matcher(llmLang).matches() ? Lang.normalizeLocale(llmLang) : Lang.detectLanguage(text);
        String pov = Util.str(castRaw.get("pov")).toLowerCase(Locale.ROOT).startsWith("first") ? "first" : "third";
        JsonObject narratorRaw = castRaw.has("narrator") && castRaw.get("narrator").isJsonObject() ? castRaw.getAsJsonObject("narrator") : new JsonObject();
        String narratorName = Util.str(narratorRaw.get("characterName"));
        Types.NarratorProfile narrator = new Types.NarratorProfile();
        narrator.gender = asGender(Util.str(narratorRaw.get("gender")));
        String tone = Util.str(narratorRaw.get("tone"));
        narrator.tone = tone.isEmpty() ? "warm, measured storyteller" : tone;
        narrator.characterId = "first".equals(pov) && !narratorName.isEmpty() && !narratorName.equalsIgnoreCase("null")
                ? reg.find(narratorName) : null;

        List<String> chunks = Texts.chunkParagraphs(Texts.splitParagraphs(text), CHUNK_CHARS);
        int total = chunks.size();
        String plural = total > 1 ? "s" : "";
        listener.onProgress("scripting", "Scripting " + total + " part" + plural, 0.15);

        AtomicInteger done = new AtomicInteger();
        List<List<Types.Segment>> parts = new ArrayList<>(Collections.nCopies(total, null));
        AtomicInteger next = new AtomicInteger();
        String finalPov = pov;
        int lanes = Math.max(1, Math.min(concurrency, total));
        ExecutorService pool = Executors.newFixedThreadPool(lanes);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int lane = 0; lane < lanes; lane++) {
                futures.add(pool.submit(() -> {
                    while (true) {
                        cancel.check();
                        int i = next.getAndIncrement();
                        if (i >= total) return null;
                        String chunk = chunks.get(i);
                        String prev = i > 0 ? chunks.get(i - 1) : "";
                        String context = prev.length() > 500 ? prev.substring(prev.length() - 500) : prev;
                        List<Types.Segment> segs = null;
                        for (int attempt = 0; attempt < 2 && segs == null; attempt++) {
                            boolean strict = attempt > 0;
                            try {
                                String raw = Util.withRetry(() -> brain.ask(SCRIPT_SYSTEM, scriptPrompt(reg.roster(), context, chunk, strict)), 2, cancel);
                                JsonElement parsed = Util.extractJSON(raw);
                                JsonArray list = parsed.isJsonArray() ? parsed.getAsJsonArray()
                                        : parsed.isJsonObject() && parsed.getAsJsonObject().has("segments") && parsed.getAsJsonObject().get("segments").isJsonArray()
                                        ? parsed.getAsJsonObject().getAsJsonArray("segments") : new JsonArray();
                                List<Types.Segment> candidate = new ArrayList<>();
                                for (JsonElement r : list) {
                                    if (!r.isJsonObject()) continue;
                                    JsonObject o = r.getAsJsonObject();
                                    String rawSpeaker = Util.str(o.has("s") ? o.get("s") : o.get("speaker"));
                                    String speaker = reg.resolve(rawSpeaker.isEmpty() ? Types.NARRATOR_ID : rawSpeaker);
                                    JsonElement tEl = o.has("t") ? o.get("t") : o.get("text");
                                    String t = tEl != null && tEl.isJsonPrimitive() && tEl.getAsJsonPrimitive().isString() ? tEl.getAsString().trim() : "";
                                    if (!Types.NARRATOR_ID.equals(speaker)) t = Texts.stripOuterQuotes(t);
                                    if (Texts.letterCount(t) == 0) continue;
                                    JsonElement eEl = o.has("e") ? o.get("e") : o.get("emotion");
                                    JsonElement pEl = o.get("p");
                                    boolean para = pEl != null && !pEl.isJsonNull() && truthy(pEl);
                                    candidate.add(segment(speaker, asEmotion(Util.str(eEl)), t, para));
                                }
                                if (!candidate.isEmpty()) {
                                    List<Types.Segment> aligned = alignWithQuotes(chunk, candidate, reg, finalPov);
                                    StringBuilder all = new StringBuilder();
                                    for (Types.Segment c : candidate) all.append(c.text).append(' ');
                                    double ratio = Texts.letterCount(all.toString()) / (double) Math.max(1, Texts.letterCount(chunk));
                                    if (aligned != null) segs = aligned;
                                    else if (ratio > 0.82 && ratio < 1.3) segs = candidate;
                                }
                            } catch (Exception e) {
                                if (isFatal(e)) throw e;
                            }
                        }
                        if (segs == null) {
                            warnings.add("Part " + (i + 1) + " got a quick read.");
                            segs = fallbackSegments(chunk, reg);
                        }
                        if (!segs.isEmpty()) segs.get(0).para = true;
                        parts.set(i, segs);
                        int d = done.incrementAndGet();
                        listener.onProgress("scripting", "Scripted " + d + " of " + total + " part" + plural, 0.15 + 0.8 * d / total);
                    }
                }));
            }
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (java.util.concurrent.ExecutionException e) {
                    cancel.cancel();
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception) throw (Exception) cause;
                    throw e;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        listener.onProgress("finishing", "Assembling the cast", 0.98);
        List<Types.Segment> segments = new ArrayList<>();
        for (List<Types.Segment> p : parts) if (p != null) segments.addAll(p);
        Map<String, Integer> counts = new HashMap<>();
        for (Types.Segment s : segments) counts.merge(s.speaker, 1, Integer::sum);
        List<Types.CastCharacter> characters = new ArrayList<>();
        for (Types.CastCharacter c : reg.snapshot()) {
            c.lineCount = counts.getOrDefault(c.id, 0);
            if (c.lineCount > 0 || c.id.equals(narrator.characterId)) characters.add(c);
        }
        characters.sort((a, b) -> b.lineCount - a.lineCount);
        for (int i = 0; i < characters.size(); i++) characters.get(i).color = Catalog.get().color(i);

        Types.Analysis analysis = new Types.Analysis();
        String rawTitle = Util.str(castRaw.get("title"));
        analysis.title = !rawTitle.isEmpty() ? rawTitle : title != null && !title.isEmpty() ? title : "Untitled chapter";
        analysis.language = language;
        List<String> mixed = new ArrayList<>();
        for (String l : Util.strList(castRaw.get("mixedLanguages"))) {
            String n = Lang.normalizeLocale(l);
            if (!n.equals(language)) mixed.add(n);
        }
        analysis.mixedLanguages = mixed;
        analysis.pov = pov;
        analysis.summary = Util.str(castRaw.get("summary"));
        analysis.narrator = narrator;
        analysis.characters = characters;
        analysis.engine = NAME;

        Result result = new Result();
        result.analysis = analysis;
        result.segments = segments;
        result.warnings = new ArrayList<>(warnings);
        return result;
    }

    private static boolean truthy(JsonElement el) {
        if (!el.isJsonPrimitive()) return true;
        if (el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
        if (el.getAsJsonPrimitive().isNumber()) return el.getAsDouble() != 0;
        return !el.getAsString().isEmpty();
    }

    public static Result direct(String text, String title, String languageHint, Brain brain, Listener listener, Cancel cancel) throws Exception {
        String hint = languageHint != null && !languageHint.equals("auto") ? languageHint : "";
        try {
            return directWithBrain(text, title, languageHint, 2, brain, listener, cancel);
        } catch (Exception e) {
            int status = Util.status(e);
            if (e instanceof Cancel.Cancelled || cancel.isCancelled() || status == 401 || status == 413) throw e;
            listener.onProgress("finishing", "Finishing a quick read", 0.95);
            Heuristic.Analyzed quick = Heuristic.analyze(text, title, hint);
            quick.analysis.engine = NAME;
            Result r = new Result();
            r.analysis = quick.analysis;
            r.segments = quick.segments;
            r.warnings = new ArrayList<>(Arrays.asList(
                    "The director was busy, so this chapter got a quick read. Re-direct it later for richer characters."));
            return r;
        }
    }
}
