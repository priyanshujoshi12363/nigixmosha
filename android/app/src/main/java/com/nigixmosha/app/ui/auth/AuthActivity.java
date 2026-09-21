package com.nigixmosha.app.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputLayout;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.model.User;
import com.nigixmosha.app.databinding.ActivityAuthBinding;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.main.MainActivity;

import java.util.Locale;
import java.util.regex.Pattern;

import okhttp3.Call;

public class AuthActivity extends AppCompatActivity {
    private static final String EXTRA_NOTICE = "notice";
    private static final String STATE_REGISTER = "register";
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_]{3,24}$");
    private static final int[] STRENGTH_LABELS = {
            R.string.strength_0, R.string.strength_1, R.string.strength_2, R.string.strength_3, R.string.strength_4
    };

    private ActivityAuthBinding b;
    private ApiClient api;
    private Call pending;
    private boolean register;
    private boolean busy;
    private int toggleWidth;

    public static Intent intent(Context context, @StringRes int notice) {
        Intent intent = new Intent(context, AuthActivity.class);
        if (notice != 0) intent.putExtra(EXTRA_NOTICE, notice);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        b = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        api = ApiClient.get(this);
        register = savedInstanceState != null && savedInstanceState.getBoolean(STATE_REGISTER);

        b.wordmark.setText(Ui.wordmark(this));
        applyInsets();
        wireInputs();
        wireActions();
        applyMode(false);

        int notice = getIntent().getIntExtra(EXTRA_NOTICE, 0);
        if (notice != 0 && savedInstanceState == null) showBanner(getString(notice), false);
        if (savedInstanceState == null) playEntrance();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_REGISTER, register);
    }

    private void applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            if (ime.bottom > 0) keepFocusedVisible();
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void keepFocusedVisible() {
        b.scroll.post(() -> {
            View focus = getCurrentFocus();
            if (focus == null) return;
            Rect rect = new Rect();
            focus.getDrawingRect(rect);
            b.scroll.offsetDescendantRectToMyCoords(focus, rect);
            int visibleBottom = b.scroll.getScrollY() + b.scroll.getHeight() - b.scroll.getPaddingBottom();
            int wanted = rect.bottom + Ui.dpi(this, 88);
            if (wanted > visibleBottom) b.scroll.smoothScrollBy(0, wanted - visibleBottom);
        });
    }

    private void wireInputs() {
        b.usernameInput.setFilters(new InputFilter[]{usernameFilter(), new InputFilter.LengthFilter(24)});
        clearErrorOnEdit(b.nameInput, b.nameLayout);
        clearErrorOnEdit(b.usernameInput, b.usernameLayout);
        clearErrorOnEdit(b.passwordInput, b.passwordLayout);
        b.passwordInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updateStrength(s.toString());
            }
        });
        b.passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        View.OnFocusChangeListener reveal = (v, hasFocus) -> {
            if (hasFocus) keepFocusedVisible();
        };
        b.nameInput.setOnFocusChangeListener(reveal);
        b.usernameInput.setOnFocusChangeListener(reveal);
        b.passwordInput.setOnFocusChangeListener(reveal);
    }

    private void wireActions() {
        b.tabLogin.setOnClickListener(v -> setMode(false));
        b.tabRegister.setOnClickListener(v -> setMode(true));
        b.switchMode.setOnClickListener(v -> setMode(!register));
        b.submit.setOnClickListener(v -> submit());
        b.toggle.addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> {
            if (r - l != toggleWidth) {
                toggleWidth = r - l;
                v.post(() -> syncIndicator(false));
            }
        });
    }

    private void setMode(boolean toRegister) {
        if (busy || register == toRegister) return;
        register = toRegister;
        applyMode(true);
    }

    private void applyMode(boolean animate) {
        if (animate && Ui.motionEnabled()) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(300);
            transition.setInterpolator(Ui.EMPHASIZED);
            TransitionManager.beginDelayedTransition(b.content, transition);
        }
        b.tabLogin.setSelected(!register);
        b.tabRegister.setSelected(register);
        b.nameLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        b.usernameLayout.setHelperText(register ? getString(R.string.field_username_helper) : null);
        b.passwordInput.setAutofillHints(register ? "newPassword" : "password");
        b.submit.setText(register ? R.string.action_register : R.string.action_login);
        b.switchMode.setText(register
                ? Ui.leadAction(this, R.string.switch_to_login_lead, R.string.switch_to_login_action)
                : Ui.leadAction(this, R.string.switch_to_register_lead, R.string.switch_to_register_action));
        clearErrors();
        if (animate) hideBanner(false);
        updateStrength(text(b.passwordInput));
        swapHeadline(animate);
        syncIndicator(animate);
    }

    private void swapHeadline(boolean animate) {
        int title = register ? R.string.auth_title_register : R.string.auth_title_login;
        int subtitle = register ? R.string.auth_sub_register : R.string.auth_sub_login;
        if (!animate || !Ui.motionEnabled()) {
            b.title.setText(title);
            b.subtitle.setText(subtitle);
            return;
        }
        float shift = Ui.dp(this, 8);
        b.title.animate().alpha(0f).translationY(-shift).setDuration(120).withEndAction(() -> {
            b.title.setText(title);
            b.title.setTranslationY(shift);
            b.title.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(Ui.DECELERATE).start();
        }).start();
        b.subtitle.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            b.subtitle.setText(subtitle);
            b.subtitle.animate().alpha(1f).setDuration(300).setStartDelay(40).start();
        }).start();
    }

    private void syncIndicator(boolean animate) {
        int inner = b.toggle.getWidth() - b.toggle.getPaddingLeft() - b.toggle.getPaddingRight();
        if (inner <= 0) return;
        int half = inner / 2;
        ViewGroup.LayoutParams params = b.toggleIndicator.getLayoutParams();
        if (params.width != half) {
            params.width = half;
            b.toggleIndicator.setLayoutParams(params);
        }
        boolean rtl = b.toggle.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        float target = register ? (rtl ? -half : half) : 0f;
        if (animate && Ui.motionEnabled()) {
            b.toggleIndicator.animate().translationX(target).setDuration(360)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f)).start();
        } else {
            b.toggleIndicator.setTranslationX(target);
        }
    }

    private void submit() {
        if (busy) return;
        String username = text(b.usernameInput).trim().toLowerCase(Locale.ROOT);
        String password = text(b.passwordInput);
        String name = text(b.nameInput).trim();
        clearErrors();
        hideBanner(true);

        TextInputLayout firstBad = null;
        if (register) {
            if (!USERNAME.matcher(username).matches()) {
                b.usernameLayout.setError(getString(username.isEmpty() ? R.string.err_username_empty : R.string.err_username_format));
                firstBad = b.usernameLayout;
            }
            if (password.length() < 8 || password.length() > 128) {
                int message = password.isEmpty() ? R.string.err_password_empty
                        : password.length() < 8 ? R.string.err_password_short : R.string.err_password_long;
                b.passwordLayout.setError(getString(message));
                if (firstBad == null) firstBad = b.passwordLayout;
            }
        } else {
            if (username.isEmpty()) {
                b.usernameLayout.setError(getString(R.string.err_username_empty));
                firstBad = b.usernameLayout;
            }
            if (password.isEmpty()) {
                b.passwordLayout.setError(getString(R.string.err_password_empty));
                if (firstBad == null) firstBad = b.passwordLayout;
            }
        }
        if (firstBad != null) {
            Ui.shake(firstBad);
            Ui.haptic(firstBad, false);
            if (firstBad.getEditText() != null) firstBad.getEditText().requestFocus();
            return;
        }

        Ui.hideKeyboard(b.getRoot());
        setBusy(true);
        ApiClient.Callback<User> callback = new ApiClient.Callback<User>() {
            @Override
            public void onSuccess(User user) {
                onSignedIn();
            }

            @Override
            public void onError(ApiException error) {
                setBusy(false);
                showFailure(error);
            }
        };
        pending = register
                ? api.signup(username, password, name, callback)
                : api.login(username, password, callback);
    }

    private void onSignedIn() {
        Ui.haptic(b.submit, true);
        b.submitProgress.hide();
        b.submit.setText(null);
        b.submit.setIconResource(R.drawable.ic_check);
        b.submit.setIconTint(ContextCompat.getColorStateList(this, R.color.on_accent));
        b.submit.setEnabled(true);
        b.submit.setClickable(false);
        b.submit.postDelayed(() -> Ui.go(this, new Intent(this, MainActivity.class)), Ui.motionEnabled() ? 420 : 0);
    }

    private void showFailure(ApiException error) {
        if (error.status() == 409) {
            b.usernameLayout.setError(error.getMessage());
            Ui.shake(b.usernameLayout);
            Ui.haptic(b.usernameLayout, false);
            b.usernameInput.requestFocus();
            return;
        }
        showBanner(error.getMessage(), true);
        Ui.shake(b.banner);
        Ui.haptic(b.banner, false);
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        b.submit.setEnabled(!busy);
        b.nameInput.setEnabled(!busy);
        b.usernameInput.setEnabled(!busy);
        b.passwordInput.setEnabled(!busy);
        b.tabLogin.setEnabled(!busy);
        b.tabRegister.setEnabled(!busy);
        b.switchMode.setEnabled(!busy);
        if (busy) {
            b.submit.setText(null);
            b.submitProgress.show();
        } else {
            b.submitProgress.hide();
            b.submit.setText(register ? R.string.action_register : R.string.action_login);
        }
    }

    private void showBanner(String message, boolean error) {
        b.banner.setBackgroundResource(error ? R.drawable.bg_banner_error : R.drawable.bg_banner_info);
        b.bannerIcon.setImageResource(error ? R.drawable.ic_error : R.drawable.ic_info);
        b.bannerIcon.setImageTintList(ContextCompat.getColorStateList(this, error ? R.color.error : R.color.accent));
        b.bannerText.setText(message);
        if (b.banner.getVisibility() == View.VISIBLE) return;
        if (Ui.motionEnabled()) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(260);
            transition.setInterpolator(Ui.EMPHASIZED);
            TransitionManager.beginDelayedTransition(b.content, transition);
        }
        b.banner.setVisibility(View.VISIBLE);
    }

    private void hideBanner(boolean animate) {
        if (b.banner.getVisibility() != View.VISIBLE) return;
        if (animate && Ui.motionEnabled()) {
            AutoTransition transition = new AutoTransition();
            transition.setDuration(200);
            TransitionManager.beginDelayedTransition(b.content, transition);
        }
        b.banner.setVisibility(View.GONE);
    }

    private void updateStrength(String password) {
        boolean show = register && !password.isEmpty();
        if (show != (b.strengthRow.getVisibility() == View.VISIBLE)) {
            if (Ui.motionEnabled()) {
                AutoTransition transition = new AutoTransition();
                transition.setDuration(220);
                TransitionManager.beginDelayedTransition(b.content, transition);
            }
            b.strengthRow.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (!show) return;
        int score = strength(password);
        int color = ContextCompat.getColor(this, score <= 1 ? R.color.crimson : score == 2 ? R.color.amber : R.color.accent);
        b.strength.setIndicatorColor(color);
        b.strength.setProgressCompat(Math.max(8, score * 25), true);
        b.strengthLabel.setText(STRENGTH_LABELS[score]);
        b.strengthLabel.setTextColor(color);
    }

    static int strength(String password) {
        if (password.length() < 8) return 0;
        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLowerCase(c)) lower = true;
            else if (Character.isUpperCase(c)) upper = true;
            else if (Character.isDigit(c)) digit = true;
            else symbol = true;
        }
        int score = 1;
        if (password.length() >= 12) score++;
        if ((lower && upper) || (digit && (lower || upper))) score++;
        if (symbol) score++;
        return Math.min(4, score);
    }

    private void playEntrance() {
        if (!Ui.motionEnabled()) return;
        float rise = Ui.dp(this, 22);
        for (int i = 0; i < b.content.getChildCount(); i++) {
            View child = b.content.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(rise);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(90L + i * 55L)
                    .setDuration(560)
                    .setInterpolator(Ui.DECELERATE)
                    .start();
        }
    }

    private void clearErrors() {
        b.nameLayout.setError(null);
        b.usernameLayout.setError(null);
        b.passwordLayout.setError(null);
    }

    private static void clearErrorOnEdit(EditText input, TextInputLayout layout) {
        input.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (layout.getError() != null) layout.setError(null);
            }
        });
    }

    private static InputFilter usernameFilter() {
        return (source, start, end, dest, dstart, dend) -> {
            StringBuilder out = new StringBuilder(end - start);
            for (int i = start; i < end; i++) {
                char c = Character.toLowerCase(source.charAt(i));
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') out.append(c);
            }
            return out.toString().contentEquals(source.subSequence(start, end)) ? null : out;
        };
    }

    private static String text(EditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    @Override
    protected void onDestroy() {
        if (pending != null) pending.cancel();
        super.onDestroy();
    }

    private abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
