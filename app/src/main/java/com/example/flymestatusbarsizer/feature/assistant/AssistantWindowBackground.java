package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;

/** Background owned only by the temporary global host; never recolors the Launcher's drawable. */
final class AssistantWindowBackground {
    private final View decor;
    private final Drawable original;
    private final ColorDrawable overlay;
    private final int blurRadius;

    AssistantWindowBackground(View decor, boolean dark, float density) {
        this.decor = decor;
        original = decor.getBackground();
        // View.setBackgroundColor mutates an existing ColorDrawable, including the saved original.
        // A fresh drawable also avoids changing any shared ConstantState used by the native window.
        overlay = new ColorDrawable(dark ? 0x99181818 : 0x99f3f3f3);
        blurRadius = Math.max(1, Math.round(40 * density));
    }

    void apply() {
        decor.setBackground(overlay);
    }

    void updateAttributes(WindowManager.LayoutParams attrs) {
        // The compositor must retain transparency to show the live app through the tinted blur.
        // Reapply on native open/close attribute changes as well as the initial attachment.
        attrs.format = PixelFormat.TRANSLUCENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attrs.setBlurBehindRadius(blurRadius);
        }
    }

    void restore() {
        decor.setBackground(original);
    }
}
