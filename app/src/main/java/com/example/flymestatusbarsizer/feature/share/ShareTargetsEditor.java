package com.example.flymestatusbarsizer.feature.share;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.GridView;
import android.widget.Switch;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.BiFunction;
import java.util.EnumMap;

public final class ShareTargetsEditor {
    private final MainActivity activity;
    private final Executor scanner;
    private final BiFunction<android.content.Context, ShareContentType, ShareTargetCatalog.Snapshot> catalog;
    private final Map<ShareContentType, ShareTargetCatalog.Snapshot> catalogs = new EnumMap<>(ShareContentType.class);
    private ShareTargetProfiles profiles;
    private ShareTargetRules defaults;
    private ShareContentType selected = ShareContentType.DEFAULT;
    private Spinner typeSelector;
    private Switch independent;
    private boolean updatingSelection;
    private final Map<String, ShareTargetCatalog.Target> targets = new LinkedHashMap<>();
    private final List<String> visible = new ArrayList<>();
    private final TargetAdapter adapter = new TargetAdapter();
    private Dialog dialog;
    private GridView list;
    private TextView status;
    private TextView save;
    private TextView refresh;
    private TextView followSystem;
    private Switch enabled;
    private EditText search;
    private ShareTargetsDraft draft;
    private boolean closed;
    private boolean loading;
    private String dragComponent;
    private String dropComponent;
    private float dragX;
    private float dragY;

    public ShareTargetsEditor(MainActivity activity) {
        this.activity = activity;
        this.scanner = command -> new Thread(command, "share-target-scan").start();
        this.catalog = ShareTargetCatalog::load;
    }

    ShareTargetsEditor(MainActivity activity, Executor scanner,
            Function<android.content.Context, List<ShareTargetCatalog.Target>> catalog) {
        this(activity, scanner, (context, type) -> catalog.apply(context));
    }

    ShareTargetsEditor(MainActivity activity, Executor scanner,
            BiFunction<android.content.Context, ShareContentType, List<ShareTargetCatalog.Target>> catalog) {
        this.activity = activity;
        this.scanner = scanner;
        this.catalog = (context, type) -> new ShareTargetCatalog.Snapshot(catalog.apply(context, type), true);
    }

