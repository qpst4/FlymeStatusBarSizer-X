package com.example.flymestatusbarsizer.feature.share;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
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

    /** Uses the actual SEND intent, including URI types when the sender uses a wildcard. */
    public static ShareContentType resolve(Context context, Intent intent) {
        if (intent == null) return DEFAULT;
        try {
            if (Intent.ACTION_CHOOSER.equals(intent.getAction())) {
                intent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (intent == null) return DEFAULT;
            }
            if (!Intent.ACTION_SEND.equals(intent.getAction())
                    && !Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) return DEFAULT;
            String mime = intent.getType();
            ShareContentType declared = fromMime(mime);
            // A specific MIME family is authoritative; URI probing is only needed for */* or absent types.
            if (mime != null && !mime.equals("*/*")) return declared;
            List<Uri> uris = new ArrayList<>();
            if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                ArrayList<Uri> streams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (streams != null) uris.addAll(streams);
            } else {
                Uri stream = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                if (stream != null) uris.add(stream);
            }
            if (uris.isEmpty()) {
                ClipData clip = intent.getClipData();
                if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) uris.add(uri);
                }
            }
            ShareContentType result = null;
            for (Uri uri : uris) {
                ShareContentType next = fromMime(context.getContentResolver().getType(uri));
                if (next == DEFAULT || (result != null && result != next)) return DEFAULT;
                result = next;
            }
            if (result != null) return result;
            return intent.hasExtra(Intent.EXTRA_TEXT) ? TEXT : DEFAULT;
        } catch (RuntimeException error) {
            // Missing URI permissions or malformed extras must not break the system share sheet.
            return DEFAULT;
        }
    }
}
