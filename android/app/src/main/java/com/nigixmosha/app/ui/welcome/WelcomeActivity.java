package com.nigixmosha.app.ui.welcome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.activity.EdgeToEdge;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.model.User;
import com.nigixmosha.app.databinding.ActivityWelcomeBinding;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.auth.AuthActivity;
import com.nigixmosha.app.ui.main.MainActivity;

import java.util.Random;

import okhttp3.Call;

public class WelcomeActivity extends AppCompatActivity {
    private static final long MIN_VISIBLE_MS = 2700L;
    private static final long WAKING_HINT_MS = 2500L;
    private static final long SLOW_HINT_MS = 9000L;
    private static final long INTRO_DONE_MS = 1500L;
    private static final long SPLASH_FALLBACK_MS = 600L;

    private enum Destination { HOME, AUTH }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Runnable wakingHint = () -> showStatus(R.string.status_waking);
    private final Runnable slowHint = () -> showStatus(R.string.status_slow);
    private final Runnable routeCheck = this::maybeRoute;
    private final Runnable blink = this::blink;

    private ActivityWelcomeBinding b;
    private ApiClient api;
    private Call pending;
    private Destination destination;
    private boolean sessionEnded;
    private boolean introStarted;
    private boolean introDone;
    private boolean routed;
    private long shownAt;
    private String taglineText;
    private int taglineWords;
    private int accentStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        b = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        Ui.padForSystemBars(b.footer, true, false, true, true);
        Ui.padForSystemBars(b.center, true, false, true, false);

        api = ApiClient.get(this);
        shownAt = SystemClock.uptimeMillis();
        b.wordmark.setText(Ui.wordmark(this));
        b.retry.setOnClickListener(v -> verify());

