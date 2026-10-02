package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/**
 * Frozen drawing reference from WifiIconDrawable at d139fe4604b7d0f1d13124e3ed9c674a049528d6.
 * Deliberately independent of ClassicWifiRenderer: changes to the new renderer must not
 * silently change the expected geometry, alpha or signal-level mapping in these tests.
 */
final class LegacyWifiDrawing {
    private static final int DRAW_ALPHA = 224;
    private static final float INACTIVE_ALPHA_RATIO = 0.3f;
    private static final float SOURCE_WIDTH = 50.617f - 4.318f;
    private static final float SOURCE_HEIGHT = 34.332f - 1.117f;
    private static final float VISUAL_ASPECT_RATIO = SOURCE_WIDTH / SOURCE_HEIGHT;
    private static final float ARC_START_ANGLE = 225f;
    private static final float ARC_SWEEP_ANGLE = 90f;
    private static final float INTER_BAND_GAP_TO_THICKNESS_RATIO = 0.8f;
    private static final float SECTOR_THICKNESS_RATIO = 1.5f;
    private static final float SQRT_TWO = (float) Math.sqrt(2d);
    private static final float DIAGONAL_UNIT_X = SQRT_TWO / 2f;
    private static final float SECONDARY_BADGE_VISIBLE_ALIGN_FRACTION = 1f / 3f;
    private static final float SECONDARY_BADGE_SAFETY_GAP_TO_THICKNESS_RATIO = 0.25f;

    private static final Paint ARC_PAINT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final IconMetrics.VisualCanvas VISUAL_CANVAS = new IconMetrics.VisualCanvas();
    private static final RectF DRAW_BOUNDS = new RectF();
    private static final RectF ARC_OVAL = new RectF();

    static {
        ARC_PAINT.setStyle(Paint.Style.STROKE);
        ARC_PAINT.setStrokeCap(Paint.Cap.BUTT);
    }

    static float resolveMergedBoxWidthRatio() {
        float canvasHeight = 1f / 1.8f;
        float canvasWidth = canvasHeight * VISUAL_ASPECT_RATIO;
        float maxOuterBoundary = Math.min(canvasHeight, canvasWidth * SQRT_TWO / 2f);
        float thickness = maxOuterBoundary / (2f + SECTOR_THICKNESS_RATIO
                + 2f * INTER_BAND_GAP_TO_THICKNESS_RATIO);
        float gap = thickness * INTER_BAND_GAP_TO_THICKNESS_RATIO;
        float sectorThickness = thickness * SECTOR_THICKNESS_RATIO;
        float sectorRadius = sectorThickness / 2f;
        float innerRadius = sectorRadius + sectorThickness / 2f + gap + thickness / 2f;
        float outerRadius = innerRadius + thickness + gap;
        float centeredLeft = (1f - canvasWidth) / 2f;
        float singleGlyphRight = centeredLeft + canvasWidth / 2f + DIAGONAL_UNIT_X * outerRadius;
        float trailingGap = Math.max(0f, 1f - singleGlyphRight);
        float cx = canvasWidth / 2f;
        float badgeOuterRadius = resolveSecondaryOuterRadius(outerRadius, innerRadius, thickness);
        float badgeCenterX = cx + DIAGONAL_UNIT_X * outerRadius
                + resolveSecondaryAnchorOffsetX(badgeOuterRadius);
        float badgeRight = badgeCenterX + DIAGONAL_UNIT_X * badgeOuterRadius;
        return Math.max(1f, badgeRight + trailingGap);
    }

    static void drawPreview(Canvas canvas, Rect bounds, int color, int alpha, ColorFilter colorFilter,
            int level, boolean showSecondaryBadge, int secondaryLevel, float verticalOffsetPx) {
        int baseColor = SignalPreviewPainter.modulateColorAlpha(color, alpha);
        drawIcon(canvas, bounds, baseColor, colorFilter, level, showSecondaryBadge, secondaryLevel,
                verticalOffsetPx);
    }

