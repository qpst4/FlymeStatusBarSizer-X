package com.example.flymestatusbarsizer;

import android.app.AlertDialog;
import android.content.pm.PackageInfo;
import android.os.Looper;

import com.example.flymestatusbarsizer.ui.RestartTarget;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowAlertDialog;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ScopeRestartTest {
    private TestActivity activity;

    @Before public void setUp() {
        activity = Robolectric.buildActivity(TestActivity.class).get();
    }

    @Test public void restartEntriesCoverDeclaredScopesAndCanQueryEveryApp() throws Exception {
        Set<String> scopes;
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("META-INF/xposed/scope.list")) {
            assertNotNull(input);
            scopes = new HashSet<>(new String(input.readAllBytes(), StandardCharsets.UTF_8)
                    .lines().map(String::trim).filter(line -> !line.isEmpty()).toList());
        }
        Set<String> entries = new HashSet<>();
        for (RestartTarget target : RestartTarget.values()) {
            assertTrue("Duplicate restart entry: " + target.packageName, entries.add(target.packageName));
        }
        assertEquals(scopes, entries);

        var manifest = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Path.of("src/main/AndroidManifest.xml").toFile());
        var packages = manifest.getElementsByTagName("queries").item(0).getChildNodes();
        Set<String> queries = new HashSet<>();
        for (int i = 0; i < packages.getLength(); i++) {
            var node = packages.item(i);
            if ("package".equals(node.getNodeName())) {
                queries.add(node.getAttributes().getNamedItem("android:name").getNodeValue());
            }
        }
        entries.remove("android");
        assertTrue("All restartable apps need package visibility", queries.containsAll(entries));
    }

    @Test public void missingAppCannotRunRootCommands() {
        assertFalse(activity.isRestartTargetInstalled(RestartTarget.GALLERY));
        activity.restartScopeApp(RestartTarget.GALLERY);
        assertTrue(activity.commands.isEmpty());
        assertEquals("Flyme 图库未安装", activity.toast);
        install(RestartTarget.GALLERY);
        assertTrue(activity.isRestartTargetInstalled(RestartTarget.GALLERY));
        activity.restartScopeApp(RestartTarget.GALLERY);
        assertFalse(activity.commands.isEmpty());
    }

    @Test public void serviceRestartsNeverForceStopPackages() {
        for (RestartTarget target : List.of(RestartTarget.PHONE_MANAGER, RestartTarget.CARLINK,
                RestartTarget.AOSP_IME, RestartTarget.GBOARD,
                RestartTarget.WECHAT_IME, RestartTarget.FLYME_IME)) {
            install(target);
            activity.commands.clear();
            activity.restartScopeApp(target);
            assertFalse(target.label, activity.commands.isEmpty());
            for (String command : activity.commands) {
                assertFalse("Keep service bindings: " + command, command.contains("force-stop"));
                assertFalse(command.contains("reboot"));
            }
        }
    }

    @Test public void frameworkOnlyRebootsAfterExplicitConfirmation() {
        activity.restartScopeApp(RestartTarget.FRAMEWORK);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.isShowing());
        assertTrue(activity.commands.isEmpty());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.commands.isEmpty());

        activity.restartScopeApp(RestartTarget.FRAMEWORK);
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(activity.commands.isEmpty());
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of("reboot"), activity.commands);
    }

    @Test public void batchIncludesOnlyEnabledInstalledAppsAndRestartsUiHostsLast() {
        for (RestartTarget target : List.of(RestartTarget.SYSTEM_UI, RestartTarget.LAUNCHER,
                RestartTarget.GALLERY, RestartTarget.GBOARD)) install(target);
        assertEquals(List.of(RestartTarget.GALLERY, RestartTarget.LAUNCHER, RestartTarget.SYSTEM_UI),
                activity.getBatchRestartTargets(Set.of("android", "com.example.unsupported",
                        RestartTarget.GALLERY.packageName, RestartTarget.CARLINK.packageName,
                        RestartTarget.SYSTEM_UI.packageName, RestartTarget.LAUNCHER.packageName)));
        assertTrue(activity.getBatchRestartTargets(null).isEmpty());
        assertTrue(activity.getBatchRestartTargets(Set.of("android")).isEmpty());
    }

    @Test public void batchRequiresConfirmationAndCancelDoesNothing() {
        install(RestartTarget.GALLERY);
        Set<String> scope = Set.of("android", RestartTarget.GALLERY.packageName);
        activity.restartAllScopeApps(scope);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(activity.batchTargets.isEmpty());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(activity.batchTargets.isEmpty());
        activity.restartAllScopeApps(scope);
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(List.of(RestartTarget.GALLERY), activity.batchTargets);
        assertTrue(activity.commands.isEmpty());
    }

    @Test public void batchContinuesAfterFailureAndNeverRunsDeviceRestart() {
        install(RestartTarget.GALLERY);
        install(RestartTarget.GBOARD);
        activity.failedPackage = RestartTarget.GALLERY.packageName;
        String result = activity.runRestartBatch(List.of(RestartTarget.FRAMEWORK,
                RestartTarget.GALLERY, RestartTarget.GBOARD));
        assertEquals(2, activity.executed.size());
        assertArrayEquals(RestartTarget.GALLERY.restartCommands(), activity.executed.get(0));
        assertArrayEquals(RestartTarget.GBOARD.restartCommands(), activity.executed.get(1));
        assertTrue(result.contains("成功 1/2 个"));
        assertTrue(result.contains("失败：Flyme 图库"));
        for (String[] commands : activity.executed) {
            for (String command : commands) assertFalse(command.contains("reboot"));
        }
        assertThrows(IllegalStateException.class, RestartTarget.FRAMEWORK::restartCommands);
    }

    private void install(RestartTarget target) {
        PackageInfo info = new PackageInfo();
        info.packageName = target.packageName;
        shadowOf(activity.getPackageManager()).installPackage(info);
    }

    public static class TestActivity extends MainActivity {
        final List<String> commands = new ArrayList<>();
        final List<String[]> executed = new ArrayList<>();
        List<RestartTarget> batchTargets = List.of();
        String failedPackage;
        String toast;

        @Override void startRestartBatch(List<RestartTarget> targets, Set<String> enabledScope) {
            batchTargets = new ArrayList<>(targets);
        }

        @Override RootRestartResult executeRootCommands(String[] commands, boolean stopOnFirstSuccess) {
            executed.add(commands);
            boolean success = failedPackage == null || !commands[0].contains(failedPackage);
            return new RootRestartResult(success, success ? null : "test failure");
        }

        @Override void restartRootCommands(String label, String[] commands, boolean stopOnFirstSuccess) {
            this.commands.addAll(Arrays.asList(commands));
        }

        @Override public void showToast(String message) {
            toast = message;
        }
    }
}
