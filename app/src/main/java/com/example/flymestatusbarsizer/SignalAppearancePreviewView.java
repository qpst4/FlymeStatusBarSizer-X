package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Enlarged examples use the same geometry and saved appearance as the status bar. */
final class SignalAppearancePreviewView extends View {
    private final SharedPreferences prefs;
    private final ModuleConfig style = new ModuleConfig();
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect iconBounds = new Rect();
    private final int color;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || SettingsStore.KEY_SIGNAL_BAR1_HEIGHT_PERCENT.equals(key)
                || SettingsStore.KEY_SIGNAL_BAR2_HEIGHT_PERCENT.equals(key)
                || SettingsStore.KEY_SIGNAL_BAR3_HEIGHT_PERCENT.equals(key)
                || SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT.equals(key)
                || SettingsStore.KEY_SIGNAL_DOT_CORNER_RADIUS_PERCENT.equals(key)) {
            updateAppearance();
        }
    };

    SignalAppearancePreviewView(Context context, SharedPreferences prefs, int color) {
        super(context);
        this.prefs = prefs;
        this.color = color;
        labelPaint.setColor(color);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
        setContentDescription("信号外观放大预览：单卡满格；双卡合一，主卡三格、副卡两格");
        updateAppearance();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        prefs.registerOnSharedPreferenceChangeListener(listener);
        updateAppearance();
    }

    @Override
    protected void onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener);
        super.onDetachedFromWindow();
    }

    private void updateAppearance() {
        style.signalBar1HeightPercent = SettingsStore.readInt(prefs,
                SettingsStore.KEY_SIGNAL_BAR1_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR1_HEIGHT_PERCENT);
        style.signalBar2HeightPercent = SettingsStore.readInt(prefs,
                SettingsStore.KEY_SIGNAL_BAR2_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR2_HEIGHT_PERCENT);
        style.signalBar3HeightPercent = SettingsStore.readInt(prefs,
                SettingsStore.KEY_SIGNAL_BAR3_HEIGHT_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR3_HEIGHT_PERCENT);
        style.signalBarCornerRadiusPercent = SettingsStore.readInt(prefs,
                SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_BAR_CORNER_RADIUS_PERCENT);
        style.signalDotCornerRadiusPercent = SettingsStore.readInt(prefs,
                SettingsStore.KEY_SIGNAL_DOT_CORNER_RADIUS_PERCENT,
                SettingsStore.DEFAULT_SIGNAL_DOT_CORNER_RADIUS_PERCENT);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Other previews share the painter; apply this view's settings for every frame.
        SignalPreviewPainter.configureStyle(style);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.max(1, Math.min(Math.round(66f * density), getWidth() / 3));
        drawExample(canvas, getWidth() * 0.25f, size, false);
        drawExample(canvas, getWidth() * 0.75f, size, true);
        float labelY = Math.min(getHeight() - 8f * density,
                8f * density + size + 20f * density);
        canvas.drawText("单卡", getWidth() * 0.25f, labelY, labelPaint);
        canvas.drawText("双卡合一", getWidth() * 0.75f, labelY, labelPaint);
    }

    private void drawExample(Canvas canvas, float centerX, int size, boolean mergedDual) {
        int left = Math.round(centerX - size / 2f);
        int top = Math.round(8f * getResources().getDisplayMetrics().density);
        iconBounds.set(left, top, left + size, top + size);
        if (mergedDual) {
            SignalPreviewPainter.drawMergedDualSim(canvas, iconBounds, color, 3, 2);
        } else {
            SignalPreviewPainter.drawSingleSim(canvas, iconBounds, color);
        }
    }
}
