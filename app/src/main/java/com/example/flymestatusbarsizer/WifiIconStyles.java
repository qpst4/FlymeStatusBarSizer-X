package com.example.flymestatusbarsizer;

/** Stable persisted style IDs and creation of independently owned renderers. */
final class WifiIconStyles {
    static final int CLASSIC = 0;
    static final int DEFAULT = CLASSIC;

    private WifiIconStyles() {
    }

    static int normalize(int styleId) {
        switch (styleId) {
            case CLASSIC:
                return styleId;
            default:
                return DEFAULT;
        }
    }

    /** Each drawable owns its renderer's mutable drawing objects. */
    static WifiIconRenderer createRenderer(int styleId) {
        switch (normalize(styleId)) {
            case CLASSIC:
            default:
                return new ClassicWifiRenderer();
        }
    }
}
