package com.example.flymestatusbarsizer.feature.statusbar;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.Arrays;

/** Scene precedence and remote preference coverage for status bar tinting. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class StatusBarTintSceneTest {
    @Test public void scenePriorityAndPreferencesRemainConsistent() {
        expect("desktop", 0, false, false, false, false, 0);
        expect("recents", 1, false, false, false, false, 1);
        expect("app", -1, false, false, false, false, -1);
        expect("unknown launcher state", -1, false, false, false, false, 9);
        expect("shade over desktop", 2, false, false, true, false, 0);
        expect("control center over shade", 3, false, true, true, false, 0);
        expect("shade over recents", 2, false, false, true, false, 1);
        expect("lockscreen over desktop", 4, false, false, false, true, 0);
        expect("control center over lockscreen", 3, false, true, false, true, 0);
        expect("shade over lockscreen", 2, false, false, true, true, 0);
        expect("return to lockscreen", 4, false, false, false, true, -1);
        expect("return to desktop", 0, false, false, false, false, 0);
        expect("leave for app", -1, false, false, false, false, -1);
        expect("password or AOD", -1, true, true, true, true, 0);
        expect("occluding app", -1, true, false, false, true, 0);
        if (SettingsStore.STATUS_BAR_TINT_KEYS.length != 5) throw new AssertionError("Five settings required");
        for (String key : SettingsStore.STATUS_BAR_TINT_KEYS) {
            if (!Arrays.asList(SettingsStore.INT_KEYS).contains(key)) {
                throw new AssertionError("Setting missing from remote sync: " + key);
            }
            if (SettingsStore.defaultInt(key) != 0) throw new AssertionError("Must default to system: " + key);
        }
    }

    private static void expect(String label, int expected, boolean blocked, boolean control,
            boolean shade, boolean locked, int launcher) {
        int actual = StatusBarTintHooks.selectScene(blocked, control, shade, locked, launcher);
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
