package com.example.flymestatusbarsizer.feature.assistant;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantWindowBackgroundTest {
    @Test public void repeatedGlobalSessionsLeaveNativeTransparentBackgroundUntouched() {
        View decor = new View(RuntimeEnvironment.getApplication());
        ColorDrawable original = new ColorDrawable(Color.TRANSPARENT);
        Drawable shared = original.getConstantState().newDrawable();
        decor.setBackground(original);

        for (boolean dark : new boolean[]{false, true, false}) {
            AssistantWindowBackground background = new AssistantWindowBackground(decor, dark, 3);
            background.apply();
            assertNotSame(original, decor.getBackground());
            assertEquals(Color.TRANSPARENT, original.getColor());
            assertEquals(Color.TRANSPARENT, ((ColorDrawable) shared).getColor());
            int tint = ((ColorDrawable) decor.getBackground()).getColor();
            assertTrue(Color.alpha(tint) > 0 && Color.alpha(tint) < 255);
            assertEquals(dark ? 0x181818 : 0xf3f3f3, tint & 0xffffff);

            background.restore();
            assertSame(original, decor.getBackground());
            assertEquals(Color.TRANSPARENT, original.getColor());
        }
    }

    @Test public void restoresOriginalDrawableOrAbsentBackground() {
        View decor = new View(RuntimeEnvironment.getApplication());
        for (Drawable original : new Drawable[]{new GradientDrawable(), null}) {
            decor.setBackground(original);
            AssistantWindowBackground background = new AssistantWindowBackground(decor, false, 1);
            background.apply();
            background.restore();
            background.restore();
            assertSame(original, decor.getBackground());
        }
    }

    @Test @Config(sdk = 31)
    public void liveBlurSurvivesNativeAttributeUpdatesAndDoesNotLeakBackToLauncher() {
        Window window = new Dialog(RuntimeEnvironment.getApplication()).getWindow();
        View decor = window.getDecorView();
        ColorDrawable originalBackground = new ColorDrawable(Color.TRANSPARENT);
        decor.setBackground(originalBackground);
        WindowManager.LayoutParams original = new WindowManager.LayoutParams();
        original.copyFrom(window.getAttributes());
        original.format = PixelFormat.TRANSPARENT;
        original.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        original.setBlurBehindRadius(0);
        AssistantWindowSession.applyIdentityAndAttributes(window, original);

        for (int i = 0; i < 2; i++) {
            AssistantWindowBackground background = new AssistantWindowBackground(decor, false, 3);
            WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
            attrs.copyFrom(original);
            background.apply();
            background.updateAttributes(attrs);
            AssistantWindowSession.applyIdentityAndAttributes(window, attrs);
            assertEquals(PixelFormat.TRANSLUCENT, window.getAttributes().format);
            assertTrue((window.getAttributes().flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0);
            assertEquals(120, window.getAttributes().getBlurBehindRadius());
            assertEquals(original.flags, attrs.flags & ~WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

            // Native panel animation can change alpha and replace window flags.
            attrs.alpha = 0.5f;
            attrs.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            attrs.format = PixelFormat.OPAQUE;
            attrs.setBlurBehindRadius(0);
            background.updateAttributes(attrs);
            assertEquals(PixelFormat.TRANSLUCENT, attrs.format);
            assertTrue((attrs.flags & WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0);
            assertTrue((attrs.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
            assertEquals(120, attrs.getBlurBehindRadius());
            assertEquals(0.5f, attrs.alpha, 0);

            background.restore();
            AssistantWindowSession.applyIdentityAndAttributes(window, original);
            assertSame(originalBackground, decor.getBackground());
            assertEquals(Color.TRANSPARENT, originalBackground.getColor());
            assertEquals(original.flags, window.getAttributes().flags);
            assertEquals(original.format, window.getAttributes().format);
            assertEquals(0, window.getAttributes().getBlurBehindRadius());
        }
    }
}
