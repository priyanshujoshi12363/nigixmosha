package com.nigixmosha.app.ui.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nigixmosha.app.ui.Ui;

public final class AvatarDrawable extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String label;
    private final int size;

    private AvatarDrawable(Context context, String label, int color, int sizeDp) {
        this.label = label;
        this.size = Ui.dpi(context, sizeDp);
        fill.setColor(color);
        text.setColor(0xFFFFFFFF);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public static AvatarDrawable of(Context context, String label, int color) {
        return new AvatarDrawable(context, label, color, 22);
    }

    public static AvatarDrawable of(Context context, String label, int color, int sizeDp) {
        return new AvatarDrawable(context, label, color, sizeDp);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect r = getBounds();
        float cx = r.exactCenterX();
        float cy = r.exactCenterY();
        float radius = Math.min(r.width(), r.height()) / 2f;
        canvas.drawCircle(cx, cy, radius, fill);
        text.setTextSize(radius * 0.95f);
        Paint.FontMetrics fm = text.getFontMetrics();
        canvas.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
