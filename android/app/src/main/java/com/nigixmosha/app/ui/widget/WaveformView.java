package com.nigixmosha.app.ui.widget;

import android.animation.ArgbEvaluator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.nigixmosha.app.R;
import com.nigixmosha.app.ui.Ui;

public class WaveformView extends View {
    private static final int STAGGER = 8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final ArgbEvaluator evaluator = new ArgbEvaluator();
    private final int[] palette;
    private int barCount = 30;
    private int[] colors = new int[0];
    private float intro = 1f;
    private float energy = 1f;
    private boolean running;
    private long startedAt;

    public WaveformView(Context context) {
        this(context, null);
    }

    public WaveformView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        palette = new int[]{
                ContextCompat.getColor(context, R.color.accent),
                ContextCompat.getColor(context, R.color.violet),
                ContextCompat.getColor(context, R.color.crimson)
        };
        rebuildColors();
    }

    public void setBarCount(int count) {
        barCount = Math.max(4, count);
        rebuildColors();
        invalidate();
    }

    public void setEnergy(float energy) {
        this.energy = energy;
        invalidate();
    }

    @Keep
    public void setIntro(float intro) {
        this.intro = intro;
        invalidate();
    }

    @Keep
    public float getIntro() {
        return intro;
    }

    public void start() {
        if (running) return;
        running = Ui.motionEnabled();
        startedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public void stop() {
        running = false;
        invalidate();
    }

    private void rebuildColors() {
        colors = new int[barCount];
        for (int i = 0; i < barCount; i++) {
            float t = barCount == 1 ? 0 : i / (float) (barCount - 1);
            float scaled = t * (palette.length - 1);
            int index = Math.min((int) scaled, palette.length - 2);
            colors[i] = (int) evaluator.evaluate(scaled - index, palette[index], palette[index + 1]);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth() - getPaddingLeft() - getPaddingRight();
        int h = getHeight() - getPaddingTop() - getPaddingBottom();
        if (w <= 0 || h <= 0) return;
        float t = running ? (SystemClock.uptimeMillis() - startedAt) / 1000f : 0.6f;
        float barWidth = w / (barCount + (barCount - 1) * 0.7f);
        float gap = barWidth * 0.7f;
        float centerY = getPaddingTop() + h / 2f;
        float x = getPaddingLeft();
        for (int i = 0; i < barCount; i++) {
            double wave = Math.sin(t * 3.1 + i * 0.55) * 0.55
                    + Math.sin(t * 1.7 + i * 1.3) * 0.3
                    + Math.sin(t * 5.3 + i * 0.37) * 0.15;
            float level = 0.5f + 0.5f * (float) wave;
            float envelope = 0.5f + 0.5f * (float) Math.sin(Math.PI * (i + 0.5f) / barCount);
            float rise = Math.max(0f, Math.min(1f, (intro * (barCount + STAGGER) - i) / STAGGER));
            rise = 1f - (1f - rise) * (1f - rise) * (1f - rise);
            float height = Math.max(barWidth, h * (0.16f + 0.84f * level * envelope) * energy * rise);
            if (rise <= 0f) height = 0f;
            bar.set(x, centerY - height / 2f, x + barWidth, centerY + height / 2f);
            paint.setColor(colors[i]);
            paint.setAlpha((int) (255 * Math.min(1f, rise * 1.4f)));
            canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, paint);
            x += barWidth + gap;
        }
        if (running) postInvalidateOnAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }
}
