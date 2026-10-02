package com.example.flymestatusbarsizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Path;
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

/** Verifies rounded geometry and preserves legacy layout, tint and signal mapping. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF) // Drawing tests do not need host-specific TLS native libraries.
public final class WifiRenderingTest {
    @Test
    public void roundedRendererPreservesLegacyLayoutAndColors() {
        WifiIconRenderer renderer = WifiIconStyles.createRenderer(WifiIconStyles.CLASSIC);
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
                                            colors(expected), colors(actual));
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
    public void unknownStylesFallBackToClassic() {
        RecordingCanvas expected = new RecordingCanvas();
        Rect bounds = new Rect(0, 0, 44, 44);
        LegacyWifiDrawing.drawPreview(expected, bounds, 0xffffffff, 255,
                null, 1, true, 4, 0f);
        for (int styleId : new int[]{WifiIconStyles.CLASSIC, -1, 99, Integer.MAX_VALUE}) {
            assertEquals(WifiIconStyles.CLASSIC, WifiIconStyles.normalize(styleId));
            WifiIconRenderer renderer = WifiIconStyles.createRenderer(styleId);
            assertEquals(WifiIconStyles.CLASSIC, renderer.getStyleId());
            RecordingCanvas actual = new RecordingCanvas();
            renderer.draw(actual, bounds, 0xffffffff, 255, null, 1, true, 4, 0f);
            assertEquals(colors(expected), colors(actual));
        }
    }

    @Test
    public void drawableReuseRequiresSameStyleAndGeometry() {
        WifiIconDrawable drawable = drawable(1, false, 0, 0f);
        assertTrue(drawable.matchesConfiguration(WifiIconStyles.CLASSIC, 22, 22, 22));
        // A future style with the same dimensions must replace the old drawable.
        assertFalse(drawable.matchesConfiguration(99, 22, 22, 22));
        assertFalse(drawable.matchesConfiguration(WifiIconStyles.CLASSIC, 23, 22, 22));
        assertFalse(drawable.matchesConfiguration(WifiIconStyles.CLASSIC, 22, 23, 22));
        assertFalse(drawable.matchesConfiguration(WifiIconStyles.CLASSIC, 22, 22, 23));
    }

    @Test
    public void singleAndDualWifiTransitionsReuseDrawableAndMeasureMatchingWidth() {
        WifiIconDrawable drawable = drawable(1, false, 0, 0f);
        Counter callback = new Counter();
        drawable.setCallback(callback);
        WifiIconRenderer renderer = drawable.getRenderer();
        for (boolean dual : new boolean[]{true, false, true}) {
            assertTrue(drawable.setStateValues(1, dual, 4, 0f));
            int expectedWidth = dual ? Math.max(44, Math.round(44
                    * LegacyWifiDrawing.resolveMergedBoxWidthRatio())) : 44;
            assertEquals(expectedWidth, renderer.measureWidth(44, dual));
            drawable.setBounds(0, 0, expectedWidth, 44);
            RecordingCanvas actual = new RecordingCanvas();
            drawable.draw(actual);
            RecordingCanvas expected = new RecordingCanvas();
            LegacyWifiDrawing.drawPreview(expected, drawable.getBounds(), 0xffffffff, 255,
                    null, 1, dual, 4, 0f);
            assertEquals(colors(expected), colors(actual));
            assertTrue(drawable.matchesConfiguration(WifiIconStyles.CLASSIC, 22, 22, 22));
            int invalidations = callback.invalidations;
            assertFalse(drawable.setStateValues(1, dual, 4, 0f));
            assertEquals(invalidations, callback.invalidations);
        }
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
                    assertEquals(colors(expected), colors(actual));
                    RecordingCanvas previewCanvas = new RecordingCanvas();
                    preview.draw(previewCanvas, bounds, 0x80557799, 123,
                            filter, 1, dual, 4, offset);
                    assertEquals(colors(expected), colors(previewCanvas));
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
        assertTrue(drawable.matchesConfiguration(WifiIconStyles.CLASSIC, 22, 22, 22));
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
            assertEquals(colors(expected), colors(actual));
        }
        drawable.setTintList(null);
        drawable.setAlpha(999);
        assertEquals(255, drawable.getAlpha());
        RecordingCanvas actual = new RecordingCanvas();
        drawable.draw(actual);
        RecordingCanvas expected = new RecordingCanvas();
        LegacyWifiDrawing.drawPreview(expected, drawable.getBounds(), 0xffffffff,
                255, null, 0, false, 0, 0f);
        assertEquals(colors(expected), colors(actual));
    }

    @Test
    public void drawingDoesNotMutateCallerBoundsOrLeakPaintBetweenInstances() {
        WifiIconRenderer first = WifiIconStyles.createRenderer(WifiIconStyles.CLASSIC);
        WifiIconRenderer second = WifiIconStyles.createRenderer(WifiIconStyles.CLASSIC);
        assertNotSame(first, second);
        Rect bounds = new Rect(7, 13, 51, 57);
        RecordingCanvas before = new RecordingCanvas();
        first.draw(before, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        RecordingCanvas after = new RecordingCanvas() {
            boolean nested;

            @Override
            public void drawPath(Path path, Paint paint) {
                if (!nested) {
                    nested = true;
                    // A nested render must not overwrite the first renderer's Paint or RectF.
                    second.draw(new RecordingCanvas(), new Rect(0, 0, 22, 22), 0x40000000, 40,
                            new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.SRC_IN),
                            0, false, 0, -4f);
                }
                super.drawPath(path, paint);
            }
        };
        first.draw(after, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        assertEquals(before.arcs, after.arcs);
        assertEquals(new Rect(7, 13, 51, 57), bounds);
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void roundedPixelsStayInsideLegacyShapeAndRetainSymmetry() {
        org.junit.Assume.assumeFalse("Robolectric native graphics does not support Linux ARM64",
                System.getProperty("os.name").toLowerCase().contains("linux")
                        && System.getProperty("os.arch").equals("aarch64"));
        for (int height : new int[]{22, 66, 220}) {
            for (boolean dual : new boolean[]{false, true}) {
                WifiIconRenderer renderer = new ClassicWifiRenderer();
                int width = renderer.measureWidth(height, dual);
                Rect bounds = new Rect(8, 8, 8 + width, 8 + height);
                Bitmap old = Bitmap.createBitmap(width + 16, height + 16, Bitmap.Config.ARGB_8888);
                Bitmap rounded = Bitmap.createBitmap(width + 16, height + 16, Bitmap.Config.ARGB_8888);
                LegacyWifiDrawing.drawPreview(new Canvas(old), bounds, 0xffffffff, 255,
                        null, 4, dual, 4, 0f);
                renderer.draw(new Canvas(rounded), bounds, 0xffffffff, 255,
                        null, 4, dual, 4, 0f);
                long oldCoverage = 0, newCoverage = 0;
                int changed = 0;
                for (int y = 0; y < rounded.getHeight(); y++) {
                    for (int x = 0; x < rounded.getWidth(); x++) {
                        int a = old.getPixel(x, y) >>> 24;
                        int b = rounded.getPixel(x, y) >>> 24;
                        oldCoverage += a;
                        newCoverage += b;
                        if (Math.abs(a - b) > 16) changed++;
                        // Allow rasterizer edge coverage differences, but no new solid pixels outside.
                        if (b > 128) assertTrue("rounded shape expanded", a > 96);
                        if (!dual) {
                            int mirror = rounded.getPixel(rounded.getWidth() - 1 - x, y) >>> 24;
                            assertEquals("left/right symmetry", b, mirror, 3);
                        }
                    }
                }
                assertTrue("rounding must visibly remove corners", changed > 0);
                assertTrue(newCoverage < oldCoverage);
                assertTrue("preserve visual weight", newCoverage > oldCoverage * 0.90);
            }
        }
    }

    @Test
    public void appearanceChangesRefreshCachedPathsAndRestoreDefaults() {
        WifiIconRenderer renderer = new ClassicWifiRenderer();
        assertFalse(renderer.setAppearance(25, 18, 80));
        Rect bounds = new Rect(0, 0, 220, 220);
        RecordingCanvas original = new RecordingCanvas();
        renderer.draw(original, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        assertTrue(renderer.setAppearance(0, 0, 40));
        RecordingCanvas sharp = new RecordingCanvas();
        renderer.draw(sharp, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        assertEquals(colors(original), colors(sharp));
        assertFalse("geometry must update even when bounds/levels are unchanged",
                original.arcs.equals(sharp.arcs));
        assertTrue(renderer.setAppearance(45, 30, 120));
        assertFalse("out-of-range input clamps to supported maximum",
                renderer.setAppearance(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        RecordingCanvas maximum = new RecordingCanvas();
        renderer.draw(maximum, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        assertEquals(6, maximum.arcs.size());
        for (Arc arc : maximum.arcs) {
            assertTrue(Float.isFinite(arc.left) && Float.isFinite(arc.top));
            assertTrue(arc.right > arc.left && arc.bottom > arc.top);
        }
        assertTrue(renderer.setAppearance(25, 18, 80));
        RecordingCanvas restored = new RecordingCanvas();
        renderer.draw(restored, bounds, 0xffffffff, 255, null, 4, true, 1, 0f);
        assertEquals(original.arcs, restored.arcs);
    }

    @Test
    public void zeroCornerSettingsPreserveSharpSectorTip() {
        WifiIconRenderer renderer = new ClassicWifiRenderer();
        renderer.setAppearance(0, 0, 80);
        RecordingCanvas sharp = new RecordingCanvas();
        renderer.draw(sharp, new Rect(0, 0, 220, 220), 0xffffffff, 255,
                null, 4, false, 0, 0f);
        renderer.setAppearance(0, 30, 80);
        RecordingCanvas rounded = new RecordingCanvas();
        renderer.draw(rounded, new Rect(0, 0, 220, 220), 0xffffffff, 255,
                null, 4, false, 0, 0f);
        assertEquals(sharp.arcs.get(0), rounded.arcs.get(0));
        assertEquals(sharp.arcs.get(1), rounded.arcs.get(1));
        assertTrue("only the rounded tip moves inward",
                rounded.arcs.get(2).bottom < sharp.arcs.get(2).bottom);
    }

    private record ColorState(int color, boolean antiAlias, ColorFilter filter) { }

    private static List<ColorState> colors(RecordingCanvas canvas) {
        List<ColorState> result = new ArrayList<>();
        for (Arc arc : canvas.arcs) {
            result.add(new ColorState(arc.color, arc.antiAlias, arc.filter));
        }
        return result;
    }

    private static WifiIconDrawable drawable(int level, boolean dual, int secondary, float offset) {
        return new WifiIconDrawable(WifiIconStyles.createRenderer(WifiIconStyles.CLASSIC), 22, 22, 22,
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
        public void drawPath(Path path, Paint paint) {
            assertEquals(Paint.Style.FILL, paint.getStyle());
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            assertFalse(path.isEmpty());
            arcs.add(new Arc(bounds.left, bounds.top, bounds.right, bounds.bottom, 0f, 0f,
                    false, paint.getColor(), 0f, paint.getStyle(), paint.getStrokeCap(),
                    paint.isAntiAlias(), paint.getColorFilter()));
        }

        @Override
        public void drawArc(RectF oval, float startAngle, float sweepAngle, boolean useCenter,
                Paint paint) {
            arcs.add(new Arc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle,
                    useCenter, paint.getColor(), paint.getStrokeWidth(), paint.getStyle(),
                    paint.getStrokeCap(), paint.isAntiAlias(), paint.getColorFilter()));
        }
    }
}
