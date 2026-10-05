package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Dialog;
import android.os.Binder;
import android.view.Window;
import android.view.WindowManager;
import com.example.flymestatusbarsizer.config.SettingsStore;
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
public class AssistantWindowIdentityTest {
    @Test public void hostIdentityCanBeClearedAndRestoredEvenWhenCopyFromPreservesIt() {
        Window window = new Dialog(RuntimeEnvironment.getApplication()).getWindow();
        Binder launcherToken = new Binder();
        window.getAttributes().token = launcherToken;
        window.getAttributes().packageName = "com.meizu.flyme.launcher";
        window.getAttributes().type = 4;
        WindowManager.LayoutParams original = new WindowManager.LayoutParams();
        original.copyFrom(window.getAttributes());
        WindowManager.LayoutParams overlay = new WindowManager.LayoutParams();
        overlay.copyFrom(original);
        AssistantWindowSession.applyHostAttributes(overlay);
        window.setAttributes(overlay);
        assertSame(launcherToken, window.getAttributes().token);
        AssistantWindowSession.applyIdentityAndAttributes(window, overlay);
        assertNull(window.getAttributes().token);
        assertEquals(AssistantProtocol.PACKAGE, window.getAttributes().packageName);
        assertEquals(2017, window.getAttributes().type);
        assertEquals(AssistantWindowSession.WINDOW_TYPE, window.getAttributes().type);
        AssistantWindowSession.applyIdentityAndAttributes(window, original);
        assertSame(launcherToken, window.getAttributes().token);
        assertEquals("com.meizu.flyme.launcher", window.getAttributes().packageName);
        assertEquals(4, window.getAttributes().type);
    }

    @Test public void nativeAttributeUpdatesKeepGlobalHostAboveShadeAndFocusable() {
        WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
        // The native component may submit its desktop identity and non-focusable flags again.
        for (int i = 0; i < 2; i++) {
            attrs.type = 4;
            attrs.token = new Binder();
            attrs.packageName = "com.meizu.flyme.launcher";
            attrs.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
            AssistantWindowSession.applyHostAttributes(attrs);
            assertEquals(2017, attrs.type);
            assertNull(attrs.token);
            assertEquals(AssistantProtocol.PACKAGE, attrs.packageName);
            assertEquals(AssistantWindowSession.TITLE, attrs.getTitle());
            assertEquals(0, attrs.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            assertNotEquals(0, attrs.flags & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }
    }

    @Test public void gestureSettingsParticipateInRemoteSyncAndBackupDefaults() {
        assertTrue(Arrays.asList(SettingsStore.BOOLEAN_KEYS).contains(SettingsStore.KEY_ASSISTANT_GESTURE_ENABLED));
        for (String key : new String[]{SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, SettingsStore.KEY_ASSISTANT_GESTURE_HOLD_MS})
            assertTrue(Arrays.asList(SettingsStore.INT_KEYS).contains(key));
        assertFalse(SettingsStore.defaultBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_ENABLED));
        assertEquals(140, SettingsStore.defaultInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP));
        assertEquals(600, SettingsStore.defaultInt(SettingsStore.KEY_ASSISTANT_GESTURE_HOLD_MS));
    }
}
