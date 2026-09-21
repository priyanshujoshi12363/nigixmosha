package com.nigixmosha.app.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.inputmethod.InputMethodManager;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.nigixmosha.app.BuildConfig;
import com.nigixmosha.app.R;

public final class Ui {
    public static final Interpolator EMPHASIZED = new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final Interpolator DECELERATE = new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final Interpolator ACCELERATE = new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private Ui() {
    }

    public static float dp(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }

    public static int dpi(Context context, float value) {
        return Math.round(dp(context, value));
    }

    public static boolean motionEnabled() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    public static void haptic(View view, boolean success) {
        int constant;
        if (Build.VERSION.SDK_INT >= 30) {
            constant = success ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT;
        } else {
            constant = success ? HapticFeedbackConstants.CONTEXT_CLICK : HapticFeedbackConstants.LONG_PRESS;
        }
        view.performHapticFeedback(constant);
    }

    public static void shake(View view) {
        if (!motionEnabled()) return;
        float d = dp(view.getContext(), 1);
        ObjectAnimator shake = ObjectAnimator.ofFloat(view, View.TRANSLATION_X,
                0, 12 * d, -10 * d, 8 * d, -5 * d, 3 * d, 0);
        shake.setDuration(420);
        shake.start();
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = ContextCompat.getSystemService(view.getContext(), InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        View focus = view.getRootView().findFocus();
        if (focus != null) focus.clearFocus();
    }

    @SuppressWarnings("deprecation")
    public static void go(Activity from, Intent to) {
        from.startActivity(to);
        from.overridePendingTransition(R.anim.enter_fade_up, R.anim.exit_fade);
        from.finish();
    }

    public static void openStudio(Activity activity, View anchor) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.BASE_URL + "/studio"));
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(anchor, R.string.no_browser, Snackbar.LENGTH_LONG).show();
        }
    }

    public static CharSequence wordmark(Context context) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        text.append(context.getString(R.string.wordmark_a));
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int start = text.length();
        text.append(context.getString(R.string.wordmark_b));
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_tertiary)),
                start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    public static CharSequence leadAction(Context context, int lead, int action) {
        SpannableStringBuilder text = new SpannableStringBuilder(context.getString(lead)).append(' ');
        int start = text.length();
        text.append(context.getString(action));
        text.setSpan(new StyleSpan(Typeface.BOLD), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context, R.color.accent)),
                start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return text;
    }

    public static void padForSystemBars(View view, boolean left, boolean top, boolean right, boolean bottom) {
        int l = view.getPaddingLeft();
        int t = view.getPaddingTop();
        int r = view.getPaddingRight();
        int b = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(l + (left ? bars.left : 0), t + (top ? bars.top : 0),
                    r + (right ? bars.right : 0), b + (bottom ? bars.bottom : 0));
            return insets;
        });
    }
}
