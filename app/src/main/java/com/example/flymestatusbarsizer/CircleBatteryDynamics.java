package com.example.flymestatusbarsizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One owner per native ring: battery state, drawing and visibility-aware frame scheduling. */
final class CircleBatteryDynamics {
    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private static boolean statusBarVisible = true;
    private static boolean drawingAvailable;
    private static final WeakHashMap<View, Boolean> FAILED = new WeakHashMap<>();

    static void enableDrawing() { drawingAvailable = true; }

    private static void fail(View view, RuntimeException error) {
        FAILED.put(view, true);
        State state = STATES.remove(view);
        if (state != null) state.dispose();
        android.util.Log.w("FlymeStatusBarSizer", "Circle animation disabled for incompatible view", error);
    }
    private static final int[] RAINBOW = {Color.RED, 0xFFFF9800, Color.YELLOW, Color.GREEN,
            Color.CYAN, Color.BLUE, 0xFF9C27B0, Color.RED};

    private CircleBatteryDynamics() { }

    static void update(Object value, ModuleConfig config) {
        if (!(value instanceof View) || !drawingAvailable) return;
        View view = (View) value;
        if (!config.enabled || !config.cameraCircleBatteryEnabled) {
            State old = STATES.remove(view);
            if (old != null) old.dispose();
            return;
        }
        if (FAILED.containsKey(view)) return;
        try {
            State state = STATES.get(view);
            if (state == null) {
                Object rect = ReflectUtils.getField(view, "mRectF");
                Object paint = ReflectUtils.getField(view, "mPaint");
                Object background = ReflectUtils.getField(view, "mBgPaint");
                if (!(rect instanceof RectF) || !(paint instanceof Paint) || !(background instanceof Paint)) return;
                state = new State(view, (RectF) rect, (Paint) paint, (Paint) background);
                STATES.put(view, state);
                state.attach();
            }
            state.update(config);
        } catch (RuntimeException error) {
            fail(view, error);
        }
    }

    static boolean draw(Object value, Canvas canvas) {
        State state = STATES.get(value);
        if (state == null || state.config == null) return false;
        try {
            state.reconcile();
            state.draw(canvas, SystemClock.uptimeMillis());
            return true;
        } catch (RuntimeException error) {
            fail((View) value, error);
            return false;
        }
    }

    static void setStatusBarVisible(boolean visible) {
        statusBarVisible = visible;
        for (State state : STATES.values()) state.reconcile();
    }

    // Package-visible for lifecycle regression tests; no strong reference back to the View.
    static final class State implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnGlobalLayoutListener, DisplayManager.DisplayListener {
        final WeakReference<View> view;
        final RectF bounds;
        final Paint nativePaint, background;
        final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint layerAlpha = new Paint();
        final Matrix matrix = new Matrix();
        final CircleBatteryMotion motion = new CircleBatteryMotion();
        final Runnable frame;
        final BroadcastReceiver receiver;
        ModuleConfig config;
        Shader palette, highlight;
        float centerX = Float.NaN, centerY;
        int baseColor, previousColor, unplugColor;
        boolean scheduled, active, registered;
        Context context;
        ViewTreeObserver observer;
        DisplayManager displays;
        PowerManager power;

