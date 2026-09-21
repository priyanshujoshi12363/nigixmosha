package com.nigixmosha.app.ui.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.nigixmosha.app.R;
import com.nigixmosha.app.ui.Ui;

public class BackdropView extends View {
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint teal = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint violet = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int background;
    private final int tealColor;
    private final int violetColor;
    private float glowRadius;
    private float phase;
    private boolean drifting = true;
    private ValueAnimator drift;

    public BackdropView(Context context) {
        this(context, null);
    }

    public BackdropView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        background = ContextCompat.getColor(context, R.color.bg);
        tealColor = ContextCompat.getColor(context, R.color.glow_teal);
        violetColor = ContextCompat.getColor(context, R.color.glow_violet);
        int tile = Ui.dpi(context, 22);
        Bitmap bitmap = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(ContextCompat.getColor(context, R.color.backdrop_dot));
        canvas.drawCircle(tile / 2f, tile / 2f, Ui.dp(context, 1f), dot);
        dots.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    public void setDrifting(boolean drifting) {
        this.drifting = drifting;
        if (drifting) startDrift();
        else stopDrift();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        glowRadius = Math.max(w, h) * 0.62f;
        teal.setShader(new RadialGradient(0, 0, glowRadius,
                new int[]{ColorUtils.setAlphaComponent(tealColor, 64), ColorUtils.setAlphaComponent(tealColor, 0)},
                null, Shader.TileMode.CLAMP));
        violet.setShader(new RadialGradient(0, 0, glowRadius,
                new int[]{ColorUtils.setAlphaComponent(violetColor, 52), ColorUtils.setAlphaComponent(violetColor, 0)},
                null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawColor(background);
        drawGlow(canvas, teal,
                w * 0.92f + (float) Math.cos(phase) * w * 0.07f,
                h * 0.12f + (float) Math.sin(phase * 1.3f) * h * 0.05f);
        drawGlow(canvas, violet,
                w * 0.06f + (float) Math.sin(phase * 0.9f) * w * 0.08f,
                h * 0.94f + (float) Math.cos(phase * 0.7f) * h * 0.05f);
        canvas.drawRect(0, 0, w, h, dots);
    }

    private void drawGlow(Canvas canvas, Paint paint, float x, float y) {
        canvas.save();
        canvas.translate(x, y);
        canvas.drawCircle(0, 0, glowRadius, paint);
        canvas.restore();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (drifting) startDrift();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopDrift();
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (isVisible && drifting) startDrift();
        else stopDrift();
    }

    private void startDrift() {
        if (drift != null || !Ui.motionEnabled() || !isAttachedToWindow()) return;
        drift = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        drift.setDuration(18000);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setInterpolator(new LinearInterpolator());
        drift.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        drift.start();
    }

    private void stopDrift() {
        if (drift != null) {
            drift.cancel();
            drift = null;
        }
    }
}
