package com.example.flymestatusbarsizer;

import android.app.Activity;
import android.app.BroadcastOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedInterface;

final class StatusBarTintHooks {
    private static final String TAG = "StatusBarTint";
    private static final String LAUNCHER = "com.meizu.flyme.launcher";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String ACTION = "com.fiyme.statusbarsizer.STATUS_BAR_SCENE";
    private static final String REQUEST = ACTION + ".REQUEST";
    private static final String PHONE = "com.android.systemui.statusbar.phone.";
    private static final String HEADER = "com.flyme.systemui.statusbar.phone.StatusBarHeaderView";
    private static final String QS = "com.flyme.systemui.controlcenter.qs.QSStatusBarController";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<Object, Integer> PANEL_COLORS = new WeakHashMap<>();
    private static final Map<View, WeakReference<Object>> LOCK_VIEWS = new WeakHashMap<>();
    private static final Runnable UPDATE = StatusBarTintHooks::updateColors;
    private static final Runnable SEND = () -> sendLauncherScene(false);
    private static WeakReference<Activity> launcherActivity = new WeakReference<>(null);
    private static Object dispatcher, panel, center, keyguard, statusState;
    private static Context systemContext;
    private static int launcherScene = -1, sentScene = -2, lastScene = -2;
    private static String appearancePackage = "";
    private static boolean launcherReceiverRegistered;
    private static boolean replaying;
    // Native dispatcher inputs are preserved even while receivers see a fixed tint.
    private static Object[] nativeDispatch;

    private StatusBarTintHooks() {}

