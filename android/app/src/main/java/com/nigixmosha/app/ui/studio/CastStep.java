package com.nigixmosha.app.ui.studio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.chip.Chip;
import com.google.android.material.slider.Slider;
import com.nigixmosha.app.R;
import com.nigixmosha.app.data.ApiClient;
import com.nigixmosha.app.data.ApiException;
import com.nigixmosha.app.databinding.ItemCastEngineBinding;
import com.nigixmosha.app.databinding.ItemCastOverviewBinding;
import com.nigixmosha.app.databinding.ItemCastWaitingBinding;
import com.nigixmosha.app.databinding.ItemSpeakerBinding;
import com.nigixmosha.app.databinding.ViewStepCastBinding;
import com.nigixmosha.app.engine.Cancel;
import com.nigixmosha.app.engine.Casting;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.Lang;
import com.nigixmosha.app.engine.model.Types;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;
import com.nigixmosha.app.ui.Avatars;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;
import com.nigixmosha.app.ui.library.GridSpacing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CastStep extends StepView {
    private static final Map<String, String> SAMPLE_LINES = new HashMap<>();

    static {
        SAMPLE_LINES.put("en", "Every story begins with a single voice. This one is mine.");
        SAMPLE_LINES.put("hi", "हर कहानी एक आवाज़ से शुरू होती है। यह मेरी आवाज़ है।");
        SAMPLE_LINES.put("bn", "প্রতিটি গল্প একটি কণ্ঠ দিয়ে শুরু হয়। এটি আমার কণ্ঠ।");
        SAMPLE_LINES.put("ta", "ஒவ்வொரு கதையும் ஒரு குரலில் தொடங்குகிறது. இது என் குரல்.");
        SAMPLE_LINES.put("es", "Toda historia empieza con una sola voz. Esta es la mía.");
        SAMPLE_LINES.put("fr", "Chaque histoire commence par une seule voix. Voici la mienne.");
    }

    private final ViewStepCastBinding b;
    private final Adapter adapter = new Adapter();
    private final GridLayoutManager grid;
    private List<Types.VoiceProfile> voices = new ArrayList<>();
    private boolean live;
    private boolean loadingVoices;
    private String voicesError;
    private String voicesKey;
    private Cancel voiceCall;
    private boolean shown;

    private Types.ProviderMeta meta;
    private String model;
    private List<Casting.Candidate> pool = new ArrayList<>();
    private boolean pitchOK;
    private boolean castReady;

    public CastStep(Context context, StudioHost host) {
        super(context, host);
        b = ViewStepCastBinding.inflate(LayoutInflater.from(context), this, true);
        grid = new GridLayoutManager(context, spans());
        grid.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return adapter.getItemViewType(position) == Adapter.SPEAKER ? 1 : grid.getSpanCount();
            }
        });
        b.list.setLayoutManager(grid);
        adapter.setHasStableIds(true);
        b.list.setAdapter(adapter);
        b.list.addItemDecoration(new GridSpacing(Ui.dpi(context, 12)));
        if (b.list.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) b.list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        b.bar.barSecondary.setText(R.string.review_script);
        b.bar.barSecondary.setOnClickListener(v -> host.store().setStep(StudioStore.Step.SCRIPT));
        b.bar.barPrimary.setOnClickListener(v -> host.jobs().startProduce());
    }

    private void ensureVoices(String provider, String model, String apiKey) {
        List<Types.VoiceProfile> fallback = Catalog.get().staticVoices(provider, model);
        StudioJobs jobs = host.jobs();
        if (!jobs.wantsLiveVoices(provider, apiKey)) {
            voices = fallback;
            live = false;
            loadingVoices = false;
            voicesError = null;
            voicesKey = null;
            return;
        }
        String key = StudioJobs.liveKey(provider, apiKey);
        List<Types.VoiceProfile> cached = jobs.cachedLiveVoices(provider, apiKey);
        if (cached != null && !cached.isEmpty()) {
            voices = cached;
            live = true;
            loadingVoices = false;
            voicesKey = key;
            return;
        }
        if (key.equals(voicesKey)) return;
        voicesKey = key;
        voices = fallback;
        live = false;
        loadingVoices = true;
        voicesError = null;
        if (voiceCall != null) voiceCall.cancel();
        ApiClient api = host.api();
        voiceCall = api.run(() -> jobs.loadLiveVoices(provider, apiKey, new Cancel()), new ApiClient.Callback<List<Types.VoiceProfile>>() {
            @Override
            public void onSuccess(List<Types.VoiceProfile> value) {
                voiceCall = null;
                loadingVoices = false;
                if (!key.equals(voicesKey)) return;
                if (value != null && !value.isEmpty()) {
                    voices = value;
                    live = true;
                }
                render();
            }

            @Override
            public void onError(ApiException error) {
                voiceCall = null;
                loadingVoices = false;
                if (!key.equals(voicesKey)) return;
                voicesError = error.getMessage();
                render();
            }
        });
    }

    @Override
    public void render() {
        StudioStore.State s = host.store().state();
        if (s.analysis == null) return;
        StudioJobs jobs = host.jobs();
        String active = host.settings().activeTTS();
        meta = Catalog.get().provider(active);
        Types.ProviderConfig cfg = host.settings().config(active);
        model = cfg.model == null || cfg.model.isEmpty() ? meta.defaultModel : cfg.model;
        ensureVoices(active, model, cfg.apiKey);
        pool = Casting.candidateVoices(voices, meta, s.analysis.language);
        pitchOK = Casting.supportsPitch(active, model);
        boolean casting = jobs.castStatus == StudioJobs.Status.RUNNING;
        castReady = s.castFor != null && active.equals(s.castFor.provider) && model.equals(s.castFor.model) && !casting;

        if (shown && jobs.castStatus == StudioJobs.Status.IDLE && jobs.directStatus != StudioJobs.Status.RUNNING
                && (s.castFor == null || !active.equals(s.castFor.provider) || !model.equals(s.castFor.model))) {
            jobs.startCast();
            return;
        }

        adapter.refresh(s);
        bindBar(s);
    }

    private void bindBar(StudioStore.State s) {
        List<Avatars.Person> people = new ArrayList<>();
        for (Types.CastCharacter c : s.analysis.characters) people.add(Avatars.person(c.name, c.color));
        Avatars.stack(b.bar.barAvatars, people, 5, 26, ContextCompat.getColor(getContext(), R.color.surface_high),
                Avatars.person(getContext().getString(R.string.narrator), Catalog.get().narratorColor));
        b.bar.barText.setText(getContext().getString(R.string.voices_cast_for, s.analysis.characters.size() + 1,
                getResources().getQuantityString(R.plurals.lines, s.segments.size(), s.segments.size())));
        boolean ready = host.settings().isReady(host.settings().activeTTS());
        b.bar.barPrimary.setEnabled(castReady && ready);
    }

    @Override
    public void onShown() {
        shown = true;
        render();
    }

    @Override
    public void onHidden() {
        shown = false;
    }

    @Override
    public void onInsets() {
        b.bar.getRoot().post(() -> {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) b.bar.getRoot().getLayoutParams();
            lp.bottomMargin = Ui.dpi(getContext(), 12) + host.bottomInset();
            b.bar.getRoot().setLayoutParams(lp);
            int gutter = getResources().getDimensionPixelSize(R.dimen.screen_gutter) - Ui.dpi(getContext(), 6);
            b.list.setPadding(gutter, Ui.dpi(getContext(), 10), gutter, b.bar.getRoot().getHeight() + lp.bottomMargin + Ui.dpi(getContext(), 16));
        });
    }

    @Override
    public void release() {
        if (voiceCall != null) voiceCall.cancel();
    }

    public void onConfigChanged() {
        grid.setSpanCount(spans());
    }

    private Types.Segment sampleFor(String speaker) {
        StudioStore.State s = host.store().state();
        Types.Segment first = null;
        for (Types.Segment seg : s.segments) {
            if (!seg.speaker.equals(speaker)) continue;
            if (first == null) first = seg;
            if (seg.text.length() > 25 && seg.text.length() < 240) return seg;
        }
        if (first != null) return first;
        Types.Segment sample = new Types.Segment();
        sample.id = "sample-" + speaker;
        sample.speaker = speaker;
        sample.emotion = "neutral";
        String line = SAMPLE_LINES.get(Lang.prefix(s.analysis.language));
        sample.text = line != null ? line : SAMPLE_LINES.get("en");
        return sample;
    }

    private static android.graphics.drawable.Drawable spinner(Context c) {
        com.google.android.material.progressindicator.CircularProgressIndicatorSpec spec =
                new com.google.android.material.progressindicator.CircularProgressIndicatorSpec(c, null, 0,
                        com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
        spec.indicatorColors = new int[]{ContextCompat.getColor(c, R.color.accent)};
        return com.google.android.material.progressindicator.IndeterminateDrawable.createCircularDrawable(c, spec);
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        static final int OVERVIEW = 1;
        static final int ENGINE = 2;
        static final int WAITING = 3;
        static final int SPEAKER = 4;

        private final List<String> speakers = new ArrayList<>();
        private StudioStore.State state;

        void refresh(StudioStore.State s) {
            state = s;
            speakers.clear();
            if (castReady) {
                speakers.add(Types.NARRATOR_ID);
                for (Types.CastCharacter c : s.analysis.characters) speakers.add(c.id);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            if (state == null) return 0;
            return 2 + (castReady ? speakers.size() : 1);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) return OVERVIEW;
            if (position == 1) return ENGINE;
            return castReady ? SPEAKER : WAITING;
        }

        @Override
        public long getItemId(int position) {
            int type = getItemViewType(position);
            if (type != SPEAKER) return type;
            return 100L + speakers.get(position - 2).hashCode();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == OVERVIEW) return new OverviewHolder(ItemCastOverviewBinding.inflate(inf, parent, false));
            if (viewType == ENGINE) return new EngineHolder(ItemCastEngineBinding.inflate(inf, parent, false));
            if (viewType == WAITING) return new WaitingHolder(ItemCastWaitingBinding.inflate(inf, parent, false));
            return new SpeakerHolder(ItemSpeakerBinding.inflate(inf, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof OverviewHolder) ((OverviewHolder) holder).bind(state);
            else if (holder instanceof EngineHolder) ((EngineHolder) holder).bind(state);
            else if (holder instanceof WaitingHolder) ((WaitingHolder) holder).bind();
            else if (holder instanceof SpeakerHolder) ((SpeakerHolder) holder).bind(state, speakers.get(position - 2), position - 2);
        }
    }

    final class OverviewHolder extends RecyclerView.ViewHolder {
        private final ItemCastOverviewBinding v;
        private boolean binding;

        OverviewHolder(ItemCastOverviewBinding v) {
            super(v.getRoot());
            this.v = v;
            v.title.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding) host.store().updateTitle(text);
            }));
        }

        void bind(StudioStore.State s) {
            Context c = getContext();
            if (!v.title.hasFocus() && !v.title.getText().toString().equals(s.analysis.title)) {
                binding = true;
                v.title.setText(s.analysis.title);
                binding = false;
            }
            v.badges.removeAllViews();
            chip(Lang.languageName(s.analysis.language), R.color.violet);
            for (String l : s.analysis.mixedLanguages) chip("+ " + Lang.languageName(l), R.color.tide);
            chip(c.getString("first".equals(s.analysis.pov) ? R.string.first_person : R.string.third_person), 0);
            chip(c.getResources().getQuantityString(R.plurals.characters, s.analysis.characters.size(), s.analysis.characters.size()), 0);
            chip(c.getResources().getQuantityString(R.plurals.lines, s.segments.size(), s.segments.size()), 0);
            boolean hasSummary = s.analysis.summary != null && !s.analysis.summary.isEmpty();
            v.summary.setVisibility(hasSummary ? View.VISIBLE : View.GONE);
            v.summary.setText(s.analysis.summary);
            v.warnings.removeAllViews();
            for (String w : s.warnings) {
                TextView t = (TextView) LayoutInflater.from(c).inflate(R.layout.view_notice, v.warnings, false);
                t.setText(w);
                t.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
                t.setBackground(null);
                t.setPadding(0, Ui.dpi(c, 6), 0, 0);
                v.warnings.addView(t);
            }
        }

        private void chip(String text, int colorRes) {
            Chip chip = new Chip(getContext());
            chip.setText(text);
            chip.setTextSize(12);
            chip.setClickable(false);
            chip.setEnsureMinTouchTargetSize(false);
            chip.setChipMinHeight(Ui.dp(getContext(), 28));
            chip.setChipStrokeWidth(0);
            if (colorRes != 0) {
                int color = ContextCompat.getColor(getContext(), colorRes);
                chip.setTextColor(color);
                chip.setChipBackgroundColor(ColorStateList.valueOf((color & 0x00FFFFFF) | 0x26000000));
            } else {
                chip.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
                chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.surface_high)));
            }
            v.badges.addView(chip);
        }
    }

    final class EngineHolder extends RecyclerView.ViewHolder {
        private final ItemCastEngineBinding v;
        private final List<String> engineIds = new ArrayList<>();
        private boolean binding;

        EngineHolder(ItemCastEngineBinding v) {
            super(v.getRoot());
            this.v = v;
            v.engine.setOnItemClickListener((p, view, pos, id) -> {
                if (!binding && pos < engineIds.size()) host.settings().setActiveTTS(engineIds.get(pos));
            });
            v.model.setOnItemClickListener((p, view, pos, id) -> {
                if (binding || meta == null) return;
                host.settings().setModel(meta.id, meta.models.get(pos));
            });
            v.recast.setOnClickListener(x -> host.jobs().startCast());
        }

        void bind(StudioStore.State s) {
            Context c = getContext();
            StudioJobs jobs = host.jobs();
            engineIds.clear();
            List<String> labels = new ArrayList<>();
            String activeLabel = null;
            for (Types.ProviderMeta p : Catalog.get().providers) {
                engineIds.add(p.id);
                String label = p.name + (p.isFree() ? c.getString(R.string.free_suffix) : "")
                        + (host.settings().isReady(p.id) ? "" : c.getString(R.string.needs_key_short));
                labels.add(label);
                if (p.id.equals(meta.id)) activeLabel = label;
            }
            binding = true;
            v.engine.setSimpleItems(labels.toArray(new String[0]));
            v.engine.setText(activeLabel, false);
            v.modelLayout.setVisibility(meta.models.size() > 1 ? View.VISIBLE : View.GONE);
            v.model.setSimpleItems(meta.models.toArray(new String[0]));
            v.model.setText(model, false);
            binding = false;

            boolean casting = jobs.castStatus == StudioJobs.Status.RUNNING;
            v.recast.setEnabled(!casting);
            v.recasting.setVisibility(casting ? View.VISIBLE : View.GONE);
            v.recast.setText(casting ? "" : c.getString(R.string.recast));
            if (casting) v.recast.setIcon(null);
            else v.recast.setIconResource(R.drawable.ic_refresh);

            v.voicesLoading.setVisibility(loadingVoices ? View.VISIBLE : View.GONE);
            v.liveDot.setVisibility(loadingVoices ? View.GONE : View.VISIBLE);
            v.liveDot.setAlpha(live ? 1f : 0.3f);
            v.voicesInfo.setText(loadingVoices ? c.getString(R.string.loading_voices)
                    : c.getString(live ? R.string.voices_live : R.string.voices_builtin, pool.size(), Lang.languageName(s.analysis.language)));

            v.castBy.setVisibility(castReady ? View.VISIBLE : View.GONE);
            v.castBy.setText("ai".equals(s.castBy) ? R.string.cast_by_ai : R.string.cast_by_rules);

            v.engineNotices.removeAllViews();
            if (jobs.castNote != null) plain(jobs.castNote);
            if (voicesError != null) plain(c.getString(R.string.live_voices_error, voicesError));
            if (!host.settings().isReady(meta.id)) {
                TextView t = warn(c.getString(R.string.needs_key_warning, meta.name));
                t.setOnClickListener(x -> host.openProfile());
            }
            boolean unsupported = !meta.anyLanguage && meta.languages != null && !meta.languages.contains(Lang.prefix(s.analysis.language));
            if (unsupported) warn(c.getString(R.string.unsupported_language, meta.name, Lang.languageName(s.analysis.language)));
        }

        private void plain(String text) {
            TextView t = new TextView(getContext());
            t.setText(text);
            t.setTextSize(12);
            t.setLineSpacing(0, 1.2f);
            t.setTextColor(ContextCompat.getColor(getContext(), R.color.text_tertiary));
            t.setPadding(0, Ui.dpi(getContext(), 8), 0, 0);
            v.engineNotices.addView(t);
        }

        private TextView warn(String text) {
            TextView t = (TextView) LayoutInflater.from(getContext()).inflate(R.layout.view_notice, v.engineNotices, false);
            t.setText(text);
            t.setBackgroundResource(R.drawable.bg_banner_info);
            t.setTextColor(ContextCompat.getColor(getContext(), R.color.text_secondary));
            v.engineNotices.addView(t);
            return t;
        }
    }

    final class WaitingHolder extends RecyclerView.ViewHolder {
        private final ItemCastWaitingBinding v;

        WaitingHolder(ItemCastWaitingBinding v) {
            super(v.getRoot());
            this.v = v;
        }

        void bind() {
            v.waitingText.setText(getContext().getString(R.string.choosing_voices, meta.name));
        }
    }

    final class SpeakerHolder extends RecyclerView.ViewHolder {
        private final ItemSpeakerBinding v;
        private final VoiceAdapter voiceAdapter;
        private String speaker;
        private boolean binding;
        private boolean tracking;
        private int animatedFor = -1;

        SpeakerHolder(ItemSpeakerBinding v) {
            super(v.getRoot());
            this.v = v;
            voiceAdapter = new VoiceAdapter(getContext());
            v.voice.setAdapter(voiceAdapter);
            v.voice.setOnItemClickListener((p, view, pos, id) -> {
                VoiceAdapter.Row row = voiceAdapter.getItem(pos);
                if (!binding && row.header == null) host.store().updateCast(speaker, row.id(), null, null);
            });
            v.name.addTextChangedListener(new SimpleWatcher(text -> {
                if (!binding && !Types.NARRATOR_ID.equals(speaker) && !text.trim().isEmpty()) {
                    host.store().updateCharacter(speaker, text, null, null);
                }
            }));
            v.gender.setSimpleItems(caps(Types.GENDERS));
            v.age.setSimpleItems(caps(Types.AGES));
            v.gender.setOnItemClickListener((p, view, pos, id) -> {
                if (!binding) host.store().updateCharacter(speaker, null, Types.GENDERS.get(pos), null);
            });
            v.age.setOnItemClickListener((p, view, pos, id) -> {
                if (!binding) host.store().updateCharacter(speaker, null, null, Types.AGES.get(pos));
            });
            v.share.setOnCheckedChangeListener((btn, checked) -> {
                if (!binding) host.store().setShareNarrator(checked);
            });
            Slider.OnSliderTouchListener touch = new Slider.OnSliderTouchListener() {
                @Override
                public void onStartTrackingTouch(@NonNull Slider slider) {
                    tracking = true;
                }

                @Override
                public void onStopTrackingTouch(@NonNull Slider slider) {
                    tracking = false;
                }
            };
            v.pitch.addOnSliderTouchListener(touch);
            v.rate.addOnSliderTouchListener(touch);
            v.pitch.addOnChangeListener((sl, value, fromUser) -> {
                v.pitchValue.setText(signed(value));
                if (fromUser && !binding) host.store().updateCast(speaker, null, (double) value, null);
            });
            v.rate.addOnChangeListener((sl, value, fromUser) -> {
                v.rateValue.setText(signed(value));
                if (fromUser && !binding) host.store().updateCast(speaker, null, null, (double) value);
            });
            v.hear.setOnClickListener(x -> {
                StudioJobs jobs = host.jobs();
                String key = "cast:" + speaker;
                if (key.equals(jobs.previewKey)) jobs.stopPreview();
                else jobs.preview(key, sampleFor(speaker));
            });
        }

        private String[] caps(List<String> values) {
            String[] out = new String[values.size()];
            for (int i = 0; i < out.length; i++) out[i] = cap(values.get(i));
            return out;
        }

        private String signed(float value) {
            int n = Math.round(value);
            return (n > 0 ? "+" : "") + n + "%";
        }

        void bind(StudioStore.State s, String speaker, int index) {
            this.speaker = speaker;
            Context c = getContext();
            binding = true;
            Types.CastCharacter character = Types.NARRATOR_ID.equals(speaker) ? null : s.analysis.character(speaker);
            String colorHex = character != null ? character.color : Catalog.get().narratorColor;
            int color = Format.color(colorHex, speaker);
            String name = character != null ? character.name : c.getString(R.string.narrator);
            v.colorBar.setBackgroundColor(color);
            Avatars.style(v.avatar, Format.initial(name), color, 44, 0);
            v.name.setEnabled(character != null);
            if (!v.name.hasFocus() && !v.name.getText().toString().equals(name)) v.name.setText(name);
            if (character != null) {
                v.sub.setText(cap(character.role) + " · " + c.getResources().getQuantityString(R.plurals.lines, character.lineCount, character.lineCount));
            } else {
                v.sub.setText(s.analysis.narrator.tone);
            }

            v.traitsRow.setVisibility(character != null ? View.VISIBLE : View.GONE);
            v.personality.setVisibility(character != null && !character.personality.isEmpty() ? View.VISIBLE : View.GONE);
            v.personality.removeAllViews();
            String styleText = "";
            if (character != null) {
                v.gender.setText(cap(character.gender), false);
                v.age.setText(cap(character.age), false);
                for (String t : character.personality) {
                    Chip chip = new Chip(c);
                    chip.setText(t);
                    chip.setTextSize(11);
                    chip.setClickable(false);
                    chip.setEnsureMinTouchTargetSize(false);
                    chip.setChipMinHeight(Ui.dp(c, 24));
                    chip.setChipStrokeWidth(0);
                    chip.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
                    chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(c, R.color.surface_high)));
                    v.personality.addView(chip);
                }
                String st = character.speakingStyle == null ? "" : character.speakingStyle;
                String vd = character.voiceDescription == null ? "" : character.voiceDescription;
                styleText = st + (!st.isEmpty() && !vd.isEmpty() ? " — " : "") + vd;
            }
            v.style.setVisibility(styleText.isEmpty() ? View.GONE : View.VISIBLE);
            v.style.setText(styleText);

            Types.CastCharacter link = character == null && s.analysis.narrator.characterId != null
                    ? s.analysis.character(s.analysis.narrator.characterId) : null;
            boolean linked = character == null && s.shareNarrator && link != null;
            v.share.setVisibility(link != null ? View.VISIBLE : View.GONE);
            if (link != null) {
                v.share.setText(c.getString(R.string.share_narrator, link.name));
                v.share.setChecked(s.shareNarrator);
            }

            Types.CastEntry entry = s.cast.get(speaker);
            String header = c.getString(R.string.native_voices, Lang.languageName(s.analysis.language));
            voiceAdapter.set(pool, entry != null ? entry.voiceId : null, header);
            v.voiceLayout.setEnabled(!linked);
            v.voice.setEnabled(!linked);
            v.voice.setText(entry != null ? voiceAdapter.labelFor(entry.voiceId) : "", false);

            String reason = s.castReasons.get(speaker);
            v.reason.setVisibility(reason != null && !linked ? View.VISIBLE : View.GONE);
            v.reason.setText(reason);

            v.sliders.setVisibility(!linked && entry != null ? View.VISIBLE : View.GONE);
            v.pitchHint.setVisibility(!linked && entry != null && !pitchOK ? View.VISIBLE : View.GONE);
            if (entry != null && !tracking) {
                float pitch = (float) Math.max(-30, Math.min(30, Math.round(entry.pitch)));
                float rate = (float) Math.max(-35, Math.min(35, Math.round(entry.rate)));
                if (v.pitch.getValue() != pitch) v.pitch.setValue(pitch);
                if (v.rate.getValue() != rate) v.rate.setValue(rate);
                v.pitchValue.setText(signed(pitch));
                v.rateValue.setText(signed(rate));
            }
            v.pitch.setEnabled(pitchOK);
            v.pitch.setAlpha(pitchOK ? 1f : 0.4f);

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(color);
            v.footDot.setBackground(dot);
            String voiceName = "—";
            if (entry != null) {
                for (Casting.Candidate cand : pool) if (cand.voice.id.equals(entry.voiceId)) voiceName = cand.voice.name;
            }
            v.footText.setText(linked ? c.getString(R.string.shares_voice, link.name) : voiceName);

            StudioJobs jobs = host.jobs();
            String key = "cast:" + speaker;
            boolean mine = key.equals(jobs.previewKey);
            boolean loading = mine && "loading".equals(jobs.previewState);
            v.hearLoading.setVisibility(View.GONE);
            v.hear.setText(loading ? c.getString(R.string.casting_preview) : mine ? c.getString(R.string.stop) : c.getString(R.string.hear_voice));
            if (loading) v.hear.setIcon(spinner(c));
            else v.hear.setIconResource(mine ? R.drawable.ic_stop : R.drawable.ic_play);
            binding = false;

            if (animatedFor != index && Ui.motionEnabled()) {
                animatedFor = index;
                View root = itemView;
                root.setAlpha(0f);
                root.setTranslationY(Ui.dp(c, 18));
                root.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(index, 8) * 50L).setDuration(450)
                        .setInterpolator(Ui.DECELERATE).start();
            }
        }
    }

    static final class SimpleWatcher implements TextWatcher {
        interface OnText {
            void apply(String text);
        }

        private final OnText action;

        SimpleWatcher(OnText action) {
            this.action = action;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            action.apply(s.toString());
        }
    }
}
