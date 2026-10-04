package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantWindowBackgroundTest {
    private String previousWindowVisibility;

    @Before public void showTestWindows() {
        // Legacy Robolectric marks windows GONE by default, which hides their backgrounds.
        previousWindowVisibility = System.getProperty("robolectric.areWindowsMarkedVisible");
        System.setProperty("robolectric.areWindowsMarkedVisible", "true");
    }

    @After public void restoreWindowVisibilitySetting() {
        if (previousWindowVisibility == null) System.clearProperty("robolectric.areWindowsMarkedVisible");
        else System.setProperty("robolectric.areWindowsMarkedVisible", previousWindowVisibility);
    }

    @Test public void repeatedGlobalSessionsLeaveNativeTransparentBackgroundUntouched() {
        View decor = new View(RuntimeEnvironment.getApplication());
        ColorDrawable original = new ColorDrawable(Color.TRANSPARENT);
        Drawable shared = original.getConstantState().newDrawable();
        decor.setBackground(original);

        for (int i = 0; i < 3; i++) {
            AssistantWindowBackground background = new AssistantWindowBackground(decor, 3);
            background.apply();
            assertNotSame(original, decor.getBackground());
            assertEquals(Color.TRANSPARENT, original.getColor());
            assertEquals(Color.TRANSPARENT, ((ColorDrawable) shared).getColor());
            assertEquals(Color.TRANSPARENT, ((ColorDrawable) decor.getBackground()).getColor());

            background.restore();
            assertSame(original, decor.getBackground());
            assertEquals(Color.TRANSPARENT, original.getColor());
        }
    }

    @Test public void restoresOriginalDrawableOrAbsentBackground() {
        View decor = new View(RuntimeEnvironment.getApplication());
        for (Drawable original : new Drawable[]{new GradientDrawable(), null}) {
            decor.setBackground(original);
            AssistantWindowBackground background = new AssistantWindowBackground(decor, 1);
            background.apply();
            background.restore();
            background.restore();
            assertSame(original, decor.getBackground());
        }
    }

    @Test @Config(sdk = 31)
    public void windowAttributesAllowNativeDrawingAndDoNotLeakBackToLauncher() {
        Window window = new Dialog(RuntimeEnvironment.getApplication()).getWindow();
        View decor = window.getDecorView();
        ColorDrawable originalBackground = new ColorDrawable(Color.TRANSPARENT);
        decor.setBackground(originalBackground);
        WindowManager.LayoutParams original = new WindowManager.LayoutParams();
        original.copyFrom(window.getAttributes());
        original.format = PixelFormat.TRANSPARENT;
        original.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_BLUR_BEHIND;
        original.setBlurBehindRadius(23);
        AssistantWindowSession.applyIdentityAndAttributes(window, original);

        for (int i = 0; i < 2; i++) {
            AssistantWindowBackground background = new AssistantWindowBackground(decor, 3);
            WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.copyFrom(original);
            background.apply();
            background.updateAttributes(attrs);
            AssistantWindowSession.applyIdentityAndAttributes(window, attrs);
            assertEquals(PixelFormat.TRANSLUCENT, window.getAttributes().format);
            assertEquals(0, window.getAttributes().flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            assertTrue((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0);
            assertEquals(0, window.getAttributes().getBlurBehindRadius());
            assertTrue((attrs.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0);

            // Native panel animation can change alpha and replace window flags.
            attrs.alpha = 0.5f;
            attrs.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            attrs.format = PixelFormat.OPAQUE;
            attrs.setBlurBehindRadius(0);
            background.updateAttributes(attrs);
            assertEquals(PixelFormat.TRANSLUCENT, attrs.format);
            assertEquals(0, attrs.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            assertTrue((attrs.flags & WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED) != 0);
            assertTrue((attrs.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
            assertEquals(0, attrs.getBlurBehindRadius());
            assertEquals(0.5f, attrs.alpha, 0);

            background.restore();
            AssistantWindowSession.applyIdentityAndAttributes(window, original);
            assertSame(originalBackground, decor.getBackground());
            assertEquals(Color.TRANSPARENT, originalBackground.getColor());
            assertEquals(original.flags, window.getAttributes().flags);
            assertEquals(original.format, window.getAttributes().format);
            assertEquals(23, window.getAttributes().getBlurBehindRadius());
        }
    }

    @Test @Config(sdk = 31)
    public void nativeBlurRegistersOnAttachedRootWithoutTintAndIsReleasedOnDetach() throws Exception {
        Dialog dialog = new Dialog(RuntimeEnvironment.getApplication());
        FrameLayout host = new FrameLayout(dialog.getContext());
        dialog.setContentView(host);
        dialog.getWindow().setLayout(400, 600);
        dialog.show();
        Dialog nextDialog = new Dialog(RuntimeEnvironment.getApplication());
        FrameLayout nextHost = new FrameLayout(nextDialog.getContext());
        nextDialog.setContentView(nextHost);
        nextDialog.getWindow().setLayout(400, 600);
        nextDialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        host.layout(0, 0, 200, 300);
        nextHost.layout(0, 0, 200, 300);
        FrameLayout decor = new FrameLayout(dialog.getContext());
        decor.layout(0, 0, 100, 200);
        ColorDrawable original = new ColorDrawable(Color.TRANSPARENT);
        decor.setBackground(original);
        TextView card = new TextView(dialog.getContext());
        card.setText("Assistant card");
        Drawable cardBackground = new ColorDrawable(Color.WHITE);
        card.setBackground(cardBackground);
        decor.addView(card);

        AssistantWindowBackground background = new AssistantWindowBackground(decor, 3);
        background.apply();
        assertTrue(decor.getBackground() instanceof ColorDrawable);
        host.addView(decor);
        decor.getViewTreeObserver().dispatchOnPreDraw();
        // Check the real framework's region registration without asking the legacy software
        // test canvas to draw a hardware RenderNode. SurfaceFlinger output needs a device.
        assertTrue(decor.isAttachedToWindow());
        Drawable first = decor.getBackground();
        assertEquals("com.android.internal.graphics.drawable.BackgroundBlurDrawable", first.getClass().getName());
        Object root = AssistantReflection.call(decor, "getViewRootImpl");
        Object aggregator = AssistantReflection.get(first, "mAggregator");
        assertSame(root, AssistantReflection.get(aggregator, "mViewRoot"));
        assertEquals(first + ", shown=" + decor.isShown() + ", windowVisibility=" + decor.getWindowVisibility(),
                true, AssistantReflection.call(aggregator, "hasRegions"));
        assertEquals(120, AssistantReflection.get(first, "mBlurRadius"));
        assertEquals(1f, (Float) AssistantReflection.get(first, "mAlpha"), 0);
        assertEquals(Color.TRANSPARENT, ((Paint) AssistantReflection.get(first, "mPaint")).getColor());
        assertSame(cardBackground, card.getBackground());
        assertEquals("Assistant card", card.getText().toString());

        host.removeView(decor);
        assertEquals(false, AssistantReflection.call(aggregator, "hasRegions"));
        assertNull(first.getCallback());
        nextHost.addView(decor);
        Drawable second = decor.getBackground();
        assertNotSame(first, second);
        assertEquals(first.getClass(), second.getClass());
        Object nextAggregator = AssistantReflection.get(second, "mAggregator");
        assertNotSame(aggregator, nextAggregator);
        assertSame(AssistantReflection.call(decor, "getViewRootImpl"),
                AssistantReflection.get(nextAggregator, "mViewRoot"));
        assertEquals(true, AssistantReflection.call(nextAggregator, "hasRegions"));

        background.restore();
        assertEquals(false, AssistantReflection.call(aggregator, "hasRegions"));
        assertEquals(false, AssistantReflection.call(nextAggregator, "hasRegions"));
        assertSame(original, decor.getBackground());
        assertNull(second.getCallback());
        // A later desktop attachment must not recreate the global blur.
        nextHost.removeView(decor);
        host.addView(decor);
        assertSame(original, decor.getBackground());
        assertEquals(false, AssistantReflection.call(aggregator, "hasRegions"));
        nextDialog.dismiss();
        dialog.dismiss();
    }

    @Test @Config(sdk = 31)
    public void blurMovesWithContentAndDisappearsBeforeTheWindowIsRemoved() throws Exception {
        Dialog dialog = new Dialog(RuntimeEnvironment.getApplication());
        FrameLayout panel = new FrameLayout(dialog.getContext());
        Drawable stationaryBackground = new ColorDrawable(Color.TRANSPARENT);
        panel.setBackground(stationaryBackground);
        dialog.setContentView(panel);
        dialog.getWindow().setLayout(400, 600);
        dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        panel.layout(0, 0, 200, 300);
        FrameLayout content = new FrameLayout(dialog.getContext());
        Drawable original = new ColorDrawable(Color.TRANSPARENT);
        content.setBackground(original);
        // Match SlidingPanelLayout: content is laid out just offscreen, then translated inward.
        content.layout(-200, 0, 0, 300);
        AssistantWindowBackground background = new AssistantWindowBackground(content, 3);
        background.apply();
        panel.addView(content);
        Drawable blur = content.getBackground();
        Object aggregator = AssistantReflection.get(blur, "mAggregator");
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertEquals(false, AssistantReflection.call(aggregator, "hasRegions"));

        content.setTranslationX(200);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(content, blur.getCallback());
        assertSame(stationaryBackground, panel.getBackground());
        assertEquals(1f, (Float) AssistantReflection.get(blur, "mAlpha"), 0);
        assertEquals(true, AssistantReflection.call(aggregator, "hasRegions"));

        // Halfway through closing, only the same half of the panel remains on screen.
        Rect visible = new Rect();
        content.setTranslationX(100);
        content.setAlpha(0.5f);
        panel.setAlpha(0.8f);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertTrue(content.getGlobalVisibleRect(visible));
        assertEquals(100, visible.width());
        assertEquals(102 / 255f, (Float) AssistantReflection.get(blur, "mAlpha"), 0.001f);
        assertEquals(0.5f, content.getAlpha(), 0);

        // Cancelling a close restores the same blur without a second independent animation.
        content.setTranslationX(200);
        content.setAlpha(1);
        panel.setAlpha(1);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(blur, content.getBackground());
        assertEquals(1f, (Float) AssistantReflection.get(blur, "mAlpha"), 0);

        content.setTranslationX(0);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertTrue(dialog.isShowing());
        assertTrue(content.isAttachedToWindow());
        assertEquals(0f, (Float) AssistantReflection.get(blur, "mAlpha"), 0);
        assertEquals(false, AssistantReflection.call(aggregator, "hasRegions"));
        background.restore();
        content.setTranslationX(200);
        content.getViewTreeObserver().dispatchOnPreDraw();
        assertSame(original, content.getBackground());
        assertSame(stationaryBackground, panel.getBackground());
        assertEquals(false, AssistantReflection.call(aggregator, "hasRegions"));
        dialog.dismiss();
    }

    @Test @Config(sdk = 31)
    public void cancellingBeforeAttachmentDoesNotInstallBlurOnTheDesktop() {
        Dialog dialog = new Dialog(RuntimeEnvironment.getApplication());
        View decor = new View(dialog.getContext());
        Drawable original = new ColorDrawable(Color.TRANSPARENT);
        decor.setBackground(original);
        AssistantWindowBackground background = new AssistantWindowBackground(decor, 3);
        background.apply();
        background.restore();
        dialog.setContentView(decor);
        dialog.show();
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue(decor.isAttachedToWindow());
        assertSame(original, decor.getBackground());
        dialog.dismiss();
    }
}
