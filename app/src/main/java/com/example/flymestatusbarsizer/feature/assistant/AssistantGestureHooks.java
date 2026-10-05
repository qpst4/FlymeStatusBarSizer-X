package com.example.flymestatusbarsizer.feature.assistant;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;
import com.example.flymestatusbarsizer.config.SettingsStore;
import com.example.flymestatusbarsizer.util.ReflectUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

final class AssistantGestureHooks {
    private static final Map<Object, Gesture> GESTURES = new WeakHashMap<>();

    static void install(FlymeStatusBarSizer module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> type = Class.forName("com.android.systemui.navigationbar.gestural.EdgeBackGestureHandler", false, loader);
        Method motion = AssistantReflection.method(type, "onMotionEvent", MotionEvent.class);
        Method cancel = AssistantReflection.method(type, "cancelGesture", MotionEvent.class);
        Method pilfer = AssistantReflection.method(type, "pilferPointers");
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            module.intercept(constructor, chain -> {
                Object result = chain.proceed();
                Object c = ReflectUtils.getField(chain.getThisObject(), "mContext");
                if (c instanceof Context) AssistantClient.get((Context) c);
                return result;
            });
        }
        module.intercept(motion, chain -> {
            Object owner = chain.getThisObject();
            MotionEvent event = (MotionEvent) chain.getArg(0);
            Gesture gesture;
            synchronized (GESTURES) {
                gesture = GESTURES.get(owner);
                if (gesture == null) {
                    Object c = ReflectUtils.getField(owner, "mContext");
                    if (!(c instanceof Context) || Looper.myLooper() == null) return chain.proceed();
                    gesture = new Gesture(owner, (Context) c, cancel, pilfer);
                    GESTURES.put(owner, gesture);
                }
            }
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                gesture.reset();
                Object result = chain.proceed();
                gesture.begin(event);
                return result;
            }
            if (gesture.consumed) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) gesture.reset();
                return null;
            }
            if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
                gesture.reset();
                return chain.proceed();
            }
            gesture.move(event);
            // UP must be intercepted before the native plugin commits back.
            if (action == MotionEvent.ACTION_UP) {
                boolean claimed = gesture.tryClaim();
                gesture.reset();
                return claimed ? null : chain.proceed();
            }
            Object result = chain.proceed();
            if (action == MotionEvent.ACTION_MOVE) gesture.tryClaim();
            return result;
        });
    }

    private static final class Gesture {
        final WeakReference<Object> owner;
        final Context context;
        final Handler handler = new Handler(Looper.myLooper());
        final AssistantGestureState state = new AssistantGestureState();
        final AssistantClient client;
        final Method cancel, pilfer;
        final Runnable timeout = this::tryClaim;
        MotionEvent last;
        boolean consumed;
        boolean fromLeft;

        Gesture(Object owner, Context context, Method cancel, Method pilfer) {
            this.owner = new WeakReference<>(owner);
            this.context = context;
            this.cancel = cancel;
            this.pilfer = pilfer;
            client = AssistantClient.get(context);
        }

        void begin(MotionEvent event) {
            Object target = owner.get();
            ModuleConfig config = ModuleConfig.load(context);
            boolean leftEdge = ReflectUtils.getBooleanField(target, "mIsOnLeftEdge", false);
            if (!config.enabled || !config.assistantGestureEnabled || !client.isReady()
                    || !event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) || event.getPointerCount() != 1
                    || ReflectUtils.invokeNoArgInt(event, "getDisplayId", -1)
                            != ReflectUtils.getIntField(target, "mDisplayId", 0)
                    || ReflectUtils.getIntField(target, "mDisplayId", 0) != 0
                    || !ReflectUtils.getBooleanField(target, "mAllowGesture", false)
                    || !SettingsStore.assistantGestureAllowsSide(config.assistantGestureSide, leftEdge)
                    || ReflectUtils.getBooleanField(target, "mIsTrackpadThreeFingerSwipe", false)
                    || ReflectUtils.getBooleanField(target, "mInterceptBack", false) || locked()) return;
            float density = context.getResources().getDisplayMetrics().density;
            state.begin(event.getX(), event.getY(), event.getDownTime(),
                    config.assistantGestureDistanceDp * density, config.assistantGestureHoldMs, 12f * density, leftEdge,
                    config.assistantGestureVerticalLimitEnabled
                            ? config.assistantGestureVerticalLimitDp * density : Float.POSITIVE_INFINITY);
            fromLeft = leftEdge;
            remember(event);
            handler.postAtTime(timeout, state.deadline());
        }

        void move(MotionEvent event) {
            if (last == null) return;
            // A batched excursion past the limit still cancels even if the finger has returned.
            for (int i = 0; i < event.getHistorySize(); i++) {
                state.move(event.getHistoricalX(i), event.getHistoricalY(i), event.getPointerCount());
            }
            state.move(event.getX(), event.getY(), event.getPointerCount());
            remember(event);
        }

        boolean tryClaim() {
            Object target = owner.get();
            if (last == null || target == null || consumed || !state.ready(SystemClock.uptimeMillis())) return false;
            if (!AssistantClient.enabled(context) || !client.isReady() || locked()
                    || !ReflectUtils.getBooleanField(target, "mAllowGesture", false)
                    || ReflectUtils.getBooleanField(target, "mInterceptBack", false)) { reset(); return false; }
            try {
                // Native predictive back may defer input ownership: ensure the old app loses this stream.
                pilfer.invoke(target);
                cancel.invoke(target, last);
                state.claim();
                consumed = true;
                handler.removeCallbacks(timeout);
                Object panel = ReflectUtils.getField(target, "mEdgeBackPlugin");
                client.show(fromLeft, () -> {
                    if (panel instanceof View) ((View) panel).post(() -> ((View) panel).performHapticFeedback(
                            HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING));
                });
                return true;
            } catch (Throwable t) {
                AssistantHooks.warn("Cannot take over edge back", t);
                reset();
                return false;
            }
        }

        boolean locked() {
            KeyguardManager manager = context.getSystemService(KeyguardManager.class);
            return manager != null && manager.isKeyguardLocked();
        }

        void remember(MotionEvent event) {
            if (last != null) last.recycle();
            last = MotionEvent.obtain(event);
        }

        void reset() {
            state.cancel();
            consumed = false;
            handler.removeCallbacks(timeout);
            if (last != null) { last.recycle(); last = null; }
        }
    }
}
