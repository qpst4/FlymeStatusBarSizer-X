package com.example.flymestatusbarsizer;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class LauncherOrganizerDesktopTest {
    @Test public void exportsPageOrderCellsSpansAndFolderRankWithoutRawRows() throws Exception {
        JSONObject row = row(1, -100, 5, 3, 2, 1, 1).put("intent", "private intent").put("icon", "private blob");
        JSONObject folder = row(100, -100, 9, 0, 0, 2, 2).put("itemType", 2);
        JSONObject child = row(2, 100, 0, 1, 0, 1, 1).put("rank", 4);
        JSONObject widget = row(200, -100, 5, 0, 3, 3, 2).put("itemType", 4);
        JSONObject dock = row(300, -101, 1, 1, 0, 1, 1);
        JSONObject hidden = row(400, -100, 100_000_000, 0, 0, 1, 1);
        JSONObject state = new JSONObject().put("hash", "example").put("columns", 4).put("rows", 5)
                .put("screens", new JSONArray().put(9).put(5)).put("folders", new JSONArray())
                .put("items", new JSONArray().put(row).put(folder).put(child).put(widget).put(dock).put(hidden))
                .put("apps", new JSONArray().put(app("1", row, 5, -1)).put(app("2", child, 9, 100)));
        JSONObject exported = LauncherOrganizerDesktop.exported(state);
        assertEquals("[9,5]", exported.getJSONArray("screens").toString());
        assertEquals(4, exported.getJSONArray("layoutItems").length());
        JSONObject appPosition = exported.getJSONArray("layoutItems").getJSONObject(0);
        assertEquals(3, appPosition.getInt("cellX"));
        assertEquals(2, appPosition.getInt("cellY"));
        assertEquals(2, exported.getJSONArray("layoutItems").getJSONObject(1).getInt("spanX"));
        assertEquals(3, exported.getJSONArray("layoutItems").getJSONObject(2).getInt("spanX"));
        assertEquals(4, exported.getJSONArray("apps").getJSONObject(1).getInt("rank"));
        assertFalse(exported.toString().contains("private"));
        assertFalse(exported.getJSONArray("apps").getJSONObject(0).has("row"));
        assertEquals(LauncherOrganizerScope.VERSION, exported.getInt("scopeVersion"));
    }

    private static JSONObject row(int id, int container, int screen, int x, int y, int w, int h) throws Exception {
        return new JSONObject().put("_id", id).put("container", container).put("screen", screen).put("itemType", 0)
                .put("cellX", x).put("cellY", y).put("spanX", w).put("spanY", h);
    }

    private static JSONObject app(String id, JSONObject row, int screen, int folder) throws Exception {
        return new JSONObject().put("id", id).put("name", id).put("package", "example.app").put("newApp", false)
                .put("screen", screen).put("folderId", folder).put("row", row);
    }
}
