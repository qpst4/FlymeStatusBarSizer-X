package com.example.flymestatusbarsizer.feature.assistant;

import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.WindowManager;

/** Native blur moves with the sliding content, and is owned only by the temporary global host. */
final class AssistantWindowBackground implements View.OnAttachStateChangeListener,
        ViewTreeObserver.OnPreDrawListener {
    private final View content;
    private final Drawable original;
    private final ColorDrawable overlay;
    private final int blurRadius;
    private Drawable nativeBlur;
    private final Rect visibleBounds = new Rect();
    private ViewTreeObserver observer;
    private boolean active;

    AssistantWindowBackground(View content, float density) {
        this.content = content;
        original = content.getBackground();
        // View.setBackgroundColor mutates an existing ColorDrawable, including the saved original.
        // A fresh drawable also avoids changing any shared ConstantState used by the native window.
        overlay = new ColorDrawable(Color.TRANSPARENT);
        blurRadius = Math.max(1, Math.round(40 * density));
    }

    void apply() {
        if (active) return;
        active = true;
        content.setBackground(overlay);
        // The global host has a new ViewRootImpl after migration. A drawable from the desktop
        // root would submit its blur regions to the wrong window.
        content.addOnAttachStateChangeListener(this);
        if (content.isAttachedToWindow()) onViewAttachedToWindow(content);
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
        if (!active) return;
        removePreDrawListener();
        observer = content.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        installNativeBlur();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        removePreDrawListener();
        releaseNativeBlur();
        if (active) content.setBackground(overlay);
    }

    @Override public boolean onPreDraw() {
        if (active && nativeBlur != null) {
            float alpha = content.getAlpha();
            if (content.getVisibility() != View.VISIBLE || !content.getGlobalVisibleRect(visibleBounds)) {
                alpha = 0;
            } else {
                for (ViewParent parent = content.getParent(); parent instanceof View; parent = parent.getParent()) {
                    if (((View) parent).getVisibility() != View.VISIBLE) { alpha = 0; break; }
                    alpha *= ((View) parent).getAlpha();
                }
            }
            // RenderNode tracks this content's sliding bounds automatically. Its blur-region
            // alpha is separate from View alpha, so synchronize it before the same frame draws.
            nativeBlur.setAlpha(Math.round(255 * Math.max(0, Math.min(1, alpha))));
        }
        return true;
    }

    private void removePreDrawListener() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        observer = null;
    }

    private void installNativeBlur() {
        if (nativeBlur != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            Object root = AssistantReflection.call(content, "getViewRootImpl");
            if (root == null) throw new IllegalStateException("Assistant window has no ViewRootImpl");
            Object drawable = AssistantReflection.call(root, "createBackgroundBlurDrawable");
            if (!(drawable instanceof Drawable))
                throw new IllegalStateException("Native assistant background blur is unavailable");
            nativeBlur = (Drawable) drawable;
            AssistantReflection.callInt(nativeBlur, "setBlurRadius", blurRadius);
            AssistantReflection.method(nativeBlur.getClass(), "setCornerRadius", float.class)
                    .invoke(nativeBlur, 0f);
            // Color alpha controls tint; drawable alpha controls the blur itself. The first
            // pre-draw enables the region only if the sliding content is actually visible.
            AssistantReflection.callInt(nativeBlur, "setColor", Color.TRANSPARENT);
            nativeBlur.setAlpha(0);
            try {
                // Flyme extension: place the blur below all of the assistant's cards and text.
                AssistantReflection.callInt(nativeBlur, "setZAdjustment", -1);
            } catch (NoSuchMethodException ignored) { /* Standard Android has no Z adjustment. */ }
            nativeBlur.setVisible(true, false);
            content.setBackground(nativeBlur);
            onPreDraw();
            Log.i("FlymeAssistantGesture", "Native assistant background blur attached: radius="
                    + blurRadius + ", hardwareAccelerated=" + content.isHardwareAccelerated());
        } catch (ReflectiveOperationException | RuntimeException error) {
            releaseNativeBlur();
            content.setBackground(overlay);
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
        content.removeOnAttachStateChangeListener(this);
        removePreDrawListener();
        releaseNativeBlur();
        content.setBackground(original);
    }
}
