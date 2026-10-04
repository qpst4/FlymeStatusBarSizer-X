package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

/** Background owned only by the temporary global host; never recolors the Launcher's drawable. */
final class AssistantWindowBackground implements View.OnAttachStateChangeListener {
    private final View decor;
    private final Drawable original;
    private final ColorDrawable overlay;
    private final int blurRadius;
    private Drawable nativeBlur;
    private boolean active;

    AssistantWindowBackground(View decor, float density) {
        this.decor = decor;
        original = decor.getBackground();
        // View.setBackgroundColor mutates an existing ColorDrawable, including the saved original.
        // A fresh drawable also avoids changing any shared ConstantState used by the native window.
        overlay = new ColorDrawable(Color.TRANSPARENT);
        blurRadius = Math.max(1, Math.round(40 * density));
    }

    void apply() {
        if (active) return;
        active = true;
        decor.setBackground(overlay);
        // The global host has a new ViewRootImpl after migration. A drawable from the desktop
        // root would submit its blur regions to the wrong window.
        decor.addOnAttachStateChangeListener(this);
        if (decor.isAttachedToWindow()) installNativeBlur();
    }

    void updateAttributes(WindowManager.LayoutParams attrs) {
        attrs.format = PixelFormat.TRANSLUCENT;
        attrs.flags |= WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Flyme's native drawable submits blur regions independently of the standard
            // blur-behind flag, which was disabled in the device's captured WindowManager state.
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
            attrs.setBlurBehindRadius(0);
        }
    }

    @Override public void onViewAttachedToWindow(View view) {
        if (active) installNativeBlur();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        releaseNativeBlur();
        if (active) decor.setBackground(overlay);
    }

    private void installNativeBlur() {
        if (nativeBlur != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Object root = AssistantReflection.call(decor, "getViewRootImpl");
            if (root == null) throw new IllegalStateException("Assistant window has no ViewRootImpl");
            Object drawable = AssistantReflection.call(root, "createBackgroundBlurDrawable");
            if (!(drawable instanceof Drawable))
                throw new IllegalStateException("Native assistant background blur is unavailable");
            nativeBlur = (Drawable) drawable;
            AssistantReflection.callInt(nativeBlur, "setBlurRadius", blurRadius);
            AssistantReflection.method(nativeBlur.getClass(), "setCornerRadius", float.class)
                    .invoke(nativeBlur, 0f);
            // Color alpha controls tint; drawable alpha controls the blur itself. Keep the latter
            // at 255 so a completely clear tint does not disable the blur region.
            AssistantReflection.callInt(nativeBlur, "setColor", Color.TRANSPARENT);
            nativeBlur.setAlpha(255);
            try {
                // Flyme extension: place the blur below all of the assistant's cards and text.
                AssistantReflection.callInt(nativeBlur, "setZAdjustment", -1);
            } catch (NoSuchMethodException ignored) { /* Standard Android has no Z adjustment. */ }
            nativeBlur.setVisible(true, false);
            decor.setBackground(nativeBlur);
            Log.i("FlymeAssistantGesture", "Native assistant background blur attached: radius="
                    + blurRadius + ", hardwareAccelerated=" + decor.isHardwareAccelerated());
        } catch (ReflectiveOperationException | RuntimeException error) {
            releaseNativeBlur();
            decor.setBackground(overlay);
            AssistantHooks.warn("Cannot attach native assistant background blur", error);
        }
    }

    private void releaseNativeBlur() {
        if (nativeBlur == null) return;
        // Remove this region from the old root's aggregator before the desktop host is restored.
        nativeBlur.setVisible(false, false);
        nativeBlur.setCallback(null);
        nativeBlur = null;
    }

    void restore() {
        active = false;
        decor.removeOnAttachStateChangeListener(this);
        releaseNativeBlur();
        decor.setBackground(original);
    }
}
