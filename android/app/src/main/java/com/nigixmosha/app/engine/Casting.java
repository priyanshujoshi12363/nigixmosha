package com.nigixmosha.app.engine;

import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Casting {
    public static final class Candidate {
        public final Types.VoiceProfile voice;
        public final boolean nativeVoice;

        Candidate(Types.VoiceProfile voice, boolean nativeVoice) {
            this.voice = voice;
            this.nativeVoice = nativeVoice;
        }
    }

    private static final Object[][] TRAIT_TAGS = {
            {Pattern.compile("brave|bold|confident|leader|command|authorit|proud|strong|determined|stern|firm|noble"),
                    new String[]{"confident", "authority", "firm", "deep", "reliable"}},
            {Pattern.compile("kind|gentle|caring|loving|warm|nurtur|soft|sweet|compassion|tender"),
                    new String[]{"warm", "gentle", "soft", "friendly", "comfort", "considerate"}},
            {Pattern.compile("cheer|playful|witty|funny|energetic|excit|impulsive|lively|mischiev|bubbly|enthusias|fiery"),
                    new String[]{"bright", "energetic", "playful", "lively", "cheerful", "upbeat", "passion", "excitable"}},
            {Pattern.compile("wise|calm|stoic|patient|thought|serene|reserved|quiet|measured|old"),
                    new String[]{"calm", "mature", "wise", "even", "rational", "knowledgeable"}},
            {Pattern.compile("villain|cruel|menac|cold|cunning|sinister|ruthless|dark|arrogant|threat|brood"),
                    new String[]{"deep", "gravelly", "serious", "intense", "firm"}},
            {Pattern.compile("shy|timid|nervous|anxious|meek|insecure|hesitant"),
                    new String[]{"soft", "gentle", "breathy", "youthful"}},
            {Pattern.compile("sarcas|dry|cynic|sardonic|blunt"), new String[]{"crisp", "casual", "rational"}},
            {Pattern.compile("formal|professional|precise|educated|intellect|scholar"),
                    new String[]{"crisp", "clear", "professional", "informative", "knowledgeable"}},
            {Pattern.compile("gruff|rough|tough|grizzled|hoarse|husky"), new String[]{"gravelly", "raspy", "deep"}},
            {Pattern.compile("young|youth|innocent|naive|curious"), new String[]{"youthful", "bright", "cute"}},
    };
    private static final Set<String> NARRATOR_TAGS = new HashSet<>(Arrays.asList("narrator", "novel", "storyteller", "audiobook", "warm", "news"));
    private static final Map<String, double[]> AGE_PROSODY = new HashMap<>();
    private static final double[][] VARIANTS = {{0, 0}, {9, 4}, {-9, -4}, {15, -3}, {-15, 3}, {5, -8}, {-5, 8}};
    private static final Pattern FAST = Pattern.compile("energetic|impulsive|excit|lively|fast|quick|hyper|bubbly");
    private static final Pattern SLOW = Pattern.compile("calm|wise|slow|measured|patient|deliberate|stoic");
    private static final Pattern NERVOUS = Pattern.compile("nervous|anxious|timid");
    private static final Pattern MENACE = Pattern.compile("menac|cold|sinister|gruff|deep");

    static {
        AGE_PROSODY.put("child", new double[]{16, 6});
        AGE_PROSODY.put("teen", new double[]{8, 4});
        AGE_PROSODY.put("young", new double[]{3, 2});
        AGE_PROSODY.put("adult", new double[]{0, 0});
        AGE_PROSODY.put("middle", new double[]{-3, -2});
        AGE_PROSODY.put("elderly", new double[]{-7, -10});
    }

    private Casting() {
    }

    public static boolean supportsPitch(String provider, String model) {
        return "edge".equals(provider) || ("sarvam-tts".equals(provider) && "bulbul:v2".equals(model));
    }

    private static String describe(Types.CastCharacter c) {
        return (String.join(" ", c.personality) + " " + nz(c.speakingStyle) + " " + nz(c.voiceDescription) + " " + nz(c.role))
                .toLowerCase(java.util.Locale.ROOT);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static Set<String> wantedTags(Types.CastCharacter c, String narratorTone) {
        String text = c != null ? describe(c) : nz(narratorTone).toLowerCase(java.util.Locale.ROOT);
        Set<String> tags = new HashSet<>();
        for (Object[] t : TRAIT_TAGS) if (((Pattern) t[0]).matcher(text).find()) tags.addAll(Arrays.asList((String[]) t[1]));
        for (String word : text.split("[^a-z]+")) if (word.length() > 3) tags.add(word);
        return tags;
    }

    private static double[] baseProsody(Types.CastCharacter c) {
        if (c == null) return new double[]{0, -3};
        double[] base = AGE_PROSODY.containsKey(c.age) ? AGE_PROSODY.get(c.age) : AGE_PROSODY.get("adult");
        double[] p = {base[0], base[1]};
        String text = describe(c);
        if (FAST.matcher(text).find()) p[1] += 6;
        if (SLOW.matcher(text).find()) p[1] -= 5;
        if (NERVOUS.matcher(text).find()) {
            p[1] += 4;
            p[0] += 2;
        }
        if (MENACE.matcher(text).find()) {
            p[0] -= 4;
            p[1] -= 3;
        }
        return p;
    }

    public static List<Candidate> candidateVoices(List<Types.VoiceProfile> voices, Types.ProviderMeta meta, String language) {
        List<Candidate> out = new ArrayList<>();
        if (meta.anyLanguage) {
            for (Types.VoiceProfile v : voices) out.add(new Candidate(v, true));
            return out;
        }
        String p = Lang.prefix(language);
        List<Candidate> nativeList = new ArrayList<>();
        List<Candidate> multi = new ArrayList<>();
        for (Types.VoiceProfile v : voices) {
            boolean speaks = false;
            for (String l : v.langs) if (l.equals("*") || Lang.prefix(l).equals(p)) {
                speaks = true;
                break;
            }
            if (speaks) nativeList.add(new Candidate(v, true));
            else if (v.isMultilingual()) multi.add(new Candidate(v, false));
        }
        if (!nativeList.isEmpty()) {
            out.addAll(nativeList);
            out.addAll(multi);
            return out;
        }
        if (!multi.isEmpty()) return multi;
        for (Types.VoiceProfile v : voices) out.add(new Candidate(v, false));
        return out;
    }

    private static double score(Types.VoiceProfile voice, boolean nativeVoice, String gender, String age, Set<String> tags,
                                boolean isNarrator, String language, int uses) {
        double s = 0;
        if (!"neutral".equals(gender)) s += voice.gender.equals(gender) ? 6 : "neutral".equals(voice.gender) ? 1 : -12;
        if (voice.age != null && voice.age.equals(age)) s += 3;
        if ("child".equals(age) || "teen".equals(age)) {
            if ("child".equals(voice.age)) s += "child".equals(age) ? 6 : 1;
            if ("elderly".equals(voice.age) || "middle".equals(voice.age)) s -= 4;
        } else if ("child".equals(voice.age)) s -= 9;
        if ("elderly".equals(age) && "middle".equals(voice.age)) s += 1.5;
        for (String t : voice.tags) if (tags.contains(t)) s += 1.5;
        if (isNarrator) {
            for (String t : voice.tags) if (NARRATOR_TAGS.contains(t)) {
                s += 3;
                break;
            }
        }
        if (nativeVoice) {
            if (voice.langs.contains(language)) s += 1.5;
        } else s -= 3;
        return s - uses * 7;
    }

    public static List<Types.CastCharacter> byLines(Types.Analysis analysis) {
        List<Types.CastCharacter> sorted = new ArrayList<>(analysis.characters);
        sorted.sort((a, b) -> b.lineCount - a.lineCount);
        return sorted;
    }

    public static Map<String, Types.CastEntry> autoCast(Types.Analysis analysis, List<Types.VoiceProfile> voices,
                                                        Types.ProviderMeta meta, String model) {
        Map<String, Types.CastEntry> cast = new LinkedHashMap<>();
        List<Candidate> pool = candidateVoices(voices, meta, analysis.language);
        if (pool.isEmpty()) return cast;
        boolean pitchOK = supportsPitch(meta.id, model);
        Map<String, Integer> used = new HashMap<>();
        List<Types.CastCharacter> order = new ArrayList<>();
        order.add(null);
        order.addAll(byLines(analysis));
        for (Types.CastCharacter c : order) {
            String gender = c != null ? c.gender : analysis.narrator.gender;
            String age = c != null ? c.age : "adult";
            Set<String> tags = wantedTags(c, analysis.narrator.tone);
            Types.VoiceProfile best = pool.get(0).voice;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Candidate cand : pool) {
                double sc = score(cand.voice, cand.nativeVoice, gender, age, tags, c == null, analysis.language,
                        used.getOrDefault(cand.voice.id, 0));
                if (sc > bestScore) {
                    bestScore = sc;
                    best = cand.voice;
                }
            }
            int reuse = used.getOrDefault(best.id, 0);
            used.put(best.id, reuse + 1);
            double[] base = baseProsody(c);
            double[] variant = VARIANTS[reuse % VARIANTS.length];
            cast.put(c != null ? c.id : Types.NARRATOR_ID, new Types.CastEntry(best.id,
                    pitchOK ? Util.clamp(base[0] + variant[0], -30, 30) : 0,
                    Util.clamp(base[1] + (pitchOK ? variant[1] : variant[1] * 1.5), -35, 35)));
        }
        return cast;
    }

    public static Types.CastEntry castFor(String speaker, Map<String, Types.CastEntry> cast, Types.Analysis analysis, boolean shareNarrator) {
        if (Types.NARRATOR_ID.equals(speaker) && shareNarrator && analysis.narrator.characterId != null) {
            Types.CastEntry linked = cast.get(analysis.narrator.characterId);
            return linked != null ? linked : cast.get(Types.NARRATOR_ID);
        }
        Types.CastEntry own = cast.get(speaker);
        return own != null ? own : cast.get(Types.NARRATOR_ID);
    }
}
