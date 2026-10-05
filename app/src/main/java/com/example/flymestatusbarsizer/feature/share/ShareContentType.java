package com.example.flymestatusbarsizer.feature.share;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Locale;

public enum ShareContentType {
    DEFAULT("默认 / 其他", "*/*"), IMAGE("图片", "image/*"), VIDEO("视频", "video/*"),
    TEXT("文本", "text/*"), PDF("PDF", "application/pdf"), AUDIO("音频", "audio/*");

    public final String label;
    public final String mime;

    ShareContentType(String label, String mime) { this.label = label; this.mime = mime; }
    @Override public String toString() { return label; }

    public static ShareContentType fromMime(String mime) {
        if (mime == null) return DEFAULT;
        String type = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (type.startsWith("image/")) return IMAGE;
        if (type.startsWith("video/")) return VIDEO;
        if (type.startsWith("text/")) return TEXT;
        if (type.equals("application/pdf")) return PDF;
        if (type.startsWith("audio/")) return AUDIO;
        return DEFAULT;
    }

    /** Uses the share payload, including Flyme wrappers and generic file MIME types. */
    public static ShareContentType resolve(Context context, Intent intent) {
        if (intent == null) return DEFAULT;
        try {
            ClipData outerClip = null;
            // Flyme's IntentParser unwraps EXTRA_INTENT even for an explicitly launched chooser
            // whose outer action is absent. Keep a bound for malformed/nested wrappers.
            for (int depth = 0; depth < 4 && intent.hasExtra(Intent.EXTRA_INTENT); depth++) {
                Object payload = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (!(payload instanceof Intent)) return DEFAULT;
                if (intent.getClipData() != null) outerClip = intent.getClipData();
                intent = (Intent) payload;
            }
            if (!Intent.ACTION_SEND.equals(intent.getAction())
                    && !Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) return DEFAULT;
            String mime = intent.getType();
            // A concrete declaration stays authoritative (including unsupported types such as ZIP).
            if (!isGenericMime(mime)) return fromMime(mime);

            Set<Uri> uris = new LinkedHashSet<>();
            boolean hasStream = intent.hasExtra(Intent.EXTRA_STREAM);
            Object stream = hasStream ? intent.getExtras().get(Intent.EXTRA_STREAM) : null;
            if (stream instanceof Uri) {
                uris.add((Uri) stream);
            } else if (stream instanceof ArrayList<?>) {
                for (Object value : (ArrayList<?>) stream) {
                    if (!(value instanceof Uri)) return DEFAULT;
                    uris.add((Uri) value);
                }
            } else if (hasStream) {
                return DEFAULT;
            }
            ClipData clip = intent.getClipData() != null ? intent.getClipData() : outerClip;
            if (uris.isEmpty() && clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            }
            if (uris.isEmpty() && intent.getData() != null) uris.add(intent.getData());

            ShareContentType clipType = attachmentClipType(clip);
            ShareContentType result = null;
            for (Uri uri : uris) {
                ShareContentType next = fromUri(context, uri);
                if (next == null) next = clipType;
                if (next == null || next == DEFAULT || (result != null && result != next)) return DEFAULT;
                result = next;
            }
            if (result != null) return result;
            if (hasStream) return DEFAULT;
            if (clipType != null) return clipType;
            return intent.hasExtra(Intent.EXTRA_TEXT) ? TEXT : DEFAULT;
        } catch (RuntimeException error) {
            // Missing URI permissions or malformed extras must not break the system share sheet.
            return DEFAULT;
        }
    }

    private static boolean isGenericMime(String mime) {
        if (mime == null) return true;
        String type = mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.isEmpty() || type.equals("*/*") || type.equals("application/*")
                || type.equals("application/octet-stream") || type.equals("application/mz-octet-stream");
    }

    /** null means no evidence; DEFAULT means an identified, unsupported file type. */
    private static ShareContentType fromUri(Context context, Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try {
                String mime = context.getContentResolver().getType(uri);
                if (!isGenericMime(mime)) return fromMime(mime);
            } catch (RuntimeException ignored) {
                // A provider may deny MIME lookup while still exposing file metadata.
            }
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    ShareContentType type = column < 0 ? null : fromFileName(cursor.getString(column));
                    if (type != null) return type;
                }
            } catch (RuntimeException ignored) { }
        }
        return "content".equals(uri.getScheme()) || "file".equals(uri.getScheme())
                ? fromFileName(uri.getLastPathSegment()) : null;
    }

    private static ShareContentType fromFileName(String name) {
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return null;
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return isGenericMime(mime) ? null : fromMime(mime);
    }

    private static ShareContentType attachmentClipType(ClipData clip) {
        if (clip == null) return null;
        ClipDescription description = clip.getDescription();
        ShareContentType result = null;
        for (int i = 0; i < description.getMimeTypeCount(); i++) {
            String mime = description.getMimeType(i);
            if (isGenericMime(mime)) continue;
            ShareContentType next = fromMime(mime);
            // Flyme screenshot ClipData can say text/plain even though its URI is an image.
            // Text hints/captions must never turn an unidentified file into a text share.
            if (next == TEXT) continue;
            if (next == DEFAULT || (result != null && result != next)) return null;
            result = next;
        }
        return result;
    }
}
