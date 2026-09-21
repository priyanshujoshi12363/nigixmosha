package com.nigixmosha.app.ui.library;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.nigixmosha.app.R;
import com.nigixmosha.app.data.model.CastMember;
import com.nigixmosha.app.data.model.Project;
import com.nigixmosha.app.databinding.ItemLibraryBinding;
import com.nigixmosha.app.databinding.ItemLibrarySkeletonBinding;
import com.nigixmosha.app.engine.Lang;
import com.nigixmosha.app.ui.Avatars;
import com.nigixmosha.app.ui.Format;
import com.nigixmosha.app.ui.Ui;

import java.util.ArrayList;
import java.util.List;

final class LibraryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    interface Listener {
        void onPlay(Project project);

        void onOpen(Project project);

        void onDownload(Project project);

        void onDelete(Project project);
    }

    private static final int TYPE_ITEM = 1;
    private static final int TYPE_SKELETON = 2;
    private static final Object PAYLOAD_PLAYBACK = new Object();
    private static final Object PAYLOAD_PROGRESS = new Object();
    private static final Object PAYLOAD_ACTIONS = new Object();
    private static final long STAGGER_WINDOW_MS = 700;

    private final List<Project> items = new ArrayList<>();
    private final Listener listener;
    private int skeletons;
    private String activeId;
    private boolean activePlaying;
    private double activeProgress;
    private String confirmId;
    private String openingId;
    private int animatedUpTo = -1;
    private long submittedAt;

    LibraryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    void showSkeleton(int count) {
        items.clear();
        skeletons = count;
        animatedUpTo = Integer.MAX_VALUE;
        notifyDataSetChanged();
    }

    void submit(List<Project> next) {
        if (skeletons > 0 || items.isEmpty()) {
            skeletons = 0;
            items.clear();
            items.addAll(next);
            animatedUpTo = -1;
            submittedAt = SystemClock.uptimeMillis();
            notifyDataSetChanged();
            return;
        }
        List<Project> old = new ArrayList<>(items);
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return old.size();
            }

            @Override
            public int getNewListSize() {
                return next.size();
            }

            @Override
            public boolean areItemsTheSame(int o, int n) {
                return old.get(o).id.equals(next.get(n).id);
            }

            @Override
            public boolean areContentsTheSame(int o, int n) {
                return old.get(o).sameContent(next.get(n));
            }
        });
        items.clear();
        items.addAll(next);
        diff.dispatchUpdatesTo(this);
    }

    int count() {
        return items.size();
    }

    void setPlayback(String id, boolean playing) {
        String previous = activeId;
        activeId = id;
        activePlaying = playing;
        notifyFor(previous, PAYLOAD_PLAYBACK);
        if (id != null && !id.equals(previous)) notifyFor(id, PAYLOAD_PLAYBACK);
        else if (id != null) notifyFor(id, PAYLOAD_PLAYBACK);
    }

    void setProgress(double fraction) {
        activeProgress = fraction;
        notifyFor(activeId, PAYLOAD_PROGRESS);
    }

    void setConfirm(String id) {
        String previous = confirmId;
        confirmId = id;
        notifyFor(previous, PAYLOAD_ACTIONS);
        notifyFor(id, PAYLOAD_ACTIONS);
    }

    String confirmId() {
        return confirmId;
    }

    void setOpening(String id) {
        String previous = openingId;
        openingId = id;
        notifyFor(previous, PAYLOAD_ACTIONS);
        notifyFor(id, PAYLOAD_ACTIONS);
    }

    private void notifyFor(String id, Object payload) {
        if (id == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) {
                notifyItemChanged(i, payload);
                return;
            }
        }
    }

    @Override
    public int getItemCount() {
        return skeletons > 0 ? skeletons : items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return skeletons > 0 ? TYPE_SKELETON : TYPE_ITEM;
    }

    @Override
    public long getItemId(int position) {
        return skeletons > 0 ? -1 - position : items.get(position).id.hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SKELETON) return new SkeletonHolder(ItemLibrarySkeletonBinding.inflate(inflater, parent, false));
        return new ItemHolder(ItemLibraryBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (holder instanceof ItemHolder && !payloads.isEmpty()) {
            ItemHolder h = (ItemHolder) holder;
            Project p = items.get(position);
            for (Object o : payloads) {
                if (o == PAYLOAD_PLAYBACK) h.bindPlayback(p);
                else if (o == PAYLOAD_PROGRESS) h.bindProgress(p);
                else if (o == PAYLOAD_ACTIONS) h.bindActions(p);
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof ItemHolder)) return;
        ((ItemHolder) holder).bind(items.get(position));
        if (position > animatedUpTo && Ui.motionEnabled()) {
            animatedUpTo = position;
            boolean firstWave = SystemClock.uptimeMillis() - submittedAt < STAGGER_WINDOW_MS;
            View view = holder.itemView;
            view.setAlpha(0f);
            view.setTranslationY(Ui.dp(view.getContext(), 28));
            view.animate().alpha(1f).translationY(0f)
                    .setStartDelay(firstWave ? Math.min(position, 8) * 60L : 0L)
                    .setDuration(520).setInterpolator(Ui.DECELERATE).start();
        }
    }

    @Override
    public void onViewAttachedToWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof SkeletonHolder) ((SkeletonHolder) holder).start();
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof SkeletonHolder) ((SkeletonHolder) holder).stop();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
    }

    final class ItemHolder extends RecyclerView.ViewHolder {
        private final ItemLibraryBinding b;

        ItemHolder(ItemLibraryBinding binding) {
            super(binding.getRoot());
            b = binding;
            b.eq.setColor(ContextCompat.getColor(binding.getRoot().getContext(), R.color.night));
        }

        void bind(Project p) {
            Context c = itemView.getContext();
            List<CastMember> cast = p.cast();
            int a = cast.size() > 0 ? Format.color(cast.get(0).color, cast.get(0).name) : 0xFF0D9488;
            int bb = cast.size() > 1 ? Format.color(cast.get(1).color, cast.get(1).name) : 0xFF1E40AF;
            int cc = cast.size() > 2 ? Format.color(cast.get(2).color, cast.get(2).name) : 0xFF4338CA;
            b.cover.setColors(a, bb, cc);
            b.badge.setText(p.hasAudio() ? R.string.recorded : R.string.draft);
            b.badge.setAlpha(p.hasAudio() ? 1f : 0.8f);
            b.lang.setText(p.language != null ? Lang.languageName(p.language) : c.getString(R.string.unknown_language));
            b.title.setText(p.displayTitle());
            b.time.setText(Format.relative(p.updatedAt));

            List<Avatars.Person> people = new ArrayList<>();
            for (CastMember m : cast) people.add(Avatars.person(m.name, m.color));
            Avatars.stack(b.cast, people, 5, 28, ContextCompat.getColor(c, R.color.surface), null);

            String duration = p.hasAudio() ? Format.clock(p.durationMs()) : c.getString(R.string.not_recorded);
            String voices = c.getResources().getQuantityString(R.plurals.voices, cast.size() + 1, cast.size() + 1);
            String lines = c.getResources().getQuantityString(R.plurals.lines, p.segments, p.segments);
            StringBuilder meta = new StringBuilder(duration).append("  ·  ").append(voices).append("  ·  ").append(lines);
            if (p.words > 0) meta.append("  ·  ").append(c.getResources().getQuantityString(R.plurals.words, p.words, Format.number(p.words)));
            b.meta.setText(meta);

            b.play.setVisibility(p.hasAudio() ? View.VISIBLE : View.GONE);
            b.download.setVisibility(p.hasAudio() && p.audio.downloadUrl != null ? View.VISIBLE : View.GONE);
            b.play.setOnClickListener(v -> listener.onPlay(p));
            b.coverFrame.setOnClickListener(v -> {
                if (p.hasAudio()) listener.onPlay(p);
                else listener.onOpen(p);
            });
            b.open.setOnClickListener(v -> listener.onOpen(p));
            b.download.setOnClickListener(v -> listener.onDownload(p));
            b.delete.setOnClickListener(v -> listener.onDelete(p));
            bindPlayback(p);
            bindActions(p);
        }

        void bindPlayback(Project p) {
            boolean active = p.id.equals(activeId);
            boolean playing = active && activePlaying;
            b.playIcon.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            b.eq.setVisibility(View.GONE);
            b.card.setStrokeColor(ContextCompat.getColor(itemView.getContext(), active ? R.color.accent : R.color.stroke));
            b.progress.setVisibility(active ? View.VISIBLE : View.GONE);
            b.play.setContentDescription(itemView.getContext().getString(playing ? R.string.pause : R.string.play));
            bindProgress(p);
        }

        void bindProgress(Project p) {
            if (p.id.equals(activeId)) b.progress.setProgressCompat((int) Math.round(activeProgress * 1000), true);
        }

        void bindActions(Project p) {
            Context c = itemView.getContext();
            boolean confirming = p.id.equals(confirmId);
            b.delete.setText(confirming ? R.string.delete_forever : R.string.delete);
            int color = ContextCompat.getColor(c, confirming ? R.color.error : R.color.text_tertiary);
            b.delete.setTextColor(color);
            b.delete.setIconTint(ColorStateList.valueOf(color));
            b.delete.setBackgroundTintList(ColorStateList.valueOf(confirming ? ContextCompat.getColor(c, R.color.error_bg) : 0));
            boolean opening = p.id.equals(openingId);
            b.opening.setVisibility(opening ? View.VISIBLE : View.GONE);
            b.open.setText(opening ? "" : c.getString(R.string.open_in_studio));
            b.open.setEnabled(openingId == null);
        }
    }

    static final class SkeletonHolder extends RecyclerView.ViewHolder {
        private final ItemLibrarySkeletonBinding b;
        private ObjectAnimator pulse;

        SkeletonHolder(ItemLibrarySkeletonBinding binding) {
            super(binding.getRoot());
            b = binding;
        }

        void start() {
            if (pulse != null || !Ui.motionEnabled()) return;
            pulse = ObjectAnimator.ofFloat(b.pulse, View.ALPHA, 1f, 0.45f);
            pulse.setDuration(900);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.start();
        }

        void stop() {
            if (pulse != null) pulse.cancel();
            pulse = null;
            b.pulse.setAlpha(1f);
        }
    }
}
