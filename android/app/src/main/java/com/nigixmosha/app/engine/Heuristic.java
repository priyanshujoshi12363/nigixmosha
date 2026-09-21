package com.nigixmosha.app.engine;

import com.nigixmosha.app.engine.model.Types;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Heuristic {
    private static final int U = Util.UNICODE;
    private static final Pattern QUOTE = Pattern.compile(
            "“[^”]{1,2000}”|\"[^\"\\n]{1,2000}\"|«[^»]{1,2000}»|„[^“”]{1,2000}[“”]|「[^」]{1,2000}」|『[^』]{1,2000}』");

    private static final String VERBS = String.join("|",
            "said", "says", "asked", "asks", "replied", "answered", "shouted", "yelled", "screamed", "cried",
            "whispered", "murmured", "muttered", "called", "exclaimed", "added", "continued", "began", "snapped",
            "growled", "hissed", "sighed", "laughed", "remarked", "told", "insisted", "demanded", "pleaded",
            "gasped", "breathed", "roared", "barked", "admitted", "agreed", "explained", "interrupted", "repeated",
            "responded", "stammered", "stuttered", "warned", "wondered", "mumbled", "snarled", "sneered", "teased",
            "urged", "groaned", "chuckled", "sobbed", "announced", "declared", "protested", "retorted", "joked");
    private static final String TITLES = "Mr|Mrs|Ms|Miss|Dr|Professor|Prof|Uncle|Aunt|Auntie|Lord|Lady|Sir|Madam|Captain|King|Queen|Prince|Princess|Father|Mother|Brother|Sister|Grandma|Grandpa|Master|Mistress|Inspector|Detective|Officer";
    private static final String NAME = "(?:(?:" + TITLES + ")\\.?\\s+)?\\p{Lu}[\\p{L}'’-]+(?:\\s+\\p{Lu}[\\p{L}'’-]+)?";
    private static final String LY = "(?:\\p{L}+ly\\s+)?";
    private static final String LEAD = "^[\\s,;:—–-]*";

    private static final Pattern AFTER_VERB_NAME = Pattern.compile(LEAD + "(" + VERBS + ")\\s+(" + NAME + ")", U);
    private static final Pattern AFTER_NAME_VERB = Pattern.compile(LEAD + "(" + NAME + ")\\s+" + LY + "(" + VERBS + ")\\b", U);
    private static final Pattern AFTER_PRONOUN = Pattern.compile(LEAD + "(he|she|I)\\s+" + LY + "(" + VERBS + ")\\b",
            U | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern BEFORE_NAME_VERB = Pattern.compile("(" + NAME + ")\\s+" + LY + "(" + VERBS + ")[^.!?\"“”]{0,40}[,:—–-]?\\s*$", U);
    private static final Pattern BEFORE_VERB_NAME = Pattern.compile("(" + VERBS + ")\\s+(" + NAME + ")[^.!?\"“”]{0,25}[,:—–-]?\\s*$", U);
    private static final Pattern BEFORE_PRONOUN = Pattern.compile("\\b(he|she|I)\\s+" + LY + "(" + VERBS + ")[^.!?\"“”]{0,40}[,:—–-]?\\s*$",
            U | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String HI_VERBS = "कहा|बोला|बोली|पूछा|चिल्लाया|चिल्लाई|फुसफुसाया|फुसफुसाई";
    private static final String DEVANAGARI = "[\\u0900-\\u097F\\uA8E0-\\uA8FF]";
    private static final Pattern HINDI_AFTER = Pattern.compile("^[\\s,।—–-]*(" + DEVANAGARI + "+)\\s+(?:ने\\s+)?(" + HI_VERBS + ")", U);
    private static final Pattern HINDI_BEFORE = Pattern.compile("(" + DEVANAGARI + "+)\\s+(?:ने\\s+)?(" + HI_VERBS + ")[^।\"“”]{0,30}[,:—–-]?\\s*$", U);
    private static final Set<String> HINDI_FIRST = new HashSet<>(Arrays.asList("मैंने", "मैं", "हमने"));
    private static final Set<String> HINDI_PRONOUNS = new HashSet<>(Arrays.asList("उसने", "उन्होंने", "वह", "वो", "तुमने", "आपने", "फिर", "और", "तब"));
    private static final Pattern HI_MALE_VERB = Pattern.compile("^(बोला|चिल्लाया|फुसफुसाया)$");
    private static final Pattern HI_FEMALE_VERB = Pattern.compile("^(बोली|चिल्लाई|फुसफुसाई)$");
    private static final Pattern HI_MALE_KIN = Pattern.compile("(दादा|दादाजी|पिता|पिताजी|भैया|भाई|चाचा|मामा|नाना|बाबा|बाबूजी)$");
    private static final Pattern HI_FEMALE_KIN = Pattern.compile("(दादी|माँ|माता|दीदी|बहन|चाची|मामी|नानी|अम्मा)$");

    private static final Set<String> NOT_NAMES = new HashSet<>(Arrays.asList(
            "The", "He", "She", "It", "They", "We", "I", "You", "But", "And", "Then", "When", "What", "Why", "How",
            "Yes", "No", "Oh", "Well", "This", "That", "There", "Here", "His", "Her", "Their", "Our", "My", "Your",
            "A", "An", "Now", "So", "Just", "If", "As", "At", "In", "On", "For", "With", "After", "Before",
            "Suddenly", "Still", "Someone", "Somebody", "Everyone", "Nobody", "One", "Another", "Finally", "Later",
            "Soon", "Again", "Instead", "Meanwhile", "Quietly", "Softly", "Slowly"));

    private static final Pattern TITLE_RE = Pattern.compile("^(" + TITLES + ")\\.?\\s+", U);
    private static final Pattern MALE_TITLE = Pattern.compile("^(Mr|Uncle|Lord|Sir|Captain|King|Prince|Father|Brother|Grandpa|Master)\\b");
    private static final Pattern FEMALE_TITLE = Pattern.compile("^(Mrs|Ms|Miss|Aunt|Auntie|Lady|Madam|Queen|Princess|Mother|Sister|Grandma|Mistress)\\b");
    private static final Pattern PRONOUN_AFTER_NAME = Pattern.compile("\\b(he|him|his|himself|she|her|hers|herself)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIRST_PERSON = Pattern.compile("\\bI\\b|\\bmy\\b|\\bme\\b|मैं|मैंने|मेरा|मेरी");
    private static final Pattern MULTI_BANG = Pattern.compile("!{2,}");
    private static final Pattern ELLIPSIS = Pattern.compile("…|\\.\\.\\.");
    private static final Pattern LEADING_PUNCT = Pattern.compile("^[\\s,;:—–-]+", U);

    private static final Map<String, String> VERB_EMOTION = new HashMap<>();
    private static final Map<String, String[]> EMOTION_TRAITS = new HashMap<>();

    static {
        String[][] pairs = {
                {"shouted", "shouting"}, {"yelled", "shouting"}, {"screamed", "shouting"}, {"roared", "shouting"},
                {"barked", "angry"}, {"snapped", "angry"}, {"growled", "angry"}, {"snarled", "angry"}, {"hissed", "angry"},
                {"whispered", "whisper"}, {"murmured", "whisper"}, {"breathed", "whisper"}, {"mumbled", "whisper"},
                {"sneered", "sarcastic"}, {"teased", "sarcastic"}, {"retorted", "sarcastic"},
                {"joked", "happy"}, {"laughed", "happy"}, {"chuckled", "happy"},
                {"sobbed", "sad"}, {"sighed", "sad"}, {"groaned", "sad"},
                {"gasped", "surprised"}, {"exclaimed", "excited"},
                {"pleaded", "fearful"}, {"stammered", "fearful"}, {"stuttered", "fearful"},
                {"demanded", "serious"}, {"insisted", "serious"}, {"warned", "serious"}, {"declared", "serious"},
                {"चिल्लाया", "shouting"}, {"चिल्लाई", "shouting"}, {"फुसफुसाया", "whisper"}, {"फुसफुसाई", "whisper"},
        };
        for (String[] p : pairs) VERB_EMOTION.put(p[0], p[1]);
        EMOTION_TRAITS.put("shouting", new String[]{"fiery", "forceful"});
        EMOTION_TRAITS.put("angry", new String[]{"fiery", "blunt"});
        EMOTION_TRAITS.put("whisper", new String[]{"soft-spoken", "reserved"});
        EMOTION_TRAITS.put("happy", new String[]{"cheerful", "warm"});
        EMOTION_TRAITS.put("sad", new String[]{"melancholic", "gentle"});
        EMOTION_TRAITS.put("sarcastic", new String[]{"witty", "sardonic"});
        EMOTION_TRAITS.put("fearful", new String[]{"anxious", "hesitant"});
        EMOTION_TRAITS.put("serious", new String[]{"earnest", "firm"});
        EMOTION_TRAITS.put("excited", new String[]{"lively", "animated"});
        EMOTION_TRAITS.put("surprised", new String[]{"expressive"});
    }

    private Heuristic() {
    }

    private static final class Attribution {
        final String who;
        final String verb;

        Attribution(String who, String verb) {
            this.who = who;
            this.verb = verb;
        }
    }

    private static final class Span {
        final boolean dialogue;
        final String text;
        Attribution attr;

        Span(boolean dialogue, String text) {
            this.dialogue = dialogue;
            this.text = text;
        }
    }

    public static final class Speaker {
        public String key;
        public String name;
        public String gender;
        public int lines;
        public Map<String, Integer> emotions = new LinkedHashMap<>();
    }

    public static final class Line {
        public boolean dialogue;
        public String speakerKey;
        public String emotion;
        public String text;
        public boolean para;
    }

    public static final class Result {
        public List<Line> lines = new ArrayList<>();
        public List<Speaker> speakers = new ArrayList<>();
        public boolean firstPerson;
    }

    private static String cleanName(String raw) {
        List<String> tokens = new ArrayList<>(Arrays.asList(raw.trim().split("\\s+")));
        while (!tokens.isEmpty() && NOT_NAMES.contains(tokens.get(0))) tokens.remove(0);
        if (tokens.isEmpty()) return null;
        String name = String.join(" ", tokens).replaceAll("['’]s$", "");
        return NOT_NAMES.contains(name) ? null : name;
    }

    private static Attribution pronoun(String p, String verb) {
        String l = p.toLowerCase(java.util.Locale.ROOT);
        return new Attribution(l.equals("i") ? "__first" : l.equals("he") ? "__he" : "__she", verb.toLowerCase(java.util.Locale.ROOT));
    }

    private static Attribution hindi(Matcher m) {
        if (m == null) return null;
        String who = m.group(1);
        String verb = m.group(2);
        if (HINDI_FIRST.contains(who)) return new Attribution("__first", verb);
        if (HINDI_PRONOUNS.contains(who)) {
            if (HI_FEMALE_VERB.matcher(verb).find()) return new Attribution("__she", verb);
            if (HI_MALE_VERB.matcher(verb).find()) return new Attribution("__he", verb);
            return null;
        }
        return new Attribution(who, verb);
    }

    private static Matcher match(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m : null;
    }

    private static Attribution attribute(String after, String before) {
        Matcher m = match(AFTER_VERB_NAME, after);
        if (m != null) {
            String n = cleanName(m.group(2));
            if (n != null) return new Attribution(n, m.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        m = match(AFTER_NAME_VERB, after);
        if (m != null) {
            String n = cleanName(m.group(1));
            if (n != null) return new Attribution(n, m.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        m = match(AFTER_PRONOUN, after);
        if (m != null) return pronoun(m.group(1), m.group(2));
        Attribution ha = hindi(match(HINDI_AFTER, after));
        if (ha != null) return ha;
        m = match(BEFORE_NAME_VERB, before);
        if (m != null) {
            String n = cleanName(m.group(1));
            if (n != null) return new Attribution(n, m.group(2).toLowerCase(java.util.Locale.ROOT));
        }
        m = match(BEFORE_VERB_NAME, before);
        if (m != null) {
            String n = cleanName(m.group(2));
            if (n != null) return new Attribution(n, m.group(1).toLowerCase(java.util.Locale.ROOT));
        }
        m = match(BEFORE_PRONOUN, before);
        if (m != null) return pronoun(m.group(1), m.group(2));
        return hindi(match(HINDI_BEFORE, before));
    }

    public static String nameKey(String name) {
        boolean titled = TITLE_RE.matcher(name).find();
        String[] tokens = TITLE_RE.matcher(name).replaceFirst("").trim().split("\\s+");
        return (titled ? tokens[tokens.length - 1] : tokens[0]).toLowerCase(java.util.Locale.ROOT);
    }

    private static String guessGender(String name, String text, List<String> verbs) {
        if (MALE_TITLE.matcher(name).find() || HI_MALE_KIN.matcher(name).find()) return "male";
        if (FEMALE_TITLE.matcher(name).find() || HI_FEMALE_KIN.matcher(name).find()) return "female";
        int he = 0;
        int she = 0;
        for (String v : verbs) {
            if (HI_MALE_VERB.matcher(v).find()) he += 2;
            if (HI_FEMALE_VERB.matcher(v).find()) she += 2;
        }
        Matcher m = Pattern.compile(Pattern.quote(name) + "([^.!?।\"“”]{0,160})").matcher(text);
        int n = 0;
        while (m.find() && n < 60) {
            n++;
            Matcher p = PRONOUN_AFTER_NAME.matcher(m.group(1));
            if (!p.find()) continue;
            String first = p.group(1).toLowerCase(java.util.Locale.ROOT);
            if (first.startsWith("h") && !first.equals("her") && !first.equals("hers") && !first.equals("herself")) he++;
            else she++;
        }
        if (he > she * 1.3 && he > 0) return "male";
        if (she > he * 1.3 && she > 0) return "female";
        return "neutral";
    }

    private static String emotionFor(String text, String verb) {
        if (verb != null && VERB_EMOTION.containsKey(verb)) return VERB_EMOTION.get(verb);
        StringBuilder letters = new StringBuilder();
        boolean upper = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (Character.isLetter(cp)) {
                letters.appendCodePoint(cp);
                if (Character.isUpperCase(cp)) upper = true;
            }
            i += Character.charCount(cp);
        }
        String l = letters.toString();
        if (l.codePointCount(0, l.length()) > 4 && l.equals(l.toUpperCase(java.util.Locale.ROOT)) && upper) return "shouting";
        if (MULTI_BANG.matcher(text).find()) return "shouting";
        if (text.contains("!")) return "excited";
        if (ELLIPSIS.matcher(text).find() && text.length() < 80) return "tender";
        return "neutral";
    }

    public static Result lines(String text) {
        List<String> paragraphs = Texts.splitParagraphs(text);
        List<List<Span>> parsed = new ArrayList<>();
        for (String para : paragraphs) {
            List<Span> spans = new ArrayList<>();
            int last = 0;
            Matcher m = QUOTE.matcher(para);
            while (m.find()) {
                if (m.start() > last) spans.add(new Span(false, para.substring(last, m.start())));
                spans.add(new Span(true, m.group()));
                last = m.end();
            }
            if (last < para.length()) spans.add(new Span(false, para.substring(last)));
            for (int i = 0; i < spans.size(); i++) {
                Span s = spans.get(i);
                if (!s.dialogue) continue;
                String after = i + 1 < spans.size() && !spans.get(i + 1).dialogue ? head(spans.get(i + 1).text, 120) : "";
                String before = i > 0 && !spans.get(i - 1).dialogue ? tail(spans.get(i - 1).text, 120) : "";
                s.attr = attribute(after, before);
            }
            parsed.add(spans);
        }

        Map<String, String> display = new LinkedHashMap<>();
        Map<String, List<String>> verbsBy = new HashMap<>();
        for (List<Span> spans : parsed) {
            for (Span s : spans) {
                if (s.attr == null || s.attr.who.startsWith("__")) continue;
                String key = nameKey(s.attr.who);
                String prev = display.get(key);
                if (prev == null || s.attr.who.length() > prev.length()) display.put(key, s.attr.who);
                if (s.attr.verb != null) verbsBy.computeIfAbsent(key, k -> new ArrayList<>()).add(s.attr.verb);
            }
        }
        Map<String, String> genders = new HashMap<>();
        for (Map.Entry<String, String> e : display.entrySet()) {
            List<String> verbs = verbsBy.get(e.getKey());
            genders.put(e.getKey(), guessGender(e.getValue(), text, verbs == null ? new ArrayList<>() : verbs));
        }

        StringBuilder narration = new StringBuilder();
        for (List<Span> spans : parsed) for (Span s : spans) if (!s.dialogue) narration.append(s.text).append(' ');
        String narrationWords = narration.length() > 0 ? narration.substring(0, narration.length() - 1) : "";
        int totalWords = Math.max(1, narrationWords.split("\\s+").length);
        int firstCount = 0;
        Matcher fp = FIRST_PERSON.matcher(narrationWords);
        while (fp.find()) firstCount++;
        boolean firstPerson = (double) firstCount / totalWords > 0.015;

        Map<String, Speaker> stats = new LinkedHashMap<>();
        List<String> recent = new ArrayList<>();
        Result result = new Result();
        boolean prevWasDialogue = false;

        for (List<Span> spans : parsed) {
            List<Span> dialogues = new ArrayList<>();
            for (Span s : spans) if (s.dialogue) dialogues.add(s);
            String speaker = null;
            if (!dialogues.isEmpty()) {
                Attribution explicit = null;
                for (Span d : dialogues) if (d.attr != null) {
                    explicit = d.attr;
                    break;
                }
                if (explicit != null) speaker = resolve(explicit, recent, genders, display);
                else if (prevWasDialogue && recent.size() >= 2) speaker = recent.get(1);
                else speaker = "__unknown";
                recent.remove(speaker);
                if (!speaker.startsWith("__")) recent.add(0, speaker);
                if (recent.size() > 4) recent.remove(recent.size() - 1);
            }
            prevWasDialogue = !dialogues.isEmpty();

            boolean first = true;
            for (Span s : spans) {
                if (!s.dialogue) {
                    String t = LEADING_PUNCT.matcher(s.text).replaceFirst("").trim();
                    if (Texts.letterCount(t) == 0) continue;
                    Line line = new Line();
                    line.dialogue = false;
                    line.emotion = "neutral";
                    line.text = t;
                    line.para = first;
                    result.lines.add(line);
                } else {
                    String who = s.attr != null ? resolve(s.attr, recent, genders, display) : (speaker != null ? speaker : "__unknown");
                    String t = Texts.stripOuterQuotes(s.text);
                    if (Texts.letterCount(t) == 0) continue;
                    String emotion = emotionFor(t, s.attr != null ? s.attr.verb : null);
                    String key = who.equals("__first") && firstPerson ? null : who.equals("__first") ? "__unknown" : who;
                    Line line = new Line();
                    line.dialogue = true;
                    line.speakerKey = key;
                    line.emotion = emotion;
                    line.text = t;
                    line.para = first;
                    result.lines.add(line);
                    if (key != null) {
                        Speaker st = stats.get(key);
                        if (st == null) {
                            st = new Speaker();
                            st.key = key;
                            st.name = key.equals("__unknown") ? "Unnamed voice" : (display.containsKey(key) ? display.get(key) : key);
                            st.gender = genders.containsKey(key) ? genders.get(key) : "neutral";
                            stats.put(key, st);
                        }
                        st.lines++;
                        st.emotions.merge(emotion, 1, Integer::sum);
                    }
                }
                first = false;
            }
        }

        result.speakers.addAll(stats.values());
        result.speakers.sort((a, b) -> b.lines - a.lines);
        result.firstPerson = firstPerson;
        return result;
    }

    private static String resolve(Attribution a, List<String> recent, Map<String, String> genders, Map<String, String> display) {
        if (a.who.equals("__first")) return "__first";
        if (a.who.equals("__he") || a.who.equals("__she")) {
            String want = a.who.equals("__he") ? "male" : "female";
            for (String k : recent) if (want.equals(genders.get(k))) return k;
            for (String k : display.keySet()) if (want.equals(genders.get(k))) return k;
            return recent.isEmpty() ? "__unknown" : recent.get(0);
        }
        return nameKey(a.who);
    }

    private static String head(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    private static String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
    }

    private static List<String> traitsFrom(Map<String, Integer> emotions) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (Map.Entry<String, Integer> e : emotions.entrySet()) if (!e.getKey().equals("neutral")) entries.add(e);
        entries.sort((a, b) -> b.getValue() - a.getValue());
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i < Math.min(2, entries.size()); i++) {
            String[] t = EMOTION_TRAITS.get(entries.get(i).getKey());
            if (t != null) out.addAll(Arrays.asList(t));
        }
        if (out.isEmpty()) out.add("measured");
        return new ArrayList<>(out);
    }

    public static final class Analyzed {
        public Types.Analysis analysis;
        public List<Types.Segment> segments = new ArrayList<>();
    }

    public static Analyzed analyze(String text, String title, String languageHint) {
        Result r = lines(text);
        Map<String, String> idByKey = new HashMap<>();
        List<Types.CastCharacter> characters = new ArrayList<>();
        for (int i = 0; i < r.speakers.size(); i++) {
            Speaker sp = r.speakers.get(i);
            String id = "c" + (i + 1);
            idByKey.put(sp.key, id);
            Types.CastCharacter c = new Types.CastCharacter();
            c.id = id;
            c.name = sp.name;
            c.gender = sp.gender;
            c.age = "adult";
            c.role = i == 0 ? "lead" : sp.key.equals("__unknown") ? "background" : "supporting";
            c.personality = traitsFrom(sp.emotions);
            c.color = Catalog.get().color(i);
            c.lineCount = sp.lines;
            characters.add(c);
        }
        Analyzed out = new Analyzed();
        for (Line l : r.lines) {
            Types.Segment s = new Types.Segment();
            s.id = Util.uid("s");
            s.speaker = l.speakerKey != null && idByKey.containsKey(l.speakerKey) ? idByKey.get(l.speakerKey) : Types.NARRATOR_ID;
            s.emotion = l.emotion;
            s.text = l.text;
            s.para = l.para;
            out.segments.add(s);
        }
        Types.Analysis a = new Types.Analysis();
        a.title = title == null || title.isEmpty() ? "Untitled chapter" : title;
        a.language = languageHint == null || languageHint.isEmpty() ? Lang.detectLanguage(text) : languageHint;
        a.pov = r.firstPerson ? "first" : "third";
        a.summary = "";
        a.narrator = new Types.NarratorProfile();
        a.narrator.gender = "neutral";
        a.narrator.tone = "warm, measured storyteller";
        a.characters = characters;
        a.engine = "Offline Director";
        out.analysis = a;
        return out;
    }
}
