package com.example.flymestatusbarsizer.feature.signal;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Looper;
import android.widget.FrameLayout;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class SignalRenderingTest {
    @After
    public void restorePainterDefaults() {
        SignalPreviewPainter.configureStyle(new ModuleConfig());
    }

    @Test
    public void shortBarsKeepCircularCornersAndStayInsideBoundsAtEverySize() {
        ModuleConfig style = new ModuleConfig();
        for (int size : new int[]{1, 11, 22, 66, 220}) {
            Rect bounds = new Rect(7, 13, 7 + size, 13 + size);
            for (int height : new int[]{0, 1, 10, 38, 100}) {
                style.signalBar1HeightPercent = height;
                style.signalBar2HeightPercent = height;
                style.signalBar3HeightPercent = height;
                for (int corner : new int[]{0, 25, 50, 100}) {
                    style.signalBarCornerRadiusPercent = corner;
                    style.signalDotCornerRadiusPercent = corner;
                    SignalPreviewPainter.configureStyle(style);
                    for (boolean dual : new boolean[]{false, true}) {
                        RecordingCanvas canvas = draw(bounds, dual);
                        assertEquals((height == 0 ? 1 : 4) + (dual ? 4 : 0),
                                canvas.shapes.size());
                        for (Shape shape : canvas.shapes) {
                            assertTrue(shape.right > shape.left);
                            assertTrue(shape.bottom > shape.top);
                            assertTrue(shape.left >= bounds.left && shape.right <= bounds.right);
                            assertTrue(shape.top >= bounds.top && shape.bottom <= bounds.bottom);
                            float maxRadius = Math.min(shape.right - shape.left,
                                    shape.bottom - shape.top) / 2f;
                            assertEquals(shape.rx, shape.ry, 0f);
                            assertTrue(shape.rx <= maxRadius + 0.0001f);
                            if (corner == 0) assertEquals(0f, shape.rx, 0f);
                            if (corner == 100) assertEquals(maxRadius, shape.rx, 0.0001f);
                            assertTrue(shape.antiAlias);
                        }
                    }
                }
            }
            assertEquals(new Rect(7, 13, 7 + size, 13 + size), bounds);
        }
    }

    @Test
    public void switchingBetweenSingleAndDualKeepsColumnWidthSpacingAndVisualBand() {
        SignalPreviewPainter.configureStyle(new ModuleConfig());
        for (int size : new int[]{1, 22, 66, 220}) {
            Rect bounds = new Rect(0, 0, size, size);
            List<Shape> single = draw(bounds, false).shapes;
            List<Shape> dual = draw(bounds, true).shapes;
            assertEquals(single.get(3).top, dual.get(3).top, 0.0001f);
            for (int i = 0; i < 4; i++) {
                Shape bar = dual.get(i);
                Shape dot = dual.get(i + 4);
                assertEquals(single.get(i).left, bar.left, 0.0001f);
                assertEquals(single.get(i).right, bar.right, 0.0001f);
                assertEquals(bar.left, dot.left, 0.0001f);
                assertEquals(bar.right, dot.right, 0.0001f);
                assertEquals(single.get(i).bottom, dot.bottom, 0.0001f);
                assertTrue("dots must remain separate from bars", bar.bottom < dot.top);
            }
        }
    }

    @Test
    public void hidingAColumnPreservesSignalLevelMappingTintAndIndependentSimLevels() {
        ModuleConfig style = new ModuleConfig();
        style.signalBar1HeightPercent = 0;
        SignalPreviewPainter.configureStyle(style);
        ColorFilter filter = new PorterDuffColorFilter(0xff557799, PorterDuff.Mode.SRC_IN);
        for (int primary = 0; primary <= 4; primary++) {
            for (int secondary = 0; secondary <= 4; secondary++) {
                RecordingCanvas canvas = new RecordingCanvas();
                SignalPreviewPainter.drawMergedDualSim(canvas, new Rect(0, 0, 66, 66),
                        0x80557799, filter, SignalPreviewPainter.MOBILE_TYPE_BADGE_NONE,
                        primary, secondary);
                assertEquals(7, canvas.shapes.size());
                for (int i = 0; i < 7; i++) {
                    Shape shape = canvas.shapes.get(i);
                    boolean active = i < 3 ? i + 1 < primary : i - 3 < secondary;
                    assertEquals(active ? 112 : 34, shape.color >>> 24);
                    assertEquals(0x00557799, shape.color & 0x00ffffff);
                    assertSame(filter, shape.filter);
                }
            }
        }
        for (Shape shape : draw(new Rect(0, 0, 66, 66), true).shapes) {
            assertNull(shape.filter);
        }
    }

    @Test
    public void importedOutOfRangeAppearanceIsClampedAndZeroHeightColumnsAreHidden() {
        ModuleConfig style = new ModuleConfig();
        style.signalBar1HeightPercent = Integer.MIN_VALUE;
        style.signalBar2HeightPercent = Integer.MAX_VALUE;
        style.signalBar3HeightPercent = 100;
        style.signalBarCornerRadiusPercent = Integer.MAX_VALUE;
        style.signalDotCornerRadiusPercent = Integer.MIN_VALUE;
        SignalPreviewPainter.configureStyle(style);
        List<Shape> shapes = draw(new Rect(0, 0, 66, 66), true).shapes;
        assertEquals(7, shapes.size());
        for (int i = 0; i < 3; i++) {
            assertEquals(shapes.get(2).top, shapes.get(i).top, 0f);
            assertTrue(shapes.get(i).rx > 0f);
        }
        for (int i = 3; i < 7; i++) assertEquals(0f, shapes.get(i).rx, 0f);
    }

    @Test
    public void previewRefreshesAllAppearanceSettingsAndReloadsWhenReattached() {
        try (ActivityController<Activity> controller = Robolectric.buildActivity(Activity.class)
                .setup()) {
            Activity activity = controller.get();
            SharedPreferences prefs = activity.getSharedPreferences("signal-preview-test",
                    Context.MODE_PRIVATE);
            prefs.edit().clear().commit();
            FrameLayout host = new FrameLayout(activity);
            activity.setContentView(host);
            SignalAppearancePreviewView preview = new SignalAppearancePreviewView(activity,
                    prefs, 0xff123456);
            host.addView(preview);
            assertTrue(preview.isAttachedToWindow());
            List<Shape> defaults = draw(preview).shapes;
            assertEquals(12, defaults.size());

            for (String key : new String[]{SettingsStore.KEY_SIGNAL_BAR1_HEIGHT_PERCENT,
                    SettingsStore.KEY_SIGNAL_BAR2_HEIGHT_PERCENT,
                    SettingsStore.KEY_SIGNAL_BAR3_HEIGHT_PERCENT,
                    SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT,
                    SettingsStore.KEY_SIGNAL_DOT_CORNER_RADIUS_PERCENT}) {
                prefs.edit().putInt(key, 0).commit();
                shadowOf(Looper.getMainLooper()).idle();
                assertNotEquals("preview must react to " + key, defaults, draw(preview).shapes);
                prefs.edit().remove(key).commit();
                shadowOf(Looper.getMainLooper()).idle();
                assertEquals(defaults, draw(preview).shapes);
            }

            ModuleConfig otherPreviewStyle = new ModuleConfig();
            otherPreviewStyle.signalBarCornerRadiusPercent = 0;
            SignalPreviewPainter.configureStyle(otherPreviewStyle);
            assertEquals("another preview must not overwrite this preview's appearance",
                    defaults, draw(preview).shapes);

            host.removeView(preview);
            assertFalse(preview.isAttachedToWindow());
            prefs.edit().putInt(SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT, 0).commit();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals("detached preview should stop observing preferences",
                    defaults, draw(preview).shapes);
            host.addView(preview);
            assertNotEquals(defaults, draw(preview).shapes);
            // The default button writes an explicit value. Android 9 does not notify
            // preference listeners for clear(), unlike Android 11 and newer.
            prefs.edit().putInt(SettingsStore.KEY_SIGNAL_BAR_CORNER_RADIUS_PERCENT,
                    SettingsStore.DEFAULT_SIGNAL_BAR_CORNER_RADIUS_PERCENT).commit();
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(defaults, draw(preview).shapes);
        }
    }

    private static RecordingCanvas draw(Rect bounds, boolean dual) {
        RecordingCanvas canvas = new RecordingCanvas();
        if (dual) SignalPreviewPainter.drawMergedDualSim(canvas, bounds, 0xffffffff);
        else SignalPreviewPainter.drawSingleSim(canvas, bounds, 0xffffffff);
        return canvas;
    }

    private static RecordingCanvas draw(SignalAppearancePreviewView preview) {
        // Use the same viewport after queued activity layout passes and reattachment.
        preview.layout(0, 0, 360, 112);
        RecordingCanvas canvas = new RecordingCanvas();
        preview.draw(canvas);
        return canvas;
    }

    private record Shape(float left, float top, float right, float bottom,
            float rx, float ry, int color, boolean antiAlias, ColorFilter filter) { }

    private static final class RecordingCanvas extends Canvas {
        final List<Shape> shapes = new ArrayList<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            shapes.add(new Shape(rect.left, rect.top, rect.right, rect.bottom, rx, ry,
                    paint.getColor(), paint.isAntiAlias(), paint.getColorFilter()));
        }
    }
}
