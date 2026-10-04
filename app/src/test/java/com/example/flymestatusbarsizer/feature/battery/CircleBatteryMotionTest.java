package com.example.flymestatusbarsizer.feature.battery;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CircleBatteryMotionTest {
    @Test public void rotationUsesSpeedChoicesAndStopsInPowerSaveOrAtFullCharge() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        c.normal = c.charging = CircleBatteryAnimationConfig.ROTATE;
        c.events = c.smooth = false;
        m.update(50, false, false, false, c, true, 1000);
        assertEquals(CircleBatteryAnimationConfig.ROTATE, m.effect(c));
        assertTrue(m.needsFrames(c, 1000));
        long[] periods = {6000, 3000, 1500};
        for (int i = 0; i < periods.length; i++) {
            c.speed = i;
            assertEquals(periods[i], CircleBatteryMotion.period(c, m.effect(c), false));
        }
        m.update(50, true, true, true, c, true, 2000);
        assertFalse(m.needsFrames(c, 2000));
        m.update(50, true, true, false, c, true, 3000);
        assertEquals(CircleBatteryAnimationConfig.ROTATE, m.effect(c));
        m.update(100, true, false, false, c, true, 4000);
        assertFalse(m.needsFrames(c, 4000));
    }

    @Test public void levelRetargetsFromCurrentPositionAndPreservesFlymeFullGap() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        m.update(50, false, false, false, c, true, 1000);
        assertEquals(50, m.displayedLevel(1000), 0);
        m.update(60, false, false, false, c, true, 2000);
        assertEquals(55, m.displayedLevel(2300), 0.001);
        m.update(70, false, false, false, c, true, 2300);
        assertEquals(55, m.displayedLevel(2300), 0.001);
        assertEquals(70, m.displayedLevel(2900), 0.001);
        assertEquals(93, CircleBatteryMotion.mappedLevel(99), 0);
        assertEquals(100, CircleBatteryMotion.mappedLevel(100), 0);
        assertEquals(0, CircleBatteryMotion.mappedLevel(-1), 0);
    }

    @Test public void connectionFeedbackIsDeduplicatedAndLatestStateWins() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        m.update(50, false, false, false, c, true, 1000);
        assertEquals(CircleBatteryMotion.NONE, m.currentEvent(1000));
        m.update(50, true, true, false, c, true, 2000);
        assertEquals(CircleBatteryMotion.CONNECT, m.currentEvent(2000));
        m.update(50, true, true, false, c, true, 2400);
        assertEquals(2000, m.eventStart);
        m.update(50, false, false, false, c, true, 2500);
        assertEquals(CircleBatteryMotion.DISCONNECT, m.currentEvent(2500));
        assertEquals(CircleBatteryMotion.NONE, m.currentEvent(2900));
    }

    @Test public void lowBatteryTriggersOnceAndPowerSaveAndMasterStopEverything() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        m.update(10, false, false, false, c, true, 1000);
        m.update(9, false, false, false, c, true, 2000);
        assertEquals(CircleBatteryMotion.LOW, m.currentEvent(2000));
        m.update(9, false, false, false, c, true, 3000);
        assertEquals(2000, m.eventStart);
        assertFalse(m.needsFrames(c, 8000));
        m.update(8, true, true, true, c, true, 9000);
        assertFalse(m.needsFrames(c, 9000));
        assertEquals(8, m.displayedLevel(9000), 0);
        m.update(8, true, true, false, c, true, 10000);
        assertEquals(CircleBatteryAnimationConfig.COMET, m.effect(c));
        c.enabled = false;
        m.update(9, true, true, false, c, true, 11000);
        assertFalse(m.needsFrames(c, 11000));
    }

    @Test public void fullAndPluggedButNotChargingDoNotRunComet() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        m.update(99, true, true, false, c, true, 1000);
        m.update(100, true, false, false, c, true, 2000);
        assertEquals(CircleBatteryMotion.FULL, m.currentEvent(2000));
        assertEquals(CircleBatteryAnimationConfig.STATIC, m.effect(c));
        assertFalse(m.needsFrames(c, 3000));
        m.update(80, true, false, false, c, true, 4000);
        assertEquals(CircleBatteryAnimationConfig.FLOW, m.effect(c));
    }

    @Test public void hiddenUpdatesAndResumeNeverReplayEvents() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        m.update(50, false, false, false, c, true, 1000);
        m.suspend();
        m.update(60, true, true, false, c, false, 2000);
        m.suspend();
        m.update(60, true, true, false, c, true, 3000);
        assertEquals(CircleBatteryMotion.NONE, m.currentEvent(3000));
        assertEquals(60, m.displayedLevel(3000), 0);
    }

    @Test public void chargingOnlyAndLegacySpeedAreIndependentChoices() {
        CircleBatteryMotion m = new CircleBatteryMotion();
        CircleBatteryAnimationConfig c = new CircleBatteryAnimationConfig();
        c.chargingOnly = true;
        m.update(50, false, false, false, c, true, 1000);
        m.update(49, false, false, false, c, true, 2000);
        assertFalse(m.needsFrames(c, 2000));
        m.update(49, true, true, false, c, true, 3000);
        assertTrue(m.needsFrames(c, 3000));
        m.update(49, false, false, false, c, true, 3100);
        assertEquals(CircleBatteryMotion.DISCONNECT, m.currentEvent(3100));
        c.legacyRainbow = true;
        c.palette = CircleBatteryAnimationConfig.RAINBOW;
        assertEquals(3000, CircleBatteryMotion.period(c, CircleBatteryAnimationConfig.FLOW, false));
        c.speed = 0;
        assertEquals(6000, CircleBatteryMotion.period(c, CircleBatteryAnimationConfig.FLOW, false));
    }
}
