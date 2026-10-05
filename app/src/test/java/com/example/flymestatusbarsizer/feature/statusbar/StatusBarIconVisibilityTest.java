package com.example.flymestatusbarsizer.feature.statusbar;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class StatusBarIconVisibilityTest {
    private SharedPreferences prefs;
    private StatusBarIconVisibilityHooks.Binding binding;

    @Before public void setup() throws Exception {
        prefs = RuntimeEnvironment.getApplication().getSharedPreferences("icon_visibility", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        binding = new StatusBarIconVisibilityHooks.Binding(Container.class);
    }

    @Test public void existingUsersKeepSystemBehaviorAndEachChoiceSurvivesConfigLoading() throws Exception {
        assertTrue(load().hiddenStatusBarSlots.isEmpty());
        for (StatusBarIconVisibility.Icon icon : StatusBarIconVisibility.ICONS) {
            // These arrays drive cross-process sync, export/import and reset-to-default.
            assertEquals(1, Arrays.stream(SettingsStore.BOOLEAN_KEYS).filter(icon.key::equals).count());
            assertFalse(SettingsStore.defaultBoolean(icon.key));
            prefs.edit().clear().putBoolean(icon.key, true).commit();
            assertEquals(Set.of(icon.slot), load().hiddenStatusBarSlots);
            prefs.edit().remove(icon.key).commit();
            assertTrue(load().hiddenStatusBarSlots.isEmpty());
        }
    }

    @Test public void vibrationAndWifiChoicesUseIndependentVerifiedSlots() throws Exception {
        prefs.edit().putBoolean("status_bar_hide_volume", true)
                .putBoolean("status_bar_hide_wifi", true)
                .putBoolean("status_bar_hide_volte_or_vowifi", true).commit();
        assertEquals(Set.of("volume", "wifi", "volte_or_vowifi"), load().hiddenStatusBarSlots);
        prefs.edit().putBoolean("status_bar_hide_wifi", false)
                .putBoolean("status_bar_hide_dual_wifi", true).commit();
        assertEquals(Set.of("volume", "dual_wifi", "volte_or_vowifi"), load().hiddenStatusBarSlots);
    }

    @Test public void hidingRetainsNativeReasonsAndTurningItOffRestoresOnlyModuleChanges() throws Throwable {
        Container container = new Container("location", "wifi");
        ArrayList<String> nativeSlots = container.mIgnoredSlots;
        ModuleConfig config = config("wifi", "bluetooth");
        Object result = new Object();

        assertSame(result, binding.withHiddenSlots(container, config, () -> {
            assertEquals(List.of("location", "wifi", "bluetooth"), container.mIgnoredSlots);
            return result;
        }));
        assertSame(nativeSlots, container.mIgnoredSlots);
        assertEquals(List.of("location", "wifi"), nativeSlots);

        // A native privacy/header update between layout passes must remain authoritative.
        nativeSlots.remove("location");
        nativeSlots.add("camera");
        config.hiddenStatusBarSlots = Set.of();
        binding.withHiddenSlots(container, config, () -> {
            assertSame(nativeSlots, container.mIgnoredSlots);
            assertEquals(List.of("wifi", "camera"), container.mIgnoredSlots);
            return null;
        });
    }

    @Test public void disablingModulePreservesChoicesWithoutApplyingThem() throws Throwable {
        ModuleConfig config = config("wifi", "mobile");
        config.enabled = false;
        Container container = new Container("location");
        binding.withHiddenSlots(container, config, () -> {
            assertEquals(List.of("location"), container.mIgnoredSlots);
            return null;
        });
        assertEquals(Set.of("wifi", "mobile"), config.hiddenStatusBarSlots);
        config.enabled = true;
        binding.withHiddenSlots(container, config, () -> {
            assertEquals(Set.of("location", "wifi", "mobile"), new HashSet<>(container.mIgnoredSlots));
            return null;
        });
        assertEquals(List.of("location"), container.mIgnoredSlots);
    }

    @Test public void reentrantLayoutAndExceptionsAlwaysRestoreTheOriginalList() throws Throwable {
        Container container = new Container("location");
        ArrayList<String> nativeSlots = container.mIgnoredSlots;
        RuntimeException failure = new RuntimeException("layout failed");
        try {
            binding.withHiddenSlots(container, config("wifi"), () -> {
                ArrayList<String> outerSlots = container.mIgnoredSlots;
                try {
                    return binding.withHiddenSlots(container, config("mobile"), () -> {
                        assertEquals(Set.of("location", "wifi", "mobile"), new HashSet<>(container.mIgnoredSlots));
                        throw failure;
                    });
                } finally {
                    assertSame(outerSlots, container.mIgnoredSlots);
                    assertEquals(List.of("location", "wifi"), container.mIgnoredSlots);
                }
            });
            fail("Must propagate the original failure");
        } catch (RuntimeException actual) {
            assertSame(failure, actual);
        }
        assertSame(nativeSlots, container.mIgnoredSlots);
        assertEquals(List.of("location"), nativeSlots);
    }

    @Test public void separateAndRecreatedContainersKeepTheirOwnNativeLists() throws Throwable {
        Container statusBar = new Container("hotspot");
        Container header = new Container("camera", "microphone");
        ModuleConfig config = config("bluetooth");
        for (Container container : List.of(statusBar, header, new Container())) {
            ArrayList<String> nativeSlots = container.mIgnoredSlots;
            binding.withHiddenSlots(container, config, () -> {
                assertTrue(container.mIgnoredSlots.containsAll(nativeSlots));
                assertTrue(container.mIgnoredSlots.contains("bluetooth"));
                assertEquals(nativeSlots.size() + 1, container.mIgnoredSlots.size());
                return null;
            });
            assertSame(nativeSlots, container.mIgnoredSlots);
            assertFalse(nativeSlots.contains("bluetooth"));
        }
    }

    private ModuleConfig load() throws Exception {
        Method method = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        method.setAccessible(true);
        ModuleConfig result = (ModuleConfig) method.invoke(null, prefs);
        assertNotNull(result);
        return result;
    }

    private static ModuleConfig config(String... slots) {
        ModuleConfig config = new ModuleConfig();
        config.enabled = true;
        config.hiddenStatusBarSlots = Set.of(slots);
        return config;
    }

    private static final class Container {
        public ArrayList<String> mIgnoredSlots;

        Container(String... slots) {
            mIgnoredSlots = new ArrayList<>(List.of(slots));
        }
    }
}
