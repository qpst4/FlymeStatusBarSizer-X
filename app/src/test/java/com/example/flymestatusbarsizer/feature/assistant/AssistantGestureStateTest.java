package com.example.flymestatusbarsizer.feature.assistant;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssistantGestureStateTest {
    private AssistantGestureState gesture() {
        AssistantGestureState state = new AssistantGestureState();
        state.begin(5, 500, 1000, 80, 600, 12, true);
        return state;
    }

    @Test public void stationaryFingerTriggersAtDeadlineWithoutAnotherMove() {
        AssistantGestureState state = gesture();
        state.move(85, 500, 1);
        assertFalse(state.ready(1599));
        assertTrue(state.ready(1600));
        state.claim();
        assertFalse(state.ready(1700));
    }

    @Test public void timeAndDistanceAreBothRequired() {
        AssistantGestureState state = gesture();
        state.move(84, 500, 1);
        assertFalse(state.ready(5000));
        state.move(85, 500, 1);
        assertTrue(state.ready(5000));
    }

    @Test public void fastSwipeRemainsOrdinaryBack() {
        AssistantGestureState state = gesture();
        state.move(200, 500, 1);
        assertFalse(state.ready(1200));
        state.cancel();
        assertFalse(state.ready(2000));
    }

    @Test public void retractingBelowThresholdPreventsDelayedTrigger() {
        AssistantGestureState state = gesture();
        state.move(120, 500, 1);
        state.move(35, 500, 1);
        assertFalse(state.ready(1600));
        state.move(100, 500, 1);
        assertTrue(state.ready(1700));
    }

    @Test public void verticalDriftBeyond48DoesNotCancelThisStream() {
        for (float y : new float[] {400, 600}) {
            AssistantGestureState state = gesture();
            state.move(165, y, 1);
            assertTrue(state.ready(1600));
        }
    }

    @Test public void predominantlyVerticalMovementCanRecoverWhenHorizontalDistanceCatchesUp() {
        AssistantGestureState state = gesture();
        state.move(85, 600, 1);
        assertFalse(state.ready(1600));
        state.move(155, 600, 1);
        assertTrue(state.ready(1600));
    }

    @Test public void secondFingerCancelsThisStream() {
        AssistantGestureState state = gesture();
        state.move(150, 500, 2);
        state.move(150, 500, 1);
        assertFalse(state.ready(1600));
    }

    @Test public void wrongDirectionAndInvalidCoordinatesCannotTrigger() {
        AssistantGestureState state = gesture();
        state.move(-100, 500, 1);
        assertFalse(state.ready(2000));
        state = gesture();
        state.move(Float.NaN, 500, 1);
        assertFalse(state.ready(2000));
    }

    @Test public void mirroredEdgesHaveIdenticalThresholdAndCancellationBehavior() {
        for (boolean left : new boolean[]{true, false}) {
            int direction = left ? 1 : -1;
            AssistantGestureState state = new AssistantGestureState();
            state.begin(500, 500, 1000, 80, 600, 12, left);
            state.move(500 + direction * 79, 500, 1);
            assertFalse(state.ready(1600));
            state.move(500 + direction * 80, 500, 1);
            assertFalse(state.ready(1599));
            assertTrue(state.ready(1600));
            state.move(500 + direction * 20, 500, 1);
            assertFalse(state.ready(1700));
            state.move(500 - direction * 13, 500, 1);
            state.move(500 + direction * 100, 500, 1);
            assertFalse(state.ready(1800));
        }
    }

    @Test public void switchingEdgesOnNextGestureResetsDirection() {
        AssistantGestureState state = gesture();
        state.move(100, 500, 1);
        state.claim();
        state.begin(500, 500, 3000, 80, 600, 12, false);
        state.move(420, 500, 1);
        assertTrue(state.ready(3600));
    }

    @Test public void aNewDownStartsAnIndependentDeadline() {
        AssistantGestureState state = gesture();
        state.move(100, 500, 1);
        state.claim();
        state.begin(5, 500, 3000, 80, 600, 12, true);
        state.move(100, 500, 1);
        assertFalse(state.ready(3599));
        assertTrue(state.ready(3600));
    }
}
