package com.example.flymestatusbarsizer.feature.assistant;

import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

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
public class AssistantPanelMotionTest {
    @Test public void entrySideIsIndependentOfNativeLayoutDirection() throws Exception {
        for (boolean rtl : new boolean[]{false, true}) {
            for (boolean left : new boolean[]{false, true}) {
                Panel panel = panel(left, rtl);
                assertEquals(left ? -1000f : 1000f, panel.mContentView.getX(), 0.01f);
                panel.setPanelX(500);
                assertEquals(left ? -500f : 500f, panel.mContentView.getX(), 0.01f);
                panel.setPanelX(1000);
                panel.motion.opened();
                assertEquals(0, panel.mContentView.getX(), 0.01f);
                assertEquals(1, panel.mContentView.getScaleX(), 0.01f);
            }
        }
    }

    @Test public void eitherSwipeDirectionClosesTowardFingerRegardlessOfEntry() throws Exception {
        for (boolean left : new boolean[]{true, false}) {
            for (int direction : new int[]{-1, 1}) {
                Panel panel = opened(left);
                startDrag(panel, direction);
                touch(panel, MotionEvent.ACTION_MOVE, 200, 500 + direction * 700, 500);
                assertEquals(direction * 600f, panel.mContentView.getX(), 0.01f);
                touch(panel, MotionEvent.ACTION_UP, 1000, 500 + direction * 700, 500);
                assertEquals(1, panel.closes);
                assertEquals(0, panel.opens);
                panel.setPanelX(0);
                assertEquals(direction * 1000f, panel.mContentView.getX(), 0.01f);
                assertEquals(1, panel.resets);
            }
        }
    }

    @Test public void backAlwaysClosesLeftIncludingInterruptedRightEntry() throws Exception {
        for (int progress : new int[]{500, 1000}) {
            Panel panel = panel(false, false);
            panel.setPanelX(progress);
            float start = panel.mContentView.getX();
            panel.closePanel(300);
            assertEquals(start, panel.mContentView.getX(), 0.01f);
            panel.setPanelX(progress / 2);
            assertEquals((start - 1000) / 2, panel.mContentView.getX(), 0.01f);
            panel.setPanelX(0);
            assertEquals(-1000, panel.mContentView.getX(), 0.01f);
        }
    }

    @Test public void shortSwipeAndCanceledLongSwipeReturnToOpen() throws Exception {
        for (int direction : new int[]{-1, 1}) {
            for (boolean cancel : new boolean[]{false, true}) {
                Panel panel = opened(false);
                startDrag(panel, direction);
                float end = 500 + direction * (cancel ? 800 : 200);
                touch(panel, MotionEvent.ACTION_MOVE, 200, end, 500);
                touch(panel, cancel ? MotionEvent.ACTION_CANCEL : MotionEvent.ACTION_UP, 1000, end, 500);
                assertEquals(0, panel.closes);
                assertEquals(1, panel.opens);
                panel.setPanelX(1000);
                panel.motion.opened();
                assertEquals(0, panel.mContentView.getX(), 0.01f);
            }
        }
    }

    @Test public void draggingCanCrossCenterWithoutJumping() throws Exception {
        Panel panel = opened(true);
        startDrag(panel, 1);
        touch(panel, MotionEvent.ACTION_MOVE, 200, 800, 500);
        assertEquals(200, panel.mContentView.getX(), 0.01f);
        touch(panel, MotionEvent.ACTION_MOVE, 300, 600, 500);
        assertEquals(0, panel.mContentView.getX(), 0.01f);
        touch(panel, MotionEvent.ACTION_MOVE, 400, 300, 500);
        assertEquals(-300, panel.mContentView.getX(), 0.01f);
    }

    @Test public void secondFingerCancelsDragAndReopens() throws Exception {
        Panel panel = opened(true);
        startDrag(panel, -1);
        touch(panel, MotionEvent.ACTION_MOVE, 200, 0, 500);
        touch(panel, MotionEvent.ACTION_POINTER_DOWN, 300, 0, 500);
        assertEquals(1, panel.opens);
        assertEquals(0, panel.closes);
        touch(panel, MotionEvent.ACTION_UP, 400, 0, 500);
        assertEquals(0, panel.closes);
    }

    @Test public void outwardFlickClosesBeforeHalfWidth() throws Exception {
        for (int direction : new int[]{-1, 1}) {
            Panel panel = opened(true);
            startDrag(panel, direction);
            touch(panel, MotionEvent.ACTION_MOVE, 110, 500 + direction * 180, 500);
            touch(panel, MotionEvent.ACTION_UP, 120, 500 + direction * 260, 500);
            assertEquals(1, panel.closes);
            assertEquals(0, panel.opens);
        }
    }

    @Test public void realDispatchCancelsChildOnlyWhenHorizontalDragIsClaimed() throws Exception {
        Panel panel = opened(false);
        java.util.List<Integer> childEvents = new java.util.ArrayList<>();
        panel.mContentView.setOnTouchListener((view, event) -> {
            childEvents.add(event.getActionMasked());
            return true;
        });
        for (int i = 0; i < 4; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == 3 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(0, i * 100L, action, 500 + i * 100, 500, 0);
            try { assertTrue(panel.dispatchTouchEvent(event)); }
            finally { event.recycle(); }
        }
        assertEquals(java.util.Arrays.asList(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_CANCEL), childEvents);
        assertEquals(1, panel.drags);
        assertEquals(1, panel.closes);
    }

