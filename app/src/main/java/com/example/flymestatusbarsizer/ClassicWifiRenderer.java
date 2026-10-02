package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;

/** Concentric Wi-Fi bands with inward, tangent circular corner fillets. */
final class ClassicWifiRenderer implements WifiIconRenderer {
    private static final int DRAW_ALPHA = 224;
    private static final float INACTIVE_ALPHA_RATIO = 0.3f;
    private static final float SOURCE_WIDTH = 50.617f - 4.318f;
    private static final float SOURCE_HEIGHT = 34.332f - 1.117f;
    private static final float VISUAL_ASPECT_RATIO = SOURCE_WIDTH / SOURCE_HEIGHT;
    private static final float ARC_START_ANGLE = 225f;
    private static final float ARC_SWEEP_ANGLE = 90f;
    private float bandGapRatio = SettingsStore.DEFAULT_WIFI_BAND_GAP_PERCENT / 100f;
    private float bandCornerRatio = SettingsStore.DEFAULT_WIFI_BAND_CORNER_PERCENT / 100f;
    private float tipCornerRatio = SettingsStore.DEFAULT_WIFI_TIP_CORNER_PERCENT / 100f;
    private static final float SECTOR_THICKNESS_RATIO = 1.5f;
    private static final float SQRT_TWO = (float) Math.sqrt(2d);
    private static final float DIAGONAL_UNIT_X = SQRT_TWO / 2f;
    private static final float SECONDARY_BADGE_VISIBLE_ALIGN_FRACTION = 1f / 3f;
    private static final float SECONDARY_BADGE_SAFETY_GAP_TO_THICKNESS_RATIO = 0.25f;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final IconMetrics.VisualCanvas visualCanvas = new IconMetrics.VisualCanvas();
    private final RectF drawBounds = new RectF();
    private final RoundedShape[] shapes = {new RoundedShape(), new RoundedShape(),
            new RoundedShape(), new RoundedShape(), new RoundedShape(), new RoundedShape()};
    private int shapeIndex;

