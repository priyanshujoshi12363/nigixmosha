package com.nigixmosha.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nigixmosha.app.engine.model.Types;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WaveSeekView extends View {
    public interface OnSeek {
        void onSeek(double seconds, boolean done);
    }

    private static final int BINS = 220;
    private static final int SILENT = 0xFF334155;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private float[] heights = new float[0];
    private int[] colors = new int[0];
    private double duration;
    private double time;
    private OnSeek listener;
    private boolean dragging;

    public WaveSeekView(Context context) {
        this(context, null);
    }

    public WaveSeekView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        head.setColor(0xCCFFFFFF);
        setClickable(true);
    }

    public void setOnSeek(OnSeek listener) {
        this.listener = listener;
    }

    public void setData(List<Double> peaks, double duration, List<Types.TimelineEntry> timeline, Map<String, Integer> speakerColors, int narratorColor) {
        this.duration = duration;
        List<Double> p = peaks == null ? Collections.emptyList() : peaks;
        heights = new float[BINS];
        colors = new int[BINS];
        double per = p.size() / (double) BINS;
        for (int i = 0; i < BINS; i++) {
            int from = (int) Math.floor(i * per);
            int to = (int) Math.floor((i + 1) * per);
            double peak = 0;
            for (int k = from; k < Math.min(to, p.size()); k++) peak = Math.max(peak, p.get(k));
            heights[i] = (float) (0.12 + peak * 0.88);
            double t = (i + 0.5) / BINS * duration;
            int idx = find(timeline, t);
            int color = SILENT;
            if (idx >= 0) {
                Types.TimelineEntry e = timeline.get(idx);
                if (t <= e.end + 0.05) {
                    Integer c = speakerColors.get(e.speaker);
                    color = c != null ? c : narratorColor;
                }
            }
            colors[i] = color;
        }
        invalidate();
    }

    public static int find(List<Types.TimelineEntry> timeline, double t) {
        if (timeline == null) return -1;
        int lo = 0;
        int hi = timeline.size() - 1;
        int best = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (timeline.get(mid).start <= t) {
                best = mid;
                lo = mid + 1;
            } else hi = mid - 1;
        }
        return best;
    }

    public void setTime(double time) {
        if (dragging) return;
        this.time = time;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (heights.length == 0 || w == 0) return;
        float gap = Math.max(1f, w / (float) BINS * 0.28f);
        float barW = (w - gap * (BINS - 1)) / BINS;
        double progress = duration > 0 ? time / duration : 0;
        float x = 0;
        for (int i = 0; i < BINS; i++) {
            float bh = h * heights[i];
            bar.set(x, (h - bh) / 2f, x + barW, (h + bh) / 2f);
            paint.setColor(colors[i]);
            paint.setAlpha((i + 0.5) / BINS <= progress ? 255 : 72);
            canvas.drawRoundRect(bar, barW / 2f, barW / 2f, paint);
            x += barW + gap;
        }
        float px = (float) (progress * w);
        canvas.drawRect(px - 0.75f, 0, px + 0.75f, h, head);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (duration <= 0) return false;
        double t = Math.max(0, Math.min(duration, event.getX() / Math.max(1, getWidth()) * duration));
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                time = t;
                invalidate();
                if (listener != null) listener.onSeek(t, false);
                return true;
            case MotionEvent.ACTION_MOVE:
                time = t;
                invalidate();
                if (listener != null) listener.onSeek(t, false);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                time = t;
                invalidate();
                if (listener != null) listener.onSeek(t, true);
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
