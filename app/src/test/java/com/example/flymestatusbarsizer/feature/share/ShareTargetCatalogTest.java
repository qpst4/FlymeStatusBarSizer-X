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
