package com.example.flymestatusbarsizer.feature.statusbar;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Uses the container's own measurement and visibility logic for every kind of system icon. */
public final class StatusBarIconVisibilityHooks {
    private static final String TAG = "StatusBarIconVisibility";
    private static final Map<View, Boolean> CONTAINERS = Collections.synchronizedMap(new WeakHashMap<>());

    private StatusBarIconVisibilityHooks() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(
                    "com.android.systemui.statusbar.phone.StatusIconContainer", false, loader);
            Binding binding = new Binding(type);
            Method measure = type.getDeclaredMethod("onMeasure", int.class, int.class);
            Method translate = type.getDeclaredMethod("calculateIconTranslations");
            for (Method method : new Method[]{measure, translate}) {
                module.intercept(method, chain -> {
                    View container = (View) chain.getThisObject();
                    CONTAINERS.put(container, Boolean.TRUE);
                    return binding.withHiddenSlots(container, ModuleConfig.load(container.getContext()),
                            chain::proceed);
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "Status icon hiding unavailable", error);
        }
    }

    public static void refresh() {
        if (CONTAINERS.isEmpty()) return;
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(StatusBarIconVisibilityHooks::refresh);
            return;
        }
        ArrayList<View> containers;
        synchronized (CONTAINERS) {
            containers = new ArrayList<>(CONTAINERS.keySet());
        }
        for (View container : containers) {
            container.requestLayout();
            container.invalidate();
        }
    }

    @FunctionalInterface
    interface LayoutPass {
        Object run() throws Throwable;
    }

    static final class Binding {
        private final Field ignoredSlots;

        Binding(Class<?> type) throws ReflectiveOperationException {
            ignoredSlots = type.getDeclaredField("mIgnoredSlots");
            ignoredSlots.setAccessible(true);
        }

        Object withHiddenSlots(Object container, ModuleConfig config, LayoutPass layout) throws Throwable {
            if (!config.enabled || config.hiddenStatusBarSlots.isEmpty()) return layout.run();
            Object original = ignoredSlots.get(container);
            if (!(original instanceof ArrayList<?> nativeSlots)) return layout.run();
            if (nativeSlots.containsAll(config.hiddenStatusBarSlots)) return layout.run();
            ArrayList<Object> effective = new ArrayList<>(nativeSlots);
            for (String slot : config.hiddenStatusBarSlots) {
                if (!effective.contains(slot)) effective.add(slot);
            }
            // Both hooked methods only read this list. Overlay it for the synchronous layout pass;
            // native add/remove calls outside the pass retain their own reasons for hiding icons.
            ignoredSlots.set(container, effective);
            try {
                return layout.run();
            } finally {
                ignoredSlots.set(container, original);
            }
        }
    }
}
