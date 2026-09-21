package com.nigixmosha.app.ui.studio;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.chip.Chip;
import com.nigixmosha.app.R;
import com.nigixmosha.app.databinding.ItemScriptRowBinding;
import com.nigixmosha.app.databinding.ViewStepScriptBinding;
import com.nigixmosha.app.engine.Catalog;
import com.nigixmosha.app.engine.Texts;
import com.nigixmosha.app.engine.model.Types;
import com.nigixmosha.app.studio.StudioJobs;
import com.nigixmosha.app.studio.StudioStore;
import com.nigixmosha.app.ui.Avatars;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ScriptStep extends StepView {
    private static final Map<String, Integer> EMOTION_TINT = new HashMap<>();
    private static final Map<String, Integer> EMOTION_TINT_LIGHT = new HashMap<>();

    static {
        EMOTION_TINT.put("happy", 0xFFF59E0B);
        EMOTION_TINT.put("sad", 0xFF60A5FA);
        EMOTION_TINT.put("angry", 0xFFF87171);
        EMOTION_TINT.put("fearful", 0xFFA78BFA);
        EMOTION_TINT.put("surprised", 0xFFF472B6);
        EMOTION_TINT.put("excited", 0xFF22D3EE);
        EMOTION_TINT.put("whisper", 0xFF818CF8);
        EMOTION_TINT.put("calm", 0xFF2DD4BF);
        EMOTION_TINT.put("serious", 0xFF94A3B8);
        EMOTION_TINT.put("sarcastic", 0xFF34D399);
        EMOTION_TINT.put("tender", 0xFFF9A8D4);
        EMOTION_TINT.put("shouting", 0xFFEF4444);
        EMOTION_TINT_LIGHT.put("happy", 0xFFB45309);
        EMOTION_TINT_LIGHT.put("sad", 0xFF1D4ED8);
        EMOTION_TINT_LIGHT.put("angry", 0xFFB91C1C);
        EMOTION_TINT_LIGHT.put("fearful", 0xFF6D28D9);
        EMOTION_TINT_LIGHT.put("surprised", 0xFFBE185D);
        EMOTION_TINT_LIGHT.put("excited", 0xFF0E7490);
        EMOTION_TINT_LIGHT.put("whisper", 0xFF4338CA);
        EMOTION_TINT_LIGHT.put("calm", 0xFF0F766E);
        EMOTION_TINT_LIGHT.put("serious", 0xFF334155);
        EMOTION_TINT_LIGHT.put("sarcastic", 0xFF047857);
        EMOTION_TINT_LIGHT.put("tender", 0xFF9D174D);
        EMOTION_TINT_LIGHT.put("shouting", 0xFF991B1B);
    }

    private final ViewStepScriptBinding b;
    private final Adapter adapter = new Adapter();
    private String filter = "all";
    private String query = "";
    private String chipSignature = "";
    private boolean bindingChips;

    public ScriptStep(Context context, StudioHost host) {
        super(context, host);
        b = ViewStepScriptBinding.inflate(LayoutInflater.from(context), this, true);
        b.list.setLayoutManager(new LinearLayoutManager(context));
        adapter.setHasStableIds(true);
        b.list.setAdapter(adapter);
        if (b.list.getItemAnimator() instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) b.list.getItemAnimator()).setSupportsChangeAnimations(false);
        }
        b.search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                render();
            }
        });
        b.chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (bindingChips || ids.isEmpty()) return;
            Object tag = group.findViewById(ids.get(0)).getTag();
            filter = tag == null ? "all" : tag.toString();
            render();
        });
        b.bar.barSecondary.setText(R.string.step_cast);
        b.bar.barSecondary.setIconResource(R.drawable.ic_group);
        b.bar.barSecondary.setOnClickListener(v -> host.store().setStep(StudioStore.Step.CAST));
        b.bar.barPrimary.setOnClickListener(v -> host.jobs().startProduce());
        b.bar.barAvatars.setVisibility(View.GONE);
    }

    @Override
    public void render() {
        StudioStore.State s = host.store().state();
        if (s.analysis == null) return;
        bindChips(s);
        adapter.refresh(s);
        b.noLines.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        int words = 0;
        for (Types.Segment seg : s.segments) words += Texts.wordCount(seg.text);
        b.bar.barText.setText(getContext().getString(R.string.script_summary,
                getResources().getQuantityString(R.plurals.lines, s.segments.size(), s.segments.size()),
                getResources().getQuantityString(R.plurals.words, words, Format.number(words)),
                Format.clock(Math.round(words / 150.0 * 60000))));
        boolean canProduce = s.castFor != null && host.settings().isReady(s.castFor.provider);
        b.bar.barPrimary.setEnabled(canProduce);
    }

    private void bindChips(StudioStore.State s) {
        StringBuilder sig = new StringBuilder(filter).append('|').append(s.segments.size());
        int narratorLines = 0;
        for (Types.Segment seg : s.segments) if (Types.NARRATOR_ID.equals(seg.speaker)) narratorLines++;
        sig.append('|').append(narratorLines);
        for (Types.CastCharacter c : s.analysis.characters) sig.append('|').append(c.id).append(c.name).append(c.lineCount);
        if (sig.toString().equals(chipSignature)) return;
        chipSignature = sig.toString();
        bindingChips = true;
        b.chips.removeAllViews();
        addChip("all", getContext().getString(R.string.everyone), 0xFF0A0A0A, s.segments.size(), false);
        addChip(Types.NARRATOR_ID, getContext().getString(R.string.narrator), Format.color(Catalog.get().narratorOnDark, ""), narratorLines, true);
        for (Types.CastCharacter c : s.analysis.characters) {
            addChip(c.id, c.name, Format.color(c.color, c.name), c.lineCount, true);
        }
        bindingChips = false;
    }

    private void addChip(String id, String name, int color, int count, boolean dot) {
        Chip chip = (Chip) LayoutInflater.from(getContext()).inflate(R.layout.view_filter_chip, b.chips, false);
        chip.setId(View.generateViewId());
        chip.setTag(id);
        chip.setText(name + "  " + count);
        if (dot) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(color);
            int size = Ui.dpi(getContext(), 8);
            d.setSize(size, size);
            chip.setChipIcon(d);
            chip.setChipIconVisible(true);
            chip.setChipIconSize(size);
        }
        b.chips.addView(chip);
        if (id.equals(filter)) chip.setChecked(true);
    }

    @Override
    public void onInsets() {
        b.bar.getRoot().post(() -> {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) b.bar.getRoot().getLayoutParams();
            lp.bottomMargin = Ui.dpi(getContext(), 12) + host.bottomInset();
            b.bar.getRoot().setLayoutParams(lp);
            int gutter = getResources().getDimensionPixelSize(R.dimen.screen_gutter);
            b.list.setPadding(gutter, Ui.dpi(getContext(), 4), gutter, b.bar.getRoot().getHeight() + lp.bottomMargin + Ui.dpi(getContext(), 16));
        });
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    final class Adapter extends RecyclerView.Adapter<Row> {
        private final List<Types.Segment> visible = new ArrayList<>();
        private final List<Integer> indexes = new ArrayList<>();
        private StudioStore.State state;

        void refresh(StudioStore.State s) {
            state = s;
            visible.clear();
            indexes.clear();
            for (int i = 0; i < s.segments.size(); i++) {
                Types.Segment seg = s.segments.get(i);
                if (!"all".equals(filter) && !seg.speaker.equals(filter)) continue;
                if (!query.isEmpty() && !seg.text.toLowerCase(Locale.ROOT).contains(query)) continue;
                visible.add(seg);
                indexes.add(i);
            }
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return visible.get(position).id.hashCode();
        }

        @Override
        public int getItemCount() {
            return visible.size();
        }

        @NonNull
        @Override
        public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Row(ItemScriptRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Row holder, int position) {
            holder.bind(state, visible.get(position), indexes.get(position));
        }
    }

    final class Row extends RecyclerView.ViewHolder {
        private final ItemScriptRowBinding v;
        private Types.Segment seg;
        private int index;

        Row(ItemScriptRowBinding v) {
            super(v.getRoot());
            this.v = v;
            v.text.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus || seg == null) return;
                String value = v.text.getText().toString().trim();
                if (!value.isEmpty() && !value.equals(seg.text)) host.store().updateSegment(seg.id, null, null, value);
            });
            v.speaker.setOnClickListener(view -> pickSpeaker());
            v.emotion.setOnClickListener(view -> pickEmotion());
            v.preview.setOnClickListener(view -> {
                StudioJobs jobs = host.jobs();
                String key = "line:" + seg.id;
                if (key.equals(jobs.previewKey)) jobs.stopPreview();
                else jobs.preview(key, seg);
            });
            v.more.setOnClickListener(this::more);
        }

        void bind(StudioStore.State s, Types.Segment seg, int index) {
            this.seg = seg;
            this.index = index;
            Context c = getContext();
            Types.CastCharacter character = s.analysis.character(seg.speaker);
            int color = character != null ? Format.color(character.color, character.name) : Format.color(Catalog.get().narratorColor, "");
            String name = character != null ? character.name : c.getString(R.string.narrator);
            Avatars.style(v.avatar, Format.initial(name), color, 24, 0);
            v.speakerName.setText(name);
            int surface = ContextCompat.getColor(c, R.color.surface);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(Ui.dp(c, 999));
            pill.setColor(ColorUtils.blendARGB(surface, color, 0.22f));
            v.speaker.setBackground(pill);
            v.speakerName.setTextColor(ColorUtils.blendARGB(ContextCompat.getColor(c, R.color.text_primary), color, 0.25f));

            boolean night = (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            Integer tint = (night ? EMOTION_TINT : EMOTION_TINT_LIGHT).get(seg.emotion);
            GradientDrawable chip = new GradientDrawable();
            chip.setCornerRadius(Ui.dp(c, 999));
            if (tint != null) {
                chip.setColor(ColorUtils.setAlphaComponent(tint, 0x24));
                chip.setStroke(Ui.dpi(c, 1), ColorUtils.setAlphaComponent(tint, 0x66));
                v.emotion.setTextColor(tint);
            } else {
                chip.setColor(0);
                chip.setStroke(Ui.dpi(c, 1), ContextCompat.getColor(c, R.color.stroke_strong));
                v.emotion.setTextColor(ContextCompat.getColor(c, R.color.text_secondary));
            }
            v.emotion.setBackground(chip);
            v.emotion.setText(cap(seg.emotion));

            boolean narration = Types.NARRATOR_ID.equals(seg.speaker);
            v.text.setTextColor(ContextCompat.getColor(c, narration ? R.color.text_secondary : R.color.text_primary));
            v.text.setTypeface(narration ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
            if (!v.text.hasFocus() && !v.text.getText().toString().equals(seg.text)) v.text.setText(seg.text);

            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams) itemView.getLayoutParams();
            lp.topMargin = Ui.dpi(c, seg.isPara() && index > 0 ? 14 : 4);
            itemView.setLayoutParams(lp);

            StudioJobs jobs = host.jobs();
            boolean mine = ("line:" + seg.id).equals(jobs.previewKey);
            boolean loading = mine && "loading".equals(jobs.previewState);
            boolean playing = mine && "playing".equals(jobs.previewState);
            v.row.setActivated(mine);
            v.previewLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            v.previewEq.setVisibility(playing ? View.VISIBLE : View.GONE);
            v.previewEq.setColor(color);
            v.previewEq.setPlaying(playing);
            v.previewIcon.setVisibility(!loading && !playing ? View.VISIBLE : View.GONE);
        }

        private void pickSpeaker() {
            StudioStore.State s = host.store().state();
            PopupMenu menu = new PopupMenu(getContext(), v.speaker);
            List<String> ids = new ArrayList<>();
            ids.add(Types.NARRATOR_ID);
            menu.getMenu().add(Menu.NONE, 0, 0, R.string.narrator);
            for (Types.CastCharacter c : s.analysis.characters) {
                menu.getMenu().add(Menu.NONE, ids.size(), ids.size(), c.name);
                ids.add(c.id);
            }
            menu.setOnMenuItemClickListener(item -> {
                host.store().updateSegment(seg.id, ids.get(item.getItemId()), null, null);
                return true;
            });
            menu.show();
        }

        private void pickEmotion() {
            PopupMenu menu = new PopupMenu(getContext(), v.emotion);
            for (int i = 0; i < Types.EMOTIONS.size(); i++) menu.getMenu().add(Menu.NONE, i, i, cap(Types.EMOTIONS.get(i)));
            menu.setOnMenuItemClickListener(item -> {
                host.store().updateSegment(seg.id, null, Types.EMOTIONS.get(item.getItemId()), null);
                return true;
            });
            menu.show();
        }

        private void more(View anchor) {
            PopupMenu menu = new PopupMenu(getContext(), anchor);
            if (index > 0) menu.getMenu().add(Menu.NONE, 1, 1, R.string.merge_up);
            menu.getMenu().add(Menu.NONE, 2, 2, R.string.remove_line);
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) host.store().mergeSegmentUp(seg.id);
                else host.store().deleteSegment(seg.id);
                Ui.haptic(anchor, true);
                return true;
            });
            menu.show();
        }
    }
}
