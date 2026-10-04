package com.example.flymestatusbarsizer.feature.assistant;

/** One touchscreen pointer, signed horizontal displacement, and a monotonic deadline. */
final class AssistantGestureState {
    private boolean tracking;
    private boolean claimed;
    private float startX, startY, dx, dy, distance, verticalLimit;
    private long deadline;

    void begin(float x, float y, long downTime, float distance, long duration, float verticalLimit) {
        tracking = true;
        claimed = false;
        startX = x;
        startY = y;
        dx = dy = 0;
        this.distance = distance;
        this.verticalLimit = verticalLimit;
        deadline = downTime + duration;
    }

    void move(float x, float y, int pointerCount) {
        dx = x - startX;
        dy = y - startY;
        if (pointerCount != 1 || !Float.isFinite(dx) || !Float.isFinite(dy)
                || Math.abs(dy) > verticalLimit || dx < -verticalLimit / 4f) cancel();
    }

    boolean ready(long now) {
        return tracking && !claimed && now >= deadline
                && dx >= distance && dx >= Math.abs(dy) * 1.5f;
    }

    void claim() { claimed = true; tracking = false; }
    void cancel() { tracking = false; }
    long deadline() { return deadline; }
}
