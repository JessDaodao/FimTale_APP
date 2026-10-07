package com.fimtale.ui;

import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.view.ViewCompat;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PullToRefreshTest {
    private ActivityController<Activity> controller;
    private SkeletonRefreshLayout refresh;
    private TextView content;
    private final AtomicInteger calls = new AtomicInteger();
    private boolean ready = true, scrolled;
    private long down;

    @Before public void setup() {
        controller = Robolectric.buildActivity(Activity.class).setup().visible();
        FrameLayout root = new FrameLayout(controller.get());
        content = new TextView(controller.get()); content.setBackgroundColor(Color.CYAN);
        content.setOnTouchListener((view, event) -> true);
        root.addView(content); controller.get().setContentView(root);
        PullToRefresh.attach(content, calls::incrementAndGet, () -> ready, () -> scrolled);
        refresh = (SkeletonRefreshLayout) content.getParent();
        root.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 400, 800);
        down = SystemClock.uptimeMillis();
    }
    @After public void cleanup() { controller.pause().stop().destroy(); }

    private void touch(int action, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, 200, y, 0);
        refresh.dispatchTouchEvent(event); event.recycle();
    }
    private int drawIndicatorPixels() {
        // A manual Canvas draw has no ViewRoot choreographer updating the frame timestamp.
        Object attachInfo = org.robolectric.util.ReflectionHelpers.getField(refresh, "mAttachInfo");
        org.robolectric.util.ReflectionHelpers.setField(attachInfo, "mDrawingTime", SystemClock.uptimeMillis());
        Bitmap bitmap = Bitmap.createBitmap(400, 800, Bitmap.Config.ARGB_8888);
        refresh.draw(new Canvas(bitmap));
        int visible = 0;
        for (int y = 0; y < 200; y++) for (int x = 120; x < 280; x++)
            if (bitmap.getPixel(x, y) != Color.CYAN) visible++;
        bitmap.recycle(); return visible;
    }
    private void frames(int count) {
        for (int i = 0; i < count; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
            drawIndicatorPixels();
        }
    }
    private void dragToBottom() {
        touch(MotionEvent.ACTION_DOWN, 80);
        for (int y = 100; y <= 740; y += 20) {
            touch(MotionEvent.ACTION_MOVE, y); frames(1);
        }
    }
    private void finishReleaseAnimation() {
        assertTrue(refresh.isRefreshing());
        frames(60);
        // Robolectric's ShadowView.AnimationRunner clears finished animations without calling
        // View.onAnimationEnd; CircleImageView forwards the refresh callback from that hook.
        for (int i = 0; i < refresh.getChildCount(); i++) {
            View child = refresh.getChildAt(i);
            if (child != content)
                org.robolectric.util.ReflectionHelpers.callInstanceMethod(child, "onAnimationEnd");
        }
        frames(30);
    }
    @Test public void indicatorRemainsVisibleWhileHoldingAtMaximumPullAndRefreshesOnceOnRelease() {
        dragToBottom(); frames(40);
        assertTrue("Pull indicator disappeared while the finger was held down", drawIndicatorPixels() > 100);
        assertEquals(0, calls.get());
        assertFalse(refresh.isRefreshing());
        touch(MotionEvent.ACTION_UP, 740); finishReleaseAnimation();
        assertEquals(1, calls.get());
        assertFalse(refresh.isRefreshing());
        assertEquals(Color.CYAN, ((android.graphics.drawable.ColorDrawable) content.getBackground()).getColor());
    }
    @Test public void nestedScrollingAlsoKeepsFeedbackUntilRelease() {
        assertTrue(refresh.onStartNestedScroll(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH));
        refresh.onNestedScrollAccepted(content, content, ViewCompat.SCROLL_AXIS_VERTICAL, ViewCompat.TYPE_TOUCH);
        refresh.onNestedScroll(content, 0, 0, 0, -600, ViewCompat.TYPE_TOUCH, new int[2]);
        frames(40);
        assertTrue(drawIndicatorPixels() > 100);
        assertEquals(0, calls.get());
        refresh.onStopNestedScroll(content, ViewCompat.TYPE_TOUCH); finishReleaseAnimation();
        assertEquals(1, calls.get());
    }
    @Test public void retreatingBeforeReleaseAndUnavailablePagesDoNotRefresh() {
        dragToBottom(); touch(MotionEvent.ACTION_MOVE, 100); touch(MotionEvent.ACTION_UP, 100); frames(60);
        assertEquals(0, calls.get());
        assertFalse(refresh.isRefreshing());
        ready = false;
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(60);
        assertEquals(0, calls.get());
        assertFalse(refresh.isRefreshing());
        ready = true; scrolled = true;
        dragToBottom(); touch(MotionEvent.ACTION_UP, 740); frames(60);
        assertEquals(0, calls.get());
        assertFalse(refresh.isRefreshing());
    }
}
