package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;

import com.example.flymestatusbarsizer.config.SettingsStore;

/** Owns a touch-through marker on the app window for the lifetime of this settings page. */
public final class AssistantGesturePreviewLayout extends LinearLayout {
    private static final long PREVIEW_DURATION_MS = 5000;
    private final SharedPreferences prefs;
    private final DistanceMarker marker;
    private final Runnable dismiss = this::hidePreview;
    private View overlayHost;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP.equals(key)) showPreview();
    };

    public AssistantGesturePreviewLayout(Context context, SharedPreferences prefs, int accentColor) {
        super(context);
        this.prefs = prefs;
        marker = new DistanceMarker(accentColor);
        setOrientation(VERTICAL);
    }

    public void showPreview() {
        if (!isAttachedToWindow() || !isShown() || !hasWindowFocus()
                || getWindowVisibility() != VISIBLE) return;
        hidePreview();
        int distance = Math.max(40, Math.min(240, SettingsStore.readInt(prefs,
                SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP,
                SettingsStore.DEFAULT_ASSISTANT_GESTURE_DISTANCE_DP)));
        overlayHost = getRootView();
        marker.distanceDp = distance;
        marker.label = distance + "dp 达标线";
        marker.setBounds(0, 0, overlayHost.getWidth(), overlayHost.getHeight());
        overlayHost.getOverlay().add(marker);
        postDelayed(dismiss, PREVIEW_DURATION_MS);
    }

    private void hidePreview() {
        removeCallbacks(dismiss);
        if (overlayHost != null) {
            overlayHost.getOverlay().remove(marker);
            overlayHost = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prefs.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    protected void onDetachedFromWindow() {
        hidePreview();
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        // This can be called by the View constructor, before our fields are initialized.
        if (marker != null && !isShown()) hidePreview();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (marker != null && visibility != VISIBLE) hidePreview();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) hidePreview();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (marker != null) hidePreview();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private final class DistanceMarker extends Drawable {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int distanceDp;
        private String label = "";

        DistanceMarker(int accentColor) {
            linePaint.setColor(accentColor);
            linePaint.setStrokeWidth(dp(2));
            outlinePaint.setColor(Color.WHITE);
            outlinePaint.setStrokeWidth(dp(4));
            labelPaint.setColor(Color.WHITE);
            labelPaint.setTextSize(14f * getResources().getDisplayMetrics().scaledDensity);
            backgroundPaint.setColor(0xe6222222);
        }

        @Override
        public void draw(Canvas canvas) {
            float x = dp(distanceDp);
            float top = dp(24);
            float bottom = getBounds().height() - dp(24);
            WindowInsets insets = getRootWindowInsets();
            if (insets != null) {
                top += insets.getSystemWindowInsetTop();
                bottom -= insets.getSystemWindowInsetBottom();
            }
            float center = (top + bottom) / 2f;
            // Decor coordinates start at the window's left edge, not at the settings card.
            canvas.drawLine(x, top, x, bottom, outlinePaint);
            canvas.drawLine(0, center, x, center, outlinePaint);
            canvas.drawLine(x, top, x, bottom, linePaint);
            canvas.drawLine(0, center, x, center, linePaint);
            canvas.drawLine(x - dp(8), center - dp(6), x, center, linePaint);
            canvas.drawLine(x - dp(8), center + dp(6), x, center, linePaint);

            float textWidth = labelPaint.measureText(label);
            float labelLeft = Math.max(dp(8), Math.min(x + dp(12),
                    getBounds().width() - textWidth - dp(24)));
            float baseline = center - dp(18) - labelPaint.descent();
            canvas.drawRoundRect(labelLeft - dp(8), baseline + labelPaint.ascent() - dp(6),
                    labelLeft + textWidth + dp(8), baseline + labelPaint.descent() + dp(6),
                    dp(8), dp(8), backgroundPaint);
            canvas.drawText(label, labelLeft, baseline, labelPaint);
        }

        @Override public void setAlpha(int alpha) {
            linePaint.setAlpha(alpha);
            outlinePaint.setAlpha(alpha);
            labelPaint.setAlpha(alpha);
            backgroundPaint.setAlpha(Math.round(alpha * 0xe6 / 255f));
            invalidateSelf();
        }

        @Override public void setColorFilter(ColorFilter filter) {
            linePaint.setColorFilter(filter);
            outlinePaint.setColorFilter(filter);
            labelPaint.setColorFilter(filter);
            backgroundPaint.setColorFilter(filter);
            invalidateSelf();
        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