    ClassicWifiRenderer() {
        arcPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public boolean setAppearance(int bandCornerPercent, int tipCornerPercent, int bandGapPercent) {
        float corner = Math.max(0, Math.min(45, bandCornerPercent)) / 100f;
        float tip = Math.max(0, Math.min(30, tipCornerPercent)) / 100f;
        float gap = Math.max(40, Math.min(120, bandGapPercent)) / 100f;
        if (corner == bandCornerRatio && tip == tipCornerRatio && gap == bandGapRatio) {
            return false;
        }
        bandCornerRatio = corner;
        tipCornerRatio = tip;
        bandGapRatio = gap;
        return true;
    }

    @Override
    public int getStyleId() {
        return WifiIconStyles.CLASSIC;
    }

    @Override
    public int measureWidth(int boxHeight, boolean showSecondaryBadge) {
        int height = Math.max(1, boxHeight);
        return showSecondaryBadge
                ? Math.max(height, Math.round(height * resolveMergedBoxWidthRatio()))
                : height;
    }

    private float resolveMergedBoxWidthRatio() {
        float canvasHeight = 1f / 1.8f;
        float canvasWidth = canvasHeight * VISUAL_ASPECT_RATIO;
        float maxOuterBoundary = Math.min(canvasHeight, canvasWidth * SQRT_TWO / 2f);
        float thickness = maxOuterBoundary / (2f + SECTOR_THICKNESS_RATIO
                + 2f * bandGapRatio);
        float gap = thickness * bandGapRatio;
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

    @Override
    public void draw(Canvas canvas, Rect bounds, int color, int alpha, ColorFilter colorFilter,
            int level, boolean showSecondaryBadge, int secondaryLevel, float verticalOffsetPx) {
        int baseColor = modulateColorAlpha(color, alpha);
        drawIcon(canvas, bounds, baseColor, colorFilter, level, showSecondaryBadge, secondaryLevel,
                verticalOffsetPx);
    }

    private void drawIcon(Canvas canvas, Rect bounds, int baseColor, ColorFilter colorFilter,
            int level, boolean showSecondaryBadge, int secondaryLevel, float verticalOffsetPx) {
        if (canvas == null || bounds == null || bounds.isEmpty()) {
            return;
        }
        int activeColor = modulateColorAlpha(baseColor, DRAW_ALPHA);
        int inactiveColor = scaleAlpha(activeColor, INACTIVE_ALPHA_RATIO);
        if (showSecondaryBadge) {
            IconMetrics.resolveStartVisualCanvas(bounds, VISUAL_ASPECT_RATIO, visualCanvas);
        } else {
            IconMetrics.resolveCenteredVisualCanvas(bounds, VISUAL_ASPECT_RATIO, visualCanvas);
        }
        if (visualCanvas.isEmpty()) {
            return;
        }
        if (verticalOffsetPx != 0f) {
            visualCanvas.rect.offset(0f, -verticalOffsetPx);
            visualCanvas.baselineY -= verticalOffsetPx;
        }

        drawBounds.set(visualCanvas.rect.left, visualCanvas.rect.top,
                visualCanvas.rect.right, visualCanvas.baselineY);
        if (drawBounds.isEmpty()) {
            return;
        }

        float maxOuterBoundary = Math.max(0f, Math.min(
                drawBounds.height(),
                drawBounds.width() * SQRT_TWO / 2f) - 0.01f);
        if (maxOuterBoundary <= 0f) {
            return;
        }

        float thickness = Math.max(0.75f,
                maxOuterBoundary / (2f + SECTOR_THICKNESS_RATIO
                        + 2f * bandGapRatio));
        float gap = thickness * bandGapRatio;
        float sectorThickness = thickness * SECTOR_THICKNESS_RATIO;
        float cx = drawBounds.centerX();
        float cy = drawBounds.bottom;
        float sectorRadius = sectorThickness / 2f;
        float innerRadius = sectorRadius + sectorThickness / 2f + gap + thickness / 2f;
        float outerRadius = innerRadius + thickness + gap;

        shapeIndex = 0;
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

    private void drawRoundedBand(Canvas canvas, float cx, float cy, float radius,
            int color, float width, float corner, float tip, ColorFilter colorFilter) {
        RoundedShape shape = shapes[shapeIndex++];
        shape.update(cx, cy, radius + width / 2f, Math.max(0f, radius - width / 2f),
                corner, tip);
        arcPaint.setColor(color);
        arcPaint.setColorFilter(colorFilter);
        canvas.drawPath(shape.path, arcPaint);
        arcPaint.setColorFilter(null);
    }

    /** A cached closed contour; signal/tint changes do not rebuild its geometry. */
    private static final class RoundedShape {
        final Path path = new Path();
        final RectF oval = new RectF();
        float oldX = Float.NaN, oldY, oldOuter, oldInner, oldCorner, oldTip;

        void update(float x, float y, float outer, float inner, float corner, float tip) {
            if (x == oldX && y == oldY && outer == oldOuter && inner == oldInner
                    && corner == oldCorner && tip == oldTip) {
                return;
            }
            oldX = x;
            oldY = y;
            oldOuter = outer;
            oldInner = inner;
            oldCorner = corner;
            oldTip = tip;
            path.rewind();
            // Keep the corner tangent points distinct even at very small icon sizes.
            float q = Math.min(corner, (outer - inner) * 0.45f);
            float start = ARC_START_ANGLE;
            float end = start + ARC_SWEEP_ANGLE;
            float outerCenter = outer - q;
            float outerDelta = (float) Math.toDegrees(Math.asin(q / outerCenter));
            arc(x, y, outer, start + outerDelta, ARC_SWEEP_ANGLE - 2f * outerDelta, true);
            fillet(x, y, outerCenter, end - outerDelta, q,
                    end - outerDelta, 90f + outerDelta);
            if (inner > 0f) {
                float innerCenter = inner + q;
                float innerDelta = (float) Math.toDegrees(Math.asin(q / innerCenter));
                fillet(x, y, innerCenter, end - innerDelta, q,
                        end + 90f, 90f - innerDelta);
                arc(x, y, inner, end - innerDelta,
                        -ARC_SWEEP_ANGLE + 2f * innerDelta, false);
                fillet(x, y, innerCenter, start + innerDelta, q,
                        start + innerDelta + 180f, 90f - innerDelta);
            } else {
                // The bottom sector has no inner arc. Its 90-degree tip has its own radius.
                // Inward rounding lifts the visible tip by (sqrt(2) - 1) * tip;
                // preserve the shared layout baseline instead of moving the entire glyph.
                float tipRadius = Math.min(tip, outer * 0.2f);
                arc(x, y - SQRT_TWO * tipRadius, tipRadius, 45f, 90f, false);
            }
            fillet(x, y, outerCenter, start + outerDelta, q,
                    start - 90f, 90f + outerDelta);
            path.close();
        }

        private void fillet(float x, float y, float distance, float angle, float radius,
                float start, float sweep) {
            double radians = Math.toRadians(angle);
            arc(x + distance * (float) Math.cos(radians),
                    y + distance * (float) Math.sin(radians), radius, start, sweep, false);
        }

        private void arc(float x, float y, float radius, float start, float sweep, boolean move) {
            if (radius <= 0f) {
                // A zero radius means a sharp corner, not a degenerate arcTo oval.
                if (move) path.moveTo(x, y);
                else path.lineTo(x, y);
                return;
            }
            oval.set(x - radius, y - radius, x + radius, y + radius);
            path.arcTo(oval, start, sweep, move);
        }
    }

    private void drawWifiGlyph(Canvas canvas, float cx, float cy, float outerRadius,
            float innerRadius, float sectorRadius, float thickness, float sectorThickness,
            int activeColor, int inactiveColor, int level, ColorFilter colorFilter) {
        float corner = thickness * bandCornerRatio;
        drawRoundedBand(canvas, cx, cy, outerRadius,
                resolveOuterBandColor(activeColor, inactiveColor, level), thickness,
                corner, 0f, colorFilter);
        drawRoundedBand(canvas, cx, cy, innerRadius,
                resolveInnerBandColor(activeColor, inactiveColor, level), thickness,
                corner, 0f, colorFilter);
        drawRoundedBand(canvas, cx, cy, sectorRadius,
                resolveSectorColor(activeColor, inactiveColor, level), sectorThickness,
                corner, thickness * tipCornerRatio, colorFilter);
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
        return (color & 0x00ffffff) | (scaledAlpha << 24);
    }

    private static int modulateColorAlpha(int color, int alpha) {
        int baseAlpha = (color >>> 24) & 0xff;
        int appliedAlpha = Math.max(0, Math.min(255, alpha));
        return (color & 0x00ffffff) | (((baseAlpha * appliedAlpha + 127) / 255) << 24);
    }
}
