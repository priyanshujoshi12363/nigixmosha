package com.nigixmosha.app.ui.studio;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

public abstract class StepView extends FrameLayout {
    protected final StudioHost host;

    protected StepView(@NonNull Context context, StudioHost host) {
        super(context);
        this.host = host;
    }

    public abstract void render();

    public void onShown() {
    }

    public void onHidden() {
    }

    public void release() {
    }

    public void onInsets() {
    }

    protected boolean wide() {
        return getResources().getConfiguration().screenWidthDp >= 840;
    }

    protected int spans() {
        int w = getResources().getConfiguration().screenWidthDp;
        return w >= 1100 ? 3 : w >= 640 ? 2 : 1;
    }
}
