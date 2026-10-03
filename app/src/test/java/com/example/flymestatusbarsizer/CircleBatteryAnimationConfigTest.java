package com.example.flymestatusbarsizer;

import android.content.Context;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import java.util.Arrays;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class CircleBatteryAnimationConfigTest {
    SharedPreferences prefs;
    @Before public void setUp() {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("animation-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
    }
    @Test public void newInstallGetsRecommendedDefaultsAndEveryKeySyncsAndBacksUp() {
        CircleBatteryAnimationConfig.migrate(prefs);
        CircleBatteryAnimationConfig c = CircleBatteryAnimationConfig.load(prefs);
        assertEquals(CircleBatteryAnimationConfig.FLOW, c.normal);
        assertEquals(CircleBatteryAnimationConfig.COMET, c.charging);
        assertTrue(c.events);
        assertTrue(c.smooth);
        assertFalse(c.legacyRainbow);
        for (String key : CircleBatteryAnimationConfig.INT_KEYS) {
            assertTrue(Arrays.asList(SettingsStore.INT_KEYS).contains(key));
            assertTrue(SettingsStore.includeInBackup(key));
            assertEquals(SettingsStore.defaultInt(key), prefs.getInt(key, -1));
        }
        for (String key : CircleBatteryAnimationConfig.BOOLEAN_KEYS) {
            assertTrue(Arrays.asList(SettingsStore.BOOLEAN_KEYS).contains(key));
            assertTrue(SettingsStore.includeInBackup(key));
            assertEquals(SettingsStore.defaultBoolean(key), prefs.getBoolean(key, false));
        }
    }
    @Test public void oldStaticAndRainbowStayCompatibleBeforeAndAfterMigration() {
        for (boolean rainbow : new boolean[]{false, true}) {
            prefs.edit().clear().putBoolean(SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TINT_ENABLED, rainbow).commit();
            assertLegacy(CircleBatteryAnimationConfig.load(prefs), rainbow);
            CircleBatteryAnimationConfig.migrate(prefs);
            assertLegacy(CircleBatteryAnimationConfig.load(prefs), rainbow);
            prefs.edit().putInt(CircleBatteryAnimationConfig.CHARGING, 3).commit();
            CircleBatteryAnimationConfig.migrate(prefs);
            assertEquals(3, CircleBatteryAnimationConfig.load(prefs).charging);
        }
    }
    @Test public void importedInvalidValuesClampAndColorsStayOpaque() {
        CircleBatteryAnimationConfig.migrate(prefs);
        prefs.edit().putInt(CircleBatteryAnimationConfig.NORMAL, 2)
                .putInt(CircleBatteryAnimationConfig.CHARGING, 99)
                .putInt(CircleBatteryAnimationConfig.SPEED, -9)
                .putInt(CircleBatteryAnimationConfig.STRENGTH, 9)
                .putInt(CircleBatteryAnimationConfig.COLOR_START, 0x123456).commit();
        CircleBatteryAnimationConfig c = CircleBatteryAnimationConfig.load(prefs);
        assertEquals(1, c.normal);
        assertEquals(CircleBatteryAnimationConfig.ROTATE, c.charging);
        assertEquals(0, c.speed);
        assertEquals(2, c.strength);
        assertEquals(0xFF123456, c.colorStart);
    }
    @Test public void rotationPersistsForNormalAndChargingWithoutChangingDefaults() {
        CircleBatteryAnimationConfig.migrate(prefs);
        assertEquals(CircleBatteryAnimationConfig.FLOW, CircleBatteryAnimationConfig.load(prefs).normal);
        prefs.edit().putInt(CircleBatteryAnimationConfig.NORMAL, CircleBatteryAnimationConfig.ROTATE)
                .putInt(CircleBatteryAnimationConfig.CHARGING, CircleBatteryAnimationConfig.ROTATE).commit();
        CircleBatteryAnimationConfig.migrate(prefs);
        CircleBatteryAnimationConfig c = CircleBatteryAnimationConfig.load(prefs);
        assertEquals(CircleBatteryAnimationConfig.ROTATE, c.normal);
        assertEquals(CircleBatteryAnimationConfig.ROTATE, c.charging);
    }
    private void assertLegacy(CircleBatteryAnimationConfig c, boolean rainbow) {
        assertEquals(rainbow ? 1 : 0, c.normal);
        assertEquals(rainbow ? 2 : 0, c.palette);
        assertEquals(rainbow, c.legacyRainbow);
        assertEquals(0, c.charging);
        assertFalse(c.events);
        assertFalse(c.smooth);
    }
}
