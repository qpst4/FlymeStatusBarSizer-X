package com.example.flymestatusbarsizer.feature.launcher.organizer;

import org.json.JSONArray;
import org.json.JSONObject;

/** Export only display metadata, never raw launcher rows, intents or icon blobs. */
final class LauncherOrganizerDesktop {
    static JSONObject exported(JSONObject state) throws Exception {
        JSONArray apps = state.getJSONArray("apps");
        JSONArray result = new JSONArray();
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            JSONObject row = app.getJSONObject("row");
            result.put(new JSONObject().put("id", app.getString("id")).put("name", app.getString("name"))
                    .put("package", app.getString("package")).put("newApp", app.getBoolean("newApp"))
                    .put("screen", app.getInt("screen")).put("folderId", app.getInt("folderId"))
                    .put("rank", row.optInt("rank", 0)));
        }
        JSONArray layout = new JSONArray();
        JSONArray items = state.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            int container = row.optInt("container", -1);
            int screen = row.optInt("screen", -1);
            if (container != -101 && (container != -100 || screen < 0 || screen >= 100_000_000)) continue;
            layout.put(new JSONObject().put("id", Integer.toString(row.getInt("_id")))
                    .put("name", row.optString("title", "")).put("itemType", row.optInt("itemType", -1))
                    .put("container", container).put("screen", screen)
                    .put("cellX", row.getInt("cellX")).put("cellY", row.getInt("cellY"))
                    .put("spanX", row.optInt("spanX", 1)).put("spanY", row.optInt("spanY", 1)));
        }
        return new JSONObject().put("hash", state.getString("hash")).put("apps", result)
                .put("columns", state.getInt("columns")).put("rows", state.getInt("rows"))
                .put("screens", state.getJSONArray("screens")).put("folders", state.getJSONArray("folders"))
                .put("layoutItems", layout).put("scopeVersion", LauncherOrganizerScope.VERSION);
    }
}
