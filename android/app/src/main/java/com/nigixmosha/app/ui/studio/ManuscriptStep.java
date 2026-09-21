package com.nigixmosha.app.ui.studio;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.databinding.ViewStepManuscriptBinding;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.Lang;
import com.nigixmosha.app.engine.Texts;
import com.nigixmosha.app.engine.model.Types;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;
import com.nigixmosha.app.ui.Avatars;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ManuscriptStep extends StepView {
    private static final String[] PHASES = {"reading", "scripting", "finishing", "casting"};
    private static final int[] PHASE_LABELS = {R.string.phase_reading, R.string.phase_scripting, R.string.phase_finishing, R.string.phase_casting};
    private static final int MAX_BYTES = 15 * 1024 * 1024;

    private final ViewStepManuscriptBinding b;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable voicePoll = this::pollVoices;
    private final Runnable detect = this::updateStats;
    private final List<String> engineIds = new ArrayList<>();
    private final List<String> languageCodes = new ArrayList<>();
    private final Set<String> shownFound = new HashSet<>();
    private final View[] phaseRows = new View[PHASES.length];
    private boolean binding;
    private String voiceState = "checking";
    private String uploadError;
    private Cancel voiceCall;
    private Cancel uploadCall;
    private String detected;
    private boolean shown;

    public ManuscriptStep(Context context, StudioHost host) {
        super(context, host);
        b = ViewStepManuscriptBinding.inflate(LayoutInflater.from(context), this, true);
        if (wide()) {
            b.columns.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams editor = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.6f);
            b.editorCard.setLayoutParams(editor);
            LinearLayout.LayoutParams side = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            side.setMarginStart(Ui.dpi(context, 16));
            b.side.setLayoutParams(side);
        }
        buildSamples();
        buildPhases();
        b.progressGlow.setBreathing(true);
        b.title.addTextChangedListener(watcher(value -> host.store().setDraft(value, null, null)));
        b.text.addTextChangedListener(watcher(value -> {
            host.store().setDraft(null, value, null);
            handler.removeCallbacks(detect);
            handler.postDelayed(detect, 250);
        }));
        b.clear.setOnClickListener(v -> {
            b.text.setText("");
            uploadError = null;
        });
        b.upload.setOnClickListener(v -> host.pickFile(this::onFile));
        b.direct.setOnClickListener(v -> {
            Ui.hideKeyboard(b.getRoot());
            host.jobs().startDirect();
        });
        b.backToCast.setOnClickListener(v -> host.store().setStep(StudioStore.Step.CAST));
        b.cancelDirect.setOnClickListener(v -> host.jobs().cancelDirect());
        b.voiceSettings.setOnClickListener(v -> host.openProfile());
        b.engine.setOnItemClickListener((parent, view, position, id) -> {
            if (!binding && position < engineIds.size()) host.settings().setActiveTTS(engineIds.get(position));
        });
        b.language.setOnItemClickListener((parent, view, position, id) -> {
            if (!binding && position < languageCodes.size()) host.store().setDraft(null, null, languageCodes.get(position));
        });
        StudioStore.State s = host.store().state();
        binding = true;
        b.title.setText(s.title);
        b.text.setText(s.text);
        binding = false;
        updateStats();
    }

    private interface OnText {
        void apply(String value);
    }

    private TextWatcher watcher(OnText action) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!binding) action.apply(s.toString());
            }
        };
    }

    private void buildSamples() {
        for (Types.Sample sample : Catalog.get().samples) {
            MaterialButton chip = (MaterialButton) LayoutInflater.from(getContext()).inflate(R.layout.view_sample_button, b.samples, false);
            chip.setText(sample.label);
            chip.setOnClickListener(v -> {
                if (host.jobs().directStatus == StudioJobs.Status.RUNNING) return;
                binding = true;
                b.title.setText(sample.title);
                b.text.setText(sample.text);
                binding = false;
                host.store().setDraft(sample.title, sample.text, "auto");
                updateStats();
                Ui.haptic(v, true);
            });
            b.samples.addView(chip);
        }
    }

    private void buildPhases() {
        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < PHASES.length; i++) {
            View row = inflater.inflate(R.layout.view_phase_row, b.phases, false);
            ((TextView) row.findViewById(R.id.phaseLabel)).setText(PHASE_LABELS[i]);
            b.phases.addView(row);
            phaseRows[i] = row;
        }
    }

    private void updateStats() {
        String text = host.store().state().text;
        int words = Texts.wordCount(text);
        int minutes = Math.max(1, Math.round(words / 150f));
        detected = text.trim().length() > 40 ? Lang.detectLanguage(text) : null;
        StringBuilder sb = new StringBuilder();
        sb.append(getResources().getQuantityString(R.plurals.words, words, Format.number(words)));
        sb.append("  ·  ").append(getContext().getString(R.string.chars_count, Format.number(text.length())));
        sb.append("  ·  ").append(getContext().getString(R.string.minutes_of_audio, minutes));
        if (detected != null) sb.append("  ·  ").append(getContext().getString(R.string.detected, Lang.languageName(detected)));
        b.stats.setText(sb);
        b.clear.setVisibility(text.isEmpty() || host.jobs().directStatus == StudioJobs.Status.RUNNING ? View.GONE : View.VISIBLE);
        bindLanguages();
    }

    private void bindEngines() {
        engineIds.clear();
        List<String> labels = new ArrayList<>();
        String active = host.settings().activeTTS();
        String activeLabel = null;
        for (Types.ProviderMeta p : Catalog.get().providers) {
            engineIds.add(p.id);
            String label = p.name + (p.isFree() ? getContext().getString(R.string.free_suffix) : "")
                    + (host.settings().isReady(p.id) ? "" : getContext().getString(R.string.needs_key_suffix));
            labels.add(label);
            if (p.id.equals(active)) activeLabel = label;
        }
        binding = true;
        b.engine.setSimpleItems(labels.toArray(new String[0]));
        b.engine.setText(activeLabel, false);
        binding = false;
    }

    private void bindLanguages() {
        languageCodes.clear();
        List<String> labels = new ArrayList<>();
        languageCodes.add("auto");
        labels.add(getContext().getString(R.string.auto_detect) + (detected != null ? " (" + Lang.languageName(detected) + ")" : ""));
        for (Types.Language l : Catalog.get().languages) {
            languageCodes.add(l.code);
            labels.add(l.nativeName.equals(l.name) ? l.name : l.nativeName + " — " + l.name);
        }
        String hint = host.store().state().languageHint;
        int index = Math.max(0, languageCodes.indexOf(hint));
        binding = true;
        b.language.setSimpleItems(labels.toArray(new String[0]));
        b.language.setText(labels.get(index), false);
        binding = false;
    }

    @Override
    public void render() {
        StudioStore.State s = host.store().state();
        StudioJobs jobs = host.jobs();
        boolean running = jobs.directStatus == StudioJobs.Status.RUNNING;
        if (!b.title.hasFocus() && !s.title.equals(b.title.getText().toString())) {
            binding = true;
            b.title.setText(s.title);
            binding = false;
        }
        if (!b.text.hasFocus() && !s.text.equals(b.text.getText().toString())) {
            binding = true;
            b.text.setText(s.text);
            binding = false;
            updateStats();
        }
        b.title.setEnabled(!running);
        b.text.setEnabled(!running);
        b.editorCard.setAlpha(running ? 0.6f : 1f);
        b.upload.setEnabled(!running && uploadCall == null);
        for (int i = 0; i < b.samples.getChildCount(); i++) b.samples.getChildAt(i).setEnabled(!running);

        boolean wasRunning = b.progressCard.getVisibility() == View.VISIBLE;
        if (running != wasRunning) swapCards(running);
        if (running) renderProgress(jobs);
        else shownFound.clear();

        boolean brain = host.settings().server().brain;
        badge(b.brainBadge, getContext().getString(brain ? R.string.ready : R.string.resting), brain ? R.color.accent : R.color.error);
        bindEngines();
        bindVoiceStatus();

        b.notices.removeAllViews();
        if (!brain) notice(R.string.brain_resting, false);
        if (s.text.length() > 80000) notice(R.string.too_long, false);
        String error = jobs.directError != null ? jobs.directError : uploadError;
        if (error != null) notice(error, true);

        b.direct.setEnabled(!s.text.trim().isEmpty());
        b.direct.setText(s.analysis != null ? R.string.redirect_action : R.string.direct_action);
        b.backToCast.setVisibility(s.analysis != null ? View.VISIBLE : View.GONE);
    }

    private void swapCards(boolean running) {
        View in = running ? b.progressCard : b.directorCard;
        View out = running ? b.directorCard : b.progressCard;
        out.setVisibility(View.GONE);
        in.setVisibility(View.VISIBLE);
        if (Ui.motionEnabled()) {
            in.setAlpha(0f);
            in.setTranslationY(Ui.dp(getContext(), 14));
            in.animate().alpha(1f).translationY(0f).setDuration(380).setInterpolator(Ui.DECELERATE).start();
        }
        if (running) b.scroll.post(() -> b.scroll.smoothScrollTo(0, b.side.getTop()));
    }

    private void renderProgress(StudioJobs jobs) {
        b.progressLabel.setText(jobs.directLabel != null ? jobs.directLabel : getContext().getString(R.string.warming_up));
        b.progress.setProgressCompat((int) Math.max(40, jobs.directValue * 1000), true);
        int phaseIndex = -1;
        for (int i = 0; i < PHASES.length; i++) if (PHASES[i].equals(jobs.directPhase)) phaseIndex = i;
        for (int i = 0; i < PHASES.length; i++) {
            View row = phaseRows[i];
            boolean done = i < phaseIndex;
            boolean active = i == phaseIndex;
            FrameLayoutHelper.state(row, done, active);
        }
        List<Types.CastCharacter> found = jobs.directCharacters;
        b.foundBlock.setVisibility(found.isEmpty() ? View.GONE : View.VISIBLE);
        if (found.isEmpty()) {
            b.found.removeAllViews();
            shownFound.clear();
            return;
        }
        int delay = 0;
        for (Types.CastCharacter c : found) {
            if (shownFound.contains(c.id + c.name)) continue;
            shownFound.add(c.id + c.name);
            Chip chip = new Chip(getContext());
            chip.setText(c.name);
            chip.setTextColor(0xFFFFFFFF);
            chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(0x1AFFFFFF));
            chip.setChipStrokeWidth(0);
            chip.setClickable(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipIcon(AvatarDrawable.of(getContext(), Format.initial(c.name), Format.color(c.color, c.name)));
            chip.setChipIconSize(Ui.dp(getContext(), 22));
            b.found.addView(chip);
            if (Ui.motionEnabled()) {
                chip.setAlpha(0f);
                chip.setScaleX(0.8f);
                chip.setScaleY(0.8f);
                chip.setTranslationY(Ui.dp(getContext(), 6));
                chip.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setStartDelay(delay)
                        .setDuration(360).setInterpolator(new android.view.animation.OvershootInterpolator(1.4f)).start();
                delay += 80;
            }
        }
    }

    private void badge(TextView view, String text, int colorRes) {
        int color = ContextCompat.getColor(getContext(), colorRes);
        view.setText("● " + text);
        view.setTextColor(color);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Ui.dp(getContext(), 999));
        bg.setColor((color & 0x00FFFFFF) | 0x1F000000);
        view.setBackground(bg);
    }

    private void bindVoiceStatus() {
        boolean nigix = "nigix".equals(host.settings().activeTTS());
        b.voiceBadge.setVisibility(nigix ? View.VISIBLE : View.GONE);
        if (nigix) {
            switch (voiceState) {
                case "ready":
                    badge(b.voiceBadge, getContext().getString(R.string.ready), R.color.accent);
                    break;
                case "waking":
                    badge(b.voiceBadge, getContext().getString(R.string.warming), R.color.tide);
                    break;
                case "off":
                    badge(b.voiceBadge, getContext().getString(R.string.backup_voices), R.color.error);
                    break;
                default:
                    badge(b.voiceBadge, getContext().getString(R.string.checking), R.color.text_tertiary);
            }
        }
        b.voiceNote.setText(nigix && "waking".equals(voiceState) ? R.string.voices_waking_note : R.string.voices_note);
    }

    private void notice(int res, boolean error) {
        notice(getContext().getString(res), error);
    }

    private void notice(String message, boolean error) {
        TextView v = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.view_notice, b.notices, false);
        v.setText(message);
        v.setBackgroundResource(error ? R.drawable.bg_banner_error : R.drawable.bg_banner_info);
        v.setTextColor(ContextCompat.getColor(getContext(), error ? R.color.error : R.color.text_secondary));
        if (error) v.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
        b.notices.addView(v);
    }

    private void pollVoices() {
        handler.removeCallbacks(voicePoll);
        if (!shown || !"nigix".equals(host.settings().activeTTS())) return;
        if (voiceCall != null) voiceCall.cancel();
        ApiClient api = host.api();
        voiceCall = api.run(() -> api.voiceStatus(new Cancel()), new ApiClient.Callback<String>() {
            @Override
            public void onSuccess(String value) {
                voiceCall = null;
                voiceState = value == null ? "off" : value;
                bindVoiceStatus();
            }

            @Override
            public void onError(ApiException error) {
                voiceCall = null;
                voiceState = "waking";
                bindVoiceStatus();
            }
        });
        handler.postDelayed(voicePoll, 30000);
    }

    private void onFile(Uri uri) {
        uploadError = null;
        Context c = getContext();
        String name = "chapter.txt";
        String mime = c.getContentResolver().getType(uri);
        try (Cursor cursor = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
        } catch (Exception ignored) {
        }
        String fileName = name;
        b.uploading.setVisibility(View.VISIBLE);
        b.upload.setText("");
        b.upload.setIcon(null);
        ApiClient api = host.api();
        uploadCall = api.run(() -> {
            byte[] bytes;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new java.io.IOException(c.getString(R.string.file_unreadable));
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new ApiException(ApiException.Kind.HTTP, 413, null, c.getString(R.string.file_too_large));
                }
                bytes = out.toByteArray();
            }
            return api.extract(bytes, fileName, mime, new Cancel());
        }, new ApiClient.Callback<ApiClient.Extracted>() {
            @Override
            public void onSuccess(ApiClient.Extracted value) {
                uploadCall = null;
                resetUpload();
                StudioStore.State s = host.store().state();
                String title = s.title.isEmpty() ? value.title : s.title;
                binding = true;
                b.title.setText(title);
                b.text.setText(value.text);
                binding = false;
                host.store().setDraft(title, value.text, null);
                updateStats();
                Ui.haptic(b.upload, true);
            }

            @Override
            public void onError(ApiException error) {
                uploadCall = null;
                resetUpload();
                if (error.isUnauthorized()) {
                    host.handleError(error);
                    return;
                }
                uploadError = error.getMessage();
                render();
            }
        });
    }

    private void resetUpload() {
        b.uploading.setVisibility(View.GONE);
        b.upload.setText(R.string.upload_file);
        b.upload.setIconResource(R.drawable.ic_upload);
        render();
    }

    @Override
    public void onShown() {
        shown = true;
        voiceState = "checking";
        pollVoices();
    }

    @Override
    public void onHidden() {
        shown = false;
        handler.removeCallbacks(voicePoll);
        if (voiceCall != null) voiceCall.cancel();
    }

    @Override
    public void onInsets() {
        b.columns.setPadding(b.columns.getPaddingLeft(), b.columns.getPaddingTop(), b.columns.getPaddingRight(),
                Ui.dpi(getContext(), 32) + host.bottomInset());
    }

    @Override
    public void release() {
        onHidden();
        handler.removeCallbacksAndMessages(null);
        if (uploadCall != null) uploadCall.cancel();
    }

    static final class FrameLayoutHelper {
        private FrameLayoutHelper() {
        }

        static void state(View row, boolean done, boolean active) {
            View dot = row.findViewById(R.id.phaseDot);
            ImageView check = row.findViewById(R.id.phaseCheck);
            CircularProgressIndicator spin = row.findViewById(R.id.phaseSpin);
            TextView label = row.findViewById(R.id.phaseLabel);
            dot.setBackgroundResource(done ? R.drawable.bg_step_done : R.drawable.bg_ring);
            dot.setAlpha(done || active ? 1f : 0.5f);
            check.setVisibility(done ? View.VISIBLE : View.GONE);
            spin.setVisibility(active ? View.VISIBLE : View.GONE);
            label.setAlpha(done || active ? 1f : 0.4f);
        }
    }
}
