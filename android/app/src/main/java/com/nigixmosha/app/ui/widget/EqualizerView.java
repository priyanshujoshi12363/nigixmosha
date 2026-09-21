package com.nigixmosha.app.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nigixmosha.app.ui.Ui;

public class EqualizerView extends View {
    private static final float[] REST = {0.38f, 0.72f, 0.5f, 0.3f};
    private static final float[] SPEED = {7.1f, 5.3f, 8.7f, 6.2f};
    private static final float[] OFFSET = {0f, 1.1f, 2.3f, 0.6f};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private boolean playing;
    private long startedAt;

    public EqualizerView(Context context) {
        this(context, null);
    }

    public EqualizerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0xFFFFFFFF);
    }

    public void setColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public void setPlaying(boolean playing) {
        boolean animate = playing && Ui.motionEnabled();
        if (this.playing == animate) return;
        this.playing = animate;
        startedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        int count = REST.length;
        float barWidth = w / (count + (count - 1) * 0.55f);
        float gap = barWidth * 0.55f;
        float t = (SystemClock.uptimeMillis() - startedAt) / 1000f;
        float x = 0;
        for (int i = 0; i < count; i++) {
            float level = playing
                    ? 0.22f + 0.78f * Math.abs((float) Math.sin(t * SPEED[i] * 0.5f + OFFSET[i]))
                    : REST[i];
            float barHeight = Math.max(barWidth, h * level);
            bar.set(x, h - barHeight, x + barWidth, h);
            canvas.drawRoundRect(bar, barWidth / 2f, barWidth / 2f, paint);
            x += barWidth + gap;
        }
        if (playing) postInvalidateOnAnimation();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        invalidate();
    }
}
