package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Switch;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.After;
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
import org.robolectric.shadows.ShadowDialog;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public final class ShareTargetsEditorTest {
    private static final String FRIEND = "com.tencent.mm/com.tencent.mm.ui.tools.ShareImgUI";
    private static final String MOMENTS = "com.tencent.mm/com.tencent.mm.ui.tools.ShareToTimeLineUI";
    private static final String FILE = "com.example.files/com.example.files.Share";
    private EditorActivity activity;
    private Dialog dialog;

    public static class EditorActivity extends MainActivity {
        @Override public SharedPreferences prefs() { return getSharedPreferences("share-editor-test", MODE_PRIVATE); }
        @Override public void showToast(String text) {}
    }

    @Before public void setup() {
        activity = Robolectric.buildActivity(EditorActivity.class).get();
        activity.prefs().edit().clear().commit();
    }

    @After public void tearDown() {
        if (dialog != null) dialog.dismiss();
    }

    private static ShareTargetCatalog.Target target(String component, String label) {
        return new ShareTargetCatalog.Target(component, label, "微信", null);
    }

    private void open() {
        new ShareTargetsEditor(activity, Runnable::run, context -> List.of(
                target(FRIEND, "微信好友"), target(MOMENTS, "朋友圈"))).show();
        captureDialog();
    }

    private void captureDialog() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog = ShadowDialog.getLatestDialog();
    }

    @Test public void scansOnlyRequestedTypesAndRescanInvalidatesPreviouslyVisitedTypes() {
        java.util.List<ShareContentType> scans = new java.util.ArrayList<>();
        new ShareTargetsEditor(activity, Runnable::run, (context, type) -> {
            scans.add(type);
            return List.of(target(FRIEND, "微信好友"));
        }).show();
        captureDialog();
        assertEquals(List.of(ShareContentType.DEFAULT), scans);
        selectType(ShareContentType.IMAGE);
        selectType(ShareContentType.DEFAULT);
        selectType(ShareContentType.IMAGE);
        assertEquals(List.of(ShareContentType.DEFAULT, ShareContentType.IMAGE), scans);
        moreAction("重新扫描");
        selectType(ShareContentType.DEFAULT);
        assertEquals(List.of(ShareContentType.DEFAULT, ShareContentType.IMAGE,
                ShareContentType.IMAGE, ShareContentType.DEFAULT), scans);
        assertFalse(byText(root(), "保存").isEnabled());
    }

    @Test public void closingBeforeQueuedScanStartsSkipsCatalogWork() {
        AtomicReference<Runnable> worker = new AtomicReference<>();
        java.util.List<ShareContentType> scans = new java.util.ArrayList<>();
        new ShareTargetsEditor(activity, worker::set, (context, type) -> {
            scans.add(type);
            return List.of(target(FRIEND, "微信好友"));
        }).show();
        captureDialog();
        dialog.dismiss();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        worker.get().run();
        assertTrue(scans.isEmpty());
    }

    @Test public void failedTypeLoadAllowsReturningToPreviousDraftWithoutLosingEdits() {
        new ShareTargetsEditor(activity, Runnable::run, (context, type) -> {
            if (type == ShareContentType.IMAGE) throw new IllegalStateException("scan failed");
            return List.of(target(FRIEND, "微信好友"), target(MOMENTS, "朋友圈"));
        }).show();
        captureDialog();
        targetAction(1, "隐藏此入口");
        selectType(ShareContentType.PDF);
        selectType(ShareContentType.IMAGE);
        assertFalse(byText(root(), "保存").isEnabled());
        selectType(ShareContentType.PDF);
        assertTrue(byText(root(), "单独设置此类型").isEnabled());
        selectType(ShareContentType.DEFAULT);
        assertEquals(1, grid().getCount());
        byText(root(), "保存").performClick();
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void unchangedEditorDoesNotSaveAndReturnsWithoutPrompt() {
        open();
        assertFalse(byText(root(), "保存").isEnabled());
        selectType(ShareContentType.IMAGE);
        selectType(ShareContentType.DEFAULT);
        moreAction("重新扫描");
        assertFalse(byText(root(), "保存").isEnabled());
        byDescription(root(), "返回").performClick();
        assertFalse(dialog.isShowing());
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void discoveredTargetsDoNotMarkAnExistingOrderAsEdited() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_TARGET_ORDER, FRIEND).commit();
        open();
        assertEquals(2, grid().getCount());
        assertFalse(byText(root(), "保存").isEnabled());
        moreAction("重新扫描");
        assertFalse(byText(root(), "保存").isEnabled());
        targetAction(1, "隐藏此入口");
        byText(root(), "保存").performClick();
        assertEquals(FRIEND, activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
    }

    @Test public void undoRestoresCleanStateRegardlessOfHiddenSetInsertionOrder() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, FRIEND + "\n" + MOMENTS).commit();
        open();
        filterHidden(true);
        targetAction(0, "恢复显示");
        byText(root(), "撤销").performClick();
        assertEquals(2, grid().getCount());
        assertFalse(byText(root(), "保存").isEnabled());
    }

    @Test public void pendingRescanKeepsExitSaveDisabledUntilDraftCanBeSaved() {
        AtomicReference<Runnable> worker = new AtomicReference<>();
        new ShareTargetsEditor(activity, worker::set, context -> List.of(
                target(FRIEND, "微信好友"), target(MOMENTS, "朋友圈"))).show();
        captureDialog();
        assertFalse(byText(root(), "保存").isEnabled());
        worker.get().run();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        targetAction(1, "移到顶部");
        moreAction("重新扫描");
        dialog.onBackPressed();
        assertFalse(ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        worker.get().run();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());
        confirm(AlertDialog.BUTTON_POSITIVE);
        assertEquals(MOMENTS + "\n" + FRIEND, activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
    }

    @Test @Config(qualifiers = "w360dp-h720dp-xhdpi")
    public void compactLayoutLeavesRoomForTwoGridRowsAndScrollableActions() {
        open();
        selectType(ShareContentType.IMAGE);
        View content = ((ViewGroup) dialog.findViewById(android.R.id.content)).getChildAt(0);
        layout(content, 360, 640);
        assertTrue("grid should show two full rows", grid().getHeight() >= itemHeight() * 2);
        setIndependent(true);
        itemView(0).performClick();
        View sheetContent = ((ViewGroup) ShadowDialog.getLatestDialog()
                .findViewById(android.R.id.content)).getChildAt(0);
        layout(sheetContent, 360, 460);
        android.widget.ScrollView scroll = find(sheetContent, android.widget.ScrollView.class);
        assertNotNull(scroll);
        assertTrue("actions should scroll on short screens", scroll.getChildAt(0).getHeight() > scroll.getHeight());
        assertTrue(byText(latestRoot(), "取消").isEnabled());
    }

    @Test @Config(qualifiers = "w320dp-h640dp-xhdpi")
    public void largeFontOnNarrowScreenKeepsGridAndSaveAccessible() {
        android.content.res.Configuration config = new android.content.res.Configuration(
                activity.getResources().getConfiguration());
        config.fontScale = 1.3f;
        activity.getResources().updateConfiguration(config, activity.getResources().getDisplayMetrics());
        open();
        selectType(ShareContentType.IMAGE);
        View content = ((ViewGroup) dialog.findViewById(android.R.id.content)).getChildAt(0);
        layout(content, 320, 580);
        assertTrue("at least one complete row must remain visible", grid().getHeight() >= itemHeight());
        TextView save = (TextView) byText(root(), "保存");
        assertTrue(save.getWidth() >= activity.dp(48));
        assertTrue(save.getHeight() >= activity.dp(48));
    }

    private void layout(View view, int width, int height) {
        int w = activity.dp(width), h = activity.dp(height);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
    }

    private int itemHeight() {
        View item = itemView(0);
        item.measure(View.MeasureSpec.makeMeasureSpec(activity.dp(76), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return item.getMeasuredHeight();
    }

    @Test public void cancelLeavesAllPreferencesUntouched() {
        open();
        targetAction(0, "隐藏此入口");
        byDescription(root(), "返回").performClick();
        assertTrue(dialog.isShowing());
        confirm(AlertDialog.BUTTON_NEGATIVE);
        assertFalse(dialog.isShowing());
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void savePersistsHideAndEnabledStateWithoutFreezingOrder() {
        open();
        targetAction(1, "隐藏此入口");
        ((Switch) byText(root(), "启用自定义分享列表")).setChecked(true);
        byText(root(), "保存").performClick();
        assertTrue(activity.prefs().getBoolean(SettingsStore.KEY_SHARE_TARGETS_ENABLED, false));
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        open();
        assertEquals(1, grid().getCount());
        filterHidden(true);
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        targetAction(0, "恢复显示");
        assertEquals(0, grid().getCount());
        filterHidden(false);
        assertEquals(2, grid().getCount());
    }

    @Test public void hideAndRestoreCanBeUndoneWithoutChangingOrderOrEnableState() {
        open();
        targetAction(1, "隐藏此入口");
        assertEquals(1, grid().getCount());
        byText(root(), "撤销").performClick();
        assertEquals(2, grid().getCount());
        assertFalse(byText(root(), "保存").isEnabled());
        targetAction(1, "隐藏此入口");
        filterHidden(true);
        targetAction(0, "恢复显示");
        assertEquals(0, grid().getCount());
        byText(root(), "撤销").performClick();
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        assertFalse(((Switch) byText(root(), "启用自定义分享列表")).isChecked());
        byText(root(), "保存").performClick();
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
    }

    @Test public void undoExpiresAndCannotLeakAcrossProfiles() {
        open();
        targetAction(0, "隐藏此入口");
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(7));
        assertEquals(View.GONE, ((View) byText(root(), "撤销").getParent()).getVisibility());
        selectType(ShareContentType.IMAGE);
        assertEquals(View.GONE, ((View) byText(root(), "撤销").getParent()).getVisibility());
        byText(root(), "撤销").performClick();
        byText(root(), "保存").performClick();
        assertEquals(FRIEND, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void visibleMoveMenuSkipsHiddenNeighborAndPreservesItsRule() {
        new ShareTargetsEditor(activity, Runnable::run, context -> List.of(
                target(FRIEND, "微信好友"), target(MOMENTS, "朋友圈"), target(FILE, "文件"))).show();
        captureDialog();
        targetAction(1, "隐藏此入口");
        targetAction(1, "向上移动");
        assertEquals(FILE, grid().getAdapter().getItem(0));
        byText(root(), "保存").performClick();
        ShareTargetRules saved = ShareTargetRules.parse(
                activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        assertEquals(List.of(FILE, FRIEND), saved.apply(List.of(FRIEND, MOMENTS, FILE), s -> s));
        assertTrue(saved.hidden().contains(MOMENTS));
    }

    @Test public void explicitReorderEnablesFeatureAndSavedOrderReachesRuntimeRules() throws Exception {
        open();
        assertFalse(((Switch) byText(root(), "启用自定义分享列表")).isChecked());
        targetAction(1, "移到顶部");
        assertTrue(((Switch) byText(root(), "启用自定义分享列表")).isChecked());
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        assertTrue(activity.prefs().getAll().isEmpty());
        byText(root(), "保存").performClick();

        java.lang.reflect.Method decode = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        decode.setAccessible(true);
        ModuleConfig config = (ModuleConfig) decode.invoke(null, activity.prefs());
        activity.setIntent(android.content.Intent.createChooser(
                new android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png"), "Share"));
        assertTrue(config.shareTargetsEnabled);
        assertEquals(List.of(MOMENTS, FRIEND),
                ShareTargetsHooks.rulesFor(config, activity).apply(List.of(FRIEND, MOMENTS), s -> s));
        open();
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
    }

    @Test public void returningWithoutSavingDiscardsOrderAndAutomaticEnable() {
        open();
        targetAction(1, "移到顶部");
        dialog.onBackPressed();
        confirm(AlertDialog.BUTTON_NEGATIVE);
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void backPromptCanContinueEditingOrSaveAllProfiles() {
        open();
        targetAction(1, "隐藏此入口");
        selectType(ShareContentType.IMAGE);
        dialog.onBackPressed();
        confirm(AlertDialog.BUTTON_NEUTRAL);
        assertTrue(dialog.isShowing());
        assertTrue(activity.prefs().getAll().isEmpty());
        dialog.onBackPressed();
        confirm(AlertDialog.BUTTON_POSITIVE);
        assertFalse(dialog.isShowing());
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void followSystemOrderRetainsHiddenSettingsAndWaitsForSave() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_TARGET_ORDER, MOMENTS + "\n" + FRIEND)
                .putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, MOMENTS).commit();
        open();
        moreAction("恢复系统排序");
        assertEquals(FRIEND, grid().getAdapter().getItem(0));
        filterHidden(true);
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        assertFalse(moreOptionEnabled("恢复系统排序"));
        assertEquals(MOMENTS + "\n" + FRIEND, activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        byText(root(), "保存").performClick();
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void rescanFollowsNewSystemOrderUntilUserMovesAnEntry() {
        ShareTargetCatalog.Target friend = target(FRIEND, "微信好友");
        ShareTargetCatalog.Target moments = target(MOMENTS, "朋友圈");
        AtomicReference<List<ShareTargetCatalog.Target>> system = new AtomicReference<>(List.of(moments, friend));
        new ShareTargetsEditor(activity, Runnable::run, context -> system.get()).show();
        captureDialog();
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        system.set(List.of(friend, moments));
        moreAction("重新扫描");
        assertEquals(FRIEND, grid().getAdapter().getItem(0));
        assertFalse(moreOptionEnabled("恢复系统排序"));
        targetAction(1, "移到顶部");
        moreAction("重新扫描");
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        assertTrue(moreOptionEnabled("恢复系统排序"));
    }

    @Test public void typeCanFollowSystemOrderIndependentlyOfDefaultCustomOrder() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_TARGET_ORDER, MOMENTS + "\n" + FRIEND).commit();
        open();
        selectType(ShareContentType.IMAGE);
        assertFalse(moreOptionEnabled("恢复系统排序"));
        setIndependent(true);
        moreAction("恢复系统排序");
        byText(root(), "保存").performClick();
        ShareTargetRules defaults = ShareTargetRules.parse(
                activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""), "");
        ShareTargetProfiles profiles = savedProfiles();
        assertEquals(List.of(MOMENTS, FRIEND), defaults.order());
        assertTrue(profiles.hasOverride(ShareContentType.IMAGE));
        assertTrue(profiles.rulesFor(ShareContentType.IMAGE, defaults).order().isEmpty());
        assertEquals(defaults.order(), profiles.rulesFor(ShareContentType.PDF, defaults).order());
    }

    @Test public void resetIsOnlyAppliedOnSave() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, MOMENTS)
                .putString(SettingsStore.KEY_SHARE_TARGET_ORDER, MOMENTS + "\n" + FRIEND).commit();
        open();
        resetCurrent();
        assertEquals(2, grid().getCount());
        dialog.dismiss();
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        open();
        resetCurrent();
        byText(root(), "保存").performClick();
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        assertFalse(activity.prefs().getBoolean(SettingsStore.KEY_SHARE_TARGETS_ENABLED, true));
    }

    @Test public void failedScanCannotOverwriteSavedRules() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, MOMENTS).commit();
        new ShareTargetsEditor(activity, Runnable::run, context -> { throw new IllegalStateException("scan failed"); }).show();
        captureDialog();
        assertFalse(byText(root(), "保存").isEnabled());
        assertFalse(moreOptionEnabled("重置当前类型"));
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void typeDraftsSaveTogetherAndResetOnlyAffectsSelectedType() {
        open();
        targetAction(1, "隐藏此入口");
        selectType(ShareContentType.IMAGE);
        filterHidden(true);
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
        itemView(0).performClick();
        assertNull(byText(latestRoot(), "恢复显示"));
        byText(latestRoot(), "单独设置此类型").performClick();
        targetAction(0, "恢复显示");
        selectType(ShareContentType.PDF);
        setIndependent(true);
        filterHidden(false);
        targetAction(0, "隐藏此入口");
        selectType(ShareContentType.IMAGE);
        assertEquals(2, grid().getCount());
        moreAction("重新扫描");
        assertEquals(2, grid().getCount());
        byText(root(), "保存").performClick();
        ShareTargetProfiles profiles = savedProfiles();
        assertTrue(profiles.hasOverride(ShareContentType.IMAGE));
        assertTrue(profiles.rulesFor(ShareContentType.IMAGE, ShareTargetRules.EMPTY).isEmpty());
        assertEquals(2, profiles.rulesFor(ShareContentType.PDF, ShareTargetRules.EMPTY).hidden().size());
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        open();
        selectType(ShareContentType.PDF);
        resetCurrent();
        assertFalse(((Switch) byText(root(), "单独设置此类型")).isChecked());
        byText(root(), "保存").performClick();
        profiles = savedProfiles();
        assertFalse(profiles.hasOverride(ShareContentType.PDF));
        assertTrue(profiles.hasOverride(ShareContentType.IMAGE));
    }

    @Test public void everyTypeKeepsItsOwnSavedHiddenAndOrderedRulesAtRuntime() throws Exception {
        open();
        for (ShareContentType type : ShareContentType.values()) {
            if (type == ShareContentType.DEFAULT) continue;
            selectType(type);
            setIndependent(true);
            targetAction(1, "移到顶部");
            targetAction(type.ordinal() % 2, "隐藏此入口");
        }
        selectType(ShareContentType.DEFAULT);
        assertEquals(2, grid().getCount());
        assertEquals(FRIEND, grid().getAdapter().getItem(0));
        byText(root(), "保存").performClick();

        java.lang.reflect.Method decode = ModuleConfig.class.getDeclaredMethod("fromSharedPreferences", SharedPreferences.class);
        decode.setAccessible(true);
        ModuleConfig config = (ModuleConfig) decode.invoke(null, activity.prefs());
        assertTrue(config.shareTargetsEnabled);
        assertTrue(config.shareTargetRules.isEmpty());
        for (ShareContentType type : ShareContentType.values()) {
            if (type == ShareContentType.DEFAULT) continue;
            activity.setIntent(android.content.Intent.createChooser(
                    new android.content.Intent(android.content.Intent.ACTION_SEND).setType(type.mime), "Share"));
            ShareTargetRules rules = ShareTargetsHooks.rulesFor(config, activity);
            assertEquals(type.name(), List.of(MOMENTS, FRIEND), rules.order());
            String hidden = type.ordinal() % 2 == 0 ? MOMENTS : FRIEND;
            assertEquals(type.name(), java.util.Set.of(hidden), rules.hidden());
        }

        open();
        filterHidden(true);
        for (ShareContentType type : ShareContentType.values()) {
            if (type == ShareContentType.DEFAULT) continue;
            selectType(type);
            assertEquals(type.name(), 1, grid().getCount());
            assertEquals(type.ordinal() % 2 == 0 ? MOMENTS : FRIEND, grid().getAdapter().getItem(0));
            assertFalse(byText(root(), "保存").isEnabled());
        }
    }

    @Test public void cancelDiscardsEditsAcrossTypes() {
        open();
        selectType(ShareContentType.IMAGE);
        setIndependent(true);
        targetAction(0, "隐藏此入口");
        selectType(ShareContentType.PDF);
        byDescription(root(), "返回").performClick();
        confirm(AlertDialog.BUTTON_NEGATIVE);
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void selectedTypeUsesItsOwnCatalog() {
        new ShareTargetsEditor(activity, Runnable::run, (context, type) -> type == ShareContentType.PDF
                ? List.of(target(FRIEND, "微信好友")) : List.of(target(MOMENTS, "朋友圈"))).show();
        captureDialog();
        selectType(ShareContentType.PDF);
        assertNotNull(byText(itemView(0), "微信好友"));
        selectType(ShareContentType.IMAGE);
        assertNotNull(byText(itemView(0), "朋友圈"));
    }

    @Test public void searchAllowsHideButPreventsReorderAndHasClearableEmptyState() {
        open();
        find(root(), EditText.class).setText("朋友圈");
        assertEquals(1, grid().getCount());
        assertFalse(itemView(0).isLongClickable());
        itemView(0).performClick();
        assertFalse(byText(latestRoot(), "移到顶部").isEnabled());
        byText(latestRoot(), "隐藏此入口").performClick();
        assertEquals(0, grid().getCount());
        assertEquals(View.VISIBLE, byText(root(), "没有找到匹配的入口\n试试应用名称，或清空搜索").getVisibility());
        byDescription(root(), "清空搜索").performClick();
        assertEquals(1, grid().getCount());
        filterHidden(true);
        assertEquals(MOMENTS, grid().getAdapter().getItem(0));
    }

    private ShareTargetProfiles savedProfiles() {
        return ShareTargetProfiles.parse(activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
    }

    private void selectType(ShareContentType type) {
        byDescription(root(), "分享类型：" + type.label).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void setIndependent(boolean value) {
        ((Switch) byText(root(), "单独设置此类型")).setChecked(value);
    }

    private void filterHidden(boolean hidden) {
        byDescription(root(), hidden ? "查看已隐藏的入口" : "查看显示中的入口").performClick();
    }

    private void targetAction(int position, String label) {
        itemView(position).performClick();
        View action = byText(latestRoot(), label);
        assertNotNull(label, action);
        assertTrue(label, action.isEnabled());
        action.performClick();
    }

    private void moreAction(String label) {
        byDescription(root(), "更多操作").performClick();
        View action = byText(latestRoot(), label);
        assertTrue(label, action.isEnabled());
        action.performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private boolean moreOptionEnabled(String label) {
        byDescription(root(), "更多操作").performClick();
        boolean enabled = byText(latestRoot(), label).isEnabled();
        ShadowDialog.getLatestDialog().dismiss();
        return enabled;
    }

    private void confirm(int button) {
        ShadowAlertDialog.getLatestAlertDialog().getButton(button).performClick();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private void resetCurrent() {
        moreAction("重置当前类型");
        confirm(AlertDialog.BUTTON_POSITIVE);
    }

    private View root() { return dialog.getWindow().getDecorView(); }
    private View latestRoot() { return ShadowDialog.getLatestDialog().getWindow().getDecorView(); }
    private GridView grid() { return find(root(), GridView.class); }
    private View itemView(int position) { return grid().getAdapter().getView(position, null, grid()); }

    private static View byText(View root, String text) {
        if (root instanceof TextView && text.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = byText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static View byDescription(View root, String text) {
        if (text.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = byDescription(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static <T> T find(View root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T result = find(group.getChildAt(i), type);
                if (result != null) return result;
            }
        }
        return null;
    }
}
