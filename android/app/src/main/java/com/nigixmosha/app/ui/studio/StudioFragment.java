package com.nigixmosha.app.ui.studio;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.data.SettingsStore;
import com.nigixmosha.app.databinding.FragmentStudioBinding;
import com.nigixmosha.app.studio.LibrarySync;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.main.MainActivity;

import java.util.EnumMap;
import java.util.Map;

public class StudioFragment extends Fragment implements StudioHost, StudioStore.Listener, StudioJobs.Listener,
        SettingsStore.Listener, MainActivity.Reselectable {
    private static final StudioStore.Step[] STEPS = StudioStore.Step.values();
    private static final int[] STEP_LABELS = {R.string.step_manuscript, R.string.step_cast, R.string.step_script, R.string.step_listen};
    private static final int[] STEP_ICONS = {R.drawable.ic_manuscript, R.drawable.ic_group, R.drawable.ic_script, R.drawable.ic_headphones};

    private FragmentStudioBinding b;
    private StudioStore store;
    private StudioJobs jobs;
    private SettingsStore settings;
    private final Map<StudioStore.Step, StepView> views = new EnumMap<>(StudioStore.Step.class);
    private final View[] chips = new View[STEPS.length];
    private final Handler handler = new Handler(Looper.getMainLooper());
    private StudioStore.Step current;
    private FilePicked pending;
    private boolean confirmingReset;
    private String lastPreviewError;
    private boolean askedNotifications;
    private int bottomInset;
    private boolean visible;
    private final Runnable resetConfirm = () -> {
        confirmingReset = false;
        bindReset();
    };
    private final Runnable renderNow = this::renderAll;

    private final ActivityResultLauncher<String[]> picker = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        FilePicked callback = pending;
        pending = null;
        if (uri != null && callback != null) callback.onPicked(uri);
    });
    private final ActivityResultLauncher<String> notifications = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        b = FragmentStudioBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        store = StudioStore.get(requireContext());
        jobs = StudioJobs.get(requireContext());
        settings = SettingsStore.get(requireContext());
        b.backdrop.setDrifting(false);
        buildStepper();
        b.reset.setOnClickListener(v -> {
            if (!confirmingReset) {
                confirmingReset = true;
                bindReset();
                Ui.haptic(v, false);
                handler.removeCallbacks(resetConfirm);
                handler.postDelayed(resetConfirm, 3000);
                return;
            }
            handler.removeCallbacks(resetConfirm);
            confirmingReset = false;
            jobs.cancelAll();
            store.reset();
            for (StepView sv : views.values()) sv.release();
            views.clear();
            b.stage.removeAllViews();
            current = null;
            bindReset();
            renderAll();
        });
        b.save.setOnClickListener(v -> {
            if (store.state().saveState == StudioStore.SaveState.ERROR) sync().saveProjectNow(null);
            else if (store.state().saveState == StudioStore.SaveState.SAVED) openLibrary();
        });
        ViewCompat.setOnApplyWindowInsetsListener(b.header, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), Ui.dpi(requireContext(), 12) + bars.top, v.getPaddingRight(), v.getPaddingBottom());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean bottomNav = requireActivity().findViewById(R.id.bottomNav) != null;
            int next = Math.max(ime.bottom > 0 ? Math.max(0, ime.bottom - navHeight()) : 0, bottomNav ? 0 : bars.bottom);
            if (next != bottomInset) {
                bottomInset = next;
                for (StepView sv : views.values()) sv.onInsets();
            }
            return insets;
        });
        store.addListener(this);
        jobs.addListener(this);
        settings.addListener(this);
        visible = !isHidden();
        renderAll();
    }

    private int navHeight() {
        View nav = requireActivity().findViewById(R.id.bottomNav);
        return nav != null ? nav.getHeight() : 0;
    }

    private void buildStepper() {
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (int i = 0; i < STEPS.length; i++) {
            if (i > 0) {
                View line = new View(requireContext());
                line.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.stroke_strong));
                ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(Ui.dpi(requireContext(), 18), Ui.dpi(requireContext(), 1));
                lp.setMarginStart(Ui.dpi(requireContext(), 4));
                lp.setMarginEnd(Ui.dpi(requireContext(), 4));
                b.stepper.addView(line, new android.widget.LinearLayout.LayoutParams(lp));
            }
            View chip = inflater.inflate(R.layout.view_step_chip, b.stepper, false);
            ((TextView) chip.findViewById(R.id.label)).setText(STEP_LABELS[i]);
            ((ImageView) chip.findViewById(R.id.icon)).setImageResource(STEP_ICONS[i]);
            StudioStore.Step step = STEPS[i];
            chip.setOnClickListener(v -> {
                if (step == StudioStore.Step.MANUSCRIPT || store.state().analysis != null) store.setStep(step);
            });
            b.stepper.addView(chip);
            chips[i] = chip;
        }
    }

    private void bindStepper() {
        StudioStore.State s = store.state();
        int active = s.step.ordinal();
        for (int i = 0; i < STEPS.length; i++) {
            View chip = chips[i];
            boolean enabled = STEPS[i] == StudioStore.Step.MANUSCRIPT || s.analysis != null;
            boolean on = i == active;
            boolean complete = (i < active && s.analysis != null) || (STEPS[i] == StudioStore.Step.LISTEN && s.output != null && !on);
            chip.setSelected(on);
            chip.setEnabled(enabled);
            chip.setAlpha(enabled ? 1f : 0.4f);
            TextView label = chip.findViewById(R.id.label);
            label.setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.bg : R.color.text_secondary));
            View dot = chip.findViewById(R.id.dot);
            ImageView icon = chip.findViewById(R.id.icon);
            dot.setBackgroundResource(on ? R.drawable.bg_step_dark : complete ? R.drawable.bg_step_done : R.drawable.bg_step_idle);
            icon.setImageResource(complete && !on ? R.drawable.ic_check : STEP_ICONS[i]);
            icon.setImageTintList(ColorStateList.valueOf(on ? 0xFFFFFFFF : ContextCompat.getColor(requireContext(),
                    complete ? R.color.on_accent : R.color.text_secondary)));
        }
        View activeChip = chips[active];
        b.stepperScroll.post(() -> {
            if (b == null) return;
            int target = activeChip.getLeft() - Ui.dpi(requireContext(), 24);
            b.stepperScroll.smoothScrollTo(Math.max(0, target), 0);
        });
    }

    private void bindHeader() {
        StudioStore.State s = store.state();
        String title = s.analysis != null && s.analysis.title != null && !s.analysis.title.isEmpty() ? s.analysis.title
                : !s.title.isEmpty() ? s.title : null;
        b.title.setText(title != null ? title : getString(R.string.new_chapter_placeholder));
        b.title.setTextColor(ContextCompat.getColor(requireContext(), title != null ? R.color.text_primary : R.color.text_tertiary));

        boolean db = settings.server().storage.db;
        if (!db || s.analysis == null || s.saveState == StudioStore.SaveState.IDLE) {
            b.save.setVisibility(View.GONE);
        } else {
            b.save.setVisibility(View.VISIBLE);
            int icon;
            int tint;
            String text;
            if (s.saveState == StudioStore.SaveState.SAVING) {
                text = getString(R.string.saving_to_history);
                icon = R.drawable.ic_cloud_upload;
                tint = R.color.text_tertiary;
            } else if (s.saveState == StudioStore.SaveState.SAVED) {
                text = getString(R.string.saved_to_history);
                icon = R.drawable.ic_cloud_done;
                tint = R.color.accent;
            } else {
                text = getString(R.string.not_saved_retry);
                icon = R.drawable.ic_cloud_off;
                tint = R.color.error;
            }
            b.save.setText(text);
            b.save.setTextColor(ContextCompat.getColor(requireContext(), s.saveState == StudioStore.SaveState.ERROR ? R.color.error : R.color.text_tertiary));
            b.save.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(b.save, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), tint)));
        }
        bindReset();
    }

    private void bindReset() {
        if (b == null) return;
        b.reset.setText(confirmingReset ? R.string.clear_everything : R.string.new_project);
        int color = ContextCompat.getColor(requireContext(), confirmingReset ? R.color.error : R.color.text_primary);
        b.reset.setTextColor(color);
        b.reset.setIconTint(ColorStateList.valueOf(color));
        b.reset.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), confirmingReset ? R.color.error_stroke : R.color.stroke_strong)));
    }

    private StepView viewFor(StudioStore.Step step) {
        StepView v = views.get(step);
        if (v != null) return v;
        switch (step) {
            case CAST:
                v = new CastStep(requireContext(), this);
                break;
            case SCRIPT:
                v = new ScriptStep(requireContext(), this);
                break;
            case LISTEN:
                v = new ListenStep(requireContext(), this);
                break;
            default:
                v = new ManuscriptStep(requireContext(), this);
        }
        v.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        views.put(step, v);
        return v;
    }

    private void renderAll() {
        if (b == null) return;
        StudioStore.State s = store.state();
        StudioStore.Step step = s.step;
        if (step != StudioStore.Step.MANUSCRIPT && s.analysis == null) step = StudioStore.Step.MANUSCRIPT;
        if (step != current) switchTo(step);
        bindHeader();
        bindStepper();
        StepView v = views.get(current);
        if (v != null) v.render();
        if (jobs.previewError != null && !jobs.previewError.equals(lastPreviewError)) {
            lastPreviewError = jobs.previewError;
            snackbar(jobs.previewError);
            jobs.clearPreviewError();
        }
        maybeAskNotifications();
    }

    private void switchTo(StudioStore.Step step) {
        StepView old = current != null ? views.get(current) : null;
        StepView next = viewFor(step);
        current = step;
        if (old != null) old.onHidden();
        if (next.getParent() == null) b.stage.addView(next);
        next.setVisibility(View.VISIBLE);
        next.onInsets();
        if (old != null && old != next) {
            if (Ui.motionEnabled()) {
                old.animate().cancel();
                old.animate().alpha(0f).translationY(-Ui.dp(requireContext(), 10)).setDuration(160).withEndAction(() -> {
                    old.setVisibility(View.GONE);
                    old.setAlpha(1f);
                    old.setTranslationY(0f);
                }).start();
                next.setAlpha(0f);
                next.setTranslationY(Ui.dp(requireContext(), 16));
                next.animate().alpha(1f).translationY(0f).setStartDelay(90).setDuration(340).setInterpolator(Ui.DECELERATE).start();
            } else {
                old.setVisibility(View.GONE);
            }
        }
        if (visible) next.onShown();
    }

    private void maybeAskNotifications() {
        if (askedNotifications || Build.VERSION.SDK_INT < 33 || !jobs.isBusy()) return;
        askedNotifications = true;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onStudioChanged() {
        schedule();
    }

    @Override
    public void onJobsChanged() {
        schedule();
    }

    @Override
    public void onSettingsChanged() {
        schedule();
    }

    private void schedule() {
        handler.removeCallbacks(renderNow);
        handler.post(renderNow);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        visible = !hidden;
        StepView v = current != null ? views.get(current) : null;
        if (v == null) return;
        if (hidden) v.onHidden();
        else {
            v.onShown();
            renderAll();
        }
    }

    @Override
    public void onReselected() {
        renderAll();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        for (StepView v : views.values()) {
            if (v instanceof CastStep) ((CastStep) v).onConfigChanged();
            v.onInsets();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        StepView v = current != null ? views.get(current) : null;
        if (v instanceof ListenStep && !requireActivity().isChangingConfigurations()) v.onHidden();
    }

    @Override
    public void onDestroyView() {
        store.removeListener(this);
        jobs.removeListener(this);
        settings.removeListener(this);
        handler.removeCallbacksAndMessages(null);
        for (StepView v : views.values()) v.release();
        views.clear();
        b = null;
        super.onDestroyView();
    }

    @Override
    public StudioStore store() {
        return store;
    }

    @Override
    public StudioJobs jobs() {
        return jobs;
    }

    @Override
    public SettingsStore settings() {
        return settings;
    }

    @Override
    public ApiClient api() {
        return ApiClient.get(requireContext());
    }

    @Override
    public LibrarySync sync() {
        return LibrarySync.get(requireContext());
    }

    @Override
    public void pickFile(FilePicked callback) {
        pending = callback;
        picker.launch(new String[]{"text/plain", "text/markdown", "text/html", "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream"});
    }

    @Override
    public void snackbar(String message) {
        if (b == null || message == null) return;
        Snackbar.make(b.root, message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void openProfile() {
        ((MainActivity) requireActivity()).openProfile();
    }

    @Override
    public void openLibrary() {
        ((MainActivity) requireActivity()).openLibrary();
    }

    @Override
    public void handleError(ApiException error) {
        ((MainActivity) requireActivity()).handleError(error);
    }

    @Override
    public int bottomInset() {
        return bottomInset;
    }
}
