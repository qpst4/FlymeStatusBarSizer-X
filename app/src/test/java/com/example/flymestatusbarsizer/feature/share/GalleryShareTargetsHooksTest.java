package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class GalleryShareTargetsHooksTest {
    private final ModuleConfig config = new ModuleConfig();
    private GalleryShareTargetsHooks.Binding binding;
    private Adapter adapter;

    @Before public void setup() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        activity.setIntent(new Intent().putExtra("enter_item_list", new ArrayList<String>()));
        adapter = new Adapter(activity);
        binding = new GalleryShareTargetsHooks.Binding(Adapter.class, Target.class);
        config.enabled = true;
        config.shareTargetsEnabled = true;
    }

    @Test public void usesSelectionPayloadInsteadOfGalleryLaunchIntentAndKeepsTargetIdentity() throws Exception {
        config.shareTargetRules = ShareTargetRules.parse("pkg/.A", "pkg/.B");
        config.shareTargetProfiles.set(ShareContentType.IMAGE, ShareTargetRules.parse("pkg/.B\npkg/.A", "pkg/.C"));
        List<Target> source = targets("image/jpeg", Intent.ACTION_SEND);
        source.get(1).favorite = true;
        source.get(2).device = true;

        List<?> result = binding.apply(config, adapter, source);

        assertEquals(List.of(source.get(1), source.get(0)), result);
        assertEquals(3, source.size());
        assertSame(source.get(1), result.get(0));
        assertTrue(source.get(1).favorite);
        assertTrue(source.get(2).device);
        assertNull(source.get(1).payload.getComponent());
    }

    @Test public void changingSelectionAndSendMultiplicityChoosesTheCurrentProfile() throws Exception {
        config.shareTargetProfiles.set(ShareContentType.IMAGE, ShareTargetRules.parse("pkg/.B", "pkg/.C"));
        config.shareTargetProfiles.set(ShareContentType.VIDEO, ShareTargetRules.parse("pkg/.C", "pkg/.A"));
        List<Target> source = targets("image/jpeg", Intent.ACTION_SEND);
        assertEquals(List.of(source.get(1), source.get(0)), binding.apply(config, adapter, source));

        for (Target target : source) target.payload.setAction(Intent.ACTION_SEND_MULTIPLE).setType("video/mp4");
        assertEquals(List.of(source.get(2), source.get(1)), binding.apply(config, adapter, source));

        for (Target target : source) target.payload.setAction(Intent.ACTION_SEND).setType("image/png");
        assertEquals(List.of(source.get(1), source.get(0)), binding.apply(config, adapter, source));
    }

    @Test public void inheritedAndMixedTypesUseDefaultsAndNewTargetsKeepRelativeOrder() throws Exception {
        config.shareTargetRules = ShareTargetRules.parse("pkg/.C", "");
        for (String mime : new String[]{"video/mp4", "*/*"}) {
            List<Target> source = targets(mime, Intent.ACTION_SEND_MULTIPLE);
            assertEquals(List.of(source.get(2), source.get(0), source.get(1)), binding.apply(config, adapter, source));
        }
    }

    @Test public void refreshCanRestoreHiddenTargetsWithoutRefetchingTheSource() throws Exception {
        List<Target> source = targets("image/jpeg", Intent.ACTION_SEND);
        config.shareTargetRules = ShareTargetRules.parse("", "pkg/.B");
        assertEquals(List.of(source.get(0), source.get(2)), binding.apply(config, adapter, source));
        config.shareTargetRules = ShareTargetRules.parse("pkg/.B", "");
        assertEquals(List.of(source.get(1), source.get(0), source.get(2)), binding.apply(config, adapter, source));

        config.shareTargetsEnabled = false;
        assertSame(source, binding.apply(config, adapter, source));
        config.shareTargetsEnabled = true;
        config.enabled = false;
        assertSame(source, binding.apply(config, adapter, source));
        assertEquals(List.of("pkg.A", "pkg.B", "pkg.C"), source.stream().map(t -> t.info.activityInfo.name).toList());
    }

    @Test public void supportsEmptyOrEntirelyHiddenLists() throws Exception {
        List<Target> empty = new ArrayList<>();
        assertSame(empty, binding.apply(config, adapter, empty));
        List<Target> source = targets("image/jpeg", Intent.ACTION_SEND);
        config.shareTargetRules = ShareTargetRules.parse("", "pkg/.A\npkg/.B\npkg/.C");
        assertTrue(binding.apply(config, adapter, source).isEmpty());
        assertEquals(3, source.size());
    }

    @Test public void findsObfuscatedMembersByShapeAndRejectsAmbiguousVersions() throws Exception {
        assertEquals("rebuild", binding.rebuild.getName());
        assertThrows(NoSuchMethodException.class,
                () -> new GalleryShareTargetsHooks.Binding(AmbiguousAdapter.class, Target.class));
        assertThrows(NoSuchFieldException.class,
                () -> new GalleryShareTargetsHooks.Binding(Adapter.class, AmbiguousTarget.class));
        assertThrows(NoSuchMethodException.class,
                () -> new GalleryShareTargetsHooks.Binding(Object.class, Target.class));
    }

    private static List<Target> targets(String mime, String action) {
        List<Target> result = new ArrayList<>();
        for (String name : new String[]{"A", "B", "C"}) {
            Target target = new Target();
            target.info = new ResolveInfo();
            target.info.activityInfo = new ActivityInfo();
            target.info.activityInfo.packageName = "pkg";
            target.info.activityInfo.name = "pkg." + name;
            target.payload = new Intent(action).setType(mime);
            result.add(target);
        }
        return result;
    }

    private static final class Target {
        ResolveInfo info;
        Intent payload;
        boolean favorite;
        boolean device;
    }

    private static final class Adapter {
        private final Context owner;
        Adapter(Context owner) { this.owner = owner; }
        public void setData(List<?> values) { rebuild(values); }
        private void rebuild(List<?> values) {}
        private static void accessor(Adapter adapter, List<?> values) { adapter.rebuild(values); }
    }

    private static final class AmbiguousAdapter {
        private void first(List<?> values) {}
        private void second(List<?> values) {}
    }

    private static final class AmbiguousTarget {
        ResolveInfo first;
        ResolveInfo second;
        Intent payload;
    }
}
