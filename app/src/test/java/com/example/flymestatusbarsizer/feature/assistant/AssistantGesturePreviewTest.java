package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
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

    @Test public void changingSideUpdatesPreviewAndRestartsTimeout() throws ReflectiveOperationException {
        for (int side : new int[]{SettingsStore.ASSISTANT_GESTURE_SIDE_RIGHT,
                SettingsStore.ASSISTANT_GESTURE_SIDE_BOTH, SettingsStore.ASSISTANT_GESTURE_SIDE_LEFT}) {
            prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_SIDE, side).apply();
            shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(marker.getCallback());
            assertEquals(side, AssistantReflection.get(marker, "side"));
            advance(4000);
        }
        advance(1000);
        assertNull(marker.getCallback());
    }

    @Test public void markersUseSelectedScreenEdgesAndMirrorDistance() {
        float density = preview.getResources().getDisplayMetrics().density;
        for (int side : new int[]{SettingsStore.ASSISTANT_GESTURE_SIDE_LEFT,
                SettingsStore.ASSISTANT_GESTURE_SIDE_RIGHT, SettingsStore.ASSISTANT_GESTURE_SIDE_BOTH}) {
            prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_SIDE, side)
                    .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, 120).apply();
            shadowOf(Looper.getMainLooper()).idle();
            preview.showPreview();
            marker.setBounds(0, 0, 1000, 1800);
            java.util.Set<Float> verticalLines = new java.util.HashSet<>();
            java.util.List<String> labels = new java.util.ArrayList<>();
            marker.draw(new Canvas() {
                @Override public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
                    if (x1 == x2 && y1 != y2) verticalLines.add(x1);
                }
                @Override public void drawText(String text, float x, float y, Paint paint) {
                    labels.add(text);
                    assertTrue(x >= 0);
                    assertTrue(x + paint.measureText(text) <= 1000);
                }
            });
            assertEquals(side != SettingsStore.ASSISTANT_GESTURE_SIDE_RIGHT,
                    verticalLines.contains(120 * density));
            assertEquals(side != SettingsStore.ASSISTANT_GESTURE_SIDE_LEFT,
                    verticalLines.contains(1000 - 120 * density));
            assertEquals(side == SettingsStore.ASSISTANT_GESTURE_SIDE_BOTH ? 2 : 1, labels.size());
        }
    }

    @Test public void sideSelectionKeepsUnselectedEdgeAsOrdinaryBack() {
        assertTrue(SettingsStore.assistantGestureAllowsSide(SettingsStore.ASSISTANT_GESTURE_SIDE_LEFT, true));
        assertFalse(SettingsStore.assistantGestureAllowsSide(SettingsStore.ASSISTANT_GESTURE_SIDE_LEFT, false));
        assertFalse(SettingsStore.assistantGestureAllowsSide(SettingsStore.ASSISTANT_GESTURE_SIDE_RIGHT, true));
        assertTrue(SettingsStore.assistantGestureAllowsSide(SettingsStore.ASSISTANT_GESTURE_SIDE_RIGHT, false));
        assertTrue(SettingsStore.assistantGestureAllowsSide(SettingsStore.ASSISTANT_GESTURE_SIDE_BOTH, true));
        assertTrue(SettingsStore.assistantGestureAllowsSide(SettingsStore.ASSISTANT_GESTURE_SIDE_BOTH, false));
        assertTrue(SettingsStore.assistantGestureAllowsSide(-1, true));
        assertFalse(SettingsStore.assistantGestureAllowsSide(99, false));
        assertTrue(java.util.Arrays.asList(SettingsStore.INT_KEYS).contains(SettingsStore.KEY_ASSISTANT_GESTURE_SIDE));
    }

    @Test public void verticalSettingsRefreshPreviewAndRestartTimeout() throws ReflectiveOperationException {
        prefs.edit().putBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED, true).apply();
        shadowOf(Looper.getMainLooper()).idle();
        assertNotNull(marker.getCallback());
        assertEquals(true, AssistantReflection.get(marker, "vertical"));
        assertEquals(true, AssistantReflection.get(marker, "verticalLimitEnabled"));
        advance(4000);
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP, 96).apply();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(96, AssistantReflection.get(marker, "verticalLimitDp"));
        advance(1000);
        assertNotNull(marker.getCallback());
        prefs.edit().putBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED, false).apply();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(false, AssistantReflection.get(marker, "verticalLimitEnabled"));
        advance(4999);
        assertNotNull(marker.getCallback());
        advance(1);
        assertNull(marker.getCallback());
        preview.showPreview();
        assertEquals(false, AssistantReflection.get(marker, "vertical"));
    }

    @Test @Config(qualifiers = "xhdpi")
    public void verticalPreviewUsesDpAboveAndBelowOriginWithoutClampingBoundaryPositions() {
        float density = preview.getResources().getDisplayMetrics().density;
        for (boolean enabled : new boolean[]{false, true}) {
            prefs.edit().putBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED, enabled).apply();
            for (int height : new int[]{1800, 400}) {
                for (int limit : new int[]{8, 48, 240}) {
                    prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP, limit).apply();
                    shadowOf(Looper.getMainLooper()).idle();
                    preview.showVerticalPreview();
                    marker.setBounds(0, 0, 1000, height);
                    java.util.Set<Float> boundaries = new java.util.HashSet<>();
                    java.util.List<String> labels = new java.util.ArrayList<>();
                    float[] origin = {Float.NaN};
                    marker.draw(new Canvas() {
                        @Override public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
                            assertEquals(0f, x1, 0);
                            assertEquals(1000f, x2, 0);
                            assertEquals(y1, y2, 0);
                            if (paint.getPathEffect() != null) origin[0] = y1;
                            else boundaries.add(y1);
                        }
                        @Override public void drawText(String text, float x, float y, Paint paint) {
                            labels.add(text);
                            assertTrue(x >= 0);
                            assertTrue(x + paint.measureText(text) <= 1000);
                            assertTrue(y + paint.ascent() >= 0);
                            assertTrue(y + paint.descent() <= height);
                        }
                    });
                    assertTrue(Float.isFinite(origin[0]));
                    assertEquals(2, boundaries.size());
                    assertTrue(boundaries.contains(origin[0] - limit * density));
                    assertTrue(boundaries.contains(origin[0] + limit * density));
                    assertEquals(3, labels.size());
                    assertTrue(labels.get(0).startsWith("上移 " + limit + "dp"));
                    assertTrue(labels.get(1).startsWith("下移 " + limit + "dp"));
                    assertTrue(labels.get(2).contains(enabled ? "实际随按下位置" : "限制未开启"));
                    if (height == 400 && limit == 240) {
                        assertTrue(labels.get(0).contains("屏幕外"));
                        assertTrue(labels.get(1).contains("屏幕外"));
                    }
                }
            }
        }
    }

    @Test public void verticalPreviewCleansUpOnFocusLossPageHideAndDetach() {
        preview.showVerticalPreview();
        assertNotNull(marker.getCallback());
        controller.windowFocusChanged(false);
        assertNull(marker.getCallback());
        prefs.edit().putInt(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_DP, 80).apply();
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(marker.getCallback());
        controller.windowFocusChanged(true);
        assertNull(marker.getCallback());
        preview.showVerticalPreview();
        pageHost.setVisibility(View.GONE);
        assertNull(marker.getCallback());
        pageHost.setVisibility(View.VISIBLE);
        preview.showVerticalPreview();
        assertNotNull(marker.getCallback());
        pageHost.removeView(preview);
        assertNull(marker.getCallback());
        prefs.edit().putBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_VERTICAL_LIMIT_ENABLED, true).apply();
        advance(5000);
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
