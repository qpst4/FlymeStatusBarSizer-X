package com.example.flymestatusbarsizer.feature.wifi;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

/**
 * Negative control for the centroid symmetry check added to {@link WifiRenderingTest}.
 *
 * <p>Confirms the check still rejects a glyph that really is lopsided, so relaxing the per-pixel
 * mirror comparison did not turn the assertion into a no-op.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class AnipWifiSymmetryNegativeControlTest {
    private static double centroidOf(Bitmap bitmap) {
        double weighted = 0;
        double total = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                int a = bitmap.getPixel(x, y) >>> 24;
                weighted += (double) a * x;
                total += a;
            }
        }
        return total <= 0 ? -1 : weighted / total;
    }

    private static Bitmap drawRect(int bitmapWidth, int bitmapHeight, Rect rect) {
        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xffffffff);
        new Canvas(bitmap).drawRect(rect, paint);
        return bitmap;
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void symmetricRectPassesAndLopsidedRectFails() {
        int w = 38;
        int h = 38;
        // A centred rect: the centroid must sit on the bitmap centre.
        Bitmap centred = drawRect(w, h, new Rect(8, 8, 30, 30));
        double centredCentroid = centroidOf(centred);
        double centre = (w - 1) / 2.0;
        System.out.println("[NEG] centred centroid=" + centredCentroid + " centre=" + centre
                + " delta=" + Math.abs(centredCentroid - centre));
        org.junit.Assert.assertTrue("a centred rect must satisfy the check",
                Math.abs(centredCentroid - centre) <= 0.05);

        // The same rect shifted one pixel right: the check must reject it.
        Bitmap lopsided = drawRect(w, h, new Rect(9, 8, 31, 30));
        double lopsidedCentroid = centroidOf(lopsided);
        System.out.println("[NEG] lopsided centroid=" + lopsidedCentroid + " centre=" + centre
                + " delta=" + Math.abs(lopsidedCentroid - centre));
        org.junit.Assert.assertTrue("a one-pixel shift must be rejected",
                Math.abs(lopsidedCentroid - centre) > 0.05);
    }
}