    static void installSystemUi(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(PHONE + "DarkIconDispatcherImpl", false, loader);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                module.intercept(constructor, chain -> {
                    Object result = chain.proceed();
                    if (chain.getArgs().size() > 1 && Integer.valueOf(0).equals(chain.getArg(0))
                            && chain.getArg(1) instanceof Context) {
                        dispatcher = chain.getThisObject();
                        registerSceneReceiver((Context) chain.getArg(1));
                    }
                    return result;
                });
            }
        } catch (Throwable error) {
            Log.w(TAG, "Dispatcher unavailable", error);
        }
        for (String method : new String[]{"applyIconTint", "addDarkReceiver", "applyDark"}) {
            hook(module, loader, PHONE + "DarkIconDispatcherImpl", method, chain -> {
                int mode = mode(scene());
                Object target = chain.getThisObject();
                if (target != dispatcher || mode == 0 || nativeDispatch != null) return chain.proceed();
                Object[] saved = {field(target, "mTintAreas"), field(target, "mDarkIntensity"),
                        field(target, "mIconTint"), field(target, "mContrastTint")};
                nativeDispatch = saved;
                try {
                    ReflectUtils.setField(target, "mTintAreas", new ArrayList<>());
                    ReflectUtils.setFloatField(target, "mDarkIntensity", mode == 1 ? 1f : 0f);
                    ReflectUtils.setIntField(target, "mIconTint", color(mode));
                    ReflectUtils.setIntField(target, "mContrastTint", color(mode == 1 ? 2 : 1));
                    return chain.proceed();
                } finally {
                    ReflectUtils.setField(target, "mTintAreas", saved[0]);
                    ReflectUtils.setField(target, "mDarkIntensity", saved[1]);
                    ReflectUtils.setField(target, "mIconTint", saved[2]);
                    ReflectUtils.setField(target, "mContrastTint", saved[3]);
                    nativeDispatch = null;
                }
            });
        }
        for (String type : new String[]{HEADER, QS}) {
            hook(module, loader, type, "onDarkChanged", chain -> {
                // Panel caches must retain native colors, not the desktop override.
                Object[] saved = nativeDispatch;
                return saved == null ? chain.proceed()
                        : chain.proceed(new Object[]{saved[0], saved[1], saved[2]});
            });
            // The expanded header also colors editing controls and dates; only tint the status bar.
            if (!QS.equals(type)) continue;
            hook(module, loader, type, "updateViewColor", chain -> {
                Object target = chain.getThisObject();
                if (!replaying) PANEL_COLORS.put(target, (Integer) chain.getArg(0));
                int currentScene = scene();
                // Each panel owns its tint; a system-default panel never inherits home/lock overrides.
                int panelScene = bool(target, "mIsBelongToClassicPanel")
                        ? (currentScene == 3 ? 3 : 2) : 3;
                int selected = mode(currentScene == panelScene ? panelScene : -1);
                return selected == 0 ? chain.proceed()
                        : chain.proceed(new Object[]{color(selected)});
            });
        }
        hook(module, loader, PHONE + "KeyguardStatusBarView", "updateIconsAndTextColors", chain -> {
            View view = (View) chain.getThisObject();
            Object manager = chain.getArg(0);
            LOCK_VIEWS.put(view, new WeakReference<>(manager));
            int currentScene = scene();
            int selected = mode(currentScene == 2 || currentScene == 3 ? currentScene :
                    currentScene == 4 ? 4 : -1);
            if (selected == 0) return chain.proceed();
            try {
                applyLockColor(view, manager, selected);
                return null;
            } catch (ReflectiveOperationException error) {
                Log.w(TAG, "Lockscreen tint unavailable", error);
                return chain.proceed();
            }
        });
        watch(module, loader, "com.android.systemui.shade.NotificationPanelViewController",
                new String[]{"updateExpansionAndVisibility", "updateVisibility"}, 0);
        watch(module, loader, "com.flyme.systemui.controlcenter.phone.CenterController",
                new String[]{"updatePanelExpanded"}, 1);
        watch(module, loader, "com.android.systemui.statusbar.policy.KeyguardStateControllerImpl",
                new String[]{"notifyKeyguardState", "notifyPrimaryBouncerShowing",
                        "notifyKeyguardGoingAway"}, 2);
        watch(module, loader, "com.android.systemui.statusbar.StatusBarStateControllerImpl",
                new String[]{"setState", "setIsDozing", "onShadeOrQsExpanded"}, 3);
        hook(module, loader, "com.android.systemui.shade.QuickSettingsControllerImpl", "setExpanded", chain -> {
            Object result = chain.proceed();
            sceneChanged();
            return result;
        });
        hook(module, loader, "com.android.systemui.statusbar.CommandQueue", "onSystemBarAttributesChanged", chain -> {
            if (Integer.valueOf(0).equals(chain.getArg(0))) {
                String name = (String) chain.getArg(6);
                MAIN.post(() -> {
                    appearancePackage = name;
                    sceneChanged();
                });
            }
            return chain.proceed();
        });
    }

    private static void watch(FlymeStatusBarSizer module, ClassLoader loader, String type,
            String[] names, int kind) {
        for (String name : names) hook(module, loader, type, name, chain -> {
            Object result = chain.proceed();
            switch (kind) {
                case 0: panel = chain.getThisObject(); break;
                case 1: center = chain.getThisObject(); break;
                case 2: keyguard = chain.getThisObject(); break;
                case 3: statusState = chain.getThisObject(); break;
                default: break;
            }
            sceneChanged();
            return result;
        });
    }

    private static int scene() {
        boolean locked = bool(keyguard, "mShowing");
        int state = ReflectUtils.getIntField(statusState, "mState", -1);
        boolean blocked = bool(statusState, "mIsDozing") || bool(panel, "mDozing")
                || bool(panel, "mIsPrimaryBouncerShowing") || bool(keyguard, "mPrimaryBouncerShowing")
                || (locked && bool(keyguard, "mOccluded")) || bool(keyguard, "mKeyguardGoingAway");
        boolean control = positive(center, "mExpandedFraction");
        boolean shade = positive(panel, "mExpandedFraction") && (!locked || state == 2);
        boolean qs = shade && Boolean.TRUE.equals(
                ReflectUtils.invokeNoArg(field(panel, "mQsController"), "getExpanded"));
        return selectScene(blocked, control || qs, shade, locked && state == 1,
                !locked && LAUNCHER.equals(appearancePackage) ? launcherScene : -1);
    }

    // Overlay scenes take precedence; unknown/occluded states always use native colors.
    static int selectScene(boolean blocked, boolean control, boolean shade, boolean locked, int launcher) {
        if (blocked) return -1;
        if (control) return 3;
        if (shade) return 2;
        if (locked) return 4;
        return launcher == 0 || launcher == 1 ? launcher : -1;
    }

    private static int mode(int scene) {
        if (scene < 0) return 0;
        ModuleConfig config = ModuleConfig.load(null);
        return config.enabled ? config.statusBarTintModes[scene] : 0;
    }

    private static int color(int mode) { return mode == 1 ? Color.BLACK : Color.WHITE; }
    private static Object field(Object object, String name) { return ReflectUtils.getField(object, name); }
    private static boolean bool(Object object, String name) {
        return ReflectUtils.getBooleanField(object, name, false);
    }
    private static boolean positive(Object object, String name) {
        Object value = field(object, name);
        return value instanceof Number && ((Number) value).floatValue() > 0f;
    }

    private static void sceneChanged() {
        int current = scene();
        if (current == lastScene) return;
        lastScene = current;
        refresh();
    }

    static void refresh() {
        MAIN.removeCallbacks(UPDATE);
        MAIN.post(UPDATE);
    }

    private static void updateColors() {
        ReflectUtils.invokeNoArg(dispatcher, "applyIconTint");
        replaying = true;
        try {
            for (Map.Entry<Object, Integer> entry : new ArrayList<>(PANEL_COLORS.entrySet())) {
                ReflectUtils.invokeMethod(entry.getKey(), "updateViewColor",
                        new Class<?>[]{int.class}, entry.getValue());
            }
            for (Map.Entry<View, WeakReference<Object>> entry : new ArrayList<>(LOCK_VIEWS.entrySet())) {
                Object manager = entry.getValue().get();
                if (manager != null) ReflectUtils.invokeMethod(entry.getKey(), "updateIconsAndTextColors",
                        new Class<?>[]{manager.getClass()}, manager);
            }
        } finally {
            replaying = false;
        }
    }

    private static void applyLockColor(View view, Object manager, int mode) throws ReflectiveOperationException {
        int tint = color(mode);
        int contrast = color(mode == 1 ? 2 : 1);
        float intensity = mode == 1 ? 1f : 0f;
        ArrayList<?> areas = (ArrayList<?>) field(view, "mEmptyTintRect");
        Class<?> changeType = Class.forName(PHONE + "SysuiDarkIconDispatcher$DarkChange", false,
                view.getClass().getClassLoader());
        Object change = changeType.getConstructor(Collection.class, float.class, int.class)
                .newInstance(areas, intensity, tint);
        for (String name : new String[]{"mCarrierLabel", "mClock", "mDate"}) {
            Object text = field(view, name);
            if (text instanceof TextView) ((TextView) text).setTextColor(tint);
        }
        View user = (View) field(view, "mUserSwitcherContainer");
        if (user != null) {
            int id = view.getResources().getIdentifier("current_user_name", "id", SYSTEM_UI);
            View text = id == 0 ? null : user.findViewById(id);
            if (text instanceof TextView) ((TextView) text).setTextColor(tint);
        }
        ReflectUtils.invokeMethod(manager, "setTint", new Class<?>[]{int.class, int.class}, tint, contrast);
        ReflectUtils.invokeMethod(field(view, "mDarkChange"), "setValue", new Class<?>[]{Object.class}, change);
        for (String name : new String[]{"battery", "clock", "connection_rate", "battery_percent"}) {
            int id = view.getResources().getIdentifier(name, "id", SYSTEM_UI);
            if (id != 0) ReflectUtils.invokeMethod(view, "applyDarkness",
                    new Class<?>[]{int.class, ArrayList.class, float.class, int.class}, id, areas, intensity, tint);
        }
    }

    private static void registerSceneReceiver(Context context) {
        if (systemContext != null || Build.VERSION.SDK_INT < 34) return;
        systemContext = context;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (!LAUNCHER.equals(getSentFromPackage())) return;
                launcherScene = intent.getIntExtra("scene", -1);
                sceneChanged();
            }
        }, new IntentFilter(ACTION), Context.RECEIVER_EXPORTED);
        send(context, new Intent(REQUEST).setPackage(LAUNCHER));
    }

    static void installLauncher(FlymeStatusBarSizer module, ClassLoader loader) {
        for (String method : new String[]{"onResume", "onPause", "onWindowFocusChanged"}) {
            hook(module, loader, "android.app.Activity", method, chain -> {
                Object result = chain.proceed();
                Activity activity = (Activity) chain.getThisObject();
                if (!LAUNCHER.equals(activity.getPackageName())) return result;
                if (ReflectUtils.invokeNoArg(activity, "getStateManager") != null) {
                    launcherActivity = new WeakReference<>(activity);
                    registerLauncherReceiver(activity.getApplicationContext());
                    MAIN.removeCallbacks(SEND);
                    MAIN.post(SEND);
                }
                return result;
            });
        }
        for (String method : new String[]{"onStateTransitionStart", "onStateTransitionEnd"}) {
            hook(module, loader, "com.android.launcher3.statemanager.StateManager", method, chain -> {
                Object result = chain.proceed();
                MAIN.removeCallbacks(SEND);
                MAIN.post(SEND);
                return result;
            });
        }
    }

    private static void registerLauncherReceiver(Context context) {
        if (launcherReceiverRegistered || Build.VERSION.SDK_INT < 34) return;
        context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent intent) {
                if (SYSTEM_UI.equals(getSentFromPackage())) sendLauncherScene(true);
            }
        }, new IntentFilter(REQUEST), Context.RECEIVER_EXPORTED);
        launcherReceiverRegistered = true;
    }

    private static void sendLauncherScene(boolean force) {
        Activity activity = launcherActivity.get();
        if (activity == null || Build.VERSION.SDK_INT < 34) return;
        int selected = -1;
        if (activity.hasWindowFocus() && !activity.isFinishing() && !activity.isDestroyed()) {
            Object manager = ReflectUtils.invokeNoArg(activity, "getStateManager");
            Object state = ReflectUtils.invokeNoArg(manager, "getState");
            Object normal = ReflectUtils.getStaticField(activity.getClassLoader(),
                    "com.android.launcher3.LauncherState", "NORMAL");
            if (state != null && state == normal) selected = 0;
            else if (bool(state, "isRecentsViewVisible")) selected = 1;
        }
        if (!force && selected == sentScene) return;
        sentScene = selected;
        send(activity, new Intent(ACTION).setPackage(SYSTEM_UI).putExtra("scene", selected));
    }

    private static void send(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= 34) context.sendBroadcast(intent, null,
                BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle());
    }

    private static void hook(FlymeStatusBarSizer module, ClassLoader loader, String type,
            String name, XposedInterface.Hooker hooker) {
        try {
            Class<?> clazz = Class.forName(type, false, loader);
            boolean found = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (!name.equals(method.getName())) continue;
                method.setAccessible(true);
                module.intercept(method, hooker);
                found = true;
            }
            if (!found) Log.w(TAG, "Missing hook: " + type + "." + name);
        } catch (Throwable error) {
            Log.w(TAG, "Cannot hook " + type + "." + name, error);
        }
    }
}
