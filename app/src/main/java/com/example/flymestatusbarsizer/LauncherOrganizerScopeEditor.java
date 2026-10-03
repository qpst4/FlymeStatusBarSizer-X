package com.example.flymestatusbarsizer;

import android.app.AlertDialog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Scope choices are edited as a copy; cancelling a dialog never changes the active plan. */
final class LauncherOrganizerScopeEditor {
    private final MainActivity activity;
    private final LinearLayout root;
    private final Consumer<LauncherOrganizerScope> changed;
    private JSONObject desktop;
    private LauncherOrganizerScope scope;

    LauncherOrganizerScopeEditor(MainActivity activity, LinearLayout root, Consumer<LauncherOrganizerScope> changed) {
        this.activity = activity;
        this.root = root;
        this.changed = changed;
    }

    void render(JSONObject desktop, LauncherOrganizerScope scope) {
        this.desktop = desktop;
        this.scope = scope;
        root.removeAllViews();
        if (desktop == null) {
            label("读取桌面后选择整理范围。默认只整理散落图标，并保留首屏。");
            return;
        }
        try {
            label(summary(desktop, scope));
            button("整理方式：" + LauncherOrganizerScope.MODES[scope.mode] + " ▾", () -> {
                LauncherOrganizerScope next = copy();
                new AlertDialog.Builder(activity).setTitle("选择整理方式")
                        .setSingleChoiceItems(LauncherOrganizerScope.MODES, scope.mode, (dialog, which) -> {
                            next.mode = which;
                            changed.accept(next);
                            dialog.dismiss();
                        }).setNegativeButton("取消", null).show();
            });
            toggle("保留首屏（含空白位置）", scope.keepFirstScreen, value -> {
                LauncherOrganizerScope next = copy();
                next.keepFirstScreen = value;
                changed.accept(next);
            });
            if (scope.mode != 2) toggle("同时添加尚未放到桌面的应用", scope.includeNewApps, value -> {
                LauncherOrganizerScope next = copy();
                next.includeNewApps = value;
                changed.accept(next);
            });
            button("保留其他页面（" + scope.keptScreens.size() + " 页）", this::chooseScreens);
            if (scope.mode == 1) {
                button("保留指定文件夹（" + scope.keptFolders.size() + " 个）", this::chooseFolders);
            } else {
                label("当前方式会保留全部已有文件夹及其内容。");
            }
            button("不参与整理的应用（" + scope.keptApps.size() + " 个）", this::chooseApps);
            label("保留文件夹内的应用时，会同时保留整个文件夹，避免内部位置变化。");
            button("查看本次整理的应用", () -> {
                try {
                    JSONArray apps = scope.selectedApps(desktop);
                    List<String> labels = new ArrayList<>();
                    for (int i = 0; i < apps.length(); i++) labels.add(appLabel(apps.getJSONObject(i)));
                    if (labels.isEmpty()) labels.add("当前范围没有可整理的应用，可调整整理方式或保留选项。");
                    new AlertDialog.Builder(activity).setTitle("本次整理 " + apps.length() + " 个应用")
                            .setItems(labels.toArray(new String[0]), null).setPositiveButton("关闭", null).show();
                } catch (Exception error) { showError(error); }
            });
        } catch (Exception error) { label(error.getMessage()); }
    }

    static String summary(JSONObject desktop, LauncherOrganizerScope scope) throws Exception {
        JSONArray selected = scope.selectedApps(desktop);
        int existing = 0;
        int selectedExisting = 0;
        JSONArray apps = desktop.getJSONArray("apps");
        for (int i = 0; i < apps.length(); i++) if (!apps.getJSONObject(i).getBoolean("newApp")) existing++;
        for (int i = 0; i < selected.length(); i++) if (!selected.getJSONObject(i).getBoolean("newApp")) selectedExisting++;
        int added = selected.length() - selectedExisting;
        return "本次整理 " + selectedExisting + " 个已有应用，添加 " + added + " 个应用\n"
                + "原位保留 " + (existing - selectedExisting) + " 个可整理应用，整页保留 "
                + scope.protectedScreens(desktop).size() + " 页；其余 "
                + (apps.length() - existing - added) + " 个应用不添加到桌面。";
    }

