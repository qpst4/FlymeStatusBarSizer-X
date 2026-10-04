package com.example.flymestatusbarsizer.feature.wifi;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.StateSet;

import java.util.Objects;

/** Android drawable state and invalidation; all style-specific painting belongs to the renderer. */
public final class WifiIconDrawable extends Drawable {
    private static final int MAX_LEVEL = 4;

    private final WifiIconRenderer renderer;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final int visualBandHeight;

    private ColorStateList tintList;
    private ColorFilter colorFilter;
    private int drawColor = Color.WHITE;
    private int alpha = 255;
    private int level;
    private boolean showSecondaryBadge;
    private int secondaryLevel;
    private float verticalOffsetPx;

    public WifiIconDrawable(WifiIconRenderer renderer, int intrinsicWidth, int intrinsicHeight,
            int visualBandHeight, int level, boolean showSecondaryBadge, int secondaryLevel,
            float verticalOffsetPx) {
        this.renderer = Objects.requireNonNull(renderer);
        this.intrinsicWidth = Math.max(1, intrinsicWidth);
        this.intrinsicHeight = Math.max(1, intrinsicHeight);
        this.visualBandHeight = Math.max(1, visualBandHeight);
        this.level = sanitizeLevel(level);
        this.showSecondaryBadge = showSecondaryBadge;
        this.secondaryLevel = sanitizeLevel(secondaryLevel);
        this.verticalOffsetPx = verticalOffsetPx;
    }

    /** The owning view also uses this instance to measure its layout. */
    public WifiIconRenderer getRenderer() {
        return renderer;
    }

    public boolean matchesConfiguration(int styleId, int intrinsicWidth, int intrinsicHeight,
            int visualBandHeight) {
        return renderer.getStyleId() == styleId
                && this.intrinsicWidth == Math.max(1, intrinsicWidth)
                && this.intrinsicHeight == Math.max(1, intrinsicHeight)
                && this.visualBandHeight == Math.max(1, visualBandHeight);
    }

    public boolean setStateValues(int level, boolean showSecondaryBadge, int secondaryLevel,
            float verticalOffsetPx) {
        int sanitized = sanitizeLevel(level);
        int sanitizedSecondary = sanitizeLevel(secondaryLevel);
        if (this.level == sanitized
                && this.showSecondaryBadge == showSecondaryBadge
                && this.secondaryLevel == sanitizedSecondary
                && Float.compare(this.verticalOffsetPx, verticalOffsetPx) == 0) {
            return false;
        }
        this.level = sanitized;
        this.showSecondaryBadge = showSecondaryBadge;
        this.secondaryLevel = sanitizedSecondary;
        this.verticalOffsetPx = verticalOffsetPx;
        invalidateSelf();
        return true;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        updateDrawColor(getState());
        renderer.draw(canvas, bounds, drawColor, alpha, colorFilter,
                level, showSecondaryBadge, secondaryLevel, verticalOffsetPx);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(alpha, 255));
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        this.colorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setTintList(ColorStateList tint) {
        tintList = tint;
        updateDrawColor(getState());
    }

    @Override
    public boolean isStateful() {
        return tintList != null && tintList.isStateful();
    }

    @Override
    protected boolean onStateChange(int[] state) {
        return updateDrawColor(state);
    }

    private boolean updateDrawColor(int[] state) {
        int resolvedColor = tintList == null
                ? Color.WHITE
                : tintList.getColorForState(state == null ? StateSet.NOTHING : state, tintList.getDefaultColor());
        if (drawColor == resolvedColor) {
            return false;
        }
        drawColor = resolvedColor;
        invalidateSelf();
        return true;
    }

    private static int sanitizeLevel(int level) {
        if (level < 0) {
            return 0;
        }
        return Math.min(level, MAX_LEVEL);
    }
}