    public void show() {
        defaults = ShareTargetRules.parse(
                SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                SettingsStore.readString(activity.prefs(), SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        profiles = ShareTargetProfiles.parse(SettingsStore.readString(activity.prefs(),
                SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setPadding(dp(16), dp(12), dp(16), dp(12));
        root.setBackgroundColor(activity.surfaceColor());

        LinearLayout titleRow = row();
        TextView back = button("返回", false);
        back.setOnClickListener(v -> dialog.dismiss());
        titleRow.addView(back);
        TextView title = text("分享列表管理", 20, activity.textColor());
        title.setGravity(Gravity.CENTER);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        save = button("保存", true);
        save.setEnabled(false);
        save.setOnClickListener(v -> save());
        titleRow.addView(save);
        root.addView(titleRow, activity.matchWrap());

        enabled = new Switch(activity);
        enabled.setText("启用自定义分享列表");
        enabled.setTextColor(activity.textColor());
        enabled.setTextSize(16);
        enabled.setMinHeight(dp(52));
        enabled.setChecked(SettingsStore.readBoolean(activity.prefs(),
                SettingsStore.KEY_SHARE_TARGETS_ENABLED, SettingsStore.DEFAULT_SHARE_TARGETS_ENABLED));
        root.addView(enabled, activity.matchWrap());
        root.addView(text("默认跟随系统顺序。长按图标拖动，或点“排序”调整位置；勾选“隐藏”移除入口。"
                + "调整顺序会开启自定义列表，点击保存后重新打开分享面板生效。",
                13, activity.subtextColor()), activity.matchWrap());

        typeSelector = new Spinner(activity);
        typeSelector.setContentDescription("分享内容类型");
        ArrayAdapter<ShareContentType> types = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, ShareContentType.values());
        types.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSelector.setAdapter(types);
        typeSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (loading || catalogs.isEmpty() || selected == ShareContentType.values()[position]) return;
                stash();
                selected = ShareContentType.values()[position];
                selectDraft();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(typeSelector, activity.matchWrapWithTop(8));
        independent = new Switch(activity);
        independent.setText("单独设置此类型");
        independent.setTextColor(activity.textColor());
        independent.setMinHeight(dp(48));
        independent.setVisibility(View.GONE);
        independent.setOnCheckedChangeListener((button, checked) -> {
            if (updatingSelection || loading || draft == null) return;
            profiles.set(selected, checked ? draft.rules() : null);
            selectDraft();
        });
        root.addView(independent, activity.matchWrap());
        root.addView(text("未单独设置的类型沿用默认配置。混合类型使用默认配置。"
                + "各类型列出支持该类型的入口，单文件和多文件共用配置。恢复默认仅重置当前类型。",
                13, activity.subtextColor()), activity.matchWrap());

        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(activity.textColor());
        search.setHintTextColor(activity.subtextColor());
        search.setHint("搜索应用或分享入口");
        search.setContentDescription("搜索分享入口");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        root.addView(search, activity.matchWrapWithTop(8));

        LinearLayout actions = row();
        refresh = button("重新扫描", false);
        refresh.setOnClickListener(v -> load());
        actions.addView(refresh);
        TextView reset = button("恢复默认", false);
        reset.setOnClickListener(v -> {
            if (draft == null || loading) return;
            if (selected == ShareContentType.DEFAULT) {
                draft.reset();
                defaults = draft.rules();
            } else {
                profiles.set(selected, null);
                selectDraft();
            }
            search.setText("");
            render();
            activity.showToast("已恢复默认草稿，点击保存生效");
        });
        actions.addView(reset);
        root.addView(actions, activity.matchWrapWithTop(4));

        LinearLayout orderRow = row();
        orderRow.addView(text("排序在常用、更多和设备各组内生效", 12, activity.subtextColor()),
                new LinearLayout.LayoutParams(0, -2, 1));
        followSystem = button("跟随系统", false);
        followSystem.setContentDescription("恢复系统排序，保留隐藏设置");
        followSystem.setOnClickListener(v -> {
            if (draft == null || !editable()) return;
            draft.followSystemOrder();
            search.setText("");
            render();
            list.setSelection(0);
            activity.showToast("已恢复系统排序，隐藏设置保留，点击保存生效");
        });
        orderRow.addView(followSystem);
        root.addView(orderRow, activity.matchWrapWithTop(4));

        status = text("正在扫描分享入口…", 12, activity.subtextColor());
        status.setPadding(0, dp(8), 0, dp(8));
        root.addView(status, activity.matchWrap());
        list = new GridView(activity);
        list.setNumColumns(4);
        list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        list.setHorizontalSpacing(dp(4));
        list.setVerticalSpacing(dp(8));
        list.setClipToPadding(false);
        list.setPadding(0, dp(4), 0, dp(12));
        list.setAdapter(adapter);
        list.setOnDragListener(this::onDrag);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        dialog.setContentView(root);
        dialog.setOnDismissListener(ignored -> {
            closed = true;
            dragComponent = null;
            list.removeCallbacks(dragScroll);
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
            window.setLayout(-1, -1);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        load();
    }

    private void load() {
        if (loading) return;
        stash();
        loading = true;
        typeSelector.setEnabled(false);
        independent.setEnabled(false);
        save.setEnabled(false);
        refresh.setEnabled(false);
        followSystem.setEnabled(false);
        status.setText("正在扫描分享入口…");
        android.content.Context context = activity.getApplicationContext();
        scanner.execute(() -> {
            try {
                Map<ShareContentType, ShareTargetCatalog.Snapshot> found = new EnumMap<>(ShareContentType.class);
                for (ShareContentType type : ShareContentType.values()) found.put(type, catalog.apply(context, type));
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                    catalogs.clear();
                    catalogs.putAll(found);
                    loading = false;
                    typeSelector.setEnabled(true);
                    independent.setEnabled(true);
                    selectDraft();
                    refresh.setEnabled(true);
                    save.setEnabled(true);
                    render();
                });
            } catch (RuntimeException error) {
                activity.runOnUiThread(() -> {
                    if (closed || activity.isFinishing() || activity.isDestroyed()) return;
                    loading = false;
                    refresh.setEnabled(true);
                    save.setEnabled(draft != null);
                    typeSelector.setEnabled(draft != null);
                    independent.setEnabled(draft != null);
                    followSystem.setEnabled(draft != null && editable() && draft.hasFixedOrder());
                    status.setText("扫描失败，请点击“重新扫描”。已保存的规则仍保留。");
                });
            }
        });
    }

    private void save() {
        if (draft == null || loading) return;
        stash();
        ShareTargetRules rules = defaults;
        activity.prefs().edit()
                .putBoolean(SettingsStore.KEY_SHARE_TARGETS_ENABLED, enabled.isChecked())
                .putString(SettingsStore.KEY_SHARE_TARGET_PROFILES, profiles.encode())
                .putString(SettingsStore.KEY_SHARE_TARGET_ORDER, rules.encodeOrder())
                .putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, rules.encodeHidden()).apply();
        SettingsStore.notifyChanged(activity);
        activity.showToast(enabled.isChecked() ? "已保存，重新打开分享面板生效" : "已保存，自定义分享列表已关闭");
        dialog.dismiss();
    }

    private void stash() {
        if (draft == null) return;
        if (selected == ShareContentType.DEFAULT) defaults = draft.rules();
        else if (profiles.hasOverride(selected)) profiles.set(selected, draft.rules());
    }

    private boolean editable() {
        return !loading && (selected == ShareContentType.DEFAULT || profiles.hasOverride(selected));
    }

    private void selectDraft() {
        dragComponent = null;
        dropComponent = null;
        list.removeCallbacks(dragScroll);
        targets.clear();
        for (ShareTargetCatalog.Target target : catalogs.get(selected).targets) targets.put(target.component, target);
        draft = new ShareTargetsDraft(targets.keySet(), profiles.rulesFor(selected, defaults));
        updatingSelection = true;
        independent.setVisibility(selected == ShareContentType.DEFAULT ? View.GONE : View.VISIBLE);
        independent.setChecked(profiles.hasOverride(selected));
        updatingSelection = false;
        search.setText("");
        render();
        list.setSelection(0);
    }

    private void render() {
        if (draft == null || status == null || loading) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        visible.clear();
        for (String component : draft.components()) {
            ShareTargetCatalog.Target target = targets.get(component);
            String haystack = component + (target == null ? "" : " " + target.label + " " + target.appName);
            if (haystack.toLowerCase(Locale.ROOT).contains(query)) visible.add(component);
        }
        status.setText(selected.label + (selected != ShareContentType.DEFAULT && !profiles.hasOverride(selected)
                ? " · 沿用默认（开启单独设置后可编辑）\n" : "\n") + visible.size() + " 个入口 · 已隐藏 " + draft.rules().hidden().size()
                + " 个 · " + (draft.hasFixedOrder() ? "自定义组内顺序" : "顺序跟随系统")
                + (!catalogs.get(selected).systemOrderAvailable
                        ? "\n暂未读取到 Flyme 排序，预览按系统查询顺序显示" : "")
                + (query.isEmpty() ? "" : "\n清空搜索后可调整顺序")
                + (visible.isEmpty() ? "\n没有匹配的分享入口" : ""));
        adapter.notifyDataSetChanged();
        followSystem.setEnabled(editable() && draft.hasFixedOrder());
    }

    private boolean canReorder() {
        if (draft == null || !editable()) return false;
        if (!search.getText().toString().trim().isEmpty()) {
            activity.showToast("请先清空搜索，再调整顺序");
            return false;
        }
        return true;
    }

    private void showMoveMenu(String component) {
        if (!canReorder()) return;
        new AlertDialog.Builder(activity).setTitle("调整位置")
                .setItems(new String[]{"移到顶部", "向上移动", "向下移动", "移到底部"}, (d, which) -> {
                    int from = draft.components().indexOf(component);
                    int last = draft.components().size() - 1;
                    int to = which == 0 ? 0 : which == 1 ? Math.max(0, from - 1)
                            : which == 2 ? Math.min(last, from + 1) : last;
                    move(component, to);
                }).show();
    }

    private boolean move(String component, int destination) {
        if (!canReorder() || !draft.move(component, destination)) return false;
        enabled.setChecked(true);
        render();
        list.setSelection(destination);
        return true;
    }

    private boolean startDrag(View handle, String component) {
        if (!canReorder()) return false;
        dragComponent = component;
        boolean started = handle.startDragAndDrop(ClipData.newPlainText("share-target", component),
                new View.DragShadowBuilder((View) handle.getParent()), this, 0);
        if (started) activity.performTapHaptic(handle); else dragComponent = null;
        return started;
    }

    private boolean onDrag(View view, DragEvent event) {
        if (event.getLocalState() != this) return false;
        switch (event.getAction()) {
            case DragEvent.ACTION_DRAG_STARTED:
                return dragComponent != null;
            case DragEvent.ACTION_DRAG_LOCATION:
                dragX = event.getX();
                dragY = event.getY();
                updateDropTarget();
                list.removeCallbacks(dragScroll);
                list.postOnAnimation(dragScroll);
                return true;
            case DragEvent.ACTION_DRAG_EXITED:
                dropComponent = null;
                list.removeCallbacks(dragScroll);
                adapter.notifyDataSetChanged();
                return true;
            case DragEvent.ACTION_DROP:
                dragX = event.getX();
                dragY = event.getY();
                updateDropTarget();
                if (dropComponent == null || dragComponent == null) return false;
                int destination = draft.components().indexOf(dropComponent);
                return move(dragComponent, destination);
            case DragEvent.ACTION_DRAG_ENDED:
                dragComponent = null;
                dropComponent = null;
                list.removeCallbacks(dragScroll);
                adapter.notifyDataSetChanged();
                return true;
            default: return true;
        }
    }

    private void updateDropTarget() {
        int position = list.pointToPosition((int) dragX, (int) dragY);
        // Use the nearest visible cell in grid gaps and while scrolling at an edge.
        if (position == GridView.INVALID_POSITION) {
            float nearest = Float.MAX_VALUE;
            for (int i = 0; i < list.getChildCount(); i++) {
                View cell = list.getChildAt(i);
                float dx = dragX - (cell.getLeft() + cell.getRight()) / 2f;
                float dy = dragY - (cell.getTop() + cell.getBottom()) / 2f;
                float distance = dx * dx + dy * dy;
                if (distance < nearest) {
                    nearest = distance;
                    position = list.getFirstVisiblePosition() + i;
                }
            }
        }
        String next = position >= 0 && position < visible.size() ? visible.get(position) : null;
        if (!java.util.Objects.equals(next, dropComponent)) {
            dropComponent = next;
            adapter.notifyDataSetChanged();
        }
    }

    private final Runnable dragScroll = new Runnable() {
        @Override public void run() {
            if (closed || dragComponent == null) return;
            int direction = dragY < dp(56) ? -1 : dragY > list.getHeight() - dp(56) ? 1 : 0;
            if (direction == 0 || !list.canScrollList(direction)) return;
            list.scrollListBy(direction * dp(7));
            updateDropTarget();
            list.postOnAnimation(this);
        }
    };

    private final class TargetAdapter extends BaseAdapter {
        @Override public int getCount() { return visible.size(); }
        @Override public Object getItem(int position) { return visible.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override public View getView(int position, View convertView, ViewGroup parent) {
            String component = visible.get(position);
            ShareTargetCatalog.Target target = targets.get(component);
            if (target == null) {
                for (ShareTargetCatalog.Target candidate : catalogs.get(ShareContentType.DEFAULT).targets) {
                    if (candidate.component.equals(component)) { target = candidate; break; }
                }
            }
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(2), dp(8), dp(2), dp(4));
            cell.setBackgroundColor(component.equals(dropComponent) ? activity.surfaceSoftColor() : Color.TRANSPARENT);
            String name = target == null ? "不可用入口" : target.label;
            String appName = target == null ? "未检测到应用" : target.appName;
            // PackageManager may use a component/package identifier as its label fallback.
            if (name.equals(component) || name.equals(component.substring(component.indexOf('/') + 1))) name = "分享入口";
            if (appName.equals(component.substring(0, component.indexOf('/')))) appName = "应用";
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(target == null || target.icon == null
                    ? activity.getPackageManager().getDefaultActivityIcon() : target.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setAlpha(draft.isHidden(component) ? 0.4f : 1f);
            icon.setContentDescription("调整 " + name + " 的位置，长按拖动");
            icon.setEnabled(editable());
            icon.setOnLongClickListener(v -> startDrag(v, component));
            icon.setOnClickListener(v -> showMoveMenu(component));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
            TextView function = text(name, 13, activity.textColor());
            function.setGravity(Gravity.CENTER);
            function.setLines(2);
            function.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(function, new LinearLayout.LayoutParams(-1, -2));
            TextView app = text(appName, 11, activity.subtextColor());
            app.setGravity(Gravity.CENTER);
            app.setSingleLine(true);
            app.setEllipsize(android.text.TextUtils.TruncateAt.END);
            cell.addView(app, new LinearLayout.LayoutParams(-1, -2));
            TextView reorder = text("↕ 排序", 13, activity.primaryColor());
            reorder.setGravity(Gravity.CENTER);
            reorder.setContentDescription("调整 " + name + " 的顺序");
            reorder.setEnabled(editable());
            reorder.setAlpha(editable() ? 1f : 0.4f);
            reorder.setOnClickListener(v -> showMoveMenu(component));
            cell.addView(reorder, new LinearLayout.LayoutParams(-1, dp(40)));
            CheckBox hidden = new CheckBox(activity);
            hidden.setText("隐藏");
            hidden.setTextColor(activity.textColor());
            hidden.setTextSize(12);
            hidden.setContentDescription("隐藏 " + name);
            hidden.setChecked(draft.isHidden(component));
            hidden.setEnabled(editable());
            hidden.setOnCheckedChangeListener((v, checked) -> {
                if (editable()) { draft.setHidden(component, checked); render(); }
            });
            hidden.setMinWidth(0);
            hidden.setMinimumWidth(0);
            hidden.setMinHeight(dp(40));
            hidden.setPadding(0, 0, 0, 0);
            cell.addView(hidden, new LinearLayout.LayoutParams(-2, dp(40)));
            return cell;
        }
    }

    private int dp(int value) { return activity.dp(value); }
    private LinearLayout row() {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }
    private TextView text(String label, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }
    private TextView button(String label, boolean primary) {
        return activity.filledButton(label, primary ? activity.primaryColor() : activity.surfaceSoftColor(),
                primary ? Color.WHITE : activity.textColor());
    }
}
