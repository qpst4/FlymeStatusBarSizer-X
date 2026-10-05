package com.example.flymestatusbarsizer.feature.share;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ShareTargetCatalog {
    private ShareTargetCatalog() {}

    public static Snapshot load(Context context) {
        return load(context, ShareContentType.DEFAULT);
    }

    public static Snapshot load(Context context, ShareContentType type) {
        PackageManager pm = context.getPackageManager();
        Map<String, ResolveInfo> found = new LinkedHashMap<>();
        for (String action : new String[]{Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE}) {
            Intent intent = new Intent(action).setType(type.mime);
            for (ResolveInfo info : pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
                ActivityInfo ai = info.activityInfo;
                if (ai == null || !ai.exported || !ai.enabled || ai.applicationInfo == null
                        || !ai.applicationInfo.enabled) continue;
                String component = ShareTargetRules.normalizeComponent(ai.packageName + "/" + ai.name);
                if (component.isEmpty() || found.containsKey(component)) continue;
                found.put(component, info);
            }
        }
        ShareTargetSystemOrder.Result ordered = ShareTargetSystemOrder.load(context, new ArrayList<>(found.values()));
        List<Target> result = new ArrayList<>();
        for (ResolveInfo info : ordered.targets) {
            ActivityInfo ai = info.activityInfo;
            String component = ShareTargetRules.normalizeComponent(ai.packageName + "/" + ai.name);
            String appName = String.valueOf(ai.applicationInfo.loadLabel(pm));
            String label = familiarLabel(component, String.valueOf(info.loadLabel(pm)));
            Drawable icon;
            try { icon = info.loadIcon(pm); }
            catch (RuntimeException ignored) { icon = pm.getDefaultActivityIcon(); }
            result.add(new Target(component, label, appName, icon));
        }
        return new Snapshot(result, ordered.available);
    }

    private static String familiarLabel(String component, String fallback) {
        switch (component) {
            case "com.tencent.mm/com.tencent.mm.ui.tools.ShareImgUI": return "微信好友";
            case "com.tencent.mm/com.tencent.mm.ui.tools.ShareToTimeLineUI": return "朋友圈";
            case "com.tencent.mm/com.tencent.mm.ui.tools.AddFavoriteUI": return "微信收藏";
            case "com.tencent.mobileqq/com.tencent.mobileqq.activity.JumpActivity": return "QQ 好友";
            case "com.tencent.mobileqq/com.tencent.mobileqq.activity.qfileJumpActivity": return "我的电脑";
            default: return fallback;
        }
    }

    public static final class Snapshot {
        public final List<Target> targets;
        public final boolean systemOrderAvailable;

        Snapshot(List<Target> targets, boolean systemOrderAvailable) {
            this.targets = targets;
            this.systemOrderAvailable = systemOrderAvailable;
        }
    }

    public static final class Target {
        public final String component;
        public final String label;
        public final String appName;
        public final Drawable icon;

        public Target(String component, String label, String appName, Drawable icon) {
            this.component = component;
            this.label = label;
            this.appName = appName;
            this.icon = icon;
        }
    }
}
