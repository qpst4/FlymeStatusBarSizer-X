package com.example.flymestatusbarsizer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** The same scope is enforced before sending apps to AI and again inside the launcher. */
final class LauncherOrganizerScope {
    static final int VERSION = 2;
    static final int MANUAL = 3;
    static final String[] MODES = {"只整理桌面散落图标", "整理桌面图标和文件夹", "只添加未放到桌面的应用", "手动勾选应用"};
    int mode;
    boolean keepFirstScreen = true;
    boolean includeNewApps;
    final Set<Integer> keptScreens = new LinkedHashSet<>();
    final Set<Integer> keptFolders = new LinkedHashSet<>();
    final Set<String> keptApps = new LinkedHashSet<>();
    final Set<String> selectedAppIds = new LinkedHashSet<>();

    LauncherOrganizerScope() { }

    LauncherOrganizerScope(JSONObject json) throws Exception {
        int version = json.getInt("version");
        if (version != 1 && version != VERSION) throw new IllegalArgumentException("整理范围版本不兼容，请重新读取桌面");
        mode = json.getInt("mode");
        if (mode < 0 || mode >= MODES.length) throw new IllegalArgumentException("未知整理范围");
        keepFirstScreen = json.getBoolean("keepFirstScreen");
        includeNewApps = json.getBoolean("includeNewApps");
        JSONArray screens = json.getJSONArray("keptScreens");
        for (int i = 0; i < screens.length(); i++) keptScreens.add(screens.getInt(i));
        JSONArray folders = json.getJSONArray("keptFolders");
        for (int i = 0; i < folders.length(); i++) keptFolders.add(folders.getInt(i));
        JSONArray apps = json.getJSONArray("keptApps");
        for (int i = 0; i < apps.length(); i++) keptApps.add(apps.getString(i));
        if (version >= 2) {
            JSONArray selected = json.getJSONArray("selectedAppIds");
            for (int i = 0; i < selected.length(); i++) selectedAppIds.add(selected.getString(i));
        } else if (mode == MANUAL) {
            throw new IllegalArgumentException("旧版整理范围不支持手动勾选");
        }
    }

    JSONObject toJson() throws Exception {
        return new JSONObject().put("version", VERSION).put("mode", mode)
                .put("keepFirstScreen", keepFirstScreen).put("includeNewApps", includeNewApps)
                .put("keptScreens", new JSONArray(keptScreens)).put("keptFolders", new JSONArray(keptFolders))
                .put("keptApps", new JSONArray(keptApps)).put("selectedAppIds", new JSONArray(selectedAppIds));
    }

    static void requireSnapshot(JSONObject desktop) {
        if (desktop.optInt("scopeVersion", 0) != VERSION) {
            throw new IllegalStateException("桌面仍在运行旧版模块，请重启桌面后重新读取");
        }
    }

    Set<Integer> protectedScreens(JSONObject desktop) throws Exception {
        requireSnapshot(desktop);
        Set<Integer> result = new LinkedHashSet<>();
        JSONArray screens = desktop.getJSONArray("screens");
        for (int i = 0; i < screens.length(); i++) {
            int screen = screens.getInt(i);
            if ((keepFirstScreen && i == 0) || keptScreens.contains(screen)) result.add(screen);
        }
        return result;
    }

    JSONArray selectedApps(JSONObject desktop) throws Exception {
        Set<Integer> protectedScreens = protectedScreens(desktop);
        JSONArray result = new JSONArray();
        JSONArray apps = desktop.getJSONArray("apps");
        if (mode == MANUAL) {
            for (int i = 0; i < apps.length(); i++) {
                JSONObject app = apps.getJSONObject(i);
                if (selectedAppIds.contains(app.getString("id"))
                        && (app.getBoolean("newApp") || !protectedScreens.contains(app.getInt("screen")))) result.put(app);
            }
            return result;
        }
        Set<Integer> protectedFolders = new HashSet<>(keptFolders);
        // Removing neighbours can make the launcher compact a folder's ranks on reload.
        // Preserve the entire folder when an app inside it has a fixed position.
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            if (keptApps.contains(app.getString("id")) && app.getInt("folderId") >= 0) {
                protectedFolders.add(app.getInt("folderId"));
            }
        }
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            if (keptApps.contains(app.getString("id"))) continue;
            if (app.getBoolean("newApp")) {
                if (mode == 2 || includeNewApps) result.put(app);
                continue;
            }
            if (mode == 2 || protectedScreens.contains(app.getInt("screen"))) continue;
            int folder = app.getInt("folderId");
            if (folder >= 0 && (mode == 0 || protectedFolders.contains(folder))) continue;
            result.put(app);
        }
        return result;
    }

    /** Start from the effective selection, so the first tap never selects unrelated apps. */
    void editSelection(JSONObject desktop, Set<String> ids, boolean selected) throws Exception {
        if (mode != MANUAL) {
            JSONArray current = selectedApps(desktop);
            selectedAppIds.clear();
            for (int i = 0; i < current.length(); i++) selectedAppIds.add(current.getJSONObject(i).getString("id"));
            mode = MANUAL;
            keptApps.clear();
            keptFolders.clear();
            includeNewApps = false;
        }
        if (selected) selectedAppIds.addAll(ids);
        else selectedAppIds.removeAll(ids);
    }

    void retainAvailable(JSONObject desktop) throws Exception {
        requireSnapshot(desktop);
        Set<Integer> screens = new HashSet<>();
        JSONArray screenIds = desktop.getJSONArray("screens");
        // The first screen is controlled exclusively by keepFirstScreen.
        for (int i = 1; i < screenIds.length(); i++) screens.add(screenIds.getInt(i));
        keptScreens.retainAll(screens);
        Set<Integer> folders = new HashSet<>();
        JSONArray folderInfo = desktop.getJSONArray("folders");
        for (int i = 0; i < folderInfo.length(); i++) folders.add(folderInfo.getJSONObject(i).getInt("id"));
        keptFolders.retainAll(folders);
        Set<String> apps = new HashSet<>();
        JSONArray appInfo = desktop.getJSONArray("apps");
        for (int i = 0; i < appInfo.length(); i++) apps.add(appInfo.getJSONObject(i).getString("id"));
        keptApps.retainAll(apps);
        selectedAppIds.retainAll(apps);
    }

    /** Only folders emptied by this operation may be deleted; untouched empty folders survive. */
    static Set<Integer> removableFolders(JSONArray items, Set<Integer> moving) throws Exception {
        Set<Integer> touched = new HashSet<>();
        Set<Integer> occupied = new HashSet<>();
        Set<Integer> folders = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject row = items.getJSONObject(i);
            int container = row.optInt("container", -1);
            if (moving.contains(row.getInt("_id"))) touched.add(container);
            else occupied.add(container);
            if (row.optInt("itemType", -1) == 2 && container == -100
                    && row.optInt("category", -1) != 13 && row.optInt("category", -1) != 14) {
                folders.add(row.getInt("_id"));
            }
        }
        touched.retainAll(folders);
        touched.removeAll(occupied);
        return touched;
    }
}
