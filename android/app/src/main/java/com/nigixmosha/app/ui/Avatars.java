package com.nigixmosha.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.nigixmosha.app.R;

import java.util.List;

public final class Avatars {
    private Avatars() {
    }

    public static TextView make(Context context, String label, int color, int sizeDp, int ringColor) {
        TextView view = new TextView(context);
        int size = Ui.dpi(context, sizeDp);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        style(view, label, color, sizeDp, ringColor);
        return view;
    }

    public static void style(TextView view, String label, int color, int sizeDp, int ringColor) {
        Context context = view.getContext();
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(color);
        if (ringColor != 0) circle.setStroke(Ui.dpi(context, 2), ringColor);
        view.setBackground(circle);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, Math.max(9, sizeDp * 0.38f));
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setTextColor(0xFFFFFFFF);
        view.setText(label);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public interface Person {
        String name();

        String color();
    }

    public static Person person(String name, String color) {
        return new Person() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String color() {
                return color;
            }
        };
    }

    public static void stack(LinearLayout row, List<? extends Person> people, int max, int sizeDp, int ringColor, Person lead) {
        Context context = row.getContext();
        row.removeAllViews();
        int shown = 0;
        if (lead != null) {
            row.addView(make(context, Format.initial(lead.name()), Format.color(lead.color(), lead.name()), sizeDp, ringColor));
            shown++;
        }
        for (Person p : people) {
            if (shown >= max) break;
            TextView v = make(context, Format.initial(p.name()), Format.color(p.color(), p.name()), sizeDp, ringColor);
            if (row.getChildCount() > 0) ((LinearLayout.LayoutParams) v.getLayoutParams()).setMarginStart(-Ui.dpi(context, sizeDp * 0.28f));
            row.addView(v);
            shown++;
        }
        int total = people.size() + (lead != null ? 1 : 0);
        if (total > max) {
            TextView more = make(context, "+" + (total - max), ContextCompat.getColor(context, R.color.surface_highest), sizeDp, ringColor);
            ((LinearLayout.LayoutParams) more.getLayoutParams()).setMarginStart(-Ui.dpi(context, sizeDp * 0.28f));
            row.addView(more);
        }
        row.setVisibility(row.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }
}
