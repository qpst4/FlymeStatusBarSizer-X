package com.example.flymestatusbarsizer;

import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Applies foreground colors without changing Flyme's native palette or background ring. */
final class CircleBatteryAppearance {
    private static final long CAMERA_CIRCLE_RAINBOW_REFRESH_MS = 100L;
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

    static void apply(Object value, ModuleConfig config) {
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
        boolean darkIcons = ReflectUtils.getBooleanField(view, "mIsDark", false);
        boolean charging = ReflectUtils.getBooleanField(view, "mCharging", false);
        boolean powerSave = ReflectUtils.getBooleanField(view, "mLowPowerMode", false);
        Object levelValue = ReflectUtils.getField(view, "mLevel");
        float level = levelValue instanceof Number ? ((Number) levelValue).floatValue() : -1f;
        int color = darkIcons ? config.cameraCircleBatteryNormalLightColor
                : config.cameraCircleBatteryNormalDarkColor;
        boolean normal = true;
        // Match Flyme: charging > critical (<10%) > power saving > normal.
        // Unknown battery levels keep the normal palette unless charging.
        if (charging) {
            color = config.cameraCircleBatteryChargingColor;
            normal = false;
        } else if (level >= 0f) {
            if (level < 10f) {
                color = config.cameraCircleBatteryLowColor;
                normal = false;
            } else if (powerSave) {
                color = config.cameraCircleBatteryPowerSaveColor;
                normal = false;
            }
        }
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
            state = new RainbowBatteryState(view);
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
        state.matrix.setRotate(state.angle, width / 2f, height / 2f);
        state.shader.setLocalMatrix(state.matrix);
        paint.setShader(state.shader);
        paint.setColor(Color.WHITE);
        view.invalidate();
        if (!state.scheduled) {
            state.scheduled = true;
            view.postDelayed(state.refresh, CAMERA_CIRCLE_RAINBOW_REFRESH_MS);
        }
    }

    private static void stopCameraCircleRainbow(View view, Paint paint) {
        RainbowBatteryState state = CAMERA_CIRCLE_RAINBOW_STATES.remove(view);
        if (state != null) {
            view.removeCallbacks(state.refresh);
        }
        paint.setShader(null);
    }

    private static final class RainbowBatteryState {
        final WeakReference<View> view;
        final Matrix matrix = new Matrix();
        final Runnable refresh;
        Shader shader;
        int width;
        int height;
        float angle;
        boolean scheduled;

        RainbowBatteryState(View view) {
            this.view = new WeakReference<>(view);
            this.refresh = () -> {
                View target = this.view.get();
                scheduled = false;
                if (target == null || target.getVisibility() != View.VISIBLE) {
                    return;
                }
                angle = (angle + 12f) % 360f;
                apply(target, ModuleConfig.load(null));
            };
        }
    }

}
