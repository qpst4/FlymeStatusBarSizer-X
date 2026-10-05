package com.example.flymestatusbarsizer.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class EnabledScopeRestartTest {
    private TestActivity activity;
    private LinearLayout content;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TestActivity.class).get();
        content = new LinearLayout(activity);
    }

    @Test public void onlyEnabledAndInstalledAppsHaveButtons() {
        activity.installed.addAll(Set.of(RestartTarget.GALLERY, RestartTarget.GBOARD));
        render(Set.of(RestartTarget.GALLERY.packageName, RestartTarget.CARLINK.packageName));
        assertNotNull(byText(content, "Flyme 图库"));
        assertNull(byText(content, "Gboard"));
        assertNull(byText(content, "CarLink"));
        assertNull(byText(content, "重启手机"));
        byText(content, "重启").performClick();
        assertEquals(RestartTarget.GALLERY, activity.restarted);
    }

    @Test public void refreshedScopeRemovesOldButtonsAndControlsDeviceRestartVisibility() {
        activity.installed.add(RestartTarget.GBOARD);
        render(Set.of("android"));
        assertNotNull(byText(content, "重启手机"));
        byText(content, "重启手机").performClick();
        assertEquals(RestartTarget.FRAMEWORK, activity.restarted);
        render(Set.of(RestartTarget.GBOARD.packageName));
        assertNull(byText(content, "重启手机"));
        assertNotNull(byText(content, "Gboard"));
        byText(content, "重启").performClick();
        assertEquals(RestartTarget.GBOARD, activity.restarted);
    }

    @Test public void unavailableScopeHasRetryAndIsDifferentFromEmptyScope() {
        activity.installed.add(RestartTarget.GBOARD);
        render(Set.of(RestartTarget.GBOARD.packageName));
        render(null);
        assertNull(byText(content, "重启全部"));
        assertNull(byText(content, "Gboard"));
        assertNull(byText(content, "重启"));
        assertNotNull(byText(content, "无法读取已启用的作用域，请确认 LSPosed 和模块已启用。"));
        byText(content, "重试").performClick();
        assertEquals(1, activity.refreshCount);
        render(Set.of());
        assertNull(byText(content, "重启全部"));
        assertNotNull(byText(content, "尚未启用任何作用域，请在 LSPosed 中勾选后返回。"));
        assertNull(byText(content, "重试"));
    }

    @Test public void loadingAndUnsupportedScopesDoNotExposeRestartButtons() {
        render(Set.of("android"));
        assertNull(byText(content, "重启全部"));
        HomePageController.renderRestartTargets(activity, content, null, true);
        assertNull(byText(content, "重启手机"));
        assertNull(byText(content, "重启全部"));
        assertNotNull(byText(content, "正在读取已启用的作用域…"));
        render(Set.of("com.example.unsupported"));
        assertNotNull(byText(content, "已启用的作用域中没有可重启的已安装应用。"));
        assertNull(byText(content, "重启"));
    }

    @Test public void batchButtonUsesTheVisibleScopeAndDisablesRestartsWhileRunning() {
        activity.installed.addAll(Set.of(RestartTarget.GALLERY, RestartTarget.GBOARD));
        Set<String> scope = Set.of("android", RestartTarget.GALLERY.packageName);
        render(scope);
        View button = byText(content, "重启全部");
        assertNotNull(button);
        assertTrue(button.isEnabled());
        assertNotNull(byText(content, "共 1 个应用，不含系统框架，不会重启手机。"));
        button.performClick();
        assertEquals(scope, activity.batchScope);
        activity.batchRunning = true;
        render(scope);
        assertFalse(byText(content, "重启中…").isEnabled());
        assertFalse(byText(content, "重启").isEnabled());
        assertFalse(byText(content, "重启手机").isEnabled());
    }

    private void render(Set<String> scope) {
        HomePageController.renderRestartTargets(activity, content, scope, false);
    }

    private static View byText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View result = byText(group.getChildAt(i), text);
                if (result != null) return result;
            }
        }
        return null;
    }

    public static class TestActivity extends MainActivity {
        final Set<RestartTarget> installed = new HashSet<>();
        RestartTarget restarted;
        int refreshCount;
        boolean batchRunning;
        Set<String> batchScope;

        @Override public boolean isBatchRestartRunning() {
            return batchRunning;
        }

        @Override public void restartAllScopeApps(Set<String> scope) {
            batchScope = scope;
        }

        @Override public boolean isRestartTargetInstalled(RestartTarget target) {
            return target == RestartTarget.FRAMEWORK || installed.contains(target);
        }

        @Override public void restartScopeApp(RestartTarget target) {
            restarted = target;
        }

        @Override public void refreshRestartScope() {
            refreshCount++;
        }
    }
}
