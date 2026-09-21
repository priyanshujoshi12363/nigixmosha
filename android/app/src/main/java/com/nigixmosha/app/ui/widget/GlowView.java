package com.nigixmosha.app.ui.widget;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.nigixmosha.app.ui.Ui;

public class GlowView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xFF2DD4BF;
    private ValueAnimator shift;
    private ValueAnimator pulse;
    private float breath = 1f;

    public GlowView(Context context) {
        this(context, null);
    }

    public GlowView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setColor(int next) {
        if (next == color) return;
        if (shift != null) shift.cancel();
        if (!Ui.motionEnabled()) {
            color = next;
            invalidate();
            return;
        }
        int from = color;
        shift = ValueAnimator.ofObject(new ArgbEvaluator(), from, next);
        shift.setDuration(700);
        shift.addUpdateListener(a -> {
            color = (int) a.getAnimatedValue();
            invalidate();
        });
        shift.start();
    }

    public void setBreathing(boolean on) {
        if (on && pulse == null && Ui.motionEnabled()) {
            pulse = ValueAnimator.ofFloat(0.85f, 1.15f);
            pulse.setDuration(3000);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> {
                breath = (float) a.getAnimatedValue();
                invalidate();
            });
            pulse.start();
        } else if (!on && pulse != null) {
            pulse.cancel();
            pulse = null;
            breath = 1f;
            invalidate();
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;
        float r = Math.max(w, h) * 0.55f * breath;
        paint.setShader(new RadialGradient(w * 0.1f, h * 0.05f, r,
                new int[]{ColorUtils.setAlphaComponent(color, 0x66), ColorUtils.setAlphaComponent(color, 0)}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        float r2 = Math.max(w, h) * 0.5f;
        soft.setShader(new RadialGradient(w * 0.95f, h * 1.05f, r2,
                new int[]{0x14FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, soft);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (shift != null) shift.cancel();
        setBreathing(false);
        super.onDetachedFromWindow();
    }
}
