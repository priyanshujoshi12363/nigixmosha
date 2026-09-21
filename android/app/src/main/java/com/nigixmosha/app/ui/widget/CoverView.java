package com.nigixmosha.app.ui.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
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

public class CoverView extends View {
    private static final float[][] SPOTS = {{0.2f, 0.2f, 0.55f}, {0.8f, 0.3f, 0.55f}, {0.5f, 1f, 0.6f}};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] colors = {0xFF0D9488, 0xFF1E40AF, 0xFF4338CA};
    private final RadialGradient[] shaders = new RadialGradient[3];

    public CoverView(Context context) {
        this(context, null);
    }

    public CoverView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        int tile = Ui.dpi(context, 16);
        Bitmap bitmap = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(0x33FFFFFF);
        new Canvas(bitmap).drawCircle(tile / 2f, tile / 2f, Ui.dp(context, 0.9f), dot);
        dots.setShader(new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    public void setColors(int a, int b, int c) {
        colors = new int[]{a, b, c};
        rebuild(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        rebuild(w, h);
    }

    private void rebuild(int w, int h) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < 3; i++) {
            float cx = SPOTS[i][0] * w;
            float cy = SPOTS[i][1] * h;
            float far = (float) Math.max(Math.max(Math.hypot(cx, cy), Math.hypot(w - cx, cy)),
                    Math.max(Math.hypot(cx, h - cy), Math.hypot(w - cx, h - cy)));
            shaders[i] = new RadialGradient(cx, cy, Math.max(1f, far * SPOTS[i][2]),
                    new int[]{colors[i], ColorUtils.setAlphaComponent(colors[i], 0)}, null, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        canvas.drawColor(0xFF0A0A0A);
        for (RadialGradient s : shaders) {
            if (s == null) continue;
            paint.setShader(s);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), dots);
    }
}
