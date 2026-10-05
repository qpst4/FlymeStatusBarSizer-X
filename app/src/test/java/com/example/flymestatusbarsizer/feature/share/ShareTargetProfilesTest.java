package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ShareTargetProfilesTest {
    private static final String TARGET = "pkg/pkg.Share";

    @Test public void overridesRoundTripAndEmptyOverrideDoesNotInheritHiddenTargets() {
        ShareTargetRules defaults = ShareTargetRules.parse("", TARGET);
        ShareTargetProfiles profiles = ShareTargetProfiles.parse("");
        assertSame(defaults, profiles.rulesFor(ShareContentType.IMAGE, defaults));
        profiles.set(ShareContentType.IMAGE, ShareTargetRules.EMPTY);
        profiles.set(ShareContentType.PDF, ShareTargetRules.parse(TARGET, ""));
        profiles = ShareTargetProfiles.parse(profiles.encode());
        assertTrue(profiles.rulesFor(ShareContentType.IMAGE, defaults).isEmpty());
        assertEquals(List.of(TARGET), profiles.rulesFor(ShareContentType.PDF, defaults).order());
        assertSame(defaults, profiles.rulesFor(ShareContentType.VIDEO, defaults));
        profiles.set(ShareContentType.IMAGE, null);
        assertSame(defaults, profiles.rulesFor(ShareContentType.IMAGE, defaults));
        assertFalse(ShareTargetProfiles.parse("broken").hasOverride(ShareContentType.IMAGE));
        assertFalse(ShareTargetProfiles.parse("{\"IMAGE\":{}}").hasOverride(ShareContentType.IMAGE));
        assertTrue(Arrays.asList(SettingsStore.STRING_KEYS).contains(SettingsStore.KEY_SHARE_TARGET_PROFILES));
    }

    @Test public void resolvesMimeFamiliesAndChooserWrapperForSingleAndMultipleShares() {
        String[] mimes = {"image/jpeg", "video/mp4", "text/plain", "application/pdf", "audio/mpeg", "application/zip"};
        ShareContentType[] expected = {ShareContentType.IMAGE, ShareContentType.VIDEO, ShareContentType.TEXT,
                ShareContentType.PDF, ShareContentType.AUDIO, ShareContentType.DEFAULT};
        for (int i = 0; i < mimes.length; i++) {
            for (String action : new String[]{Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE}) {
                Intent send = new Intent(action).setType(mimes[i]);
                assertEquals(expected[i], resolve(send));
                assertEquals(expected[i], resolve(Intent.createChooser(send, "Share")));
            }
        }
        assertEquals(ShareContentType.DEFAULT, resolve(new Intent(Intent.ACTION_VIEW).setType("image/png")));
        assertEquals(ShareContentType.DEFAULT, resolve(new Intent(Intent.ACTION_CHOOSER)));
    }

    @Test public void wildcardUsesUriTypesAndMixedSharesFallBackToDefaults() {
        Uri image = Uri.parse("content://test/image");
        Uri video = Uri.parse("content://test/video");
        android.content.ContentProvider provider = new android.content.ContentProvider() {
            @Override public boolean onCreate() { return true; }
            @Override public android.database.Cursor query(Uri uri, String[] projection, String selection,
                    String[] args, String order) { return null; }
            @Override public Uri insert(Uri uri, android.content.ContentValues values) { return null; }
            @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
            @Override public int update(Uri uri, android.content.ContentValues values, String selection, String[] args) { return 0; }
            @Override public String getType(Uri uri) { return uri.equals(image) ? "image/png" : "video/mp4"; }
        };
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("test", provider);
        Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*");
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(List.of(image, image)));
        assertEquals(ShareContentType.IMAGE, resolve(send));
        send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(List.of(image, video)));
        assertEquals(ShareContentType.DEFAULT, resolve(send));
        assertEquals(ShareContentType.TEXT, resolve(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello")));
    }

    @Test public void runtimeSelectionUsesCurrentActivityIntentAndIndependentEmptyRules() {
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        ModuleConfig config = new ModuleConfig();
        config.shareTargetRules = ShareTargetRules.parse("", TARGET);
        config.shareTargetProfiles.set(ShareContentType.IMAGE, ShareTargetRules.EMPTY);
        activity.setIntent(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("image/png"), "Share"));
        assertTrue(ShareTargetsHooks.rulesFor(config, activity).isEmpty());
        activity.setIntent(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("application/pdf"), "Share"));
        assertEquals(List.of(), ShareTargetsHooks.rulesFor(config, activity).apply(List.of(TARGET), s -> s));
    }

    private ShareContentType resolve(Intent intent) {
        return ShareContentType.resolve(RuntimeEnvironment.getApplication(), intent);
    }
}