        State(View target, RectF bounds, Paint paint, Paint background) {
            view = new WeakReference<>(target);
            this.bounds = bounds;
            nativePaint = paint;
            this.background = background;
            base.setStyle(Paint.Style.STROKE);
            base.setStrokeCap(Paint.Cap.ROUND);
            overlay.setStyle(Paint.Style.STROKE);
            overlay.setStrokeCap(Paint.Cap.ROUND);
            frame = () -> {
                scheduled = false;
                reconcile();
                View v = view.get();
                if (active && v != null) v.invalidate();
            };
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) { reconcile(); }
            };
            target.addOnAttachStateChangeListener(this);
        }

        void attach() {
            View target = view.get();
            if (target == null || !target.isAttachedToWindow() || registered) return;
            context = target.getContext().getApplicationContext();
            if (context == null) context = target.getContext();
            power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            displays = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver, filter);
            registered = true;
            if (displays != null) displays.registerDisplayListener(this, new Handler(Looper.getMainLooper()));
            observer = target.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(this);
            registered = true;
        }

        boolean visible() {
            View v = view.get();
            if (v == null || !v.isAttachedToWindow() || !v.isShown()
                    || v.getWindowVisibility() != View.VISIBLE || !statusBarVisible) return false;
            Display display = v.getDisplay();
            return (display == null || display.getState() == Display.STATE_ON)
                    && (power == null || power.isInteractive());
        }

        void update(ModuleConfig next) {
            config = next;
            long now = SystemClock.uptimeMillis();
            View v = view.get();
            if (v == null) return;
            boolean visible = visible();
            if (!visible) motion.suspend();
            float level = number(ReflectUtils.getField(v, "mLevel"));
            boolean charging = ReflectUtils.getBooleanField(v, "mCharging", false);
            boolean plugged = ReflectUtils.getBooleanField(v, "mPlugged", charging);
            boolean saving = ReflectUtils.getBooleanField(v, "mLowPowerMode", false);
            int oldEvent = motion.currentEvent(now);
            long oldStart = motion.eventStart;
            previousColor = baseColor;
            baseColor = CircleBatteryAppearance.foregroundColor(v, config) | 0xFF000000;
            motion.update(level, plugged, charging, saving, config.circleAnimation, visible, now);
            if (motion.currentEvent(now) == CircleBatteryMotion.DISCONNECT
                    && (oldEvent != CircleBatteryMotion.DISCONNECT || oldStart != motion.eventStart)) {
                unplugColor = previousColor;
            }
            rebuildShaders();
            reconcile();
            v.invalidate();
        }

        void reconcile() {
            if (config == null) return;
            boolean visible = visible();
            View v = view.get();
            if (!visible) {
                stop();
                active = false;
                motion.suspend();
                return;
            }
            if (!active) {
                // Resume at the current state, without replaying events from the hidden interval.
                motion.suspend();
                motion.update(motion.rawLevel, motion.plugged, motion.charging, motion.powerSave,
                        config.circleAnimation, true, SystemClock.uptimeMillis());
                active = true;
                if (v != null) v.invalidate();
            }
            boolean moving = CircleBatteryAppearance.foregroundAlpha(
                    config.cameraCircleBatteryTransparencyTenthPercent) > 0
                    && motion.needsFrames(config.circleAnimation, SystemClock.uptimeMillis());
            if (!moving) stop();
            else if (!scheduled && v != null) {
                scheduled = true;
                v.postDelayed(frame, 33);
            }
        }

        void stop() {
            View v = view.get();
            if (v != null) v.removeCallbacks(frame);
            scheduled = false;
        }
        void detach() {
            stop();
            active = false;
            motion.suspend();
            if (registered) {
                context.unregisterReceiver(receiver);
                if (displays != null) displays.unregisterDisplayListener(this);
                if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            }
            registered = false;
            context = null;
            displays = null;
            power = null;
            observer = null;
        }
        void dispose() {
            detach();
            View v = view.get();
            if (v != null) v.removeOnAttachStateChangeListener(this);
        }
        @Override public void onViewAttachedToWindow(View v) { attach(); CircleBatteryDynamics.update(v, ModuleConfig.load(null)); }
        @Override public void onViewDetachedFromWindow(View v) { detach(); }
        @Override public void onGlobalLayout() { reconcile(); }
        @Override public void onDisplayAdded(int id) { reconcile(); }
        @Override public void onDisplayRemoved(int id) { reconcile(); }
        @Override public void onDisplayChanged(int id) { reconcile(); }

        void rebuildShaders() {
            centerX = bounds.centerX();
            centerY = bounds.centerY();
            CircleBatteryAnimationConfig c = config.circleAnimation;
            boolean normal = !motion.charging && (motion.rawLevel < 0
                    || (motion.rawLevel >= 10 && !motion.powerSave));
            palette = !normal || c.palette == CircleBatteryAnimationConfig.STATE_COLOR ? null
                    : new SweepGradient(centerX, centerY, c.palette == CircleBatteryAnimationConfig.RAINBOW
                    ? RAINBOW : new int[]{c.colorStart, c.colorEnd, c.colorStart}, null);
            // A narrow, soft highlight; its shader is reused and only its matrix moves per frame.
            int highlightColor = contrastHighlight(baseColor);
            highlight = new SweepGradient(centerX, centerY,
                    new int[]{highlightColor & 0xFFFFFF, highlightColor, highlightColor & 0xFFFFFF,
                            highlightColor & 0xFFFFFF}, new float[]{0, 0.0625f, 0.125f, 1});
        }

        void draw(Canvas canvas, long now) {
            if (bounds.isEmpty()) return;
            if (bounds.centerX() != centerX || bounds.centerY() != centerY) rebuildShaders();
            CircleBatteryAnimationConfig c = config.circleAnimation;
            int effect = motion.effect(c);
            int event = motion.currentEvent(now);
            int alpha = CircleBatteryAppearance.foregroundAlpha(config.cameraCircleBatteryTransparencyTenthPercent);
            float level = motion.displayedLevel(now) * 3.6f;
            float phase = (now % CircleBatteryMotion.period(c, effect, motion.charging))
                    / (float) CircleBatteryMotion.period(c, effect, motion.charging);
            // Rotate the foreground geometry, leaving its battery sweep and background intact.
            float arcRotation = effect == CircleBatteryAnimationConfig.ROTATE
                    && event == CircleBatteryMotion.NONE ? phase * 360 : 0;
            float arcStart = -90 + arcRotation;
            float strength = c.strength == 0 ? 0.45f : c.strength == 2 ? 1f : 0.7f;
            float eventProgress = event == CircleBatteryMotion.NONE ? 0
                    : Math.min(1, (now - motion.eventStart) / (float) motion.eventDuration());
            float brightness = 1;
            if (event == CircleBatteryMotion.LOW) brightness = breathe((now - motion.eventStart) / 3000f, strength);
            else if (event == CircleBatteryMotion.NONE && effect == CircleBatteryAnimationConfig.BREATHE) brightness = breathe(phase, strength);
            base.setStrokeWidth(nativePaint.getStrokeWidth());
            base.setColor(baseColor);
            base.setShader(palette);
            if (palette != null) {
                float rotation = effect == CircleBatteryAnimationConfig.FLOW
                        && event == CircleBatteryMotion.NONE ? phase * 360 : arcRotation;
                matrix.setRotate(rotation - 90, centerX, centerY);
                palette.setLocalMatrix(matrix);
            }
            canvas.drawArc(bounds, -90, 360, false, background);
            if (alpha == 0) return;
            // Apply user opacity once to the composited foreground, including overlapping effects.
            layerAlpha.setAlpha(alpha);
            float padding = nativePaint.getStrokeWidth();
            int save = canvas.saveLayer(bounds.left - padding, bounds.top - padding,
                    bounds.right + padding, bounds.bottom + padding, layerAlpha);
            try {
                base.setAlpha(Math.round(255 * brightness));
                canvas.drawArc(bounds, arcStart, level, false, base);
                overlay.setStrokeWidth(nativePaint.getStrokeWidth());
                if (event == CircleBatteryMotion.DISCONNECT || event == CircleBatteryMotion.FULL) {
                    overlay.setShader(null);
                    overlay.setColor(event == CircleBatteryMotion.DISCONNECT ? unplugColor : contrastHighlight(baseColor));
                    float amount = event == CircleBatteryMotion.DISCONNECT ? 1 - eventProgress
                            : strength * 0.5f * (float) Math.sin(Math.PI * eventProgress);
                    overlay.setAlpha(Math.round(255 * amount));
                    canvas.drawArc(bounds, -90, level, false, overlay);
                } else if (event == CircleBatteryMotion.CONNECT
                        || (event == CircleBatteryMotion.NONE && effect == CircleBatteryAnimationConfig.FLOW)) {
                    matrix.setRotate((event == CircleBatteryMotion.CONNECT ? eventProgress : phase) * 360 - 90, centerX, centerY);
                    highlight.setLocalMatrix(matrix);
                    overlay.setShader(highlight);
                    overlay.setColor(Color.WHITE);
                    overlay.setAlpha(Math.round(255 * strength));
                    canvas.drawArc(bounds, -90, level, false, overlay);
                } else if (event == CircleBatteryMotion.NONE && effect == CircleBatteryAnimationConfig.COMET) {
                    overlay.setShader(null);
                    overlay.setColor(contrastHighlight(baseColor));
                    // Draw the faint tail first, bright head last. No luminous full-circle track.
                    for (int i = 0; i < 24; i++) {
                        overlay.setAlpha(Math.round(255 * strength * (i + 1) / 24f));
                        canvas.drawArc(bounds, phase * 360 - 90 - 60 + i * 2.5f, 2.5f, false, overlay);
                    }
                }
            } finally {
                canvas.restoreToCount(save);
            }
        }
        private static float number(Object value) { return value instanceof Number ? ((Number) value).floatValue() : -1; }
        private static float breathe(float phase, float strength) {
            return 1 - 0.5f * strength * (0.5f - 0.5f * (float) Math.cos(phase * Math.PI * 2));
        }
        private static int contrastHighlight(int color) {
            float luminance = Color.red(color) * 0.299f + Color.green(color) * 0.587f + Color.blue(color) * 0.114f;
            return mix(color, luminance > 200 ? Color.DKGRAY : Color.WHITE, 0.7f);
        }
        private static int mix(int a, int b, float t) {
            return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                    Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                    Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
        }
    }
}
