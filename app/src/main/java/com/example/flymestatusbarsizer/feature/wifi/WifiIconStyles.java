package com.example.flymestatusbarsizer.feature.wifi;

/** Stable persisted style IDs and creation of independently owned renderers. */
public final class WifiIconStyles {
    static final int CLASSIC = 0;
    public static final int DEFAULT = CLASSIC;

    private WifiIconStyles() {
    }

    public static int normalize(int styleId) {
        switch (styleId) {
            case CLASSIC:
                return styleId;
            default:
                return DEFAULT;
        }
    }

    /** Each drawable owns its renderer's mutable drawing objects. */
    public static WifiIconRenderer createRenderer(int styleId) {
        switch (normalize(styleId)) {
            case CLASSIC:
            default:
                return new ClassicWifiRenderer();
        }
    }
}
