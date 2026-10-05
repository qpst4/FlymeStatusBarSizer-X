package com.example.flymestatusbarsizer.feature.share;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads Flyme's current ranking without changing its favorites or usage history. */
final class ShareTargetSystemOrder {
    private static final String PREFIX = "com.android.internal.chooser.";

    private ShareTargetSystemOrder() {}

    static Result load(Context context, List<ResolveInfo> source) {
        try {
            ClassLoader loader = context.getClassLoader();
            Class<?> displayClass = Class.forName(PREFIX + "bean.DisplayResolveInfo", false, loader);
            Class<?> repositoryClass = Class.forName(PREFIX + "db.ChooserRepository", false, loader);
            Class<?> itemClass = Class.forName(PREFIX + "db.ChooserItem", false, loader);
            Class<?> appClass = Class.forName(PREFIX + "chooser.AppInfo", false, loader);
            Class<?> comparatorClass = Class.forName(PREFIX + "chooser.ChooserItemComparator", false, loader);

            // These OEM framework members are marked blacklist in the boot DEX. The
            // settings app runs outside the hooked resolver, so it needs its own exemption.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && !HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/chooser/")) {
                throw new IllegalStateException("Cannot access Flyme chooser ranking");
            }

            // A fresh, isolated repository rereads Settings.System on every scan. Using
            // ChooserFinder.sortAndLoadDisplayInfo instead would WRITE new favorites.
            Constructor<?> repositoryConstructor = repositoryClass.getDeclaredConstructor(Context.class);
            repositoryConstructor.setAccessible(true);
            Object repository = repositoryConstructor.newInstance(context);
            boolean custom = (boolean) repositoryClass.getMethod("useCustomizeFavorite").invoke(repository);
            Method itemFor = repositoryClass.getMethod("getChooserItemInfo", ResolveInfo.class);
            Method nextOrder = repositoryClass.getMethod("getNextOrder");
            Method state = itemClass.getMethod("getState");
            Method setState = itemClass.getMethod("setState", int.class);
            Method setOrder = itemClass.getMethod("setOrder", int.class);
            Method isDevice = appClass.getMethod("isDevice", ResolveInfo.class);
            Method isDefault = appClass.getMethod("isDefaultFavorite", ResolveInfo.class);
            Constructor<?> displayConstructor = displayClass.getConstructor(ResolveInfo.class, Intent.class);
            Field deviceField = displayClass.getField("isDevice");
            Field favoriteField = displayClass.getField("isFavorite");
            Field labelField = displayClass.getField("activityLabel");
            Field resolveField = displayClass.getField("resolveInfo");
            List<Object> display = new ArrayList<>();
            for (ResolveInfo info : source) {
                Object target = displayConstructor.newInstance(info, null);
                labelField.set(target, info.loadLabel(context.getPackageManager()).toString().replace("\n", ""));
                boolean device = (boolean) isDevice.invoke(null, info);
                deviceField.setBoolean(target, device);
                if (!device) {
                    Object item = itemFor.invoke(repository, info);
                    int savedState = (int) state.invoke(item);
                    boolean systemApp = (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    boolean defaultFavorite = systemApp || (boolean) isDefault.invoke(null, info);
                    boolean favorite = custom ? savedState == 1 || (savedState != 2 && defaultFavorite)
                            : defaultFavorite;
                    favoriteField.setBoolean(target, favorite);
                    if (custom && savedState != 1 && savedState != 2 && favorite) {
                        // Match the system's append behavior in this in-memory snapshot only.
                        setState.invoke(item, 1);
                        setOrder.invoke(item, nextOrder.invoke(repository));
                    }
                }
                display.add(target);
            }
            @SuppressWarnings("unchecked")
            Comparator<Object> comparator = (Comparator<Object>) comparatorClass.getConstructor(Context.class)
                    .newInstance(context);
            Field comparatorRepository = comparatorClass.getDeclaredField("mRepository");
            comparatorRepository.setAccessible(true);
            comparatorRepository.set(comparator, repository);
            display.sort(comparator);

            // The comparator puts devices first, but the actual adapter displays them last.
            List<ResolveInfo> favorites = new ArrayList<>();
            List<ResolveInfo> more = new ArrayList<>();
            List<ResolveInfo> devices = new ArrayList<>();
            for (Object target : display) {
                ResolveInfo info = (ResolveInfo) resolveField.get(target);
                if (deviceField.getBoolean(target)) devices.add(info);
                else if (favoriteField.getBoolean(target)) favorites.add(info);
                else more.add(info);
            }
            favorites.addAll(more);
            favorites.addAll(devices);
            return new Result(favorites, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            Log.w("FlymeStatusBarSizer", "Cannot read Flyme share order; retaining package query order", error);
            return new Result(new ArrayList<>(source), false);
        }
    }

    static final class Result {
        final List<ResolveInfo> targets;
        final boolean available;

        Result(List<ResolveInfo> targets, boolean available) {
            this.targets = targets;
            this.available = available;
        }
    }
}
