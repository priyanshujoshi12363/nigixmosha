package com.nigixmosha.app.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Texts {
    private static final Pattern TERMINAL = Pattern.compile("[.!?\"”’»」』…:;।॥。！？)\\]—–-]$");
    private static final Pattern SENTENCE = Pattern.compile("[^.!?।॥。！？…]+(?:[.!?।॥。！？…]+[\"”’»」』)\\]]*|$)\\s*");
    private static final Pattern LEAD_QUOTES = Pattern.compile("^[\"“”«»„「『'‘]+");
    private static final Pattern TRAIL_QUOTES = Pattern.compile("[\"“”«»」』'’]+$");

    private Texts() {
    }

    public static List<String> splitParagraphs(String text) {
        String clean = text.replaceAll("\\r\\n?", "\n").trim();
        List<String> out = new ArrayList<>();
        if (clean.isEmpty()) return out;
        String[] lines = clean.split("\n", -1);
        List<String> filled = new ArrayList<>();
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty()) filled.add(t);
        }
        if (filled.isEmpty()) return out;
        int blank = lines.length - filled.size();
        int[] lens = new int[filled.size()];
        for (int i = 0; i < lens.length; i++) lens[i] = filled.get(i).length();
        Arrays.sort(lens);
        int median = lens[lens.length / 2];
        int unterminated = 0;
        for (String l : filled) if (!TERMINAL.matcher(l).find()) unterminated++;
        boolean hardWrapped = blank > 0 && median > 40 && median < 100 && (double) unterminated / filled.size() > 0.45;
        if (hardWrapped) {
            for (String p : clean.split("\\n\\s*\\n")) {
                String joined = p.replaceAll("\\s*\\n\\s*", " ").trim();
                if (!joined.isEmpty()) out.add(joined);
            }
            return out;
        }
        return filled;
    }

    public static List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = SENTENCE.matcher(text);
        while (m.find()) {
            if (m.end() == m.start()) {
                if (m.end() >= text.length()) break;
                continue;
            }
            String s = m.group();
            if (!s.trim().isEmpty()) out.add(s);
        }
        if (out.isEmpty() && !text.trim().isEmpty()) out.add(text);
        return out;
    }

    private static List<String> hardSplit(String text, int max) {
        List<String> out = new ArrayList<>();
        String rest = text;
        while (rest.length() > max) {
            int cut = rest.lastIndexOf(", ", max);
            if (cut < max * 0.5) cut = rest.lastIndexOf(' ', max);
            if (cut < max * 0.3) cut = max;
            int end = Math.min(rest.length(), cut + 1);
            out.add(rest.substring(0, end).trim());
            rest = rest.substring(end);
        }
        if (!rest.trim().isEmpty()) out.add(rest.trim());
        return out;
    }

    public static List<String> splitForTTS(String text, int max) {
        String t = text.trim();
        List<String> pieces = new ArrayList<>();
        if (t.length() <= max) {
            pieces.add(t);
            return pieces;
        }
        StringBuilder buf = new StringBuilder();
        for (String sentence : splitSentences(t)) {
            if (sentence.length() > max) {
                if (!buf.toString().trim().isEmpty()) pieces.add(buf.toString().trim());
                buf.setLength(0);
                pieces.addAll(hardSplit(sentence, max));
                continue;
            }
            if (buf.length() + sentence.length() > max) {
                pieces.add(buf.toString().trim());
                buf.setLength(0);
            }
            buf.append(sentence);
        }
        if (!buf.toString().trim().isEmpty()) pieces.add(buf.toString().trim());
        pieces.removeIf(String::isEmpty);
        return pieces;
    }

    public static List<String> chunkParagraphs(List<String> paragraphs, int maxChars) {
        List<String> chunks = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int size = 0;
        for (String para : paragraphs) {
            if (para.length() > maxChars) {
                if (!current.isEmpty()) chunks.add(String.join("\n\n", current));
                current.clear();
                size = 0;
                StringBuilder buf = new StringBuilder();
                for (String s : splitSentences(para)) {
                    if (buf.length() + s.length() > maxChars && buf.length() > 0) {
                        chunks.add(buf.toString().trim());
                        buf.setLength(0);
                    }
                    buf.append(s);
                }
                if (!buf.toString().trim().isEmpty()) chunks.add(buf.toString().trim());
                continue;
            }
            if (size + para.length() > maxChars) {
                if (!current.isEmpty()) chunks.add(String.join("\n\n", current));
                current.clear();
                size = 0;
            }
            current.add(para);
            size += para.length() + 2;
        }
        if (!current.isEmpty()) chunks.add(String.join("\n\n", current));
        return chunks;
    }

    public static int letterCount(String s) {
        if (s == null) return 0;
        int n = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (Character.isLetter(cp) || Character.isDigit(cp) || Character.getType(cp) == Character.LETTER_NUMBER
                    || Character.getType(cp) == Character.OTHER_NUMBER) n++;
            i += Character.charCount(cp);
        }
        return n;
    }

    public static String stripOuterQuotes(String s) {
        String t = s.trim();
        t = LEAD_QUOTES.matcher(t).replaceFirst("");
        t = TRAIL_QUOTES.matcher(t).replaceFirst("");
        return t.trim();
    }

    public static int wordCount(String text) {
        String t = text == null ? "" : text.trim();
        return t.isEmpty() ? 0 : t.split("\\s+").length;
    }

    public static String norm(String s) {
        StringBuilder out = new StringBuilder();
        String lower = s.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < lower.length(); ) {
            int cp = lower.codePointAt(i);
            if (Character.isLetter(cp) || Character.isDigit(cp)) out.appendCodePoint(cp);
            i += Character.charCount(cp);
        }
        return out.toString();
    }
}
