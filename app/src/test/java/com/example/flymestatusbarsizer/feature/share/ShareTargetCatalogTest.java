package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.List;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class ShareTargetCatalogTest {
    @Test public void unavailableFlymeRankingKeepsQueryOrderAndDoesNotSortByName() {
        Context context = RuntimeEnvironment.getApplication();
        ShadowPackageManager pm = Shadows.shadowOf(context.getPackageManager());
        ResolveInfo z = target("z.app", "Z app");
        ResolveInfo a = target("a.app", "A app");
        ResolveInfo m = target("m.app", "M app");
        ResolveInfo disabled = target("disabled.app", "Disabled");
        disabled.activityInfo.enabled = false;
        ResolveInfo privateActivity = target("private.app", "Private");
        privateActivity.activityInfo.exported = false;
        pm.addResolveInfoForIntent(new Intent(Intent.ACTION_SEND).setType("image/*"),
                List.of(z, a, disabled, privateActivity));
        pm.addResolveInfoForIntent(new Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*"), List.of(a, m));

        ShareTargetCatalog.Snapshot snapshot = ShareTargetCatalog.load(context, ShareContentType.IMAGE);
        assertFalse(snapshot.systemOrderAvailable); // AOSP test runtime has no Flyme framework.
        assertEquals(List.of("z.app/z.app.Share", "a.app/a.app.Share", "m.app/m.app.Share"),
                snapshot.targets.stream().map(t -> t.component).collect(Collectors.toList()));
    }

    @Test @Config(shadows = QueryPackageManager.class)
    public void sessionReusesLabelsAndIconResourcesButNotDrawableInstances() {
        Context context = RuntimeEnvironment.getApplication();
        QueryPackageManager pm = (QueryPackageManager) Shadows.shadowOf(context.getPackageManager());
        CountingInfo info = new CountingInfo(target("shared.app", "Shared"));
        for (String mime : List.of("image/*", "application/pdf")) {
            pm.results.put(mime, List.of(info));
        }
        ShareTargetCatalog.Session session = new ShareTargetCatalog.Session();
        ShareTargetCatalog.Target image = session.load(context, ShareContentType.IMAGE).targets.get(0);
        ShareTargetCatalog.Target pdf = session.load(context, ShareContentType.PDF).targets.get(0);
        assertEquals(1, info.labelLoads);
        assertEquals(1, info.iconLoads);
        assertEquals(image.label, pdf.label);
        assertNotSame(image.icon, pdf.icon);
        new ShareTargetCatalog.Session().load(context, ShareContentType.IMAGE);
        assertEquals(2, info.labelLoads);
        assertEquals(2, info.iconLoads);
    }

    @Test @Config(shadows = QueryPackageManager.class)
    public void sessionPreservesMimeSpecificLabelsForTheSameComponent() {
        Context context = RuntimeEnvironment.getApplication();
        QueryPackageManager pm = (QueryPackageManager) Shadows.shadowOf(context.getPackageManager());
        pm.results.put("image/*", List.of(target("shared.app", "Send image")));
        pm.results.put("application/pdf", List.of(target("shared.app", "Send PDF")));
        ShareTargetCatalog.Session session = new ShareTargetCatalog.Session();
        assertEquals("Send image", session.load(context, ShareContentType.IMAGE).targets.get(0).label);
        assertEquals("Send PDF", session.load(context, ShareContentType.PDF).targets.get(0).label);
    }

    @Test @Config(shadows = QueryPackageManager.class)
    public void cancellationBetweenTargetsStopsFurtherResourceLoads() {
        Context context = RuntimeEnvironment.getApplication();
        QueryPackageManager pm = (QueryPackageManager) Shadows.shadowOf(context.getPackageManager());
        CountingInfo first = new CountingInfo(target("first.app", "First"));
        CountingInfo second = new CountingInfo(target("second.app", "Second"));
        pm.results.put("image/*", List.of(first, second));
        ShareTargetCatalog.Session session = new ShareTargetCatalog.Session(() -> first.iconLoads > 0);
        assertThrows(java.util.concurrent.CancellationException.class,
                () -> session.load(context, ShareContentType.IMAGE));
        assertEquals(1, first.iconLoads);
        assertEquals(0, second.iconLoads);
    }

    // Preserve ResolveInfo subclasses and per-Intent metadata. The stock shadow rebuilds
    // ResolveInfo from a component registry, losing both details needed by these tests.
    @org.robolectric.annotation.Implements(className = "android.app.ApplicationPackageManager",
            isInAndroidSdk = false)
    public static class QueryPackageManager extends org.robolectric.shadows.ShadowApplicationPackageManager {
        final java.util.Map<String, List<ResolveInfo>> results = new java.util.HashMap<>();
        @org.robolectric.annotation.Implementation
        @Override protected List<ResolveInfo> queryIntentActivities(Intent intent, int flags) {
            return Intent.ACTION_SEND.equals(intent.getAction())
                    ? results.getOrDefault(intent.getType(), List.of()) : List.of();
        }
    }

    private static final class CountingInfo extends ResolveInfo {
        int labelLoads;
        int iconLoads;
        CountingInfo(ResolveInfo info) { super(info); }
        @Override public CharSequence loadLabel(android.content.pm.PackageManager pm) {
            labelLoads++;
            return nonLocalizedLabel;
        }
        @Override public android.graphics.drawable.Drawable loadIcon(android.content.pm.PackageManager pm) {
            iconLoads++;
            return new android.graphics.drawable.ColorDrawable(android.graphics.Color.BLUE);
        }
    }

    private static ResolveInfo target(String pkg, String name) {
        ResolveInfo info = new ResolveInfo();
        info.nonLocalizedLabel = name;
        info.isDefault = true;
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = pkg;
        info.activityInfo.name = pkg + ".Share";
        info.activityInfo.enabled = true;
        info.activityInfo.exported = true;
        info.activityInfo.applicationInfo = new ApplicationInfo();
        info.activityInfo.applicationInfo.packageName = pkg;
        info.activityInfo.applicationInfo.nonLocalizedLabel = name;
        info.activityInfo.applicationInfo.enabled = true;
        return info;
    }
}
