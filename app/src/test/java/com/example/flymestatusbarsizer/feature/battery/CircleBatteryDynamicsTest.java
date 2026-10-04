package com.example.flymestatusbarsizer.feature.battery;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.widget.FrameLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class CircleBatteryDynamicsTest {
    ActivityController<Activity> controller;
    FrameLayout parent;
    Ring ring;
    ModuleConfig config;

    @Before public void setUp() {
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        parent = new FrameLayout(activity);
        ring = new Ring(activity);
        parent.addView(ring, new FrameLayout.LayoutParams(40, 40));
        activity.setContentView(parent);
        controller.visible();
        ring.layout(0, 0, 40, 40);
        // Robolectric attaches the window without a real WindowManager visibility transaction.
        Object attachInfo = org.robolectric.util.ReflectionHelpers.getField(ring, "mAttachInfo");
        org.robolectric.util.ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE);
        Shadows.shadowOf(ring.getDisplay()).setState(android.view.Display.STATE_ON);
        Shadows.shadowOf((PowerManager) activity.getSystemService(Context.POWER_SERVICE)).setIsInteractive(true);
        config = new ModuleConfig();
        config.cameraCircleBatteryEnabled = true;
        CircleBatteryDynamics.enableDrawing();
        CircleBatteryDynamics.setStatusBarVisible(true);
        CircleBatteryDynamics.update(ring, config);
    }
    @After public void tearDown() {
        config.cameraCircleBatteryEnabled = false;
        CircleBatteryDynamics.update(ring, config);
        controller.pause().stop().destroy();
    }

    @Test public void ancestorsWindowScreenOpacityAndDetachStopAndResumeScheduling() throws Exception {
        CircleBatteryDynamics.State state = state();
        assertTrue(ring.isAttachedToWindow());
        assertTrue("shown=" + ring.isShown() + ", window=" + ring.getWindowVisibility()
                + ", display=" + ring.getDisplay().getState() + ", interactive=" + state.power.isInteractive(), state.scheduled);
        parent.setVisibility(View.GONE);
        state.onGlobalLayout();
        assertFalse(state.scheduled);
        assertFalse(state.active);
        parent.setVisibility(View.VISIBLE);
        state.onGlobalLayout();
        assertTrue(state.scheduled);
        CircleBatteryDynamics.setStatusBarVisible(false);
        assertFalse(state.scheduled);
        CircleBatteryDynamics.setStatusBarVisible(true);
        assertTrue(state.scheduled);
        PowerManager power = (PowerManager) ring.getContext().getSystemService(Context.POWER_SERVICE);
        Shadows.shadowOf(power).setIsInteractive(false);
        state.onDisplayChanged(0);
        assertFalse(state.scheduled);
        Shadows.shadowOf(power).setIsInteractive(true);
        state.onDisplayChanged(0);
        assertTrue(state.scheduled);
        config.cameraCircleBatteryTransparencyTenthPercent = 1000;
        CircleBatteryDynamics.update(ring, config);
        assertFalse(state.scheduled);
        config.cameraCircleBatteryTransparencyTenthPercent = 0;
        CircleBatteryDynamics.update(ring, config);
        assertTrue(state.scheduled);
        parent.removeView(ring);
        assertFalse(state.scheduled);
        assertFalse(state.registered);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS);
        assertFalse(state.scheduled);
    }

    @Test public void disablingAnimationAndModuleLeavesNoCallbacksOrCustomDraw() throws Exception {
        CircleBatteryDynamics.State state = state();
        for (int i = 0; i < 10; i++) CircleBatteryDynamics.update(ring, config);
        assertSame(state, state());
        config.circleAnimation.enabled = false;
        CircleBatteryDynamics.update(ring, config);
        assertFalse(state.scheduled);
        config.enabled = false;
        CircleBatteryDynamics.update(ring, config);
        assertFalse(state.registered);
        assertFalse(CircleBatteryDynamics.draw(ring, new Canvas()));
        assertNull(state());
    }

    @Test public void cometRetainsBatterySweepAndAppliesOpacityOnceToForeground() throws Exception {
        ring.mCharging = ring.mPlugged = true;
        config.circleAnimation.events = false;
        config.cameraCircleBatteryTransparencyTenthPercent = 500;
        CircleBatteryDynamics.update(ring, config);
        CircleBatteryDynamics.State state = state();
        RecordingCanvas first = new RecordingCanvas();
        state.draw(first, 2500);
        assertEquals(26, first.sweeps.size());
        assertEquals(360, first.sweeps.get(0), 0);
        assertEquals(180, first.sweeps.get(1), 0);
        assertEquals(128, first.layerAlpha);
        assertEquals(1, first.restores);
        assertEquals(30, ring.mBgPaint.getAlpha());
        RecordingCanvas next = new RecordingCanvas();
        state.draw(next, 3125);
        assertEquals(first.sweeps.get(1), next.sweeps.get(1));
        assertNotEquals(first.starts.get(2), next.starts.get(2));
        config.cameraCircleBatteryTransparencyTenthPercent = 1000;
        CircleBatteryDynamics.update(ring, config);
        RecordingCanvas hidden = new RecordingCanvas();
        state.draw(hidden, 3200);
        assertEquals(1, hidden.sweeps.size());
        assertEquals(0, hidden.restores);
    }

    @Test public void missingNativeGeometryFallsBackWithoutScheduling() {
        View unsupported = new View(controller.get());
        CircleBatteryDynamics.update(unsupported, config);
        assertFalse(CircleBatteryDynamics.draw(unsupported, new Canvas()));
    }

    @Test public void rotationMovesArcAndPaletteTogetherButKeepsBackgroundAndBatterySweep() throws Exception {
        config.circleAnimation.normal = config.circleAnimation.charging = CircleBatteryAnimationConfig.ROTATE;
        config.circleAnimation.palette = CircleBatteryAnimationConfig.TWO_COLORS;
        config.circleAnimation.events = false;
        CircleBatteryDynamics.update(ring, config);
        CircleBatteryDynamics.State state = state();
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        for (int i = 0; i < 3; i++) {
            RecordingCanvas canvas = new RecordingCanvas();
            state.draw(canvas, 3000 + i * 750);
            assertEquals(2, canvas.sweeps.size());
            assertEquals(-90, canvas.starts.get(0), 0);
            assertEquals(360, canvas.sweeps.get(0), 0);
            assertEquals(-90 + i * 90, canvas.starts.get(1), 0);
            assertEquals(180, canvas.sweeps.get(1), 0);
            matrix.reset();
            // Shader may omit an identity matrix at the quarter-turn position.
            state.palette.getLocalMatrix(matrix);
            float[] point = {21, 20};
            matrix.mapPoints(point);
            float[][] expected = {{20, 19}, {21, 20}, {20, 21}};
            assertArrayEquals(expected[i], point, 0.001f);
        }
        config.circleAnimation.events = true;
        state.motion.update(50, true, true, false, config.circleAnimation, true, 5000);
        RecordingCanvas feedback = new RecordingCanvas();
        state.draw(feedback, 5250);
        assertEquals(-90, feedback.starts.get(1), 0);
        RecordingCanvas resumed = new RecordingCanvas();
        state.draw(resumed, 6000);
        assertEquals(2, resumed.sweeps.size());
        assertEquals(180, resumed.sweeps.get(1), 0);
    }

    private CircleBatteryDynamics.State state() throws Exception {
        Field field = CircleBatteryDynamics.class.getDeclaredField("STATES");
        field.setAccessible(true);
        return (CircleBatteryDynamics.State) ((Map<?, ?>) field.get(null)).get(ring);
    }
    public static final class Ring extends View {
        public final RectF mRectF = new RectF(2, 2, 38, 38);
        public final Paint mPaint = new Paint();
        public final Paint mBgPaint = new Paint();
        public float mLevel = 50;
        public boolean mCharging, mPlugged, mLowPowerMode, mIsDark;
        Ring(Context context) {
            super(context);
            mPaint.setStrokeWidth(4);
            mBgPaint.setAlpha(30);
        }
    }
    static final class RecordingCanvas extends Canvas {
        final ArrayList<Float> starts = new ArrayList<>(), sweeps = new ArrayList<>();
        int layerAlpha, restores;
        @Override public int saveLayer(float left, float top, float right, float bottom, Paint paint) {
            layerAlpha = paint.getAlpha();
            return 1;
        }
        @Override public void restoreToCount(int count) { restores++; }
        @Override public void drawArc(RectF oval, float start, float sweep, boolean center, Paint paint) {
            starts.add(start);
            sweeps.add(sweep);
        }
    }
}
