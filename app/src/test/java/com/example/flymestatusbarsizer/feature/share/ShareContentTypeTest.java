package com.example.flymestatusbarsizer.feature.share;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.example.flymestatusbarsizer.config.ModuleConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowContentResolver;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class ShareContentTypeTest {
    private final Uri image = Uri.parse("content://share-test/image");
    private final Uri video = Uri.parse("content://share-test/video");
    private final Uri pdf = Uri.parse("content://share-test/pdf");
    private final Uri audio = Uri.parse("content://share-test/audio");
    private final Uri opaquePdf = Uri.parse("content://share-test/document/42");
    private final Uri denied = Uri.parse("content://share-test/denied");

    @Before public void provider() {
        ShadowContentResolver.registerProviderInternal("share-test", new ContentProvider() {
            @Override public boolean onCreate() { return true; }
            @Override public String getType(Uri uri) {
                if (uri.equals(image)) return "image/jpeg";
                if (uri.equals(video)) return "video/mp4";
                if (uri.equals(pdf)) return "application/pdf";
                if (uri.equals(audio)) return "audio/mpeg";
                if (uri.equals(denied)) throw new SecurityException("Not granted yet");
                return "application/octet-stream";
            }
            @Override public Cursor query(Uri uri, String[] projection, String selection,
                    String[] args, String order) {
                if (uri.equals(denied)) throw new SecurityException("Not granted yet");
                MatrixCursor result = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME});
                if (uri.equals(opaquePdf)) result.addRow(new Object[]{"分享文档.PDF"});
                return result;
            }
            @Override public Uri insert(Uri uri, ContentValues values) { return null; }
            @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
            @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
        });
    }

    @Test public void genericFileMimesSelectEveryMediaProfileAtRuntime() {
        Activity activity = Robolectric.buildActivity(Activity.class).get();
        ModuleConfig config = new ModuleConfig();
        Uri[] uris = {image, video, pdf, audio};
        ShareContentType[] types = {ShareContentType.IMAGE, ShareContentType.VIDEO,
                ShareContentType.PDF, ShareContentType.AUDIO};
        for (int i = 0; i < types.length; i++) {
            String target = "pkg/pkg." + types[i].name();
            config.shareTargetProfiles.set(types[i], ShareTargetRules.parse("", target));
            for (String mime : new String[]{"application/octet-stream", "application/mz-octet-stream",
                    "application/*", "*/*; charset=utf-8"}) {
                activity.setIntent(Intent.createChooser(send(mime, uris[i]), "Share"));
                assertTrue(types[i] + " / " + mime, ShareTargetsHooks.rulesFor(config, activity).hidden().contains(target));
            }
        }
    }

    @Test public void flymeExplicitChooserWrapperDoesNotRequireChooserAction() {
        Intent wrapper = new Intent().setClassName("com.android.intentresolver",
                "com.android.intentresolver.ChooserActivity")
                .putExtra(Intent.EXTRA_INTENT, send("image/png", image));
        assertEquals(ShareContentType.IMAGE, resolve(wrapper));
        assertEquals(ShareContentType.IMAGE, resolve(Intent.createChooser(wrapper, "Share")));
        assertEquals(ShareContentType.DEFAULT, resolve(new Intent(Intent.ACTION_CHOOSER)));
    }

    @Test public void dataUriTakesPrecedenceOverAnAttachedTextCaption() {
        Intent intent = new Intent(Intent.ACTION_SEND).setData(image).putExtra(Intent.EXTRA_TEXT, "caption");
        assertEquals(ShareContentType.IMAGE, resolve(intent));
        intent.setData(Uri.parse("content://share-test/unknown"));
        assertEquals(ShareContentType.DEFAULT, resolve(intent));
    }

    @Test public void genericFilesUseDisplayNameOrFileSuffixWhenMimeIsUnavailable() {
        assertEquals(ShareContentType.PDF, resolve(send("application/octet-stream", opaquePdf)));
        assertEquals(ShareContentType.IMAGE, resolve(send("*/*", Uri.parse("file:///sdcard/DCIM/photo.JPG"))));
        assertEquals(ShareContentType.AUDIO, resolve(send(null, Uri.parse("file:///sdcard/Music/song.mp3"))));
    }

    @Test public void clipMimeCanIdentifyAnUnreadableAttachmentButItsCaptionCannot() {
        Intent intent = send("*/*", denied);
        intent.setClipData(new ClipData(new ClipDescription("file", new String[]{"image/jpeg"}), new ClipData.Item(denied)));
        assertEquals(ShareContentType.IMAGE, resolve(intent));
        intent.setClipData(new ClipData(new ClipDescription("file", new String[]{"text/plain"}), new ClipData.Item(denied)));
        intent.putExtra(Intent.EXTRA_TEXT, "caption");
        assertEquals(ShareContentType.DEFAULT, resolve(intent));
    }

    @Test public void mixedFilesStayDefaultEvenWithAnImageClipDescription() {
        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE).setType("application/octet-stream");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(List.of(image, video)));
        intent.setClipData(new ClipData(new ClipDescription("files", new String[]{"image/jpeg"}), new ClipData.Item(image)));
        assertEquals(ShareContentType.DEFAULT, resolve(intent));
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(List.of(image, image)));
        assertEquals(ShareContentType.IMAGE, resolve(intent));
    }

    @Test public void explicitMimeRemainsAuthoritativeAndNonShareIntentsStayDefault() {
        Intent screenshot = send("image/png", image);
        screenshot.setClipData(new ClipData(new ClipDescription("content", new String[]{"text/plain"}), new ClipData.Item(image)));
        assertEquals(ShareContentType.IMAGE, resolve(screenshot));
        assertEquals(ShareContentType.DEFAULT, resolve(send("application/zip", image)));
        assertEquals(ShareContentType.DEFAULT, resolve(new Intent(Intent.ACTION_VIEW).setDataAndType(image, "image/jpeg")));
        assertEquals(ShareContentType.TEXT, resolve(new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "hello")));
    }

    @Test public void malformedStreamDoesNotCrashOrBecomeText() {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("*/*")
                .putExtra(Intent.EXTRA_STREAM, "not a URI").putExtra(Intent.EXTRA_TEXT, "caption");
        assertEquals(ShareContentType.DEFAULT, resolve(intent));
    }

    private Intent send(String mime, Uri uri) {
        return new Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri);
    }

    private ShareContentType resolve(Intent intent) {
        Context context = RuntimeEnvironment.getApplication();
        return ShareContentType.resolve(context, intent);
    }
}