    private static void drawIcon(Canvas canvas, Rect bounds, int baseColor, ColorFilter colorFilter,
            int level, boolean showSecondaryBadge, int secondaryLevel, float verticalOffsetPx) {
        if (canvas == null || bounds == null || bounds.isEmpty()) {
            return;
        }
        int activeColor = SignalPreviewPainter.modulateColorAlpha(baseColor, DRAW_ALPHA);
        int inactiveColor = scaleAlpha(activeColor, INACTIVE_ALPHA_RATIO);
        if (showSecondaryBadge) {
            IconMetrics.resolveStartVisualCanvas(bounds, VISUAL_ASPECT_RATIO, VISUAL_CANVAS);
        } else {
            IconMetrics.resolveCenteredVisualCanvas(bounds, VISUAL_ASPECT_RATIO, VISUAL_CANVAS);
        }
        if (VISUAL_CANVAS.isEmpty()) {
            return;
        }
        if (verticalOffsetPx != 0f) {
            VISUAL_CANVAS.rect.offset(0f, -verticalOffsetPx);
            VISUAL_CANVAS.baselineY -= verticalOffsetPx;
        }

        DRAW_BOUNDS.set(VISUAL_CANVAS.rect.left, VISUAL_CANVAS.rect.top,
                VISUAL_CANVAS.rect.right, VISUAL_CANVAS.baselineY);
        if (DRAW_BOUNDS.isEmpty()) {
            return;
        }

        float maxOuterBoundary = Math.max(0f, Math.min(
                DRAW_BOUNDS.height(),
                DRAW_BOUNDS.width() * SQRT_TWO / 2f) - 0.01f);
        if (maxOuterBoundary <= 0f) {
            return;
        }

        float thickness = Math.max(0.75f,
                maxOuterBoundary / (2f + SECTOR_THICKNESS_RATIO
                        + 2f * INTER_BAND_GAP_TO_THICKNESS_RATIO));
        float gap = thickness * INTER_BAND_GAP_TO_THICKNESS_RATIO;
        float sectorThickness = thickness * SECTOR_THICKNESS_RATIO;
        float cx = DRAW_BOUNDS.centerX();
        float cy = DRAW_BOUNDS.bottom;
        float sectorRadius = sectorThickness / 2f;
        float innerRadius = sectorRadius + sectorThickness / 2f + gap + thickness / 2f;
        float outerRadius = innerRadius + thickness + gap;

        drawWifiGlyph(canvas, cx, cy, outerRadius, innerRadius, sectorRadius,
                thickness, sectorThickness, activeColor, inactiveColor, level, colorFilter);
        if (showSecondaryBadge) {
            float badgeOuterRadius = resolveSecondaryOuterRadius(outerRadius, innerRadius, thickness);
            if (badgeOuterRadius <= 0f || outerRadius <= 0f) {
                return;
            }
            float badgeScale = badgeOuterRadius / outerRadius;
            float badgeInnerRadius = innerRadius * badgeScale;
            float badgeSectorRadius = sectorRadius * badgeScale;
            float badgeThickness = thickness * badgeScale;
            float badgeSectorThickness = sectorThickness * badgeScale;
            float badgeCenterX = cx + DIAGONAL_UNIT_X * outerRadius
                    + resolveSecondaryAnchorOffsetX(badgeOuterRadius);
            float badgeCenterY = cy;
            drawWifiGlyph(canvas, badgeCenterX, badgeCenterY, badgeOuterRadius, badgeInnerRadius,
                    badgeSectorRadius, badgeThickness, badgeSectorThickness,
                    activeColor, inactiveColor, secondaryLevel, colorFilter);
        }
    }

    private static void drawConcentricArc(Canvas canvas, float cx, float cy, float radius,
            float startAngle, float sweepAngle, int color, float strokeWidth,
            ColorFilter colorFilter) {
        if (radius <= 0f) {
            return;
        }
        ARC_OVAL.set(cx - radius, cy - radius, cx + radius, cy + radius);
        ARC_PAINT.setStrokeWidth(strokeWidth);
        ARC_PAINT.setColor(color);
        ARC_PAINT.setColorFilter(colorFilter);
        canvas.drawArc(ARC_OVAL, startAngle, sweepAngle, false, ARC_PAINT);
        ARC_PAINT.setColorFilter(null);
    }

    private static void drawWifiGlyph(Canvas canvas, float cx, float cy, float outerRadius,
            float innerRadius, float sectorRadius, float thickness, float sectorThickness,
            int activeColor, int inactiveColor, int level, ColorFilter colorFilter) {
        drawConcentricArc(canvas, cx, cy, outerRadius, ARC_START_ANGLE, ARC_SWEEP_ANGLE,
                resolveOuterBandColor(activeColor, inactiveColor, level), thickness, colorFilter);
        drawConcentricArc(canvas, cx, cy, innerRadius, ARC_START_ANGLE, ARC_SWEEP_ANGLE,
                resolveInnerBandColor(activeColor, inactiveColor, level), thickness, colorFilter);
        drawConcentricArc(canvas, cx, cy, sectorRadius, ARC_START_ANGLE, ARC_SWEEP_ANGLE,
                resolveSectorColor(activeColor, inactiveColor, level), sectorThickness, colorFilter);
    }

    private static float resolveSecondaryAnchorOffsetX(float badgeOuterRadius) {
        if (badgeOuterRadius <= 0f) {
            return 0f;
        }
        // Align one-third of the child glyph's visible arc span to the primary top-right endpoint.
        return DIAGONAL_UNIT_X * badgeOuterRadius
                * (1f - 2f * SECONDARY_BADGE_VISIBLE_ALIGN_FRACTION);
    }

    private static float resolveSecondaryOuterRadius(float outerRadius, float innerRadius,
            float thickness) {
        if (outerRadius <= 0f || thickness <= 0f) {
            return 0f;
        }
        float leftReachFactor = DIAGONAL_UNIT_X * 2f * SECONDARY_BADGE_VISIBLE_ALIGN_FRACTION;
        if (leftReachFactor <= 0f) {
            return 0f;
        }
        float safetyGap = thickness * SECONDARY_BADGE_SAFETY_GAP_TO_THICKNESS_RATIO;
        float availableLeftSpan = DIAGONAL_UNIT_X * (outerRadius - innerRadius) - safetyGap;
        return Math.max(0f, Math.min(outerRadius, availableLeftSpan / leftReachFactor));
    }

    private static int resolveSectorColor(int activeColor, int inactiveColor, int level) {
        return resolveVisibleBars(level) >= 1 ? activeColor : inactiveColor;
    }

    private static int resolveInnerBandColor(int activeColor, int inactiveColor, int level) {
        return resolveVisibleBars(level) >= 2 ? activeColor : inactiveColor;
    }

    private static int resolveOuterBandColor(int activeColor, int inactiveColor, int level) {
        return resolveVisibleBars(level) >= 3 ? activeColor : inactiveColor;
    }

    private static int resolveVisibleBars(int level) {
        if (level <= 0) {
            return 1;
        }
        if (level == 1) {
            return 2;
        }
        return 3;
    }

    private static int scaleAlpha(int color, float ratio) {
        int alpha = (color >>> 24) & 0xff;
        int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * ratio)));
        return SignalPreviewPainter.withFixedAlpha(color, scaledAlpha);
    }
}
