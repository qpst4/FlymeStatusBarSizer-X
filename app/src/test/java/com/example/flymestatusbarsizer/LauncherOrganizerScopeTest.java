package com.example.flymestatusbarsizer;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class LauncherOrganizerScopeTest {
    @Test public void defaultScopeKeepsFirstPageFoldersAndOffDesktopApps() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        assertEquals(List.of("loose"), ids(scope.selectedApps(desktop())));
        assertEquals(Set.of(9), scope.protectedScreens(desktop()));
    }

    @Test public void pageFolderAndAppExclusionsComposeAndSurviveSerialization() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.mode = 1;
        scope.keepFirstScreen = false;
        scope.includeNewApps = true;
        scope.keptFolders.add(100);
        scope.keptApps.add("loose");
        scope.keptScreens.add(9);
        LauncherOrganizerScope restored = new LauncherOrganizerScope(scope.toJson());
        assertEquals(List.of("new"), ids(restored.selectedApps(desktop())));
        assertEquals(Set.of(9), restored.protectedScreens(desktop()));
    }

    @Test public void keepingAnAppInsideAFolderAlsoKeepsItsNeighbours() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.mode = 1;
        scope.keptApps.add("folder-a");
        assertEquals(List.of("loose"), ids(scope.selectedApps(desktop())));
    }

    @Test public void newOnlyModeNeverMovesExistingApps() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.mode = 2;
        scope.keepFirstScreen = false;
        assertEquals(List.of("new"), ids(scope.selectedApps(desktop())));
        scope.keptApps.add("new");
        assertEquals(0, scope.selectedApps(desktop()).length());
    }

    @Test public void fullModeMustExplicitlyOptIntoAddingMissingIcons() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.mode = 1;
        scope.keepFirstScreen = false;
        assertEquals(List.of("first", "loose", "folder-a", "folder-b"), ids(scope.selectedApps(desktop())));
        scope.includeNewApps = true;
        assertEquals(5, scope.selectedApps(desktop()).length());
    }

    @Test public void preservingAllPagesProducesAnEmptyScopeWithoutChangingTheSnapshot() throws Exception {
        JSONObject desktop = desktop();
        String before = desktop.toString();
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.mode = 1;
        scope.keptScreens.add(5);
        assertEquals(0, scope.selectedApps(desktop).length());
        assertEquals(before, desktop.toString());
    }

    @Test public void scopeCannotBeUsedWithAnOldLauncherSnapshot() throws Exception {
        JSONObject old = desktop();
        old.remove("scopeVersion");
        assertThrows(IllegalStateException.class, () -> new LauncherOrganizerScope().selectedApps(old));
        assertThrows(Exception.class, () -> new LauncherOrganizerScope(new JSONObject()));
    }

    @Test public void refreshingSnapshotDropsRemovedSelections() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.keptScreens.addAll(Set.of(5, 9, 20));
        scope.keptFolders.addAll(Set.of(100, 200));
        scope.keptApps.addAll(Set.of("loose", "uninstalled"));
        scope.retainAvailable(desktop());
        assertEquals(Set.of(5), scope.keptScreens);
        assertEquals(Set.of(100), scope.keptFolders);
        assertEquals(Set.of("loose"), scope.keptApps);
    }

    @Test public void aiCannotMoveAnAppOutsideTheSelectedScope() throws Exception {
        JSONArray selected = new LauncherOrganizerScope().selectedApps(desktop());
        JSONArray groups = new JSONArray().put(new JSONObject().put("name", "错误分类")
                .put("apps", new JSONArray().put("first")));
        assertThrows(IllegalArgumentException.class, () -> LauncherOrganizerProvider.validateGroups(selected, groups));
        assertEquals(List.of("loose"), LauncherOrganizerProvider.validateGroups(selected, new JSONArray()));
    }

    @Test public void untouchedEmptyAndPartlyOccupiedFoldersAreNeverDeleted() throws Exception {
        JSONArray items = new JSONArray()
                .put(row(100, -100, 2)).put(row(101, -100, 2)).put(row(102, -100, 2))
                .put(row(1, 100, 0)).put(row(2, 101, 0)).put(row(3, 101, 6));
        assertEquals(Set.of(100), LauncherOrganizerScope.removableFolders(items, Set.of(1, 2)));
        assertEquals(Set.of(), LauncherOrganizerScope.removableFolders(items, Set.of()));
    }

    @Test public void firstManualEditRetainsOnlyThePreviousEffectiveSelection() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.editSelection(desktop(), Set.of("folder-a"), true);
        assertEquals(LauncherOrganizerScope.MANUAL, scope.mode);
        assertEquals(List.of("loose", "folder-a"), ids(scope.selectedApps(desktop())));
        scope.editSelection(desktop(), Set.of("loose"), false);
        assertEquals(List.of("folder-a"), ids(scope.selectedApps(desktop())));
    }

    @Test public void manualFolderSelectionCanBePartialAndSurvivesSerialization() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.editSelection(desktop(), Set.of("folder-a", "folder-b"), true);
        scope.editSelection(desktop(), Set.of("folder-b", "loose"), false);
        LauncherOrganizerScope restored = new LauncherOrganizerScope(scope.toJson());
        assertEquals(List.of("folder-a"), ids(restored.selectedApps(desktop())));
        JSONArray groups = new JSONArray().put(new JSONObject().put("name", "分类")
                .put("apps", new JSONArray().put("folder-b")));
        assertThrows(IllegalArgumentException.class, () -> LauncherOrganizerProvider.validateGroups(restored.selectedApps(desktop()), groups));
    }

    @Test public void manualSelectionStillHonoursLockedScreensIncludingWhitespace() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.editSelection(desktop(), Set.of("first", "folder-a", "new"), true);
        scope.keptScreens.add(5);
        assertEquals(List.of("new"), ids(scope.selectedApps(desktop())));
        assertEquals(Set.of(9, 5), scope.protectedScreens(desktop()));
        scope.keepFirstScreen = false;
        assertEquals(List.of("first", "new"), ids(scope.selectedApps(desktop())));
    }

    @Test public void manualEmptySelectionStaysEmptyAndRefreshDoesNotSelectNewArrivals() throws Exception {
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.editSelection(desktop(), Set.of("loose"), false);
        LauncherOrganizerScope restored = new LauncherOrganizerScope(scope.toJson());
        restored.retainAvailable(desktop());
        assertEquals(0, restored.selectedApps(desktop()).length());
        restored.editSelection(desktop(), Set.of("folder-a", "uninstalled"), true);
        restored.retainAvailable(desktop());
        assertEquals(Set.of("folder-a"), restored.selectedAppIds);
    }

    @Test public void duplicateAppPackagesAreSelectedByDesktopEntryId() throws Exception {
        JSONObject desktop = desktop();
        desktop.getJSONArray("apps").getJSONObject(3).put("package", "example.folder-a");
        LauncherOrganizerScope scope = new LauncherOrganizerScope();
        scope.editSelection(desktop, Set.of("folder-a"), true);
        assertEquals(List.of("loose", "folder-a"), ids(scope.selectedApps(desktop)));
    }

    @Test public void oldPreferencesMigrateButOldSnapshotsRequireReload() throws Exception {
        LauncherOrganizerScope original = new LauncherOrganizerScope();
        original.mode = 1;
        original.keptApps.add("folder-a");
        JSONObject json = original.toJson().put("version", 1);
        json.remove("selectedAppIds");
        LauncherOrganizerScope restored = new LauncherOrganizerScope(json);
        assertEquals(List.of("loose"), ids(restored.selectedApps(desktop())));
        assertEquals(LauncherOrganizerScope.VERSION, restored.toJson().getInt("version"));
        assertThrows(IllegalStateException.class, () -> restored.selectedApps(desktop().put("scopeVersion", 1)));
    }

    private static JSONObject row(int id, int container, int type) throws Exception {
        return new JSONObject().put("_id", id).put("container", container).put("itemType", type);
    }

    static JSONObject desktop() throws Exception {
        return new JSONObject().put("scopeVersion", LauncherOrganizerScope.VERSION)
                .put("screens", new JSONArray().put(9).put(5))
                .put("folders", new JSONArray().put(new JSONObject().put("id", 100).put("name", "工作").put("screen", 5)))
                .put("apps", new JSONArray().put(app("first", 9, -1, false))
                        .put(app("loose", 5, -1, false)).put(app("folder-a", 5, 100, false))
                        .put(app("folder-b", 5, 100, false)).put(app("new", -1, -1, true)));
    }

    private static JSONObject app(String id, int screen, int folder, boolean isNew) throws Exception {
        return new JSONObject().put("id", id).put("name", id).put("package", "example." + id)
                .put("screen", screen).put("folderId", folder).put("newApp", isNew);
    }

    private static List<String> ids(JSONArray apps) throws Exception {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < apps.length(); i++) result.add(apps.getJSONObject(i).getString("id"));
        return result;
    }
}
