package com.example.flymestatusbarsizer.feature.assistant;

/** One touchscreen pointer, signed horizontal displacement, and a monotonic deadline. */
final class AssistantGestureState {
    private boolean tracking;
    private boolean claimed;
    private float startX, startY, dx, dy, distance, backwardLimit, verticalLimit;
    private long deadline;
    private int inwardDirection;

    void begin(float x, float y, long downTime, float distance, long duration,
            float backwardLimit, boolean leftEdge, float verticalLimit) {
        inwardDirection = leftEdge ? 1 : -1;
        tracking = true;
        claimed = false;
        startX = x;
        startY = y;
        dx = dy = 0;
        this.distance = distance;
        this.backwardLimit = backwardLimit;
        // Positive infinity disables the optional vertical limit for this gesture.
        this.verticalLimit = verticalLimit;
        deadline = downTime + duration;
    }

    void move(float x, float y, int pointerCount) {
        dx = (x - startX) * inwardDirection;
        dy = y - startY;
        if (pointerCount != 1 || !Float.isFinite(dx) || !Float.isFinite(dy)
                || dx < -backwardLimit || Math.abs(dy) > verticalLimit) cancel();
    }

    boolean ready(long now) {
        return tracking && !claimed && now >= deadline
                && dx >= distance;
    }

    void claim() { claimed = true; tracking = false; }
    void cancel() { tracking = false; }
    long deadline() { return deadline; }
}
