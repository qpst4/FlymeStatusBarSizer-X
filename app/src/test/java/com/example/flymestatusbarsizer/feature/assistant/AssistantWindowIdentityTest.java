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
        overlay.token = null;
        overlay.packageName = AssistantProtocol.PACKAGE;
        overlay.type = 2038;
        window.setAttributes(overlay);
        assertSame(launcherToken, window.getAttributes().token);
        AssistantWindowSession.applyIdentityAndAttributes(window, overlay);
        assertNull(window.getAttributes().token);
        assertEquals(AssistantProtocol.PACKAGE, window.getAttributes().packageName);
        assertEquals(2038, window.getAttributes().type);
        AssistantWindowSession.applyIdentityAndAttributes(window, original);
        assertSame(launcherToken, window.getAttributes().token);
        assertEquals("com.meizu.flyme.launcher", window.getAttributes().packageName);
        assertEquals(4, window.getAttributes().type);
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