    @Test public void verticalScrollingTapsAndDisabledDraggingStayWithChildren() throws Exception {
        Panel panel = opened(true);
        assertFalse(intercept(panel, MotionEvent.ACTION_DOWN, 0, 500, 500));
        assertFalse(intercept(panel, MotionEvent.ACTION_UP, 50, 500, 500));
        assertFalse(intercept(panel, MotionEvent.ACTION_DOWN, 0, 500, 500));
        assertFalse(intercept(panel, MotionEvent.ACTION_MOVE, 100, 500, 700));
        assertFalse(intercept(panel, MotionEvent.ACTION_MOVE, 200, 900, 700));
        assertEquals(0, panel.drags);
        panel.mPanelDragEnable = false;
        assertFalse(intercept(panel, MotionEvent.ACTION_DOWN, 0, 500, 500));
        assertFalse(intercept(panel, MotionEvent.ACTION_MOVE, 100, 900, 500));
        assertEquals(0, panel.drags);
    }

    @Test public void nativeLayoutPassReappliesRightEntryTranslation() throws Exception {
        Panel panel = panel(false, false);
        panel.setPanelX(400);
        panel.mContentView.layout(-1000, 0, 0, 1800);
        panel.motion.positionChanged();
        assertEquals(600, panel.mContentView.getX(), 0.01f);
    }

    @Test public void disposeCancelsAnimationAndReleasesNativeTouchState() throws Exception {
        Panel panel = opened(false);
        startDrag(panel, 1);
        int cancellations = panel.mSlidingPanelLayoutInterpolator.cancellations;
        AssistantPanelMotion motion = panel.motion;
        // Session restoration disables global hooks before disposing motion.
        panel.motion = null;
        motion.dispose();
        assertEquals(cancellations + 1, panel.mSlidingPanelLayoutInterpolator.cancellations);
        assertEquals(1, panel.resets);
        assertEquals(-1000, panel.mContentView.getX(), 0.01f);
    }

    private Panel panel(boolean fromLeft, boolean rtl) throws Exception {
        Panel panel = new Panel(rtl);
        panel.motion = new AssistantPanelMotion(panel, panel.mContentView, fromLeft);
        panel.motion.positionChanged();
        return panel;
    }

    private Panel opened(boolean fromLeft) throws Exception {
        Panel panel = panel(fromLeft, false);
        panel.setPanelX(1000);
        panel.motion.opened();
        return panel;
    }

    private void startDrag(Panel panel, int direction) throws Exception {
        assertFalse(intercept(panel, MotionEvent.ACTION_DOWN, 0, 500, 500));
        assertTrue(intercept(panel, MotionEvent.ACTION_MOVE, 100, 500 + direction * 100, 500));
        touch(panel, MotionEvent.ACTION_MOVE, 100, 500 + direction * 100, 500);
    }

    private boolean intercept(Panel panel, int action, long time, float x, float y) throws Exception {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        try { return panel.motion.intercept(event); }
        finally { event.recycle(); }
    }

    private void touch(Panel panel, int action, long time, float x, float y) throws Exception {
        MotionEvent event = MotionEvent.obtain(0, time, action, x, y, 0);
        try { panel.motion.touch(event); }
        finally { event.recycle(); }
    }

    /** Native API stand-in: progress and completion stay owned by the assistant animator. */
    private static final class Panel extends FrameLayout {
        final View mContentView;
        final Interpolator mSlidingPanelLayoutInterpolator = new Interpolator();
        final boolean rtl;
        boolean mPanelDragEnable = true;
        int mPanelX, animateTime = 300, drags, resets, opens, closes;
        AssistantPanelMotion motion;

        Panel(boolean rtl) {
            super(RuntimeEnvironment.getApplication());
            this.rtl = rtl;
            mContentView = new View(getContext());
            addView(mContentView);
            measure(MeasureSpec.makeMeasureSpec(1000, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(1800, MeasureSpec.EXACTLY));
            layout(0, 0, 1000, 1800);
            mContentView.layout(rtl ? 1000 : -1000, 0, rtl ? 2000 : 0, 1800);
        }

        public void setPanelX(int x) throws Exception {
            mPanelX = x;
            mContentView.setTranslationX(rtl ? -x : x);
            if (motion != null) motion.positionChanged();
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event) {
            if (motion == null) return super.onInterceptTouchEvent(event);
            try { return motion.intercept(event); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        @Override public boolean onTouchEvent(MotionEvent event) {
            if (motion == null) return super.onTouchEvent(event);
            try { return motion.touch(event); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        public void startDrag() { drags++; }
        private void resetTouchState() { resets++; }
        public void openPanel(int duration) { opens++; }
        public void closePanel(int duration) throws Exception {
            if (motion != null) motion.prepareClose();
            closes++;
        }
    }

    private static final class Interpolator {
        int cancellations;
        public void cancelAll() { cancellations++; }
    }
}
