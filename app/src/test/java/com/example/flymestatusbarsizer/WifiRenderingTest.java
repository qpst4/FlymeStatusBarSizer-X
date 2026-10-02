package com.example.flymestatusbarsizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.util.ArrayList;
import java.util.List;

/** Compares exact Canvas commands, including paint properties, against the pre-refactor style. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF) // Drawing tests do not need host-specific TLS native libraries.
public final class WifiRenderingTest {
    @Test
    public void rendererMatchesLegacyForSingleAndDualWifi() {
        WifiIconRenderer renderer = new ClassicWifiRenderer();
        int[] levels = {-1, 0, 1, 2, 3, 4, 9};
        int[] colors = {0xffffffff, 0xff000000, 0x80224466};
        int[] alphas = {0, 127, 255};
        float[] offsets = {-6f, -0.75f, 0f, 0.5f, 8f};
        int cases = 0;
        for (int height : new int[]{1, 11, 22, 24, 44, 66}) {
            for (boolean dual : new boolean[]{false, true}) {
                int oldWidth = dual ? Math.max(height, Math.round(height
                        * LegacyWifiDrawing.resolveMergedBoxWidthRatio())) : height;
                assertEquals(oldWidth, renderer.measureWidth(height, dual));
                Rect bounds = new Rect(7, 13, 7 + oldWidth, 13 + height);
                for (float offset : offsets) {
                    for (int level : levels) {
                        for (int secondary : dual ? levels : new int[]{0}) {
                            for (int color : colors) {
                                for (int alpha : alphas) {
                                    RecordingCanvas expected = new RecordingCanvas();
                                    RecordingCanvas actual = new RecordingCanvas();
                                    LegacyWifiDrawing.drawPreview(expected, bounds, color, alpha,
                                            null, level, dual, secondary, offset);
                                    renderer.draw(actual, bounds, color, alpha,
                                            null, level, dual, secondary, offset);
                                    assertEquals("height=" + height + ", dual=" + dual
                                                    + ", offset=" + offset + ", level=" + level
                                                    + ", secondary=" + secondary + ", color=" + color
                                                    + ", alpha=" + alpha,
                                            expected.arcs, actual.arcs);
                                    assertEquals(dual ? 6 : 3, actual.arcs.size());
                                    cases++;
                                }
                            }
                        }
                    }
                }
            }
        }
        assertEquals(15120, cases);
    }

    @Test
    public void drawableAndPreviewUseIdenticalDrawingInputs() {
        WifiIconRenderer preview = new ClassicWifiRenderer();
        ColorFilter filter = new PorterDuffColorFilter(0xff557799, PorterDuff.Mode.SRC_IN);
        for (Rect bounds : new Rect[]{new Rect(), new Rect(3, 5, 25, 27),
                new Rect(3, 5, 69, 49), new Rect(3, 5, 14, 49)}) {
            for (boolean dual : new boolean[]{false, true}) {
                for (float offset : new float[]{-2.5f, 0f, 3.75f}) {
                    WifiIconDrawable drawable = drawable(1, dual, 4, offset);
                    drawable.setBounds(bounds);
                    drawable.setTintList(ColorStateList.valueOf(0x80557799));
                    drawable.setAlpha(123);
                    drawable.setColorFilter(filter);
                    RecordingCanvas actual = new RecordingCanvas();
                    drawable.draw(actual);
                    RecordingCanvas expected = new RecordingCanvas();
                    LegacyWifiDrawing.drawPreview(expected, bounds, 0x80557799, 123,
                            filter, 1, dual, 4, offset);
                    assertEquals(expected.arcs, actual.arcs);
                    RecordingCanvas previewCanvas = new RecordingCanvas();
                    preview.draw(previewCanvas, bounds, 0x80557799, 123,
                            filter, 1, dual, 4, offset);
                    assertEquals(expected.arcs, previewCanvas.arcs);
                }
            }
        }
    }

    @Test
    public void offsetOnlyUpdateInvalidatesReusedDrawable() {
        WifiIconDrawable drawable = drawable(1, true, 4, 0f);
        drawable.setBounds(0, 0, 44, 44);
        Counter callback = new Counter();
        drawable.setCallback(callback);
        RecordingCanvas before = new RecordingCanvas();
        drawable.draw(before);
        assertTrue(drawable.setStateValues(1, true, 4, 2.5f));
        assertEquals(1, callback.invalidations);
        assertFalse(drawable.setStateValues(1, true, 4, 2.5f));
        assertEquals(1, callback.invalidations);
        assertTrue(drawable.matchesGeometry(22, 22, 22));
        RecordingCanvas after = new RecordingCanvas();
        drawable.draw(after);
        assertEquals(before.arcs.size(), after.arcs.size());
        for (int i = 0; i < before.arcs.size(); i++) {
            assertEquals(before.arcs.get(i).top - 2.5f, after.arcs.get(i).top, 0.00001f);
            assertEquals(before.arcs.get(i).left, after.arcs.get(i).left, 0f);
        }
    }

    @Test
    public void drawableKeepsStatefulTintAndAlpha() {
        WifiIconDrawable drawable = drawable(0, false, 0, 0f);
        drawable.setBounds(0, 0, 22, 22);
        int selected = android.R.attr.state_selected;
        drawable.setTintList(new ColorStateList(new int[][]{{selected}, {}},
                new int[]{0x80ffffff, 0xff000000}));
        drawable.setAlpha(128);
        assertTrue(drawable.isStateful());
        for (int[] state : new int[][]{{selected}, {}}) {
            drawable.setState(state);
            RecordingCanvas actual = new RecordingCanvas();
            drawable.draw(actual);
            RecordingCanvas expected = new RecordingCanvas();
            LegacyWifiDrawing.drawPreview(expected, drawable.getBounds(),
                    state.length == 0 ? 0xff000000 : 0x80ffffff, 128, null, 0, false, 0, 0f);
            assertEquals(expected.arcs, actual.arcs);
        }
        drawable.setTintList(null);
        drawable.setAlpha(999);
        assertEquals(255, drawable.getAlpha());
        RecordingCanvas actual = new RecordingCanvas();
        drawable.draw(actual);
        RecordingCanvas expected = new RecordingCanvas();
        LegacyWifiDrawing.drawPreview(expected, drawable.getBounds(), 0xffffffff,
                255, null, 0, false, 0, 0f);
        assertEquals(expected.arcs, actual.arcs);
    }

    @Test
    public void drawingDoesNotMutateCallerBoundsOrLeakPaintBetweenInstances() {
        WifiIconRenderer first = new ClassicWifiRenderer();
        WifiIconRenderer second = new ClassicWifiRenderer();
        Rect bounds = new Rect(7, 13, 51, 57);
        RecordingCanvas before = new RecordingCanvas();
        first.draw(before, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        RecordingCanvas after = new RecordingCanvas() {
            boolean nested;

            @Override
            public void drawArc(RectF oval, float start, float sweep, boolean useCenter, Paint paint) {
                if (!nested) {
                    nested = true;
                    // A nested render must not overwrite the first renderer's Paint or RectF.
                    second.draw(new RecordingCanvas(), new Rect(0, 0, 22, 22), 0x40000000, 40,
                            new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.SRC_IN),
                            0, false, 0, -4f);
                }
                super.drawArc(oval, start, sweep, useCenter, paint);
            }
        };
        first.draw(after, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        assertEquals(before.arcs, after.arcs);
        assertEquals(new Rect(7, 13, 51, 57), bounds);
    }

    private static WifiIconDrawable drawable(int level, boolean dual, int secondary, float offset) {
        return new WifiIconDrawable(new ClassicWifiRenderer(), 22, 22, 22,
                level, dual, secondary, offset);
    }

    private static final class Counter implements Drawable.Callback {
        int invalidations;

        @Override public void invalidateDrawable(Drawable who) { invalidations++; }
        @Override public void scheduleDrawable(Drawable who, Runnable what, long when) { }
        @Override public void unscheduleDrawable(Drawable who, Runnable what) { }
    }

    private record Arc(float left, float top, float right, float bottom, float start, float sweep,
            boolean useCenter, int color, float strokeWidth, Paint.Style style, Paint.Cap cap,
            boolean antiAlias, ColorFilter filter) { }

    private static class RecordingCanvas extends Canvas {
        final List<Arc> arcs = new ArrayList<>();

        @Override
        public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter,
                Paint paint) {
            arcs.add(new Arc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle,
                    useCenter, paint.getColor(), paint.getStrokeWidth(), paint.getStyle(),
                    paint.getStrokeCap(), paint.isAntiAlias(), paint.getColorFilter()));
        }
    }
}
