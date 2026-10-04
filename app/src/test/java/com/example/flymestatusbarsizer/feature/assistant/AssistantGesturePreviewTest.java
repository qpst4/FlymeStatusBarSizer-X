package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;

import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.time.Duration;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantGesturePreviewTest {
    private ActivityController<Activity> controller;
    private FrameLayout pageHost;
    private AssistantGesturePreviewLayout preview;
    private SharedPreferences prefs;
    private Drawable marker;
    private String previousWindowVisibility;

    @Before public void setUp() throws ReflectiveOperationException {
        previousWindowVisibility = System.getProperty("robolectric.areWindowsMarkedVisible");
        System.setProperty("robolectric.areWindowsMarkedVisible", "true");
        controller = Robolectric.buildActivity(Activity.class).setup();
        Activity activity = controller.get();
        prefs = activity.getSharedPreferences("preview-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        preview = new AssistantGesturePreviewLayout(activity, prefs, Color.BLUE);
        pageHost = new FrameLayout(activity);
        pageHost.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        activity.setContentView(pageHost);
        controller.visible().windowFocusChanged(true);
        shadowOf(Looper.getMainLooper()).idle();
        marker = (Drawable) AssistantReflection.get(preview, "marker");
    }

    @After public void tearDown() {
        controller.pause().stop().destroy();
        if (previousWindowVisibility == null) System.clearProperty("robolectric.areWindowsMarkedVisible");
        else System.setProperty("robolectric.areWindowsMarkedVisible", previousWindowVisibility);
    }

    @Test public void repeatedShowRestartsFiveSecondTimeout() {
        preview.showPreview();
        assertNotNull(marker.getCallback());
        advance(4000);
        preview.showPreview();
        advance(1000);
        assertNotNull(marker.getCallback());
        advance(3999);
        assertNotNull(marker.getCallback());
        advance(1);
        assertNull(marker.getCallback());
    }

    @Test public void adjustingDistanceShowsLatestValueAndRestartsTimeout() throws ReflectiveOperationException {
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, 120).apply();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(marker.getCallback());
        assertEquals(120, AssistantReflection.get(marker, "distanceDp"));
        advance(4000);
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, 240).apply();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(240, AssistantReflection.get(marker, "distanceDp"));
        advance(1000);
        assertNotNull(marker.getCallback());
        advance(4000);
        assertNull(marker.getCallback());
    }

    @Test public void leavingPageOrLosingFocusClearsMarkerWithoutRestoringIt() {
        preview.showPreview();
        assertNotNull(marker.getCallback());
        pageHost.setVisibility(View.GONE);
        assertNull(marker.getCallback());
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, 200).apply();
        shadowOf(Looper.getMainLooper()).idle();
        pageHost.setVisibility(View.VISIBLE);
        assertNull(marker.getCallback());

        preview.showPreview();
        assertNotNull(marker.getCallback());
        controller.windowFocusChanged(false);
        assertNull(marker.getCallback());
        controller.windowFocusChanged(true);
        assertNull(marker.getCallback());

        preview.showPreview();
        preview.setVisibility(View.GONE);
        assertNull(marker.getCallback());
    }

    @Test public void detachingClearsMarkerAndPendingTimeout() {
        preview.showPreview();
        assertNotNull(marker.getCallback());
        pageHost.removeView(preview);
        assertNull(marker.getCallback());
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, 160).apply();
        advance(5000);
        assertNull(marker.getCallback());
    }

    private void advance(long millis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }
}
