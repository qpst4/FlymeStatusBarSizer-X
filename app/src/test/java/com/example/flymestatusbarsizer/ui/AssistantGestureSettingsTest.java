package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowAlertDialog;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantGestureSettingsTest {
    private TestActivity activity;
    private View card;
    private static final String KEY = SettingsStore.KEY_ASSISTANT_GESTURE_SCENES;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TestActivity.class).get();
        activity.prefs().edit().clear().commit();
        card = new SettingsCardFactory(activity).createAssistantGestureSettingsCard();
    }

    @Test public void defaultIsAllAndCancelDiscardsPendingChanges() {
        assertNotNull(byText(card, "常规界面、通知栏、控制中心"));
        AlertDialog dialog = open();
        for (int i = 0; i < 3; i++) assertTrue(dialog.getListView().isItemChecked(i));
        toggle(dialog, 1);
        assertFalse(activity.prefs().contains(KEY));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(activity.prefs().contains(KEY));
        assertNotNull(byText(card, "常规界面、通知栏、控制中心"));
        assertTrue(open().getListView().isItemChecked(1));
    }

    @Test public void confirmSavesCombinationAndEmptySelectionSurvivesReopening() {
        AlertDialog dialog = open();
        toggle(dialog, 1);
        confirm(dialog);
        assertEquals(5, activity.prefs().getInt(KEY, -1));
        assertNotNull(byText(card, "常规界面、控制中心"));
        dialog = open();
        assertFalse(dialog.getListView().isItemChecked(1));
        toggle(dialog, 0);
        toggle(dialog, 2);
        confirm(dialog);
        assertEquals(0, activity.prefs().getInt(KEY, -1));
        assertNotNull(byText(card, "未选择触发场景"));
        card = new SettingsCardFactory(activity).createAssistantGestureSettingsCard();
        assertNotNull(byText(card, "未选择触发场景"));
        dialog = open();
        for (int i = 0; i < 3; i++) assertFalse(dialog.getListView().isItemChecked(i));
        toggle(dialog, 1);
        confirm(dialog);
        assertEquals(2, activity.prefs().getInt(KEY, -1));
        assertNotNull(byText(card, "通知栏"));
    }

    private AlertDialog open() {
        View title = byText(card, "触发场景");
        assertNotNull(title);
        ((View) title.getParent()).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        return ShadowAlertDialog.getLatestAlertDialog();
    }

    private void toggle(AlertDialog dialog, int index) {
        ListView list = dialog.getListView();
        list.performItemClick(list.getChildAt(index), index, list.getAdapter().getItemId(index));
    }

    private void confirm(AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static View byText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = byText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static class TestActivity extends MainActivity {
        @Override public SharedPreferences prefs() {
            return getSharedPreferences("assistant-settings-test", MODE_PRIVATE);
        }
        @Override public void putIntSetting(String key, int value) {
            prefs().edit().putInt(key, value).apply();
        }
    }
}
