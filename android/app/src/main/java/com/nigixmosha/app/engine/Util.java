package com.nigixmosha.app.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nigixmosha.app.data.ApiException;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Util {
    public static final int UNICODE = unicodeFlag();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern THINK = Pattern.compile("<think>[\\s\\S]*?</think>", Pattern.CASE_INSENSITIVE);
    private static final Set<Integer> NO_RETRY = new HashSet<>(Arrays.asList(400, 401, 403, 404, 413, 415, 422));

    public interface Task<T> {
        T run() throws Exception;
    }

    private Util() {
    }

    private static int unicodeFlag() {
        try {
            Pattern.compile("a", Pattern.UNICODE_CHARACTER_CLASS);
            return Pattern.UNICODE_CHARACTER_CLASS;
        } catch (IllegalArgumentException e) {
            return 0;
        }
    }

    public static String uid(String prefix) {
        String a = Long.toString(Math.abs(RANDOM.nextLong()), 36);
        String rand = a.length() > 8 ? a.substring(0, 8) : a;
        String time = Long.toString(System.currentTimeMillis(), 36);
        return prefix + rand + time.substring(Math.max(0, time.length() - 4));
    }

    public static double clamp(double n, double min, double max) {
        return Math.min(max, Math.max(min, n));
    }

    public static JsonElement extractJSON(String raw) {
        String s = raw.trim();
        Matcher fence = FENCE.matcher(s);
        if (fence.find()) s = fence.group(1).trim();
        s = THINK.matcher(s).replaceAll("").trim();
        try {
            JsonElement el = JsonParser.parseString(s);
            if (el.isJsonObject() || el.isJsonArray()) return el;
        } catch (Exception ignored) {
        }
        int start = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                start = i;
                break;
            }
        }
        if (start == -1) throw new IllegalArgumentException("Model did not return JSON");
        char open = s.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        boolean esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return JsonParser.parseString(s.substring(start, i + 1));
            }
        }
        throw new IllegalArgumentException("Model returned incomplete JSON");
    }

    public static <T> T withRetry(Task<T> fn, int tries, Cancel cancel) throws Exception {
        return withRetry(fn, tries, 800, cancel);
    }

    public static <T> T withRetry(Task<T> fn, int tries, long baseDelay, Cancel cancel) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < tries; attempt++) {
            cancel.check();
            try {
                return fn.run();
            } catch (Cancel.Cancelled c) {
                throw c;
            } catch (Exception e) {
                last = e;
                if (cancel.isCancelled()) throw new Cancel.Cancelled();
                if (e instanceof ApiException && NO_RETRY.contains(((ApiException) e).status())) throw e;
                if (attempt < tries - 1) cancel.sleep(baseDelay * (1L << attempt));
            }
        }
        throw last;
    }

    public static int status(Throwable e) {
        return e instanceof ApiException ? ((ApiException) e).status() : 0;
    }

    public static String str(JsonElement el) {
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return "";
        return el.getAsString().trim();
    }

    public static List<String> strList(JsonElement el) {
        List<String> out = new java.util.ArrayList<>();
        if (el == null || !el.isJsonArray()) return out;
        for (JsonElement e : el.getAsJsonArray()) {
            String s = str(e);
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }
}
