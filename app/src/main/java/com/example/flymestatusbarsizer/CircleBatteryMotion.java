package com.example.flymestatusbarsizer;

/** Time-based motion model, independent of SystemUI fields and Android frame callbacks. */
final class CircleBatteryMotion {
    static final int NONE = 0, CONNECT = 1, DISCONNECT = 2, FULL = 3, LOW = 4;
    boolean initialized, plugged, charging, powerSave;
    float rawLevel = -1, fromLevel, targetLevel;
    long levelStart, eventStart;
    int event;

    static float mappedLevel(float level) {
        if (!Float.isFinite(level) || level < 0) return 0;
        if (level > 92 && level < 100) return 93;
        return Math.min(100, level);
    }

    void update(float level, boolean plugged, boolean charging, boolean powerSave,
            CircleBatteryAnimationConfig config, boolean visible, long now) {
        float target = mappedLevel(level);
        boolean animate = visible && config.enabled && !powerSave;
        boolean changed = !initialized || rawLevel != level || this.plugged != plugged
                || this.charging != charging || this.powerSave != powerSave;
        if (changed) {
            float current = displayedLevel(now);
            if (initialized && animate && config.events) {
                // Latest battery state wins; no queue of stale feedback after rapid changes.
                event = NONE;
                if (this.plugged && !plugged) event = DISCONNECT;
                else if (plugged && !this.plugged) event = CONNECT;
                else if (plugged && level >= 100 && rawLevel < 100) event = FULL;
                else if (level >= 0 && level < 10 && rawLevel >= 10
                        && !charging && !config.chargingOnly) event = LOW;
                eventStart = now;
            }
            fromLevel = initialized && animate && config.smooth
                    && (!config.chargingOnly || charging) ? current : target;
            targetLevel = target;
            levelStart = now;
        }
        if (!animate || !config.events) event = NONE;
        if (!animate || !config.smooth || (config.chargingOnly && !charging)) fromLevel = target;
        rawLevel = level;
        this.plugged = plugged;
        this.charging = charging;
        this.powerSave = powerSave;
        initialized = true;
    }

    void suspend() {
        initialized = false;
        event = NONE;
        fromLevel = targetLevel;
    }

    float displayedLevel(long now) {
        float t = Math.max(0, Math.min(1, (now - levelStart) / 600f));
        t = t * t * (3 - 2 * t);
        return fromLevel + (targetLevel - fromLevel) * t;
    }
    long eventDuration() {
        switch (event) {
            case CONNECT: return 800;
            case DISCONNECT: return 400;
            case FULL: return 1000;
            case LOW: return 6000;
            default: return 0;
        }
    }
    int currentEvent(long now) {
        return now - eventStart < eventDuration() ? event : NONE;
    }
    int effect(CircleBatteryAnimationConfig config) {
        if (!config.enabled || powerSave || (plugged && rawLevel >= 100)) return CircleBatteryAnimationConfig.STATIC;
        if (charging) return config.charging;
        if (config.chargingOnly || (rawLevel >= 0 && rawLevel < 10)) return CircleBatteryAnimationConfig.STATIC;
        return config.normal;
    }
    boolean needsFrames(CircleBatteryAnimationConfig config, long now) {
        return config.enabled && !powerSave && (currentEvent(now) != NONE
                || effect(config) != CircleBatteryAnimationConfig.STATIC
                || (fromLevel != targetLevel && now - levelStart < 600));
    }
    static long period(CircleBatteryAnimationConfig config, int effect, boolean charging) {
        long base = effect == CircleBatteryAnimationConfig.BREATHE
                || effect == CircleBatteryAnimationConfig.ROTATE ? 3000
                : effect == CircleBatteryAnimationConfig.COMET ? 2500
                : !charging && config.legacyRainbow && config.palette == CircleBatteryAnimationConfig.RAINBOW
                ? 3000 : 8000;
        return config.speed == 0 ? base * 2 : config.speed == 2 ? base / 2 : base;
    }
}
