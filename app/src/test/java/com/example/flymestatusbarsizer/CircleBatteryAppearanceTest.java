package com.example.flymestatusbarsizer;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.view.View;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Method;
import java.util.Arrays;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class CircleBatteryAppearanceTest {
    private CircleView view;
    private ModuleConfig config;

    @Before public void setUp() {
        view = new CircleView(RuntimeEnvironment.getApplication());
        view.layout(0, 0, 40, 40);
        config = new ModuleConfig();
        config.cameraCircleBatteryEnabled = true;
    }

    @Test public void defaultTransparencySurvivesNativeColorUpdatesInBothThemes() {
        view.mIsDark = true;
        assertPaint(0xE1088BFF);
        view.mIsDark = false;
        // Simulate native apply() overwriting Paint on a theme/battery update.
        view.mPaint.setColor(0xFFFFFFFF);
        assertPaint(0xE1FFFFFF);
        assertEquals(0x1E000000, view.mBgPaint.getColor());
        assertEquals(0xFF088BFF, view.mPaintColor);
    }

    @Test public void customStatesKeepFlymePriorityAndExactTenPercentBoundary() {
        config.cameraCircleBatteryChargingColor = 0xFF112233;
        config.cameraCircleBatteryLowColor = 0xFF445566;
        config.cameraCircleBatteryPowerSaveColor = 0xFF778899;
        config.cameraCircleBatteryNormalDarkColor = 0xFFABCDEF;
        view.mCharging = true;
        view.mLowPowerMode = true;
        view.mLevel = 9;
        assertPaint(0xE1112233);
        view.mCharging = false;
        assertPaint(0xE1445566);
        view.mLevel = 10;
        assertPaint(0xE1778899);
        view.mLowPowerMode = false;
        assertPaint(0xE1ABCDEF);
        view.mLevel = 100;
        assertPaint(0xE1ABCDEF);
        view.mLevel = -1;
        view.mLowPowerMode = true;
        assertPaint(0xE1ABCDEF);
        view.mCharging = true;
        assertPaint(0xE1112233);
    }

    @Test public void rainbowUsesSameAlphaAndYieldsToEverySpecialState() {
        config.cameraCircleBatteryTintEnabled = true;
        CircleBatteryAppearance.apply(view, config);
        assertNotNull(view.mPaint.getShader());
        assertEquals(225, view.mPaint.getAlpha());
        view.mCharging = true;
        assertPaint(0xE120D013);
        assertNull(view.mPaint.getShader());
        view.mCharging = false;
        view.mLowPowerMode = true;
        assertPaint(0xE1FFAC26);
        assertNull(view.mPaint.getShader());
        view.mLevel = 9;
        assertPaint(0xE1F6400F);
        assertNull(view.mPaint.getShader());
        view.mLowPowerMode = false;
        view.mLevel = 10;
        CircleBatteryAppearance.apply(view, config);
        assertNotNull(view.mPaint.getShader());
        config.cameraCircleBatteryTransparencyTenthPercent = 1000;
        CircleBatteryAppearance.apply(view, config);
        assertEquals(0, view.mPaint.getAlpha());
        config.cameraCircleBatteryTintEnabled = false;
        config.cameraCircleBatteryNormalDarkColor = 0xFF123456;
        config.cameraCircleBatteryTransparencyTenthPercent = 0;
        assertPaint(0xFF123456);
        assertNull(view.mPaint.getShader());
    }

    @Test public void disablingModuleOrRingRestoresNativePaintAndRemovesRainbow() {
        for (boolean disableModule : new boolean[]{true, false}) {
            config.enabled = true;
            config.cameraCircleBatteryEnabled = true;
            config.cameraCircleBatteryTintEnabled = true;
            CircleBatteryAppearance.apply(view, config);
            assertNotNull(view.mPaint.getShader());
            if (disableModule) config.enabled = false;
            else config.cameraCircleBatteryEnabled = false;
            assertPaint(view.mPaintColor);
            assertNull(view.mPaint.getShader());
            assertEquals(0x1E000000, view.mBgPaint.getColor());
        }
    }

    @Test public void transparencyEndpointsClampAndColorAlphaCannotOverrideSetting() {
        config.cameraCircleBatteryNormalDarkColor = 0x12123456;
        config.cameraCircleBatteryTransparencyTenthPercent = -100;
        assertPaint(0xFF123456);
        config.cameraCircleBatteryTransparencyTenthPercent = 500;
        assertPaint(0x80123456);
        config.cameraCircleBatteryTransparencyTenthPercent = Integer.MAX_VALUE;
        assertPaint(0x00123456);
    }

    @Test public void newSettingsParticipateInSyncBackupDefaultsAndConfigReload() throws Exception {
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("circle-appearance-test", Context.MODE_PRIVATE);
        String[] keys = {
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_TRANSPARENCY_TENTH_PERCENT,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_LIGHT_COLOR,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_NORMAL_DARK_COLOR,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_CHARGING_COLOR,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_POWER_SAVE_COLOR,
                SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_LOW_COLOR
        };
        SharedPreferences.Editor editor = prefs.edit().clear();
        for (String key : keys) {
            assertTrue(Arrays.asList(SettingsStore.INT_KEYS).contains(key));
            assertTrue(SettingsStore.includeInBackup(key));
            editor.putInt(key, SettingsStore.defaultInt(key));
        }
        editor.putBoolean(SettingsStore.KEY_CAMERA_CIRCLE_BATTERY_ENABLED, true).commit();
        Method load = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        load.setAccessible(true);
        config = (ModuleConfig) load.invoke(null, prefs);
        assertNotNull(config);
        assertPaint(0xE1FFFFFF);
        prefs.edit().putInt(keys[0], 1500).putInt(keys[2], 0x00123456).commit();
        config = (ModuleConfig) load.invoke(null, prefs);
        assertEquals(1000, config.cameraCircleBatteryTransparencyTenthPercent);
        assertEquals(0xFF123456, config.cameraCircleBatteryNormalDarkColor);
        assertPaint(0x00123456);
        prefs.edit().putInt(keys[0], -1).commit();
        config = (ModuleConfig) load.invoke(null, prefs);
        assertPaint(0xFF123456);
    }

    @Test public void colorInputAcceptsRgbAndRejectsAlphaOrInvalidInput() {
        assertEquals(0xFF088BFF, CircleBatteryAppearanceEditor.parseColor(" #088bff "));
        assertEquals(0xFF000000, CircleBatteryAppearanceEditor.parseColor("000000"));
        for (String bad : new String[]{"", "#123", "#FF088BFF", "#GG0000", "-12345"}) {
            assertThrows(IllegalArgumentException.class, () -> CircleBatteryAppearanceEditor.parseColor(bad));
        }
    }

    private void assertPaint(int color) {
        CircleBatteryAppearance.apply(view, config);
        assertEquals(color & 0xFFFFFF, view.mPaint.getColor() & 0xFFFFFF);
        assertEquals(color >>> 24, view.mPaint.getAlpha());
    }

    // Legacy Robolectric tracks Paint.color and Paint.alpha independently. Real Android's
    // setColor also resets alpha; model that contract to catch incorrect operation ordering.
    private static final class ColorAlphaPaint extends Paint {
        @Override public void setColor(int color) {
            super.setColor(color);
            super.setAlpha(color >>> 24);
        }
    }

    public static final class CircleView extends View {
        public final Paint mPaint = new ColorAlphaPaint();
        public final Paint mBgPaint = new Paint();
        public int mPaintColor = 0xFF088BFF;
        public boolean mIsDark;
        public boolean mCharging;
        public boolean mLowPowerMode;
        public float mLevel = 50;

        CircleView(Context context) {
            super(context);
            mPaint.setColor(mPaintColor);
            mBgPaint.setColor(0x1E000000);
        }
    }
}