        prepareTagline();
        prepareIntro();
        waitForFirstFrame();
        verify();
    }

    private void waitForFirstFrame() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSplashScreen().setOnExitAnimationListener(splash -> {
                splash.remove();
                beginIntro(true);
            });
        }
        b.getRoot().getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                b.getRoot().getViewTreeObserver().removeOnPreDrawListener(this);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    handler.postDelayed(() -> beginIntro(false), SPLASH_FALLBACK_MS);
                } else {
                    b.getRoot().post(() -> beginIntro(false));
                }
                return true;
            }
        });
    }

    private void beginIntro(boolean fromSplash) {
        if (introStarted || isFinishing()) return;
        introStarted = true;
        shownAt = SystemClock.uptimeMillis();
        float startScale = fromSplash ? 1.5f : 0.86f;
        b.logo.setScaleX(startScale);
        b.logo.setScaleY(startScale);
        b.logo.setAlpha(fromSplash ? 1f : 0f);
        playIntro();
    }

    private void prepareTagline() {
        taglineText = getString(R.string.tagline);
        accentStart = taglineText.indexOf(getString(R.string.tagline_accent_from));
        taglineWords = taglineText.split(" ").length;
        renderTagline(0f);
    }

    private void prepareIntro() {
        b.logo.setAlpha(0f);
        b.wordmark.setAlpha(0f);
        b.wordmark.setTranslationY(Ui.dp(this, 16));
        b.tagline.setTranslationY(Ui.dp(this, 10));
        b.wave.setIntro(0f);
        b.signature.setAlpha(0f);
    }

    private void playIntro() {
        if (!Ui.motionEnabled()) {
            b.logo.setAlpha(1f);
            b.logo.setScaleX(1f);
            b.logo.setScaleY(1f);
            b.wordmark.setAlpha(1f);
            b.wordmark.setTranslationY(0f);
            b.tagline.setTranslationY(0f);
            renderTagline(taglineWords);
            b.wave.setIntro(1f);
            b.signature.setAlpha(1f);
            onIntroDone();
            return;
        }

        float logoCenter = b.center.getTop() + b.logo.getTop() + b.logo.getHeight() / 2f;
        b.logo.setTranslationY(b.getRoot().getHeight() / 2f - logoCenter);

        ObjectAnimator logoFade = ObjectAnimator.ofFloat(b.logo, View.ALPHA, b.logo.getAlpha(), 1f);
        logoFade.setDuration(320);

        ObjectAnimator logoMove = ObjectAnimator.ofPropertyValuesHolder(b.logo,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, b.logo.getTranslationY(), 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, b.logo.getScaleX(), 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, b.logo.getScaleY(), 1f));
        logoMove.setStartDelay(160);
        logoMove.setDuration(820);
        logoMove.setInterpolator(Ui.EMPHASIZED);

        ObjectAnimator wordmark = ObjectAnimator.ofPropertyValuesHolder(b.wordmark,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, b.wordmark.getTranslationY(), 0f));
        wordmark.setStartDelay(520);
        wordmark.setDuration(560);
        wordmark.setInterpolator(Ui.DECELERATE);

        ValueAnimator words = ValueAnimator.ofFloat(0f, taglineWords);
        words.setStartDelay(760);
        words.setDuration(980);
        words.addUpdateListener(a -> renderTagline((float) a.getAnimatedValue()));

        ObjectAnimator taglineRise = ObjectAnimator.ofFloat(b.tagline, View.TRANSLATION_Y, b.tagline.getTranslationY(), 0f);
        taglineRise.setStartDelay(760);
        taglineRise.setDuration(700);
        taglineRise.setInterpolator(Ui.DECELERATE);

        ObjectAnimator wave = ObjectAnimator.ofFloat(b.wave, "intro", 0f, 1f);
        wave.setStartDelay(900);
        wave.setDuration(1000);
        wave.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                b.wave.start();
            }
        });

        ObjectAnimator signature = ObjectAnimator.ofFloat(b.signature, View.ALPHA, 0f, 1f);
        signature.setStartDelay(1300);
        signature.setDuration(600);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(logoFade, logoMove, wordmark, words, taglineRise, wave, signature);
        set.start();
        handler.postDelayed(this::onIntroDone, INTRO_DONE_MS);
    }

    private void onIntroDone() {
        introDone = true;
        b.wave.start();
        handler.postDelayed(blink, 900);
        maybeRoute();
    }

    private void renderTagline(float progress) {
        int secondary = ContextCompat.getColor(this, R.color.text_secondary);
        int accent = ContextCompat.getColor(this, R.color.accent);
        SpannableStringBuilder text = new SpannableStringBuilder(taglineText);
        int start = 0;
        int word = 0;
        while (start < taglineText.length()) {
            int end = taglineText.indexOf(' ', start);
            if (end < 0) end = taglineText.length();
            float alpha = Math.max(0f, Math.min(1f, progress - word));
            int base = accentStart >= 0 && start >= accentStart ? accent : secondary;
            text.setSpan(new ForegroundColorSpan(ColorUtils.setAlphaComponent(base, Math.round(alpha * 255))),
                    start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            start = end + 1;
            word++;
        }
        b.tagline.setText(text);
    }

    private void verify() {
        hideRetry();
        destination = null;
        if (!api.hasSession()) {
            destination = Destination.AUTH;
            maybeRoute();
            return;
        }
        handler.postDelayed(wakingHint, WAKING_HINT_MS);
        handler.postDelayed(slowHint, SLOW_HINT_MS);
        pending = api.me(new ApiClient.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                clearHints();
                if (user != null) {
                    destination = Destination.HOME;
                } else {
                    sessionEnded = true;
                    destination = Destination.AUTH;
                }
                maybeRoute();
            }

            @Override
            public void onError(ApiException error) {
                clearHints();
                if (error.isUnauthorized()) {
                    sessionEnded = true;
                    destination = Destination.AUTH;
                    maybeRoute();
                } else {
                    showRetry(error.getMessage());
                }
            }
        });
    }

    private void maybeRoute() {
        handler.removeCallbacks(routeCheck);
        if (routed || destination == null || !introDone) return;
        long wait = MIN_VISIBLE_MS - (SystemClock.uptimeMillis() - shownAt);
        if (wait > 0) {
            handler.postDelayed(routeCheck, wait);
            return;
        }
        routed = true;
        Intent next = destination == Destination.HOME
                ? new Intent(this, MainActivity.class)
                : AuthActivity.intent(this, sessionEnded ? R.string.notice_expired : 0);
        handler.removeCallbacks(blink);
        Ui.go(this, next);
    }

    private void showStatus(@StringRes int message) {
        b.status.setText(message);
        b.status.animate().alpha(1f).setDuration(320).start();
        if (b.progress.getVisibility() != View.VISIBLE) {
            b.progress.setAlpha(0f);
            b.progress.setVisibility(View.VISIBLE);
            b.progress.animate().alpha(1f).setDuration(320).start();
        }
    }

    private void showRetry(String message) {
        b.progress.setVisibility(View.INVISIBLE);
        b.status.setText(message);
        b.status.animate().alpha(1f).setDuration(240).start();
        b.retry.setAlpha(0f);
        b.retry.setTranslationY(Ui.dp(this, 8));
        b.retry.setVisibility(View.VISIBLE);
        b.retry.animate().alpha(1f).translationY(0f).setDuration(360).setInterpolator(Ui.DECELERATE).start();
        Ui.haptic(b.retry, false);
    }

    private void hideRetry() {
        b.retry.setVisibility(View.GONE);
        b.status.setAlpha(0f);
        b.progress.setVisibility(View.INVISIBLE);
    }

    private void clearHints() {
        handler.removeCallbacks(wakingHint);
        handler.removeCallbacks(slowHint);
        b.progress.setVisibility(View.INVISIBLE);
        b.status.animate().alpha(0f).setDuration(200).start();
    }

    private void blink() {
        Drawable drawable = b.logo.getDrawable();
        if (drawable instanceof Animatable && Ui.motionEnabled()) {
            ((Animatable) drawable).start();
            handler.postDelayed(blink, 2600 + random.nextInt(2200));
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (pending != null) pending.cancel();
        super.onDestroy();
    }
}
