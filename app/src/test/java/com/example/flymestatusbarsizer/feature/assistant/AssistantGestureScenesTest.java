package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Method;
import java.util.Arrays;

import static com.example.flymestatusbarsizer.config.SettingsStore.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantGestureScenesTest {
    @Test public void allEightSelectionsFilterAppsAndEachOverlayIndependently() {
        EdgeHandler edge = new EdgeHandler();
        WindowState state = edge.mNotificationShadeWindowController.mCurrentState;
        for (int selected = 0; selected < 8; selected++) {
            for (int flags = 0; flags < 8; flags++) {
                state.centerControllerVisible = (flags & 1) != 0;
                state.qsExpanded = (flags & 2) != 0;
                edge.expansion().value = (flags & 4) != 0 ? 1f : 0f;
                int expected = (flags & 3) != 0 ? ASSISTANT_GESTURE_SCENE_CONTROL_CENTER
                        : flags != 0 ? ASSISTANT_GESTURE_SCENE_NOTIFICATION : ASSISTANT_GESTURE_SCENE_NORMAL;
                assertEquals(expected, AssistantGestureScenes.current(edge));
                assertEquals("selection=" + selected + ", window flags=" + flags,
                        (selected & expected) != 0, AssistantGestureScenes.allows(selected, edge));
            }
        }
    }

    @Test public void liveStateIsRecheckedAsPanelsOpenSwitchAndFinishClosing() {
        EdgeHandler edge = new EdgeHandler();
        WindowState state = edge.mNotificationShadeWindowController.mCurrentState;
        assertTrue(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NORMAL, edge));
        // Even a partially expanded shade owns the scene; it need not be fully open.
        state.panelVisible = true;
        edge.expansion().value = 0.01f;
        assertFalse(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NORMAL, edge));
        assertTrue(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NOTIFICATION, edge));
        state.shadeOrQsExpanded = true;
        state.centerControllerVisible = true;
        assertFalse(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NOTIFICATION, edge));
        assertTrue(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_CONTROL_CENTER, edge));
        state.panelVisible = false;
        state.shadeOrQsExpanded = false;
        edge.expansion().value = 0f;
        assertFalse(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NORMAL, edge));
        state.centerControllerVisible = false;
        assertTrue(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NORMAL, edge));
    }

    @Test public void headsUpAndOtherWindowFlagsDoNotCountAsAnExpandedPanel() {
        EdgeHandler edge = new EdgeHandler();
        edge.mNotificationShadeWindowController.mCurrentState.headsUpNotificationShowing = true;
        edge.mNotificationShadeWindowController.mCurrentState.forcePluginOpen = true;
        edge.mNotificationShadeWindowController.mCurrentState.panelVisible = true;
        edge.mNotificationShadeWindowController.mCurrentState.shadeOrQsExpanded = true;
        assertEquals(ASSISTANT_GESTURE_SCENE_NORMAL, AssistantGestureScenes.current(edge));
        edge.expansion().value = 0.5f;
        assertEquals(ASSISTANT_GESTURE_SCENE_NOTIFICATION, AssistantGestureScenes.current(edge));
    }

    @Test public void unavailableOrInvalidExpansionIsNeverTreatedAsAnOrdinaryApp() {
        EdgeHandler edge = new EdgeHandler();
        for (Object value : new Object[]{null, "unknown", Float.NaN, Float.POSITIVE_INFINITY, -1f, 2f}) {
            edge.expansion().value = value;
            assertEquals(0, AssistantGestureScenes.current(edge));
            assertFalse(AssistantGestureScenes.allows(ASSISTANT_GESTURE_SCENE_NORMAL, edge));
        }
        edge.mNotificationShadeWindowController.mCurrentState.centerControllerVisible = true;
        assertEquals(ASSISTANT_GESTURE_SCENE_CONTROL_CENTER, AssistantGestureScenes.current(edge));
    }

    @Test public void unreadableStatePreservesAllScenesButNeverBypassesARestrictedSelection() {
        for (Object edge : new Object[]{null, new Object(), new BrokenEdge()}) {
            assertEquals(0, AssistantGestureScenes.current(edge));
            for (int selected = 0; selected < 8; selected++) {
                assertEquals(selected == DEFAULT_ASSISTANT_GESTURE_SCENES,
                        AssistantGestureScenes.allows(selected, edge));
            }
        }
    }

    @Test public void scenesLoadWithCompatibleDefaultsAndParticipateInSyncBackupAndReset() throws Exception {
        String key = KEY_ASSISTANT_GESTURE_SCENES;
        assertTrue(Arrays.asList(SettingsStore.INT_KEYS).contains(key));
        assertTrue(SettingsStore.includeInBackup(key));
        assertEquals(7, SettingsStore.defaultInt(key));
        SharedPreferences prefs = RuntimeEnvironment.getApplication()
                .getSharedPreferences("assistant-scene-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Method load = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        load.setAccessible(true);
        assertEquals(7, ((ModuleConfig) load.invoke(null, prefs)).assistantGestureScenes);
        for (int selected = 0; selected < 8; selected++) {
            prefs.edit().putInt(key, selected).commit();
            assertEquals(selected, ((ModuleConfig) load.invoke(null, prefs)).assistantGestureScenes);
        }
        for (int[] values : new int[][]{{-2, 7}, {8, 0}, {9, 1}, {15, 7}}) {
            prefs.edit().putInt(key, values[0]).commit();
            assertEquals(values[1], ((ModuleConfig) load.invoke(null, prefs)).assistantGestureScenes);
        }
        prefs.edit().putInt(key, SettingsStore.defaultInt(key)).commit();
        assertEquals(7, ((ModuleConfig) load.invoke(null, prefs)).assistantGestureScenes);
    }

    private static final class EdgeHandler {
        final WindowController mNotificationShadeWindowController = new WindowController();
        Expansion expansion() {
            return mNotificationShadeWindowController.mShadeInteractorLazy.get().getLegacyShadeExpansion();
        }
    }
    private static final class WindowController {
        final WindowState mCurrentState = new WindowState();
        final LazyInteractor mShadeInteractorLazy = new LazyInteractor();
    }
    private static final class LazyInteractor {
        final Interactor interactor = new Interactor();
        public Interactor get() { return interactor; }
    }
    private static final class Interactor {
        final Expansion expansion = new Expansion();
        public Expansion getLegacyShadeExpansion() { return expansion; }
    }
    private static final class Expansion {
        Object value = 0f;
        public Object getValue() { return value; }
    }
    private static final class WindowState {
        boolean centerControllerVisible, qsExpanded, panelVisible, shadeOrQsExpanded;
        boolean headsUpNotificationShowing, forcePluginOpen;
    }
    private static final class BrokenEdge {
        final Object mNotificationShadeWindowController = new Object() {
            final Object mCurrentState = new Object() {
                final boolean panelVisible = false, shadeOrQsExpanded = false;
                final String centerControllerVisible = "unavailable";
            };
        };
    }
}
