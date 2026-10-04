package com.example.flymestatusbarsizer.feature.launcher.organizer;

import com.example.flymestatusbarsizer.MainActivity;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public final class LauncherOrganizerPageTest {
    private TestActivity activity;
    private SharedPreferences prefs;
    private LinearLayout root;

    // Card expansion icons are unrelated to the range/preview lifecycle under test.
    public static class TestActivity extends MainActivity {
        @Override public View buildSectionCard(String title, String subtitle, View content) { return content; }
    }

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(TestActivity.class).get();
        prefs = activity.getSharedPreferences("launcher_organizer_ai", Context.MODE_PRIVATE);
        JSONObject desktop = LauncherOrganizerScopeTest.desktop().put("columns", 4).put("rows", 5).put("hash", "example");
        JSONObject group = new JSONObject().put("name", "生活").put("apps", new JSONArray().put("loose"));
        JSONObject preview = new JSONObject().put("desktop", desktop).put("groups", new JSONArray().put(group))
                .put("scope", new LauncherOrganizerScope().toJson());
        prefs.edit().clear().putString("preview", preview.toString()).commit();
    }

    @Test public void reopeningRestoresScopedPreview() throws Exception {
        LauncherOrganizerPage page = open();
        assertNotNull(field(page, "desktop"));
        JSONArray groups = (JSONArray) field(page, "groups");
        assertEquals("loose", groups.getJSONObject(0).getJSONArray("apps").getString(0));
        assertTrue(firstScreenSwitch(root).isChecked());
    }

    @Test public void changingRangeInvalidatesAndPersistsPreviewBeforeReopening() throws Exception {
        LauncherOrganizerPage page = open();
        firstScreenSwitch(root).setChecked(false);
        assertNull(field(page, "groups"));
        JSONObject stored = new JSONObject(prefs.getString("preview", ""));
        assertTrue(stored.isNull("groups"));
        assertFalse(stored.getJSONObject("scope").getBoolean("keepFirstScreen"));
        LauncherOrganizerPage reopened = open();
        assertNull(field(reopened, "groups"));
        assertFalse(firstScreenSwitch(root).isChecked());
    }

    @Test public void legacyPreviewCannotBeAppliedAsAnUnscopedPlan() throws Exception {
        JSONObject stored = new JSONObject(prefs.getString("preview", ""));
        stored.remove("scope");
        stored.getJSONObject("desktop").remove("scopeVersion");
        prefs.edit().putString("preview", stored.toString()).commit();
        LauncherOrganizerPage page = open();
        assertNull(field(page, "desktop"));
        assertNull(field(page, "groups"));
    }

    private LauncherOrganizerPage open() throws Exception {
        root = new LinearLayout(activity);
        Constructor<LauncherOrganizerPage> constructor = LauncherOrganizerPage.class
                .getDeclaredConstructor(MainActivity.class, LinearLayout.class);
        constructor.setAccessible(true);
        return constructor.newInstance(activity, root);
    }

    private static Object field(LauncherOrganizerPage page, String name) throws Exception {
        Field field = LauncherOrganizerPage.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(page);
    }

    private static CheckBox firstScreenSwitch(View view) {
        if (view instanceof CheckBox box && box.getText().toString().startsWith("保留首屏")) return box;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                CheckBox box = firstScreenSwitch(group.getChildAt(i));
                if (box != null) return box;
            }
        }
        return null;
    }
}
