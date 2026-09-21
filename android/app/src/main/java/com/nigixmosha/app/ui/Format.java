package com.nigixmosha.app.ui;

import android.graphics.Color;
import android.text.format.DateUtils;

import java.text.BreakIterator;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {
    private static final int[] FALLBACK = {
            0xFF0D9488, 0xFF4F46E5, 0xFF2563EB, 0xFFBE123C, 0xFF0891B2, 0xFFD97706, 0xFF7C3AED
    };

    private static final String JUST_NOW = "Just now";

    private Format() {
    }

    public static String clock(long ms) {
        long total = Math.max(0, ms / 1000);
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, s)
                : String.format(Locale.US, "%d:%02d", m, s);
    }

    public static String number(long value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    public static String language(String tag) {
        if (tag == null || tag.trim().isEmpty()) return null;
        String name = Locale.forLanguageTag(tag.trim()).getDisplayLanguage(Locale.ENGLISH);
        return name.isEmpty() ? tag.trim() : name;
    }

    public static CharSequence relative(String iso) {
        try {
            long time = Instant.parse(iso).toEpochMilli();
            if (Math.abs(System.currentTimeMillis() - time) < DateUtils.MINUTE_IN_MILLIS) return JUST_NOW;
            return DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE);
        } catch (Exception e) {
            return "";
        }
    }

    public static String monthYear(String iso) {
        try {
            return DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.parse(iso));
        } catch (Exception e) {
            return null;
        }
    }

    public static String initial(String name) {
        if (name == null) return "?";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "?";
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(trimmed);
        int end = it.next();
        return trimmed.substring(0, end == BreakIterator.DONE ? 1 : end).toUpperCase(Locale.getDefault());
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return initial(parts[0]);
        return initial(parts[0]) + initial(parts[parts.length - 1]);
    }

    public static int color(String hex, String seed) {
        if (hex != null) {
            try {
                return Color.parseColor(hex.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        int index = Math.abs((seed == null ? 0 : seed.hashCode()) % FALLBACK.length);
        return FALLBACK[index];
    }
}
