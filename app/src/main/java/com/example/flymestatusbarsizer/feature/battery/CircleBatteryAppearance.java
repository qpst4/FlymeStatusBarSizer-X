package com.example.flymestatusbarsizer.feature.battery;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

import java.util.WeakHashMap;

/** Applies foreground colors without changing Flyme's native palette or background ring. */
public final class CircleBatteryAppearance {
    private static final int[] CAMERA_CIRCLE_RAINBOW_COLORS = {
            Color.RED, 0xFFFF9800, Color.YELLOW, Color.GREEN,
            Color.CYAN, Color.BLUE, 0xFF9C27B0, Color.RED
    };
    private static final WeakHashMap<View, RainbowBatteryState> CAMERA_CIRCLE_RAINBOW_STATES =
            new WeakHashMap<>();

    private CircleBatteryAppearance() { }

    static int foregroundAlpha(int transparencyTenthPercent) {
        int transparency = Math.max(0, Math.min(1000, transparencyTenthPercent));
        return Math.round((1000 - transparency) * 255f / 1000f);
    }

    public static void apply(Object value, ModuleConfig config) {
        if (!(value instanceof View) || config == null) return;
        View view = (View) value;
        Object paintValue = ReflectUtils.getField(view, "mPaint");
        if (!(paintValue instanceof Paint)) return;
        Paint paint = (Paint) paintValue;
        if (!config.enabled || !config.cameraCircleBatteryEnabled) {
            stopCameraCircleRainbow(view, paint);
            // mPaintColor is maintained by Flyme.apply(); never overwrite it with custom colors.
            paint.setColor(ReflectUtils.getIntField(view, "mPaintColor", Color.WHITE));
            view.invalidate();
            return;
        }
        boolean charging = ReflectUtils.getBooleanField(view, "mCharging", false);
        boolean powerSave = ReflectUtils.getBooleanField(view, "mLowPowerMode", false);
        Object levelValue = ReflectUtils.getField(view, "mLevel");
        float level = levelValue instanceof Number ? ((Number) levelValue).floatValue() : -1f;
        int color = foregroundColor(view, config);
        boolean normal = !charging && (level < 0 || (level >= 10 && !powerSave));
        if (normal && config.cameraCircleBatteryTintEnabled) {
            applyCameraCircleRainbow(view, paint);
        } else {
            stopCameraCircleRainbow(view, paint);
            paint.setColor(color);
        }
        // setColor (including rainbow's white base) overwrites alpha, so set alpha last.
        paint.setAlpha(foregroundAlpha(config.cameraCircleBatteryTransparencyTenthPercent));
        view.invalidate();
    }

    private static void applyCameraCircleRainbow(View view, Paint paint) {
        RainbowBatteryState state = CAMERA_CIRCLE_RAINBOW_STATES.get(view);
        if (state == null) {
            state = new RainbowBatteryState();
            CAMERA_CIRCLE_RAINBOW_STATES.put(view, state);
        }
        int width = view.getWidth();
        int height = view.getHeight();
        if (state.shader == null || state.width != width || state.height != height) {
            state.width = width;
            state.height = height;
            state.shader = new SweepGradient(width / 2f, height / 2f,
                    CAMERA_CIRCLE_RAINBOW_COLORS, null);
        }
        paint.setShader(state.shader);
        paint.setColor(Color.WHITE);
        view.invalidate();
    }

    private static void stopCameraCircleRainbow(View view, Paint paint) {
        CAMERA_CIRCLE_RAINBOW_STATES.remove(view);
        paint.setShader(null);
    }

    static int foregroundColor(View view, ModuleConfig config) {
        if (ReflectUtils.getBooleanField(view, "mCharging", false)) return config.cameraCircleBatteryChargingColor;
        Object raw = ReflectUtils.getField(view, "mLevel");
        float level = raw instanceof Number ? ((Number) raw).floatValue() : -1;
        if (level >= 0) {
            if (level < 10) return config.cameraCircleBatteryLowColor;
            if (ReflectUtils.getBooleanField(view, "mLowPowerMode", false)) return config.cameraCircleBatteryPowerSaveColor;
        }
        return ReflectUtils.getBooleanField(view, "mIsDark", false)
                ? config.cameraCircleBatteryNormalLightColor : config.cameraCircleBatteryNormalDarkColor;
    }

    private static final class RainbowBatteryState {
        Shader shader;
        int width, height;
    }
}
