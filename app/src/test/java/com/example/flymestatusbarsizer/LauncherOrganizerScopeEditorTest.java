package com.example.flymestatusbarsizer;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowAlertDialog;

import java.util.LinkedHashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public final class LauncherOrganizerScopeEditorTest {
    private MainActivity activity;
    private LinearLayout root;
    private LauncherOrganizerScopeEditor editor;
    private LauncherOrganizerScope scope;
    private JSONObject desktop;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(MainActivity.class).get();
        root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        desktop = LauncherOrganizerScopeTest.desktop().put("columns", 4).put("rows", 5)
                .put("layoutItems", new JSONArray()
                        .put(item("first", 9, 0, 0, 1, 1, 0))
                        .put(item("loose", 5, 3, 2, 1, 1, 0))
                        .put(item("100", 5, 0, 0, 2, 2, 2))
                        .put(item("widget", 5, 0, 3, 2, 2, 4)));
        scope = new LauncherOrganizerScope();
        editor = new LauncherOrganizerScopeEditor(activity, root, next -> {
            scope = next;
            editor.render(desktop, scope);
        });
        editor.render(desktop, scope);
    }

    @Test public void pageOrderPositionsAndSpansMatchSnapshotIncludingWhitespace() {
        byDescription(root, "下一页").performClick();
        LauncherOrganizerDesktopGrid grid = grid(root);
        assertNotNull(grid);
        grid.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        grid.layout(0, 0, grid.getMeasuredWidth(), grid.getMeasuredHeight());
        View app = byDescription(grid, "loose，点击勾选");
        View folder = byDescription(grid, "工作，点击展开文件夹，已选 0/2");
        int gap = activity.dp(2);
        int rowHeight = grid.getMeasuredHeight() / 5;
        assertEquals(300 + gap, app.getLeft());
        assertEquals(rowHeight * 2 + gap, app.getTop());
        assertEquals(gap, folder.getLeft());
        assertEquals(200 - gap * 2, folder.getWidth());
        assertEquals(rowHeight * 2 - gap * 2, folder.getHeight());
        assertEquals(3, grid.getChildCount());
        assertNull(grid.getChildAt(2).findViewWithTag("selection"));
    }

    @Test public void appAndFolderBulkSelectionUsePositiveCheckboxes() throws Exception {
        byDescription(root, "下一页").performClick();
        CheckBox folder = (CheckBox) byDescription(root, "工作，整组选中，已选 0/2");
        folder.performClick();
        assertEquals(Set.of("loose", "folder-a", "folder-b"), selected());
        ((CheckBox) byDescription(root, "工作，整组选中，已选 2/2")).performClick();
        assertEquals(Set.of("loose"), selected());
        byDescription(root, "loose，点击勾选").performClick();
        assertEquals(Set.of(), selected());
        assertNotNull(byText(root, "第 2 / 2 页 ▾"));
    }

    @Test public void cancellingFolderChangesLeavesScopeUntouchedAndConfirmCanSelectOne() throws Exception {
        byDescription(root, "下一页").performClick();
        byDescription(root, "工作，点击展开文件夹，已选 0/2").performClick();
        AlertDialog cancelled = ShadowAlertDialog.getLatestAlertDialog();
        byDescription(cancelled.getWindow().getDecorView(), "folder-a，参与整理").performClick();
        cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Set.of("loose"), selected());

        byDescription(root, "工作，点击展开文件夹，已选 0/2").performClick();
        AlertDialog confirmed = ShadowAlertDialog.getLatestAlertDialog();
        byDescription(confirmed.getWindow().getDecorView(), "folder-a，参与整理").performClick();
        confirmed.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Set.of("loose", "folder-a"), selected());
        ((CheckBox) byDescription(root, "工作，整组选中，已选 1/2")).performClick();
        assertEquals(Set.of("loose", "folder-a", "folder-b"), selected());
    }

    @Test public void busyCycleNeverUnlocksPreservedPages() throws Exception {
        assertFalse(byDescription(root, "first，参与整理").isEnabled());
        editor.setBusy(true);
        editor.setBusy(false);
        assertTrue(root.isEnabled());
        assertFalse(byDescription(root, "first，参与整理").isEnabled());
        ((CheckBox) byText(root, "保留首屏（含空白位置）")).setChecked(false);
        assertTrue(byDescription(root, "first，参与整理").isEnabled());
        byDescription(root, "first，点击勾选").performClick();
        assertEquals(Set.of("loose"), selected()); // Unlocked legacy preset had selected the first icon.
    }

    @Test public void newAppsDialogAndPageBulkSelectionDoNotIncludeOtherPages() throws Exception {
        byDescription(root, "下一页").performClick();
        byText(root, "清空本页").performClick();
        assertEquals(Set.of(), selected());
        byText(root, "未放到桌面的应用（已选 0/1）").performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        byText(dialog.getWindow().getDecorView(), "全选").performClick();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertEquals(Set.of("new"), selected());
        byText(root, "全选本页").performClick();
        assertEquals(Set.of("loose", "folder-a", "folder-b", "new"), selected());
    }

    private Set<String> selected() throws Exception {
        Set<String> ids = new LinkedHashSet<>();
        JSONArray apps = scope.selectedApps(desktop);
        for (int i = 0; i < apps.length(); i++) ids.add(apps.getJSONObject(i).getString("id"));
        return ids;
    }

    private static JSONObject item(String id, int screen, int x, int y, int w, int h, int type) throws Exception {
        return new JSONObject().put("id", id).put("name", "").put("container", -100).put("screen", screen)
                .put("cellX", x).put("cellY", y).put("spanX", w).put("spanY", h).put("itemType", type);
    }

    private static View byText(View view, String text) {
        if (view instanceof TextView label && text.equals(label.getText().toString())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = byText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static View byDescription(View view, String text) {
        if (text.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = byDescription(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private static LauncherOrganizerDesktopGrid grid(View view) {
        if (view instanceof LauncherOrganizerDesktopGrid grid) return grid;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            LauncherOrganizerDesktopGrid found = grid(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }
}
