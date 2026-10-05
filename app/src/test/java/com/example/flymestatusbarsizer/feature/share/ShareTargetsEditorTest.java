package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.GridView;
import android.widget.Switch;
import android.widget.Spinner;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

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

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public final class ShareTargetsEditorTest {
    private static final String FRIEND = "com.tencent.mm/com.tencent.mm.ui.tools.ShareImgUI";
    private static final String MOMENTS = "com.tencent.mm/com.tencent.mm.ui.tools.ShareToTimeLineUI";
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

    private void open() {
        new ShareTargetsEditor(activity, Runnable::run, context -> List.of(
                new ShareTargetCatalog.Target(FRIEND, "微信好友", "微信", null),
                new ShareTargetCatalog.Target(MOMENTS, "朋友圈", "微信", null))).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog = ShadowDialog.getLatestDialog();
    }

    @Test public void cancelLeavesAllPreferencesUntouched() {
        open();
        checkbox(0).setChecked(true);
        byText(root(), "返回").performClick();
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void savePersistsHideAndEnabledStateWithoutFreezingOrder() {
        open();
        checkbox(1).setChecked(true);
        ((Switch) byText(root(), "启用自定义分享列表")).setChecked(true);
        byText(root(), "保存").performClick();
        assertTrue(activity.prefs().getBoolean(SettingsStore.KEY_SHARE_TARGETS_ENABLED, false));
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        open();
        assertTrue(checkbox(1).isChecked());
        dialog.dismiss();
    }

    @Test public void accessibleMoveMenuSavesFixedOrderAndHiddenTargetStaysEditable() {
        open();
        checkbox(1).setChecked(true);
        View row = itemView(1);
        byDescription(row, "调整 朋友圈 的位置，长按拖动").performClick();
        android.app.AlertDialog menu = ShadowAlertDialog.getLatestAlertDialog();
        menu.getListView().performItemClick(null, 0, 0);
        assertTrue(checkbox(0).isChecked());
        byText(root(), "保存").performClick();
        ShareTargetRules saved = ShareTargetRules.parse(
                activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""),
                activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        assertEquals(List.of(MOMENTS, FRIEND), saved.order());
        assertTrue(saved.hidden().contains(MOMENTS));
    }

    @Test public void explicitReorderEnablesFeatureAndSavedOrderReachesRuntimeRules() throws Exception {
        open();
        assertFalse(((Switch) byText(root(), "启用自定义分享列表")).isChecked());
        byDescription(itemView(1), "调整 朋友圈 的顺序").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
        assertTrue(((Switch) byText(root(), "启用自定义分享列表")).isChecked());
        assertEquals(MOMENTS, find(root(), GridView.class).getAdapter().getItem(0));
        assertTrue(activity.prefs().getAll().isEmpty()); // Still only a draft.
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
        assertEquals(MOMENTS, find(root(), GridView.class).getAdapter().getItem(0));
        dialog.dismiss();
    }

    @Test public void returningWithoutSavingDiscardsOrderAndAutomaticEnable() {
        open();
        byDescription(itemView(1), "调整 朋友圈 的顺序").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
        byText(root(), "返回").performClick();
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void followSystemOrderRetainsHiddenSettingsAndWaitsForSave() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_TARGET_ORDER, MOMENTS + "\n" + FRIEND)
                .putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, MOMENTS).commit();
        open();
        byText(root(), "跟随系统").performClick();
        assertEquals(FRIEND, find(root(), GridView.class).getAdapter().getItem(0));
        assertTrue(checkbox(1).isChecked());
        assertFalse(byText(root(), "跟随系统").isEnabled());
        assertEquals(MOMENTS + "\n" + FRIEND, activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        byText(root(), "保存").performClick();
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void rescanFollowsNewSystemOrderUntilUserMovesAnEntry() {
        ShareTargetCatalog.Target friend = new ShareTargetCatalog.Target(FRIEND, "微信好友", "微信", null);
        ShareTargetCatalog.Target moments = new ShareTargetCatalog.Target(MOMENTS, "朋友圈", "微信", null);
        AtomicReference<List<ShareTargetCatalog.Target>> system = new AtomicReference<>(List.of(moments, friend));
        new ShareTargetsEditor(activity, Runnable::run, context -> system.get()).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog = ShadowDialog.getLatestDialog();
        assertEquals(MOMENTS, find(root(), GridView.class).getAdapter().getItem(0));
        system.set(List.of(friend, moments));
        byText(root(), "重新扫描").performClick();
        assertEquals(FRIEND, find(root(), GridView.class).getAdapter().getItem(0));
        assertFalse(byText(root(), "跟随系统").isEnabled());
        byDescription(itemView(1), "调整 朋友圈 的顺序").performClick();
        ShadowAlertDialog.getLatestAlertDialog().getListView().performItemClick(null, 0, 0);
        byText(root(), "重新扫描").performClick();
        assertEquals(MOMENTS, find(root(), GridView.class).getAdapter().getItem(0));
        assertTrue(byText(root(), "跟随系统").isEnabled());
        dialog.dismiss();
    }

    @Test public void typeCanFollowSystemOrderIndependentlyOfDefaultCustomOrder() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_TARGET_ORDER, MOMENTS + "\n" + FRIEND).commit();
        open();
        selectType(ShareContentType.IMAGE);
        assertFalse(byText(root(), "跟随系统").isEnabled());
        ((Switch) byText(root(), "单独设置此类型")).setChecked(true);
        byText(root(), "跟随系统").performClick();
        byText(root(), "保存").performClick();
        ShareTargetRules defaults = ShareTargetRules.parse(
                activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""), "");
        ShareTargetProfiles profiles = ShareTargetProfiles.parse(activity.prefs().getString(
                SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
        assertEquals(List.of(MOMENTS, FRIEND), defaults.order());
        assertTrue(profiles.hasOverride(ShareContentType.IMAGE));
        assertTrue(profiles.rulesFor(ShareContentType.IMAGE, defaults).order().isEmpty());
        assertEquals(defaults.order(), profiles.rulesFor(ShareContentType.PDF, defaults).order());
    }

    @Test public void resetIsOnlyAppliedOnSave() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, MOMENTS)
                .putString(SettingsStore.KEY_SHARE_TARGET_ORDER, MOMENTS + "\n" + FRIEND).commit();
        open();
        byText(root(), "恢复默认").performClick();
        assertFalse(checkbox(0).isChecked());
        dialog.dismiss();
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        open();
        byText(root(), "恢复默认").performClick();
        byText(root(), "保存").performClick();
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        assertEquals("", activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_ORDER, ""));
        assertFalse(activity.prefs().getBoolean(SettingsStore.KEY_SHARE_TARGETS_ENABLED, true));
    }

    @Test public void failedScanCannotOverwriteSavedRules() {
        activity.prefs().edit().putString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, MOMENTS).commit();
        new ShareTargetsEditor(activity, Runnable::run, context -> { throw new IllegalStateException("scan failed"); }).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog = ShadowDialog.getLatestDialog();
        assertFalse(byText(root(), "保存").isEnabled());
        dialog.dismiss();
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
    }

    @Test public void typeDraftsSaveTogetherAndResetOnlyAffectsSelectedType() {
        open();
        checkbox(1).setChecked(true); // Default hides Moments.
        selectType(ShareContentType.IMAGE);
        assertTrue(checkbox(1).isChecked());
        assertFalse(checkbox(1).isEnabled());
        ((Switch) byText(root(), "单独设置此类型")).setChecked(true);
        checkbox(1).setChecked(false); // Explicitly empty overrides must survive saving.
        selectType(ShareContentType.PDF);
        ((Switch) byText(root(), "单独设置此类型")).setChecked(true);
        checkbox(0).setChecked(true);
        selectType(ShareContentType.IMAGE);
        assertFalse(checkbox(1).isChecked());
        byText(root(), "重新扫描").performClick();
        assertFalse(checkbox(1).isChecked());
        byText(root(), "保存").performClick();
        ShareTargetProfiles profiles = ShareTargetProfiles.parse(activity.prefs().getString(
                SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
        assertTrue(profiles.hasOverride(ShareContentType.IMAGE));
        assertTrue(profiles.rulesFor(ShareContentType.IMAGE, ShareTargetRules.EMPTY).isEmpty());
        assertEquals(2, profiles.rulesFor(ShareContentType.PDF, ShareTargetRules.EMPTY).hidden().size());
        assertEquals(MOMENTS, activity.prefs().getString(SettingsStore.KEY_SHARE_HIDDEN_TARGETS, ""));
        open();
        selectType(ShareContentType.PDF);
        byText(root(), "恢复默认").performClick();
        assertFalse(((Switch) byText(root(), "单独设置此类型")).isChecked());
        byText(root(), "保存").performClick();
        profiles = ShareTargetProfiles.parse(activity.prefs().getString(SettingsStore.KEY_SHARE_TARGET_PROFILES, ""));
        assertFalse(profiles.hasOverride(ShareContentType.PDF));
        assertTrue(profiles.hasOverride(ShareContentType.IMAGE));
    }

    @Test public void cancelDiscardsEditsAcrossTypes() {
        open();
        selectType(ShareContentType.IMAGE);
        ((Switch) byText(root(), "单独设置此类型")).setChecked(true);
        checkbox(0).setChecked(true);
        selectType(ShareContentType.PDF);
        byText(root(), "返回").performClick();
        assertTrue(activity.prefs().getAll().isEmpty());
    }

    @Test public void selectedTypeUsesItsOwnCatalog() {
        new ShareTargetsEditor(activity, Runnable::run, (context, type) -> type == ShareContentType.PDF
                ? List.of(new ShareTargetCatalog.Target(FRIEND, "微信好友", "微信", null))
                : List.of(new ShareTargetCatalog.Target(MOMENTS, "朋友圈", "微信", null))).show();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        dialog = ShadowDialog.getLatestDialog();
        selectType(ShareContentType.PDF);
        assertNotNull(byText(itemView(0), "微信好友"));
        selectType(ShareContentType.IMAGE);
        assertNotNull(byText(itemView(0), "朋友圈"));
        dialog.dismiss();
    }

    private void selectType(ShareContentType type) {
        find(root(), Spinner.class).setSelection(type.ordinal());
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    private View root() { return dialog.getWindow().getDecorView(); }
    private View itemView(int position) {
        GridView list = find(root(), GridView.class);
        return list.getAdapter().getView(position, null, list);
    }
    private CheckBox checkbox(int position) { return find(itemView(position), CheckBox.class); }

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
