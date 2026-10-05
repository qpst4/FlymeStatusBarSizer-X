package com.example.flymestatusbarsizer.feature.share;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class ShareTargetsHooks {
    public static final String PACKAGE = "com.android.intentresolver";
    private static final String TAG = "FlymeStatusBarSizer";

    private ShareTargetsHooks() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> base = Class.forName("com.android.internal.chooser.BaseResolverHooker", false, loader);
            Class<?> item = Class.forName("com.android.internal.chooser.bean.DisplayResolveInfo", false, loader);
            Field activityField = base.getDeclaredField("mActivity");
            Field resolveInfoField = item.getDeclaredField("resolveInfo");
            activityField.setAccessible(true);
            resolveInfoField.setAccessible(true);
            Method filter = base.getDeclaredMethod("filterResolve", List.class);
            filter.setAccessible(true);
            module.intercept(filter, chain -> {
                Object original = chain.proceed();
                ModuleConfig config = ModuleConfig.load(null);
                if (!config.enabled || !config.shareTargetsEnabled
                        || !(original instanceof List<?>)) return original;
                try {
                    Object activity = activityField.get(chain.getThisObject());
                    if (!(activity instanceof Activity)
                            || !"com.android.intentresolver.ChooserActivity".equals(activity.getClass().getName())) {
                        return original;
                    }
                    // Runs before the system's zero/one-target checks and before adapter grouping.
                    ShareTargetRules rules = rulesFor(config, (Activity) activity);
                    return rules.apply((List<?>) original,
                            value -> componentOf(value, resolveInfoField));
                } catch (Throwable error) {
                    Log.w(TAG, "Share list rules failed; using system targets", error);
                    return original;
                }
            });
            deoptimize(module, base, "showNormalTargets");
            installAdapter(module, loader, resolveInfoField);
            Log.i(TAG, "Share list rules installed in IntentResolver");
        } catch (Throwable error) {
            Log.w(TAG, "Share list hooks unavailable on this system", error);
        }
    }

    private static void installAdapter(FlymeStatusBarSizer module, ClassLoader loader, Field resolveInfoField) {
        try {
            Class<?> adapter = Class.forName("com.android.internal.chooser.adapter.BaseResolverAdapter", false, loader);
            Field activityField = adapter.getDeclaredField("mActivity");
            activityField.setAccessible(true);
            Field isChooser = adapter.getDeclaredField("mIsChooserActivity");
            isChooser.setAccessible(true);
            Method rebuild = adapter.getDeclaredMethod("rebuildData", List.class);
            rebuild.setAccessible(true);
            module.intercept(rebuild, chain -> {
                Object[] args = chain.getArgs().toArray();
                Object[] replacement = args;
                try {
                    ModuleConfig config = ModuleConfig.load(null);
                    if (config.enabled && config.shareTargetsEnabled
                            && isChooser.getBoolean(chain.getThisObject()) && args[0] instanceof List<?>) {
                        ShareTargetRules rules = rulesFor(config, (Activity) activityField.get(chain.getThisObject()));
                        replacement = new Object[]{rules.apply((List<?>) args[0],
                                value -> componentOf(value, resolveInfoField))};
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Share adapter rules failed; using system targets", error);
                }
                return chain.proceed(replacement);
            });
            deoptimize(module, adapter, "setData", "refresh", "-$$Nest$mrebuildData");
            // The native long-press actions re-sort before rebuilding the list.
            Class<?> menu = Class.forName("com.android.internal.chooser.adapter.BaseResolverAdapter$ResolveInfoViewHolder$2$1",
                    false, loader);
            deoptimize(module, menu, "onMenuItemClick");
            Class<?> more = Class.forName("com.android.internal.chooser.adapter.BaseResolverAdapter$MoreButtonViewHolder$1",
                    false, loader);
            deoptimize(module, more, "onClick");
        } catch (Throwable error) {
            Log.w(TAG, "Share adapter refresh hook unavailable", error);
        }
    }

    static ShareTargetRules rulesFor(ModuleConfig config, Activity activity) {
        ShareContentType type = activity == null ? ShareContentType.DEFAULT
                : ShareContentType.resolve(activity, activity.getIntent());
        return config.shareTargetProfiles.rulesFor(type, config.shareTargetRules);
    }

    private static void deoptimize(FlymeStatusBarSizer module, Class<?> type, String... names) {
        try {
            for (Method method : type.getDeclaredMethods()) {
                for (String name : names) {
                    if (name.equals(method.getName())) module.deoptimize(method);
                }
            }
        } catch (Throwable error) {
            Log.w(TAG, "Cannot deoptimize share list callers", error);
        }
    }

    private static String componentOf(Object target, Field field) {
        if (target == null) return "";
        try {
            Object info = field.get(target);
            if (!(info instanceof ResolveInfo)) return "";
            ActivityInfo activity = ((ResolveInfo) info).activityInfo;
            return activity == null ? "" : activity.packageName + "/" + activity.name;
        } catch (IllegalAccessException error) {
            throw new IllegalStateException(error);
        }
    }
}