    private void chooseScreens() {
        try {
            LauncherOrganizerScope next = copy();
            JSONArray screens = desktop.getJSONArray("screens");
            List<Integer> ids = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < screens.length(); i++) {
                if (i == 0) continue; // The first page has its own switch.
                ids.add(screens.getInt(i));
                labels.add("第 " + (i + 1) + " 页（含空白位置）");
            }
            choose("勾选需要完整保留的页面", ids, labels, next.keptScreens, next);
        } catch (Exception error) { showError(error); }
    }

    private void chooseFolders() {
        try {
            LauncherOrganizerScope next = copy();
            JSONArray folders = desktop.getJSONArray("folders");
            List<Integer> ids = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < folders.length(); i++) {
                JSONObject folder = folders.getJSONObject(i);
                ids.add(folder.getInt("id"));
                labels.add(folder.getString("name") + " · " + screenLabel(folder.getInt("screen")));
            }
            choose("保留文件夹及其中全部内容", ids, labels, next.keptFolders, next);
        } catch (Exception error) { showError(error); }
    }

    private void chooseApps() {
        try {
            LauncherOrganizerScope next = copy();
            JSONArray apps = desktop.getJSONArray("apps");
            List<String> ids = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < apps.length(); i++) {
                JSONObject app = apps.getJSONObject(i);
                ids.add(app.getString("id"));
                labels.add(appLabel(app));
            }
            choose("勾选不参与整理的应用", ids, labels, next.keptApps, next);
        } catch (Exception error) { showError(error); }
    }

    private <T> void choose(String title, List<T> ids, List<String> labels, Set<T> selected, LauncherOrganizerScope next) {
        if (ids.isEmpty()) {
            new AlertDialog.Builder(activity).setMessage("当前没有可选择的项目。").setPositiveButton("确定", null).show();
            return;
        }
        boolean[] checked = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) checked[i] = selected.contains(ids.get(i));
        new AlertDialog.Builder(activity).setTitle(title)
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (dialog, which, value) -> {
                    if (value) selected.add(ids.get(which));
                    else selected.remove(ids.get(which));
                }).setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> changed.accept(next)).show();
    }

    private String appLabel(JSONObject app) throws Exception {
        String location = "尚未放到桌面";
        if (!app.getBoolean("newApp")) {
            location = screenLabel(app.getInt("screen"));
            int folderId = app.getInt("folderId");
            JSONArray folders = desktop.getJSONArray("folders");
            for (int i = 0; i < folders.length(); i++) {
                JSONObject folder = folders.getJSONObject(i);
                if (folder.getInt("id") == folderId) { location += " / " + folder.getString("name"); break; }
            }
        }
        return app.getString("name") + "\n" + location + " · " + app.getString("package");
    }

    private String screenLabel(int screen) throws Exception {
        JSONArray screens = desktop.getJSONArray("screens");
        for (int i = 0; i < screens.length(); i++) if (screens.getInt(i) == screen) return "第 " + (i + 1) + " 页";
        return "桌面";
    }

    private LauncherOrganizerScope copy() {
        try { return new LauncherOrganizerScope(scope.toJson()); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    void setBusy(boolean busy) { setEnabled(root, !busy); }

    private static void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) setEnabled(group.getChildAt(i), enabled);
        }
    }

    private void toggle(String title, boolean checked, Consumer<Boolean> changed) {
        CheckBox box = new CheckBox(activity);
        box.setText(title);
        box.setTextColor(activity.textColor());
        box.setChecked(checked);
        box.setOnCheckedChangeListener((button, value) -> changed.accept(value));
        root.addView(box, PageViewUtils.matchWrap());
    }

    private void button(String title, Runnable action) {
        TextView button = activity.filledButton(title, activity.surfaceSoftColor(), activity.primaryColor());
        activity.setTapClickListener(button, view -> action.run());
        root.addView(button, activity.matchWrapWithTop(8));
    }

    private void label(String text) {
        TextView view = new TextView(activity);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(activity.subtextColor());
        root.addView(view, activity.matchWrapWithTop(8));
    }

    private void showError(Exception error) {
        new AlertDialog.Builder(activity).setMessage(error.getMessage()).setPositiveButton("确定", null).show();
    }
}
