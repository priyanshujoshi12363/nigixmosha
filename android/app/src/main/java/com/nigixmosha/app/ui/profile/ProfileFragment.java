package com.nigixmosha.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import com.nigixmosha.app.BuildConfig;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.SettingsStore;
import com.nigixmosha.app.data.model.User;
import com.nigixmosha.app.databinding.FragmentProfileBinding;
import com.nigixmosha.app.databinding.ItemEngineBinding;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.model.Types;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.main.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProfileFragment extends Fragment implements SettingsStore.Listener, MainActivity.UserAware {
    private FragmentProfileBinding b;
    private SettingsStore settings;
    private ApiClient api;
    private final Map<String, ItemEngineBinding> cards = new HashMap<>();
    private final Map<String, Cancel> tests = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Slider> sliders = new ArrayList<>();
    private final List<TextView> sliderValues = new ArrayList<>();
    private MediaPlayer testPlayer;
    private Cancel passwordCall;
    private Cancel deleteCall;
    private boolean bindingAdvanced;

    private static final int[][] SLIDERS = {{1, 8, 1}, {0, 1000, 20}, {0, 1200, 20}, {0, 2000, 20}};
    private static final int[] SLIDER_LABELS = {R.string.adv_concurrency, R.string.adv_line_gap, R.string.adv_speaker_gap, R.string.adv_paragraph_gap};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentProfileBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        settings = SettingsStore.get(requireContext());
        api = ApiClient.get(requireContext());
        b.backdrop.setDrifting(false);
        ViewCompat.setOnApplyWindowInsetsListener(b.scroll, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean bottomNav = requireActivity().findViewById(R.id.bottomNav) != null;
            v.setPadding(bars.left, bars.top, bars.right, Math.max(ime.bottom, bottomNav ? 0 : bars.bottom));
            return insets;
        });
        bindUser(api.cachedUser());
        b.signOut.setOnClickListener(v -> signOut(false));
        b.signOutAll.setOnClickListener(v -> signOut(true));
        b.updatePassword.setOnClickListener(v -> changePassword());
        b.confirm.setOnEditorActionListener((v, actionId, event) -> {
            changePassword();
            return true;
        });
        buildEngines();
        buildSliders();
        b.normalize.setOnCheckedChangeListener((btn, checked) -> {
            if (bindingAdvanced) return;
            Types.Advanced a = settings.advanced();
            a.normalize = checked;
            settings.setAdvanced(a);
        });
        b.resetAdvanced.setOnClickListener(v -> settings.resetAdvanced());
        b.clearKeys.setOnClickListener(v -> {
            settings.clearKeys();
            for (ItemEngineBinding card : cards.values()) card.key.setText("");
            b.clearKeys.setText(R.string.keys_removed);
            b.clearKeys.setIconResource(R.drawable.ic_check);
            handler.postDelayed(() -> {
                if (b == null) return;
                b.clearKeys.setText(R.string.remove_keys);
                b.clearKeys.setIcon(null);
            }, 2500);
        });
        int mode = settings.themeMode();
        b.themeGroup.check(mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO ? R.id.themeLight
                : mode == androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES ? R.id.themeDark : R.id.themeSystem);
        b.themeGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            int next = id == R.id.themeLight ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    : id == R.id.themeDark ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (next != settings.themeMode()) settings.setThemeMode(next);
        });
        b.version.setText(getString(R.string.version, BuildConfig.VERSION_NAME));
        b.privacyPolicy.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(api.pageUrl("privacy").toString())));
            } catch (Exception ignored) {
            }
        });
        b.deleteAccount.setOnClickListener(v -> {
            if (b.deleteForm.getVisibility() == View.VISIBLE) deleteAccount();
            else showDeleteForm(true);
        });
        b.deleteCancel.setOnClickListener(v -> showDeleteForm(false));
        b.deletePassword.setOnEditorActionListener((v, actionId, event) -> {
            deleteAccount();
            return true;
        });
        settings.addListener(this);
        refresh();
    }

    @Override
    public void onUser(User user) {
        bindUser(user);
    }

    private void bindUser(User user) {
        if (b == null || user == null) return;
        b.avatar.setText(Format.initials(user.name()));
        b.name.setText(user.name());
        b.username.setText("@" + user.username);
        String since = Format.monthYear(user.createdAt);
        b.since.setVisibility(since == null ? View.GONE : View.VISIBLE);
        if (since != null) b.since.setText(getString(R.string.member_since, since));
    }

    private void signOut(boolean everywhere) {
        b.signOut.setEnabled(false);
        b.signOutAll.setEnabled(false);
        (everywhere ? b.signOutAll : b.signOut).setText(R.string.signing_out);
        api.logout(everywhere, new ApiClient.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                if (isAdded()) ((MainActivity) requireActivity()).toAuth(R.string.notice_signed_out);
            }

            @Override
            public void onError(ApiException error) {
                if (isAdded()) ((MainActivity) requireActivity()).toAuth(R.string.notice_signed_out);
            }
        });
    }

    private static String text(TextView v) {
        return v.getText() == null ? "" : v.getText().toString();
    }

    private void passwordStatus(String message, boolean ok) {
        b.passwordStatus.setVisibility(View.VISIBLE);
        b.passwordStatus.setText(message);
        b.passwordStatus.setBackgroundResource(ok ? R.drawable.bg_banner_info : R.drawable.bg_banner_error);
        b.passwordStatus.setTextColor(ContextCompat.getColor(requireContext(), ok ? R.color.accent : R.color.error));
    }

    private void changePassword() {
        if (passwordCall != null) return;
        String current = text(b.current);
        String next = text(b.next);
        String confirm = text(b.confirm);
        if (current.isEmpty() || next.isEmpty()) return;
        if (next.length() < 8) {
            passwordStatus(getString(R.string.pw_too_short), false);
            Ui.shake(b.nextLayout);
            return;
        }
        if (!next.equals(confirm)) {
            passwordStatus(getString(R.string.pw_mismatch), false);
            Ui.shake(b.confirmLayout);
            return;
        }
        Ui.hideKeyboard(b.getRoot());
        b.updatePassword.setEnabled(false);
        b.updatePassword.setText("");
        b.passwordBusy.setVisibility(View.VISIBLE);
        passwordCall = api.run(() -> {
            api.changePassword(current, next, new Cancel());
            return true;
        }, new ApiClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean value) {
                passwordCall = null;
                if (b == null) return;
                resetPasswordButton();
                b.current.setText("");
                b.next.setText("");
                b.confirm.setText("");
                passwordStatus(getString(R.string.pw_updated), true);
                Ui.haptic(b.updatePassword, true);
            }

            @Override
            public void onError(ApiException error) {
                passwordCall = null;
                if (b == null) return;
                resetPasswordButton();
                if (error.isUnauthorized()) ((MainActivity) requireActivity()).toAuth(R.string.notice_expired);
                else passwordStatus(error.getMessage(), false);
            }
        });
    }

    private void resetPasswordButton() {
        b.updatePassword.setEnabled(true);
        b.updatePassword.setText(R.string.update_password);
        b.passwordBusy.setVisibility(View.GONE);
    }

    private void showDeleteForm(boolean open) {
        if (deleteCall != null) return;
        b.deleteForm.setVisibility(open ? View.VISIBLE : View.GONE);
        b.deleteCancel.setVisibility(open ? View.VISIBLE : View.GONE);
        b.deleteStatus.setVisibility(View.GONE);
        b.deleteAccount.setText(open ? R.string.delete_account_confirm : R.string.delete_account);
        if (open) b.deletePassword.requestFocus();
        else {
            b.deletePassword.setText("");
            Ui.hideKeyboard(b.getRoot());
        }
    }

    private void deleteAccount() {
        if (deleteCall != null) return;
        String password = text(b.deletePassword);
        if (password.isEmpty()) {
            Ui.shake(b.deletePasswordLayout);
            return;
        }
        Ui.hideKeyboard(b.getRoot());
        b.deleteAccount.setEnabled(false);
        b.deleteCancel.setEnabled(false);
        b.deleteAccount.setText(R.string.deleting);
        b.deleteStatus.setVisibility(View.GONE);
        deleteCall = api.run(() -> {
            api.deleteAccount(password, new Cancel());
            return true;
        }, new ApiClient.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean value) {
                deleteCall = null;
                settings.clearKeys();
                if (isAdded()) ((MainActivity) requireActivity()).toAuth(R.string.notice_deleted);
            }

            @Override
            public void onError(ApiException error) {
                deleteCall = null;
                if (b == null) return;
                if (error.isUnauthorized()) {
                    ((MainActivity) requireActivity()).toAuth(R.string.notice_expired);
                    return;
                }
                b.deleteAccount.setEnabled(true);
                b.deleteCancel.setEnabled(true);
                b.deleteAccount.setText(R.string.delete_account_confirm);
                b.deleteStatus.setVisibility(View.VISIBLE);
                b.deleteStatus.setText(error.getMessage());
                Ui.shake(b.deletePasswordLayout);
            }
        });
    }

    private void buildEngines() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        boolean wide = getResources().getConfiguration().screenWidthDp >= 720;
        LinearLayout row = null;
        int i = 0;
        for (Types.ProviderMeta meta : Catalog.get().providers) {
            ItemEngineBinding card = ItemEngineBinding.inflate(inflater, b.engines, false);
            cards.put(meta.id, card);
            bindEngine(card, meta);
            if (wide) {
                if (i % 2 == 0) {
                    row = new LinearLayout(requireContext());
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (i > 0) rp.topMargin = Ui.dpi(requireContext(), 12);
                    b.engines.addView(row, rp);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (i % 2 == 1) lp.setMarginStart(Ui.dpi(requireContext(), 12));
                row.addView(card.getRoot(), lp);
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.topMargin = Ui.dpi(requireContext(), 12);
                b.engines.addView(card.getRoot(), lp);
            }
            i++;
        }
        if (wide && i % 2 == 1 && row != null) {
            row.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private void bindEngine(ItemEngineBinding card, Types.ProviderMeta meta) {
        Types.ProviderConfig cfg = settings.config(meta.id);
        card.tile.setText(meta.name.replaceAll("[^A-Za-z]", "").substring(0, 2));
        card.name.setText(meta.name);
        card.tagline.setText(meta.tagline);
        boolean showKey = meta.needsKey || meta.editableBaseUrl;
        boolean showModel = meta.models.size() > 1 || meta.editableBaseUrl;
        card.keyLayout.setVisibility(showKey ? View.VISIBLE : View.GONE);
        card.baseLayout.setVisibility(meta.editableBaseUrl ? View.VISIBLE : View.GONE);
        card.modelLayout.setVisibility(showModel ? View.VISIBLE : View.GONE);
        card.key.setText(cfg.apiKey);
        card.base.setText(cfg.baseUrl);
        card.model.setText(cfg.model == null || cfg.model.isEmpty() ? meta.defaultModel : cfg.model, false);
        card.model.setSimpleItems(meta.models.toArray(new String[0]));
        card.key.addTextChangedListener(debounced(v -> settings.setApiKey(meta.id, v.trim())));
        card.base.addTextChangedListener(debounced(v -> settings.setBaseUrl(meta.id, v.trim())));
        card.model.addTextChangedListener(debounced(v -> settings.setModel(meta.id, v.trim())));
        card.use.setOnClickListener(v -> {
            settings.setActiveTTS(meta.id);
            Ui.haptic(v, true);
        });
        card.test.setOnClickListener(v -> test(card, meta));
        if (meta.keyUrl != null) {
            card.keyLink.setVisibility(View.VISIBLE);
            card.keyLink.setText(meta.needsKey ? R.string.get_key : R.string.setup_guide);
            card.keyLink.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(meta.keyUrl)));
                } catch (Exception ignored) {
                }
            });
        } else {
            card.keyLink.setVisibility(View.GONE);
        }
    }

    private interface OnText {
        void apply(String value);
    }

    private TextWatcher debounced(OnText action) {
        return new TextWatcher() {
            private Runnable pending;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pending != null) handler.removeCallbacks(pending);
                String value = s.toString();
                pending = () -> action.apply(value);
                handler.postDelayed(pending, 350);
            }
        };
    }

    private void refresh() {
        if (b == null) return;
        String active = settings.activeTTS();
        for (Types.ProviderMeta meta : Catalog.get().providers) {
            ItemEngineBinding card = cards.get(meta.id);
            if (card == null) continue;
            boolean isActive = meta.id.equals(active);
            boolean serverKey = settings.server().hasServerKey(meta.id);
            boolean ready = settings.isReady(meta.id);
            boolean local = meta.editableBaseUrl && meta.defaultBaseUrl != null && meta.defaultBaseUrl.matches(".*(localhost|127\\.0\\.0\\.1).*");
            card.card.setStrokeColor(ContextCompat.getColor(requireContext(), isActive ? R.color.accent : R.color.stroke));
            card.card.setStrokeWidth(Ui.dpi(requireContext(), isActive ? 2 : 1));
            card.badges.removeAllViews();
            if (meta.isFree()) addBadge(card, getString(R.string.badge_free), R.color.accent);
            if (local) addBadge(card, getString(R.string.badge_local), R.color.tide);
            if (serverKey) addBadge(card, getString(R.string.badge_ready), R.color.violet);
            if (isActive) addBadge(card, getString(R.string.badge_active), R.color.accent);
            card.badges.setVisibility(card.badges.getChildCount() == 0 ? View.GONE : View.VISIBLE);
            card.keyLayout.setHint(getString(meta.needsKey && !serverKey ? R.string.api_key : R.string.api_key_optional));
            card.keyLayout.setPlaceholderText(getString(serverKey ? R.string.key_included : meta.needsKey ? R.string.key_paste : R.string.key_not_required));
            card.use.setEnabled(!isActive);
            card.use.setText(isActive ? R.string.in_use : R.string.use_for_voices);
            card.test.setEnabled(ready && !tests.containsKey(meta.id));
            card.keyLink.setVisibility(meta.keyUrl != null && !serverKey ? View.VISIBLE : View.GONE);
        }
        bindAdvanced();
    }

    private void addBadge(ItemEngineBinding card, String label, int colorRes) {
        Chip chip = new Chip(requireContext());
        chip.setText(label);
        chip.setTextSize(11);
        int color = ContextCompat.getColor(requireContext(), colorRes);
        chip.setTextColor(color);
        chip.setChipBackgroundColor(ColorStateList.valueOf((color & 0x00FFFFFF) | 0x22000000));
        chip.setChipStrokeWidth(0);
        chip.setChipMinHeight(Ui.dp(requireContext(), 22));
        chip.setEnsureMinTouchTargetSize(false);
        chip.setClickable(false);
        chip.setChipStartPadding(Ui.dp(requireContext(), 2));
        chip.setChipEndPadding(Ui.dp(requireContext(), 2));
        card.badges.addView(chip);
    }

    private void buildSliders() {
        Context c = requireContext();
        for (int i = 0; i < SLIDERS.length; i++) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label = new TextView(c);
            label.setText(SLIDER_LABELS[i]);
            label.setTextColor(ContextCompat.getColor(c, R.color.text_primary));
            label.setTextSize(14);
            TextView value = new TextView(c);
            value.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
            value.setTextSize(13);
            value.setFontFeatureSettings("tnum");
            row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rp.topMargin = Ui.dpi(c, 10);
            b.sliders.addView(row, rp);
            Slider slider = new Slider(c, null, com.google.android.material.R.attr.sliderStyle);
            slider.setValueFrom(SLIDERS[i][0]);
            slider.setValueTo(SLIDERS[i][1]);
            slider.setStepSize(SLIDERS[i][2]);
            slider.setLabelBehavior(com.google.android.material.slider.LabelFormatter.LABEL_GONE);
            slider.setTrackActiveTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.accent)));
            slider.setTrackInactiveTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.stroke_strong)));
            slider.setThumbTintList(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.text_primary)));
            slider.setTickVisible(false);
            int index = i;
            slider.addOnChangeListener((s, v, fromUser) -> {
                value.setText(formatSlider(index, Math.round(v)));
                if (fromUser && !bindingAdvanced) commitSlider(index, Math.round(v));
            });
            b.sliders.addView(slider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            sliders.add(slider);
            sliderValues.add(value);
        }
    }

    private String formatSlider(int index, int v) {
        return index == 0 ? String.valueOf(v) : getString(R.string.ms_value, v);
    }

    private void commitSlider(int index, int v) {
        Types.Advanced a = settings.advanced();
        if (index == 0) a.ttsConcurrency = v;
        else if (index == 1) a.lineGapMs = v;
        else if (index == 2) a.speakerGapMs = v;
        else a.paragraphGapMs = v;
        settings.setAdvanced(a);
    }

    private void bindAdvanced() {
        Types.Advanced a = settings.advanced();
        int[] values = {a.ttsConcurrency, a.lineGapMs, a.speakerGapMs, a.paragraphGapMs};
        bindingAdvanced = true;
        for (int i = 0; i < sliders.size(); i++) {
            float v = Math.max(SLIDERS[i][0], Math.min(SLIDERS[i][1], values[i]));
            v = Math.round(v / SLIDERS[i][2]) * SLIDERS[i][2];
            if (sliders.get(i).getValue() != v) sliders.get(i).setValue(v);
            sliderValues.get(i).setText(formatSlider(i, Math.round(v)));
        }
        b.normalize.setChecked(a.normalize);
        bindingAdvanced = false;
    }

    private void test(ItemEngineBinding card, Types.ProviderMeta meta) {
        if (tests.containsKey(meta.id)) return;
        Types.ProviderConfig cfg = settings.config(meta.id);
        String model = cfg.model == null || cfg.model.isEmpty() ? meta.defaultModel : cfg.model;
        cfg.model = model;
        List<Types.VoiceProfile> voices = Catalog.get().staticVoices(meta.id, model);
        if (voices.isEmpty()) return;
        Types.VoiceProfile voice = voices.get(0);
        boolean sarvam = "sarvam-tts".equals(meta.id);
        String lang = sarvam ? "hi-IN" : "en-US";
        String text = getString(sarvam ? R.string.test_line_hi : R.string.test_line_en);
        Types.SynthesisStyle style = new Types.SynthesisStyle();
        style.emotion = "happy";
        style.instructions = "Speak warmly and clearly, like a friendly studio host.";
        card.test.setText("");
        card.test.setIcon(null);
        card.testing.setVisibility(View.VISIBLE);
        card.test.setEnabled(false);
        card.testResult.setVisibility(View.GONE);
        long started = SystemClock.elapsedRealtime();
        File dir = requireContext().getCacheDir();
        Cancel call = api.run(() -> {
            byte[] audio = api.speech(meta.id, cfg, voice.id, text, lang, style, new Cancel());
            File f = File.createTempFile("engine-test-", ".audio", dir);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(audio);
            }
            return f;
        }, new ApiClient.Callback<File>() {
            @Override
            public void onSuccess(File file) {
                tests.remove(meta.id);
                if (b == null) {
                    file.delete();
                    return;
                }
                long ms = SystemClock.elapsedRealtime() - started;
                resetTest(card);
                testResult(card, getString(R.string.test_ok, voice.name, ms), true);
                play(file);
            }

            @Override
            public void onError(ApiException error) {
                tests.remove(meta.id);
                if (b == null) return;
                resetTest(card);
                testResult(card, error.getMessage(), false);
            }
        });
        tests.put(meta.id, call);
    }

    private void resetTest(ItemEngineBinding card) {
        card.testing.setVisibility(View.GONE);
        card.test.setText(R.string.test);
        card.test.setIconResource(R.drawable.ic_bolt);
        refresh();
    }

    private void testResult(ItemEngineBinding card, String message, boolean ok) {
        card.testResult.setVisibility(View.VISIBLE);
        card.testResult.setText(message);
        card.testResult.setBackgroundResource(ok ? R.drawable.bg_banner_info : R.drawable.bg_banner_error);
        card.testResult.setTextColor(ContextCompat.getColor(requireContext(), ok ? R.color.accent : R.color.error));
    }

    private void play(File file) {
        releaseTestPlayer();
        MediaPlayer mp = new MediaPlayer();
        testPlayer = mp;
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setOnCompletionListener(done -> {
                releaseTestPlayer();
                file.delete();
            });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            releaseTestPlayer();
            file.delete();
        }
    }

    private void releaseTestPlayer() {
        if (testPlayer != null) {
            try {
                testPlayer.release();
            } catch (Exception ignored) {
            }
            testPlayer = null;
        }
    }

    @Override
    public void onSettingsChanged() {
        refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            refresh();
            ((MainActivity) requireActivity()).refreshServer();
        }
    }

    @Override
    public void onDestroyView() {
        settings.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        for (Cancel c : tests.values()) c.cancel();
        tests.clear();
        if (passwordCall != null) passwordCall.cancel();
        if (deleteCall != null) deleteCall.cancel();
        releaseTestPlayer();
        cards.clear();
        sliders.clear();
        sliderValues.clear();
        b = null;
        super.onDestroyView();
    }
}
