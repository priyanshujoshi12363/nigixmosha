package com.nigixmosha.app.engine;

import com.nigixmosha.app.engine.model.Types;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lang {
    private static final Character.UnicodeScript[] SCRIPTS = {
            Character.UnicodeScript.DEVANAGARI, Character.UnicodeScript.BENGALI, Character.UnicodeScript.GURMUKHI,
            Character.UnicodeScript.GUJARATI, Character.UnicodeScript.ORIYA, Character.UnicodeScript.TAMIL,
            Character.UnicodeScript.TELUGU, Character.UnicodeScript.KANNADA, Character.UnicodeScript.MALAYALAM,
            Character.UnicodeScript.ARABIC, Character.UnicodeScript.CYRILLIC, null,
            Character.UnicodeScript.HANGUL, Character.UnicodeScript.HAN
    };
    private static final String[] SCRIPT_CODES = {
            "hi-IN", "bn-IN", "pa-IN", "gu-IN", "or-IN", "ta-IN", "te-IN", "kn-IN", "ml-IN",
            "ar-SA", "ru-RU", "ja-JP", "ko-KR", "zh-CN"
    };
    private static final String[][] LATIN_HINTS = {
            {"en-US", "the", "and", "was", "that", "with", "his", "her", "said"},
            {"es-ES", "el", "la", "que", "los", "una", "dijo", "pero", "por"},
            {"fr-FR", "le", "les", "est", "une", "dans", "qui", "pas", "dit"},
            {"de-DE", "der", "die", "und", "nicht", "ist", "sie", "ich", "sagte"},
            {"it-IT", "il", "che", "non", "una", "della", "per", "sono", "disse"},
            {"pt-BR", "não", "uma", "que", "ele", "ela", "disse", "com", "para"},
            {"id-ID", "yang", "dan", "itu", "tidak", "dengan", "aku", "kata", "ini"},
            {"tr-TR", "bir", "ve", "bu", "için", "ama", "dedi", "çok", "gibi"},
    };
    private static final Pattern WORD = Pattern.compile("[\\p{L}']+");
    private static final Pattern URDU = Pattern.compile("[ےںٹڈڑ]");
    private static final Pattern MARATHI = Pattern.compile("(आहे|आणि|होते|झाले)");

    private Lang() {
    }

    public static String languageName(String code) {
        if (code == null) return "";
        for (Types.Language l : Catalog.get().languages) if (l.code.equalsIgnoreCase(code)) return l.name;
        String prefix = code.split("-")[0].toLowerCase(Locale.ROOT);
        for (Types.Language l : Catalog.get().languages) if (l.code.startsWith(prefix + "-")) return l.name;
        return code;
    }

    public static String normalizeLocale(String code) {
        String[] parts = code.trim().replace("_", "-").split("-");
        if (parts.length == 0 || parts[0].isEmpty()) return "en-US";
        String l = parts[0].toLowerCase(Locale.ROOT);
        if (parts.length > 1 && !parts[1].isEmpty()) return l + "-" + parts[1].toUpperCase(Locale.ROOT);
        for (Types.Language x : Catalog.get().languages) if (x.code.startsWith(l + "-")) return x.code;
        return l;
    }

    public static String prefix(String code) {
        return code == null ? "" : code.split("-")[0].toLowerCase(Locale.ROOT);
    }

    public static String detectLanguage(String text) {
        String sample = text.length() > 20000 ? text.substring(0, 20000) : text;
        int[] counts = new int[SCRIPTS.length];
        int latin = 0;
        int kana = 0;
        for (int i = 0; i < sample.length(); ) {
            int cp = sample.codePointAt(i);
            i += Character.charCount(cp);
            if ((cp >= 'A' && cp <= 'Z') || (cp >= 'a' && cp <= 'z')) {
                latin++;
                continue;
            }
            Character.UnicodeScript script;
            try {
                script = Character.UnicodeScript.of(cp);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) {
                counts[11]++;
                kana++;
                continue;
            }
            for (int s = 0; s < SCRIPTS.length; s++) {
                if (SCRIPTS[s] != null && SCRIPTS[s] == script) {
                    counts[s]++;
                    break;
                }
            }
        }
        String best = "";
        int bestCount = 0;
        for (int s = 0; s < SCRIPTS.length; s++) {
            if (counts[s] > bestCount) {
                best = SCRIPT_CODES[s];
                bestCount = counts[s];
            }
        }
        if (!best.isEmpty() && bestCount > latin * 0.3) {
            if (best.equals("zh-CN") && kana > 20) return "ja-JP";
            if (best.equals("ar-SA") && URDU.matcher(sample).find()) return "ur-IN";
            if (best.equals("hi-IN") && MARATHI.matcher(sample).find()) return "mr-IN";
            return best;
        }
        Map<String, Integer> freq = new HashMap<>();
        Matcher m = WORD.matcher(sample.toLowerCase(Locale.ROOT));
        while (m.find()) freq.merge(m.group(), 1, Integer::sum);
        String lang = "en-US";
        int score = 0;
        for (String[] hints : LATIN_HINTS) {
            int s = 0;
            for (int i = 1; i < hints.length; i++) s += freq.getOrDefault(hints[i], 0);
            if (s > score) {
                score = s;
                lang = hints[0];
            }
        }
        return lang;
    }
}
