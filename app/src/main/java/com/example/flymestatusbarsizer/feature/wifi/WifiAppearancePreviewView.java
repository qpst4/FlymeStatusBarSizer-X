package com.example.flymestatusbarsizer.feature.wifi;

import com.example.flymestatusbarsizer.config.SettingsStore;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

/** Local preview observes the same saved values that are sent to SystemUI. */
public final class WifiAppearancePreviewView extends View {
    private final SharedPreferences prefs;
    private final WifiIconRenderer single = new ClassicWifiRenderer();
    private final WifiIconRenderer dual = new ClassicWifiRenderer();
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect iconBounds = new Rect();
    private final int color;
    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (prefs, key) -> {
        if (key == null || SettingsStore.KEY_WIFI_BAND_CORNER_PERCENT.equals(key)
                || SettingsStore.KEY_WIFI_TIP_CORNER_PERCENT.equals(key)
                || SettingsStore.KEY_WIFI_BAND_GAP_PERCENT.equals(key)) {
            updateAppearance();
        }
    };

    public WifiAppearancePreviewView(Context context, SharedPreferences prefs, int color) {
        super(context);
        this.prefs = prefs;
        this.color = color;
        labelPaint.setColor(color);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(13f * getResources().getDisplayMetrics().scaledDensity);
        setContentDescription("Wi-Fi 外观预览：单 Wi-Fi 与双 Wi-Fi");
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
        int corner = SettingsStore.readInt(prefs, SettingsStore.KEY_WIFI_BAND_CORNER_PERCENT,
                SettingsStore.DEFAULT_WIFI_BAND_CORNER_PERCENT);
        int tip = SettingsStore.readInt(prefs, SettingsStore.KEY_WIFI_TIP_CORNER_PERCENT,
                SettingsStore.DEFAULT_WIFI_TIP_CORNER_PERCENT);
        int gap = SettingsStore.readInt(prefs, SettingsStore.KEY_WIFI_BAND_GAP_PERCENT,
                SettingsStore.DEFAULT_WIFI_BAND_GAP_PERCENT);
        single.setAppearance(corner, tip, gap);
        dual.setAppearance(corner, tip, gap);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        int size = Math.max(1, Math.min(Math.round(66f * density), getWidth() / 3));
        drawExample(canvas, single, getWidth() * 0.25f, size, false);
        drawExample(canvas, dual, getWidth() * 0.75f, size, true);
        float labelY = Math.min(getHeight() - 8f * density,
                8f * density + size + 20f * density);
        canvas.drawText("单 Wi-Fi", getWidth() * 0.25f, labelY, labelPaint);
        canvas.drawText("双 Wi-Fi", getWidth() * 0.75f, labelY, labelPaint);
    }

    private void drawExample(Canvas canvas, WifiIconRenderer renderer, float centerX,
            int size, boolean secondary) {
        int width = renderer.measureWidth(size, secondary);
        int left = Math.round(centerX - width / 2f);
        int top = Math.round(8f * getResources().getDisplayMetrics().density);
        iconBounds.set(left, top, left + width, top + size);
        renderer.draw(canvas, iconBounds, color, 255, null, 4, secondary, 1, 0f);
    }
}
