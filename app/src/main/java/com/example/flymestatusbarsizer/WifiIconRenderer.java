package com.example.flymestatusbarsizer;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

/** A Wi-Fi style's drawing contract, shared by the drawable and settings preview. */
interface WifiIconRenderer {
    /** Stable style identity used when selecting and reusing a drawable. */
    int getStyleId();

    /** Layout width at the given icon box height, including the optional secondary glyph. */
    int measureWidth(int boxHeight, boolean showSecondaryBadge);

    /**
     * Draw inside the supplied icon box. A positive pixel offset moves the glyph upwards.
     * Instances own their drawing scratch objects and must not be shared across drawing threads.
     */
    void draw(Canvas canvas, Rect bounds, int color, int alpha, ColorFilter colorFilter,
            int level, boolean showSecondaryBadge, int secondaryLevel, float verticalOffsetPx);
}
